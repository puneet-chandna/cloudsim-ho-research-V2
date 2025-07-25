package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ProgressTracker provides real-time tracking and reporting of experiment progress
 * across all phases of the Hippopotamus Optimization research framework.
 * 
 * <p>This class offers thread-safe progress monitoring with memory usage tracking,
 * estimated time remaining, and detailed phase reporting suitable for long-running
 * experiments on 16GB systems.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class ProgressTracker {
    private static final Logger logger = LoggerFactory.getLogger(ProgressTracker.class);
    
    private final Map<String, ProgressData> progressMap;
    private final LocalDateTime startTime;
    private final AtomicLong totalMemoryUsed;
    private final AtomicInteger gcCount;
    
    /**
     * Creates a new ProgressTracker instance with initial tracking state.
     */
    public ProgressTracker() {
        this.progressMap = new ConcurrentHashMap<>();
        this.startTime = LocalDateTime.now();
        this.totalMemoryUsed = new AtomicLong(0);
        this.gcCount = new AtomicInteger(0);
        
        logger.info("ProgressTracker initialized at {}", startTime);
    }
    
    /**
     * Initializes a task with the specified name and total count.
     * 
     * @param taskName the name of the task to initialize
     * @param totalCount the total number of items to process
     */
    public void initializeTask(String taskName, int totalCount) {
        if (taskName == null || taskName.trim().isEmpty()) {
            throw new IllegalArgumentException("Task name cannot be null or empty");
        }
        
        if (totalCount <= 0) {
            throw new IllegalArgumentException("Total count must be positive");
        }
        
        ProgressData data = progressMap.computeIfAbsent(taskName, k -> new ProgressData());
        data.update(0, totalCount);
        
        logger.info("Task '{}' initialized with {} total items", taskName, totalCount);
    }
    
    /**
     * Reports progress for a specific phase or task.
     * 
     * @param phaseName the name of the phase being tracked
     * @param current the current progress count
     * @param total the total expected count
     * @throws IllegalArgumentException if phaseName is null or empty
     */
    public void reportProgress(String phaseName, int current, int total) {
        if (phaseName == null || phaseName.trim().isEmpty()) {
            throw new IllegalArgumentException("Phase name cannot be null or empty");
        }
        
        if (total <= 0) {
            throw new IllegalArgumentException("Total must be positive");
        }
        
        if (current < 0 || current > total) {
            throw new IllegalArgumentException("Current must be between 0 and total");
        }
        
        ProgressData data = progressMap.computeIfAbsent(phaseName, k -> new ProgressData());
        data.update(current, total);
        
        // Log progress every 10% or at completion
        if (current == total || (current % Math.max(1, total / 10) == 0)) {
            logProgress(phaseName, data);
        }
        
        // Check memory usage periodically
        if (current % 100 == 0) {
            MemoryManager.checkMemoryUsage(phaseName);
        }
    }
    
    /**
     * Reports progress with additional metadata.
     * 
     * @param phaseName the name of the phase
     * @param current the current progress count
     * @param total the total expected count
     * @param metadata additional context information
     */
    public void reportProgress(String phaseName, int current, int total, String metadata) {
        reportProgress(phaseName, current, total);
        
        if (metadata != null && !metadata.trim().isEmpty()) {
            logger.debug("Phase [{}] metadata: {}", phaseName, metadata);
        }
    }
    
    /**
     * Gets the overall progress across all phases.
     * 
     * @return overall progress as a percentage (0-100)
     */
    public double getOverallProgress() {
        if (progressMap.isEmpty()) {
            return 0.0;
        }
        
        double totalWeightedProgress = 0.0;
        int totalWeight = 0;
        
        for (Map.Entry<String, ProgressData> entry : progressMap.entrySet()) {
            ProgressData data = entry.getValue();
            totalWeightedProgress += data.getProgress() * data.total;
            totalWeight += data.total;
        }
        
        return totalWeight > 0 ? (totalWeightedProgress / totalWeight) * 100 : 0.0;
    }
    
    /**
     * Gets estimated time remaining for a specific phase.
     * 
     * @param phaseName the name of the phase
     * @return estimated duration remaining, or null if not enough data
     */
    public Duration getEstimatedTimeRemaining(String phaseName) {
        ProgressData data = progressMap.get(phaseName);
        if (data == null || data.startTime == null || data.current == 0) {
            return null;
        }
        
        Duration elapsed = Duration.between(data.startTime, LocalDateTime.now());
        double itemsPerSecond = (double) data.current / elapsed.getSeconds();
        
        if (itemsPerSecond <= 0) {
            return null;
        }
        
        long remainingItems = data.total - data.current;
        long estimatedSeconds = (long) (remainingItems / itemsPerSecond);
        
        return Duration.ofSeconds(estimatedSeconds);
    }
    
    /**
     * Gets estimated time remaining for the entire experiment.
     * 
     * @return estimated duration remaining, or null if not enough data
     */
    public Duration getOverallEstimatedTimeRemaining() {
        if (progressMap.isEmpty()) {
            return null;
        }
        
        Duration totalElapsed = Duration.between(startTime, LocalDateTime.now());
        double overallProgress = getOverallProgress() / 100.0;
        
        if (overallProgress <= 0) {
            return null;
        }
        
        long totalEstimatedSeconds = (long) (totalElapsed.getSeconds() / overallProgress);
        long remainingSeconds = totalEstimatedSeconds - totalElapsed.getSeconds();
        
        return Duration.ofSeconds(Math.max(0, remainingSeconds));
    }
    
    /**
     * Records memory usage statistics.
     * 
     * @param memoryUsedBytes memory used in bytes
     */
    public void recordMemoryUsage(long memoryUsedBytes) {
        totalMemoryUsed.addAndGet(memoryUsedBytes);
    }
    
    /**
     * Increments the garbage collection counter.
     */
    public void incrementGCCount() {
        gcCount.incrementAndGet();
    }
    
    /**
     * Gets a summary report of all progress data.
     * 
     * @return formatted summary string
     */
    public String getSummaryReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n=== Progress Summary ===\n");
        report.append(String.format("Start Time: %s\n", startTime));
        report.append(String.format("Current Time: %s\n", LocalDateTime.now()));
        report.append(String.format("Overall Progress: %.2f%%\n", getOverallProgress()));
        
        Duration overallRemaining = getOverallEstimatedTimeRemaining();
        if (overallRemaining != null) {
            report.append(String.format("Estimated Time Remaining: %s\n", 
                formatDuration(overallRemaining)));
        }
        
        report.append(String.format("Total Memory Used: %.2f MB\n", 
            totalMemoryUsed.get() / (1024.0 * 1024.0)));
        report.append(String.format("GC Count: %d\n", gcCount.get()));
        
        report.append("\nPhase Details:\n");
        for (Map.Entry<String, ProgressData> entry : progressMap.entrySet()) {
            ProgressData data = entry.getValue();
            report.append(String.format("  %s: %d/%d (%.1f%%)", 
                entry.getKey(), data.current, data.total, data.getProgress() * 100));
            
            Duration phaseRemaining = getEstimatedTimeRemaining(entry.getKey());
            if (phaseRemaining != null) {
                report.append(String.format(", ETA: %s", formatDuration(phaseRemaining)));
            }
            report.append("\n");
        }
        
        report.append("========================\n");
        return report.toString();
    }
    
    /**
     * Logs a detailed progress report to the logger.
     */
    public void logSummaryReport() {
        logger.info(getSummaryReport());
    }
    
    /**
     * Resets progress for a specific phase.
     * 
     * @param phaseName the name of the phase to reset
     */
    public void resetPhase(String phaseName) {
        if (phaseName != null) {
            progressMap.remove(phaseName);
            logger.info("Reset progress for phase: {}", phaseName);
        }
    }
    
    /**
     * Clears all progress data.
     */
    public void resetAll() {
        progressMap.clear();
        totalMemoryUsed.set(0);
        gcCount.set(0);
        logger.info("All progress data reset");
    }
    
    private void logProgress(String phaseName, ProgressData data) {
        double progress = data.getProgress() * 100;
        Duration remaining = getEstimatedTimeRemaining(phaseName);
        
        StringBuilder message = new StringBuilder();
        message.append(String.format("Progress [%s]: %.1f%% (%d/%d)", 
            phaseName, progress, data.current, data.total));
        
        if (remaining != null) {
            message.append(String.format(", ETA: %s", formatDuration(remaining)));
        }
        
        // Log at appropriate level based on progress
        if (data.current == data.total) {
            logger.info(message.toString());
        } else if (progress >= 90) {
            logger.info(message.toString());
        } else if (progress >= 50) {
            logger.info(message.toString());
        } else {
            logger.info(message.toString());
        }
    }
    
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Inner class to store progress data for a single phase.
     */
    private static class ProgressData {
        private final LocalDateTime startTime;
        private volatile int current;
        private volatile int total;
        
        ProgressData() {
            this.startTime = LocalDateTime.now();
            this.current = 0;
            this.total = 0;
        }
        
        synchronized void update(int current, int total) {
            this.current = current;
            this.total = total;
        }
        
        double getProgress() {
            return total > 0 ? (double) current / total : 0.0;
        }
    }
    
    /**
     * Creates a progress bar string for console output.
     * 
     * @param phaseName the name of the phase
     * @param width the width of the progress bar
     * @return formatted progress bar string
     */
    public String createProgressBar(String phaseName, int width) {
        ProgressData data = progressMap.get(phaseName);
        if (data == null) {
            return String.format("[%s] No progress data", phaseName);
        }
        
        double progress = data.getProgress();
        int filled = (int) (width * progress);
        
        StringBuilder bar = new StringBuilder();
        bar.append('[');
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? '=' : ' ');
        }
        bar.append(']');
        
        return String.format("%s %s %.1f%% (%d/%d)", 
            phaseName, bar.toString(), progress * 100, data.current, data.total);
    }
}