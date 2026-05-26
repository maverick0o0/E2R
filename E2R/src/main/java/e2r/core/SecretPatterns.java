package e2r.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secret detection patterns for various API keys, tokens, and credentials.
 * Optimized and deduplicated from common secret scanning tools.
 */
public class SecretPatterns {
    
    /**
     * Data class for secret matches.
     */
    public static class SecretMatch {
        public final String value;
        public final String secretType;
        public final int startPosition;
        public final int endPosition;
        
        public SecretMatch(String value, String secretType, int startPosition, int endPosition) {
            this.value = value;
            this.secretType = secretType;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }
    
    /**
     * Pattern entry with compiled regex and description.
     */
    private static class PatternEntry {
        final Pattern pattern;
        final String description;
        final int captureGroup;
        
        PatternEntry(String regex, String description) {
            this(regex, description, 1);
        }
        
        PatternEntry(String regex, String description, int captureGroup) {
            this.pattern = Pattern.compile(regex);
            this.description = description;
            this.captureGroup = captureGroup;
        }
    }
    
    // All secret patterns organized by category
    private static final List<PatternEntry> PATTERNS = new ArrayList<>();
    
    static {
        // ===== CLOUD PROVIDERS =====
        
        // AWS
        PATTERNS.add(new PatternEntry(
            "((?:AKIA|ASIA|AROA|AIDA)[A-Z0-9]{16})",
            "AWS Access Key ID"));
        PATTERNS.add(new PatternEntry(
            "(?i)aws_secret_access_key[\"'\\s]*[:=][\"'\\s]*([A-Za-z0-9/+=]{40})",
            "AWS Secret Access Key"));
        PATTERNS.add(new PatternEntry(
            "(?i)aws_session_token[\"'\\s]*[:=][\"'\\s]*([A-Za-z0-9/+=]{16,})",
            "AWS Session Token"));
        
        // Google Cloud / GCP
        PATTERNS.add(new PatternEntry(
            "(AIza[0-9A-Za-z\\-_]{35})",
            "Google Cloud API Key"));
        PATTERNS.add(new PatternEntry(
            "(ya29\\.[0-9A-Za-z\\-_]+)",
            "Google OAuth Access Token"));
        
        // Azure
        PATTERNS.add(new PatternEntry(
            "(?i)(DefaultEndpointsProtocol=https;AccountName=[^;]+;AccountKey=[A-Za-z0-9+/=]{40,};EndpointSuffix=core\\.windows\\.net)",
            "Azure Storage Connection String"));
        PATTERNS.add(new PatternEntry(
            "(?i)(sv=\\d{4}-\\d{2}-\\d{2}[^\"'\\s]*sig=[A-Za-z0-9%/+]+=*)",
            "Azure SAS Token"));
        
        // Alibaba Cloud
        PATTERNS.add(new PatternEntry(
            "(LTAI[a-zA-Z0-9]{20})",
            "Alibaba Cloud AccessKey"));
        
        // ===== GIT PROVIDERS =====
        
        // GitHub - all token types (ghp, gho, ghu, ghs, ghr)
        PATTERNS.add(new PatternEntry(
            "(gh[pousr]_[a-zA-Z0-9]{36,255})",
            "GitHub Token"));
        
        // GitLab
        PATTERNS.add(new PatternEntry(
            "(glpat-[0-9a-zA-Z\\-_]{20,})",
            "GitLab Personal Access Token"));
        
        // Bitbucket
        PATTERNS.add(new PatternEntry(
            "(bb[a-zA-Z0-9]{30,})",
            "Bitbucket Token"));
        
        // ===== PAYMENT SERVICES =====
        
        // Stripe
        PATTERNS.add(new PatternEntry(
            "(sk_(?:live|test)_[0-9a-zA-Z]{24,})",
            "Stripe Secret Key"));
        PATTERNS.add(new PatternEntry(
            "(rk_(?:live|test)_[0-9a-zA-Z]{24,})",
            "Stripe Restricted Key"));
        PATTERNS.add(new PatternEntry(
            "(pk_(?:live|test)_[0-9a-zA-Z]{24,})",
            "Stripe Publishable Key"));
        
        // Square
        PATTERNS.add(new PatternEntry(
            "(sq0atp-[0-9A-Za-z\\-_]{22,})",
            "Square Access Token"));
        
        // Shopify
        PATTERNS.add(new PatternEntry(
            "(shpat_[0-9a-fA-F]{32})",
            "Shopify Private App Token"));
        
        // ===== MESSAGING PLATFORMS =====
        
        // Slack - all token types (xoxb, xoxp, xoxa, xoxc, xoxd)
        PATTERNS.add(new PatternEntry(
            "(xox[baprscd]-[0-9a-zA-Z\\-]{10,48})",
            "Slack Token"));
        
        // Discord
        PATTERNS.add(new PatternEntry(
            "([a-zA-Z0-9]{24}\\.[a-zA-Z0-9]{6}\\.[a-zA-Z0-9_-]{27})",
            "Discord Bot Token"));
        PATTERNS.add(new PatternEntry(
            "(mfa\\.[0-9A-Za-z\\-_]{80,})",
            "Discord MFA Token"));
        
        // Telegram
        PATTERNS.add(new PatternEntry(
            "([0-9]{8,10}:[A-Za-z0-9_-]{35})",
            "Telegram Bot Token"));
        
        // ===== EMAIL/SMS SERVICES =====
        
        // Twilio
        PATTERNS.add(new PatternEntry(
            "(SK[0-9a-fA-F]{32})",
            "Twilio API Key"));
        PATTERNS.add(new PatternEntry(
            "(AC[0-9a-fA-F]{32})",
            "Twilio Account SID"));
        
        // SendGrid
        PATTERNS.add(new PatternEntry(
            "(SG\\.[a-zA-Z0-9\\-_]{22,}\\.[a-zA-Z0-9\\-_]{22,})",
            "SendGrid API Key"));
        
        // Mailgun
        PATTERNS.add(new PatternEntry(
            "(key-[0-9a-zA-Z]{32})",
            "Mailgun API Key"));
        
        // Mailchimp
        PATTERNS.add(new PatternEntry(
            "([0-9a-f]{32}-us[0-9]{1,2})",
            "Mailchimp API Key"));
        
        // ===== CI/CD & PACKAGE REGISTRIES =====
        
        // NPM
        PATTERNS.add(new PatternEntry(
            "(npm_[a-zA-Z0-9]{36})",
            "NPM Access Token"));
        
        // PyPI
        PATTERNS.add(new PatternEntry(
            "(pypi-[A-Za-z0-9\\-_]{30,})",
            "PyPI Upload Token"));
        
        // Heroku
        PATTERNS.add(new PatternEntry(
            "(?i)heroku[\"'\\s]*[:=][\"'\\s]*([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})",
            "Heroku API Key"));
        
        // ===== OBSERVABILITY =====
        
        // Datadog
        PATTERNS.add(new PatternEntry(
            "(?i)(?:DD_API_KEY|DATADOG_API_KEY)[\"'\\s]*[:=][\"'\\s]*([0-9a-fA-F]{32})",
            "Datadog API Key"));
        
        // New Relic
        PATTERNS.add(new PatternEntry(
            "(NRII-[A-Za-z0-9\\-_]{20,})",
            "New Relic Insert Key"));
        
        // Sentry
        PATTERNS.add(new PatternEntry(
            "(sentry[_-]?auth[_-]?token[=:][0-9a-fA-F]{64})",
            "Sentry Auth Token"));
        
        // Cloudflare
        PATTERNS.add(new PatternEntry(
            "(cfp_[A-Za-z0-9\\-_]{30,})",
            "Cloudflare API Token"));
        
        // ===== DATABASE CONNECTIONS =====
        
        // MySQL
        PATTERNS.add(new PatternEntry(
            "(mysql://[^\\s\"'<>]+)",
            "MySQL Connection String"));
        
        // Redis
        PATTERNS.add(new PatternEntry(
            "(redis://[^\\s\"'<>]+)",
            "Redis Connection String"));
        
        // PostgreSQL
        PATTERNS.add(new PatternEntry(
            "(postgres(?:ql)?://[^\\s\"'<>]+)",
            "PostgreSQL Connection String"));
        
        // MongoDB
        PATTERNS.add(new PatternEntry(
            "(mongodb(?:\\+srv)?://[^\\s\"'<>]+)",
            "MongoDB Connection String"));
        
        // ===== PRIVATE KEYS =====
        
        PATTERNS.add(new PatternEntry(
            "(-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY(?: BLOCK)?-----)",
            "Private Key Header"));
        PATTERNS.add(new PatternEntry(
            "(-----BEGIN PGP PRIVATE KEY BLOCK-----)",
            "PGP Private Key"));
        
        // ===== TOKENS =====
        
        // JWT
        PATTERNS.add(new PatternEntry(
            "(eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]+)",
            "JSON Web Token (JWT)"));
        
        // Facebook
        PATTERNS.add(new PatternEntry(
            "(EAACEdEose0cBA[0-9A-Za-z]+)",
            "Facebook Access Token"));
        
        // Cloudinary
        PATTERNS.add(new PatternEntry(
            "(cloudinary://[0-9]+:[0-9A-Za-z@_\\-]+@[a-z]+)",
            "Cloudinary Credentials"));
        
        // Amazon MWS
        PATTERNS.add(new PatternEntry(
            "(amzn\\.mws\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})",
            "Amazon MWS Auth Token"));
    }
    
    /**
     * Find all secrets in the given content.
     */
    public static List<SecretMatch> findSecrets(String content) {
        List<SecretMatch> matches = new ArrayList<>();
        
        if (content == null || content.isEmpty()) {
            return matches;
        }
        
        for (PatternEntry entry : PATTERNS) {
            Matcher matcher = entry.pattern.matcher(content);
            while (matcher.find()) {
                String value = matcher.group(entry.captureGroup);
                if (value != null && isValidSecret(value, entry.description)) {
                    matches.add(new SecretMatch(
                        value,
                        entry.description,
                        matcher.start(entry.captureGroup),
                        matcher.end(entry.captureGroup)
                    ));
                }
            }
        }
        
        return matches;
    }
    
    /**
     * Additional validation to reduce false positives.
     */
    private static boolean isValidSecret(String value, String type) {
        // Skip very short matches
        if (value.length() < 8) {
            return false;
        }
        
        // Skip placeholder values
        String lower = value.toLowerCase();
        if (lower.contains("example") || lower.contains("placeholder") || 
            lower.contains("your_") || lower.contains("xxx") ||
            lower.contains("test") || lower.contains("demo") ||
            lower.contains("sample") || lower.contains("dummy")) {
            return false;
        }
        
        // Skip obvious non-secrets (repeated chars)
        if (value.matches("^(.)\\1+$")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the count of registered patterns.
     */
    public static int getPatternCount() {
        return PATTERNS.size();
    }
}
