import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Screen for the final verification stage: Scanning physical RFID tags.
 * Displays required tag numbers, checkpoints, and current match validation feedback.
 */
public class RFIDGamePanel extends GameView {

    private static final long serialVersionUID = 1L;

    // UI elements styled with the project theme
    private final JLabel requiredTagValue = UiTheme.value("Waiting...", 50);
    private final JLabel checkpointValue = UiTheme.value("Checkpoint", 28);
    private final JLabel statusValue = UiTheme.value("Scan the required RFID tag", 28);
    private final JLabel detailValue = UiTheme.subtitle(
            "Place the matching RFID tag on the reader and hold it steady."
    );

    // Constructs layout boundaries, centering, and spacings
    public RFIDGamePanel() {

        setLayout(new BorderLayout(18, 18));
        setBackground(UiTheme.BG);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Top header section
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        heading.add(UiTheme.title("RFID Verification"));
        heading.add(Box.createVerticalStrut(6));
        heading.add(UiTheme.subtitle("Scan the correct RFID tag to unlock the next stage."));

        // Central visual card displaying main instructions and feedback status
        JPanel center = UiTheme.card();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setPreferredSize(new Dimension(760, 420));

        requiredTagValue.setAlignmentX(Component.CENTER_ALIGNMENT);
        checkpointValue.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusValue.setAlignmentX(Component.CENTER_ALIGNMENT);
        detailValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(Box.createVerticalGlue());
        center.add(requiredTagValue);
        center.add(Box.createVerticalStrut(22));
        center.add(checkpointValue);
        center.add(Box.createVerticalStrut(18));
        center.add(statusValue);
        center.add(Box.createVerticalStrut(18));
        center.add(detailValue);
        center.add(Box.createVerticalGlue());

        // Add sections to main layout
        add(heading, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    // Repaints labels and background colors when RFID signals arrive
    @Override
    public void updateView(GameSnapshot snapshot) {

        // Displays required tag id from physical hardware
        if (snapshot.rfidRequiredTagNumber > 0) {
            requiredTagValue.setText("RFID Tag " + snapshot.rfidRequiredTagNumber);
        } else {
            requiredTagValue.setText("Waiting...");
        }

        // Show context name of checkpoint stage
        if (snapshot.rfidCheckpointName != null && !snapshot.rfidCheckpointName.trim().isEmpty()) {
            checkpointValue.setText("After " + snapshot.rfidCheckpointName);
        } else {
            checkpointValue.setText("Checkpoint");
        }

        // Update messaging themes depending on whether a read matched or errored out
        if (snapshot.message != null && snapshot.message.toLowerCase().contains("correct")) {
            statusValue.setText("Correct tag accepted");
            statusValue.setForeground(UiTheme.SUCCESS);
            detailValue.setText("Proceeding to the next stage.");
        } else if (snapshot.message != null && snapshot.message.toLowerCase().contains("wrong")) {
            statusValue.setText("Wrong tag. Try again.");
            statusValue.setForeground(UiTheme.DANGER);
            detailValue.setText("Remove the tag and scan the required RFID tag.");
        } else {
            statusValue.setText("Scan the required RFID tag");
            statusValue.setForeground(UiTheme.TEXT);

            if (snapshot.rfidRequiredTagNumber > 0) {
                detailValue.setText("Place RFID Tag " + snapshot.rfidRequiredTagNumber + " on the reader.");
            } else {
                detailValue.setText("Waiting for Arduino to tell us which RFID tag is required.");
            }
        }
    }
}