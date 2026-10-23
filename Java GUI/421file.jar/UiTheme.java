import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Utility class governing the global look-and-feel of the "Cash Me If You Can" application.
 * Establishes a cohesive cyber-theft dark theme with neon accent indicators for states.
 * Standardizes fonts, colors, and layout borders across separate minigame panels.
 */
public final class UiTheme {

    // --- Color Palette ---
    
    /** Deep black background color used for the primary panel containers. */
    public static final Color BG = new Color(0, 0, 0);
    
    /** Core card container color representing unselected or resting interface states. */
    public static final Color CARD = new Color(8, 22, 14);
    
    /** Secondary card color used for subtle layering effects and boundaries. */
    public static final Color CARD_2 = new Color(16, 45, 28);

    /** High-contrast mint-white foreground color for text values. */
    public static final Color TEXT = new Color(230, 255, 236);
    
    /** Low-contrast muted green-gray color for helper captions or non-interactive labels. */
    public static final Color MUTED = new Color(132, 190, 151);

    /** Neon green theme color highlighting interactive focus zones or selection targets. */
    public static final Color ACCENT = new Color(0, 255, 102);
    
    /** Positive green state color identifying a matching code, tag read, or correct encoder point. */
    public static final Color SUCCESS = new Color(57, 255, 123);
    
    /** Amber alert color signaling proximity to target zones or running timers. */
    public static final Color WARNING = new Color(190, 255, 90);
    
    /** Bright warning red representing a tag failure, wrong key entry, or run hazard. */
    public static final Color DANGER = new Color(255, 92, 92);

    /**
     * Private constructor to enforce utility pattern and prevent direct instantiation.
     */
    private UiTheme() {
        // Utility class.
    }

    // --- Text Component Generators ---

    /**
     * Creates and styles a standard bold primary title label.
     * * @param text The text to display in the header
     * @return Styled JLabel configured with large bold SansSerif font and high-contrast text color
     */
    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(new Font("SansSerif", Font.BOLD, 30));
        return label;
    }

    /**
     * Creates and styles a small secondary subtitle label for warnings or gameplay suggestions.
     * * @param text The descriptive text block
     * @return Styled JLabel configured with standard SansSerif font and muted color
     */
    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.PLAIN, 15));
        return label;
    }

    /**
     * Generates a stylized dynamic value label (such as timers, distances, or coordinates).
     * * @param text The initial value output
     * @param size The desired font sizing factor (e.g., larger sizes for critical indicators)
     * @return Styled JLabel configured with standard high-contrast text styling
     */
    public static JLabel value(String text, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(new Font("SansSerif", Font.BOLD, size));
        return label;
    }

    // --- Panel Structural Styling ---

    /**
     * Generates a standardized container card panel utilizing the theme's core color values.
     * Features dual-border styling (thin boundary frame plus inner margin padding).
     * * @return A styled JPanel ready to act as a sub-grid container card
     */
    public static JPanel card() {
        JPanel panel = new JPanel();
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_2, 1),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }
}