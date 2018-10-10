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
package bacmman.plugins.plugins.track_post_filter;

import bacmman.configuration.parameters.BoundedNumberParameter;
import bacmman.configuration.parameters.EnumChoiceParameter;
import bacmman.configuration.parameters.Parameter;
import bacmman.data_structure.*;
import bacmman.plugins.Hint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import bacmman.plugins.TrackPostFilter;
import static bacmman.plugins.plugins.track_post_filter.PostFilter.MERGE_POLICY_TT;

import java.util.function.BiPredicate;

/**
 *
 * @author Jean Ollion
 */
public class RemoveTracksStartingAfterFrame implements TrackPostFilter, Hint {
    
    BoundedNumberParameter startFrame = new BoundedNumberParameter("Maximum starting frame", 0, 0, 0, null);
    EnumChoiceParameter<PostFilter.MERGE_POLICY> mergePolicy = new EnumChoiceParameter<>("Merge Policy", PostFilter.MERGE_POLICY.values(), PostFilter.MERGE_POLICY.ALWAYS_MERGE, false).setHint(MERGE_POLICY_TT);
    Parameter[] parameters = new Parameter[]{startFrame, mergePolicy};
    
    @Override
    public String getHintText() {
        return "Remove tracks that start after a user-defined frame";
    }
    
    public RemoveTracksStartingAfterFrame() {}
    
    public RemoveTracksStartingAfterFrame setMergePolicy(PostFilter.MERGE_POLICY policy) {
        mergePolicy.setSelectedEnum(policy);
        return this;
    }
    
    public RemoveTracksStartingAfterFrame(int startFrame) {
        this.startFrame.setValue(startFrame);
    }
    @Override
    public void filter(int structureIdx, List<SegmentedObject> parentTrack, SegmentedObjectFactory factory, TrackLinkEditor editor) {
        int start = startFrame.getValue().intValue();
        List<SegmentedObject> objectsToRemove = new ArrayList<>();
        Map<SegmentedObject, List<SegmentedObject>> allTracks = SegmentedObjectUtils.getAllTracks(parentTrack, structureIdx);
        for (Entry<SegmentedObject, List<SegmentedObject>> e : allTracks.entrySet()) {
            if (e.getKey().getFrame()>start) objectsToRemove.addAll(e.getValue());
        }
        //logger.debug("remove track trackLength: #objects to remove: {}", objectsToRemove.size());
        BiPredicate<SegmentedObject, SegmentedObject> mergePredicate = mergePolicy.getSelectedEnum().mergePredicate;
        if (!objectsToRemove.isEmpty()) StructureObjectEditor.deleteObjects(null, objectsToRemove, mergePredicate, factory, editor);
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    
    
}