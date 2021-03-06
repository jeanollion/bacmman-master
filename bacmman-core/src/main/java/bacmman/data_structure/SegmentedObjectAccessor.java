package bacmman.data_structure;

import bacmman.configuration.experiment.Experiment;
import bacmman.data_structure.dao.ObjectDAO;
import bacmman.data_structure.region_container.RegionContainer;
import bacmman.image.BlankMask;
import bacmman.image.Image;
import bacmman.plugins.Measurement;
import bacmman.utils.JSONUtils;
import org.json.simple.JSONObject;

import java.util.Collection;
import java.util.List;

public class SegmentedObjectAccessor {
    SegmentedObjectAccessor() {

    }

    // constructor

    public SegmentedObject createFromJSON(String s) {
        JSONObject jo = JSONUtils.parse(s);
        return new SegmentedObject(jo);
    }

    public SegmentedObject duplicate(SegmentedObject o) {
        return o.duplicate();
    }
    public SegmentedObject duplicate(SegmentedObject o, boolean generateNewID, boolean duplicateObject, boolean duplicateImages) {
        return o.duplicate(generateNewID, duplicateObject, duplicateImages);
    }
    public SegmentedObject createRoot(int timePoint, BlankMask mask, ObjectDAO dao) {
        return new SegmentedObject(timePoint, mask, dao);
    }

    // structure access
    public boolean hasRegionContainer(SegmentedObject o) {
        return o.hasRegionContainer();
    }
    public RegionContainer getRegionContainer(SegmentedObject o) {
        return o.getRegionContainer();
    }

    public String trackHeadId(SegmentedObject o) {
        return o.trackHeadId;
    }

    public ObjectDAO getDAO(SegmentedObject o) {
        return o.getDAO();
    }

    public List<SegmentedObject> getDirectChildren(SegmentedObject o, int objectClassIdx) {
        return o.getDirectChildren(objectClassIdx);
    }
    public Experiment getExperiment(SegmentedObject o) {
        return o.getExperiment();
    }
    // structure modifiers

    public void setDAO(SegmentedObject o, ObjectDAO dao) {
        o.setDAO(dao);
    }

    public void setIdx(SegmentedObject o, int newIdx) {
        o.setIdx(newIdx);
    }

    public void relabelChildren(SegmentedObject o, int objectClassIdx, Collection<SegmentedObject> modifiedObjectsStore) {
        o.relabelChildren(objectClassIdx, modifiedObjectsStore);
    }
    public void setTrackHead(SegmentedObject o, SegmentedObject trackHead, boolean resetPrevious, boolean propagate) {
        o.setTrackHead(trackHead, resetPrevious, propagate, null);
    }
    public void setRawImage(SegmentedObject o, int objectClassIdx, Image image) {
        o.setRawImage(objectClassIdx, image);
    }
    public void setPreFilteredImage(SegmentedObject o, int objectClassIdx, Image image) {
        o.setPreFilteredImage(image, objectClassIdx);
    }

    public void setChildren(SegmentedObject o, List<SegmentedObject> children, int objectClassIdx) {
        o.setChildren(children, objectClassIdx);
    }

    public void flushImages(SegmentedObject o) {
        o.flushImages();
    }

    public void updateRegionContainer(SegmentedObject o) {
        o.updateRegionContainer();
    }

    // track modifiers


    // measurements
    public void setMeasurements(SegmentedObject o,  Measurements m) {
        o.setMeasurements(m);
    }

}
