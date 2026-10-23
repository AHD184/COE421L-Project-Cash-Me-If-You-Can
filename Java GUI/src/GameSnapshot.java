/**
 * Simple data container that holds the entire game state.
 * This is passed from the Arduino serial communication listener to the GUI screens
 * to update what the player sees.
 */
public class GameSnapshot {

    // General game settings
    public String stage = "SETUP";
    public String difficulty = "EASY";
    public String message = "Waiting for Arduino...";

    public int lives = 3;

    // Minigame 1: Frequency Dial (Rotary Encoder) variables
    public int encoderPosition = 0;
    public int encoderTarget = 0;
    public int encoderDistance = 0;

    public String encoderDuplicateLetterClue = "Clue: Two of the same letters are present.";

    // Minigame 2: Morse Code variables
    public double morseHoldSeconds = 0.0;
    public String morseSequence = "";

    public String morseTargetLetters = "";
    public String morseTargetCode = "";

    // Minigame 3: Codebreaker (Keypad) variables
    public int keypadSecondsLeft = 0;
    public String keypadGuess = "";

    public int keypadRunNumber = 1;
    public int keypadTotalRuns = 1;
    public int keypadCodeLength = 4;

    public String keypadStartInstruction = "Press *** to start";
    public String keypadStatus = "Waiting to start";
    public String keypadFeedback = "";

    public int keypadBullseyes = 0;
    public int keypadCows = 0;

    public String keypadBullseyeLetters = "";
    public String keypadCowLetters = "";
    public String keypadWrongLetters = "";
    public String keypadLetterFeedback = "";

    public boolean keypadStarted = false;

    // Minigame 4: RFID Scan variables
    public int rfidRequiredTagNumber = 0;
    public String rfidCheckpointName = "";

    // Timer tracking variables for the end screen
    public double totalGameTimeSeconds = 0.0;
    public String totalGameTimeText = "";

    public boolean finished = false;
}