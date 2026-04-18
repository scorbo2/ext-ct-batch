package ca.corbett.crypttext.extensions.batch;

import ca.corbett.crypttext.extensions.CryptTextExtension;
import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;

import java.util.List;

public class BatchExtension extends CryptTextExtension {
    private final AppExtensionInfo extInfo;

    public BatchExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(), "/ca/corbett/crypttext/extensions/batch/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("BatchExtension: can't parse extInfo.json from jar resources!");
        }
    }

    /**
     * Returns an AppExtensionInfo instance that describes this extension.
     * This is used by ExtensionManager to display us properly in the extension dialog.
     */
    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    /**
     * We can return an optional list of config properties that will be merged together
     * with the parent application's configuration, and displayed to the user in the config dialog.
     */
    @Override
    protected List<AbstractProperty> createConfigProperties() {
        return List.of(); // nothing yet
    }

    /**
     * Because of the way our extension classloader works, we can only load jar resources
     * in our constructor, or in this method. If we try to load them anywhere else, we
     * will get null, because our classloader has been closed.
     */
    @Override
    protected void loadJarResources() {
        // Nothing to load
    }
}
