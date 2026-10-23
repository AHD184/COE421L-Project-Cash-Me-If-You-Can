import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Screen for the first minigame: rotating the dial to find the hidden frequency.
 * Shows the current position, progress bar, target distance, and any clues.
 */
public class EncoderGamePanel extends GameView {

    private static final long serialVersionUID = 1L;

    // UI elements styled with the project theme
    private final JLabel positionValue = UiTheme.value("0", 52);
    private final JLabel distanceValue = UiTheme.value("--", 34);
    private final JLabel targetStatusValue = UiTheme.value("Hidden", 30);
    private final JLabel duplicateLetterClueValue = UiTheme.value("Two matching letters", 26);

    private final JProgressBar positionBar = new JProgressBar(0, 100);

    private final JLabel instruction = UiTheme.subtitle(
            "Rotate the control slowly. Use the distance and buzzer feedback to locate the hidden gate."
    );

    // Sets up the main layout and arranges all the components
    public EncoderGamePanel() {

        setLayout(new BorderLayout(18, 18));
        setBackground(UiTheme.BG);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Top header section
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        heading.add(UiTheme.title("Minigame 1. Hidden Frequency Dial"));
        heading.add(Box.createVerticalStrut(6));
        heading.add(instruction);

        // Center section for the main position value and progress bar
        JPanel center = UiTheme.card();
        center.setLayout(new BorderLayout(16, 16));

        positionValue.setHorizontalAlignment(SwingConstants.CENTER);

        positionBar.setStringPainted(true);
        positionBar.setPreferredSize(new Dimension(500, 34));
        positionBar.setBackground(UiTheme.CARD_2);
        positionBar.setForeground(UiTheme.ACCENT);

        center.add(positionValue, BorderLayout.CENTER);
        center.add(positionBar, BorderLayout.SOUTH);

        // Bottom section for the three stats cards
        JPanel stats = new JPanel(new GridLayout(1, 3, 14, 0));
        stats.setOpaque(false);

        stats.add(statCard("HIDDEN TARGET", targetStatusValue));
        stats.add(statCard("DISTANCE FROM TARGET", distanceValue));
        stats.add(statCard("ENCODER CLUE", duplicateLetterClueValue));

        // Add sections to the main layout
        add(heading, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(stats, BorderLayout.SOUTH);
    }

    // Helper to build a styled info card for the stats grid
    private JPanel statCard(String name, JLabel value) {

        JPanel panel = UiTheme.card();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = UiTheme.subtitle(name);

        value.setAlignmentX(CENTER_ALIGNMENT);

        panel.add(label);
        panel.add(Box.createVerticalStrut(8));
        panel.add(value);

        return panel;
    }

    // Updates all UI elements with the latest data from the Arduino
    @Override
    public void updateView(GameSnapshot snapshot) {

        // Change progress bar max size depending on the difficulty
        int max = 100;

        if ("MEDIUM".equalsIgnoreCase(snapshot.difficulty)) {
            max = 150;
        } else if ("HARD".equalsIgnoreCase(snapshot.difficulty)) {
            max = 200;
        }

        positionBar.setMaximum(max);

        // Display current position and distance to target
        positionValue.setText(String.valueOf(snapshot.encoderPosition));
        distanceValue.setText(String.valueOf(snapshot.encoderDistance));
        targetStatusValue.setText("Hidden");

        // Display the duplicate letter clue if one exists, otherwise use a fallback string
        if (snapshot.encoderDuplicateLetterClue == null
                || snapshot.encoderDuplicateLetterClue.trim().isEmpty()) {
            duplicateLetterClueValue.setText("Two matching letters");
        } else {
            duplicateLetterClueValue.setText(snapshot.encoderDuplicateLetterClue);
        }

        duplicateLetterClueValue.setForeground(UiTheme.ACCENT);

        // Sync progress bar values
        positionBar.setValue(snapshot.encoderPosition);
        positionBar.setString("Position " + snapshot.encoderPosition + " / " + max);

        // Give visual and textual feedback based on how close they are
        if (snapshot.encoderDistance == 0) {
            instruction.setText("Perfect match. Hidden gate found. Remember the clue: two letters are the same.");
            distanceValue.setForeground(UiTheme.SUCCESS);
        } else if (snapshot.encoderDistance <= 5) {
            instruction.setText("Very close. Move carefully and listen to the buzzer. Two of the letters are the same.");
            distanceValue.setForeground(UiTheme.WARNING);
        } else {
            instruction.setText("Rotate slowly. The target remains hidden. Clue: two of the letters are the same.");
            distanceValue.setForeground(UiTheme.TEXT);
        }
    }
}