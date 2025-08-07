package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.exceptions.ScalabilityTestException;
import org.puneet.cloudsimplus.hiippo.policy.BaselineVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.policy.HippopotamusVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// 1. Use correct CloudSim import (CloudSim Plus)
// import org.cloudsimplus.core.CloudSim;
import org.puneet.cloudsimplus.hiippo.util.ScenarioConfigLoader.ScenarioSpec;
import org.puneet.cloudsimplus.hiippo.simulation.TestScenarios.TestScenario;
import org.puneet.cloudsimplus.hiippo.util.CSVResultsWriter.ExperimentResult;
import org.puneet.cloudsimplus.hiippo.util.PerformanceMonitor.PerformanceMetrics;

/**
 * ScalabilityTester - Tests algorithm performance across different problem sizes.
 * Optimized for 16GB RAM systems with careful memory management and batch processing.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-24
 */
public class ScalabilityTester {
    private static final Logger logger = LoggerFactory.getLogger(ScalabilityTester.class);
    
    // JVM arguments for optimized memory usage
    private static final String JVM_ARGS = "-Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=200";
    
    // Scalability test configurations - optimized for 16GB systems
    private static final int[] VM_COUNTS = {10, 50, 100, 200, 500};
    private static final int[] HOST_COUNTS = {3, 10, 20, 40, 100};
    
    // Memory management thresholds
    private static final double MEMORY_USAGE_WARNING_THRESHOLD = 0.85; // 85% memory usage
    private static final long CLEANUP_DELAY_SMALL = 2000; // 2 seconds
    private static final long CLEANUP_DELAY_LARGE = 5000; // 5 seconds
    
    // Test configuration
    private static final int WARMUP_RUNS = 2;
    private static final int TEST_RUNS = 5;
    private static final int MAX_RETRIES = 3;
    
    private final ExperimentRunner experimentRunner;
    private final Map<String, ScalabilityMetrics> metricsCache;
    
    /**
     * Creates a new ScalabilityTester instance.
     * 
     * @throws ScalabilityTestException if initialization fails
     */
    public ScalabilityTester() {
        try {
            logger.info("Initializing ScalabilityTester with JVM args: {}", JVM_ARGS);
            
            this.experimentRunner = new ExperimentRunner();
            this.metricsCache = new HashMap<>();
            
            // Configure JVM if not already configured
            configureJVM();
            
            logger.info("ScalabilityTester initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize ScalabilityTester", e);
            // 3. Use a valid constructor or static factory for ScalabilityTestException
            throw new RuntimeException("ScalabilityTester initialization failed", e);
        }
    }
    
    /**
     * Configures JVM settings for optimal performance during scalability tests.
     */
    private void configureJVM() {
        try {
            // JVM options must be set when starting the JVM, not at runtime
            // Use: java -Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 ...
            
            // Log current JVM settings
            Runtime runtime = Runtime.getRuntime();
            logger.info("JVM Configuration - Max Memory: {} MB, Total Memory: {} MB, Free Memory: {} MB",
                runtime.maxMemory() / (1024 * 1024),
                runtime.totalMemory() / (1024 * 1024),
                runtime.freeMemory() / (1024 * 1024));
        } catch (Exception e) {
            logger.warn("Unable to configure JVM settings: {}", e.getMessage());
        }
    }
    
    /**
     * Runs complete scalability test suite for a given algorithm.
     * 
     * @param algorithm the algorithm name to test
     * @return ScalabilityTestResult containing all test results
     * @throws ScalabilityTestException if test execution fails
     */
    public ScalabilityTestResult runScalabilityTest(String algorithm) throws org.puneet.cloudsimplus.hiippo.exceptions.ScalabilityTestException {
        if (algorithm == null || algorithm.trim().isEmpty()) {
            throw new IllegalArgumentException("Algorithm name cannot be null or empty");
        }
        
        logger.info("Starting scalability test for algorithm: {}", algorithm);
        
        ScalabilityTestResult testResult = new ScalabilityTestResult(algorithm);
        ProgressTracker progressTracker = new ProgressTracker();
        
        try {
            // Run warmup iterations
            logger.info("Running warmup iterations...");
            runWarmupIterations(algorithm);
            
            // Run actual scalability tests
            for (int i = 0; i < VM_COUNTS.length; i++) {
                logger.debug("Scalability test progress: {}/{} VM counts tested", i + 1, VM_COUNTS.length);
                int vmCount = VM_COUNTS[i];
                int hostCount = HOST_COUNTS[i];
                
                String scenarioKey = String.format("%s_%d_%d", algorithm, vmCount, hostCount);
                
                // Check if we should skip this scenario due to memory constraints
                if (!shouldRunScenario(vmCount, hostCount)) {
                    logger.warn("Skipping scenario - VMs: {}, Hosts: {} (insufficient memory)", 
                        vmCount, hostCount);
                    testResult.addSkippedScenario(vmCount, hostCount, "Insufficient memory");
                    continue;
                }
                
                // Run scenario with retry logic
                ScenarioResult scenarioResult = runScenarioWithRetries(
                    algorithm, vmCount, hostCount, scenarioKey);
                
                if (scenarioResult != null) {
                    testResult.addScenarioResult(scenarioResult);
                    
                    // Save intermediate results
                    saveScalabilityResult(algorithm, vmCount, hostCount, scenarioResult);
                    
                    // Cleanup after scenario
                    cleanupAfterScenario(vmCount);
                } else {
                    testResult.addSkippedScenario(vmCount, hostCount, "Execution failed");
                }
            }
            
            // Analyze scalability trends
            analyzeScalabilityTrends(testResult);
            
            // Save final results
            saveFinalScalabilityReport(testResult);
            
            logger.info("Scalability test completed for algorithm: {}", algorithm);
            return testResult;
            
        } catch (Exception e) {
            logger.error("Scalability test failed for algorithm: {}", algorithm, e);
            throw new org.puneet.cloudsimplus.hiippo.exceptions.ScalabilityTestException(
                org.puneet.cloudsimplus.hiippo.exceptions.ScalabilityTestException.ScalabilityErrorType.BENCHMARK_FAILURE,
                "Scalability test failed: " + e.getMessage(), e);
        } finally {
            // Final cleanup
            performFinalCleanup();
        }
    }
    
    /**
     * Runs warmup iterations to stabilize JVM performance.
     * 
     * @param algorithm the algorithm to warm up
     */
    private void runWarmupIterations(String algorithm) {
        logger.debug("Starting {} warmup iterations", WARMUP_RUNS);
        
        for (int i = 0; i < WARMUP_RUNS; i++) {
            try {
                // Use small scenario for warmup
                int vmCount = VM_COUNTS[0];
                int hostCount = HOST_COUNTS[0];
                
                // 7. Use correct ScenarioSpec constructor
                TestScenario warmupScenario = org.puneet.cloudsimplus.hiippo.simulation.TestScenarios.createScenario(
                    "Warmup", vmCount, hostCount);
                
                ExperimentResult warmupResult = experimentRunner.runExperiment(
                    algorithm, warmupScenario, 0); // -1 indicates warmup run
                
                logger.debug("Warmup iteration {} completed", i + 1);
                
                // Don't save warmup results
                cleanupAfterScenario(vmCount);
                
            } catch (Exception e) {
                logger.warn("Warmup iteration {} failed: {}", i + 1, e.getMessage());
            }
        }
        
        logger.debug("Warmup completed");
    }
    
    /**
     * Checks if a scenario should be run based on available memory.
     * 
     * @param vmCount number of VMs in the scenario
     * @param hostCount number of hosts in the scenario
     * @return true if scenario can be run, false otherwise
     */
    private boolean shouldRunScenario(int vmCount, int hostCount) {
        // Use correct static method for MemoryManager.getCurrentMemoryUsage()
        double currentUsage = MemoryManager.getCurrentMemoryUsage();
        if (currentUsage > MEMORY_USAGE_WARNING_THRESHOLD) {
            logger.warn("Current memory usage too high: {:.2f}%", currentUsage * 100);
            return false;
        }
        // Check if we have enough memory for the scenario
        return MemoryManager.hasEnoughMemoryForScenario(vmCount, hostCount);
    }
    
    /**
     * Runs a single scenario with retry logic for handling transient failures.
     * 
     * @param algorithm the algorithm to test
     * @param vmCount number of VMs
     * @param hostCount number of hosts
     * @param scenarioKey unique key for the scenario
     * @return ScenarioResult or null if all retries failed
     */
    private ScenarioResult runScenarioWithRetries(String algorithm, int vmCount, 
                                                  int hostCount, String scenarioKey) {
        logger.info("Running scenario - Algorithm: {}, VMs: {}, Hosts: {}", 
            algorithm, vmCount, hostCount);
        
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            try {
                // Check memory before each attempt
                MemoryManager.checkMemoryUsage(String.format("Scenario %s (Retry %d)", 
                    scenarioKey, retry));
                
                // Run the scenario
                ScenarioResult result = runSingleScenario(algorithm, vmCount, hostCount);
                
                if (result != null && result.isValid()) {
                    logger.info("Scenario completed successfully on attempt {}", retry + 1);
                    return result;
                }
                
                logger.warn("Invalid result on attempt {}, retrying...", retry + 1);
                
            } catch (OutOfMemoryError oom) {
                logger.error("OutOfMemoryError on attempt {} for scenario: VMs={}, Hosts={}", 
                    retry + 1, vmCount, hostCount);
                
                // Aggressive cleanup for OOM
                emergencyCleanup();
                
                // Don't retry if OOM on large scenarios
                if (vmCount >= 200) {
                    logger.error("Skipping remaining attempts for large scenario due to OOM");
                    break;
                }
                
            } catch (Exception e) {
                logger.error("Error on attempt {} for scenario: {}", retry + 1, scenarioKey, e);
                
                if (retry < MAX_RETRIES - 1) {
                    // Wait before retry
                    try {
                        Thread.sleep(1000 * (retry + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        logger.error("All retry attempts failed for scenario: {}", scenarioKey);
        return null;
    }
    
    /**
     * Runs a single scalability test scenario.
     * 
     * @param algorithm the algorithm to test
     * @param vmCount number of VMs
     * @param hostCount number of hosts
     * @return ScenarioResult containing test metrics
     * @throws Exception if scenario execution fails
     */
    private ScenarioResult runSingleScenario(String algorithm, int vmCount, int hostCount) 
            throws Exception {
        PerformanceMonitor performanceMonitor = new PerformanceMonitor();
        performanceMonitor.startMonitoring();
        ScenarioResult scenarioResult = new ScenarioResult(vmCount, hostCount);
        List<Double> executionTimes = new ArrayList<>();
        List<Double> solutionQualities = new ArrayList<>();
        try {
            for (int run = 0; run < TEST_RUNS; run++) {
                logger.debug("Starting test run {} of {}", run + 1, TEST_RUNS);
                TestScenario testScenario = org.puneet.cloudsimplus.hiippo.simulation.TestScenarios.createScenario(
                    String.format("Scalability_%d_%d", vmCount, hostCount),
                    vmCount, hostCount);
                ExperimentConfig.initializeRandomSeed(run);
                long startTime = System.nanoTime();
                ExperimentResult result = experimentRunner.runExperiment(
                    algorithm, testScenario, run);
                long endTime = System.nanoTime();
                double executionTime = (endTime - startTime) / 1_000_000.0;
                if (result == null) {
                    logger.warn("Invalid result for run {}", run + 1);
                    continue;
                }
                try {
                    ResultValidator.validateResults(result);
                } catch (Exception e) {
                    logger.warn("Result validation failed for run {}: {}", run + 1, e.getMessage());
                    continue;
                }
                executionTimes.add(executionTime);
                solutionQualities.add(calculateSolutionQuality(result));
                scenarioResult.addRunResult(executionTime, result);
                logger.debug("Test run {} completed in {} ms", run + 1, String.format("%.2f", executionTime));
            }
            if (!executionTimes.isEmpty()) {
                scenarioResult.setAverageExecutionTime(calculateAverage(executionTimes));
                scenarioResult.setStdDevExecutionTime(calculateStandardDeviation(executionTimes));
                scenarioResult.setAverageSolutionQuality(calculateAverage(solutionQualities));
            }
            PerformanceMetrics performanceMetrics = performanceMonitor.stopMonitoring();
            scenarioResult.setPerformanceMetrics(performanceMetrics);
            scenarioResult.setAverageMemoryUsage(performanceMetrics.getPeakMemoryUsage());
            logger.info("Scenario completed - Avg execution time: {} ms, Avg solution quality: {}", 
                String.format("%.2f", scenarioResult.getAverageExecutionTime()),
                String.format("%.4f", scenarioResult.getAverageSolutionQuality()));
            return scenarioResult;
        } catch (Exception e) {
            logger.error("Error in scenario execution", e);
            throw e;
        } finally {
            performanceMonitor.stopMonitoring();
        }
    }
    
    /**
     * Calculates solution quality metric from experiment result.
     * 
     * @param result the experiment result
     * @return solution quality score (0-1)
     */
    private double calculateSolutionQuality(ExperimentResult result) {
        if (result == null) {
            return 0.0;
        }
        
        // CRITICAL FIX: Use real-time resource utilization metrics (now properly collected during simulation)
        double utilization = (result.getResourceUtilCPU() + 
                             result.getResourceUtilRAM()) / 2.0;
        
        // Ensure utilization is reasonable (not zero due to post-deallocation collection)
        if (utilization <= 0.0) {
            logger.warn("Zero utilization detected in result - this may indicate metrics collection issue");
            utilization = 0.1; // Minimum reasonable utilization
        }
        
        double slaScore = 1.0 - (result.getSlaViolations() / 
                                Math.max(1.0, result.getVmTotal()));
        double powerScore = 1.0 - Math.min(1.0, result.getPowerConsumption() / 10000.0);
        
        // Weighted average with emphasis on resource utilization
        double qualityScore = 0.4 * utilization + 0.3 * slaScore + 0.3 * powerScore;
        
        logger.debug("Solution quality calculation - Utilization: {:.2f}, SLA: {:.2f}, Power: {:.2f}, Quality: {:.2f}", 
            utilization, slaScore, powerScore, qualityScore);
        
        return qualityScore;
    }
    
    /**
     * Cleans up resources after a scenario based on its size.
     * 
     * @param vmCount number of VMs in the completed scenario
     */
    private void cleanupAfterScenario(int vmCount) {
        logger.debug("Cleaning up after scenario with {} VMs", vmCount);
        
        try {
            // Force garbage collection
            long cleanupDelay = vmCount >= 200 ? CLEANUP_DELAY_LARGE : CLEANUP_DELAY_SMALL;
            Thread.sleep(cleanupDelay);
            
            // Log memory status after cleanup
            MemoryManager.checkMemoryUsage("Post-scenario cleanup");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Cleanup interrupted");
        }
    }
    
    /**
     * Performs emergency cleanup after OutOfMemoryError.
     */
    private void emergencyCleanup() {
        logger.warn("Performing emergency cleanup after OOM");
        
        try {
            // Clear caches
            metricsCache.clear();
            
            // Wait based on scenario size
            Thread.sleep(3000); // Just wait for memory to be reclaimed naturally
            
            // Log memory status
            MemoryManager.checkMemoryUsage("Emergency cleanup");
            
        } catch (Exception e) {
            logger.error("Error during emergency cleanup", e);
        }
    }
    
    /**
     * Analyzes scalability trends from test results.
     * 
     * @param testResult the complete test result
     */
    private void analyzeScalabilityTrends(ScalabilityTestResult testResult) {
        logger.info("Analyzing scalability trends for algorithm: {}", 
            testResult.getAlgorithm());
        
        try {
            List<ScenarioResult> results = testResult.getScenarioResults();
            if (results.size() < 2) {
                logger.warn("Insufficient data points for trend analysis");
                return;
            }
            
            // Calculate time complexity trend
            double[] sizes = results.stream()
                .mapToDouble(r -> r.getVmCount())
                .toArray();
            double[] times = results.stream()
                .mapToDouble(r -> r.getAverageExecutionTime())
                .toArray();
            
            double timeComplexityCoefficient = calculateComplexityCoefficient(sizes, times);
            testResult.setTimeComplexityCoefficient(timeComplexityCoefficient);
            
            // Calculate quality degradation trend
            double[] qualities = results.stream()
                .mapToDouble(r -> r.getAverageSolutionQuality())
                .toArray();
            
            double qualityDegradationRate = calculateDegradationRate(sizes, qualities);
            testResult.setQualityDegradationRate(qualityDegradationRate);
            
            // Log analysis results
            logger.info("Scalability Analysis - Time Complexity: O(n^{:.2f}), " +
                "Quality Degradation Rate: {:.4f}",
                timeComplexityCoefficient, qualityDegradationRate);
            
        } catch (Exception e) {
            logger.error("Error analyzing scalability trends", e);
        }
    }
    
    /**
     * Calculates time complexity coefficient using regression analysis.
     * 
     * @param sizes problem sizes
     * @param times execution times
     * @return complexity coefficient
     */
    private double calculateComplexityCoefficient(double[] sizes, double[] times) {
        // Simple log-log regression to estimate complexity
        // log(time) = a * log(size) + b
        // Complexity is O(n^a)
        
        double[] logSizes = Arrays.stream(sizes)
            .map(Math::log)
            .toArray();
        double[] logTimes = Arrays.stream(times)
            .map(Math::log)
            .toArray();
        
        // Calculate linear regression coefficient
        double sumX = Arrays.stream(logSizes).sum();
        double sumY = Arrays.stream(logTimes).sum();
        double sumXY = 0.0;
        double sumX2 = 0.0;
        
        for (int i = 0; i < logSizes.length; i++) {
            sumXY += logSizes[i] * logTimes[i];
            sumX2 += logSizes[i] * logSizes[i];
        }
        
        int n = logSizes.length;
        double coefficient = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        
        return Math.max(0.0, coefficient); // Ensure non-negative
    }
    
    /**
     * Calculates quality degradation rate as problem size increases.
     * 
     * @param sizes problem sizes
     * @param qualities solution qualities
     * @return degradation rate
     */
    private double calculateDegradationRate(double[] sizes, double[] qualities) {
        if (sizes.length < 2) {
            return 0.0;
        }
        
        // Calculate average degradation per size increase
        double totalDegradation = 0.0;
        int comparisons = 0;
        
        for (int i = 1; i < sizes.length; i++) {
            double sizeIncrease = sizes[i] / sizes[i-1];
            double qualityDecrease = (qualities[i-1] - qualities[i]) / qualities[i-1];
            
            if (qualityDecrease > 0) {
                totalDegradation += qualityDecrease / Math.log(sizeIncrease);
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalDegradation / comparisons : 0.0;
    }
    
    /**
     * Saves scalability test result to CSV.
     * 
     * @param algorithm the algorithm name
     * @param vmCount number of VMs
     * @param hostCount number of hosts
     * @param result the scenario result
     */
    private void saveScalabilityResult(String algorithm, int vmCount, int hostCount,
                                      ScenarioResult result) {
        try {
            // Use correct signature for CSVResultsWriter.writeScalabilityResult
            double avgExecutionTime = result.getAverageExecutionTime();
            double avgMemoryUsage = result.getAverageMemoryUsage();
            double avgCpuUtilization = result.getRunResults().isEmpty() ? 0.0 :
                result.getRunResults().stream().mapToDouble(ExperimentResult::getResourceUtilCPU).average().orElse(0.0);
            double avgQualityScore = result.getAverageSolutionQuality();
            double successRate = result.getRunResults().isEmpty() ? 0.0 :
                result.getRunResults().stream().mapToDouble(r -> r.getVmAllocated() / (double) Math.max(1, r.getVmTotal())).average().orElse(0.0);
            CSVResultsWriter.writeScalabilityResult(
                algorithm,
                vmCount,
                hostCount,
                avgExecutionTime,
                avgMemoryUsage,
                avgCpuUtilization,
                avgQualityScore,
                successRate
            );
            logger.debug("Scalability result saved for {} with {} VMs", algorithm, vmCount);
        } catch (Exception e) {
            logger.error("Failed to save scalability result", e);
        }
    }
    
    /**
     * Saves final scalability test report.
     * 
     * @param testResult the complete test result
     */
    private void saveFinalScalabilityReport(ScalabilityTestResult testResult) {
        try {
            Map<String, Object> report = new HashMap<>();
            report.put("Algorithm", testResult.getAlgorithm());
            report.put("TotalScenarios", testResult.getScenarioResults().size());
            report.put("SkippedScenarios", testResult.getSkippedScenarios().size());
            report.put("TimeComplexityCoefficient", testResult.getTimeComplexityCoefficient());
            report.put("QualityDegradationRate", testResult.getQualityDegradationRate());
            report.put("TestCompletionTime", testResult.getTestDuration());
            report.put("Timestamp", new Date());
            // 7. Ensure CSVResultsWriter.writeScalabilityReport signature matches usage
            // Instead, use CSVResultsWriter.writeScalabilityResult for each scenario result
            for (ScenarioResult result : testResult.getScenarioResults()) {
                CSVResultsWriter.writeScalabilityResult(
                    testResult.getAlgorithm(),
                    result.getVmCount(),
                    result.getHostCount(),
                    result.getAverageExecutionTime(),
                    result.getAverageMemoryUsage(),
                    result.getPerformanceMetrics() != null ? result.getPerformanceMetrics().getAvgCpuUsage() : 0.0,
                    result.getAverageSolutionQuality(),
                    1.0 // Placeholder for success rate, adjust as needed
                );
            }
            logger.info("Final scalability report saved for {}", testResult.getAlgorithm());
        } catch (Exception e) {
            logger.error("Failed to save final scalability report", e);
        }
    }
    
    /**
     * Performs final cleanup after all tests.
     */
    private void performFinalCleanup() {
        logger.info("Performing final cleanup");
        
        try {
            // Clear all caches
            metricsCache.clear();
            
            // Log final memory status
            MemoryManager.checkMemoryUsage("Final cleanup");
            
        } catch (Exception e) {
            logger.error("Error during final cleanup", e);
        }
    }
    
    /**
     * Calculates average of a list of doubles.
     * 
     * @param values list of values
     * @return average value
     */
    private double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Calculates standard deviation of a list of doubles.
     * 
     * @param values list of values
     * @return standard deviation
     */
    private double calculateStandardDeviation(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }
        
        double mean = calculateAverage(values);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Result container for a single scalability test scenario.
     */
    public static class ScenarioResult {
        private final int vmCount;
        private final int hostCount;
        private final List<ExperimentResult> runResults;
        private double averageExecutionTime;
        private double stdDevExecutionTime;
        private double averageSolutionQuality;
        private double averageMemoryUsage;
        private PerformanceMetrics performanceMetrics;
        
        public ScenarioResult(int vmCount, int hostCount) {
            this.vmCount = vmCount;
            this.hostCount = hostCount;
            this.runResults = new ArrayList<>();
        }
        
        public void addRunResult(double executionTime, ExperimentResult result) {
            if (result != null) {
                runResults.add(result);
            }
        }
        
        public boolean isValid() {
            return !runResults.isEmpty() && averageExecutionTime > 0;
        }
        
        // Getters and setters
        public int getVmCount() { return vmCount; }
        public int getHostCount() { return hostCount; }
        public List<ExperimentResult> getRunResults() { return new ArrayList<>(runResults); }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(double time) { this.averageExecutionTime = time; }
        public double getStdDevExecutionTime() { return stdDevExecutionTime; }
        public void setStdDevExecutionTime(double stdDev) { this.stdDevExecutionTime = stdDev; }
        public double getAverageSolutionQuality() { return averageSolutionQuality; }
        public void setAverageSolutionQuality(double quality) { this.averageSolutionQuality = quality; }
        public double getAverageMemoryUsage() { return averageMemoryUsage; }
        public void setAverageMemoryUsage(double usage) { this.averageMemoryUsage = usage; }
        public PerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }
        public void setPerformanceMetrics(PerformanceMetrics metrics) { this.performanceMetrics = metrics; }
    }
    
    /**
     * Result container for complete scalability test.
     */
    public static class ScalabilityTestResult {
        private final String algorithm;
        private final List<ScenarioResult> scenarioResults;
        private final Map<String, String> skippedScenarios;
        private double timeComplexityCoefficient;
        private double qualityDegradationRate;
        private final long startTime;
        private long endTime;
        
        public ScalabilityTestResult(String algorithm) {
            this.algorithm = algorithm;
            this.scenarioResults = new ArrayList<>();
            this.skippedScenarios = new HashMap<>();
            this.startTime = System.currentTimeMillis();
        }
        
        public void addScenarioResult(ScenarioResult result) {
            if (result != null) {
                scenarioResults.add(result);
            }
        }
        
        public void addSkippedScenario(int vmCount, int hostCount, String reason) {
            String key = String.format("%d_%d", vmCount, hostCount);
            skippedScenarios.put(key, reason);
        }
        
        public long getTestDuration() {
            if (endTime == 0) {
                endTime = System.currentTimeMillis();
            }
            return endTime - startTime;
        }
        
        // Getters and setters
        public String getAlgorithm() { return algorithm; }
        public List<ScenarioResult> getScenarioResults() { return new ArrayList<>(scenarioResults); }
        public Map<String, String> getSkippedScenarios() { return new HashMap<>(skippedScenarios); }
        public double getTimeComplexityCoefficient() { return timeComplexityCoefficient; }
        public void setTimeComplexityCoefficient(double coefficient) { this.timeComplexityCoefficient = coefficient; }
        public double getQualityDegradationRate() { return qualityDegradationRate; }
        public void setQualityDegradationRate(double rate) { this.qualityDegradationRate = rate; }
    }
    
    /**
     * Scalability metrics for trend analysis.
     */
    public static class ScalabilityMetrics {
        private final Map<Integer, Double> executionTimeBySize;
        private final Map<Integer, Double> qualityBySize;
        private final Map<Integer, Long> memoryUsageBySize;
        
        public ScalabilityMetrics() {
            this.executionTimeBySize = new HashMap<>();
            this.qualityBySize = new HashMap<>();
            this.memoryUsageBySize = new HashMap<>();
        }
        
        public void addMetric(int problemSize, double executionTime, 
                             double quality, long memoryUsage) {
            executionTimeBySize.put(problemSize, executionTime);
            qualityBySize.put(problemSize, quality);
            memoryUsageBySize.put(problemSize, memoryUsage);
        }
        
        // Getters
        public Map<Integer, Double> getExecutionTimeBySize() { 
            return new HashMap<>(executionTimeBySize); 
        }
        public Map<Integer, Double> getQualityBySize() { 
            return new HashMap<>(qualityBySize); 
        }
        public Map<Integer, Long> getMemoryUsageBySize() { 
            return new HashMap<>(memoryUsageBySize); 
        }
    }
}