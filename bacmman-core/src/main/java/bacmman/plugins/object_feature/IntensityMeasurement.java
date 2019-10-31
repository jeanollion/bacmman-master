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
package bacmman.plugins.object_feature;

import bacmman.configuration.parameters.Parameter;
import bacmman.configuration.parameters.PreFilterSequence;
import bacmman.configuration.parameters.ObjectClassParameter;
import bacmman.data_structure.RegionPopulation;
import bacmman.data_structure.SegmentedObject;
import bacmman.image.Image;
import java.util.List;

import bacmman.plugins.plugins.measurements.objectFeatures.object_feature.SimpleObjectFeature;

/**
 *
 * @author Jean Ollion
 */
public abstract class IntensityMeasurement extends SimpleObjectFeature implements ObjectFeatureWithCore {
    protected IntensityMeasurementCore core;
    protected ObjectClassParameter intensity = new ObjectClassParameter("Intensity").setAutoConfiguration(ObjectClassParameter.defaultAutoConfiguration());
    protected Image intensityMap;
    public IntensityMeasurement setIntensityStructure(int structureIdx) {
        this.intensity.setSelectedClassIdx(structureIdx);
        return this;
    }
    public int getIntensityStructure() {
        return this.intensity.getSelectedClassIdx();
    }
    @Override public IntensityMeasurement setUp(SegmentedObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        super.setUp(parent, childStructureIdx, childPopulation);
        this.parent=parent;
        if (intensity.getSelectedIndex()==-1) intensity.setSelectedIndex(childStructureIdx);
        this.intensityMap=parent.getRawImage(intensity.getSelectedIndex());
        if (this.intensity==null) throw new RuntimeException("Could not open raw image of object class "+intensity.getSelectedIndex()+". Maybe experiment structure was modified after pre-processing was run ? ");
        return this;
    }
    
    @Override public Parameter[] getParameters() {return new Parameter[]{intensity};}

    @Override
    public void setUpOrAddCore(List<ObjectFeatureCore> availableCores, PreFilterSequence preFilters) {
        IntensityMeasurementCore existingCore = getCore(availableCores);
        if (existingCore==null) {
            if (core==null) {
                core = new IntensityMeasurementCore();
                core.setUp(intensityMap, parent.getMask(), preFilters);
            }
            if (availableCores!=null) availableCores.add(core);
        } else core=existingCore;
    }
    private IntensityMeasurementCore getCore(List<ObjectFeatureCore> availableCores) {
        if (availableCores==null || availableCores.isEmpty()) return null;
        return availableCores.stream().filter(c -> c instanceof IntensityMeasurementCore).map(c->(IntensityMeasurementCore)c).filter(c->c.getIntensityMap(false)==intensityMap).findAny().orElse(null);
    }
}
