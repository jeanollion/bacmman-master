package bacmman.plugins;

import bacmman.image.Image;

public interface DLengine extends Plugin {
    Image[][] process(Image[][] inputNC);
}
