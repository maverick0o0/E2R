package e2r.core;

/**
 * JavaScript Beautifier for minified code.
 * Adds proper line breaks and indentation to make context extraction more useful.
 */
public class JsBeautifier {
    
    private static final int INDENT_SIZE = 2;
    
    /**
     * Check if content appears to be minified (very long lines, few newlines).
     */
    public static boolean isMinified(String content) {
        if (content == null || content.length() < 1000) {
            return false;
        }
        
        // Count newlines
        int newlines = 0;
        for (char c : content.toCharArray()) {
            if (c == '\n') newlines++;
        }
        
        // If average line length > 500 chars, likely minified
        int avgLineLength = content.length() / Math.max(1, newlines + 1);
        return avgLineLength > 500;
    }
    
    /**
     * Beautify JavaScript code by adding proper formatting.
     * This is a simplified beautifier focused on making endpoint context more readable.
     */
    public static String beautify(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // Only beautify if content appears minified
        if (!isMinified(content)) {
            return content;
        }
        
        StringBuilder result = new StringBuilder();
        int indentLevel = 0;
        boolean inString = false;
        boolean inTemplate = false;
        char stringChar = 0;
        boolean escaped = false;
        char prevChar = 0;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            char nextChar = (i + 1 < content.length()) ? content.charAt(i + 1) : 0;
            
            // Handle escape sequences
            if (escaped) {
                result.append(c);
                escaped = false;
                prevChar = c;
                continue;
            }
            
            if (c == '\\') {
                result.append(c);
                escaped = true;
                prevChar = c;
                continue;
            }
            
            // Handle strings
            if (!inString && !inTemplate && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
                result.append(c);
                prevChar = c;
                continue;
            }
            
            if (inString && c == stringChar) {
                inString = false;
                result.append(c);
                prevChar = c;
                continue;
            }
            
            // Handle template literals
            if (!inString && !inTemplate && c == '`') {
                inTemplate = true;
                result.append(c);
                prevChar = c;
                continue;
            }
            
            if (inTemplate && c == '`') {
                inTemplate = false;
                result.append(c);
                prevChar = c;
                continue;
            }
            
            // If inside string or template, just append
            if (inString || inTemplate) {
                result.append(c);
                prevChar = c;
                continue;
            }
            
            // Handle formatting outside strings
            switch (c) {
                case '{':
                    result.append(" {\n");
                    indentLevel++;
                    appendIndent(result, indentLevel);
                    break;
                    
                case '}':
                    result.append("\n");
                    indentLevel = Math.max(0, indentLevel - 1);
                    appendIndent(result, indentLevel);
                    result.append("}");
                    if (nextChar != ',' && nextChar != ';' && nextChar != ')' && nextChar != ']') {
                        result.append("\n");
                        appendIndent(result, indentLevel);
                    }
                    break;
                    
                case '[':
                    result.append("[");
                    // Only newline for arrays that look like they have objects
                    if (nextChar == '{') {
                        result.append("\n");
                        indentLevel++;
                        appendIndent(result, indentLevel);
                    }
                    break;
                    
                case ']':
                    if (prevChar == '}') {
                        result.append("\n");
                        indentLevel = Math.max(0, indentLevel - 1);
                        appendIndent(result, indentLevel);
                    }
                    result.append("]");
                    break;
                    
                case ';':
                    result.append(";\n");
                    appendIndent(result, indentLevel);
                    break;
                    
                case ',':
                    result.append(",");
                    // Newline after comma in object/array context
                    if (indentLevel > 0) {
                        result.append("\n");
                        appendIndent(result, indentLevel);
                    } else {
                        result.append(" ");
                    }
                    break;
                    
                case ':':
                    result.append(": ");
                    break;
                    
                default:
                    // Skip excessive whitespace
                    if (Character.isWhitespace(c)) {
                        if (!Character.isWhitespace(prevChar) && result.length() > 0) {
                            result.append(' ');
                        }
                    } else {
                        result.append(c);
                    }
                    break;
            }
            
            prevChar = c;
        }
        
        return result.toString();
    }
    
    private static void appendIndent(StringBuilder sb, int level) {
        for (int i = 0; i < level * INDENT_SIZE; i++) {
            sb.append(' ');
        }
    }
}
