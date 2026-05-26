package e2r.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parameter detection patterns and extraction logic for JavaScript code and URLs.
 */
public class ParameterPatterns {
    
    public static class ParameterMatch {
        public final String name;
        public final String type; // "Query", "Body", "JS Variable", "Manual"
        public final int startPosition;
        public final int endPosition;
        
        public ParameterMatch(String name, String type, int startPosition, int endPosition) {
            this.name = name;
            this.type = type;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }
    
    private static class PatternEntry {
        final Pattern pattern;
        final String type;
        final int captureGroup;
        
        PatternEntry(String regex, String type, int captureGroup) {
            this.pattern = Pattern.compile(regex);
            this.type = type;
            this.captureGroup = captureGroup;
        }
    }
    
    private static final List<PatternEntry> PATTERNS = new ArrayList<>();
    
    static {
        // 1. URLSearchParams: e.g. searchParams.append('name', value) or searchParams.set('name', value)
        PATTERNS.add(new PatternEntry(
            "(?:\\.append|\\.set|\\.get|\\.has)\\s*\\(\\s*['\"`]([a-zA-Z0-9_\\-\\[\\]]+)['\"`]",
            "Query",
            1
        ));
        
        // 2. Axios/Fetch params config: e.g. params: { name: value }
        PATTERNS.add(new PatternEntry(
            "params\\s*:\\s*\\{\\s*([a-zA-Z0-9_\\-]+)\\s*:",
            "Query",
            1
        ));
        
        // 3. Axios/Fetch data config: e.g. data: { name: value }
        PATTERNS.add(new PatternEntry(
            "data\\s*:\\s*\\{\\s*([a-zA-Z0-9_\\-]+)\\s*:",
            "Body",
            1
        ));
        
        // 4. Axios/Fetch body config: e.g. body: JSON.stringify({ name: value })
        PATTERNS.add(new PatternEntry(
            "body\\s*:\\s*(?:JSON\\.stringify\\(\\s*)?\\{\\s*([a-zA-Z0-9_\\-]+)\\s*:",
            "Body",
            1
        ));
        
        // 5. Express/Node req.query.name / req.body.name / req.params.name
        PATTERNS.add(new PatternEntry(
            "req\\.query\\.([a-zA-Z0-9_\\-]+)",
            "Query",
            1
        ));
        PATTERNS.add(new PatternEntry(
            "req\\.body\\.([a-zA-Z0-9_\\-]+)",
            "Body",
            1
        ));
        PATTERNS.add(new PatternEntry(
            "req\\.params\\.([a-zA-Z0-9_\\-]+)",
            "Query",
            1
        ));
    }
    
    /**
     * Find parameters by scanning code using regex.
     */
    public static List<ParameterMatch> findParameters(String content) {
        List<ParameterMatch> matches = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return matches;
        }
        
        for (PatternEntry entry : PATTERNS) {
            Matcher matcher = entry.pattern.matcher(content);
            while (matcher.find()) {
                String name = matcher.group(entry.captureGroup);
                if (isValidParameterName(name)) {
                    matches.add(new ParameterMatch(
                        name,
                        entry.type,
                        matcher.start(entry.captureGroup),
                        matcher.end(entry.captureGroup)
                    ));
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Extract parameters from a query string of a URL or Endpoint.
     */
    public static List<ParameterMatch> extractFromUrl(String url, int startPos) {
        List<ParameterMatch> matches = new ArrayList<>();
        if (url == null || !url.contains("?")) {
            return matches;
        }
        
        int queryStart = url.indexOf('?');
        String queryString = url.substring(queryStart + 1);
        int queryEnd = queryString.indexOf('#');
        if (queryEnd >= 0) {
            queryString = queryString.substring(0, queryEnd);
        }
        
        String[] pairs = queryString.split("&");
        int offset = startPos + queryStart + 1;
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            String[] keyValue = pair.split("=");
            String key = keyValue[0];
            if (isValidParameterName(key)) {
                int keyStart = offset;
                int keyEnd = offset + key.length();
                matches.add(new ParameterMatch(key, "Query", keyStart, keyEnd));
            }
            offset += pair.length() + 1;
        }
        return matches;
    }
    
    /**
     * Validate parameter name to avoid false positives.
     */
    public static boolean isValidParameterName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.length() > 50) {
            return false;
        }
        // Blacklist common javascript words to reduce noise
        String lower = name.toLowerCase();
        if (lower.equals("function") || lower.equals("const") || lower.equals("let") ||
            lower.equals("var") || lower.equals("return") || lower.equals("true") ||
            lower.equals("false") || lower.equals("null") || lower.equals("undefined") ||
            lower.equals("typeof") || lower.equals("instanceof")) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9_\\[\\]\\-]+$");
    }
}
