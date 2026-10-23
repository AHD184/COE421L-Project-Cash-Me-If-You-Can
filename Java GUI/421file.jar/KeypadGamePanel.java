import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Screen for the third minigame: Codebreaker.
 * Displays guesses, active countdowns, accuracy metrics, and character positioning clues.
 */
public class KeypadGamePanel extends GameView {

    private static final long serialVersionUID = 1L;

    // UI elements styled with the project theme
    private final JLabel runValue = UiTheme.value("Run 1 / 1", 24);
    private final JLabel codeLengthValue = UiTheme.value("4 characters", 24);
    private final JLabel startInstructionValue = UiTheme.value("Press *** to start", 24);

    private final JLabel timerValue = UiTheme.value("Waiting", 52);
    private final JLabel guessValue = UiTheme.value("_ _ _ _", 42);

    private final JLabel bullseyeValue = UiTheme.value("0", 38);
    private final JLabel cowValue = UiTheme.value("0", 38);

    private final JTextArea letterFeedbackArea = new JTextArea();
    private final JLabel feedbackValue = UiTheme.value("Waiting for keypad input.", 22);

    private final JProgressBar timerBar = new JProgressBar(0, 60);

    private final JLabel instruction = UiTheme.subtitle(
            "No repeated characters. Enter the generated code and press # to submit."
    );

    // Arranges all components, headers, progress bars, and grids
    public KeypadGamePanel() {

        setLayout(new BorderLayout(18, 18));
        setBackground(UiTheme.BG);
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // Top header section
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        heading.add(UiTheme.title("Minigame 3. Codebreaker"));
        heading.add(Box.createVerticalStrut(6));
        heading.add(instruction);

        // Center section showing current guess blanks and timer countdown
        JPanel center = UiTheme.card();
        center.setLayout(new BorderLayout(16, 16));

        timerValue.setHorizontalAlignment(SwingConstants.CENTER);
        guessValue.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel centerInfo = new JPanel(new GridLayout(2, 1));
        centerInfo.setOpaque(false);
        centerInfo.add(timerValue);
        centerInfo.add(guessValue);

        timerBar.setStringPainted(true);
        timerBar.setBackground(UiTheme.CARD_2);
        timerBar.setForeground(UiTheme.ACCENT);

        center.add(centerInfo, BorderLayout.CENTER);
        center.add(timerBar, BorderLayout.SOUTH);

        // Bottom section for game metrics and text hints
        JPanel stats = new JPanel(new GridLayout(2, 3, 14, 14));
        stats.setOpaque(false);

        stats.add(statCard("RUN PROGRESS", runValue));
        stats.add(statCard("CODE LENGTH", codeLengthValue));
        stats.add(statCard("START ACTION", startInstructionValue));

        stats.add(statCard("BULLSEYES (Correct)", bullseyeValue));
        stats.add(statCard("COWS (Wrong Position)", cowValue));

        // Feedback card detailing which characters were correct or misplaced
        JPanel feedbackCard = UiTheme.card();
        feedbackCard.setLayout(new BorderLayout(10, 10));
        JLabel feedbackTitle = UiTheme.subtitle("LETTER FEEDBACK");
        feedbackValue.setHorizontalAlignment(SwingConstants.CENTER);

        letterFeedbackArea.setEditable(false);
        letterFeedbackArea.setOpaque(false);
        letterFeedbackArea.setForeground(UiTheme.TEXT);
        letterFeedbackArea.setFont(feedbackValue.getFont().deriveFont(14f));
        letterFeedbackArea.setLineWrap(true);
        letterFeedbackArea.setWrapStyleWord(true);

        feedbackCard.add(feedbackTitle, BorderLayout.NORTH);
        feedbackCard.add(feedbackValue, BorderLayout.CENTER);
        feedbackCard.add(letterFeedbackArea, BorderLayout.SOUTH);

        stats.add(feedbackCard);

        // Add sections to main layout
        add(heading, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(stats, BorderLayout.SOUTH);
    }

    // Helper to build modular status displays for metrics like progress and targets
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

    // Updates UI elements dynamically based on live variables sent over Serial
    @Override
    public void updateView(GameSnapshot snapshot) {

        runValue.setText("Run " + snapshot.keypadRunNumber + " / " + snapshot.keypadTotalRuns);
        codeLengthValue.setText(snapshot.keypadCodeLength + " characters");
        startInstructionValue.setText(snapshot.keypadStartInstruction);

        // Adjust timer thresholds dynamically based on chosen game difficulty
        int maxSeconds = difficultyMaximum(snapshot.difficulty);
        timerBar.setMaximum(maxSeconds);

        int seconds = snapshot.keypadSecondsLeft;

        // Render countdown and change colors if the timer is dangerously low
        if (snapshot.keypadStarted) {
            timerValue.setText(seconds + " s");
            timerBar.setValue(seconds);
            timerBar.setString(seconds + " seconds left");

            if (seconds <= 10) {
                timerValue.setForeground(UiTheme.DANGER);
                timerBar.setForeground(UiTheme.DANGER);
            } else {
                timerValue.setForeground(UiTheme.TEXT);
                timerBar.setForeground(UiTheme.ACCENT);
            }
        } else {
            timerValue.setText("Waiting");
            timerValue.setForeground(UiTheme.MUTED);
            timerBar.setValue(0);
            timerBar.setString("Not started");
        }

        // Draw blank spaces or submitted keystrokes to mimic an input pad entry
        if (snapshot.keypadGuess == null || snapshot.keypadGuess.trim().isEmpty()) {
            guessValue.setText(placeholders(snapshot.keypadCodeLength));
        } else {
            StringBuilder spacedGuess = new StringBuilder();
            for (int i = 0; i < snapshot.keypadGuess.length(); i++) {
                if (i > 0) {
                    spacedGuess.append(" ");
                }
                spacedGuess.append(snapshot.keypadGuess.charAt(i));
            }

            int remaining = snapshot.keypadCodeLength - snapshot.keypadGuess.length();
            for (int i = 0; i < remaining; i++) {
                spacedGuess.append(" _");
            }
            guessValue.setText(spacedGuess.toString());
        }

        bullseyeValue.setText(String.valueOf(snapshot.keypadBullseyes));
        cowValue.setText(String.valueOf(snapshot.keypadCows));

        // Handle letter-specific feedback strings
        if (snapshot.keypadLetterFeedback == null || snapshot.keypadLetterFeedback.trim().isEmpty()) {
            letterFeedbackArea.setText("");
        } else {
            letterFeedbackArea.setText(snapshot.keypadLetterFeedback);
        }

        // Handle generic status updates and errors
        if (snapshot.keypadFeedback == null || snapshot.keypadFeedback.trim().isEmpty()) {
            feedbackValue.setText(snapshot.keypadStatus);
        } else {
            feedbackValue.setText(snapshot.keypadFeedback);
        }

        // Update instruction label with contextual feedback based on game state and time limits
        if (snapshot.keypadStarted && seconds <= 10 && seconds > 0) {
            instruction.setText("Hurry. No repeated characters. Enter the rest and press #.");
        } else if (snapshot.keypadStarted) {
            instruction.setText("No repeated characters. Bullseye means correct character and correct position. Cow means correct character, wrong position.");
        } else {
            instruction.setText("Waiting for Codebreaker to start. Follow the start action shown above.");
        }
    }

    // Returns a string containing a set of blank space indicators matching the requested code length
    private String placeholders(int length) {

        if (length <= 0) {
            length = 4;
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(" ");
            }

            builder.append("_");
        }

        return builder.toString();
    }

    // Translates the game difficulty string into raw timer seconds
    private int difficultyMaximum(String difficulty) {

        if ("HARD".equalsIgnoreCase(difficulty)) {
            return 30;
        }

        if ("MEDIUM".equalsIgnoreCase(difficulty)) {
            return 45;
        }

        return 60;
    }
}