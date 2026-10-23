# Cash Me If You Can

**COE 421L**

Cash Me If You Can is a physical game where the vault is opened by breaking into it via Arduino Mega 2560, wireless technology (Zigbee), and Java Swing interface. There are three hardware mini-games to be played with the successful scan of the RFID tag in each stage.

## Game Flow

1. Register three RFID tags.
2. Calibrate the metal touch sensor.
3. Select Easy, Medium, or Hard mode using the encoder button.
4. Complete the rotary encoder puzzle.
5. Scan the RFID tag that is randomly chosen.
6. Enter the given Morse sequence using the touch sensor.
7. Scan another RFID tag.
8. Solve the keypad codebreaker puzzle.
9. Scan the final RFID tag and see the completion time.

## Minigames

### 1. Rotary Encoder Safe Dial
The encoder starts from position 50. The game is played by searching for a hidden target while the buzzer signals proximity. Rapid beeping means the player is closer to the target. Medium and Hard difficulty levels feature more than one target with overshooting consequences.

### 2. Touch Sensor Morse Puzzle
The player types a random letter sequence in Morse code through the metal touch sensor. Touch that lasts for 2 or more seconds is considered a dot, while touch for 5 or more seconds is considered a dash. The touch sensor is calibrated upon start up for any variations in the analog reading.

### 3. Keypad Codebreaker
The Arduino creates a code consisting of letters and numbers from `0-9` and `A-D`. Player makes guess inputs from the 4x4 keypad and gets feedback of Bullseye, Cow, and Wrong. Time limits and code lengths vary depending on difficulty levels chosen.

## Hardware

- Arduino Mega 2560
- Rotary encoder with push button
- Metal touch sensor
- 4x4 keypad
- Parallax serial RFID reader
- Three RFID tags
- Buzzer
- Indicator LED
- Two ZigBee/XBee modules

## System Architecture

The Arduino takes care of reading sensors, game logic, timers, lives, RFID authentication, and peripheral devices. ZigBee offers the wireless connection from the Arduino to the PC.

The Java program uses the JSSC serial port API to read data that comes in at 9600 bits per second. Bytes are read until a newline is found. After that, the data is processed by the GUI and the current state of the game is saved.

## Running the Project

1. Connect the peripherals and ZigBee module to the Arduino Mega.
2. Upload the Arduino code using the Arduino IDE.
3. Connect the receiving ZigBee module to the computer.
4. Open the Java GUI project in Eclipse or another Java IDE.
5. Add `jssc.jar` to the Java build path.
6. Run `HeistRushGUI.java`.
7. Select the ZigBee COM port and connect.
8. Follow the setup instructions shown by the GUI.

## Team Members

- Arjunraj Sasikumar
- Zainulabdeen Lalmiya
- Adrish Danka
- Asadullah Sameer

## Demonstration

YouTube video: **https://youtu.be/hvmoxO88UAw?si=UDfqvlRcmWNDCo4I**

## Use of Generative AI

ChatGPT and Claude were used to assist with generating selected code snippets, debugging, code commenting, GUI development, and the initial class diagram layout.
