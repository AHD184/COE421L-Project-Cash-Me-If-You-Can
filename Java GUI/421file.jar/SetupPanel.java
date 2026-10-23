import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class SetupPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final StepCard[] stepCards = new StepCard[6];
    private final JButton beginButton = new JButton("Begin Game");
    private final JLabel mainStatus = UiTheme.subtitle("Waiting for Arduino setup...");

    private int currentStep = 0;

    public SetupPanel(Runnable onBeginGame) {
        setLayout(new BorderLayout(18, 18));
        setBackground(UiTheme.BG);
        setBorder(new EmptyBorder(22, 22, 22, 22));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(UiTheme.title("Cash Me If You Can Setup"));
        heading.add(Box.createVerticalStrut(8));
        heading.add(mainStatus);

        JPanel steps = new JPanel(new GridLayout(2, 3, 14, 14));
        steps.setOpaque(false);

        stepCards[0] = new StepCard(
                "1",
                "Connect hardware",
                "Power the Arduino and connect the Zigbee or USB serial receiver. Select the correct COM port in the Controls tab."
        );
        stepCards[1] = new StepCard(
                "2",
                "Register RFID tags",
                "Place each RFID tag flat on the reader and hold it steady for about 1 to 2 seconds. When a tag is scanned, remove it and then scan the next tag."
        );
        stepCards[2] = new StepCard(
                "3",
                "Calibrate metal touch sensor",
                "First, do not touch the sensor for 2 seconds. Then touch and hold the metal sensor for 2 seconds when asked."
        );
        stepCards[3] = new StepCard(
                "4",
                "Choose difficulty mode",
                "Use the encoder button to switch modes: Easy, Medium, Hard. Short press cycles modes. Hold for 1.5 seconds to lock the selected mode."
        );
        stepCards[4] = new StepCard(
                "5",
                "Confirm setup",
                "Arduino confirms that RFID tags, touch calibration, and difficulty selection are ready."
        );
        stepCards[5] = new StepCard(
                "6",
                "Ready to play",
                "Press Begin Game. The first game will start with the hidden frequency dial."
        );

        for (StepCard card : stepCards) {
            steps.add(card);
        }

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);

        beginButton.setEnabled(false);
        beginButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        beginButton.addActionListener(e -> {
            if (onBeginGame != null) {
                onBeginGame.run();
            }
        });
        bottom.add(beginButton);

        add(heading, BorderLayout.NORTH);
        add(steps, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        resetSetup();
    }

    public void resetSetup() {
        currentStep = 0;

        for (StepCard card : stepCards) {
            card.setLocked();
        }

        stepCards[0].setActive("Connect the Arduino and open the serial connection.");
        beginButton.setEnabled(false);
        mainStatus.setText("Step 1 active. Connect the hardware.");
    }

    public void completeStep(int index, String successMessage) {
        if (index < 0 || index >= stepCards.length) {
            return;
        }

        stepCards[index].setComplete(successMessage);

        if (index + 1 < stepCards.length) {
            currentStep = index + 1;
            stepCards[currentStep].setActive("Waiting for Arduino...");
            mainStatus.setText(
                    "Step " + (currentStep + 1) + " active. " + stepCards[currentStep].getTitleText()
            );
        } else {
            setupComplete();
        }
    }

    public void setStepMessage(int index, String message) {
        if (index < 0 || index >= stepCards.length) {
            return;
        }

        currentStep = index;
        stepCards[index].setActive(message);
        mainStatus.setText("Step " + (index + 1) + ". " + message);
    }

    public void setupComplete() {
        for (StepCard card : stepCards) {
            if (!card.isComplete()) {
                card.setComplete("Completed.");
            }
        }

        mainStatus.setText("Setup complete. Press Begin Game.");
        beginButton.setEnabled(true);
    }

    private static class StepCard extends JPanel {

        private static final long serialVersionUID = 1L;

        private final JLabel numberLabel;
        private final JLabel titleLabel;
        private final JLabel statusLabel;
        private final JTextArea descriptionArea;

        private boolean complete;

        StepCard(String number, String title, String description) {
            setLayout(new BorderLayout(8, 8));
            setBackground(UiTheme.CARD);
            setBorder(new LineBorder(UiTheme.CARD_2, 1));

            numberLabel = UiTheme.value(number, 24);
            numberLabel.setForeground(UiTheme.ACCENT);

            titleLabel = UiTheme.value(title, 16);

            statusLabel = UiTheme.subtitle("LOCKED");
            statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

            descriptionArea = new JTextArea(description);
            descriptionArea.setEditable(false);
            descriptionArea.setLineWrap(true);
            descriptionArea.setWrapStyleWord(true);
            descriptionArea.setOpaque(false);
            descriptionArea.setFocusable(false);
            descriptionArea.setForeground(UiTheme.MUTED);
            descriptionArea.setFont(new Font("SansSerif", Font.PLAIN, 14));

            JPanel top = new JPanel(new BorderLayout(8, 8));
            top.setOpaque(false);
            top.add(numberLabel, BorderLayout.WEST);
            top.add(titleLabel, BorderLayout.CENTER);

            add(top, BorderLayout.NORTH);
            add(descriptionArea, BorderLayout.CENTER);
            add(statusLabel, BorderLayout.SOUTH);
        }

        String getTitleText() {
            return titleLabel.getText();
        }

        void setLocked() {
            complete = false;
            setBackground(UiTheme.CARD);
            setBorder(new LineBorder(UiTheme.CARD_2, 1));
            statusLabel.setText("LOCKED");
            statusLabel.setForeground(UiTheme.MUTED);
        }

        void setActive(String message) {
            complete = false;
            setBackground(UiTheme.CARD);
            setBorder(new LineBorder(UiTheme.ACCENT, 2));
            statusLabel.setText("ACTIVE. " + message);
            statusLabel.setForeground(UiTheme.ACCENT);
        }

        void setComplete(String message) {
            complete = true;
            setBackground(UiTheme.CARD);
            setBorder(new LineBorder(UiTheme.SUCCESS, 2));
            statusLabel.setText("SUCCESS. " + message);
            statusLabel.setForeground(UiTheme.SUCCESS);
        }

        boolean isComplete() {
            return complete;
        }
    }
}
