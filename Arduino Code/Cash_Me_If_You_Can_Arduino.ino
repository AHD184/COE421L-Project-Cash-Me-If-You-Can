// Cash Me if You Can
//
// We integrated the rotary encoder, touch sensor, 4x4 keypad, RFID reader, one buzzers, ZigBee communication, game difficulty, lives, and timing.
// The Arduino sends state and event messages to the Java controller through Serial2.
// We use Serial2 for communication with the Java application over the ZigBee link.

#define Serial Serial2

int gameState = 0;
unsigned long globalStartTime = 0;
unsigned long encoderStartTime = 0;
unsigned long encoderEndTime = 0;
unsigned long encoderDuration = 0;
unsigned long morseStartTime = 0;
unsigned long morseEndTime = 0;
unsigned long morseDuration = 0;
unsigned long game3StartTime = 0;
unsigned long game3EndTime = 0;
unsigned long game3Duration = 0;
unsigned long finalEndTime = 0;
unsigned long totalDuration = 0;

int difficulty = 0;
int maxLives = 3;
int currentLives = 3;
int encoderInstance = 0;
int totalEncoderInstances = 1;
int encoderOvershootCount = 0;
int morseInstance = 0;
int totalMorseInstances = 2;
int lettersPerInstance = 1;
String targetMorseString = "";
int keypadInstance = 0;
int totalKeypadInstances = 1;
int keypadCodeLength = 4;
unsigned long KEYPAD_TIMELIMIT = 60000;

// Rotary encoder inputs and the buzzer output.
#define CLK_PIN 2
#define DT_PIN 3
#define SW_PIN 4
#define BUZZER_PIN 8
int position = 50;
int minPosition = 0;
int maxPosition = 100;
int targetPosition = 70;
int tolerance = 5;
int detectionRange = 35;
const int COUNTS_PER_STEP = 4;
volatile long encoderCount = 50 * COUNTS_PER_STEP;
volatile int lastEncoded = 0;
int lastPrintedPosition = -1;
unsigned long lastButtonPress = 0;

// Touch-sensor calibration values and Morse-entry timing thresholds.
int analogPin = A3;
int ledPin = 13;
int touchStartThreshold = 0;
int touchEndThreshold = 0;
bool touchIsHigher = true;
const unsigned long DOT_MIN_TIME = 2000;
const unsigned long DASH_MIN_TIME = 5000;
const unsigned long STABLE_TIME = 300;
char letterPool[] = {'A', 'O', 'P', 'E', 'Q', 'R', 'S', 'T'};
const int poolSize = 8;
bool isTouching = false;
bool lastRawTouch = false;
unsigned long rawChangedAt = 0;
unsigned long touchStartTime = 0;
unsigned long lastHoldPrintTime = 0;
String currentMorse = "";

// Row and column connections for the 4x4 matrix keypad.
byte rowPins[4] = {23, 25, 27, 29};
byte colPins[4] = {47, 49, 51, 53};
char hexaKeys[4][4] = {
  {'1','2','3','A'},
  {'4','5','6','B'},
  {'7','8','9','C'},
  {'*','0','#','D'}
};
String targetKeypadCode = "";
String keypadInputBuffer = "";
bool keypadGameStarted = false;
bool keypadSetupDone = false;
bool isFirstKeypadAttempt = true;
unsigned long keypadCountdownStart = 0;
unsigned long lastCountdownPrint = 0;

// Stores the three registered tag IDs and their randomized minigame assignments.
const int RFID_ENABLE_PIN = 30;
String rfidUIDs[3] = {"", "", ""};
int minigameToTag[3] = {-1, -1, -1};

// ZigBee communication
// Sends a formatted message to the Java controller through Serial2.
void sendZigbee(String msg) {
  Serial2.println(msg);
  Serial2.println("[ZB] " + msg); 
}

// RFID registration and progression, we use Serial1
// Discards unread bytes left in the RFID serial buffer before a new scan.
void clearRFIDBuffer() {
  while (Serial1.available()) {
    Serial1.read();
  }
}

// Waits for a quiet interval from the reader so the same tag is not accepted twice.
void waitForTagRemoved() {
  Serial.println("Remove the tag from the reader...");
  unsigned long quietStart = millis();
  while (millis() - quietStart < 1200) {
    if (Serial1.available()) {
      clearRFIDBuffer();
      quietStart = millis();
    }
    delay(20);
  }
  Serial.println("Tag removed / reader is clear.");
}

// Attempts to read one complete 10-character Parallax RFID identifier.
// An empty string is returned when no valid tag is currently available.
String tryReadParallaxTagNonBlocking() {
  if (!Serial1.available()) return "";
  int firstByte = Serial1.read();
  if (firstByte != 0x0A) return "";
  String tagID = "";
  unsigned long startTime = millis();
  while (tagID.length() < 10) {
    if (Serial1.available()) {
      char c = Serial1.read();
      tagID += c;
    }
    if (millis() - startTime > 1000) return "";
  }
  if (Serial1.available()) Serial1.read();
  return tagID;
}

// Repeatedly checks the reader until a complete RFID identifier is received.
String readParallaxTagBlocking() {
  while (true) {
    String uid = tryReadParallaxTagNonBlocking();
    if (uid != "") return uid;
    delay(20);
  }
}

// Registers three unique RFID tags, then randomly assigns one tag to each minigame.
void initializeRFIDMapping() {
  Serial.println();
  Serial.println("==========================================");
  Serial.println("   RFID INITIALIZATION: REGISTER 3 TAGS   ");
  Serial.println("==========================================");
  Serial.println("Scan 3 different RFID tags.");
  Serial.println();
  clearRFIDBuffer();
  // Register each tag once and reject duplicate scans.
  for (int i = 0; i < 3; i++) {
    Serial.print("Scan tag #");
    Serial.print(i + 1);
    Serial.println(" now...");
    while (true) {
      String uid = readParallaxTagBlocking();
      bool duplicate = false;
      for (int j = 0; j < i; j++) {
        if (rfidUIDs[j] == uid) {
          duplicate = true;
          break;
        }
      }
      if (duplicate) {
        Serial.print("This tag is already registered: ");
        Serial.println(uid);
        Serial.println("Use a different tag.");
        waitForTagRemoved();
        Serial.print("Scan tag #");
        Serial.print(i + 1);
        Serial.println(" again...");
      } else {
        rfidUIDs[i] = uid;
        Serial.print("Registered tag #");
        Serial.print(i + 1);
        Serial.print(": ");
        Serial.println(uid);
        waitForTagRemoved();
        break;
      }
    }
  }
  // Randomize which physical tag unlocks each minigame.
  int indices[3] = {0, 1, 2};
  for (int i = 2; i > 0; i--) {
    int j = random(0, i + 1);
    int temp = indices[i];
    indices[i] = indices[j];
    indices[j] = temp;
  }
  for (int m = 0; m < 3; m++) {
    minigameToTag[m] = indices[m];
  }
  Serial.println();
  Serial.println("RFID mapping complete:");
  Serial.print("Encoder -> Tag #");
  Serial.println(minigameToTag[0] + 1);
  Serial.print("Morse   -> Tag #");
  Serial.println(minigameToTag[1] + 1);
  Serial.print("Keypad  -> Tag #");
  Serial.println(minigameToTag[2] + 1);
  Serial.println("==========================================");
  delay(1000);
}

// Holds progression after a minigame until the player scans its assigned RFID tag.
void waitForRFIDAndAdvance(int minigameIndex) {
  if (minigameIndex < 0 || minigameIndex > 2) return;
  int expectedTagIndex = minigameToTag[minigameIndex];
  String expectedUID = rfidUIDs[expectedTagIndex];
  clearRFIDBuffer();
  Serial.println();
  Serial.println("==========================================");
  if (minigameIndex == 0) Serial.println("ENCODER COMPLETE.");
  else if (minigameIndex == 1) Serial.println("MORSE COMPLETE.");
  else if (minigameIndex == 2) Serial.println("KEYPAD COMPLETE.");
  Serial.println("Scan the correct RFID tag to continue.");
  Serial.print("Expected tag number: ");
  Serial.println(expectedTagIndex + 1);
  Serial.println("==========================================");
  while (true) {
    String scannedUID = tryReadParallaxTagNonBlocking();
    if (scannedUID != "") {
      Serial.print("Scanned UID: ");
      Serial.println(scannedUID);
      if (scannedUID == expectedUID) {
        Serial.println("Correct RFID tag. Proceeding...");
        sendZigbee("RFID|CORRECT");
        waitForTagRemoved();
        return;
      } else {
        Serial.println("Wrong RFID tag. Try again.");
        waitForTagRemoved();
        Serial.println("\nScan again...");
      }
    }
    delay(20);
  }
}

// Arduino setup and main game loop
// Initializes communication ports, peripherals, interrupts, player setup, and timers.
void setup() {
  // Start ZigBee communication and the RFID reader serial port.
  Serial.begin(9600);
  Serial2.begin(9600);

  sendZigbee("BOOT|CASH_ME_IF_YOU_CAN_READY");
  Serial1.begin(2400);

  // Enable the RFID reader and prepare its serial input.
  pinMode(RFID_ENABLE_PIN, OUTPUT);
  digitalWrite(RFID_ENABLE_PIN, LOW);
  Serial.println("Parallax RFID Reader Ready.");

  // Seed the random generator using an unused analog input.
  randomSeed(analogRead(A1));

  // Configure the encoder, button, buzzer, and indicator LED pins.
  pinMode(CLK_PIN, INPUT_PULLUP);
  pinMode(DT_PIN, INPUT_PULLUP);
  pinMode(SW_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(ledPin, OUTPUT);

  // Store the encoder's initial state before enabling interrupts.
  int MSB = digitalRead(CLK_PIN);
  int LSB = digitalRead(DT_PIN);
  lastEncoded = (MSB << 1) | LSB;

  // Update the encoder count whenever either encoder signal changes.
  attachInterrupt(digitalPinToInterrupt(CLK_PIN), updateEncoder, CHANGE);
  attachInterrupt(digitalPinToInterrupt(DT_PIN), updateEncoder, CHANGE);

  // Register the RFID tags, calibrate the touch sensor, and load difficulty settings.
  initializeRFIDMapping();
  calibrateSensor();
  selectDifficultyMenu();
  applyDifficultyConfiguration();

  // Inform the Java controller that the encoder minigame is starting.
  sendZigbee("STATE|ENCODER");

  // Send the selected difficulty so the Java GUI uses the same configuration.
  if (difficulty == 0)      sendZigbee("DIFFICULTY|EASY");
  else if (difficulty == 1) sendZigbee("DIFFICULTY|MEDIUM");
  else if (difficulty == 2) sendZigbee("DIFFICULTY|HARD");

  // Start the total game timer and the encoder minigame timer.
  globalStartTime = millis();
  encoderStartTime = millis();
}

// Main game loop
// gameState decides which minigame is allowed to run.
void loop() {
  // Read the encoder, update the guidance sound, and check the reset button.
  if (gameState == 0) {
    updatePositionFromEncoder();
    updateBuzzer();
    handleResetButton();
    int distance = abs(position - targetPosition);
    if (distance == 0) {
      noTone(BUZZER_PIN);
      Serial.println();
      Serial.print(">>> Encoder Target #");
      Serial.print(encoderInstance + 1);
      Serial.println(" Found! <<<");
      encoderInstance++;
      encoderOvershootCount = 0;

      // More encoder rounds are required on medium and hard difficulty.
      if (encoderInstance < totalEncoderInstances) {
        if (difficulty == 1) targetPosition = random(30, 130);
        else if (difficulty == 2) targetPosition = random(40, 180);

        position = 50;
        noInterrupts();
        encoderCount = 50 * COUNTS_PER_STEP;
        interrupts();
        lastPrintedPosition = -1;

        Serial.println("\n------------------------------------------");
        Serial.print(" Next Target Initialized (");
        Serial.print(encoderInstance + 1);
        Serial.print("/");
        Serial.print(totalEncoderInstances);
        Serial.print("): Match position [ ");
        Serial.print(targetPosition);
        Serial.println(" ]");
        Serial.println("------------------------------------------");
      } else {
        // All encoder targets are complete. Record the time, check RFID, and move to Morse.
        encoderEndTime = millis();
        encoderDuration = encoderEndTime - encoderStartTime;
        Serial.println();
        Serial.println(">>> MINIGAME 1 COMPLETE! All paths aligned. <<<");
        Serial.print("Time taken for Minigame 1: ");
        Serial.print(encoderDuration / 1000.0);
        Serial.println(" seconds.");
        sendZigbee("EVENT|ENCODER_COMPLETE|TIME|" + String(encoderDuration / 1000.0));
        sendZigbee("RFID|WAIT|ENCODER");
        waitForRFIDAndAdvance(0);
        gameState = 1;
        sendZigbee("STATE|MORSE");
        morseStartTime = millis();
        currentLives = maxLives;
        setupMorseInstanceProfile();
      }
    }
  }
  // Morse input is handled by timing stable touches on the metal sensor.
  else if (gameState == 1) {
    int analogVal = analogRead(analogPin);
    bool rawTouch = getRawTouch(analogVal);
    // Remember when the raw reading changed so brief noise can be ignored.
    if (rawTouch != lastRawTouch) {
      rawChangedAt = millis();
      lastRawTouch = rawTouch;
    }
    // Accept the new state only after it stays unchanged for STABLE_TIME.
    if (rawTouch != isTouching && millis() - rawChangedAt >= STABLE_TIME) {
      isTouching = rawTouch;
      if (isTouching) touchStarted();
      else touchEnded();
    }
    if (isTouching && millis() - lastHoldPrintTime >= 1000) {
      unsigned long holdTime = millis() - touchStartTime;
      Serial.print("Holding for: ");
      Serial.print(holdTime / 1000.0);
      Serial.println(" seconds");
      lastHoldPrintTime = millis();
    }
  }
  // The keypad puzzle handles activation, the countdown, guesses, and feedback.
  else if (gameState == 2) {
    // Set up the keypad and create the first code only once when this minigame begins.
    if (!keypadSetupDone) {
      for (int i = 0; i < 4; i++) {
        pinMode(rowPins[i], INPUT_PULLUP);
        pinMode(colPins[i], INPUT);
      }
      generateNewKeypadCode();
      keypadSetupDone = true;
      isFirstKeypadAttempt = true; 
    }
    char key = customGetKey();
    // The first round starts with ***, while later rounds restart with one *.
    if (!keypadGameStarted) {
      if (key != '\0') {
        if (isFirstKeypadAttempt) {
          if (key == '*') {
            keypadInputBuffer += key;
            Serial.print(key);
          } else {
            keypadInputBuffer = ""; 
          }
          if (keypadInputBuffer.endsWith("***")) {
            keypadGameStarted = true;
            isFirstKeypadAttempt = false; 
            keypadCountdownStart = millis();
            game3StartTime = millis();
            lastCountdownPrint = millis();
            keypadInputBuffer = "";
            Serial.println();
            Serial.print(">>> CODEBREAKER ACTIVE (Run ");
            Serial.print(keypadInstance + 1);
            Serial.print("/");
            Serial.print(totalKeypadInstances);
            Serial.print(")! ");
            Serial.print(KEYPAD_TIMELIMIT / 1000);
            Serial.println("s remaining.");
            Serial.println("Deduce the code. Submit with #");
            Serial.print(KEYPAD_TIMELIMIT / 1000);
            Serial.print("s | Active Guess: [ ");
          }
        } else {
          if (key == '*') {
            keypadGameStarted = true;
            keypadCountdownStart = millis();
            lastCountdownPrint = millis();
            keypadInputBuffer = "";
            Serial.println();
            Serial.print(">>> CODEBREAKER RE-ARMED (Run ");
            Serial.print(keypadInstance + 1);
            Serial.print("/");
            Serial.print(totalKeypadInstances);
            Serial.print(")! ");
            Serial.print(KEYPAD_TIMELIMIT / 1000);
            Serial.println("s remaining.");
            Serial.print(KEYPAD_TIMELIMIT / 1000);
            Serial.print("s | Active Guess: [ ");
          }
        }
      }
    } else {
      // From this point onward, every guess uses the same running countdown.
      unsigned long elapsedCountdown = millis() - keypadCountdownStart;
      // Running out of time costs a life and generates another code.
      if (elapsedCountdown >= KEYPAD_TIMELIMIT) {
        Serial.println(" ]");
        Serial.println("\n ERROR: TIME LIMIT EXPIRED!");
        Serial.print(" System locked down. The correct code was: ");
        Serial.println(targetKeypadCode);

        currentLives--;
        Serial.print(" Remaining Lives: ");
        Serial.println(currentLives);
        if (currentLives <= 0) {
          Serial.println("\n CRITICAL FAILURE: Out of safety attempts. Purging minigame sequence...");
          currentLives = maxLives;
          keypadInstance = 0;
          resetKeypadGame();
        } else {
          Serial.println("\n Resetting this sequence instance. Generating new trace pattern...");
          keypadInputBuffer = "";
          keypadGameStarted = false; 
          generateNewKeypadCode();
        }
      } else {
        if (millis() - lastCountdownPrint >= 1000) {
          int secondsLeft = (KEYPAD_TIMELIMIT - elapsedCountdown) / 1000;
          Serial.println(" ]");
          Serial.print(secondsLeft);
          Serial.print("s | Active Guess: [ ");
          Serial.print(keypadInputBuffer);
          lastCountdownPrint = millis();
        }
        if (key != '\0') {
          if (key != '*' && key != '#') {
            keypadInputBuffer += key;
            Serial.print(key);
          }
          // The # key submits whatever is currently in the input buffer.
          if (key == '#') {
            String guess = keypadInputBuffer;
            Serial.println(" ]");
            if (guess.length() != keypadCodeLength) {
              Serial.println("\n------------------------------------------");
              Serial.print(" INVALID LENGTH: Expected exactly ");
              Serial.print(keypadCodeLength);
              Serial.println(" digits before pressing '#'.");
              Serial.println("------------------------------------------");
              keypadInputBuffer = "";
              Serial.print((KEYPAD_TIMELIMIT - (millis() - keypadCountdownStart)) / 1000);
              Serial.print("s | Active Guess: [ ");
            } else {
              // A correct guess finishes this round or moves to the final RFID check.
              if (guess == targetKeypadCode) {
                Serial.println("\n==========================================");
                Serial.print(" ACCESS CONFIRMED: Sub-profile ");
                Serial.print(keypadInstance + 1);
                Serial.println(" Cleared!");
                Serial.println("==========================================");
                keypadInstance++;
                if (keypadInstance < totalKeypadInstances) {
                  keypadGameStarted = false;
                  keypadInputBuffer = "";
                  generateNewKeypadCode();
                } else {
                  game3EndTime = millis();
                  game3Duration = game3EndTime - game3StartTime;
                  sendZigbee("RFID|WAIT|ENCODER");
                  waitForRFIDAndAdvance(2);
                  finalEndTime = millis();
                  gameState = 3;
                  totalDuration = finalEndTime - globalStartTime;
                  sendZigbee("GAME|FINISHED|TOTAL|" + String(totalDuration / 1000.0));
                }
              } else {
                // Bullseyes match in value and position. Cows match only in value.
                int bullseyes = 0;
                int cows = 0;
                char statusMap[6] = {'X', 'X', 'X', 'X', 'X', 'X'};
                bool targetMatched[6] = {false, false, false, false, false, false};
                bool guessMatched[6] = {false, false, false, false, false, false};
                for (int i = 0; i < keypadCodeLength; i++) {
                  if (guess[i] == targetKeypadCode[i]) {
                    bullseyes++;
                    statusMap[i] = 'B';
                    targetMatched[i] = true;
                    guessMatched[i] = true;
                  }
                }
                for (int i = 0; i < keypadCodeLength; i++) {
                  if (!guessMatched[i]) {
                    for (int j = 0; j < keypadCodeLength; j++) {
                      if (!targetMatched[j] && guess[i] == targetKeypadCode[j]) {
                        cows++;
                        statusMap[i] = 'C';
                        targetMatched[j] = true;
                        break;
                      }
                    }
                  }
                }
                Serial.println("\n------------------------------------------");
                Serial.print(" EVALUATING GUESS: [ "); Serial.print(guess); Serial.println(" ]");
                Serial.print(" ANALYSIS: ");
                for (int i = 0; i < keypadCodeLength; i++) {
                  Serial.print(" ");
                  Serial.print(guess[i]);
                  if (statusMap[i] == 'B')      Serial.print("( Spot-On)");
                  else if (statusMap[i] == 'C') Serial.print("( Misplaced)");
                  else                         Serial.print("( Wrong)");
                }
                Serial.println();
                Serial.print(" Total Bullseyes : "); Serial.println(bullseyes);
                Serial.print(" Total Cows      : "); Serial.println(cows);
                Serial.println(" Incorrect Sequence! Try again quickly before timer expires.");
                Serial.print(" Lives Safe: "); Serial.println(currentLives);
                Serial.println("------------------------------------------");
                keypadInputBuffer = "";
                Serial.print((KEYPAD_TIMELIMIT - (millis() - keypadCountdownStart)) / 1000);
                Serial.print("s | Active Guess: [ ");
              }
            }
          }
        }
      }
    }
  }
  // Print the final times once, then leave the completed game idle.
  else if (gameState == 3) {
    morseDuration = morseEndTime - morseStartTime;
    totalDuration = finalEndTime - globalStartTime;
    Serial.println();
    Serial.println("==========================================");
    Serial.println("         ALL MINIGAMES COMPLETED!         ");
    Serial.println("==========================================");
    Serial.print("Difficulty Level          : ");
    if (difficulty == 0) Serial.println("EASY");
    else if (difficulty == 1) Serial.println("MEDIUM");
    else if (difficulty == 2) Serial.println("HARD");
    Serial.print("Minigame 1 (Encoder) Time : ");
    Serial.print(encoderDuration / 1000.0);
    Serial.println(" seconds");
    Serial.print("Minigame 2 (Morse) Time   : ");
    Serial.print(morseDuration / 1000.0);
    Serial.println(" seconds");
    Serial.print("Minigame 3 (Keypad) Time  : ");
    Serial.print(game3Duration / 1000.0);
    Serial.println(" seconds");
    Serial.println("------------------------------------------");
    Serial.print("Total Runtime             : ");
    Serial.print(totalDuration / 1000.0);
    Serial.println(" seconds");
    Serial.println("==========================================");
    while (true) {
      delay(1000);
    }
  }
}

// Difficulty selection
// Short presses cycle through the options. A 1.5 second hold confirms the choice.
void selectDifficultyMenu() {
  Serial.println();
  Serial.println("==========================================");
  Serial.println("       HARDWARE CONFIGURATION PROFILE       ");
  Serial.println("==========================================");
  Serial.println("Click Encoder Button (SW) to CYCLE levels.");
  Serial.println("HOLD Encoder Button for 1.5 seconds to SELECT.");
  Serial.println("------------------------------------------");
  int selectedOption = 0;
  printMenuOption(selectedOption);
  // Stay in the menu until the player confirms an option.
  while (true) {
    if (digitalRead(SW_PIN) == LOW) {
      delay(40);
      unsigned long pressTime = millis();
      bool isHold = false;
      while (digitalRead(SW_PIN) == LOW) {
        if (millis() - pressTime >= 1500) {
          isHold = true;
          break;
        }
        delay(10);
      }
      // A long press confirms the current option; a short press moves to the next one.
      if (isHold) {
        difficulty = selectedOption;
        Serial.println("\n>>> LEVEL LOCKED IN SUCCESSFUL! SYSTEM ARMED. <<<\n");
        while (digitalRead(SW_PIN) == LOW);
        delay(200);
        return;
      } else {
        selectedOption = (selectedOption + 1) % 3;
        printMenuOption(selectedOption);
        delay(200);
      }
    }
    delay(20);
  }
}

// Print the settings for the difficulty currently highlighted.
void printMenuOption(int opt) {
  if (opt == 0) Serial.println(" -> [ EASY MODE ]  : 3 Lives | 60s Keypad Timer");
  else if (opt == 1) Serial.println(" -> [ MEDIUM MODE ]: 2 Lives | 45s Keypad Timer | 2x Encoder, 2-Letter Morse, 2x Keypad");
  else if (opt == 2) Serial.println(" -> [ HARD MODE ]  : 1 Life  | 30s Keypad Timer | 3x Encoder, 3-Letter Morse, 2x Keypad");
}

// Load the lives, number of rounds, code length, and timer for the chosen difficulty.
void applyDifficultyConfiguration() {
  // Easy uses one round per puzzle and gives the longest timer.
  if (difficulty == 0) {
    maxLives = 3;
    totalEncoderInstances = 1;
    maxPosition = 100;
    targetPosition = 70;
    lettersPerInstance = 1;
    totalKeypadInstances = 1;
    keypadCodeLength = 4;
    KEYPAD_TIMELIMIT = 60000; 
  // Medium adds extra rounds and reduces the available lives and time.
  } else if (difficulty == 1) {
    maxLives = 2;
    totalEncoderInstances = 2;
    maxPosition = 150;
    targetPosition = random(50, 130);
    lettersPerInstance = 2;
    totalKeypadInstances = 2;
    keypadCodeLength = 4;
    KEYPAD_TIMELIMIT = 45000; 
  // Hard uses the longest codes, most encoder targets, and the shortest timer.
  } else if (difficulty == 2) {
    maxLives = 1;
    totalEncoderInstances = 3;
    maxPosition = 200;
    targetPosition = random(60, 180);
    lettersPerInstance = 3;
    totalKeypadInstances = 2;
    keypadCodeLength = 5;
    KEYPAD_TIMELIMIT = 30000; 
  }
  currentLives = maxLives;
}

// Rotary encoder
// This runs whenever either encoder signal changes.
void updateEncoder() {
  int MSB = digitalRead(CLK_PIN);
  int LSB = digitalRead(DT_PIN);
  // Combine the previous and current pin states to determine the direction of rotation.
  int encoded = (MSB << 1) | LSB;
  int sum = (lastEncoded << 2) | encoded;
  if (sum == 0b1101 || sum == 0b0100 || sum == 0b0010 || sum == 0b1011) encoderCount++;
  if (sum == 0b1110 || sum == 0b0111 || sum == 0b0001 || sum == 0b1000) encoderCount--;
  lastEncoded = encoded;
}

// Convert the raw encoder count into a game position and apply overshoot penalties.
void updatePositionFromEncoder() {
  // Copy the volatile count while interrupts are paused so the value cannot change halfway through.
  long countCopy;
  noInterrupts();
  countCopy = encoderCount;
  interrupts();
  int newPosition = countCopy / COUNTS_PER_STEP;
  // Keep the displayed position within the range allowed by the selected difficulty.
  if (newPosition < minPosition) {
    newPosition = minPosition;
    noInterrupts();
    encoderCount = minPosition * COUNTS_PER_STEP;
    interrupts();
  }
  if (newPosition > maxPosition) {
    newPosition = maxPosition;
    noInterrupts();
    encoderCount = maxPosition * COUNTS_PER_STEP;
    interrupts();
  }
  position = newPosition;
  if (position != lastPrintedPosition) {
    // On medium and hard, crossing the target without stopping counts as an overshoot.
    if (difficulty > 0 && lastPrintedPosition != -1) {
      if ((lastPrintedPosition < targetPosition && position > targetPosition) ||
          (lastPrintedPosition > targetPosition && position < targetPosition)) {

        static unsigned long lastOvershootLog = 0;
        if (millis() - lastOvershootLog > 400) {
          lastOvershootLog = millis();
          encoderOvershootCount++;

          Serial.println();
          Serial.print(" OVERSHOOT WARNING: Spun too quickly past hidden gate target! (");
          Serial.print(encoderOvershootCount);
          Serial.println("/3)");
          if (encoderOvershootCount >= 3) {
            currentLives--;
            Serial.println("\n LIFE LOST: Penalty for mindless twisting navigation actions!");
            Serial.print("Remaining Lives: "); Serial.println(currentLives);
            encoderOvershootCount = 0;
            if (currentLives <= 0) {
              Serial.println(" CRITICAL: Safety systems compromised. Resetting current instance target code!");
              if (difficulty == 1) targetPosition = random(50, 130);
              else if (difficulty == 2) targetPosition = random(60, 180);
              currentLives = maxLives;
            }

            position = 50;
            noInterrupts();
            encoderCount = 50 * COUNTS_PER_STEP;
            interrupts();
            lastPrintedPosition = -1;
            return;
          }
        }
      }
    }
    int distance = abs(position - targetPosition);
    Serial.print("Encoder Position: ");
    Serial.print(position);
    Serial.print(" | Distance from target: ");
    Serial.print(distance);
    if (distance == 0) Serial.println(" | PERFECT TARGET MATCH!");
    else if (distance <= tolerance) Serial.println(" | NEAR TARGET");
    else Serial.println();
    sendZigbee("ENCODER|POS|" + String(position) + "|TARGET|" + String(targetPosition) + "|DIST|" + String(distance));
    lastPrintedPosition = position;
  }
}

// Beep faster as the encoder gets closer to the hidden target.
void updateBuzzer() {
  int distance = abs(position - targetPosition);
  if (distance == 0) {
    playSuccessSound();
    return;
  }
  // Stay silent until the player enters the useful guidance range.
  if (distance > detectionRange) {
    noTone(BUZZER_PIN);
    return;
  }
  // A smaller distance gives a shorter gap between beep patterns.
  int beepGap = map(distance, detectionRange, 1, 800, 100);
  if (beepGap < 100) beepGap = 100;
  if (beepGap > 800) beepGap = 800;
  static unsigned long lastStepTime = 0;
  static int step = 0;
  unsigned long currentTime = millis();
  if (currentTime - lastStepTime >= beepGap) {
    lastStepTime = currentTime;
    if (step == 0) {
      tone(BUZZER_PIN, 880, 80);
      step = 1;
    } else if (step == 1) {
      tone(BUZZER_PIN, 1175, 80);
      step = 2;
    } else if (step == 2) {
      tone(BUZZER_PIN, 988, 80);
      step = 3;
    } else {
      noTone(BUZZER_PIN);
      step = 0;
    }
  }
}

// Play a short rising sound when the target is found.
void playSuccessSound() {
  tone(BUZZER_PIN, 1000, 100); delay(130);
  tone(BUZZER_PIN, 1300, 100); delay(130);
  tone(BUZZER_PIN, 1600, 100); delay(130);
  tone(BUZZER_PIN, 2200, 200); delay(250);
  noTone(BUZZER_PIN);
}

// Pressing the encoder switch returns the position to 50.
void handleResetButton() {
  if (digitalRead(SW_PIN) == LOW && millis() - lastButtonPress > 300) {
    position = 50;
    noInterrupts();
    encoderCount = 50 * COUNTS_PER_STEP;
    interrupts();
    noTone(BUZZER_PIN);
    Serial.println("Encoder manually reset to position 50");
    lastPrintedPosition = -1;
    lastButtonPress = millis();
  }
}

// Touch sensor and Morse input
// Measure the untouched and touched values before the game starts.
void calibrateSensor() {
  Serial.println();
  Serial.println("Sensor calibration starting...");
  Serial.println("Do NOT touch the sensor.");
  Serial.println("Measuring untouched value in 2 seconds...");
  delay(2000);
  int notTouchedValue = readAverageAnalog();
  Serial.print("Untouched average: ");
  Serial.println(notTouchedValue);
  Serial.println();
  Serial.println("Now TOUCH and HOLD the sensor.");
  Serial.println("Measuring touched value in 2 seconds...");
  delay(2000);
  int touchedValue = readAverageAnalog();
  Serial.print("Touched average: ");
  Serial.println(touchedValue);
  // Two thresholds are used: one to begin a touch and another to end it.
  int difference = abs(touchedValue - notTouchedValue);
  if (difference < 30) Serial.println("WARNING: Values are too close.");
  if (touchedValue > notTouchedValue) {
    touchIsHigher = true;
    touchStartThreshold = notTouchedValue + (difference * 6 / 10);
    touchEndThreshold = notTouchedValue + (difference * 4 / 10);
  } else {
    touchIsHigher = false;
    touchStartThreshold = notTouchedValue - (difference * 6 / 10);
    touchEndThreshold = notTouchedValue - (difference * 4 / 10);
  }
  Serial.println("\nCalibration complete.");
}

// Average several readings because the touch-sensor value can fluctuate.
int readAverageAnalog() {
  long total = 0;
  int samples = 100;
  for (int i = 0; i < samples; i++) {
    total += analogRead(analogPin);
    delay(10);
  }
  return total / samples;
}

// Use separate touch and release thresholds so the state does not flicker near the boundary.
bool getRawTouch(int analogVal) {
  if (touchIsHigher) {
    return isTouching ? (analogVal > touchEndThreshold) : (analogVal > touchStartThreshold);
  } else {
    return isTouching ? (analogVal < touchEndThreshold) : (analogVal < touchStartThreshold);
  }
}

// Start timing the touch after it has remained stable.
void touchStarted() {
  touchStartTime = millis();
  lastHoldPrintTime = millis();
  digitalWrite(ledPin, HIGH);
  Serial.println("Touch detected...");
}

// Classify the completed hold as a dot, dash, or invalid short press.
void touchEnded() {
  unsigned long touchDuration = millis() - touchStartTime;
  digitalWrite(ledPin, LOW);
  Serial.print("Touch duration: ");
  Serial.print(touchDuration / 1000.0);
  Serial.println(" seconds");
  // Five seconds or more is a dash; two seconds or more is a dot.
  if (touchDuration >= DASH_MIN_TIME) {
    currentMorse += "-";
    Serial.println("Dash added");
  } else if (touchDuration >= DOT_MIN_TIME) {
    currentMorse += ".";
    Serial.println("Dot added");
  } else {
    Serial.println("Ignored. Hold at least 2 seconds.");
  }
  Serial.print("Current stream: ");
  Serial.println(currentMorse);
  checkIfLetterCanBeChecked();
}

// Create a new random letter sequence for this Morse round.
void setupMorseInstanceProfile() {
  targetMorseString = "";
  for (int i = 0; i < lettersPerInstance; i++) {
    int idx = random(0, poolSize);
    targetMorseString += letterPool[idx];
  }
  currentMorse = "";
  Serial.println();
  Serial.println("==========================================");
  Serial.print("       STARTING MORSE TASK (RUN ");
  Serial.print(morseInstance + 1);
  Serial.print("/");
  Serial.print(totalMorseInstances);
  Serial.println(")");
  Serial.println("==========================================");
  showCurrentMorseTarget();
}

// Show the letters and the Morse pattern the player needs to enter.
void showCurrentMorseTarget() {
  Serial.println("------------------------------------------");
  Serial.print("Target Sequence String: ");
  Serial.println(targetMorseString);
  Serial.print("Expected Core Map Layout: ");
  for (int i = 0; i < targetMorseString.length(); i++) {
    Serial.print(getMorse(targetMorseString[i]));
    if (i < targetMorseString.length() - 1) Serial.print(" ");
  }
  Serial.println("\nEnter full pattern array now!");
  Serial.println("------------------------------------------");
}

// Compare the input once the player has entered enough dots and dashes.
void checkIfLetterCanBeChecked() {
  // Join the Morse code for all target letters into one expected stream.
  String fullExpectedMorse = "";
  for (int i = 0; i < targetMorseString.length(); i++) {
    fullExpectedMorse += getMorse(targetMorseString[i]);
  }
  if (currentMorse.length() == 0) return;
  if (currentMorse.length() < fullExpectedMorse.length()) return;
  // Extra symbols make the attempt invalid immediately.
  if (currentMorse.length() > fullExpectedMorse.length()) {
    Serial.println("\n ERROR: Input stream overflowed expected parameters!");
    currentLives--;
    currentMorse = "";
    if (currentLives <= 0) {
      Serial.println(" INSTANCE FAILURE: Resetting character map sets...");
      currentLives = maxLives;
      setupMorseInstanceProfile();
    } else {
      showCurrentMorseTarget();
    }
    return;
  }
  // A full match completes this round and may advance to the keypad.
  if (currentMorse == fullExpectedMorse) {
    Serial.println(" STRUCTURAL MATCH DECLARED!");
    morseInstance++;
    currentMorse = "";
    if (morseInstance >= totalMorseInstances) {
      morseEndTime = millis();
      Serial.println("\n>>> MINIGAME 2 COMPLETE! <<<");
      sendZigbee("RFID|WAIT|ENCODER");
      waitForRFIDAndAdvance(1);
      gameState = 2;
      sendZigbee("STATE|KEYPAD");
      keypadSetupDone = false;
      currentLives = maxLives;
    } else {
      delay(1000);
      setupMorseInstanceProfile();
    }
  } else {
    Serial.println(" ERROR: Verification mismatch.");
    currentLives--;
    currentMorse = "";
    if (currentLives <= 0) {
      Serial.println(" CRITICAL: Target lost. Regenerating target profile sets...");
      currentLives = maxLives;
      setupMorseInstanceProfile();
    } else {
      showCurrentMorseTarget();
    }
  }
}

// Morse-code lookup for the letters used by this game.
String getMorse(char letter) {
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

// Keypad codebreaker
// Generate a random code with no repeated symbols.
void generateNewKeypadCode() {
  char keypadPool[] = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D'};
  const int poolLength = 14;

  // Shuffle the full key pool, then use the first few symbols as the code.
  for (int i = poolLength - 1; i > 0; i--) {
    int j = random(0, i + 1);
    char temp = keypadPool[i];
    keypadPool[i] = keypadPool[j];
    keypadPool[j] = temp;
  }

  targetKeypadCode = "";
  for (int i = 0; i < keypadCodeLength; i++) {
    targetKeypadCode += keypadPool[i];
  }

  Serial.println();
  Serial.println("==========================================");
  Serial.print("     STARTING MINIGAME 3: CODEBREAKER (RUN ");
  Serial.print(keypadInstance + 1);
  Serial.print("/");
  Serial.print(totalKeypadInstances);
  Serial.println(")");
  Serial.println("==========================================");
  Serial.print("A secret ");
  Serial.print(keypadCodeLength);
  Serial.println("-digit security code has been generated.");

  if (isFirstKeypadAttempt) {
    Serial.println("Action Required: Press *** to activate countdown.");
  } else {
    Serial.println("Action Required: Press * to activate countdown.");
  }
  Serial.println("------------------------------------------");
}

// Return the keypad puzzle to its first-attempt state after all lives are lost.
void resetKeypadGame() {
  keypadGameStarted = false;
  keypadInputBuffer = "";
  isFirstKeypadAttempt = true; 
  generateNewKeypadCode();
}

// Scan the keypad matrix and return one clean key press.
char customGetKey() {
  char currentDetectedKey = '\0';
  // Pull one column low at a time and check which row is also low.
  for (int c = 0; c < 4; c++) {
    pinMode(colPins[c], OUTPUT);
    digitalWrite(colPins[c], LOW);
    delayMicroseconds(10);
    for (int r = 0; r < 4; r++) {
      if (digitalRead(rowPins[r]) == LOW) {
        currentDetectedKey = hexaKeys[r][c];
      }
    }
    digitalWrite(colPins[c], HIGH);
    pinMode(colPins[c], INPUT);
  }
  // Return the key only after it remains stable for 40 ms.
  static char trackingKey = '\0';
  static unsigned long lastDebounceTime = 0;
  static char lastStableKey = '\0';
  if (currentDetectedKey != trackingKey) {
    trackingKey = currentDetectedKey;
    lastDebounceTime = millis();
  }
  if ((millis() - lastDebounceTime) > 40) {
    if (currentDetectedKey != lastStableKey) {
      lastStableKey = currentDetectedKey;
      if (lastStableKey != '\0') return lastStableKey;
    }
  }
  return '\0';
}
