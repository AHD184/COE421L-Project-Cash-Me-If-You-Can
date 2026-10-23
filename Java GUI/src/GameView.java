import java.awt.BorderLayout;
import javax.swing.JPanel;

/**
 * Base panel that all game screens inherit from.
 * It sets up a standard BorderLayout and makes sure the background is visible.
 */
public abstract class GameView extends JPanel {

    // Creates the view with standard layout settings
    public GameView() {
        setLayout(new BorderLayout());
        setOpaque(true);
    }

    // Every game screen must implement this to update its UI when the game state changes
    public abstract void updateView(GameSnapshot snapshot);

    // Run when the game first starts (subclasses can override if needed)
    public void onGameStart() {
        // Optional
    }

    // Run when the game finishes (subclasses can override if needed)
    public void onGameEnd() {
        // Optional
    }

    // Helper to get the class name of the panel
    public String getGameName() {
        return getClass().getSimpleName();
    }
}