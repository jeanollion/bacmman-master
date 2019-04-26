package bacmman.data_structure.region_container;

import bacmman.data_structure.Region;
import bacmman.data_structure.SegmentedObject;
import bacmman.data_structure.Spot;
import bacmman.utils.JSONUtils;
import bacmman.utils.geom.Point;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class RegionContainerSpot extends RegionContainer {
    public final static Logger logger = LoggerFactory.getLogger(RegionContainerSpot.class);
    public RegionContainerSpot() {
        super();
    }

    public RegionContainerSpot(SegmentedObject object) {
        super(object);
    }

    @Override
    public Region getRegion() {
        return new Spot(new Point(JSONUtils.fromFloatArray((List)structureObject.getAttributes().get("Center"))),
                ((Number)structureObject.getAttributes().get("Radius")).doubleValue(),
                ((Number)structureObject.getAttributes().get("Intensity")).doubleValue(),
                structureObject.getIdx() + 1, is2D, structureObject.getScaleXY(), structureObject.getScaleZ());
    }

    @Override
    public JSONObject toJSON() {
        JSONObject res = super.toJSON();
        res.put("Type", ANALYTICAL_TYPES.SPHERE.name());
        return res;
    }
}