package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks and displays progress information for the experimental suite.
 * Provides clean console output with progress bars and status updates.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-01-26
 */
public class ProgressTracker {
    private static final Logger logger = LoggerFactory.getLogger(ProgressTracker.class);
    private static final Marker PROGRESS_MARKER = MarkerFactory.getMarker("PROGRESS");
    
    private final AtomicInteger totalTasks;
    private final AtomicInteger completedTasks;
    private final AtomicLong startTime;
    private final AtomicLong lastUpdateTime;
    
    private String currentTask;
    private String currentAlgorithm;
    private String currentScenario;
    private int currentReplication;
    
    public ProgressTracker() {
        this.totalTasks = new AtomicInteger(0);
        this.completedTasks = new AtomicInteger(0);
        this.startTime = new AtomicLong(0);
        this.lastUpdateTime = new AtomicLong(0);
        this.currentTask = "";
        this.currentAlgorithm = "";
        this.currentScenario = "";
        this.currentReplication = 0;
    }
    
    /**
     * Initializes the progress tracker with total number of tasks.
     * 
     * @param taskName Name of the overall task
     * @param totalTasks Total number of tasks to complete
     */
    public void initializeTask(String taskName, int totalTasks) {
        this.currentTask = taskName;
        this.totalTasks.set(totalTasks);
        this.completedTasks.set(0);
        this.startTime.set(System.currentTimeMillis());
        this.lastUpdateTime.set(System.currentTimeMillis());
        
        printHeader();
        printProgress();
    }
    
    /**
     * Reports progress for a specific operation.
     * 
     * @param operationName Name of the current operation
     * @param completed Number of completed items
     * @param total Total number of items
     */
    public void reportProgress(String operationName, int completed, int total) {
        this.completedTasks.set(completed);
        updateProgress(operationName);
    }
    
    /**
     * Updates the current algorithm being processed.
     * 
     * @param algorithm Algorithm name
     */
    public void setCurrentAlgorithm(String algorithm) {
        this.currentAlgorithm = algorithm;
        updateProgress("Algorithm: " + algorithm);
    }
    
    /**
     * Updates the current scenario being processed.
     * 
     * @param scenario Scenario name
     */
    public void setCurrentScenario(String scenario) {
        this.currentScenario = scenario;
        updateProgress("Scenario: " + scenario);
    }
    
    /**
     * Updates the current replication being processed.
     * 
     * @param replication Replication number
     */
    public void setCurrentReplication(int replication) {
        this.currentReplication = replication;
        updateProgress("Replication: " + replication);
    }
    
    /**
     * Increments the completed task count.
     */
    public void incrementCompleted() {
        int completed = this.completedTasks.incrementAndGet();
        updateProgress("Task completed");
        
        if (completed >= totalTasks.get()) {
            printCompletion();
        }
    }
    
    /**
     * Prints the header information.
     */
    private void printHeader() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logger.info(PROGRESS_MARKER, "=".repeat(80));
        logger.info(PROGRESS_MARKER, "Hippopotamus Optimization Research Framework");
        logger.info(PROGRESS_MARKER, "Run Started: {}", timestamp);
        logger.info(PROGRESS_MARKER, "Total Tasks: {}", totalTasks.get());
        logger.info(PROGRESS_MARKER, "=".repeat(80));
    }
    
    /**
     * Updates and prints the current progress.
     * 
     * @param status Current status message
     */
    private void updateProgress(String status) {
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastUpdateTime.get();
        
        // Update progress every 2 seconds or when status changes
        if (currentTime - lastUpdate > 2000 || !status.equals("Task completed")) {
            lastUpdateTime.set(currentTime);
            printProgress(status);
        }
    }
    
    /**
     * Prints the current progress with status.
     * 
     * @param status Current status message
     */
    private void printProgress(String status) {
        int completed = completedTasks.get();
        int total = totalTasks.get();
        double percentage = total > 0 ? (double) completed / total * 100 : 0;
        
        long elapsed = System.currentTimeMillis() - startTime.get();
        long estimatedTotal = 0;
        long remaining = 0;
        
        // Calculate estimated total time only if we have completed tasks
        if (total > 0 && completed > 0) {
            estimatedTotal = (elapsed * total) / completed;
            remaining = Math.max(0, estimatedTotal - elapsed);
        }
        
        String progressBar = createProgressBar(percentage);
        String timeInfo = formatTimeInfo(elapsed, remaining);
        
        logger.info(PROGRESS_MARKER, "Progress: {}% [{}] {} - {}", 
                   String.format("%.1f", percentage), progressBar, timeInfo, status);
        
        if (!currentAlgorithm.isEmpty() || !currentScenario.isEmpty()) {
            String details = String.format("Current: %s | %s | Rep: %d", 
                                         currentAlgorithm, currentScenario, currentReplication);
            logger.info(PROGRESS_MARKER, "  {}", details);
        }
    }
    
    /**
     * Prints the current progress without status.
     */
    private void printProgress() {
        printProgress("Initializing...");
    }
    
    /**
     * Creates a visual progress bar.
     * 
     * @param percentage Completion percentage
     * @return Progress bar string
     */
    private String createProgressBar(double percentage) {
        int barLength = 30;
        int filledLength = (int) (barLength * percentage / 100);
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        return bar.toString();
    }
    
    /**
     * Formats time information for display.
     * 
     * @param elapsed Elapsed time in milliseconds
     * @param remaining Remaining time in milliseconds
     * @return Formatted time string
     */
    private String formatTimeInfo(long elapsed, long remaining) {
        String elapsedStr = formatDuration(elapsed);
        String remainingStr = formatDuration(remaining);
        return String.format("Elapsed: %s | Remaining: %s", elapsedStr, remainingStr);
    }
    
    /**
     * Formats duration in milliseconds to human-readable format.
     * 
     * @param milliseconds Duration in milliseconds
     * @return Formatted duration string
     */
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Prints completion information.
     */
    private void printCompletion() {
        long totalTime = System.currentTimeMillis() - startTime.get();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        logger.info(PROGRESS_MARKER, "=".repeat(80));
        logger.info(PROGRESS_MARKER, "EXPERIMENT COMPLETED SUCCESSFULLY!");
        logger.info(PROGRESS_MARKER, "Run Finished: {}", timestamp);
        logger.info(PROGRESS_MARKER, "Total Time: {}", formatDuration(totalTime));
        logger.info(PROGRESS_MARKER, "Tasks Completed: {}/{}", completedTasks.get(), totalTasks.get());
        logger.info(PROGRESS_MARKER, "=".repeat(80));
    }
    
    /**
     * Prints an error message.
     * 
     * @param message Error message
     */
    public void printError(String message) {
        logger.error(PROGRESS_MARKER, "ERROR: {}", message);
    }
    
    /**
     * Prints a warning message.
     * 
     * @param message Warning message
     */
    public void printWarning(String message) {
        logger.warn(PROGRESS_MARKER, "WARNING: {}", message);
    }
    
    /**
     * Prints an info message.
     * 
     * @param message Info message
     */
    public void printInfo(String message) {
        logger.info(PROGRESS_MARKER, "INFO: {}", message);
    }
    
    /**
     * Gets a summary report of the progress.
     * 
     * @return Summary report string
     */
    public String getSummaryReport() {
        int completed = completedTasks.get();
        int total = totalTasks.get();
        double percentage = total > 0 ? (double) completed / total * 100 : 0;
        long totalTime = System.currentTimeMillis() - startTime.get();
        
        return String.format("Progress Summary: %d/%d tasks completed (%.1f%%) in %s", 
                           completed, total, percentage, formatDuration(totalTime));
    }
    
    /**
     * Logs the summary report.
     */
    public void logSummaryReport() {
        logger.info(PROGRESS_MARKER, getSummaryReport());
    }
    
    /**
     * Resets the progress for a specific phase.
     * 
     * @param phaseName Name of the phase to reset
     */
    public void resetPhase(String phaseName) {
        logger.info(PROGRESS_MARKER, "Resetting phase: {}", phaseName);
        // Reset phase-specific counters if needed
    }
    
    /**
     * Resets all progress tracking.
     */
    public void resetAll() {
        this.completedTasks.set(0);
        this.startTime.set(System.currentTimeMillis());
        this.lastUpdateTime.set(System.currentTimeMillis());
        this.currentTask = "";
        this.currentAlgorithm = "";
        this.currentScenario = "";
        this.currentReplication = 0;
        logger.info(PROGRESS_MARKER, "All progress tracking reset");
    }
    
    /**
     * Gets the overall progress as a percentage.
     * 
     * @return Progress percentage (0.0 to 100.0)
     */
    public double getOverallProgress() {
        int completed = completedTasks.get();
        int total = totalTasks.get();
        return total > 0 ? (double) completed / total * 100 : 0.0;
    }
    
    /**
     * Creates a progress bar for a specific phase.
     * 
     * @param phaseName Name of the phase
     * @param barLength Length of the progress bar
     * @return Progress bar string
     */
    public String createProgressBar(String phaseName, int barLength) {
        double percentage = getOverallProgress();
        int filledLength = (int) (barLength * percentage / 100);
        
        StringBuilder bar = new StringBuilder();
        bar.append(phaseName).append(": [");
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("] ").append(String.format("%.1f%%", percentage));
        return bar.toString();
    }
}