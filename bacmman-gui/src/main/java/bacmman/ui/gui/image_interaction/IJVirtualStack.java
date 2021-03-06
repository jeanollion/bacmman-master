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
package bacmman.ui.gui.image_interaction;

import bacmman.configuration.experiment.Experiment;
import bacmman.configuration.experiment.Position;
import bacmman.image.SimpleBoundingBox;
import bacmman.ui.GUI;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import bacmman.image.wrappers.IJImageWrapper;
import bacmman.image.Image;
import static bacmman.image.Image.logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 *
 * @author Jean Ollion
 */
public class IJVirtualStack extends VirtualStack {
    BiFunction<Integer, Integer, Image> imageOpenerCT;
    Function<Integer, int[]> getFCZ;
    int[] FCZCount;
    Image[][] imageCT;
    private final Object lock = new Object();
    public IJVirtualStack(int sizeX, int sizeY, int[] FCZCount, Function<Integer, int[]> getFCZ, BiFunction<Integer, Integer, Image> imageOpenerCT) {
        super(sizeX, sizeY, null, "");
        this.imageOpenerCT=imageOpenerCT;
        this.getFCZ=getFCZ;
        this.FCZCount=FCZCount;
        this.imageCT=new Image[FCZCount[1]][FCZCount[0]];
        for (int n = 0; n<FCZCount[0]*FCZCount[1]*FCZCount[2]; ++n) super.addSlice("");
    }
    
    @Override
    public ImageProcessor getProcessor(int n) {
        int[] fcz = getFCZ.apply(n);
        if (imageCT[fcz[1]][fcz[0]]==null) {
            synchronized(lock) {
                if (imageCT[fcz[1]][fcz[0]]==null) {
                    imageCT[fcz[1]][fcz[0]] = imageOpenerCT.apply(fcz[1], fcz[0]);
                    if (imageCT[fcz[1]][fcz[0]]==null) logger.error("could not open image: channel: {}, frame: {}", fcz[1], fcz[0]);
                }
            }
        }
        if (fcz[2]>= imageCT[fcz[1]][fcz[0]].sizeZ()) {
            if (imageCT[fcz[1]][fcz[0]].sizeZ()==1) fcz[2]=0; // case of reference images 
            else throw new IllegalArgumentException("Wrong Z size for channel: "+fcz[1]);
        }
        return IJImageWrapper.getImagePlus(imageCT[fcz[1]][fcz[0]].getZPlane(fcz[2])).getProcessor();
    }
    public static void openVirtual(Experiment xp, String position, boolean preProcessed) {
        Position f = xp.getPosition(position);
        int channels = xp.getChannelImageCount(preProcessed);
        int frames = f.getFrameNumber(false);
        Image[] bdsC = new Image[xp.getChannelImageCount(preProcessed)];
        for (int c = 0; c<bdsC.length; ++c) bdsC[c]= preProcessed ? xp.getImageDAO().openPreProcessedImage(c, 0, position) : f.getInputImages().getImage(c, 0);
        if (bdsC[0]==null) {
            GUI.log("No "+(preProcessed ? "preprocessed " : "input")+" images found for position: "+position);
            return;
        }
        logger.debug("scale: {}", bdsC[0].getScaleXY());
        // case of reference image with only one Z -> duplicate
        int maxZ = Collections.max(Arrays.asList(bdsC), Comparator.comparingInt(SimpleBoundingBox::sizeZ)).sizeZ();
        int[] fcz = new int[]{frames, channels, maxZ};
        BiFunction<Integer, Integer, Image> imageOpenerCT  = preProcessed ? (c, t) -> xp.getImageDAO().openPreProcessedImage(c, t, position) : (c, t) -> f.getInputImages().getImage(c, t);
        IJVirtualStack s = new IJVirtualStack(bdsC[0].sizeX(), bdsC[0].sizeY(), fcz, IJImageWrapper.getStackIndexFunctionRev(fcz), imageOpenerCT);
        ImagePlus ip = new ImagePlus();
        ip.setTitle((preProcessed ? "PreProcessed Images of position: #" : "Input Images of position: #")+f.getIndex());
        ip.setStack(s, channels,maxZ, frames);
        ip.setOpenAsHyperStack(true);
        Calibration cal = new Calibration();
        cal.pixelWidth=bdsC[0].getScaleXY();
        cal.pixelHeight=bdsC[0].getScaleXY();
        cal.pixelDepth=bdsC[0].getScaleZ();
        ip.setCalibration(cal);
        ip.show();
        ImageWindowManagerFactory.getImageManager().addLocalZoom(ip.getCanvas());
        ImageWindowManagerFactory.getImageManager().addInputImage(position, ip, !preProcessed);
    }
}
