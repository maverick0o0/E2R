package e2r.core;

/**
 * Extracts code context around a match position.
 * Provides ±N lines of context for AI analysis.
 */
public class ContextExtractor {
    
    private static final int DEFAULT_CONTEXT_LINES = 100;
    
    /**
     * Extract context lines around a position in the content.
     * 
     * @param content Full source content
     * @param position Character position of the match
     * @param contextLines Number of lines before and after to include
     * @return Context string with the match highlighted
     */
    public static ContextResult extractContext(String content, int position, int contextLines) {
        if (content == null || content.isEmpty() || position < 0) {
            return new ContextResult("", 0, 0, 0);
        }
        
        String[] lines = content.split("\n", -1);
        
        // Find the line number containing the position
        int charCount = 0;
        int matchLine = 0;
        for (int i = 0; i < lines.length; i++) {
            int lineLength = lines[i].length() + 1; // +1 for newline
            if (charCount + lineLength > position) {
                matchLine = i;
                break;
            }
            charCount += lineLength;
        }
        
        // Calculate start and end lines
        int startLine = Math.max(0, matchLine - contextLines);
        int endLine = Math.min(lines.length - 1, matchLine + contextLines);
        
        // Build context string
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            contextBuilder.append(lines[i]);
            if (i < endLine) {
                contextBuilder.append("\n");
            }
        }
        
        return new ContextResult(
            contextBuilder.toString(),
            matchLine + 1,  // 1-indexed for display
            startLine + 1,
            endLine + 1
        );
    }
    
    /**
     * Extract context with default number of lines (100).
     */
    public static ContextResult extractContext(String content, int position) {
        return extractContext(content, position, DEFAULT_CONTEXT_LINES);
    }
    
    /**
     * Get the line number for a character position.
     */
    public static int getLineNumber(String content, int position) {
        if (content == null || position < 0 || position >= content.length()) {
            return 1;
        }
        
        int lineNumber = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }
    
    /**
     * Result container for context extraction.
     */
    public static class ContextResult {
        public final String context;
        public final int matchLine;
        public final int startLine;
        public final int endLine;
        
        public ContextResult(String context, int matchLine, int startLine, int endLine) {
            this.context = context;
            this.matchLine = matchLine;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }
}
