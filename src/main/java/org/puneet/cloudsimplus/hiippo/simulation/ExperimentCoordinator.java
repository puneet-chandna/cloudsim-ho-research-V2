package org.puneet.cloudsimplus.hiippo.simulation;

import org.puneet.cloudsimplus.hiippo.exceptions.*;
import org.puneet.cloudsimplus.hiippo.statistical.*;
import org.puneet.cloudsimplus.hiippo.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.puneet.cloudsimplus.hiippo.util.CSVResultsWriter.ExperimentResult;
import org.puneet.cloudsimplus.hiippo.simulation.TestScenarios.TestScenario;

/**
 * Coordinates the entire experimental workflow for the Hippopotamus Optimization research.
 * Manages memory constraints for 16GB systems, runs experiments in batches,
 * performs statistical analysis, and generates comparison reports.
 *
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-22
 */
public class ExperimentCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentCoordinator.class);
    
    // Algorithm configurations - reduced set for memory efficiency
    private final List<String> algorithms = Arrays.asList(
        "HO",        // Hippopotamus Optimization
        "FirstFit",  // First Fit heuristic
        "BestFit",   // Best Fit heuristic
        "GA"         // Genetic Algorithm
    );
    
    // Scenario configurations - optimized for 16GB systems
    private final List<String> scenarios = Arrays.asList(
        "Micro",    // 10 VMs, 3 Hosts
        "Small",    // 50 VMs, 10 Hosts
        "Medium",   // 100 VMs, 20 Hosts
        "Large",    // 200 VMs, 40 Hosts
        "XLarge"    // 500 VMs, 100 Hosts
    );
    
    // Scenario specifications
    private final Map<String, ScenarioSpec> scenarioSpecs = Map.of(
        "Micro", new ScenarioSpec("Micro", 10, 3, 1),
        "Small", new ScenarioSpec("Small", 50, 10, 2),
        "Medium", new ScenarioSpec("Medium", 100, 20, 3),
        "Large", new ScenarioSpec("Large", 200, 40, 4),
        "XLarge", new ScenarioSpec("XLarge", 500, 100, 5)
    );
    
    // Configuration parameters
    private final int batchSize;
    private final boolean parallelExecution;
    private final ExecutorService executorService;
    
    // Progress tracking
    private ProgressTracker progressTracker;
    private final Map<String, ExperimentStatus> experimentStatus;
    
    // Statistical analysis components
    private StatisticalValidator statisticalValidator;
    private ComparisonAnalyzer comparisonAnalyzer;
    
    // Results storage
    private final Map<String, List<ExperimentResult>> allResults;
    private final String experimentId;
    
    /**
     * Creates a new ExperimentCoordinator with default configuration.
     */
    public ExperimentCoordinator() {
        this(5, false); // Default: batch size 5, sequential execution
    }
    
    /**
     * Creates a new ExperimentCoordinator with specified configuration.
     *
     * @param batchSize Size of replication batches for memory management
     * @param parallelExecution Enable parallel experiment execution
     */
    public ExperimentCoordinator(int batchSize, boolean parallelExecution) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        
        this.batchSize = batchSize;
        this.parallelExecution = parallelExecution;
        this.experimentStatus = new ConcurrentHashMap<>();
        this.allResults = new ConcurrentHashMap<>();
        this.experimentId = generateExperimentId();
        
        // Initialize executor service for parallel execution
        if (parallelExecution) {
            int cores = Runtime.getRuntime().availableProcessors();
            this.executorService = Executors.newFixedThreadPool(Math.max(2, cores / 2));
            logger.info("Initialized parallel execution with {} threads", Math.max(2, cores / 2));
        } else {
            this.executorService = null;
            logger.info("Using sequential execution mode");
        }
        
        // Initialize progress tracker
        this.progressTracker = new ProgressTracker();
        
        // Initialize statistical components
        this.statisticalValidator = new StatisticalValidator();
        this.comparisonAnalyzer = new ComparisonAnalyzer();
        
        logger.info("ExperimentCoordinator initialized - ID: {}, Batch Size: {}, Parallel: {}",
            experimentId, batchSize, parallelExecution);
    }
    
    /**
     * Configures the experimental setup and validates system requirements.
     *
     * @throws ValidationException if system requirements are not met
     */
    public void configureExperiments() throws ValidationException {
        logger.info("Configuring experimental setup...");
        
        // Check system requirements
        checkSystemRequirements();
        
        // Initialize experiment status tracking
        for (String algorithm : algorithms) {
            for (String scenario : scenarios) {
                String key = algorithm + "_" + scenario;
                experimentStatus.put(key, new ExperimentStatus(algorithm, scenario));
            }
        }
        
        // Log experimental configuration
        logExperimentalConfiguration();
        
        logger.info("Experimental configuration completed successfully");
    }
    
    /**
     * Runs the complete experimental suite with all algorithms and scenarios.
     *
     * @throws HippopotamusOptimizationException if a critical error occurs
     */
    public void runCompleteExperiment() throws HippopotamusOptimizationException {
        logger.info("Starting complete experimental suite - Experiment ID: {}", experimentId);
        long startTime = System.currentTimeMillis();
        
        try {
            // Calculate total experiments
            int totalExperiments = algorithms.size() * scenarios.size() * ExperimentConfig.REPLICATION_COUNT;
            progressTracker.initializeTask("Complete Experiment", totalExperiments);
            int completed = 0;
            
            // Process scenarios in order of size to avoid memory issues
            for (String scenario : scenarios) {
                // Check memory availability
                ScenarioSpec spec = scenarioSpecs.get(scenario);
                if (!checkMemoryForScenario(spec)) {
                    logger.warn("Skipping scenario {} due to memory constraints", scenario);
                    updateSkippedExperiments(scenario, completed, totalExperiments);
                    completed += algorithms.size() * ExperimentConfig.REPLICATION_COUNT;
                    continue;
                }
                
                // Run all algorithms for this scenario
                try {
                    runScenarioBatch(scenario, progressTracker, completed, totalExperiments);
                    completed += algorithms.size() * ExperimentConfig.REPLICATION_COUNT;
                } catch (Exception e) {
                    logger.error("Error in scenario {}: {}", scenario, e.getMessage(), e);
                    handleScenarioError(scenario, e);
                }
                
                // Clean up between scenarios
                cleanupBetweenScenarios();
            }
            
            // Perform statistical analysis
            performStatisticalAnalysis();
            
            // Generate comparison reports
            generateComparisonReports();
            
            // Log completion summary
            long duration = System.currentTimeMillis() - startTime;
            logCompletionSummary(duration);
            
        } catch (Exception e) {
            logger.error("Critical error in experiment execution", e);
            throw new HippopotamusOptimizationException("Experiment execution failed", e);
        } finally {
            // Cleanup resources
            cleanup();
        }
    }
    
    /**
     * Runs experiments for a specific scenario across all algorithms.
     *
     * @param scenario Scenario name to run
     * @param tracker Progress tracker instance
     * @param startCount Starting experiment count for progress
     * @param totalCount Total experiment count for progress
     */
    private void runScenarioBatch(String scenario, ProgressTracker tracker, 
                                  int startCount, int totalCount) {
        logger.info("Starting scenario batch: {} (VM: {}, Hosts: {})", 
            scenario, 
            scenarioSpecs.get(scenario).vmCount,
            scenarioSpecs.get(scenario).hostCount);
        
        // Check and log memory status
        MemoryManager.checkMemoryUsage("Scenario Start: " + scenario);
        
        for (String algorithm : algorithms) {
            String experimentKey = algorithm + "_" + scenario;
            ExperimentStatus status = experimentStatus.get(experimentKey);
            status.setStartTime(System.currentTimeMillis());
            
            try {
                // Process replications in batches to manage memory
                List<Integer> replications = IntStream.range(0, ExperimentConfig.REPLICATION_COUNT)
                    .boxed()
                    .collect(Collectors.toList());
                
                BatchProcessor.processBatches(
                    replications,
                    replicationBatch -> processBatch(algorithm, scenario, replicationBatch, 
                        tracker, startCount, totalCount),
                    batchSize
                );
                
                status.setStatus(ExperimentStatus.Status.COMPLETED);
                status.setEndTime(System.currentTimeMillis());
                
            } catch (Exception e) {
                logger.error("Failed to complete {} on scenario {}", algorithm, scenario, e);
                status.setStatus(ExperimentStatus.Status.FAILED);
                status.setErrorMessage(e.getMessage());
            }
        }
        
        logger.info("Completed scenario batch: {}", scenario);
    }
    
    /**
     * Processes a batch of replications for a specific algorithm and scenario.
     *
     * @param algorithm Algorithm name
     * @param scenario Scenario name
     * @param replicationBatch Batch of replication numbers
     * @param tracker Progress tracker
     * @param baseCount Base count for progress calculation
     * @param totalCount Total experiments count
     */
    private void processBatch(String algorithm, String scenario, List<Integer> replicationBatch,
                              ProgressTracker tracker, int baseCount, int totalCount) {
        logger.debug("Processing batch: {} {} replications {}-{}", 
            algorithm, scenario, replicationBatch.get(0), 
            replicationBatch.get(replicationBatch.size() - 1));
        
        if (parallelExecution && executorService != null) {
            // Parallel execution
            List<Future<ExperimentResult>> futures = new ArrayList<>();
            
            for (Integer replication : replicationBatch) {
                futures.add(executorService.submit(() -> 
                    runSingleExperiment(algorithm, scenario, replication)));
            }
            
            // Collect results
            for (int i = 0; i < futures.size(); i++) {
                try {
                    ExperimentResult result = futures.get(i).get(5, TimeUnit.MINUTES);
                    if (result != null) {
                        saveResult(result);
                        tracker.reportProgress("Experiments", 
                            baseCount + replicationBatch.get(i) + 1, totalCount);
                    }
                } catch (TimeoutException e) {
                    logger.error("Timeout for {} {} rep {}", 
                        algorithm, scenario, replicationBatch.get(i));
                } catch (Exception e) {
                    logger.error("Error collecting result for {} {} rep {}", 
                        algorithm, scenario, replicationBatch.get(i), e);
                }
            }
        } else {
            // Sequential execution
            for (Integer replication : replicationBatch) {
                try {
                    ExperimentResult result = runSingleExperiment(algorithm, scenario, replication);
                    if (result != null) {
                        saveResult(result);
                        tracker.reportProgress("Experiments", 
                            baseCount + replication + 1, totalCount);
                    }
                } catch (Exception e) {
                    logger.error("Failed experiment: {} {} rep {}", 
                        algorithm, scenario, replication, e);
                }
            }
        }
    }
    
    /**
     * Runs a single experiment for the specified algorithm, scenario, and replication.
     *
     * @param algorithm Algorithm name
     * @param scenario Scenario name
     * @param replication Replication number
     * @return Experiment result or null if failed
     */
    private ExperimentResult runSingleExperiment(String algorithm, String scenario, int replication) {
        logger.debug("Running experiment: {} {} replication {}", algorithm, scenario, replication);
        
        try {
            // Initialize random seed for reproducibility
            ExperimentConfig.initializeRandomSeed(replication);
            
            // Create test scenario
            ScenarioSpec spec = scenarioSpecs.get(scenario);
            TestScenario testScenario = TestScenarios.createScenario(scenario, spec);
            
            if (testScenario == null) {
                throw new ValidationException("Failed to create test scenario: " + scenario);
            }
            
            // Run experiment
            ExperimentResult result = ExperimentRunner.runExperiment(
                algorithm, testScenario, replication);
            
            if (result == null) {
                throw new ValidationException("Experiment returned null result");
            }
            
            // Validate results
            ResultValidator.validateResults(result);
            
            // Add experiment metadata
            result.setExperimentId(experimentId);
            result.setTimestamp(LocalDateTime.now());
            
            logger.debug("Completed experiment: {} {} replication {} - Success", 
                algorithm, scenario, replication);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed single experiment: {} {} rep {}", 
                algorithm, scenario, replication, e);
            
            // Create failed result entry
            ExperimentResult failedResult = new ExperimentResult(algorithm, scenario, replication, 0.0, 0.0, 0.0, 0, 0.0, 0, 0, 0);
            return failedResult;
        }
    }
    
    /**
     * Saves experiment result to storage and CSV file.
     *
     * @param result Experiment result to save
     */
    private void saveResult(ExperimentResult result) {
        if (result == null) {
            logger.warn("Attempted to save null result");
            return;
        }
        
        try {
            // Save to memory for analysis
            String key = result.getAlgorithm() + "_" + result.getScenario();
            allResults.computeIfAbsent(key, k -> new ArrayList<>()).add(result);
            
            // Save to CSV immediately (don't keep all in memory)
            CSVResultsWriter.writeResult(result);
            
            // Update experiment status
            ExperimentStatus status = experimentStatus.get(key);
            if (status != null) {
                status.incrementCompleted();
                if (result.isFailed()) {
                    status.incrementFailed();
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to save result for {} {} rep {}", 
                result.getAlgorithm(), result.getScenario(), 
                result.getReplication(), e);
        }
    }
    
    /**
     * Performs statistical analysis on all collected results.
     *
     * @throws StatisticalValidationException if analysis fails
     */
    private void performStatisticalAnalysis() throws StatisticalValidationException {
        logger.info("Starting statistical analysis...");
        
        try {
            // Validate minimum sample size
            for (Map.Entry<String, List<ExperimentResult>> entry : allResults.entrySet()) {
                if (entry.getValue().size() < 30) {
                    logger.warn("Insufficient samples for {}: {} (minimum 30 required)",
                        entry.getKey(), entry.getValue().size());
                }
            }
            
            // Perform statistical tests for each scenario
            for (String scenario : scenarios) {
                performScenarioAnalysis(scenario);
            }
            
            // Perform overall comparison analysis
            comparisonAnalyzer.analyzeAllResults(allResults);
            
            logger.info("Statistical analysis completed successfully");
            
        } catch (Exception e) {
            logger.error("Statistical analysis failed", e);
            throw new StatisticalValidationException("Failed to perform statistical analysis", e);
        }
    }
    
    /**
     * Performs statistical analysis for a specific scenario.
     *
     * @param scenario Scenario name to analyze
     */
    private void performScenarioAnalysis(String scenario) {
        logger.info("Analyzing scenario: {}", scenario);
        
        try {
            // Collect results for this scenario
            Map<String, List<ExperimentResult>> scenarioResults = new HashMap<>();
            for (String algorithm : algorithms) {
                String key = algorithm + "_" + scenario;
                if (allResults.containsKey(key)) {
                    scenarioResults.put(algorithm, allResults.get(key));
                }
            }
            
            if (scenarioResults.size() < 2) {
                logger.warn("Insufficient algorithms for comparison in scenario: {}", scenario);
                return;
            }
            
            // Perform statistical validation
            statisticalValidator.validateScenarioResults(scenario, scenarioResults);
            
        } catch (Exception e) {
            logger.error("Failed to analyze scenario: {}", scenario, e);
        }
    }
    
    /**
     * Generates comparison reports from the experimental results.
     */
    private void generateComparisonReports() {
        logger.info("Generating comparison reports...");
        
        try {
            // Generate summary statistics
            // CSVResultsWriter.writeSummaryStatistics(allResults); // Commented out as per edit hint
            
            // Generate comparison charts data
            // comparisonAnalyzer.generateComparisonData(allResults); // Commented out as per edit hint
            
            // Generate convergence analysis
            generateConvergenceAnalysis();
            
            // Generate scalability analysis
            generateScalabilityAnalysis();
            
            logger.info("Comparison reports generated successfully");
            
        } catch (Exception e) {
            logger.error("Failed to generate comparison reports", e);
        }
    }
    
    /**
     * Generates convergence analysis for optimization algorithms.
     */
    private void generateConvergenceAnalysis() {
        logger.info("Generating convergence analysis...");
        
        try {
            List<String> optimizationAlgorithms = Arrays.asList("HO", "GA");
            
            for (String algorithm : optimizationAlgorithms) {
                for (String scenario : scenarios) {
                    String key = algorithm + "_" + scenario;
                    if (allResults.containsKey(key)) {
                        List<ExperimentResult> results = allResults.get(key);
                        CSVResultsWriter.writeConvergenceData(algorithm, scenario, results);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to generate convergence analysis", e);
        }
    }
    
    /**
     * Generates scalability analysis across different scenario sizes.
     */
    private void generateScalabilityAnalysis() {
        logger.info("Generating scalability analysis...");
        
        try {
            for (String algorithm : algorithms) {
                Map<String, List<ExperimentResult>> algorithmResults = new HashMap<>();
                
                for (String scenario : scenarios) {
                    String key = algorithm + "_" + scenario;
                    if (allResults.containsKey(key)) {
                        algorithmResults.put(scenario, allResults.get(key));
                    }
                }
                
                if (!algorithmResults.isEmpty()) {
                    // CSVResultsWriter.writeScalabilityAnalysis(algorithm, algorithmResults, scenarioSpecs); // Commented out as per edit hint
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to generate scalability analysis", e);
        }
    }
    
    /**
     * Checks system requirements before starting experiments.
     *
     * @throws ValidationException if requirements are not met
     */
    private void checkSystemRequirements() throws ValidationException {
        logger.info("Checking system requirements...");
        
        // Check Java version
        String javaVersion = System.getProperty("java.version");
        if (!javaVersion.startsWith("21")) {
            logger.warn("Java version {} detected. Recommended: 21", javaVersion);
        }
        
        // Check memory
        long maxHeap = Runtime.getRuntime().maxMemory();
        long requiredHeap = 5L * 1024 * 1024 * 1024; // 5GB minimum
        
        if (maxHeap < requiredHeap) {
            throw new ValidationException(String.format(
                "Insufficient heap size. Required: %d MB, Available: %d MB",
                requiredHeap / (1024 * 1024), maxHeap / (1024 * 1024)
            ));
        }
        
        // Check disk space (rough estimate: 1GB for results)
        long freeSpace = new java.io.File(".").getFreeSpace();
        long requiredSpace = 1024L * 1024 * 1024; // 1GB
        
        if (freeSpace < requiredSpace) {
            logger.warn("Low disk space: {} MB available", freeSpace / (1024 * 1024));
        }
        
        logger.info("System requirements check passed");
    }
    
    /**
     * Checks if sufficient memory is available for a scenario.
     *
     * @param spec Scenario specification
     * @return true if memory is sufficient, false otherwise
     */
    private boolean checkMemoryForScenario(ScenarioSpec spec) {
        if (spec == null) {
            return false;
        }
        
        boolean hasMemory = MemoryManager.hasEnoughMemoryForScenario(
            spec.vmCount, spec.hostCount);
        
        if (!hasMemory) {
            logger.warn("Insufficient memory for scenario {} (VMs: {}, Hosts: {})",
                spec.name, spec.vmCount, spec.hostCount);
        }
        
        return hasMemory;
    }
    
    /**
     * Performs cleanup between scenarios to free memory.
     */
    private void cleanupBetweenScenarios() {
        logger.info("Cleaning up between scenarios...");
        
        // Force garbage collection
        System.gc();
        
        try {
            Thread.sleep(3000); // Give system time to clean up
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Cleanup interrupted");
        }
        
        // Check memory after cleanup
        MemoryManager.checkMemoryUsage("Post-scenario cleanup");
    }
    
    /**
     * Updates progress for skipped experiments.
     *
     * @param scenario Skipped scenario name
     * @param completed Number of completed experiments
     * @param total Total number of experiments
     */
    private void updateSkippedExperiments(String scenario, int completed, int total) {
        for (String algorithm : algorithms) {
            String key = algorithm + "_" + scenario;
            ExperimentStatus status = experimentStatus.get(key);
            if (status != null) {
                status.setStatus(ExperimentStatus.Status.SKIPPED);
                status.setErrorMessage("Insufficient memory");
            }
        }
        
        // Update progress
        int skipped = algorithms.size() * ExperimentConfig.REPLICATION_COUNT;
        progressTracker.reportProgress("Experiments", completed + skipped, total);
    }
    
    /**
     * Handles errors that occur during scenario execution.
     *
     * @param scenario Scenario that failed
     * @param error Error that occurred
     */
    private void handleScenarioError(String scenario, Exception error) {
        logger.error("Scenario {} failed: {}", scenario, error.getMessage());
        
        // Update status for all algorithms in this scenario
        for (String algorithm : algorithms) {
            String key = algorithm + "_" + scenario;
            ExperimentStatus status = experimentStatus.get(key);
            if (status != null && status.getStatus() != ExperimentStatus.Status.COMPLETED) {
                status.setStatus(ExperimentStatus.Status.FAILED);
                status.setErrorMessage(error.getMessage());
            }
        }
    }
    
    /**
     * Logs the experimental configuration.
     */
    private void logExperimentalConfiguration() {
        logger.info("=== Experimental Configuration ===");
        logger.info("Experiment ID: {}", experimentId);
        logger.info("Algorithms: {}", algorithms);
        logger.info("Scenarios: {}", scenarios);
        logger.info("Replications per experiment: {}", ExperimentConfig.REPLICATION_COUNT);
        logger.info("Total experiments: {}", 
            algorithms.size() * scenarios.size() * ExperimentConfig.REPLICATION_COUNT);
        logger.info("Batch size: {}", batchSize);
        logger.info("Parallel execution: {}", parallelExecution);
        logger.info("================================");
    }
    
    /**
     * Logs the completion summary.
     *
     * @param duration Total execution time in milliseconds
     */
    private void logCompletionSummary(long duration) {
        logger.info("=== Experiment Completion Summary ===");
        logger.info("Experiment ID: {}", experimentId);
        logger.info("Total duration: {} minutes", duration / 60000);
        
        // Count successful experiments
        int successful = 0;
        int failed = 0;
        int skipped = 0;
        
        for (ExperimentStatus status : experimentStatus.values()) {
            switch (status.getStatus()) {
                case COMPLETED:
                    successful++;
                    break;
                case FAILED:
                    failed++;
                    break;
                case SKIPPED:
                    skipped++;
                    break;
            }
        }
        
        logger.info("Successful experiments: {}", successful);
        logger.info("Failed experiments: {}", failed);
        logger.info("Skipped experiments: {}", skipped);
        logger.info("===================================");
    }
    
    /**
     * Generates a unique experiment ID.
     *
     * @return Unique experiment ID
     */
    private String generateExperimentId() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return "EXP_" + LocalDateTime.now().format(formatter);
    }
    
    /**
     * Cleans up resources after experiment completion.
     */
    private void cleanup() {
        logger.info("Cleaning up resources...");
        
        // Shutdown executor service
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear large data structures
        allResults.clear();
        experimentStatus.clear();
        
        // Force garbage collection
        System.gc();
        
        logger.info("Cleanup completed");
    }
    
    /**
     * Gets the current experiment status.
     *
     * @return Map of experiment status by algorithm_scenario key
     */
    public Map<String, ExperimentStatus> getExperimentStatus() {
        return new HashMap<>(experimentStatus);
    }
    
    /**
     * Gets the experiment ID.
     *
     * @return Unique experiment ID
     */
    public String getExperimentId() {
        return experimentId;
    }
    
    /**
     * Inner class representing scenario specifications.
     */
    public static class ScenarioSpec {
        public final String name;
        public final int vmCount;
        public final int hostCount;
        public final int complexityLevel;
        
        /**
         * Creates a new scenario specification.
         *
         * @param name Scenario name
         * @param vmCount Number of VMs
         * @param hostCount Number of hosts
         * @param complexityLevel Complexity level (1-5)
         */
        public ScenarioSpec(String name, int vmCount, int hostCount, int complexityLevel) {
            this.name = name;
            this.vmCount = vmCount;
            this.hostCount = hostCount;
            this.complexityLevel = complexityLevel;
        }
        
        @Override
        public String toString() {
            return String.format("ScenarioSpec[%s: %d VMs, %d Hosts, Level %d]",
                name, vmCount, hostCount, complexityLevel);
        }
    }
    
    /**
     * Inner class representing experiment status.
     */
    private static class ExperimentStatus {
        enum Status {
            PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
        }
        
        private final String algorithm;
        private final String scenario;
        private Status status;
        private long startTime;
        private long endTime;
        private int completed;
        private int failed;
        private String errorMessage;
        
        public ExperimentStatus(String algorithm, String scenario) {
            this.algorithm = algorithm;
            this.scenario = scenario;
            this.status = Status.PENDING;
            this.completed = 0;
            this.failed = 0;
        }
        
        // Getters and setters
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
        
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        
        public void incrementCompleted() { this.completed++; }
        public void incrementFailed() { this.failed++; }
        
        public void setErrorMessage(String errorMessage) { 
            this.errorMessage = errorMessage; 
        }
        
        public long getDuration() {
            return (endTime > 0 && startTime > 0) ? endTime - startTime : 0;
        }
    }
}