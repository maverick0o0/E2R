package e2r.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex engine to identify dangerous DOM XSS sources and sinks in JavaScript source code.
 */
public class SourceSinkPatterns {
    
    public static class PatternMatch {
        public final String value;
        public final String category;
        public final int startPosition;
        public final int endPosition;
        
        public PatternMatch(String value, String category, int startPosition, int endPosition) {
            this.value = value;
            this.category = category;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }
    
    private static class PatternEntry {
        final Pattern pattern;
        final String category;
        
        PatternEntry(String regex, String category) {
            this.pattern = Pattern.compile(regex);
            this.category = category;
        }
    }
    
    private static final List<PatternEntry> SOURCES = new ArrayList<>();
    private static final List<PatternEntry> SINKS = new ArrayList<>();
    
    static {
        // Predefined high-precision DOM XSS Sources
        SOURCES.add(new PatternEntry("document\\.(URL|documentURI|URLUnencoded|baseURI)", "URL/URI"));
        SOURCES.add(new PatternEntry("location\\.(href|search|hash|pathname)", "Location"));
        SOURCES.add(new PatternEntry("document\\.cookie", "Cookie"));
        SOURCES.add(new PatternEntry("document\\.referrer", "Referrer"));
        SOURCES.add(new PatternEntry("window\\.name", "Window Name"));
        SOURCES.add(new PatternEntry("event\\.data", "Message Data"));
        
        // Predefined high-precision DOM XSS Sinks
        SINKS.add(new PatternEntry("eval\\s*\\([^)]*\\)", "eval()"));
        SINKS.add(new PatternEntry("new\\s+Function\\s*\\([^)]*\\)", "Function()"));
        SINKS.add(new PatternEntry("setTimeout\\s*\\(\\s*['\"`][^)]*\\)", "setTimeout()"));
        SINKS.add(new PatternEntry("setInterval\\s*\\(\\s*['\"`][^)]*\\)", "setInterval()"));
        SINKS.add(new PatternEntry("document\\.write(ln)?\\s*\\([^)]*\\)", "document.write()"));
        SINKS.add(new PatternEntry("\\.(innerHTML|outerHTML)\\s*=\\s*", "HTML Assignment"));
        SINKS.add(new PatternEntry("location\\s*=\\s*|location\\.href\\s*=\\s*", "location Redirect"));
        SINKS.add(new PatternEntry("location\\.(replace|assign)\\s*\\([^)]*\\)", "location Method"));
        SINKS.add(new PatternEntry("\\.setAttribute\\s*\\(\\s*['\"`](src|href|action|on[a-z]+)['\"`]\\s*,", "setAttribute()"));
        SINKS.add(new PatternEntry("\\.(src|href|action)\\s*=\\s*", "DOM Property (src/href)"));
    }
    
    /**
     * Find DOM XSS sources.
     */
    public static List<PatternMatch> findSources(String content) {
        return findMatches(content, SOURCES);
    }
    
    /**
     * Find DOM XSS sinks.
     */
    public static List<PatternMatch> findSinks(String content) {
        return findMatches(content, SINKS);
    }
    
    private static List<PatternMatch> findMatches(String content, List<PatternEntry> patterns) {
        List<PatternMatch> matches = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return matches;
        }
        
        for (PatternEntry entry : patterns) {
            Matcher matcher = entry.pattern.matcher(content);
            while (matcher.find()) {
                String value = matcher.group(0);
                matches.add(new PatternMatch(
                    value,
                    entry.category,
                    matcher.start(),
                    matcher.end()
                ));
            }
        }
        
        return matches;
    }
}
