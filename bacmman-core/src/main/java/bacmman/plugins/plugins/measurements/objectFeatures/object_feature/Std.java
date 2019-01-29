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
package bacmman.plugins.plugins.measurements.objectFeatures.object_feature;

import bacmman.data_structure.Region;
import bacmman.plugins.Hint;
import bacmman.plugins.object_feature.IntensityMeasurement;

/**
 *
 * @author Jean Ollion
 */
public class Std extends IntensityMeasurement implements Hint {

    @Override public double performMeasurement(Region object) {
        return core.getIntensityMeasurements(object).sd;
    }

    @Override public String getDefaultName() {
        return "std";
    }

    @Override
    public String getHintText() {
        return "Computed the standard deviation of pixel values within the segmented object";
    }
}
