import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Screen shown when the game is won and the vault is unlocked.
 * Shows final statistics, overall completion time, and difficulty settings.
 */
public class FinishGamePanel extends GameView {

    private static final long serialVersionUID = 1L;

    // UI elements styled with the project theme
    private final JLabel titleValue = UiTheme.value("VAULT OPEN", 56);
    private final JLabel difficultyValue = UiTheme.value("Difficulty: EASY", 28);
    private final JLabel totalTimeValue = UiTheme.value("Total Time: --", 36);
    private final JLabel detailValue = UiTheme.subtitle(
            "All vault security stages have been cleared."
    );

    // Sets up the final summary screen layout
    public FinishGamePanel() {

        setLayout(new BorderLayout(18, 18));
        setBackground(UiTheme.BG);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Top header section
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        heading.add(UiTheme.title("Cash Me If You Can"));
        heading.add(Box.createVerticalStrut(6));
        heading.add(UiTheme.subtitle("Vault completion summary"));

        // Central statistics card
        JPanel center = UiTheme.card();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setPreferredSize(new Dimension(760, 420));

        titleValue.setForeground(UiTheme.SUCCESS);
        titleValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        difficultyValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        totalTimeValue.setForeground(UiTheme.ACCENT);
        totalTimeValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        detailValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Add components with vertical spacing in the card
        center.add(Box.createVerticalGlue());
        center.add(titleValue);
        center.add(Box.createVerticalStrut(22));
        center.add(difficultyValue);
        center.add(Box.createVerticalStrut(18));
        center.add(totalTimeValue);
        center.add(Box.createVerticalStrut(18));
        center.add(detailValue);
        center.add(Box.createVerticalGlue());

        // Bottom horizontal status cards
        JPanel bottomStats = new JPanel(new GridLayout(1, 2, 14, 0));
        bottomStats.setOpaque(false);

        bottomStats.add(summaryCard("GAME STATUS", "Completed"));
        bottomStats.add(summaryCard("FINAL RESULT", "Vault unlocked"));

        // Add sections to main layout
        add(heading, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(bottomStats, BorderLayout.SOUTH);
    }

    // Helper to create a small status card panel
    private JPanel summaryCard(String title, String value) {

        JPanel panel = UiTheme.card();
        panel.setLayout(new BorderLayout(10, 10));

        JLabel titleLabel = UiTheme.subtitle(title);
        JLabel valueLabel = UiTheme.value(value, 24);

        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);

        return panel;
    }

    // Updates the final summary statistics
    @Override
    public void updateView(GameSnapshot snapshot) {

        difficultyValue.setText("Difficulty: " + snapshot.difficulty);

        // Check if there is pre-formatted timer text first; otherwise, format the raw seconds
        if (snapshot.totalGameTimeText != null
                && !snapshot.totalGameTimeText.trim().isEmpty()) {

            totalTimeValue.setText("Total Time: " + snapshot.totalGameTimeText);

        } else if (snapshot.totalGameTimeSeconds > 0.0) {

            totalTimeValue.setText(
                    "Total Time: " + formatSeconds(snapshot.totalGameTimeSeconds)
            );

        } else {

            totalTimeValue.setText("Total Time: --");
        }

        detailValue.setText("All vault security stages have been cleared.");
    }

    // Converts seconds into a clean minute-and-second format
    private String formatSeconds(double seconds) {

        int totalSeconds = (int) Math.round(seconds);

        int minutes = totalSeconds / 60;
        int remainingSeconds = totalSeconds % 60;

        if (minutes > 0) {
            return minutes + " min " + remainingSeconds + " sec";
        }

        return remainingSeconds + " sec";
    }
}