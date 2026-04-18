package ca.corbett.crypttext.extensions.batch;

import ca.corbett.crypttext.ui.MainWindow;
import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.ScrollUtil;
import ca.corbett.forms.Alignment;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.FileField;
import ca.corbett.forms.fields.LabelField;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.logging.Logger;

/**
 * Presents options to the user for batch-encrypting or batch-decrypting
 * all text files in a given directory, with optional recursion.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class BatchDialog extends JDialog {

    private static final Logger log = Logger.getLogger(BatchDialog.class.getName());

    private MessageUtil messageUtil;
    private FormPanel formPanel;
    private ComboField<String> operationChooser;
    private CheckBoxField recursiveCheckBox;
    private FileField directoryField;

    public BatchDialog() {
        super(MainWindow.getInstance(), "Batch encrypt/decrypt", true);
        setSize(460, 375);
        setResizable(false);
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
        recursiveCheckBox = new CheckBoxField("Include subdirectories", false);
        formPanel.add(recursiveCheckBox);
        directoryField = new FileField("Directory:", null, 15, FileField.SelectionType.ExistingDirectory);
        formPanel.add(directoryField);

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

            // TODO perform the selected operation
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

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, log);
        }
        return messageUtil;
    }
}
