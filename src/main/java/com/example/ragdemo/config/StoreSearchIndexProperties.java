package com.example.ragdemo.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.store.search-index")
public class StoreSearchIndexProperties {

    private boolean syncEnabled = true;
    private Duration fixedDelay = Duration.ofSeconds(5);
    private int batchSize = 50;
    private int maxRetries = 5;
    private Duration initialBackoff = Duration.ofSeconds(10);
    private Duration processingTimeout = Duration.ofMinutes(5);
    private boolean reconciliationEnabled;
    private Duration reconciliationFixedDelay = Duration.ofHours(6);

    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }
    public Duration getFixedDelay() { return fixedDelay; }
    public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public Duration getProcessingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
    public boolean isReconciliationEnabled() { return reconciliationEnabled; }
    public void setReconciliationEnabled(boolean reconciliationEnabled) { this.reconciliationEnabled = reconciliationEnabled; }
    public Duration getReconciliationFixedDelay() { return reconciliationFixedDelay; }
    public void setReconciliationFixedDelay(Duration reconciliationFixedDelay) { this.reconciliationFixedDelay = reconciliationFixedDelay; }
}
