package e2r.core;

/**
 * Generic data model for all discovered items (endpoints, URLs, secrets, emails, files).
 */
public class DiscoveredItem {
    
    /**
     * Type of discovered item.
     */
    public enum ItemType {
        ENDPOINT("Endpoint"),
        URL("URL"),
        SECRET("Secret"),
        EMAIL("Email"),
        FILE("File"),
        PARAMETER("Parameter"),
        SOURCE("Source"),
        SINK("Sink");
        
        public final String displayName;
        
        ItemType(String displayName) {
            this.displayName = displayName;
        }
    }
    
    private final ItemType type;
    private final String value;
    private final String host;
    private final String sourceUrl;
    private final String sourceContent;
    private final int lineNumber;
    private final int startPosition;
    private final int endPosition;
    
    // Type-specific metadata
    private final String method;         // For endpoints
    private final String secretType;     // For secrets
    private final String fileCategory;   // For files
    private final String fileExtension;  // For files
    private final String parameterType;  // For parameters
    private final String sourceSinkCategory; // For DOM sources/sinks
    
    private DiscoveredItem(Builder builder) {
        this.type = builder.type;
        this.value = builder.value;
        this.host = builder.host;
        this.sourceUrl = builder.sourceUrl;
        this.sourceContent = builder.sourceContent;
        this.lineNumber = builder.lineNumber;
        this.startPosition = builder.startPosition;
        this.endPosition = builder.endPosition;
        this.method = builder.method;
        this.secretType = builder.secretType;
        this.fileCategory = builder.fileCategory;
        this.fileExtension = builder.fileExtension;
        this.parameterType = builder.parameterType;
        this.sourceSinkCategory = builder.sourceSinkCategory;
    }
    
    // Getters
    public ItemType getType() { return type; }
    public String getValue() { return value; }
    public String getHost() { return host; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceContent() { return sourceContent; }
    public int getLineNumber() { return lineNumber; }
    public int getStartPosition() { return startPosition; }
    public int getEndPosition() { return endPosition; }
    public String getMethod() { return method; }
    public String getSecretType() { return secretType; }
    public String getFileCategory() { return fileCategory; }
    public String getFileExtension() { return fileExtension; }
    public String getParameterType() { return parameterType; }
    public String getSourceSinkCategory() { return sourceSinkCategory; }
    
    /**
     * Generate unique key for deduplication.
     */
    public String getDeduplicationKey() {
        switch (type) {
            case ENDPOINT:
                return "EP|" + method + "|" + value + "|" + host;
            case URL:
                return "URL|" + value + "|" + host;
            case SECRET:
                return "SEC|" + secretType + "|" + value;
            case EMAIL:
                return "EMAIL|" + value;
            case FILE:
                return "FILE|" + value;
            case PARAMETER:
                return "PARAM|" + parameterType + "|" + value + "|" + host;
            case SOURCE:
                return "SRC|" + sourceSinkCategory + "|" + value + "|" + host;
            case SINK:
                return "SNK|" + sourceSinkCategory + "|" + value + "|" + host;
            default:
                return type + "|" + value;
        }
    }
    
    /**
     * Get display value based on type.
     */
    public String getDisplayValue() {
        return value;
    }
    
    /**
     * Get secondary info for table display.
     */
    public String getSecondaryInfo() {
        switch (type) {
            case ENDPOINT:
                return method;
            case SECRET:
                return secretType;
            case FILE:
                return fileCategory;
            case PARAMETER:
                return parameterType;
            case SOURCE:
            case SINK:
                return sourceSinkCategory;
            default:
                return "";
        }
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s", type.displayName, value);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DiscoveredItem that = (DiscoveredItem) obj;
        return getDeduplicationKey().equals(that.getDeduplicationKey());
    }
    
    @Override
    public int hashCode() {
        return getDeduplicationKey().hashCode();
    }
    
    // ===== Builder Pattern =====
    
    public static Builder builder(ItemType type, String value) {
        return new Builder(type, value);
    }
    
    public static class Builder {
        private final ItemType type;
        private final String value;
        private String host = "";
        private String sourceUrl = "";
        private String sourceContent = "";
        private int lineNumber = 0;
        private int startPosition = 0;
        private int endPosition = 0;
        private String method = "";
        private String secretType = "";
        private String fileCategory = "";
        private String fileExtension = "";
        private String parameterType = "";
        private String sourceSinkCategory = "";
        
        public Builder(ItemType type, String value) {
            this.type = type;
            this.value = value;
        }
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        
        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }
        
        public Builder sourceContent(String sourceContent) {
            this.sourceContent = sourceContent;
            return this;
        }
        
        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }
        
        public Builder position(int start, int end) {
            this.startPosition = start;
            this.endPosition = end;
            return this;
        }
        
        public Builder method(String method) {
            this.method = method;
            return this;
        }
        
        public Builder secretType(String secretType) {
            this.secretType = secretType;
            return this;
        }
        
        public Builder fileCategory(String fileCategory) {
            this.fileCategory = fileCategory;
            return this;
        }
        
        public Builder fileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
            return this;
        }
        
        public Builder parameterType(String parameterType) {
            this.parameterType = parameterType;
            return this;
        }
        
        public Builder sourceSinkCategory(String category) {
            this.sourceSinkCategory = category;
            return this;
        }
        
        public DiscoveredItem build() {
            return new DiscoveredItem(this);
        }
    }
    
    // ===== Factory Methods =====
    
    /**
     * Create from existing DiscoveredEndpoint for compatibility.
     */
    public static DiscoveredItem fromEndpoint(DiscoveredEndpoint ep) {
        boolean isUrl = ep.getEndpoint().startsWith("http://") || 
                        ep.getEndpoint().startsWith("https://") ||
                        ep.getEndpoint().startsWith("//");
        
        return builder(isUrl ? ItemType.URL : ItemType.ENDPOINT, ep.getEndpoint())
            .host(ep.getHost())
            .sourceUrl(ep.getSourceUrl())
            .sourceContent(ep.getSourceContent())
            .lineNumber(ep.getMatchLineNumber())
            .position(ep.getMatchStartPosition(), ep.getMatchEndPosition())
            .method(ep.getMethod())
            .build();
    }
    
    /**
     * Create from SecretMatch.
     */
    public static DiscoveredItem fromSecret(SecretPatterns.SecretMatch match, 
                                            String sourceUrl, String sourceContent, 
                                            String host, int lineNumber) {
        return builder(ItemType.SECRET, match.value)
            .host(host)
            .sourceUrl(sourceUrl)
            .sourceContent(sourceContent)
            .lineNumber(lineNumber)
            .position(match.startPosition, match.endPosition)
            .secretType(match.secretType)
            .build();
    }
    
    /**
     * Create from EmailMatch.
     */
    public static DiscoveredItem fromEmail(EmailPatterns.EmailMatch match,
                                           String sourceUrl, String sourceContent,
                                           String host, int lineNumber) {
        return builder(ItemType.EMAIL, match.email)
            .host(host)
            .sourceUrl(sourceUrl)
            .sourceContent(sourceContent)
            .lineNumber(lineNumber)
            .position(match.startPosition, match.endPosition)
            .build();
    }
    
    /**
     * Create from FileMatch.
     */
    public static DiscoveredItem fromFile(FilePatterns.FileMatch match,
                                          String sourceUrl, String sourceContent,
                                          String host, int lineNumber) {
        return builder(ItemType.FILE, match.filename)
            .host(host)
            .sourceUrl(sourceUrl)
            .sourceContent(sourceContent)
            .lineNumber(lineNumber)
            .position(match.startPosition, match.endPosition)
            .fileCategory(match.category.displayName)
            .fileExtension(match.extension)
            .build();
    }
    
    /**
     * Create from Parameter parameters.
     */
    public static DiscoveredItem fromParameter(String name, String type,
                                               String sourceUrl, String sourceContent,
                                               String host, int lineNumber,
                                               int startPosition, int endPosition) {
        return builder(ItemType.PARAMETER, name)
            .host(host)
            .sourceUrl(sourceUrl)
            .sourceContent(sourceContent)
            .lineNumber(lineNumber)
            .position(startPosition, endPosition)
            .parameterType(type)
            .build();
    }
    
    /**
     * Create from Source.
     */
    public static DiscoveredItem fromSource(String name, String category,
                                            String sourceUrl, String sourceContent,
                                            String host, int lineNumber,
                                            int startPosition, int endPosition) {
        return builder(ItemType.SOURCE, name)
            .host(host)
            .sourceUrl(sourceUrl)
            .sourceContent(sourceContent)
            .lineNumber(lineNumber)
            .position(startPosition, endPosition)
            .sourceSinkCategory(category)
            .build();
    }
    
    /**
     * Create from Sink.
     */
    public static DiscoveredItem fromSink(String name, String category,
                                          String sourceUrl, String sourceContent,
                                          String host, int lineNumber,
                                          int startPosition, int endPosition) {
        return builder(ItemType.SINK, name)
            .host(host)
            .sourceUrl(sourceUrl)
            .sourceContent(sourceContent)
            .lineNumber(lineNumber)
            .position(startPosition, endPosition)
            .sourceSinkCategory(category)
            .build();
    }
}
