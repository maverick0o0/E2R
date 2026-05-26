package e2r.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Email address detection patterns.
 */
public class EmailPatterns {
    
    /**
     * Data class for email matches.
     */
    public static class EmailMatch {
        public final String email;
        public final int startPosition;
        public final int endPosition;
        
        public EmailMatch(String email, int startPosition, int endPosition) {
            this.email = email;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }
    
    // Standard email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})"
    );
    
    // Common false positive domains to exclude
    private static final Set<String> EXCLUDED_DOMAINS = Set.of(
        "example.com",
        "example.org",
        "example.net",
        "test.com",
        "test.org",
        "localhost",
        "domain.com",
        "email.com",
        "your-domain.com",
        "placeholder.com",
        "dummy.com",
        "sample.com",
        "acme.com",
        "company.com",
        "yourcompany.com",
        "sentry.io",           // Common in error handlers
        "w3.org"               // Schema definitions
    );
    
    // Common false positive prefixes (Modified to allow admin, support, info which are valuable)
    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
        "noreply",
        "no-reply",
        "donotreply",
        "test",
        "example",
        "placeholder",
        "sample",
        "demo",
        "dummy",
        "mock"
    );

    // Common file extensions that get mistaken for TLDs
    private static final Set<String> INVALID_TLDS = Set.of(
        "png", "jpg", "jpeg", "gif", "svg", "bmp", "ico",
        "css", "js", "jsx", "ts", "tsx", "html", "php",
        "woff", "woff2", "ttf", "eot", "otf",
        "mp3", "mp4", "wav", "avi", "mov",
        "zip", "tar", "gz", "rar", "7z",
        "exe", "dll", "so", "dylib", "bin"
    );
    
    /**
     * Find all email addresses in the given content.
     */
    public static List<EmailMatch> findEmails(String content) {
        List<EmailMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        if (content == null || content.isEmpty()) {
            return matches;
        }
        
        Matcher matcher = EMAIL_PATTERN.matcher(content);
        
        while (matcher.find()) {
            int start = matcher.start(1);
            int end = matcher.end(1);
            String email = matcher.group(1);
            
            // Check context to avoid file paths like /path/to/image@2x.png
            if (start > 0) {
                char prevChar = content.charAt(start - 1);
                if (prevChar == '/' || prevChar == '\\') {
                    continue; 
                }
            }
            
            String normalized = email.toLowerCase();
            
            // Deduplicate
            if (seen.contains(normalized)) {
                continue;
            }
            seen.add(normalized);
            
            // Validate
            if (isValidEmail(normalized)) {
                matches.add(new EmailMatch(
                    email, // Keep original case
                    start,
                    end
                ));
            }
        }
        
        return matches;
    }
    
    /**
     * Validate if an email is worth keeping (filter false positives).
     */
    private static boolean isValidEmail(String email) {
        String lower = email.toLowerCase();
        
        // Check TLD (avoid image@2x.png)
        int lastDot = lower.lastIndexOf('.');
        if (lastDot > 0 && lastDot < lower.length() - 1) {
            String tld = lower.substring(lastDot + 1);
            if (INVALID_TLDS.contains(tld)) {
                return false;
            }
        }
        
        // Check domain exclusions
        for (String excluded : EXCLUDED_DOMAINS) {
            if (lower.endsWith("@" + excluded)) {
                return false;
            }
        }
        
        // Check prefix exclusions
        String prefix = lower.substring(0, lower.indexOf('@'));
        for (String excluded : EXCLUDED_PREFIXES) {
            if (prefix.equals(excluded) || prefix.startsWith(excluded + ".") || 
                prefix.startsWith(excluded + "_") || prefix.startsWith(excluded + "-")) {
                return false;
            }
        }
        
        // Skip emails that look like file paths (common in source maps)
        if (email.contains("/") || email.contains("\\")) {
            return false;
        }
        
        // Skip emails in CSS/JS comments or code patterns
        if (email.contains("/*") || email.contains("*/") || email.contains("//")) {
            return false;
        }
        
        return true;
    }
}
