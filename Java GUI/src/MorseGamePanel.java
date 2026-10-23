import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Screen for the second minigame: decoding Morse code.
 * Displays target letters, expected Morse codes, current hold durations, and player entry sequences.
 */
public class MorseGamePanel extends GameView {

    private static final long serialVersionUID = 1L;

    // UI elements styled with the project theme
    private final JLabel targetLettersValue = UiTheme.value("Waiting...", 42);
    private final JLabel targetMorseValue = UiTheme.value("Waiting...", 38);

    private final JLabel holdValue = UiTheme.value("0.0 s", 48);
    private final JLabel sequenceValue = UiTheme.value("No input yet", 34);

    private final JProgressBar holdBar = new JProgressBar(0, 50);

    private final JLabel instruction = UiTheme.subtitle(
            "Hold the metal touch sensor for 2 seconds to enter a dot, or 5 seconds to enter a dash."
    );

    // Sets up the layouts, margins, and grid alignments
    public MorseGamePanel() {

        setLayout(new BorderLayout(18, 18));
        setBackground(UiTheme.BG);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Top header section
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        heading.add(UiTheme.title("Minigame 2. Morse Code Decryption"));
        heading.add(Box.createVerticalStrut(6));
        heading.add(instruction);

        // Center section showing active hold times and progress bar
        JPanel center = UiTheme.card();
        center.setLayout(new BorderLayout(16, 16));

        holdValue.setHorizontalAlignment(SwingConstants.CENTER);

        holdBar.setStringPainted(true);
        holdBar.setBackground(UiTheme.CARD_2);
        holdBar.setForeground(UiTheme.ACCENT);

        center.add(holdValue, BorderLayout.CENTER);
        center.add(holdBar, BorderLayout.SOUTH);

        // Bottom section for the three informational display cards
        JPanel stats = new JPanel(new GridLayout(1, 3, 14, 0));
        stats.setOpaque(false);

        stats.add(statCard("TARGET LETTERS", targetLettersValue));
        stats.add(statCard("TARGET MORSE CODE", targetMorseValue));
        stats.add(statCard("YOUR SEQUENCE", sequenceValue));

        // Add sections to main layout
        add(heading, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(stats, BorderLayout.SOUTH);
    }

    // Helper to generate a standardized visual card for individual metrics
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

    // Syncs the UI components with the current game state snapshot from the Arduino
    @Override
    public void updateView(GameSnapshot snapshot) {

        // Display the correct alpha target letters
        if (snapshot.morseTargetLetters == null || snapshot.morseTargetLetters.trim().isEmpty()) {
            targetLettersValue.setText("Waiting...");
        } else {
            targetLettersValue.setText(snapshot.morseTargetLetters);
        }

        // Display the expected Morse pattern
        if (snapshot.morseTargetCode == null || snapshot.morseTargetCode.trim().isEmpty()) {
            targetMorseValue.setText("Waiting...");
        } else {
            targetMorseValue.setText(snapshot.morseTargetCode);
        }

        double seconds = snapshot.morseHoldSeconds;

        // Render hold time and visually represent it in the progress bar
        holdValue.setText(String.format("%.1f s", seconds));
        holdBar.setValue((int) Math.min(50, seconds * 10));

        // Update instruction notes dynamically based on the current press duration
        if (seconds >= 5.0) {
            holdBar.setString("Dash range");
            instruction.setText("Release now to record a dash.");
        } else if (seconds >= 2.0) {
            holdBar.setString("Dot range");
            instruction.setText("Release now to record a dot, or keep holding until 5 seconds for a dash.");
        } else {
            holdBar.setString("Keep holding");
            instruction.setText("Hold for 2 seconds for dot, or 5 seconds for dash.");
        }

        // Show whatever sequence sequence has been logged by the player so far
        if (snapshot.morseSequence == null || snapshot.morseSequence.trim().isEmpty()) {
            sequenceValue.setText("No input yet...");
        } else {
            sequenceValue.setText(snapshot.morseSequence);
        }
    }
}