package ca.corbett.crypttext.extensions.batch;

import ca.corbett.crypttext.Version;
import ca.corbett.crypttext.ui.MainWindow;
import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.ScrollUtil;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.progress.SimpleProgressAdapter;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.SwingFormsResources;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.FileField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.PasswordField;
import ca.corbett.forms.fields.ShortTextField;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Presents options to the user for batch-encrypting or batch-decrypting
 * all text files in a given directory, with optional recursion.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class BatchDialog extends JDialog {

    private static final Logger log = Logger.getLogger(BatchDialog.class.getName());
    private static File lastBrowseDirectory = Version.SETTINGS_DIR;

    private MessageUtil messageUtil;
    private FormPanel formPanel;
    private ComboField<String> operationChooser;
    private PasswordField passwordField;
    private FileField directoryField;
    private CheckBoxField recursiveCheckBox;
    private ShortTextField extensionsField;
    private CheckBoxField logErrorsCheckBox;
    private CheckBoxField logSkippedCheckBox;
    private CheckBoxField logSucceededCheckBox;

    public BatchDialog() {
        super(MainWindow.getInstance(), "Batch encrypt/decrypt", true);
        setSize(460, 550);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(MainWindow.getInstance());
        initComponents();
    }

    private void initComponents() {
        formPanel = new FormPanel(Alignment.TOP_LEFT);
        formPanel.setBorderMargin(12);

        // Give the user some context and general info:
        formPanel.add(LabelField.createBoldHeaderLabel("Batch encrypt/decrypt"));
        formPanel.add(new LabelField("<html>Here you can batch-encrypt or batch-decrypt" +
                                             "<br>all text files in a given directory." +
                                             "<br><br>If a file is already in the desired state," +
                                             "<br>it will be skipped. Non-text files are ignored."));

        // Create our fields:
        operationChooser = new ComboField<>("Operation:", List.of("Encrypt", "Decrypt"), 0);
        operationChooser.getMargins().setTop(12);
        formPanel.add(operationChooser);
        passwordField = new PasswordField("Password:", 15);
        passwordField.setAllowBlank(false);
        formPanel.add(passwordField);
        directoryField = new FileField("Directory:", null, 15, FileField.SelectionType.ExistingDirectory);
        directoryField.setButtonIcon(SwingFormsResources.getEditIcon(16));
        directoryField.setFile(lastBrowseDirectory != null && lastBrowseDirectory.exists()
                                       ? lastBrowseDirectory
                                       : Version.SETTINGS_DIR); // revert to default if lastBrowseDirectory is invalid
        formPanel.add(directoryField);
        recursiveCheckBox = new CheckBoxField("Include subdirectories", false);
        formPanel.add(recursiveCheckBox);
        extensionsField = new ShortTextField("Extensions:", 15);
        extensionsField.setText("txt"); // by default, we just look for text files.
        extensionsField.setHelpText("<html>Comma-separated list of file extensions (without dots)." +
                                            "<br>For example: \"txt,md\". Leave blank to include all files.</html>");
        formPanel.add(extensionsField);

        // Logging options:
        logErrorsCheckBox = new CheckBoxField("Log errors", true);
        logErrorsCheckBox.getMargins().setTop(12);
        logErrorsCheckBox.setEnabled(false); // this one should always be enabled
        formPanel.add(logErrorsCheckBox);
        logSkippedCheckBox = new CheckBoxField("Log skipped files (already in desired state)", true);
        formPanel.add(logSkippedCheckBox);
        logSucceededCheckBox = new CheckBoxField("Log successfully processed files", true);
        formPanel.add(logSucceededCheckBox);

        setLayout(new BorderLayout());
        add(ScrollUtil.buildScrollPane(formPanel), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private void buttonHandler(boolean okay) {
        if (okay) {
            if (!formPanel.isFormValid()) {
                // Validation errors are already showing in the form, so we can just return.
                // The dialog stays open until the form is fixed or the user cancels.
                return;
            }

            // Store this directory for next time:
            lastBrowseDirectory = directoryField.getFile();

            // Figure out logging levels:
            Level errorLevel = Level.WARNING; // not configurable
            Level skippedLevel = logSkippedCheckBox.isChecked() ? Level.INFO : null;
            Level succeededLevel = logSucceededCheckBox.isChecked() ? Level.INFO : null;

            // Figure out extensions:
            List<String> extensions = getFileExtensions();

            // Do the batch operation:
            MultiProgressDialog dialog = new MultiProgressDialog(this, "Batch operation in progress");
            BatchThread.Operation op = operationChooser.getSelectedIndex() == 0
                    ? BatchThread.Operation.ENCRYPT
                    : BatchThread.Operation.DECRYPT;
            BatchThread thread = new BatchThread(op, passwordField.getPassword(), directoryField.getFile(),
                                                 recursiveCheckBox.isChecked());
            thread.addProgressListener(new DialogListener(thread));
            thread.setLogLevels(errorLevel, skippedLevel, succeededLevel);
            thread.setExtensions(extensions);
            dialog.runWorker(thread, true);
            return; // Our DialogListener will dispose() if the operation completes.
        }

        dispose();
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton button = new JButton("OK");
        button.setPreferredSize(new Dimension(90, 24));
        button.addActionListener(e -> buttonHandler(true));
        panel.add(button);
        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90, 24));
        button.addActionListener(e -> buttonHandler(false));
        panel.add(button);
        panel.setBorder(BorderFactory.createRaisedBevelBorder());
        return panel;
    }

    /**
     * Makes a best-effort attempt to parse out the list of file extensions provided by the user.
     * We'll remove leading dots if the user ignored our help instructions, and also trim
     * whitespace. If each resulting extension is not blank, we'll include it. This may result
     * in an empty list if the user gave us garbage. In that case, all extensions will be included.
     */
    private List<String> getFileExtensions() {
        List<String> extensions = new ArrayList<>();
        String rawString = extensionsField.getText();
        if (!rawString.isBlank()) {
            String[] extensionsArr = extensionsField.getText().split(",");
            for (String ext : extensionsArr) {
                ext = ext.trim().toLowerCase().replaceAll("^\\.+", ""); // remove leading dots, if any
                if (!ext.isBlank()) {
                    extensions.add(ext);
                }
            }
        }
        return extensions; // Might be empty, which means "include all files regardless of extension"
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, log);
        }
        return messageUtil;
    }

    private class DialogListener extends SimpleProgressAdapter {

        private final BatchThread thread;

        public DialogListener(BatchThread thread) {
            this.thread = thread;
        }

        @Override
        public void progressCanceled() {
            report("Operation canceled",
                   "The operation was canceled by the user." +
                           "\nPartial results are shown below.");
            // Note we DON'T dispose(), in case the user wants to try again.
        }

        @Override
        public void progressComplete() {
            report("Operation completed", "The operation completed successfully.");
            dispose(); // Operation succeeded, so we can close the dialog now.
        }

        private void report(String title, String message) {
            int totalProcessed = thread.getTotalProcessed();
            int errored = thread.getErrored();
            int skipped = thread.getSkipped();
            int succeeded = thread.getSucceeded();
            getMessageUtil().info(title, message + "\n\n" +
                    "Total files processed: " + totalProcessed + "\n" +
                    "Successfully processed: " + succeeded + "\n" +
                    "Errored: " + errored + "\n" +
                    "Skipped (already in desired state): " + skipped);
        }
    }
}
