package ca.corbett.crypttext.extensions.batch;

import ca.corbett.extras.EnhancedAction;

import java.awt.event.ActionEvent;

/**
 * A simple action for launching our BatchDialog.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class BatchDialogAction extends EnhancedAction {
    public BatchDialogAction() {
        super("Batch encrypt/decrypt");
        setTooltip("Launch the batch encrypt/decrypt dialog");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BatchDialog dialog = new BatchDialog();
        dialog.setVisible(true);
    }
}
