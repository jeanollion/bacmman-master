package bacmman.plugins.plugins.scalers;

import bacmman.configuration.parameters.BoundedNumberParameter;
import bacmman.configuration.parameters.Parameter;
import bacmman.image.Histogram;
import bacmman.image.HistogramFactory;
import bacmman.image.Image;
import bacmman.image.TypeConverter;
import bacmman.plugins.Hint;
import bacmman.plugins.HistogramScaler;
import bacmman.processing.ImageOperations;

public class ModeScaler implements HistogramScaler, Hint {
    Histogram histogram;
    double center;
    BoundedNumberParameter range = new BoundedNumberParameter("Range", 3,  0, 0.001, null).setEmphasized(true).setHint("Values will be transformed: I -> ( I - mode ) / range");
    boolean transformInputImage = false;
    @Override
    public void setHistogram(Histogram histogram) {
        this.histogram = histogram;
        this.center = histogram.getMode();
        logger.debug("ModePercentile scaler: center: {}, range: {}", center, range.getValue().doubleValue());
    }

    @Override
    public Image scale(Image image) {
        if (isConfigured()) return ImageOperations.affineOperation2(image, transformInputImage? TypeConverter.toFloat(image, null, false):null, 1./ range.getValue().doubleValue(), -center);
        else { // perform on single image
            double center = HistogramFactory.getHistogram(()->image.stream(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS).getMode(); // TODO smooth ?
            return ImageOperations.affineOperation2(image, transformInputImage?TypeConverter.toFloat(image, null, false):null, 1./ range.getValue().doubleValue(), -center);
        }
    }

    @Override
    public Image reverseScale(Image image) {
        if (isConfigured()) return ImageOperations.affineOperation(image, transformInputImage?TypeConverter.toFloat(image, null, false):null, range.getValue().doubleValue(), center);
        else throw new RuntimeException("Cannot Reverse Scale if scaler is not configured");
    }

    @Override
    public ModeScaler transformInputImage(boolean transformInputImage) {
        this.transformInputImage = transformInputImage;
        return this;
    }
    @Override
    public boolean isConfigured() {
        return histogram != null;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[] {range};
    }

    @Override
    public String getHintText() {
        return "Scales image values by the formula I = ( I - mode) / range";
    }
}
