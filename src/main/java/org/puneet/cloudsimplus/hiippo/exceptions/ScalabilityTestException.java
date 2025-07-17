package org.puneet.cloudsimplus.hiippo.exceptions;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exception class for scalability test failures in the CloudSim HO Research Framework.
 * This exception handles errors related to performance degradation, resource exhaustion,
 * time complexity violations, and scalability limit detection during large-scale experiments.
 * 
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class ScalabilityTestException extends Exception implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final Logger LOGGER = Logger.getLogger(ScalabilityTestException.class.getName());
    
    /**
     * Types of scalability test failures
     */
    public enum ScalabilityErrorType {
        MEMORY_EXHAUSTION("SCALE001", "Memory exhausted during scalability test"),
        TIME_LIMIT_EXCEEDED("SCALE002", "Execution time exceeded acceptable limits"),
        PERFORMANCE_DEGRADATION("SCALE003", "Unacceptable performance degradation detected"),
        RESOURCE_LIMIT_REACHED("SCALE004", "System resource limit reached"),
        COMPLEXITY_VIOLATION("SCALE005", "Time complexity exceeds theoretical bounds"),
        QUALITY_DEGRADATION("SCALE006", "Solution quality degraded below threshold"),
        CONVERGENCE_DEGRADATION("SCALE007", "Convergence rate degraded significantly"),
        THROUGHPUT_COLLAPSE("SCALE008", "System throughput collapsed"),
        ALLOCATION_TIMEOUT("SCALE009", "VM allocation timed out"),
        HEAP_SPACE_ERROR("SCALE010", "Heap space exhausted"),
        GC_OVERHEAD_LIMIT("SCALE011", "Garbage collection overhead limit exceeded"),
        THREAD_EXHAUSTION("SCALE012", "Thread pool exhausted"),
        IO_BOTTLENECK("SCALE013", "I/O bottleneck detected"),
        NETWORK_SATURATION("SCALE014", "Network capacity saturated"),
        CPU_SATURATION("SCALE015", "CPU utilization at maximum"),
        INVALID_SCALE_FACTOR("SCALE016", "Invalid scale factor specified"),
        BENCHMARK_FAILURE("SCALE017", "Benchmark execution failed"),
        METRIC_COLLECTION_FAILURE("SCALE018", "Failed to collect scalability metrics"),
        SCENARIO_TOO_LARGE("SCALE019", "Scenario exceeds system capabilities"),
        INITIALIZATION_TIMEOUT("SCALE020", "Initialization timed out for large scenario");
        
        private final String code;
        private final String description;
        
        ScalabilityErrorType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Scalability metrics at the point of failure
     */
    public static class ScalabilityMetrics implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private final int vmCount;
        private final int hostCount;
        private final long executionTimeMs;
        private final long memoryUsedBytes;
        private final double cpuUtilization;
        private final double solutionQuality;
        private final int convergenceIterations;
        private final Map<String, Double> additionalMetrics;
        
        public ScalabilityMetrics(int vmCount, int hostCount, long executionTimeMs,
                                long memoryUsedBytes, double cpuUtilization,
                                double solutionQuality, int convergenceIterations) {
            this.vmCount = vmCount;
            this.hostCount = hostCount;
            this.executionTimeMs = executionTimeMs;
            this.memoryUsedBytes = memoryUsedBytes;
            this.cpuUtilization = cpuUtilization;
            this.solutionQuality = solutionQuality;
            this.convergenceIterations = convergenceIterations;
            this.additionalMetrics = new HashMap<>();
        }
        
        public void addMetric(String name, double value) {
            additionalMetrics.put(name, value);
        }
        
        public int getVmCount() {
            return vmCount;
        }
        
        public int getHostCount() {
            return hostCount;
        }
        
        public long getExecutionTimeMs() {
            return executionTimeMs;
        }
        
        public long getMemoryUsedBytes() {
            return memoryUsedBytes;
        }
        
        public double getCpuUtilization() {
            return cpuUtilization;
        }
        
        public double getSolutionQuality() {
            return solutionQuality;
        }
        
        public int getConvergenceIterations() {
            return convergenceIterations;
        }
        
        public Map<String, Double> getAdditionalMetrics() {
            return Collections.unmodifiableMap(additionalMetrics);
        }
        
        @Override
        public String toString() {
            return String.format(
                    "VMs=%d, Hosts=%d, Time=%dms, Memory=%dMB, CPU=%.1f%%, Quality=%.3f",
                    vmCount, hostCount, executionTimeMs, 
                    memoryUsedBytes / (1024 * 1024), cpuUtilization * 100, solutionQuality
            );
        }
    }
    
    /**
     * Performance thresholds that were violated
     */
    public static class PerformanceThreshold implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private final String metricName;
        private final double threshold;
        private final double actualValue;
        private final ThresholdType type;
        
        public enum ThresholdType {
            MAXIMUM, MINIMUM, EXACT
        }
        
        public PerformanceThreshold(String metricName, double threshold, 
                                  double actualValue, ThresholdType type) {
            this.metricName = metricName;
            this.threshold = threshold;
            this.actualValue = actualValue;
            this.type = type;
        }
        
        public boolean isViolated() {
            return switch (type) {
                case MAXIMUM -> actualValue > threshold;
                case MINIMUM -> actualValue < threshold;
                case EXACT -> Math.abs(actualValue - threshold) > 0.0001;
            };
        }
        
        public double getViolationPercentage() {
            return Math.abs((actualValue - threshold) / threshold) * 100;
        }
        
        @Override
        public String toString() {
            return String.format("%s: expected %s %.3f, got %.3f (%.1f%% violation)",
                    metricName, type, threshold, actualValue, getViolationPercentage());
        }
    }
    
    private final ScalabilityErrorType errorType;
    private final LocalDateTime timestamp;
    private final String scenarioName;
    private final ScalabilityMetrics metrics;
    private final List<PerformanceThreshold> violatedThresholds;
    private final Map<String, Object> scalabilityContext;
    private final Duration elapsedTime;
    
    /**
     * Constructs a new ScalabilityTestException with error type and message.
     * 
     * @param errorType The type of scalability error
     * @param message The detailed error message
     * @throws NullPointerException if errorType is null
     */
    public ScalabilityTestException(ScalabilityErrorType errorType, String message) {
        this(errorType, message, null, null, null, new ArrayList<>(), 
             null, new HashMap<>());
    }
    
    /**
     * Constructs a new ScalabilityTestException with error type, message, and cause.
     * 
     * @param errorType The type of scalability error
     * @param message The detailed error message
     * @param cause The underlying cause
     * @throws NullPointerException if errorType is null
     */
    public ScalabilityTestException(ScalabilityErrorType errorType, 
                                  String message, Throwable cause) {
        this(errorType, message, null, null, cause, new ArrayList<>(), 
             null, new HashMap<>());
    }
    
    /**
     * Full constructor with all parameters.
     * 
     * @param errorType The type of scalability error
     * @param message The detailed error message
     * @param scenarioName Name of the test scenario
     * @param metrics Scalability metrics at failure point
     * @param cause The underlying cause
     * @param violatedThresholds List of violated performance thresholds
     * @param elapsedTime Time elapsed before failure
     * @param scalabilityContext Additional context information
     * @throws NullPointerException if errorType is null
     */
    public ScalabilityTestException(ScalabilityErrorType errorType, String message,
                                  String scenarioName, ScalabilityMetrics metrics,
                                  Throwable cause, List<PerformanceThreshold> violatedThresholds,
                                  Duration elapsedTime, Map<String, Object> scalabilityContext) {
        super(formatMessage(errorType, message, metrics), cause);
        
        Objects.requireNonNull(errorType, "Error type cannot be null");
        
        this.errorType = errorType;
        this.timestamp = LocalDateTime.now();
        this.scenarioName = scenarioName;
        this.metrics = metrics;
        this.violatedThresholds = violatedThresholds != null ? 
                new ArrayList<>(violatedThresholds) : new ArrayList<>();
        this.elapsedTime = elapsedTime;
        this.scalabilityContext = scalabilityContext != null ? 
                new HashMap<>(scalabilityContext) : new HashMap<>();
        
        logException();
    }
    
    /**
     * Creates an exception for memory exhaustion.
     * 
     * @param scenarioName The scenario that exhausted memory
     * @param vmCount Number of VMs
     * @param hostCount Number of hosts
     * @param usedMemory Memory used in bytes
     * @param maxMemory Maximum available memory
     * @return A new ScalabilityTestException
     */
    public static ScalabilityTestException memoryExhaustion(String scenarioName,
                                                           int vmCount, int hostCount,
                                                           long usedMemory, long maxMemory) {
        String message = String.format(
                "Memory exhausted: %d MB used of %d MB available",
                usedMemory / (1024 * 1024), maxMemory / (1024 * 1024)
        );
        
        ScalabilityMetrics metrics = new ScalabilityMetrics(
                vmCount, hostCount, 0, usedMemory, 0, 0, 0
        );
        
        return new ScalabilityTestException(
                ScalabilityErrorType.MEMORY_EXHAUSTION, message,
                scenarioName, metrics, null, new ArrayList<>(), null, new HashMap<>()
        );
    }
    
    /**
     * Creates an exception for time limit exceeded.
     * 
     * @param scenarioName The scenario name
     * @param timeLimit The time limit in milliseconds
     * @param actualTime The actual execution time
     * @param metrics The scalability metrics
     * @return A new ScalabilityTestException
     */
    public static ScalabilityTestException timeLimitExceeded(String scenarioName,
                                                            long timeLimit, long actualTime,
                                                            ScalabilityMetrics metrics) {
        String message = String.format(
                "Execution time %d ms exceeded limit of %d ms (%.1fx slower)",
                actualTime, timeLimit, (double) actualTime / timeLimit
        );
        
        PerformanceThreshold threshold = new PerformanceThreshold(
                "ExecutionTime", timeLimit, actualTime, 
                PerformanceThreshold.ThresholdType.MAXIMUM
        );
        
        return new ScalabilityTestException(
                ScalabilityErrorType.TIME_LIMIT_EXCEEDED, message,
                scenarioName, metrics, null, List.of(threshold),
                Duration.ofMillis(actualTime), new HashMap<>()
        );
    }
    
    /**
     * Creates an exception for performance degradation.
     * 
     * @param scenarioName The scenario name
     * @param baselinePerformance The baseline performance metric
     * @param currentPerformance The current performance metric
     * @param degradationThreshold The acceptable degradation threshold
     * @param metrics The scalability metrics
     * @return A new ScalabilityTestException
     */
    public static ScalabilityTestException performanceDegradation(String scenarioName,
                                                                 double baselinePerformance,
                                                                 double currentPerformance,
                                                                 double degradationThreshold,
                                                                 ScalabilityMetrics metrics) {
        double degradation = (baselinePerformance - currentPerformance) / baselinePerformance;
        
        String message = String.format(
                "Performance degraded by %.1f%% (threshold: %.1f%%)",
                degradation * 100, degradationThreshold * 100
        );
        
        PerformanceThreshold threshold = new PerformanceThreshold(
                "PerformanceDegradation", degradationThreshold, degradation,
                PerformanceThreshold.ThresholdType.MAXIMUM
        );
        
        ScalabilityTestException ex = new ScalabilityTestException(
                ScalabilityErrorType.PERFORMANCE_DEGRADATION, message,
                scenarioName, metrics, null, List.of(threshold), null, new HashMap<>()
        );
        
        ex.addContext("baselinePerformance", baselinePerformance);
        ex.addContext("currentPerformance", currentPerformance);
        
        return ex;
    }
    
    /**
     * Creates an exception for scenario too large.
     * 
     * @param vmCount Number of VMs requested
     * @param hostCount Number of hosts requested
     * @param maxVms Maximum supported VMs
     * @param maxHosts Maximum supported hosts
     * @param availableMemory Available memory in bytes
     * @return A new ScalabilityTestException
     */
    public static ScalabilityTestException scenarioTooLarge(int vmCount, int hostCount,
                                                           int maxVms, int maxHosts,
                                                           long availableMemory) {
        String message = String.format(
                "Scenario (VMs=%d, Hosts=%d) exceeds system capabilities (max VMs=%d, max Hosts=%d)",
                vmCount, hostCount, maxVms, maxHosts
        );
        
        ScalabilityTestException ex = new ScalabilityTestException(
                ScalabilityErrorType.SCENARIO_TOO_LARGE, message
        );
        
        ex.addContext("requestedVms", vmCount);
        ex.addContext("requestedHosts", hostCount);
        ex.addContext("maxVms", maxVms);
        ex.addContext("maxHosts", maxHosts);
        ex.addContext("availableMemoryMB", availableMemory / (1024 * 1024));
        
        return ex;
    }
    
    /**
     * Creates an exception for complexity violation.
     * 
     * @param scenarioName The scenario name
     * @param expectedComplexity The expected time complexity
     * @param observedGrowthRate The observed growth rate
     * @param metrics The scalability metrics
     * @return A new ScalabilityTestException
     */
    public static ScalabilityTestException complexityViolation(String scenarioName,
                                                             String expectedComplexity,
                                                             double observedGrowthRate,
                                                             ScalabilityMetrics metrics) {
        String message = String.format(
                "Time complexity violation: expected %s, observed growth rate %.2f",
                expectedComplexity, observedGrowthRate
        );
        
        ScalabilityTestException ex = new ScalabilityTestException(
                ScalabilityErrorType.COMPLEXITY_VIOLATION, message,
                scenarioName, metrics, null, new ArrayList<>(), null, new HashMap<>()
        );
        
        ex.addContext("expectedComplexity", expectedComplexity);
        ex.addContext("observedGrowthRate", observedGrowthRate);
        
        return ex;
    }
    
    /**
     * Formats the exception message.
     * 
     * @param errorType The error type
     * @param message The base message
     * @param metrics Optional metrics
     * @return Formatted message
     */
    private static String formatMessage(ScalabilityErrorType errorType, String message,
                                      ScalabilityMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType.getCode()).append("] ");
        sb.append(errorType.getDescription());
        
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        
        if (metrics != null) {
            sb.append(" | Metrics: ").append(metrics);
        }
        
        return sb.toString();
    }
    
    /**
     * Logs the exception details.
     */
    private void logException() {
        if (LOGGER.isLoggable(Level.SEVERE)) {
            LOGGER.log(Level.SEVERE, String.format(
                    "ScalabilityTestException created: [%s] %s at %s",
                    errorType.getCode(),
                    getMessage(),
                    timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ), this);
            
            if (!violatedThresholds.isEmpty() && LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("Violated thresholds:");
                for (PerformanceThreshold threshold : violatedThresholds) {
                    LOGGER.warning("  - " + threshold);
                }
            }
        }
    }
    
    /**
     * Adds a violated performance threshold.
     * 
     * @param threshold The violated threshold
     */
    public void addViolatedThreshold(PerformanceThreshold threshold) {
        if (threshold != null) {
            violatedThresholds.add(threshold);
        }
    }
    
    /**
     * Adds scalability context information.
     * 
     * @param key The context key
     * @param value The context value
     */
    public void addContext(String key, Object value) {
        if (key != null) {
            scalabilityContext.put(key, value);
        }
    }
    
    /**
     * Gets the error type.
     * 
     * @return The scalability error type
     */
    public ScalabilityErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * Gets the timestamp.
     * 
     * @return The exception timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the scenario name.
     * 
     * @return The scenario name, may be null
     */
    public String getScenarioName() {
        return scenarioName;
    }
    
    /**
     * Gets the scalability metrics.
     * 
     * @return The metrics at failure point, may be null
     */
    public ScalabilityMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Gets the violated thresholds.
     * 
     * @return An unmodifiable list of violated thresholds
     */
    public List<PerformanceThreshold> getViolatedThresholds() {
        return Collections.unmodifiableList(violatedThresholds);
    }
    
    /**
     * Gets the elapsed time.
     * 
     * @return The elapsed time before failure, may be null
     */
    public Duration getElapsedTime() {
        return elapsedTime;
    }
    
    /**
     * Gets the scalability context.
     * 
     * @return An unmodifiable map of context information
     */
    public Map<String, Object> getScalabilityContext() {
        return Collections.unmodifiableMap(scalabilityContext);
    }
    
    /**
     * Gets a detailed message for logging.
     * 
     * @return A detailed string representation
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("ScalabilityTestException Details:\n");
        sb.append("  Error Type: ").append(errorType.getCode()).append(" - ")
          .append(errorType.getDescription()).append("\n");
        sb.append("  Message: ").append(getMessage()).append("\n");
        sb.append("  Timestamp: ").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        
        if (scenarioName != null) {
            sb.append("  Scenario: ").append(scenarioName).append("\n");
        }
        
        if (metrics != null) {
            sb.append("  Metrics: ").append(metrics).append("\n");
        }
        
        if (elapsedTime != null) {
            sb.append("  Elapsed Time: ").append(elapsedTime.toMillis()).append(" ms\n");
        }
        
        if (!violatedThresholds.isEmpty()) {
            sb.append("  Violated Thresholds:\n");
            for (PerformanceThreshold threshold : violatedThresholds) {
                sb.append("    - ").append(threshold).append("\n");
            }
        }
        
        if (!scalabilityContext.isEmpty()) {
            sb.append("  Context:\n");
            scalabilityContext.forEach((key, value) -> 
                    sb.append("    ").append(key).append(": ").append(value).append("\n"));
        }
        
        if (getCause() != null) {
            sb.append("  Cause: ").append(getCause().getClass().getName())
              .append(" - ").append(getCause().getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Checks if this is a critical scalability failure.
     * 
     * @return true if critical, false otherwise
     */
    public boolean isCritical() {
        return switch (errorType) {
            case MEMORY_EXHAUSTION, HEAP_SPACE_ERROR, 
                 GC_OVERHEAD_LIMIT, THREAD_EXHAUSTION,
                 SCENARIO_TOO_LARGE -> true;
            default -> false;
        };
    }
    
    /**
     * Checks if the test can be retried with reduced scale.
     * 
     * @return true if retriable, false otherwise
     */
    public boolean isRetriableWithReducedScale() {
        return switch (errorType) {
            case MEMORY_EXHAUSTION, TIME_LIMIT_EXCEEDED,
                 PERFORMANCE_DEGRADATION, SCENARIO_TOO_LARGE -> true;
            default -> false;
        };
    }
    
    /**
     * Suggests a reduced scale for retry.
     * 
     * @return Suggested scale reduction factor (0.5 = 50% reduction)
     */
    public double suggestedScaleReduction() {
        return switch (errorType) {
            case MEMORY_EXHAUSTION, HEAP_SPACE_ERROR -> 0.5;
            case TIME_LIMIT_EXCEEDED -> 0.7;
            case PERFORMANCE_DEGRADATION -> 0.8;
            default -> 0.9;
        };
    }
    
    @Override
    public String toString() {
        return String.format("ScalabilityTestException[type=%s, scenario=%s, timestamp=%s]: %s",
                errorType.getCode(),
                scenarioName != null ? scenarioName : "unknown",
                timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                getMessage());
    }
}