import java.io.File;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Sound playback manager manipulating low-latency audio assets utilizing standard Java Audio clips.
 * Provides loops, standard stop procedures, and relative decibel level adjustments
 * to manage heist atmosphere dynamics during minigame sequences.
 */
public class SoundPlayer {

    /** Native audio hardware controller channel holding the running audio data. */
    private Clip clip;

    /**
     * Initiates loop playback at a safe default secondary gain volume of -12.0 decibels.
     * * @param filePath System file path pointing to the WAV resource
     * @return TRUE if audio resource loaded and started successfully; FALSE otherwise
     */
    public boolean playLoop(String filePath) {
        return playLoop(filePath, -12.0f);
    }

    /**
     * Shuts down active playbacks, opens the target system file, establishes standard gain,
     * and sets continuous audio loop configurations.
     * * @param filePath System file path pointing to the WAV resource
     * @param volumeDb Desired gain scaling in decibels (lower values reduce background volume)
     * @return TRUE if audio playback runs; FALSE if files are missing or streams throw errors
     */
    public boolean playLoop(String filePath, float volumeDb) {

        try {
            // Clean up any audio clips currently bound to the player channel
            stop();

            File audioFile = new File(filePath);

            if (!audioFile.exists()) {
                System.out.println("Audio file not found: " + audioFile.getAbsolutePath());
                return false;
            }

            // Establish buffered input streams linked directly with standard sound libraries
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);

            clip = AudioSystem.getClip();
            clip.open(audioStream);

            // Configure Master Gain boundaries
            setVolume(volumeDb);

            // Configure continuous loop parameters and execute playback
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();

            return true;

        } catch (Exception e) {
            System.out.println("Could not play audio: " + e.getMessage());
            return false;
        }
    }

    /**
     * Terminate active audio routines, closing background streams and releasing system audio memory.
     */
    public void stop() {

        try {
            if (clip != null) {
                clip.stop();
                clip.close();
                clip = null;
            }
        } catch (Exception e) {
            System.out.println("Could not stop audio: " + e.getMessage());
        }
    }

    /**
     * Resets visual and audio streams, restarting the target loop track from the beginning.
     * * @param filePath Target audio resource filepath
     */
    public void restart(String filePath) {
        playLoop(filePath);
    }

    /**
     * Simple indicator status querying whether audio is actively playing.
     * * @return TRUE if an audio track is open and currently executing; FALSE otherwise
     */
    public boolean isPlaying() {
        return clip != null && clip.isRunning();
    }

    /**
     * Adjusts the current playback decibel output, adhering to system gain limits.
     * * @param volumeDb Decibel offset value to scale hardware output
     */
    private void setVolume(float volumeDb) {

        if (clip == null) {
            return;
        }

        try {
            // Verify if master volume hardware control layers are accessible
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {

                FloatControl gainControl =
                        (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

                float min = gainControl.getMinimum();
                float max = gainControl.getMaximum();

                // Safe range verification (clamps values exceeding hardware bounds)
                if (volumeDb < min) {
                    volumeDb = min;
                }

                if (volumeDb > max) {
                    volumeDb = max;
                }

                gainControl.setValue(volumeDb);
            }
        } catch (Exception e) {
            System.out.println("Could not set volume: " + e.getMessage());
        }
    }
}