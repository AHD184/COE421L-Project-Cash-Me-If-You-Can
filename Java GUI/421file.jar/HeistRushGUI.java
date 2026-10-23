import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import jssc.SerialPortList;

/**
 * Main Controller and Graphical User Interface (GUI) for the "Heist Rush" / "Cash Me If You Can" game.
 * This class acts as the central hub: coordinating individual minigame panels, handling asynchronous 
 * serial communication with the Arduino receiver, parsing incoming physical hardware events, and 
 * managing global game state updates.
 */
public class HeistRushGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    // Path to the background audio asset
    private static final String BACKGROUND_MUSIC_PATH = "assets/soviet_connection.wav";

    // Layout managers to swap minigame screens and organize the top-level game container
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel gameContainer = new JPanel(cardLayout);

    // Individual game panels representing different stages/puzzles of the physical heist game
    private final EncoderGamePanel encoderPanel = new EncoderGamePanel();
    private final MorseGamePanel morsePanel = new MorseGamePanel();
    private final KeypadGamePanel keypadPanel = new KeypadGamePanel();
    private final RFIDGamePanel rfidPanel = new RFIDGamePanel();
    private final FinishGamePanel finishPanel = new FinishGamePanel();

    // The initial calibration and setup wizard panel (not instantiated inline to register callback)
    private SetupPanel setupPanel;

    // UI components for the diagnostic/debug Serial Console tab
    private final JTextArea consoleArea = new JTextArea();

    // UI components for managing physical serial hardware connections
    private JComboBox<String> portComboBox;
    private JButton connectButton;
    private JButton disconnectButton;

    // Multithreading and Serial I/O fields to manage non-blocking communication
    private SerialPortHandler serialPort;
    private Thread serialThread;
    private volatile boolean readingSerial = false; // Thread-safe flag to safely stop the reading loop

    // Media player wrapper for background ambience
    private final SoundPlayer backgroundMusic = new SoundPlayer();

    // Global game state model shared with all sub-panels
    private final GameSnapshot gameState = new GameSnapshot();

    // Local configuration tracking variables (temporary state storage)
    private String currentDifficulty = "EASY";
    private String currentMorseSequence = "";
    private String currentMorseTargetLetters = "";
    private String currentMorseTargetCode = "";

    private int rfidTagsRegistered = 0;
    private String lastCompletedMinigame = "";

    /**
     * Constructor: Initializes window properties, registers setup callback routines,
     * builds the user interface, and kicks off background music.
     */
    public HeistRushGUI() {
        // Configure main frame properties
        setTitle("Cash Me If You Can");
        setSize(1180, 760);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null); // Center window on screen
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Instantiate setup panel with a callback to kick off the game when setup finishes
        setupPanel = new SetupPanel(new Runnable() {
            @Override
            public void run() {
                beginGame();
            }
        });

        // Add all sub-panels to the card-swapping container
        gameContainer.setBackground(UiTheme.BG);
        gameContainer.add(setupPanel, "SETUP");
        gameContainer.add(encoderPanel, "ENCODER");
        gameContainer.add(morsePanel, "MORSE");
        gameContainer.add(keypadPanel, "KEYPAD");
        gameContainer.add(rfidPanel, "RFID");
        gameContainer.add(finishPanel, "FINISH");

        // Set up main navigational tabs (Main Game, Developer Console, and Manual Controls)
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Game", gameContainer);
        tabs.addTab("Serial Console", buildConsolePanel());
        tabs.addTab("Controls", buildControlPanel());

        // Place tabs in the main window
        add(tabs, BorderLayout.CENTER);

        // Show the calibration wizard screen first
        cardLayout.show(gameContainer, "SETUP");

        // Start background ambient music
        startBackgroundMusic();
    }

    /**
     * Builds the Serial Console panel used to log diagnostic data and Arduino communication stream.
     */
    private JPanel buildConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiTheme.BG);

        // Style console area with a monospaced font for readable log alignment
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        consoleArea.setBackground(UiTheme.CARD);
        consoleArea.setForeground(UiTheme.TEXT);

        JScrollPane scrollPane = new JScrollPane(consoleArea);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(UiTheme.BG);

        JLabel title = UiTheme.title("Serial Console");
        topPanel.add(title);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Builds the Controls / Manual Overrides panel.
     * Contains serial port connection settings and testing buttons to simulate physical game milestones.
     */
    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(18, 18));
        panel.setBackground(UiTheme.BG);

        // Card container for serial port selection
        JPanel serialPanel = UiTheme.card();
        serialPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 12));

        JLabel portLabel = UiTheme.subtitle("Serial Port:");

        portComboBox = new JComboBox<String>();
        refreshPorts();

        JButton refreshButton = new JButton("Refresh Ports");
        connectButton = new JButton("Connect");
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);

        // Button action mappings to physical connectivity routines
        refreshButton.addActionListener(e -> refreshPorts());
        connectButton.addActionListener(e -> connectSerial());
        disconnectButton.addActionListener(e -> disconnectSerial());

        serialPanel.add(portLabel);
        serialPanel.add(portComboBox);
        serialPanel.add(refreshButton);
        serialPanel.add(connectButton);
        serialPanel.add(disconnectButton);

        // Card container for UI and hardware emulation testing buttons
        JPanel testPanel = UiTheme.card();
        testPanel.setLayout(new GridLayout(3, 4, 12, 12));

        JButton resetSetupButton = new JButton("Reset Setup");
        JButton simulateSetupButton = new JButton("Simulate Setup Done");
        JButton encoderButton = new JButton("Show Encoder");
        JButton morseButton = new JButton("Show Morse");
        JButton keypadButton = new JButton("Show Keypad");
        JButton rfidButton = new JButton("Show RFID");
        JButton finishButton = new JButton("Show Finish");
        JButton setupButton = new JButton("Show Setup");
        JButton restartMusicButton = new JButton("Restart Music");
        JButton stopMusicButton = new JButton("Stop Music");

        // Logic resets and UI navigation routing for testing
        resetSetupButton.addActionListener(e -> {
            setupPanel.resetSetup();
            resetGameState();
            cardLayout.show(gameContainer, "SETUP");
            appendConsole("[GUI] Setup reset.");
        });

        simulateSetupButton.addActionListener(e -> simulateSetupComplete());

        encoderButton.addActionListener(e -> showEncoderDemo());
        morseButton.addActionListener(e -> showMorseDemo());
        keypadButton.addActionListener(e -> showKeypadDemo());
        rfidButton.addActionListener(e -> showRFIDDemo());
        finishButton.addActionListener(e -> showFinishDemo());

        setupButton.addActionListener(e -> {
            cardLayout.show(gameContainer, "SETUP");
            appendConsole("[GUI] Showing setup screen.");
        });

        restartMusicButton.addActionListener(e -> startBackgroundMusic());

        stopMusicButton.addActionListener(e -> {
            backgroundMusic.stop();
            appendConsole("[GUI] Background music stopped.");
        });

        testPanel.add(resetSetupButton);
        testPanel.add(simulateSetupButton);
        testPanel.add(encoderButton);
        testPanel.add(morseButton);
        testPanel.add(keypadButton);
        testPanel.add(rfidButton);
        testPanel.add(finishButton);
        testPanel.add(setupButton);
        testPanel.add(restartMusicButton);
        testPanel.add(stopMusicButton);

        // Combine sub-sections into a single wrapped control view
        JPanel wrapper = new JPanel(new BorderLayout(18, 18));
        wrapper.setBackground(UiTheme.BG);
        wrapper.add(serialPanel, BorderLayout.NORTH);
        wrapper.add(testPanel, BorderLayout.CENTER);

        panel.add(wrapper, BorderLayout.NORTH);

        return panel;
    }

    /**
     * Initializes playback of the background soundtrack loop with adjusted volume.
     */
    private void startBackgroundMusic() {
        // Play track continuously with a soft attenuation (-15dB)
        boolean started = backgroundMusic.playLoop(BACKGROUND_MUSIC_PATH, -15.0f);

        if (started) {
            appendConsole("[GUI] Background music started: " + BACKGROUND_MUSIC_PATH);
        } else {
            appendConsole("[GUI] Background music not started. Put the WAV file here: " + BACKGROUND_MUSIC_PATH);
        }
    }

    /**
     * Queries the system's operating system environment for active serial/COM ports 
     * and updates the selection dropdown menu.
     */
    private void refreshPorts() {
        if (portComboBox == null) {
            return;
        }

        portComboBox.removeAllItems();
        String[] ports = SerialPortList.getPortNames();

        if (ports.length == 0) {
            portComboBox.addItem("No ports found");
            return;
        }

        for (String port : ports) {
            portComboBox.addItem(port);
        }
    }

    /**
     * Connects to the selected serial port and spins up a dedicated worker thread 
     * to read incoming physical transmissions without blocking the UI.
     */
    private void connectSerial() {
        Object selected = portComboBox.getSelectedItem();

        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No serial port selected.", "Serial Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String portName = selected.toString();

        if (portName.equals("No ports found")) {
            JOptionPane.showMessageDialog(this, "No serial ports were found. Connect the receiver and refresh.", "Serial Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Establish serial configuration using the JSSC handler
            serialPort = new SerialPortHandler(portName);
            readingSerial = true;

            // Start a secondary background thread to continually listen for hardware reports
            serialThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    readSerialLoop();
                }
            });

            serialThread.setName("CashMeIfYouCanSerialReader");
            serialThread.start();

            // Adjust button states to prevent parallel connection calls
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);

            appendConsole("[GUI] Connected to " + portName);

            // Notify setup manager that connection step is complete
            setupPanel.completeStep(0, "Arduino and receiver connected.");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not connect to serial port: " + e.getMessage(), "Serial Error", JOptionPane.ERROR_MESSAGE);
            appendConsole("[ERROR] Could not connect to " + portName + ": " + e.getMessage());
        }
    }

    /**
     * Safely closes the current serial connection and terminates the monitoring background thread.
     */
    private void disconnectSerial() {
        readingSerial = false; // Breaks reading loop in thread

        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }

        if (serialThread != null) {
            serialThread.interrupt(); // Force wake thread if blocked on I/O
            serialThread = null;
        }

        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);

        appendConsole("[GUI] Serial disconnected.");
    }

    /**
     * Thread loop executing on a separate worker thread. Listens to COM port 
     * character streams and dispatches complete strings to the GUI thread.
     */
    private void readSerialLoop() {
        while (readingSerial) {
            try {
                if (serialPort == null) {
                    return;
                }

                // Retrieve newline-terminated string block
                String line = serialPort.readLine();

                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                final String finalLine = line;

                // CRITICAL: Swing UI is NOT thread-safe. All updates back to visual panels 
                // must be queued onto the Swing Event Dispatch Thread (EDT).
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        processArduinoLine(finalLine);
                    }
                });

            } catch (Exception e) {
                // If serial pipeline fails while system is active, log error and clean up
                if (readingSerial) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            appendConsole("[ERROR] Serial read failed: " + e.getMessage());
                        }
                    });
                }
                readingSerial = false;
            }
        }
    }

    /**
     * Cleans up raw incoming serial packets, strips headers, and passes messages to corresponding parsers.
     */
    private void processArduinoLine(String rawLine) {
        if (rawLine == null) {
            return;
        }

        String line = rawLine.trim();

        if (line.isEmpty()) {
            return;
        }

        // Print raw logs onto the developer console tab
        appendConsole("[ARDUINO] " + line);

        // Strip ZigBee [ZB] prefix headers before feeding message routing machine
        if (line.startsWith("[ZB]")) {
            line = line.substring(4).trim();
        }

        // Send parsed payload through state matching algorithms
        handleSetupMessage(line);
        handleGameMessage(line);
    }

    /**
     * Big Parser: Evaluates strings sent during the initial configuration/calibration stage
     * and advances calibration checkpoints in the setup screen.
     */
    private void handleSetupMessage(String line) {
        String normalized = line.toLowerCase();

        // Checkpoint 0: Confirming hardware handshake
        if (line.equals("BOOT|HEIST_RUSH_READY")
                || normalized.contains("parallax rfid reader ready")
                || normalized.contains("heist rush ready")
                || normalized.contains("arduino ready")) {

            setupPanel.completeStep(0, "Arduino and receiver connected.");

        // Checkpoint 1: RFID tag mapping sequence
        } else if (normalized.startsWith("scan tag #")) {
            int tagNumber = extractFirstInteger(line);
            if (tagNumber > 0) {
                setupPanel.setStepMessage(1, "Scan RFID Tag " + tagNumber + ". Hold it steady for 1 to 2 seconds.");
            } else {
                setupPanel.setStepMessage(1, "Scan the RFID tag. Hold it steady for 1 to 2 seconds.");
            }

        } else if (normalized.startsWith("registered tag #")) {
            int tagNumber = extractFirstInteger(line);
            if (tagNumber <= 0) {
                tagNumber = rfidTagsRegistered + 1;
            }
            updateRFIDSetupProgress(tagNumber);

        } else if (line.startsWith("RFID|REGISTERED|TAG|")) {
            int tagNumber = parseStructuredTagNumber(line);
            updateRFIDSetupProgress(tagNumber);

        } else if (normalized.contains("this tag is already registered")) {
            setupPanel.setStepMessage(1, "This tag was already scanned. Use a different RFID tag.");

        } else if (normalized.contains("remove the tag")) {
            if (rfidTagsRegistered > 0) {
                setupPanel.setStepMessage(1, "RFID Tag " + rfidTagsRegistered + " scanned. Remove it.");
            } else {
                setupPanel.setStepMessage(1, "Scanned. Remove the tag.");
            }

        } else if (normalized.contains("tag removed") || normalized.contains("reader is clear")) {
            int nextTag = rfidTagsRegistered + 1;
            if (nextTag <= 3) {
                setupPanel.setStepMessage(1, "Now scan RFID Tag " + nextTag + ".");
            }

        } else if (line.equals("SETUP|RFID_TAGS_DONE") || normalized.contains("rfid mapping complete")) {
            rfidTagsRegistered = 3;
            setupPanel.completeStep(1, "All 3 RFID tags scanned successfully.");

        // Checkpoint 2: Copper touch plate calibration
        } else if (normalized.contains("do not touch the sensor")) {
            setupPanel.setStepMessage(2, "Do not touch the metal sensor. Keep it untouched for 2 seconds.");

        } else if (normalized.contains("now touch and hold the sensor")) {
            setupPanel.setStepMessage(2, "Touch and hold the metal sensor for 2 seconds.");

        } else if (normalized.contains("measuring touched value in 2 seconds")) {
            setupPanel.setStepMessage(2, "Keep holding the metal sensor. Measuring for 2 seconds.");

        } else if (line.equals("SETUP|TOUCH_CALIBRATED") || normalized.contains("calibration complete")) {
            setupPanel.completeStep(2, "Metal touch sensor calibrated.");

        // Checkpoint 3: Difficulty mapping using rotary encoder button
        } else if (normalized.contains("click encoder button")) {
            setupPanel.setStepMessage(3, "Short press the encoder button to switch Easy, Medium, and Hard. Hold for 1.5 seconds to lock.");

        } else if (isDifficultyPreviewLine(normalized)) {
            String previewDifficulty = parseDifficultyFromLine(normalized);
            if (!previewDifficulty.isEmpty()) {
                currentDifficulty = previewDifficulty;
                gameState.difficulty = currentDifficulty;
                setupPanel.setStepMessage(3, "Current mode: " + currentDifficulty + ". Short press to change. Hold for 1.5 seconds to lock.");
            }

        } else if (line.startsWith("DIFFICULTY|")) {
            currentDifficulty = line.substring("DIFFICULTY|".length()).trim().toUpperCase();
            gameState.difficulty = currentDifficulty;
            setupPanel.completeStep(3, "Difficulty selected: " + currentDifficulty + ".");

        } else if (normalized.contains("level locked in successful") || normalized.contains("system armed")) {
            setupPanel.completeStep(3, "Difficulty selected: " + currentDifficulty + ".");

        // Final handshake transitions
        } else if (line.equals("SETUP|CONFIRMED")) {
            setupPanel.completeStep(4, "Setup values confirmed.");

        } else if (line.equals("SETUP|COMPLETE") || line.equals("STATE|ENCODER")) {
            setupPanel.completeStep(4, "Setup values confirmed.");
            setupPanel.completeStep(5, "Ready to begin.");
            setupPanel.setupComplete();
        }
    }

    /**
     * Game Parser: Translates physical controller updates into visual data representations 
     * for the four core minigames and the final scoreboard.
     */
    private void handleGameMessage(String line) {
        String normalized = line.toLowerCase();

        // === MINIGAME STATE SWAPPING ROUTER ===
        if (line.equals("STATE|ENCODER")) {
            gameState.stage = "ENCODER";
            gameState.encoderDuplicateLetterClue = "Clue: Two of the same letters are present.";
            encoderPanel.updateView(gameState);
            cardLayout.show(gameContainer, "ENCODER");

        } else if (line.startsWith("ENCODER|CLUE|")) {
            gameState.stage = "ENCODER";
            gameState.encoderDuplicateLetterClue = line.substring("ENCODER|CLUE|".length()).trim();
            encoderPanel.updateView(gameState);
            cardLayout.show(gameContainer, "ENCODER");

        } else if (normalized.contains("two of the same letters")
                || normalized.contains("two same letters")
                || normalized.contains("duplicate letters")) {
            gameState.stage = "ENCODER";
            gameState.encoderDuplicateLetterClue = "Clue: Two of the same letters are present.";
            encoderPanel.updateView(gameState);
            cardLayout.show(gameContainer, "ENCODER");

        } else if (line.equals("STATE|MORSE")) {
            gameState.stage = "MORSE";
            gameState.morseSequence = currentMorseSequence;
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        } else if (line.equals("STATE|KEYPAD")) {
            gameState.stage = "KEYPAD";
            gameState.keypadStatus = "Waiting for Codebreaker setup.";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        // === COMPLETED GAME CAPTURING ===
        } else if (normalized.contains("encoder complete")) {
            lastCompletedMinigame = "Encoder";
            gameState.rfidCheckpointName = lastCompletedMinigame;

        } else if (normalized.contains("morse complete")) {
            lastCompletedMinigame = "Morse";
            gameState.rfidCheckpointName = lastCompletedMinigame;

        } else if (normalized.contains("keypad complete")) {
            lastCompletedMinigame = "Keypad";
            gameState.rfidCheckpointName = lastCompletedMinigame;

        // === INTER-STAGE RFID CHECKPOINT HANDLING ===
        } else if (line.startsWith("RFID|WAIT")) {
            parseStructuredRFIDWait(line);
            gameState.stage = "RFID";

            if (gameState.rfidCheckpointName == null || gameState.rfidCheckpointName.trim().isEmpty()) {
                gameState.rfidCheckpointName = lastCompletedMinigame;
            }

            if (gameState.rfidRequiredTagNumber > 0) {
                gameState.message = "Scan RFID Tag " + gameState.rfidRequiredTagNumber + ".";
            } else {
                gameState.message = "Waiting for required RFID tag number.";
            }

            rfidPanel.updateView(gameState);
            cardLayout.show(gameContainer, "RFID");

        } else if (normalized.startsWith("expected tag number")) {
            int tagNumber = extractFirstInteger(line);
            if (tagNumber > 0) {
                gameState.stage = "RFID";
                gameState.rfidRequiredTagNumber = tagNumber;
                gameState.rfidCheckpointName = lastCompletedMinigame;
                gameState.message = "Scan RFID Tag " + tagNumber + ".";
                rfidPanel.updateView(gameState);
                cardLayout.show(gameContainer, "RFID");
            }

        } else if (line.equals("RFID|CORRECT") || normalized.contains("correct rfid tag")) {
            gameState.stage = "RFID";
            gameState.message = "Correct RFID tag accepted.";
            rfidPanel.updateView(gameState);
            cardLayout.show(gameContainer, "RFID");

        } else if (normalized.contains("wrong rfid tag")) {
            gameState.stage = "RFID";
            gameState.message = "Wrong RFID tag. Try again.";
            rfidPanel.updateView(gameState);
            cardLayout.show(gameContainer, "RFID");

        // === MINIGAME 1: ROTARY ENCODER MODULE ===
        } else if (line.startsWith("ENCODER|")) {
            parseEncoderLineIntoState(line);
            gameState.encoderDuplicateLetterClue = "Clue: Two of the same letters are present.";
            encoderPanel.updateView(gameState);
            cardLayout.show(gameContainer, "ENCODER");

        // === MINIGAME 2: MORSE DECODER MODULE ===
        } else if (line.startsWith("Target Sequence String:")) {
            currentMorseTargetLetters = line.substring("Target Sequence String:".length()).trim();
            currentMorseTargetCode = buildMorseCodeForLetters(currentMorseTargetLetters);
            currentMorseSequence = "";

            gameState.stage = "MORSE";
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            gameState.morseSequence = currentMorseSequence;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        } else if (line.startsWith("Expected Core Map Layout:")) {
            currentMorseTargetCode = line.substring("Expected Core Map Layout:".length()).trim();

            gameState.stage = "MORSE";
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            gameState.morseSequence = currentMorseSequence;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        } else if (line.startsWith("MORSE|TARGET|")) {
            parseStructuredMorseTarget(line);

            gameState.stage = "MORSE";
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            gameState.morseSequence = currentMorseSequence;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        } else if (line.startsWith("Holding for:")) {
            gameState.stage = "MORSE";
            gameState.morseHoldSeconds = extractFirstDouble(line);
            gameState.morseSequence = currentMorseSequence;
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        } else if (line.equalsIgnoreCase("Dot added")) {
            gameState.stage = "MORSE";
            currentMorseSequence = currentMorseSequence + ".";
            gameState.morseSequence = currentMorseSequence;
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        } else if (line.equalsIgnoreCase("Dash added")) {
            gameState.stage = "MORSE";
            currentMorseSequence = currentMorseSequence + "-";
            gameState.morseSequence = currentMorseSequence;
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        } else if (line.startsWith("Current stream:")) {
            currentMorseSequence = line.substring("Current stream:".length()).trim();

            gameState.stage = "MORSE";
            gameState.morseSequence = currentMorseSequence;
            gameState.morseTargetLetters = currentMorseTargetLetters;
            gameState.morseTargetCode = currentMorseTargetCode;
            morsePanel.updateView(gameState);
            cardLayout.show(gameContainer, "MORSE");

        // === MINIGAME 3: KEYPAD CODEBREAKER (COWS & BULLSEYES) ===
        } else if (normalized.contains("starting minigame 3: codebreaker")
                || normalized.contains("codebreaker active")
                || normalized.contains("codebreaker re-armed")) {
            parseKeypadRunFromLine(line);

            gameState.stage = "KEYPAD";
            gameState.keypadStarted = normalized.contains("active") || normalized.contains("re-armed");
            gameState.keypadStatus = gameState.keypadStarted ? "Codebreaker active." : "Codebreaker setup ready.";
            gameState.keypadFeedback = "";
            gameState.keypadLetterFeedback = "";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("secret") && normalized.contains("code") 
                && (normalized.contains("digit") || normalized.contains("character"))) {
            int length = extractFirstInteger(line);
            if (length > 0) {
                gameState.keypadCodeLength = length;
            }

            gameState.stage = "KEYPAD";
            gameState.keypadFeedback = "Secret code generated. No repeated characters.";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("press ***")) {
            gameState.stage = "KEYPAD";
            gameState.keypadStartInstruction = "Press *** to start";
            gameState.keypadStarted = false;
            gameState.keypadStatus = "Waiting for ***.";
            gameState.keypadFeedback = "Press *** on the keypad to activate countdown.";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("press *")) {
            gameState.stage = "KEYPAD";
            gameState.keypadStartInstruction = "Press * to start";
            gameState.keypadStarted = false;
            gameState.keypadStatus = "Waiting for *.";
            gameState.keypadFeedback = "Press * on the keypad to activate countdown.";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (line.contains("Active Guess:")) {
            parseKeypadActiveGuessLine(line);
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("invalid length")) {
            gameState.stage = "KEYPAD";
            gameState.keypadFeedback = "Invalid length. Enter exactly " + gameState.keypadCodeLength + " characters.";
            gameState.keypadGuess = "";
            gameState.keypadStarted = true;
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("evaluating guess")) {
            String guess = parseBracketValue(line);
            if (!guess.isEmpty()) {
                gameState.keypadGuess = guess;
            }

            gameState.stage = "KEYPAD";
            gameState.keypadFeedback = "Evaluating guess.";
            gameState.keypadStarted = true;
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("analysis:")) {
            parseArduinoAnalysisLine(line);

            gameState.stage = "KEYPAD";
            gameState.keypadStarted = true;
            gameState.keypadFeedback = "Guess analyzed.";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("total bullseyes")) {
            gameState.stage = "KEYPAD";
            gameState.keypadBullseyes = extractFirstInteger(line);
            gameState.keypadFeedback = "Bullseyes updated.";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("total cows")) {
            gameState.stage = "KEYPAD";
            gameState.keypadCows = extractFirstInteger(line);
            gameState.keypadFeedback = "Cows updated.";
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("incorrect sequence")) {
            gameState.stage = "KEYPAD";
            gameState.keypadFeedback = "Incorrect sequence. Try again.";
            gameState.keypadGuess = "";
            gameState.keypadStarted = true;
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("access confirmed") || normalized.contains("sub-profile") || normalized.contains("cleared")) {
            gameState.stage = "KEYPAD";
            gameState.keypadFeedback = "Correct code. Sub-profile cleared.";
            gameState.keypadGuess = "";
            gameState.keypadBullseyes = 0;
            gameState.keypadCows = 0;
            gameState.keypadBullseyeLetters = "";
            gameState.keypadCowLetters = "";
            gameState.keypadWrongLetters = "";
            gameState.keypadLetterFeedback = "";
            gameState.keypadStarted = false;
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        } else if (normalized.contains("time limit expired")) {
            gameState.stage = "KEYPAD";
            gameState.keypadFeedback = "Time limit expired.";
            gameState.keypadGuess = "";
            gameState.keypadStarted = false;
            keypadPanel.updateView(gameState);
            cardLayout.show(gameContainer, "KEYPAD");

        // === SCOREBOARD / FINISH STAGE ===
        } else if (line.startsWith("GAME|FINISHED")) {
            parseFinishedLine(line);
            gameState.stage = "FINISH";
            gameState.finished = true;
            finishPanel.updateView(gameState);
            cardLayout.show(gameContainer, "FINISH");

        } else if (normalized.contains("total time")) {
            parseTotalTimeText(line);
            gameState.stage = "FINISH";
            gameState.finished = true;
            finishPanel.updateView(gameState);
            cardLayout.show(gameContainer, "FINISH");
        }
    }

    /**
     * Determines whether a serial line contains the text string of current setup mode settings.
     */
    private boolean isDifficultyPreviewLine(String normalized) {
        return normalized.contains("easy mode")
                || normalized.contains("medium mode")
                || normalized.contains("hard mode")
                || normalized.contains("[ easy")
                || normalized.contains("[ medium")
                || normalized.contains("[ hard");
    }

    /**
     * Extracts and returns the specific locked difficulty constant from an incoming string packet.
     */
    private String parseDifficultyFromLine(String normalized) {
        if (normalized.contains("hard")) {
            return "HARD";
        }
        if (normalized.contains("medium")) {
            return "MEDIUM";
        }
        if (normalized.contains("easy")) {
            return "EASY";
        }
        return "";
    }

    /**
     * Tokenizes structured rotary encoder data sequences.
     * Example String format: "ENCODER|POS|12|TARGET|50|DIST|-38|CLUE|My Clue"
     */
    private void parseEncoderLineIntoState(String line) {
        gameState.stage = "ENCODER";
        String[] parts = line.split("\\|");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("POS") && i + 1 < parts.length) {
                gameState.encoderPosition = parseInt(parts[i + 1], gameState.encoderPosition);
            }
            if (parts[i].equals("TARGET") && i + 1 < parts.length) {
                gameState.encoderTarget = parseInt(parts[i + 1], gameState.encoderTarget);
            }
            if (parts[i].equals("DIST") && i + 1 < parts.length) {
                gameState.encoderDistance = parseInt(parts[i + 1], gameState.encoderDistance);
            }
            if (parts[i].equals("CLUE") && i + 1 < parts.length) {
                gameState.encoderDuplicateLetterClue = parts[i + 1].trim();
            }
        }
    }

    /**
     * Tokenizes structured Morse target code sequences.
     * Example String format: "MORSE|TARGET|LETTERS|SOS|CODE|... --- ..."
     */
    private void parseStructuredMorseTarget(String line) {
        String[] parts = line.split("\\|");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("LETTERS") && i + 1 < parts.length) {
                currentMorseTargetLetters = parts[i + 1].trim();
            }
            if (parts[i].equals("CODE") && i + 1 < parts.length) {
                currentMorseTargetCode = parts[i + 1].trim();
            }
        }

        // Generate Morse codes locally if no pre-packaged mapping was received
        if (currentMorseTargetCode.isEmpty() && !currentMorseTargetLetters.isEmpty()) {
            currentMorseTargetCode = buildMorseCodeForLetters(currentMorseTargetLetters);
        }

        currentMorseSequence = "";
    }

    /**
     * Tokenizes structured RFID checkpoint requests between puzzles.
     * Example String format: "RFID|WAIT|TAG|2|AFTER|Morse"
     */
    private void parseStructuredRFIDWait(String line) {
        String[] parts = line.split("\\|");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("TAG") && i + 1 < parts.length) {
                gameState.rfidRequiredTagNumber = parseInt(parts[i + 1], gameState.rfidRequiredTagNumber);
            }
            if (parts[i].equalsIgnoreCase("AFTER") && i + 1 < parts.length) {
                gameState.rfidCheckpointName = parts[i + 1].trim();
            }
        }
    }

    /**
     * Evaluates round markers for codebreaker keypad puzzles.
     * Example String format: "STARTING RUN 1 OF 3"
     */
    private void parseKeypadRunFromLine(String line) {
        String upper = line.toUpperCase();
        int runIndex = upper.indexOf("RUN");

        if (runIndex >= 0) {
            String runPart = line.substring(runIndex);
            int[] numbers = extractIntegers(runPart);

            if (numbers.length >= 2) {
                gameState.keypadRunNumber = numbers[0];
                gameState.keypadTotalRuns = numbers[1];
            }
        }
    }

    /**
     * Evaluates active guesses and timer ticks from Keypad puzzle transmissions.
     * Example String format: "15s Left! Active Guess: [A3B_]"
     */
    private void parseKeypadActiveGuessLine(String line) {
        gameState.stage = "KEYPAD";
        gameState.keypadStarted = true;
        gameState.keypadStatus = "Codebreaker active.";
        gameState.keypadFeedback = "Enter code and press #.";

        // Find and isolate remaining time numbers preceding the "Active Guess:" label
        int activeIndex = line.indexOf("Active Guess:");
        if (activeIndex >= 0) {
            String beforeActive = line.substring(0, activeIndex);
            int[] nums = extractIntegers(beforeActive);
            if (nums.length > 0) {
                gameState.keypadSecondsLeft = nums[nums.length - 1];
            }
        }

        // Parse user characters held inside brackted structures
        int openBracket = line.indexOf("[");
        int closeBracket = line.indexOf("]");

        if (openBracket >= 0 && closeBracket > openBracket) {
            gameState.keypadGuess = line.substring(openBracket + 1, closeBracket).trim();
        } else if (openBracket >= 0) {
            gameState.keypadGuess = line.substring(openBracket + 1).replace("]", "").trim();
        }
    }

    /**
     * Regex Engine: Parses detailed character analysis reports for Keypad Codebreaker.
     * Maps inputs to Bullseyes (correct character in spot) and Cows (correct character, wrong spot).
     * Example input: "Analysis: A (spot) 3 (misplaced) B (wrong)"
     */
    private void parseArduinoAnalysisLine(String line) {
        String cleaned = line;
        int analysisIndex = cleaned.toLowerCase().indexOf("analysis:");

        if (analysisIndex >= 0) {
            cleaned = cleaned.substring(analysisIndex + "analysis:".length()).trim();
        }

        // Regex captures characters followed by parenthesized classifications: e.g. "A (spot)"
        Matcher matcher = Pattern.compile("([0-9A-Da-d])\\s*\\(([^)]*)\\)").matcher(cleaned);

        StringBuilder allFeedback = new StringBuilder();
        StringBuilder bullseyes = new StringBuilder();
        StringBuilder cows = new StringBuilder();
        StringBuilder wrong = new StringBuilder();

        int bullseyeCount = 0;
        int cowCount = 0;

        while (matcher.find()) {
            String character = matcher.group(1).toUpperCase();
            String label = matcher.group(2).toLowerCase();
            String result;

            if (label.contains("spot") || label.contains("bull") || label.contains("correct position")) {
                result = "Bullseye";
                bullseyeCount++;
                appendListValue(bullseyes, character);
            } else if (label.contains("misplaced") || label.contains("cow") || label.contains("wrong position")) {
                result = "Cow";
                cowCount++;
                appendListValue(cows, character);
            } else {
                result = "Wrong";
                appendListValue(wrong, character);
            }

            if (allFeedback.length() > 0) {
                allFeedback.append(" | ");
            }
            allFeedback.append(character).append(" = ").append(result);
        }

        // Update state data properties with grouped lists of evaluated characters
        if (allFeedback.length() > 0) {
            gameState.keypadLetterFeedback = allFeedback.toString();
            gameState.keypadBullseyeLetters = bullseyes.toString();
            gameState.keypadCowLetters = cows.toString();
            gameState.keypadWrongLetters = wrong.toString();
            gameState.keypadBullseyes = bullseyeCount;
            gameState.keypadCows = cowCount;
        }
    }

    /**
     * Tokenizes structured endgame metrics.
     * Example String format: "GAME|FINISHED|TOTAL|145.30"
     */
    private void parseFinishedLine(String line) {
        String[] parts = line.split("\\|");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("TOTAL") && i + 1 < parts.length) {
                double seconds = parseDouble(parts[i + 1], 0.0);
                if (seconds > 0.0) {
                    gameState.totalGameTimeSeconds = seconds;
                    gameState.totalGameTimeText = formatSeconds(seconds);
                }
            }
        }
    }

    /**
     * Fallback timer text parser that extracts decimal values representing total playtime.
     */
    private void parseTotalTimeText(String line) {
        double seconds = extractFirstDouble(line);
        if (seconds > 0.0) {
            gameState.totalGameTimeSeconds = seconds;
            gameState.totalGameTimeText = formatSeconds(seconds);
        }
    }

    /**
     * Utility: Formats raw double values of seconds into structured human-readable text.
     */
    private String formatSeconds(double seconds) {
        int totalSeconds = (int) Math.round(seconds);
        int minutes = totalSeconds / 60;
        int remainingSeconds = totalSeconds % 60;

        if (minutes > 0) {
            return minutes + " min " + remainingSeconds + " sec";
        }
        return remainingSeconds + " sec";
    }

    /**
     * Utility helper that appends comma-separated string labels.
     */
    private void appendListValue(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(value);
    }

    /**
     * Extracts and retrieves contents isolated inside square brackets '[ ]'.
     */
    private String parseBracketValue(String line) {
        int openBracket = line.indexOf("[");
        int closeBracket = line.indexOf("]");

        if (openBracket >= 0 && closeBracket > openBracket) {
            return line.substring(openBracket + 1, closeBracket).trim();
        }
        return "";
    }

    /**
     * Generates a combined Morse code translation representation for an input word.
     */
    private String buildMorseCodeForLetters(String letters) {
        if (letters == null || letters.trim().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < letters.length(); i++) {
            char c = Character.toUpperCase(letters.charAt(i));
            String code = getMorseForChar(c);

            if (!code.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(code);
            }
        }
        return builder.toString();
    }

    /**
     * Basic lookup matrix mapping core puzzle characters to their respective Morse Code strings.
     */
    private String getMorseForChar(char letter) {
        if (letter == 'A') return ".-";
        if (letter == 'O') return "---";
        if (letter == 'P') return ".--.";
        if (letter == 'E') return ".";
        if (letter == 'Q') return "--.-";
        if (letter == 'R') return ".-.";
        if (letter == 'S') return "...";
        if (letter == 'T') return "-";
        return "";
    }

    /**
     * State machine updater logic tracking RFID scans during initial calibration setup stages.
     */
    private void updateRFIDSetupProgress(int tagNumber) {
        if (tagNumber <= 0) {
            return;
        }

        if (tagNumber > rfidTagsRegistered) {
            rfidTagsRegistered = tagNumber;
        }

        appendConsole("[GUI] RFID Tag " + tagNumber + " scanned.");

        if (rfidTagsRegistered == 1) {
            setupPanel.setStepMessage(1, "RFID Tag 1 scanned. Remove it.");
        } else if (rfidTagsRegistered == 2) {
            setupPanel.setStepMessage(1, "RFID Tag 2 scanned. Remove it.");
        } else if (rfidTagsRegistered >= 3) {
            setupPanel.completeStep(1, "RFID Tag 3 scanned. Remove it. All 3 RFID tags are ready.");
        }
    }

    /**
     * Regex Parser: Extracts the first consecutive integers discovered within a string.
     */
    private int extractFirstInteger(String text) {
        String cleaned = text.replaceAll("[^0-9]", " ").trim();

        if (cleaned.isEmpty()) {
            return 0;
        }

        String[] parts = cleaned.split("\\s+");
        try {
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Regex Parser: Isolates and returns all occurrences of integer arrays throughout text.
     */
    private int[] extractIntegers(String text) {
        ArrayList<Integer> values = new ArrayList<Integer>();
        Matcher matcher = Pattern.compile("\\d+").matcher(text);

        while (matcher.find()) {
            values.add(Integer.parseInt(matcher.group()));
        }

        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    /**
     * Parses the tag index digit from structured serial sequences.
     */
    private int parseStructuredTagNumber(String line) {
        String[] parts = line.split("\\|");

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("TAG") && i + 1 < parts.length) {
                return parseInt(parts[i + 1], 0);
            }
        }
        return 0;
    }

    /**
     * Parses standard strings into integers with a clean safe fallback handling.
     */
    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Parses standard strings into double precision floats with a safe fallback.
     */
    private double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Regex Parser: Finds and isolates the first matching double floating-point value.
     */
    private double extractFirstDouble(String line) {
        String cleaned = line.replaceAll("[^0-9.]", " ").trim();
        String[] parts = cleaned.split("\\s+");

        if (parts.length == 0) {
            return 0.0;
        }

        try {
            return Double.parseDouble(parts[0]);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Triggers active game start, resets base snapshot properties, 
     * switches window layouts, and fires connection start packets to the Arduino.
     */
    private void beginGame() {
        appendConsole("[GUI] Begin Game clicked.");

        // Clean slate initialize starting values for Minigame 1 (Rotary Encoder)
        gameState.stage = "ENCODER";
        gameState.difficulty = currentDifficulty;
        gameState.message = "Game running.";
        gameState.encoderPosition = 0;
        gameState.encoderTarget = 0;
        gameState.encoderDistance = 0;
        gameState.encoderDuplicateLetterClue = "Clue: Two of the same letters are present.";

        encoderPanel.updateView(gameState);

        // Slide target frame layout into view
        cardLayout.show(gameContainer, "ENCODER");

        // Send confirmation signal over serial TX line so Arduino arms game modules
        if (serialPort != null) {
            try {
                serialPort.printLine("START_GAME");
                appendConsole("[GUI] Sent START_GAME to Arduino.");
            } catch (Exception e) {
                appendConsole("[ERROR] Could not send START_GAME: " + e.getMessage());
            }
        }
    }

    /**
     * Developer Override: Simulates complete hardware handshake configurations instantly
     * to skip manual calibration for GUI layout testing.
     */
    private void simulateSetupComplete() {
        currentDifficulty = "EASY";
        gameState.difficulty = currentDifficulty;

        setupPanel.completeStep(0, "Arduino and receiver connected.");
        setupPanel.completeStep(1, "All 3 RFID tags scanned successfully.");
        setupPanel.completeStep(2, "Metal touch sensor calibrated.");
        setupPanel.completeStep(3, "Difficulty selected: EASY.");
        setupPanel.completeStep(4, "Setup values confirmed.");
        setupPanel.completeStep(5, "Ready to begin.");
        setupPanel.setupComplete();

        appendConsole("[GUI] Simulated setup completed.");
    }

    /**
     * Developer Overrides: Hardcode-populates GameSnapshot models with demo values
     * to test layouts across all 4 stages.
     */
    private void showEncoderDemo() {
        gameState.stage = "ENCODER";
        gameState.encoderPosition = 45;
        gameState.encoderTarget = 0;
        gameState.encoderDistance = 25;
        gameState.encoderDuplicateLetterClue = "Clue: Two of the same letters are present.";

        encoderPanel.updateView(gameState);
        cardLayout.show(gameContainer, "ENCODER");
    }

    private void showMorseDemo() {
        gameState.stage = "MORSE";
        gameState.morseHoldSeconds = 3.2;
        gameState.morseSequence = ".";
        gameState.morseTargetLetters = "A";
        gameState.morseTargetCode = ".-";

        currentMorseSequence = ".";
        currentMorseTargetLetters = "A";
        currentMorseTargetCode = ".-";

        morsePanel.updateView(gameState);
        cardLayout.show(gameContainer, "MORSE");
    }

    private void showKeypadDemo() {
        gameState.stage = "KEYPAD";
        gameState.keypadSecondsLeft = 45;
        gameState.keypadGuess = "A3B";
        gameState.keypadRunNumber = 1;
        gameState.keypadTotalRuns = 2;
        gameState.keypadCodeLength = 4;
        gameState.keypadStartInstruction = "Press *** to start";
        gameState.keypadStatus = "Codebreaker active.";
        gameState.keypadFeedback = "Guess analyzed.";
        gameState.keypadBullseyes = 1;
        gameState.keypadCows = 1;
        gameState.keypadBullseyeLetters = "A";
        gameState.keypadCowLetters = "3";
        gameState.keypadWrongLetters = "B";
        gameState.keypadLetterFeedback = "A = Bullseye | 3 = Cow | B = Wrong";
        gameState.keypadStarted = true;

        keypadPanel.updateView(gameState);
        cardLayout.show(gameContainer, "KEYPAD");
    }

    private void showRFIDDemo() {
        gameState.stage = "RFID";
        gameState.rfidRequiredTagNumber = 2;
        gameState.rfidCheckpointName = "Encoder";
        gameState.message = "Scan RFID Tag 2.";

        rfidPanel.updateView(gameState);
        cardLayout.show(gameContainer, "RFID");
    }

    private void showFinishDemo() {
        gameState.stage = "FINISH";
        gameState.finished = true;
        gameState.totalGameTimeSeconds = 145.30;
        gameState.totalGameTimeText = formatSeconds(145.30);

        finishPanel.updateView(gameState);
        cardLayout.show(gameContainer, "FINISH");
    }

    /**
     * Resets local variable containers and clears the centralized game snapshot properties.
     */
    private void resetGameState() {
        gameState.stage = "SETUP";
        gameState.difficulty = "EASY";
        gameState.message = "Waiting for Arduino...";
        gameState.lives = 3;

        gameState.encoderPosition = 0;
        gameState.encoderTarget = 0;
        gameState.encoderDistance = 0;
        gameState.encoderDuplicateLetterClue = "Clue: Two of the same letters are present.";

        gameState.morseHoldSeconds = 0.0;
        gameState.morseSequence = "";
        gameState.morseTargetLetters = "";
        gameState.morseTargetCode = "";

        currentMorseSequence = "";
        currentMorseTargetLetters = "";
        currentMorseTargetCode = "";

        gameState.keypadSecondsLeft = 0;
        gameState.keypadGuess = "";
        gameState.keypadRunNumber = 1;
        gameState.keypadTotalRuns = 1;
        gameState.keypadCodeLength = 4;
        gameState.keypadStartInstruction = "Press *** to start";
        gameState.keypadStatus = "Waiting to start";
        gameState.keypadFeedback = "";
        gameState.keypadBullseyes = 0;
        gameState.keypadCows = 0;
        gameState.keypadBullseyeLetters = "";
        gameState.keypadCowLetters = "";
        gameState.keypadWrongLetters = "";
        gameState.keypadLetterFeedback = "";
        gameState.keypadStarted = false;

        gameState.rfidRequiredTagNumber = 0;
        gameState.rfidCheckpointName = "";

        gameState.totalGameTimeSeconds = 0.0;
        gameState.totalGameTimeText = "";

        gameState.finished = false;

        currentDifficulty = "EASY";
        rfidTagsRegistered = 0;
        lastCompletedMinigame = "";
    }

    /**
     * Appends an entry string to the log output console and auto-scrolls view container to the bottom.
     */
    private void appendConsole(String text) {
        consoleArea.append(text + System.lineSeparator());
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }

    /**
     * Entry Point of the GUI program. Initializes on the Swing Event Dispatch Thread (EDT).
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new HeistRushGUI().setVisible(true);
            }
        });
    }
}