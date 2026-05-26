package e2r.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File reference detection patterns for sensitive file types.
 * Detects references to SQL, config, backup, certificate, and other sensitive files.
 */
public class FilePatterns {
    
    /**
     * File category enum for grouping.
     */
    public enum FileCategory {
        DATA("Data"),           // SQL, CSV, JSON, XML, etc.
        CONFIG("Config"),       // ENV, conf, ini, etc.
        BACKUP("Backup"),       // bak, backup, old, etc.
        CERTS("Certs"),         // key, pem, crt, etc.
        DOCS("Docs"),           // pdf, doc, docx
        ARCHIVES("Archives"),   // zip, tar, gz, etc.
        SCRIPTS("Scripts");     // sh, bat, ps1, py, etc.
        
        public final String displayName;
        
        FileCategory(String displayName) {
            this.displayName = displayName;
        }
    }
    
    /**
     * Data class for file matches.
     */
    public static class FileMatch {
        public final String filename;
        public final String extension;
        public final FileCategory category;
        public final int startPosition;
        public final int endPosition;
        
        public FileMatch(String filename, String extension, FileCategory category, 
                         int startPosition, int endPosition) {
            this.filename = filename;
            this.extension = extension;
            this.category = category;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }
    }
    
    // File pattern with all sensitive extensions
    private static final Pattern FILE_PATTERN = Pattern.compile(
        "[\"']([a-zA-Z0-9_/.\\-]+\\.(" +
        // Data files
        "sql|csv|xlsx|xls|json|xml|yaml|yml|" +
        // Config/logs
        "txt|log|conf|config|cfg|ini|env|properties|" +
        // Backups
        "bak|backup|old|orig|copy|swp|swo|" +
        // Certificates/Keys
        "key|pem|crt|cer|p12|pfx|jks|keystore|" +
        // Documents
        "doc|docx|pdf|rtf|" +
        // Archives
        "zip|tar|gz|tgz|rar|7z|bz2|xz|" +
        // Scripts
        "sh|bash|bat|cmd|ps1|py|rb|pl|php" +
        "))[\"']",
        Pattern.CASE_INSENSITIVE
    );
    
    // Extension to category mapping
    private static FileCategory getCategory(String extension) {
        String ext = extension.toLowerCase();
        
        // Data files
        if (ext.matches("sql|csv|xlsx|xls|json|xml|yaml|yml")) {
            return FileCategory.DATA;
        }
        
        // Config/logs
        if (ext.matches("txt|log|conf|config|cfg|ini|env|properties")) {
            return FileCategory.CONFIG;
        }
        
        // Backups
        if (ext.matches("bak|backup|old|orig|copy|swp|swo")) {
            return FileCategory.BACKUP;
        }
        
        // Certificates/Keys
        if (ext.matches("key|pem|crt|cer|p12|pfx|jks|keystore")) {
            return FileCategory.CERTS;
        }
        
        // Documents
        if (ext.matches("doc|docx|pdf|rtf")) {
            return FileCategory.DOCS;
        }
        
        // Archives
        if (ext.matches("zip|tar|gz|tgz|rar|7z|bz2|xz")) {
            return FileCategory.ARCHIVES;
        }
        
        // Scripts
        if (ext.matches("sh|bash|bat|cmd|ps1|py|rb|pl|php")) {
            return FileCategory.SCRIPTS;
        }
        
        return FileCategory.DATA; // Default
    }
    
    // Exclusion patterns for false positives
    private static final Set<String> EXCLUDED_FILENAMES = Set.of(
        "package.json",
        "tsconfig.json",
        "jsconfig.json",
        "webpack.config.js",
        "babel.config.js",
        "eslint.config.js",
        ".eslintrc.json",
        "manifest.json",
        "composer.json",
        "bower.json",
        "angular.json",
        "vite.config.js",
        "rollup.config.js"
    );
    
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "node_modules",
        ".git",
        "__pycache__",
        ".vscode",
        ".idea"
    );
    
    /**
     * Find all sensitive file references in the given content.
     */
    public static List<FileMatch> findFiles(String content) {
        List<FileMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        if (content == null || content.isEmpty()) {
            return matches;
        }
        
        Matcher matcher = FILE_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String fullPath = matcher.group(1);
            String extension = matcher.group(2).toLowerCase();
            
            // Get just the filename
            String filename = fullPath;
            int lastSlash = fullPath.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < fullPath.length() - 1) {
                filename = fullPath.substring(lastSlash + 1);
            }
            
            // Deduplicate
            if (seen.contains(fullPath.toLowerCase())) {
                continue;
            }
            seen.add(fullPath.toLowerCase());
            
            // Validate
            if (isValidFile(fullPath, filename)) {
                matches.add(new FileMatch(
                    filename,
                    extension,
                    getCategory(extension),
                    matcher.start(1),
                    matcher.end(1)
                ));
            }
        }
        
        return matches;
    }
    
    /**
     * Validate if a file reference is worth keeping.
     */
    private static boolean isValidFile(String fullPath, String filename) {
        String lower = fullPath.toLowerCase();
        String filenameLower = filename.toLowerCase();
        
        // Check excluded filenames
        if (EXCLUDED_FILENAMES.contains(filenameLower)) {
            return false;
        }
        
        // Check excluded paths
        for (String excluded : EXCLUDED_PATHS) {
            if (lower.contains("/" + excluded + "/") || lower.contains("\\" + excluded + "\\")) {
                return false;
            }
        }
        
        // Skip obvious non-file patterns
        if (lower.contains("://") && !lower.contains("file://")) {
            return false;
        }
        
        // Skip MIME types that look like files
        if (lower.startsWith("application/") || lower.startsWith("text/")) {
            return false;
        }
        
        // Skip version numbers that look like files (e.g., "1.2.3.json")
        if (filename.matches("^[0-9]+\\.[0-9]+\\.[0-9]+\\..*")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get all supported file extensions.
     */
    public static Set<String> getSupportedExtensions() {
        return Set.of(
            // Data
            "sql", "csv", "xlsx", "xls", "json", "xml", "yaml", "yml",
            // Config
            "txt", "log", "conf", "config", "cfg", "ini", "env", "properties",
            // Backup
            "bak", "backup", "old", "orig", "copy", "swp", "swo",
            // Certs
            "key", "pem", "crt", "cer", "p12", "pfx", "jks", "keystore",
            // Docs
            "doc", "docx", "pdf", "rtf",
            // Archives
            "zip", "tar", "gz", "tgz", "rar", "7z", "bz2", "xz",
            // Scripts
            "sh", "bash", "bat", "cmd", "ps1", "py", "rb", "pl", "php"
        );
    }
}
