package com.attijariwafa.custody.EpargneOPCVM.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("plans.scheduler")
public class SchedulerProperties {

    private String cron = "0 0 18 * * *";
    private String zone = "Africa/Casablanca";
    private int batchSize = 25;
    /** A RUNNING run older than this is treated as abandoned (pod died mid-batch). */
    private Duration staleRunAfter = Duration.ofHours(1);

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getStaleRunAfter() {
        return staleRunAfter;
    }

    public void setStaleRunAfter(Duration staleRunAfter) {
        this.staleRunAfter = staleRunAfter;
    }
}
