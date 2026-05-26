package e2r.core;

/**
 * Data model for a discovered endpoint.
 */
public class DiscoveredEndpoint {
    
    private final String endpoint;
    private final String method;
    private final String host;
    private final String sourceUrl;
    private final String sourceContent;
    private final int matchLineNumber;
    private final int matchStartPosition;
    private final int matchEndPosition;
    
    public DiscoveredEndpoint(String endpoint, String method, String host, 
                              String sourceUrl, String sourceContent,
                              int matchLineNumber, int matchStartPosition, int matchEndPosition) {
        this.endpoint = endpoint;
        this.method = method;
        this.host = host;
        this.sourceUrl = sourceUrl;
        this.sourceContent = sourceContent;
        this.matchLineNumber = matchLineNumber;
        this.matchStartPosition = matchStartPosition;
        this.matchEndPosition = matchEndPosition;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public String getMethod() {
        return method;
    }
    
    public String getHost() {
        return host;
    }
    
    public String getSourceUrl() {
        return sourceUrl;
    }
    
    public String getSourceContent() {
        return sourceContent;
    }
    
    public int getMatchLineNumber() {
        return matchLineNumber;
    }
    
    public int getMatchStartPosition() {
        return matchStartPosition;
    }
    
    public int getMatchEndPosition() {
        return matchEndPosition;
    }
    
    /**
     * Generate unique key for deduplication.
     */
    public String getDeduplicationKey() {
        return method + "|" + endpoint + "|" + host;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", method, endpoint, host);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DiscoveredEndpoint that = (DiscoveredEndpoint) obj;
        return getDeduplicationKey().equals(that.getDeduplicationKey());
    }
    
    @Override
    public int hashCode() {
        return getDeduplicationKey().hashCode();
    }
}
