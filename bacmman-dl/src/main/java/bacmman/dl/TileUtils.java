package bacmman.dl;

import bacmman.image.*;
import bacmman.processing.Resize;
import bacmman.utils.ArrayUtil;
import bacmman.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TileUtils {
    static Logger logger = LoggerFactory.getLogger(TileUtils.class);


    public static Image[] splitTiles(Image input, int[] tileShapeXYZ, int[] minOverlapXYZ, Resize.EXPAND_MODE paddingMode) {
        long[] size = new long[]{input.sizeX(), input.sizeY(), input.sizeZ()};
        long[][] coords = getTilesCoords(size, tileShapeXYZ, minOverlapXYZ, paddingMode!=null);
        return Resize.crop(input, coords, new long[][]{ArrayUtil.toLong(tileShapeXYZ)}, paddingMode);
    }

    public static Image[][] splitTiles(Image[] inputC, int[] tileShapeXYZ, int[] minOverlapXYZ, Resize.EXPAND_MODE paddingMode) {
        long[] size = new long[]{inputC[0].sizeX(), inputC[0].sizeY(), inputC[0].sizeZ()};
        long[][] coords = getTilesCoords(size, tileShapeXYZ, minOverlapXYZ, paddingMode!=null);
        Image[][] CN = Arrays.stream(inputC).map(input -> Resize.crop(input, coords, new long[][]{ArrayUtil.toLong(tileShapeXYZ)}, paddingMode)).toArray(Image[][]::new);
        return ArrayUtil.transpose(CN, new Image[coords.length][inputC.length]);
    }

    public static Image[][] splitTiles(Image[][] inputNC, int[] tileShapeXYZ, int[] minOverlapXYZ, Resize.EXPAND_MODE paddingMode) {
        long[] size = tileShapeXYZ.length==2 ? new long[]{inputNC[0][0].sizeX(), inputNC[0][0].sizeY()} : new long[]{inputNC[0][0].sizeX(), inputNC[0][0].sizeY(), inputNC[0][0].sizeZ()};
        long[][] sizes = new long[][]{ArrayUtil.toLong(tileShapeXYZ)};
        long[][] coords = getTilesCoords(size, tileShapeXYZ, minOverlapXYZ, paddingMode!=null);
        logger.debug("tile coords: {}", Utils.toStringArray(coords[0]));
        Image[][] res = new Image[coords.length * inputNC.length][inputNC[0].length];
        for (int i = 0; i<inputNC.length; ++i) {
            Image[][] CN = Arrays.stream(inputNC[i]).map(input -> Resize.crop(input, coords, sizes, paddingMode)).toArray(Image[][]::new);
            int offset = i * coords.length;
            for (int t = 0; t<coords.length; ++t) {
                for (int c = 0; c<inputNC[0].length; ++c) res[offset + t][c] = CN[c][t];
            }
        }
        return res;
    }

    public static void mergeTiles(Image[][] targetNC, Image[][] tilesNtC, int[] minOverlapXYZ) {
        int nTiles = tilesNtC.length / targetNC.length;
        Image[] tiles = new Image[nTiles];
        for (int n = 0; n<targetNC.length; ++n) {
            for (int c = 0; c<targetNC[n].length; ++c) {
                int off = n*nTiles;
                for (int t = 0; t < nTiles; ++t) tiles[t] = tilesNtC[off + t][c];
                mergeTiles(targetNC[n][c], tiles, minOverlapXYZ);
            }
        }
    }

    public static void mergeTiles(Image target, Image[] tiles, int[] minOverlapXYZ) {
        BoundingBox extend = minOverlapXYZ==null ? new MutableBoundingBox(0, 0, 0) :  new MutableBoundingBox(minOverlapXYZ[0]/2, -minOverlapXYZ[0]/2, minOverlapXYZ[1]/2, -minOverlapXYZ[1]/2, minOverlapXYZ.length>2 ? minOverlapXYZ[2]/2 : 0, minOverlapXYZ.length>2 ? -minOverlapXYZ[2]/2 : 0);
        Map<Image, BoundingBox> tileView = Arrays.stream(tiles).collect(Collectors.toMap(Function.identity(),  tile -> tile.getBoundingBox().extend(extend).trim(target)));
        Map<Integer, List<Image>> tilesPerX = Arrays.stream(tiles).collect(Collectors.groupingBy(SimpleBoundingBox::xMin));
        int[] x = tilesPerX.keySet().stream().sorted().mapToInt(i->i).toArray();
        Image[][][] tilesXYZ = new Image[x.length][][];
        for (int xi = 0; xi<x.length; ++xi) {
            List<Image> imagesX = tilesPerX.get(x[xi]);
            Map<Integer, List<Image>> tilesPerY = imagesX.stream().collect(Collectors.groupingBy(SimpleBoundingBox::yMin));
            int[] y = tilesPerY.keySet().stream().sorted().mapToInt(i->i).toArray();
            tilesXYZ[xi] = new Image[y.length][];
            for (int yi = 0; yi<y.length; ++yi) {
                List<Image> imagesY = tilesPerY.get(y[yi]);
                tilesXYZ[xi][yi] = imagesY.stream().sorted(Comparator.comparingInt(SimpleBoundingBox::zMin)).toArray(Image[]::new);
            }
        }
        List<Overlap> overlaps = new ArrayList<>();
        Image[] nextXYZ = new Image[3];
        Image[] prevXYZ = new Image[3];
        Image[] nextXY_XZ_YZ_XYZ = new Image[4];
        if (tilesXYZ[0][0].length==1) {
            for (int xi = 0; xi<x.length; ++xi) {
                for (int yi=0; yi<tilesXYZ[xi].length; ++yi) {
                    for (int zi = 0; zi<tilesXYZ[xi][yi].length; ++zi) {
                        prevXYZ[0] = xi==0? null : tilesXYZ[xi-1][yi][zi];
                        prevXYZ[1] = yi==0? null : tilesXYZ[xi][yi-1][zi];
                        prevXYZ[2] = zi==0? null : tilesXYZ[xi][yi][zi-1];
                        nextXYZ[0] = xi<x.length-1? tilesXYZ[xi+1][yi][zi] : null;
                        nextXYZ[1] = yi<tilesXYZ[xi].length-1? tilesXYZ[xi][yi+1][zi] : null;
                        nextXYZ[2] = zi<tilesXYZ[xi][yi].length-1? tilesXYZ[xi][yi][zi+1] : null;
                        nextXY_XZ_YZ_XYZ[0] = nextXYZ[0]==null || nextXYZ[1] ==null ? null : tilesXYZ[xi+1][yi + 1][zi];
                        nextXY_XZ_YZ_XYZ[1] = nextXYZ[0]==null || nextXYZ[2] ==null ? null : tilesXYZ[xi+1][yi][zi+1];
                        nextXY_XZ_YZ_XYZ[2] = nextXYZ[1]==null || nextXYZ[2] ==null ? null : tilesXYZ[xi][yi + 1][zi + 1];
                        nextXY_XZ_YZ_XYZ[3] = nextXYZ[0]==null || nextXYZ[1] ==null || nextXYZ[2] ==null ? null : tilesXYZ[xi+1][yi + 1][zi + 1];
                        Overlap.addOverlaps(tilesXYZ[xi][yi][zi], prevXYZ, nextXYZ, nextXY_XZ_YZ_XYZ, tileView, overlaps);
                    }

                }
            }
        }
        //overlaps.parallelStream().forEach(o -> o.copyToImage(target));
        for (Overlap o : overlaps) o.copyToImage(target);
    }
    @FunctionalInterface
    interface ToIntIntFunction {
        int getAsInt(int i);
    }
    private static class Overlap {
        BoundingBox area;
        Image[] tiles;

        public Overlap(BoundingBox area, Image... tiles) {
            this.tiles = tiles;
            this.area = area;
        }

        public static void addOverlaps(Image tile, Image[] prevXYZ, Image[] nextXYZ, Image[] nextXY_XZ_YZ_XYZ, Map<Image, BoundingBox> tileView, List<Overlap> dest) {
            BoundingBox view = tileView.get(tile);
            BoundingBox[] prevView = Arrays.stream(prevXYZ).map(a -> a==null ? null : tileView.get(a)).toArray(BoundingBox[]::new);
            BoundingBox[] nextView = Arrays.stream(nextXYZ).map(a -> a==null ? null : tileView.get(a)).toArray(BoundingBox[]::new);
            ToIntIntFunction getCoordMin = axis -> prevView[axis] == null ? view.getIntPosition(axis) : Math.max(prevView[axis].getMax(axis),  view.getIntPosition(axis));
            ToIntIntFunction getCoordMax = axis -> nextView[axis] == null ? view.getMax(axis) : Math.min(nextView[axis].getIntPosition(axis), view.getMax(axis));

            // center tile:
            dest.add(new Overlap(new SimpleBoundingBox(getCoordMin.getAsInt(0), getCoordMax.getAsInt(0), getCoordMin.getAsInt(1), getCoordMax.getAsInt(1), getCoordMin.getAsInt(2), getCoordMax.getAsInt(2)), tile));
            // 2 overlapping tiles
            if (nextXYZ[0]!=null) dest.add(new Overlap(new SimpleBoundingBox(nextView[0].xMin(), view.xMax(), getCoordMin.getAsInt(1), getCoordMax.getAsInt(1), getCoordMin.getAsInt(2), getCoordMax.getAsInt(2)), tile, nextXYZ[0]));
            if (nextXYZ[1]!=null) dest.add(new Overlap(new SimpleBoundingBox(getCoordMin.getAsInt(0), getCoordMax.getAsInt(0), nextView[1].yMin(), view.yMax(), getCoordMin.getAsInt(2), getCoordMax.getAsInt(2)), tile, nextXYZ[1]));
            if (nextXYZ[2]!=null) dest.add(new Overlap(new SimpleBoundingBox(getCoordMin.getAsInt(0), getCoordMax.getAsInt(0), getCoordMin.getAsInt(1), getCoordMax.getAsInt(1), nextView[2].zMin(), view.zMax()), tile, nextXYZ[2]));
            // 4 overlapping tiles
            if (nextXYZ[0]!=null && nextXYZ[1]!=null) dest.add(new Overlap(new SimpleBoundingBox(nextView[0].xMin(), view.xMax(), nextView[1].yMin(), view.yMax(), getCoordMin.getAsInt(2), getCoordMax.getAsInt(2)), tile, nextXYZ[0], nextXYZ[1], nextXY_XZ_YZ_XYZ[0]));
            if (nextXYZ[0]!=null && nextXYZ[2]!=null) dest.add(new Overlap(new SimpleBoundingBox(nextView[0].xMin(), view.xMax(), getCoordMin.getAsInt(1), getCoordMax.getAsInt(1), nextView[2].zMin(), view.zMax()), tile, nextXYZ[0], nextXYZ[2], nextXY_XZ_YZ_XYZ[1]));
            if (nextXYZ[1]!=null && nextXYZ[2]!=null) dest.add(new Overlap(new SimpleBoundingBox(getCoordMin.getAsInt(0), getCoordMax.getAsInt(0), nextView[1].yMin(), view.yMax(), nextView[2].zMin(), view.zMax()), tile, nextXYZ[1], nextXYZ[2], nextXY_XZ_YZ_XYZ[2]));
            // 8 overlapping tile
            if (nextXYZ[0]!=null && nextXYZ[1]!=null && nextXYZ[2]!=null) dest.add(new Overlap(new SimpleBoundingBox(nextView[0].xMin(), view.xMax(), nextView[1].yMin(), view.yMax(), nextView[2].zMin(), view.zMax()), tile, nextXYZ[0], nextXYZ[1], nextXYZ[2], nextXY_XZ_YZ_XYZ[0], nextXY_XZ_YZ_XYZ[1], nextXY_XZ_YZ_XYZ[2], nextXY_XZ_YZ_XYZ[3]));

        }
        public void copyToImage(Image target) {
            if (this.tiles.length==1) { // simple paste
                Image.pasteImageView(tiles[0], target, area.duplicate().translate(target.getOffset().reverseOffset()), area.duplicate().translate(tiles[0].getBoundingBox().reverseOffset()) );
            } else { // average
                double norm = tiles.length;
                BoundingBox.loop(area, (x ,y, z)-> {
                    double res = 0;
                    for (Image tile: tiles) res+=tile.getPixelWithOffset(x, y, z);
                    target.setPixelWithOffset(x, y, z, res / norm);
                });
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Overlap overlap = (Overlap) o;
            return Objects.equals(area, overlap.area);
        }

        @Override
        public int hashCode() {
            return Objects.hash(area);
        }
    }
    public static long[] getTileCoordAxis(long size, int tileSize, int minOverlap, boolean padding) {
        if (tileSize==size && !padding) return new long[1];
        else if (tileSize>size) throw new IllegalArgumentException("Tile size must be inferior or equal to size");
        else if (minOverlap>=tileSize) throw new IllegalArgumentException("Min overlap must be inferior to tile size");
        long effectiveSize = padding ? size + 2*minOverlap : size;
        int nTiles = (int) Math.ceil((effectiveSize - minOverlap) / (double)(tileSize - minOverlap));
        long sumStride = Math.abs(nTiles * tileSize - effectiveSize);
        long[] stride = new long[nTiles];
        for (int i = 1; i<nTiles; ++i) stride[i] = -sumStride/(nTiles - 1);
        long remain = sumStride%(nTiles - 1);
        for (int i = 1; i<=remain; ++i) --stride[i];
        for (int i = 1; i<stride.length; ++i) stride[i] +=stride[i-1];
        long[] coords = new long[nTiles];
        int offset = padding ? -minOverlap : 0;
        for (int i = 0; i<nTiles; ++i) coords[i] = tileSize*i + stride[i] + offset;
        //logger.debug("size: {}, tile size: {}, min o: {}, padding: {}, tiles: {}", size, tileSize, minOverlap, padding, coords);
        return coords;
    }
    public static long[][] getTilesCoords(long[] size, int[] tileSize, int[] minOverlap, boolean padding) {
        long[][] coordsAxis = IntStream.range(0, size.length).mapToObj(i -> getTileCoordAxis(size[i], tileSize[i], minOverlap[i], padding)).toArray(long[][]::new);
        int totalTiles = Arrays.stream(coordsAxis).mapToInt(a->a.length).reduce(1, (a, b) -> a * b);
        long[][] tileCoords = new long[totalTiles][];
        int tIdx=0;
        switch (size.length)  {
            case 1:
                for (int x = 0; x<coordsAxis[0].length; ++x) {
                    tileCoords[tIdx++] = new long[]{coordsAxis[0][x]};
                }
                break;
            case 2:
                for (int x = 0; x<coordsAxis[0].length; ++x) {
                    for (int y = 0; y<coordsAxis[1].length; ++y) {
                        tileCoords[tIdx++] = new long[]{coordsAxis[0][x], coordsAxis[1][y]};
                    }
                }
                break;
            case 3:
                for (int x = 0; x<coordsAxis[0].length; ++x) {
                    for (int y = 0; y<coordsAxis[1].length; ++y) {
                        for (int z = 0; z<coordsAxis[2].length; ++z) {
                            tileCoords[tIdx++] = new long[]{coordsAxis[0][x], coordsAxis[1][y], coordsAxis[2][z]};
                        }
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("up to 3 axis supported");
        }
        return tileCoords;
    }

}
