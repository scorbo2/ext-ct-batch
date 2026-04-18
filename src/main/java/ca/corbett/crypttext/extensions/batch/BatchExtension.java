package ca.corbett.crypttext.extensions.batch;

import ca.corbett.crypttext.AppConfig;
import ca.corbett.crypttext.extensions.CryptTextExtension;
import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;

import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;

public class BatchExtension extends CryptTextExtension {
    private final AppExtensionInfo extInfo;
    private final BatchDialogAction dialogAction;

    public BatchExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(), "/ca/corbett/crypttext/extensions/batch/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("BatchExtension: can't parse extInfo.json from jar resources!");
        }
        dialogAction = new BatchDialogAction();
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
        List<AbstractProperty> props = new ArrayList<>();
        props.add(new KeyStrokeProperty(AppConfig.KEYSTROKE_PREFIX + "Batch.batchDialogKey",
                                        "Show batch dialog:",
                                        KeyStrokeManager.parseKeyStroke("Ctrl+B"), // default value
                                        dialogAction)); // use a single action instance; its accelerator is auto-updated
        return props;
    }

    /**
     * We will contribute a menu item in the "Crypt" menu to launch our batch dialog.
     *
     * @param topLevelMenu The top-level menu being built. We only care about Crypt.
     * @return A list of menu items to insert into the given menu, or null/empty list for none.
     */
    @Override
    public List<JMenuItem> getMenuItems(String topLevelMenu) {
        List<JMenuItem> items = new ArrayList<>();
        if ("Crypt".equals(topLevelMenu)) {
            items.add(new JMenuItem(dialogAction)); // re-use our dialog action so the accelerator is always correct
        }
        return items;
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
