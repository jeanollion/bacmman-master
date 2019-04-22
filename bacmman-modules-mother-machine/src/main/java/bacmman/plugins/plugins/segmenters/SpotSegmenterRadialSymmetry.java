/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package bacmman.plugins.plugins.segmenters;

import bacmman.configuration.parameters.BoundedNumberParameter;
import bacmman.configuration.parameters.NumberParameter;
import bacmman.configuration.parameters.Parameter;
import bacmman.core.Core;
import bacmman.data_structure.Region;
import bacmman.data_structure.RegionPopulation;
import bacmman.data_structure.SegmentedObject;
import bacmman.data_structure.SegmentedObjectUtils;
import bacmman.image.*;
import bacmman.plugins.*;
import bacmman.plugins.plugins.manual_segmentation.WatershedObjectSplitter;
import bacmman.plugins.plugins.pre_filters.FastRadialSymmetryTransform;
import bacmman.plugins.plugins.thresholders.BackgroundThresholder;
import bacmman.processing.*;
import bacmman.processing.neighborhood.ConicalNeighborhood;
import bacmman.processing.neighborhood.CylindricalNeighborhood;
import bacmman.processing.neighborhood.Neighborhood;
import bacmman.processing.watershed.MultiScaleWatershedTransform;
import bacmman.processing.watershed.WatershedTransform;
import bacmman.utils.StreamConcatenation;
import bacmman.utils.Utils;
import bacmman.utils.geom.Point;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static bacmman.processing.watershed.WatershedTransform.watershed;

/**
 *
 * @author Jean Ollion
 */
public class SpotSegmenterRadialSymmetry implements Segmenter, TrackConfigurable<SpotSegmenterRadialSymmetry>, ManualSegmenter, ObjectSplitter, TestableProcessingPlugin, Hint, HintSimple {
    public static boolean debug = false;
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 1.5, 1, 5).setHint("Scale (in pixels) for gaussian smooth <br />Configuration hint: determines the <em>Gaussian</em> image displayed in test mode");
    NumberParameter minSpotSize = new BoundedNumberParameter("Min. Spot Size", 0, 5, 1, null).setHint("Spots under this size (in voxel number) will be removed");
    NumberParameter thresholdHigh = new NumberParameter<>("Radial Symmetry Seed Threshold", 2, 0.3).setEmphasized(true).setHint("Radial Symmetry threshold for selection of watershed seeds.<br />Higher values tend to increase false negative detections and decrease false positive detection.<br /> Configuration hint: refer to the <em>Radial Symmetry</em> image displayed in test mode"); // was 2.25
    NumberParameter thresholdLow = new NumberParameter<>("Propagation Threshold", 2, 0.2).setEmphasized(true).setHint("Radial Symmetry threshold for watershed propagation: watershed propagation stops at this value. <br />Lower value will yield larger spots.<br />Configuration hint: refer to <em>Radial Symmetry</em> image displayed in test mode");
    NumberParameter intensityThreshold = new NumberParameter<>("Seed Threshold", 2, 1.2).setEmphasized(true).setHint("Gaussian threshold for selection of watershed seeds.<br /> Higher values tend to increase false negative detections and decrease false positive detections.<br />Configuration hint: refer to <em>Gaussian</em> image displayed in test mode"); // was 1.6
    Parameter[] parameters = new Parameter[]{smoothScale, minSpotSize, thresholdHigh,  thresholdLow, intensityThreshold};
    ProcessingVariables pv = new ProcessingVariables();
    boolean planeByPlane = false;
    protected static String toolTipAlgo = "<br /><br /><em>Algorithmic Details</em>:<ul>"
            + "<li>Spots are detected using a seeded watershed algorithm applied on the Radial Symmetry transform.</li> "
            + "<li>Seeds are set on the regional maxima of the Radial Symmetry transform, within the mask of the segmentation parent. Selected seeds have a Radial Symmetry value larger than <em>Radial Symmetry Seed Threshold</em> and a Gaussian value superior to <em>Seed Threshold</em></li>"
            + "<li>If several scales are provided, the Radial Symmetry scale-space will be computed (3D for 2D input, and 4D for 3D input) and the seeds will be 3D/4D local extrema in the scale space in order to determine at the same time their scale and spatial localization</li>"
            + "<li>Watershed propagation is done within the segmentation parent mask until Radial Symmetry values reach the threshold defined in the <em>Propagation Threshold</em> parameter</li>"
            + "<li>A quality parameter defined as √(Radial Symmetry x Gaussian) at the center of the spot is computed (used in <em>NestedSpotTracker</em>)</li></ul>" +
            "<br />In order to increase robustness to variations in the background fluorescence in bacteria, the input image is first normalized by subtracting the mean value and dividing by the standard-deviation value of the background signal within the cell. Radial Symmetry & Gaussian transforms are then computed on the normalized image.";
    protected static String toolTipDispImage = "<br /><br />Images displayed in test mode:" +
            "<ul><li><em>Gaussian</em>: Gaussian transform applied to the normalized input image.<br />This image can be used to tune the <em>Seed Threshold</em> parameter, which should be lower than the intensity of the center of the spots and larger than the background intracellular fluorescence on the <em>Gaussian</em> transformed image.</li>" +
            "<li><em>Radial Symmetry</em>: Radial Symmetry transform applied to the normalized input image.<br />This image can be used to tune the <em>Radial Symmetry Seed Threshold</em> parameter, which should be lower than the intensity of the center of the spots and larger than the background intracellular fluorescence on the <em>Radial Symmetry</em> transformed image.<br />This image can also be used to tune the <em>Propagation Threshold</em> parameter, which value should be lower than the intensity inside the spots and larger than the background intracellular fluorescence on the <em>Radial Symmetry</em> transformed image</li>";
    protected static String toolTipDispImageAdvanced = "<li><em>Seeds</em>: Selected seeds for the seeded-watershed transform</li></ul>";
    protected static String toolTipSimple ="<b>Fluorescence Spot Detection</b>.<br />" +
            "Segments spot-like objects in fluorescence images using a criterion on the Gaussian and the Radial Symmetry transforms. <br />If spot detection is not satisfying try changing the <em>Seed Threshold</em> and/or <em>Radial Symmetry Seed Threshold</em>. If spots are too big or too small, try changing the <em>Propagation Threshold</em> parameter. ";

    // tool tip interface
    @Override
    public String getHintText() {
        return toolTipSimple+toolTipAlgo+toolTipDispImage+toolTipDispImageAdvanced;
    }

    @Override
    public String getSimpleHintText() {
        return toolTipSimple + toolTipDispImage+ "</ul>";
    }

    public SpotSegmenterRadialSymmetry() {}

    public SpotSegmenterRadialSymmetry(double thresholdSeeds, double thresholdPropagation, double thresholdIntensity) {
        this.intensityThreshold.setValue(thresholdIntensity);
        this.thresholdHigh.setValue(thresholdSeeds);
        this.thresholdLow.setValue(thresholdPropagation);
    }
    
    public SpotSegmenterRadialSymmetry setThresholdSeeds(double threshold) {
        this.thresholdHigh.setValue(threshold);
        return this;
    }
    
    public SpotSegmenterRadialSymmetry setThresholdPropagation(double threshold) {
        //this.thresholdLow.setPlugin(new ConstantValue(threshold));
        this.thresholdLow.setValue(threshold);
        return this;
    }
    
    public SpotSegmenterRadialSymmetry setIntensityThreshold(double threshold) {
        this.intensityThreshold.setValue(threshold);
        return this;
    }

    /**
     * See {@link #run(Image, SegmentedObject, int, double, double, double)}
     * @param input
     * @param objectClassIdx
     * @param parent
     * @return 
     */
    @Override
    public RegionPopulation runSegmenter(Image input, int objectClassIdx, SegmentedObject parent) {
        return run(input, parent, minSpotSize.getValue().intValue(), thresholdHigh.getValue().doubleValue(), thresholdLow.getValue().doubleValue(), intensityThreshold.getValue().doubleValue());
    }
    // testable
    Map<SegmentedObject, TestDataStore> stores;
    @Override public void setTestDataStore(Map<SegmentedObject, TestDataStore> stores) {
        this.stores=  stores;
    }

    private static class ProcessingVariables {
        Image input;
        Image smooth, radialSymmetry;
        boolean symScaled, smoothScaled;
        double[] ms;
        double smoothScale;
        public void initPV(Image input, ImageMask mask, double smoothScale) {
            this.input=input;
            this.smoothScale=smoothScale;
            if (ms == null) {
                //BackgroundFit.debug=debug;
                ms = new double[2];
                //double thld = BackgroundFit.backgroundFit(HistogramFactory.getHistogram(()->input.stream(mask, true), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS), 5, ms);
                double thld = BackgroundThresholder.runThresholder(input, mask, 6, 6, 2, Double.MAX_VALUE, ms); // more robust than background fit because too few values to make histogram
                if (debug) logger.debug("scaling thld: {} mean & sigma: {}", thld, ms); //if (debug) 
            }
        }
        public Image getScaledInput() {
            return ImageOperations.affineOperation2WithOffset(input, null, 1/ms[1], -ms[0]).setName("Scaled Input");
        }
        protected Image getSmoothedMap() {
            if (smooth==null) throw new RuntimeException("Smooth map not initialized");
            if (!smoothScaled) {
                if (!smooth.sameDimensions(input)) smooth = smooth.cropWithOffset(input.getBoundingBox()); // map was computed on parent that differs from segmentation parent
                ImageOperations.affineOperation2WithOffset(smooth, smooth, smoothScale/ms[1], -ms[0]);
                smoothScaled=true;
            }
            return smooth;
        }
        
        protected Image getRadialSymmetryMap() {
            if (radialSymmetry==null) throw new RuntimeException("Radial Symmetry map not initialized");
            if (!symScaled) {
                if (!radialSymmetry.sameDimensions(input)) radialSymmetry = radialSymmetry.cropWithOffset(input.getBoundingBox()); // map was computed on parent that differs from segmentation parent
                ImageOperations.affineOperation2WithOffset(radialSymmetry, radialSymmetry, 1/ms[1], 0);
                symScaled =true;
            }
            return radialSymmetry;
        }
    }
    /**
     * Spots are detected using a seeded watershed algorithm in the laplacian transform
     * Input image is scaled by removing the mean value and dividing by the standard-deviation value of the background within the segmentation parent
     * Seeds are set on regional maxima of the laplacian transform, within the mask of {@param parent}, with laplacian value superior to {@param thresholdSeeds} and gaussian value superior to {@param intensityThreshold}
     * If several scales are provided, the laplacian scale space will be computed (3D for 2D input, and 4D for 3D input) and the seeds will be 3D/4D local extrema in the scale space in order to determine at the same time their scale and spatial localization
     * Watershed propagation is done within the mask of {@param parent} until laplacian values reach {@param thresholdPropagation}
     * A quality parameter in computed as √(laplacian x gaussian) at the center of the spot
     * @param input pre-diltered image from wich spots will be detected
     * @param parent segmentation parent
     * @param minSpotSize under this size spots will be erased
     * @param thresholdSeeds minimal laplacian value to segment a spot
     * @param thresholdPropagation laplacian value at the border of spots
     * @param intensityThreshold minimal gaussian value to semgent a spot
     * @return segmented spots
     */
    public RegionPopulation run(Image input, SegmentedObject parent, int minSpotSize, double thresholdSeeds, double thresholdPropagation, double intensityThreshold) {
        ImageMask parentMask = parent.getMask().sizeZ()!=input.sizeZ() ? new ImageMask2D(parent.getMask()) : parent.getMask();
        if (this.parentSegTHMapmeanAndSigma!=null) pv.ms = parentSegTHMapmeanAndSigma.get(((SegmentedObject)parent).getTrackHead());
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        if (pv.smooth==null || pv.getRadialSymmetryMap()==null) throw new RuntimeException("Mutation Segmenter not parametrized");//setMaps(computeMaps(input, input));
        
        Image smooth = pv.getSmoothedMap();
        Image radSym = pv.getRadialSymmetryMap();
        Neighborhood n = new CylindricalNeighborhood(1.5, 1, false);
        ImageByte seeds = Filters.localExtrema(radSym, null, true, parent.getMask(), n);
        BoundingBox.loop(new SimpleBoundingBox(seeds).resetOffset(),
                (x, y, z)->seeds.setPixel(x, y, z, 0),
                (x, y, z)->seeds.insideMask(x, y, z) && (smooth.getPixel(x, y, z)<intensityThreshold || radSym.getPixel(x, y, z)<thresholdSeeds));

        WatershedTransform.WatershedConfiguration config = new WatershedTransform.WatershedConfiguration()
                .decreasingPropagation(true)
                .propagationCriterion(new WatershedTransform.ThresholdPropagationOnWatershedMap(thresholdPropagation));

        RegionPopulation pop =  watershed(radSym, parent.getMask(), seeds, config);

        SubPixelLocalizator.debug=debug;
        setCenterAndQuality(radSym, smooth, pop); // TODO voir si en 3D pas mieux avec gaussian

        if (stores!=null) {
            logger.debug("Parent: {}: Q: {}", parent, Utils.toStringList(pop.getRegions(), o->""+o.getQuality()));
            logger.debug("Parent: {}: C: {}", parent ,Utils.toStringList(pop.getRegions(), o->""+o.getCenter()));
        }
        pop.filter(new RegionPopulation.RemoveFlatObjects(false));
        pop.filter(new RegionPopulation.Size().setMin(minSpotSize));
        if (stores!=null) {
            stores.get(parent).addIntermediateImage("Gaussian", smooth);
            stores.get(parent).addIntermediateImage("RadialSymmetryTransform", radSym);
        }
        return pop;
    }
    
    private static void setCenterAndQuality(Image map, Image map2, RegionPopulation pop) {
        SubPixelLocalizator.setSubPixelCenter(map, pop.getRegions(), true); // lap -> better in case of close objects
        for (Region o : pop.getRegions()) { // quality criterion : sqrt (smooth * lap)
            if (o.getQuality()==0 || o.getCenter()==null) o.setCenter( o.getMassCenter(map, false)); // localizator didnt work -> use center of mass
            o.getCenter().ensureWithinBounds(map.getBoundingBox().resetOffset());
            double zz = o.getCenter().numDimensions()>2?o.getCenter().get(2):0;
            o.setQuality(Math.sqrt(map.getPixel(o.getCenter().get(0), o.getCenter().get(1), zz) * map2.getPixel(o.getCenter().get(0), o.getCenter().get(1), zz)));
        }
    }



    private static <T extends Image<T>> List<T> arrangeSpAndZPlanes(T[] spZ, boolean ZbyZ) {
        if (ZbyZ) {
            List<T> res = new ArrayList<>(spZ.length * spZ[0].sizeZ());
            for (int z = 0; z<spZ.length; ++z) {
                for (int sp = 0; sp<spZ[z].sizeZ(); ++sp) {
                    res.add(spZ[z].getZPlane(sp));
                }
            }
            return res;
        } else return Image.mergeImagesInZ(Arrays.asList(spZ));
    }
    
    public void printSubLoc(String name, Image locMap, Image smooth, Image lap, RegionPopulation pop, MutableBoundingBox globBound) {
        MutableBoundingBox b = locMap.getBoundingBox().translate(globBound.reverseOffset());
        List<Region> objects = pop.getRegions();
        
        for(Region o : objects) o.setCenter(o.getMassCenter(locMap, false));
        pop.translate(b, false);
        logger.debug("mass center: centers: {}", Utils.toStringList(objects, o -> o.getCenter()+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        
        for(Region o : objects) o.setCenter(o.getGeomCenter(false));
        pop.translate(b, false);
        logger.debug("geom center {}: centers: {}", name, Utils.toStringList(objects, o -> o.getCenter()+" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        
        
        SubPixelLocalizator.setSubPixelCenter(locMap, objects, true);
        pop.translate(b, false);
        logger.debug("locMap: {}, centers: {}", name, Utils.toStringList(objects, o ->  o.getCenter() +" value: "+o.getQuality()));
        pop.translate(b.duplicate().reverseOffset(), false);
        logger.debug("smooth values: {}", Utils.toStringList(objects, o->""+smooth.getPixel(o.getCenter().get(0), o.getCenter().get(1), o.getCenter().getWithDimCheck(2))) );
        logger.debug("lap values: {}", Utils.toStringList(objects, o->""+lap.getPixel(o.getCenter().get(0), o.getCenter().get(1), o.getCenter().getWithDimCheck(2))) );
        
        
        
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    public void setQuality(List<Region> objects, Offset offset, Image input, ImageMask parentMask) {
        if (objects.isEmpty()) return;
        if (offset==null) offset = new MutableBoundingBox();
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        for (Region o : objects) {
            Point center = o.getCenter().duplicate().translateRev(offset);
            if (center==null) throw new IllegalArgumentException("No center for object: "+o);
            double smooth = pv.getSmoothedMap().getPixel(center.get(0), center.get(1), center.getWithDimCheck(2));
            double rad = pv.getRadialSymmetryMap().getPixel(center.get(0), center.get(1), center.getWithDimCheck(2));
            o.setQuality(Math.sqrt(smooth * rad));
            //logger.debug("object: {} smooth: {} lap: {} q: {}", o.getCenter(), smooth, Collections.max(lapValues), o.getQuality());
        }
    }
    
    
    

    protected boolean verboseManualSeg;
    public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }
    
    @Override
    public RegionPopulation manualSegment(Image input, SegmentedObject parent, ImageMask segmentationMask, int objectClassIdx, List<int[]> seedsXYZ) {
        ImageMask parentMask = parent.getMask().sizeZ()!=input.sizeZ() ? new ImageMask2D(parent.getMask()) : parent.getMask();
        this.pv.initPV(input, parentMask, smoothScale.getValue().doubleValue()) ;
        if (pv.smooth==null || pv.radialSymmetry==null) setMaps(computeMaps(input, input));
        else logger.debug("manual seg: maps already set!");
        List<Region> seedObjects = RegionFactory.createSeedObjectsFromSeeds(seedsXYZ, input.sizeZ()==1, input.getScaleXY(), input.getScaleZ());
        Image radialSymmetryMap = pv.getRadialSymmetryMap();
        Image smooth = pv.getSmoothedMap();
        WatershedTransform.WatershedConfiguration config = new WatershedTransform.WatershedConfiguration().decreasingPropagation(true).propagationCriterion(new WatershedTransform.ThresholdPropagationOnWatershedMap(this.thresholdLow.getValue().doubleValue())).fusionCriterion(new WatershedTransform.SizeFusionCriterion(minSpotSize.getValue().intValue())).lowConectivity(false);
        RegionPopulation pop =  watershed(radialSymmetryMap, parentMask, seedObjects, config);
        setCenterAndQuality(radialSymmetryMap, smooth, pop);
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            Core.showImage(seedMap);
            Core.showImage(radialSymmetryMap.setName("Radial Symmetry (watershedMap). "));
            Core.showImage(smooth.setName("Smmothed Scale: "+smoothScale.getValue().doubleValue()));
            Core.showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        return pop;
    }

    @Override
    public RegionPopulation splitObject(SegmentedObject parent, int structureIdx, Region object) {
        Image input = parent.getPreFilteredImage(structureIdx);
        Image wsMap = FastRadialSymmetryTransformUtil.runTransform(input, new double[]{3, 4, 5, 6}, FastRadialSymmetryTransformUtil.fluoSpotKappa, false, FastRadialSymmetryTransformUtil.GRADIENT_SIGN.POSITIVE_ONLY, 1.5, 1, 0);
        wsMap = object.isAbsoluteLandMark() ? wsMap.cropWithOffset(object.getBounds()) : wsMap.crop(object.getBounds());
        RegionPopulation res =  WatershedObjectSplitter.splitInTwo(wsMap, object.getMask(), true, true, manualSplitVerbose);
        res.translate(object.getBounds(), object.isAbsoluteLandMark());
        return res;
    }

    boolean manualSplitVerbose;
    @Override
    public void setSplitVerboseMode(boolean verbose) {
        manualSplitVerbose=verbose;
    }

    // track parametrizable
    /**
     * Compute Maps on parent image 
     * {@link #runSegmenter(Image, int, SegmentedObject) } is supposed to be called from bacteria, thus to avoid border effect gaussian smooth and laplacian transform should be computed on microchannel images
     * @param structureIdx
     * @param parentTrack
     * @return 
     */
    @Override
    public TrackConfigurer<SpotSegmenterRadialSymmetry> run(int structureIdx, List<SegmentedObject> parentTrack) {
        Map<SegmentedObject, Image[]> parentMapImages = parentTrack.stream().parallel().collect(Collectors.toMap(p->p, p->computeMaps(p.getRawImage(structureIdx), p.getPreFilteredImage(structureIdx))));
        // get scaling per segmentation parent track
        int segParent = parentTrack.iterator().next().getExperimentStructure().getSegmentationParentObjectClassIdx(structureIdx);
        int parentIdx = parentTrack.iterator().next().getStructureIdx();
        Map<SegmentedObject, List<SegmentedObject>> segParentTracks = SegmentedObjectUtils.getAllTracks(parentTrack, segParent);
        Function<List<SegmentedObject>, DoubleStream> valueStream = t -> {
            DoubleStream[] ds = t.stream().map(so-> so.getParent(parentIdx).getPreFilteredImage(structureIdx).stream(so.getMask(), true)).toArray(s->new DoubleStream[s]);
            return StreamConcatenation.concat(ds);
        };
        /*
        // compute background per track -> not very effective because background can vary within track. To do -> sliding mean ?
        Map<SegmentedObject, double[]> parentSegTHMapmeanAndSigma = segParentTracks.values().stream().parallel().collect(Collectors.toMap(t->t.get(0), t -> {
            //DoubleStatistics ds = DoubleStatistics.getStats(valueStream.apply(t));
            //logger.debug("track: {}: mean: {}, sigma: {}", t.get(0), ds.getAverage(), ds.getStandardDeviation());
            //return new double[]{ds.getAverage(), ds.getStandardDeviation()}; // get mean & std 
            // TEST  backgroundFit / background thlder
            double[] ms = new double[2];
            if (t.size()>2) {
                Histogram histo = HistogramFactory.getHistogram(()->valueStream.apply(t), HistogramFactory.BIN_SIZE_METHOD.AUTO);
                try {
                    BackgroundFit.backgroundFit(histo, 0, ms);
                } catch(Throwable e) { }
                if (stores!=null && t.get(0).getFrame()==0) {
                    histo.plotIJ1("values of track: "+t.get(0)+" (length: "+t.size()+ " total values: "+valueStream.apply(t).count()+")" + " mean: "+ms[0]+ " std: "+ms[1], true);
                }
            }
            if (ms[1]==0) {
                DoubleStatistics ds = DoubleStatistics.getStats(valueStream.apply(t));
                ms[0] = ds.getAverage();
                ms[1] = ds.getStandardDeviation();
            }
            return ms;
        }));
        */
        return (p, s) -> {
            //s.parentSegTHMapmeanAndSigma = parentSegTHMapmeanAndSigma;
            s.setMaps(parentMapImages.get(p));
        };
    }
    Map<SegmentedObject, double[]> parentSegTHMapmeanAndSigma;
    protected Image[] computeMaps(Image rawSource, Image filteredSource) {
        double smoothScale = this.smoothScale.getValue().doubleValue();
        Image[] maps = new Image[2];
        Function<Image, Image> gaussF = f->ImageFeatures.gaussianSmooth(f, smoothScale, false).setName("gaussian: "+smoothScale);
        maps[0] = planeByPlane ? ImageOperations.applyPlaneByPlane(filteredSource, gaussF) : gaussF.apply(filteredSource); //
        Function<Image, Image> symF = f->FastRadialSymmetryTransformUtil.runTransform(f, new double[]{3, 4, 5, 6}, FastRadialSymmetryTransformUtil.fluoSpotKappa, false, FastRadialSymmetryTransformUtil.GRADIENT_SIGN.POSITIVE_ONLY, 1.5, 1, 0);
        maps[1] = ImageOperations.applyPlaneByPlane(filteredSource, symF);
        return maps;
    }
    
    
    protected void setMaps(Image[] maps) {
        if (maps==null) return;
        if (maps.length!=2) throw new IllegalArgumentException("Maps should be of length 2");
        this.pv.smooth=maps[0];
        this.pv.radialSymmetry=maps[1];
    }
    
}