package e2r.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/**
 * Code viewer component with line numbers and syntax highlighting.
 * Displays JavaScript source with the match line highlighted.
 */
public class ContextViewer extends JPanel {
    
    private final JTextPane codePane;
    private final JTextArea lineNumbers;
    private final JLabel sourceLabel;
    
    // Colors - dark theme inspired by the mockup
    private static final Color BG_COLOR = new Color(30, 30, 30);
    private static final Color LINE_NUMBER_BG = new Color(40, 40, 40);
    private static final Color LINE_NUMBER_FG = new Color(120, 120, 120);
    private static final Color CODE_FG = new Color(220, 220, 220);
    private static final Color HIGHLIGHT_BG = new Color(180, 100, 50);
    private static final Color HIGHLIGHT_FG = Color.WHITE;
    
    private int currentHighlightLine = -1;
    
    public ContextViewer() {
        setLayout(new BorderLayout());
        setBackground(BG_COLOR);
        
        // Source URL label
        sourceLabel = new JLabel("CONTEXT VIEWER:");
        sourceLabel.setForeground(new Color(200, 200, 200));
        sourceLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sourceLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        sourceLabel.setOpaque(true);
        sourceLabel.setBackground(new Color(45, 45, 45));
        add(sourceLabel, BorderLayout.NORTH);
        
        // Line numbers
        lineNumbers = new JTextArea();
        lineNumbers.setBackground(LINE_NUMBER_BG);
        lineNumbers.setForeground(LINE_NUMBER_FG);
        lineNumbers.setFont(new Font("Consolas", Font.PLAIN, 13));
        lineNumbers.setEditable(false);
        lineNumbers.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10));
        
        // Code pane
        codePane = new JTextPane();
        codePane.setBackground(BG_COLOR);
        codePane.setForeground(CODE_FG);
        codePane.setFont(new Font("Consolas", Font.PLAIN, 13));
        codePane.setEditable(false);
        codePane.setCaretColor(CODE_FG);
        codePane.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // Create scroll pane with line numbers
        JScrollPane scrollPane = new JScrollPane(codePane);
        scrollPane.setRowHeaderView(lineNumbers);
        scrollPane.setBackground(BG_COLOR);
        scrollPane.getViewport().setBackground(BG_COLOR);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        add(scrollPane, BorderLayout.CENTER);
    }
    
    /**
     * Display code with highlighted match line.
     * 
     * @param sourceUrl URL of the source file
     * @param code The code to display
     * @param highlightLine Line number to highlight (1-indexed)
     * @param startLine First line number in the context
     */
    public void displayCode(String sourceUrl, String code, int highlightLine, int startLine) {
        sourceLabel.setText("CONTEXT VIEWER: " + (sourceUrl != null ? sourceUrl : ""));
        currentHighlightLine = highlightLine;
        
        if (code == null || code.isEmpty()) {
            codePane.setText("");
            lineNumbers.setText("");
            return;
        }
        
        String[] lines = code.split("\n", -1);
        
        // Build line numbers
        StringBuilder lineNumBuilder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            int lineNum = startLine + i;
            lineNumBuilder.append(String.format("%4d", lineNum));
            if (i < lines.length - 1) {
                lineNumBuilder.append("\n");
            }
        }
        lineNumbers.setText(lineNumBuilder.toString());
        
        // Set code with highlighting
        StyledDocument doc = codePane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            
            Style defaultStyle = codePane.addStyle("default", null);
            StyleConstants.setForeground(defaultStyle, CODE_FG);
            StyleConstants.setBackground(defaultStyle, BG_COLOR);
            
            Style highlightStyle = codePane.addStyle("highlight", null);
            StyleConstants.setForeground(highlightStyle, HIGHLIGHT_FG);
            StyleConstants.setBackground(highlightStyle, HIGHLIGHT_BG);
            StyleConstants.setBold(highlightStyle, true);
            
            int highlightIndex = highlightLine - startLine;
            
            for (int i = 0; i < lines.length; i++) {
                Style style = (i == highlightIndex) ? highlightStyle : defaultStyle;
                doc.insertString(doc.getLength(), lines[i], style);
                if (i < lines.length - 1) {
                    doc.insertString(doc.getLength(), "\n", defaultStyle);
                }
            }
            
            // Scroll to highlighted line
            if (highlightIndex >= 0 && highlightIndex < lines.length) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        int pos = 0;
                        for (int i = 0; i < highlightIndex; i++) {
                            pos += lines[i].length() + 1;
                        }
                        codePane.setCaretPosition(Math.min(pos, doc.getLength()));
                        Rectangle rect = codePane.modelToView(codePane.getCaretPosition());
                        if (rect != null) {
                            // Center the highlighted line
                            Rectangle visible = codePane.getVisibleRect();
                            rect.y -= visible.height / 2;
                            codePane.scrollRectToVisible(rect);
                        }
                    } catch (Exception e) {
                        // Ignore scroll errors
                    }
                });
            }
            
        } catch (BadLocationException e) {
            codePane.setText(code);
        }
    }
    
    /**
     * Clear the viewer.
     */
    public void clear() {
        sourceLabel.setText("CONTEXT VIEWER:");
        codePane.setText("");
        lineNumbers.setText("");
        currentHighlightLine = -1;
    }
}
