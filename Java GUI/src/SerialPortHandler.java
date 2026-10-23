import jssc.SerialPort;
import jssc.SerialPortException;

/**
 * Handles the raw serial connection to the Arduino board.
 * It opens the port, configures the standard baud rate, and reads/writes text lines.
 */
public class SerialPortHandler {

    private SerialPort sp;
    private String path;

    // Opens the serial port and clears out any leftover data in the buffer
    public SerialPortHandler(String path) throws SerialPortException {

        this.path = path;
        this.sp = new SerialPort(path);

        sp.openPort();

        // Standard 9600 baud rate, 8 data bits, 1 stop bit, no parity
        sp.setParams(
                SerialPort.BAUDRATE_9600,
                SerialPort.DATABITS_8,
                SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE
        );

        // Flush any remaining characters waiting in the system buffer
        while (sp.getInputBufferBytesCount() > 0) {
            sp.readBytes();
        }
    }

    // Reads incoming serial data one byte at a time until it encounters a newline character
    public String readLine() throws SerialPortException {

        StringBuilder line = new StringBuilder();

        while (!Thread.currentThread().isInterrupted()) {

            byte[] buffer = sp.readBytes(1);

            if (buffer == null || buffer.length == 0) {
                continue;
            }

            char c = (char) buffer[0];

            // If we hit a newline, trim the accumulated string and return it
            if (c == '\n') {

                String result = line.toString().trim();

                if (!result.isEmpty()) {
                    return result;
                }

                line.setLength(0);
                continue;
            }

            // Skip carriage return characters
            if (c == '\r') {
                continue;
            }

            line.append(c);
        }

        return null;
    }

    // Sends a string of text followed by a newline over to the Arduino
    public void printLine(String text) throws SerialPortException {

        if (sp != null && sp.isOpened()) {
            sp.writeString(text + "\n");
        }
    }

    // Safely closes the serial port connection
    public void close() {

        try {
            if (sp != null && sp.isOpened()) {
                sp.closePort();
            }
        } catch (SerialPortException e) {
            // Ignore any issues during cleanup
        }
    }
}