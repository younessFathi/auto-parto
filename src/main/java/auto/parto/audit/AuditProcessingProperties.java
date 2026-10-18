package auto.parto.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auto.parto.audit")
public class AuditProcessingProperties {

    private boolean enabled;
    private String r2dbcUrl = "r2dbc:postgresql://localhost:5432/auto_parto_audit";
    private String username = "";
    private String password = "";
    private String workerId = "audit-worker";
    private int batchSize = 100;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private int maxAttempts = 3;
    private int maxConcurrency = 4;
    private Duration baseRetryDelay = Duration.ofSeconds(2);
    private Duration maxRetryDelay = Duration.ofMinutes(1);
    private Duration reconciliationInterval = Duration.ofMinutes(5);
    private Duration reconciliationLookback = Duration.ofDays(1);
    private Duration cleanupInterval = Duration.ofHours(1);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getR2dbcUrl() { return r2dbcUrl; }
    public void setR2dbcUrl(String r2dbcUrl) { this.r2dbcUrl = r2dbcUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public Duration getBaseRetryDelay() { return baseRetryDelay; }
    public void setBaseRetryDelay(Duration baseRetryDelay) { this.baseRetryDelay = baseRetryDelay; }
    public Duration getMaxRetryDelay() { return maxRetryDelay; }
    public void setMaxRetryDelay(Duration maxRetryDelay) { this.maxRetryDelay = maxRetryDelay; }
    public Duration getReconciliationInterval() { return reconciliationInterval; }
    public void setReconciliationInterval(Duration reconciliationInterval) { this.reconciliationInterval = reconciliationInterval; }
    public Duration getReconciliationLookback() { return reconciliationLookback; }
    public void setReconciliationLookback(Duration reconciliationLookback) { this.reconciliationLookback = reconciliationLookback; }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
}
