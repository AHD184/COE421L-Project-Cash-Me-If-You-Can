# Cash Me If You Can

**COE 421L | Computer Systems Engineering Laboratory | American University of Sharjah**

Cash Me If You Can is a physical, vault-themed puzzle game built around an **Arduino Mega 2560** and a **Java Swing desktop interface**. Players work through three hardware challenges, using a rotary encoder, a metal touch sensor, and a 4 × 4 keypad. Correct RFID scans unlock the next stage. Two ZigBee/XBee modules carry game-state messages wirelessly between the Arduino and the computer.

**[Watch the game demonstration on YouTube](https://youtu.be/hvmoxO88UAw)**

## How the Game Works

1. Connect the Arduino and Java application through the ZigBee serial link.
2. Register three different RFID tags and calibrate the metal touch sensor.
3. Select **Easy**, **Medium**, or **Hard** using the rotary encoder push button.
4. Find the hidden position in the rotary encoder safe-dial puzzle.
5. Scan the requested RFID tag to proceed.
6. Enter the displayed Morse code using timed touches on the metal sensor.
7. Scan the next requested RFID tag.
8. Guess the keypad code using Bullseye/Cow/Wrong feedback before time runs out.
9. Scan the final RFID tag to open the vault and display the completion time.

The difficulty setting affects the number of lives, puzzle rounds, input lengths, and time limits. A failed challenge can restart the current mini-game without restarting the entire game.

## Mini-Games

### 1. Rotary Encoder Safe Dial

The dial starts at position 50. The player rotates the encoder to locate a hidden target and uses the GUI distance indicator and buzzer sounds as feedback. The buzzer beeps faster near the target. Medium and Hard introduce more targets and penalties for repeatedly overshooting.

### 2. Morse Code Touch Puzzle

The player reproduces a randomly generated letter sequence in Morse code using a metal touch sensor:

- Hold for at least **2 seconds** to enter a dot (`.`).
- Hold for at least **5 seconds** to enter a dash (`-`).

Before the game starts, the Arduino measures the sensor's untouched and touched readings to set detection thresholds. The sequence length increases with difficulty.

### 3. Keypad Codebreaker

The Arduino generates a code using characters `0-9` and `A-D`. The player enters guesses on the 4 × 4 keypad and submits each guess with `#`. The Java GUI reports:

- **Bullseye:** correct character in the correct position.
- **Cow:** correct character in the wrong position.
- **Wrong:** character not in the code.

| Difficulty | Keypad rounds | Code length | Time per round |
|---|---:|---:|---:|
| Easy | 1 | 4 characters | 60 seconds |
| Medium | 2 | 4 characters | 45 seconds |
| Hard | 2 | 5 characters | 30 seconds |

## Hardware

- Arduino Mega 2560
- Rotary encoder with push button
- Metal touch sensor
- 4 × 4 matrix keypad
- Parallax serial RFID reader and three RFID tags
- Buzzer and indicator LED
- Two ZigBee/XBee modules
- Computer running the Java application

## System Architecture

The **Arduino Mega 2560** handles sensor readings, puzzle logic, RFID checks, timers, difficulty settings, and lives. It sends game-state and event messages through `Serial2` to a ZigBee module at **9600 bps**.

The receiving ZigBee module connects to the computer. The Java Swing application uses the **JSSC serial-port library** to read newline-terminated messages, interpret the data, and update the graphical interface. The GUI displays instructions, active puzzles, RFID checkpoints, timers, remaining lives, and the final completion time.

```text
Hardware inputs → Arduino Mega 2560 → ZigBee/XBee → Java Swing GUI
       ↑                  ↓                              ↓
  Player actions    Game-state logic             Live visual feedback
```

## Running the Project

1. Connect the input devices, output devices, and ZigBee transmitter to the Arduino Mega.
2. Upload the Arduino sketch using the Arduino IDE.
3. Connect the receiving ZigBee module to the computer.
4. Open the Java project in Eclipse or another Java IDE.
5. Add `jssc.jar` to the Java build path.
6. Run `HeistRushGUI.java`.
7. Select the receiving ZigBee module's COM port and connect.
8. Follow the on-screen setup steps to register RFID tags, calibrate the touch sensor, and select difficulty.

**Note:** This is a physical-hardware project. The Java GUI needs an active serial connection and the connected Arduino peripherals to run the complete game.

## Team

- Arjunraj Sasikumar
- Zainulabdeen Lalmiya
- Adrish Danka
- Asadullah Sameer

The team divided work across Arduino game logic, hardware integration, the Java GUI, testing, and documentation.

## Demonstration

[Watch Cash Me If You Can on YouTube](https://youtu.be/hvmoxO88UAw)

## Use of Generative AI

ChatGPT and Claude were used to assist with selected code snippets, debugging, code comments, GUI development, and the initial class-diagram layout. The team integrated and tested the project with the physical hardware.
