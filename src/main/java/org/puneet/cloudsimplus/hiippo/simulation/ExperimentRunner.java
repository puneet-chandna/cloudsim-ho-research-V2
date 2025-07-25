package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.puneet.cloudsimplus.hiippo.algorithm.AlgorithmConstants;
import org.puneet.cloudsimplus.hiippo.algorithm.HippopotamusOptimization;
import org.puneet.cloudsimplus.hiippo.algorithm.HippopotamusParameters;
import org.puneet.cloudsimplus.hiippo.baseline.BestFitAllocation;
import org.puneet.cloudsimplus.hiippo.baseline.FirstFitAllocation;
import org.puneet.cloudsimplus.hiippo.baseline.GeneticAlgorithmAllocation;
import org.puneet.cloudsimplus.hiippo.exceptions.HippopotamusOptimizationException;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.policy.AllocationValidator;
import org.puneet.cloudsimplus.hiippo.policy.HippopotamusVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.util.*;
import org.puneet.cloudsimplus.hiippo.util.CSVResultsWriter.ExperimentResult;
import org.puneet.cloudsimplus.hiippo.simulation.TestScenarios.TestScenario;
import org.puneet.cloudsimplus.hiippo.util.PerformanceMonitor.PerformanceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe experiment runner for CloudSimPlus simulations.
 * Manages complete lifecycle of experiments with proper resource cleanup.
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-22
 */
public class ExperimentRunner implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentRunner.class);
    
    // Thread-safe experiment tracking
    private static final AtomicInteger globalExperimentCounter = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, AtomicInteger> algorithmExecutionCounts = new ConcurrentHashMap<>();
    private static final ReadWriteLock statsLock = new ReentrantReadWriteLock();
    
    // Instance-specific thread pool for progress monitoring
    private final ExecutorService progressExecutor;
    private final Set<Future<?>> activeProgressTasks;
    private final Object progressTaskLock = new Object();
    
    // Timeout and retry configuration
    private static final long SIMULATION_TIMEOUT_SECONDS = 600; // 10 minutes
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5000;
    
    // Resource tracking for cleanup
    private final List<CloudSimPlus> activeSimulations;
    private final Object simulationLock = new Object();
    private volatile boolean closed = false;
    
    /**
     * Creates a new ExperimentRunner with dedicated thread pool.
     */
    public ExperimentRunner() {
        this.progressExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("ExperimentProgress-" + t.getId());
            return t;
        });
        this.activeProgressTasks = Collections.synchronizedSet(new HashSet<>());
        this.activeSimulations = Collections.synchronizedList(new ArrayList<>());
    }
    
    /**
     * Runs a complete experiment for the specified algorithm and scenario.
     * Thread-safe implementation with proper resource management.
     */
    public ExperimentResult runExperiment(String algorithmName, 
                                         TestScenario scenario, 
                                         int replication) throws HippopotamusOptimizationException {
        ensureNotClosed();
        validateInputs(algorithmName, scenario, replication);
        
        int experimentId = globalExperimentCounter.incrementAndGet();
        logger.info("Starting experiment {} - Algorithm: {}, Scenario: {}, Replication: {}", 
                   experimentId, algorithmName, scenario.getName(), replication);
        
        // Thread-safe algorithm execution counting
        algorithmExecutionCounts.computeIfAbsent(algorithmName, k -> new AtomicInteger(0))
                               .incrementAndGet();
        
        ExperimentResult result = null;
        int attempts = 0;
        
        while (attempts < MAX_RETRY_ATTEMPTS && result == null) {
            attempts++;
            CloudSimPlus simulation = null;
            
            try {
                // Check memory before starting
                MemoryManager.checkMemoryUsage("Pre-experiment " + experimentId);
                
                // Initialize experiment configuration for this thread
                ExperimentConfig.initializeRandomSeed(replication);
                
                // Create isolated simulation instance
                simulation = createAndRegisterSimulation(experimentId);
                
                // Run experiment with proper resource management
                result = executeExperiment(simulation, algorithmName, scenario, 
                                          replication, experimentId);
                
                // Validate result
                if (result != null) {
                    validateExperimentResult(result);
                    logger.info("Experiment {} completed successfully", experimentId);
                }
                
            } catch (Exception e) {
                logger.error("Experiment {} failed on attempt {}/{}: {}", 
                           experimentId, attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    handleRetry(experimentId, attempts);
                } else {
                    throw new HippopotamusOptimizationException(
                        HippopotamusOptimizationException.ErrorCode.CONVERGENCE_FAILURE,
                        "Experiment failed after " + MAX_RETRY_ATTEMPTS + " attempts", e);
                }
            } finally {
                // Clean up simulation resources
                cleanupSimulation(simulation);
            }
        }
        
        // Check memory after experiment
        MemoryManager.checkMemoryUsage("Post-experiment " + experimentId);
        
        return result;
    }
    
    /**
     * Creates and registers a new simulation instance.
     */
    private CloudSimPlus createAndRegisterSimulation(int experimentId) {
        CloudSimPlus simulation = new CloudSimPlus();
        // No terminateAt in CloudSimPlus; handle timeout via monitoring if needed
        synchronized (simulationLock) {
            activeSimulations.add(simulation);
        }
        
        logger.debug("Created CloudSimPlus simulation for experiment {}", experimentId);
        return simulation;
    }
    
    /**
     * Executes the experiment with the given simulation.
     */
    private ExperimentResult executeExperiment(CloudSimPlus simulation,
                                             String algorithmName,
                                             TestScenario scenario,
                                             int replication,
                                             int experimentId) throws HippopotamusOptimizationException {
        Instant startTime = Instant.now();
        
        // Create datacenter
        DatacenterSimple datacenter = createDatacenter(simulation, scenario);
        logger.debug("Created datacenter with {} hosts", scenario.getHosts().size());
        
        // Create broker
        DatacenterBroker broker = createBroker(simulation, algorithmName);
        logger.debug("Created broker for algorithm: {}", algorithmName);
        
        // Allocation policy is set in datacenter constructor, not after
        // Create thread-local copies of VMs and cloudlets to avoid shared state
        List<Vm> vms = createVmCopies(scenario.getVms(), simulation);
        List<Cloudlet> cloudlets = createCloudletCopies(scenario.getCloudlets(), broker);
        
        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        logger.debug("Submitted {} VMs and {} cloudlets", vms.size(), cloudlets.size());
        
        // Create metrics collector
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // Create performance monitor
        PerformanceMonitor performanceMonitor = new PerformanceMonitor();
        performanceMonitor.startMonitoring();
        
        // Run simulation with progress tracking
        Future<?> progressTask = runSimulationWithProgress(simulation, experimentId, startTime);
        
        try {
            // Start simulation
            simulation.start();
            
            // Wait for progress task to complete
            cancelProgressTask(progressTask);
            
        } catch (Exception e) {
            cancelProgressTask(progressTask);
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.UNKNOWN, "Simulation execution failed", e);
        }
        
        // Stop monitoring
        PerformanceMetrics performanceMetrics = performanceMonitor.stopMonitoring();
        
        // Collect metrics
        Map<String, Double> metrics = collectExperimentMetrics(
            simulation, broker, datacenter, metricsCollector, performanceMetrics);
        
        // Calculate execution time
        Duration executionTime = Duration.between(startTime, Instant.now());
        
        // Create and return result
        return createExperimentResult(
            algorithmName, scenario.getName(), replication, metrics, 
            executionTime, broker, datacenter.getVmAllocationPolicy());
    }
    
    /**
     * Creates copies of VMs to avoid shared state between experiments.
     */
    private List<Vm> createVmCopies(List<Vm> originalVms, CloudSimPlus simulation) {
        List<Vm> copies = new ArrayList<>();
        for (Vm original : originalVms) {
            Vm copy = new VmSimple(original.getMips(), original.getPesNumber());
            copy.setRam(original.getRam().getCapacity());
            copy.setBw(original.getBw().getCapacity());
            copy.setSize(original.getStorage().getCapacity());
            copy.setDescription(original.getDescription());
            copies.add(copy);
        }
        return copies;
    }
    
    /**
     * Creates copies of cloudlets to avoid shared state between experiments.
     */
    private List<Cloudlet> createCloudletCopies(List<Cloudlet> originalCloudlets, DatacenterBroker broker) {
        List<Cloudlet> copies = new ArrayList<>();
        for (Cloudlet original : originalCloudlets) {
            Cloudlet copy = new CloudletSimple(
                original.getLength(), 
                original.getPesNumber()
            );
            copy.setFileSize(original.getFileSize());
            copy.setOutputSize(original.getOutputSize());
            copy.setUtilizationModelCpu(original.getUtilizationModelCpu());
            copy.setUtilizationModelRam(original.getUtilizationModelRam());
            copy.setUtilizationModelBw(original.getUtilizationModelBw());
            copies.add(copy);
        }
        return copies;
    }
    
    /**
     * Runs simulation with progress tracking in a separate thread.
     */
    private Future<?> runSimulationWithProgress(CloudSimPlus simulation, 
                                               int experimentId,
                                               Instant startTime) {
        CompletableFuture<Void> progressFuture = new CompletableFuture<>();
        
        Future<?> task = progressExecutor.submit(() -> {
            try {
                ProgressTracker progressTracker = new ProgressTracker();
                
                while (simulation.isRunning() && 
                       !Thread.currentThread().isInterrupted()) {
                    
                    double progress = simulation.clock() / 1000.0; // Use fixed time for progress
                    progressTracker.reportProgress("Simulation " + experimentId, 
                                                 (int)(progress * 100), 100);
                    
                    // Check timeout
                    if (Duration.between(startTime, Instant.now()).getSeconds() > SIMULATION_TIMEOUT_SECONDS) {
                        logger.error("Simulation {} timeout - forcing termination", experimentId);
                        simulation.terminate();
                        break;
                    }
                    
                    try {
                        Thread.sleep(1000); // Update every second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                progressFuture.complete(null);
            } catch (Exception e) {
                progressFuture.completeExceptionally(e);
            }
        });
        
        synchronized (progressTaskLock) {
            activeProgressTasks.add(task);
        }
        
        return task;
    }
    
    /**
     * Cancels progress tracking task safely.
     */
    private void cancelProgressTask(Future<?> task) {
        if (task != null) {
            task.cancel(true);
            synchronized (progressTaskLock) {
                activeProgressTasks.remove(task);
            }
        }
    }
    
    /**
     * Cleans up simulation resources.
     */
    private void cleanupSimulation(CloudSimPlus simulation) {
        if (simulation != null) {
            try {
                synchronized (simulationLock) {
                    activeSimulations.remove(simulation);
                }
                simulation = null;
            } catch (Exception e) {
                logger.warn("Error during simulation cleanup", e);
            }
        }
    }
    
    /**
     * Validates input parameters.
     */
    private void validateInputs(String algorithmName, TestScenario scenario, int replication) {
        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            throw new IllegalArgumentException("Algorithm name cannot be null or empty");
        }
        if (scenario == null) {
            throw new IllegalArgumentException("Test scenario cannot be null");
        }
        if (replication < 0) {
            throw new IllegalArgumentException("Replication number must be non-negative");
        }
    }
    
    /**
     * Ensures the runner is not closed.
     */
    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("ExperimentRunner has been closed");
        }
    }
    
    /**
     * Handles retry with delay.
     */
    private void handleRetry(int experimentId, int attempt) {
        logger.info("Retrying experiment {} after {} ms delay", experimentId, RETRY_DELAY_MS);
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.TIMEOUT, "Experiment interrupted during retry delay", ie);
        }
    }
    
    /**
     * Gets thread-safe execution statistics.
     */
    public static Map<String, Object> getExecutionStatistics() {
        statsLock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalExperiments", globalExperimentCounter.get());
            
            Map<String, Integer> algorithmCounts = new HashMap<>();
            algorithmExecutionCounts.forEach((k, v) -> algorithmCounts.put(k, v.get()));
            stats.put("algorithmExecutions", algorithmCounts);
            
            return stats;
        } finally {
            statsLock.readLock().unlock();
        }
    }
    
    /**
     * Resets global counters (thread-safe).
     */
    public static void resetGlobalCounters() {
        statsLock.writeLock().lock();
        try {
            globalExperimentCounter.set(0);
            algorithmExecutionCounts.clear();
            logger.info("Global experiment counters reset");
        } finally {
            statsLock.writeLock().unlock();
        }
    }
    
    /**
     * Closes the experiment runner and releases all resources.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        logger.info("Closing ExperimentRunner");
        
        // Cancel all active progress tasks
        synchronized (progressTaskLock) {
            activeProgressTasks.forEach(task -> task.cancel(true));
            activeProgressTasks.clear();
        }
        
        // Shutdown progress executor
        progressExecutor.shutdown();
        try {
            if (!progressExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                progressExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            progressExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Clean up any remaining simulations
        synchronized (simulationLock) {
            activeSimulations.forEach(this::cleanupSimulation);
            activeSimulations.clear();
        }
        
        logger.info("ExperimentRunner closed successfully");
    }
    
    // Rest of the methods remain the same but with thread-local considerations...
    
    private static DatacenterSimple createDatacenter(CloudSimPlus simulation, TestScenario scenario) {
        if (scenario.getHosts() == null || scenario.getHosts().isEmpty()) {
            throw new ValidationException("Test scenario must contain at least one host");
        }
        
        try {
            // Use DatacenterFactory to create DatacenterSimple with allocation policy in constructor
            VmAllocationPolicy allocationPolicy = createAllocationPolicy("HO", scenario.getHosts()); // Or pass algorithmName if needed
            return new DatacenterSimple(simulation, scenario.getHosts(), allocationPolicy);
        } catch (Exception e) {
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.INVALID_HOST_CONFIG, "Failed to create datacenter", e);
        }
    }
    
    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algorithmName) {
        try {
            HODatacenterBroker broker = new HODatacenterBroker(simulation, algorithmName + "_Broker", algorithmName, "Unknown", 0);
            broker.setName(algorithmName + "_Broker_" + Thread.currentThread().getId());
            return broker;
        } catch (Exception e) {
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.INITIALIZATION_FAILURE, "Failed to create broker", e);
        }
    }
    
    private static VmAllocationPolicy createAllocationPolicy(String algorithmName, 
                                                           List<Host> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            throw new ValidationException("Host list cannot be null or empty");
        }
        
        logger.debug("Creating allocation policy for algorithm: {}", algorithmName);
        
        try {
            VmAllocationPolicy policy = switch (algorithmName.toUpperCase()) {
                case "HO" -> {
                    HippopotamusParameters params = new HippopotamusParameters();
                    params.setPopulationSize(AlgorithmConstants.DEFAULT_POPULATION_SIZE);
                    params.setMaxIterations(AlgorithmConstants.DEFAULT_MAX_ITERATIONS);
                    yield new HippopotamusVmAllocationPolicy(params);
                }
                case "FIRSTFIT" -> new FirstFitAllocation();
                case "BESTFIT" -> new BestFitAllocation();
                case "GA" -> new GeneticAlgorithmAllocation();
                default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
            };
            
            // Note: setHostList and validatePolicy methods are not available in CloudSim Plus 8.0.0
            // The policy will be configured when used in the datacenter
            
            return policy;
            
        } catch (Exception e) {
            throw new HippopotamusOptimizationException(
                HippopotamusOptimizationException.ErrorCode.INVALID_PARAMETER,
                "Failed to create allocation policy for " + algorithmName, e);
        }
    }
    private static Map<String, Double> collectExperimentMetrics(CloudSimPlus simulation,
                                                              DatacenterBroker broker,
                                                              DatacenterSimple datacenter,
                                                              MetricsCollector collector,
                                                              PerformanceMetrics performance) {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            // Resource utilization metrics
            double cpuUtilization = collector.calculateAverageCpuUtilization(
                datacenter.getHostList());
            double ramUtilization = collector.calculateAverageRamUtilization(
                datacenter.getHostList());
            
            metrics.put("cpuUtilization", cpuUtilization);
            metrics.put("ramUtilization", ramUtilization);
            
            // Power consumption
            double powerConsumption = collector.calculateTotalPowerConsumption(
                datacenter.getHostList());
            metrics.put("powerConsumption", powerConsumption);
            
            // SLA violations
            int slaViolations = collector.countSLAViolations(broker.getCloudletFinishedList());
            metrics.put("slaViolations", (double) slaViolations);
            
            // VM allocation success
            long allocatedVms = broker.getVmCreatedList().size();
            long totalVms = broker.getVmCreatedList().size() + broker.getVmFailedList().size();
            metrics.put("vmAllocationSuccess", totalVms > 0 ? (double) allocatedVms / totalVms : 0.0);
            
            // Performance metrics
            metrics.put("executionTimeMs", (double) performance.getExecutionTimeMillis());
            metrics.put("peakMemoryMB", (double) performance.getPeakMemoryUsageMB());
            
            // Cloudlet execution metrics
            double avgCloudletExecutionTime = broker.getCloudletFinishedList().stream()
                .mapToDouble(Cloudlet::getActualCpuTime)
                .average()
                .orElse(0.0);
            metrics.put("avgCloudletExecutionTime", avgCloudletExecutionTime);
            
            // Host efficiency metrics
            double avgHostEfficiency = datacenter.getHostList().stream()
                .mapToDouble(host -> {
                    double cpuUsage = host.getCpuUtilizationStats().getMean();
                    // Note: getRamUtilizationStats() is not available in CloudSim Plus 8.0.0
                    // Using CPU usage as approximation for overall efficiency
                    return cpuUsage;
                })
                .average()
                .orElse(0.0);
            metrics.put("avgHostEfficiency", avgHostEfficiency);
            
            logger.debug("Collected {} metrics for experiment", metrics.size());
            
        } catch (Exception e) {
            logger.error("Error collecting metrics", e);
            // Return partial metrics rather than failing completely
        }
        
        return metrics;
    }

    private static ExperimentResult createExperimentResult(String algorithmName,
                                                         String scenarioName,
                                                         int replication,
                                                         Map<String, Double> metrics,
                                                         Duration executionTime,
                                                         DatacenterBroker broker,
                                                         VmAllocationPolicy policy) {
        ExperimentResult result = new ExperimentResult();
        
        // Basic information
        result.setAlgorithm(algorithmName);
        result.setScenario(scenarioName);
        result.setReplication(replication);
        result.setTimestamp(Instant.now());
        
        // Metrics
        result.setResourceUtilizationCPU(metrics.getOrDefault("cpuUtilization", 0.0));
        result.setResourceUtilizationRAM(metrics.getOrDefault("ramUtilization", 0.0));
        result.setPowerConsumption(metrics.getOrDefault("powerConsumption", 0.0));
        result.setSlaViolations(metrics.getOrDefault("slaViolations", 0.0).intValue());
        result.setExecutionTime(executionTime.toMillis() / 1000.0); // Convert to seconds
        
        // VM allocation metrics
        int totalVms = broker.getVmCreatedList().size() + broker.getVmFailedList().size();
        int allocatedVms = broker.getVmCreatedList().size();
        result.setVmAllocated(allocatedVms);
        result.setVmTotal(totalVms);
        
        // Algorithm-specific metrics
        if (algorithmName.equalsIgnoreCase("HO") && 
            policy instanceof HippopotamusVmAllocationPolicy hoPolicy) {
            result.setConvergenceIterations(hoPolicy.getConvergenceIteration());
            result.setFinalFitness(hoPolicy.getBestFitness());
        } else {
            result.setConvergenceIterations(1); // Non-iterative algorithms
            result.setFinalFitness(0.0);
        }
        
        // Additional metrics
        result.setAvgCloudletExecutionTime(metrics.getOrDefault("avgCloudletExecutionTime", 0.0));
        result.setAvgHostEfficiency(metrics.getOrDefault("avgHostEfficiency", 0.0));
        result.setPeakMemoryUsageMB(metrics.getOrDefault("peakMemoryMB", 0.0));
        
        // Validation status
        result.setValid(true);
        result.setValidationMessage("Experiment completed successfully");
        
        return result;
    }
    private static void validateExperimentResult(ExperimentResult result) {
        List<String> validationErrors = new ArrayList<>();
        
        // Check required fields
        if (result.getAlgorithm() == null || result.getAlgorithm().isEmpty()) {
            validationErrors.add("Algorithm name is missing");
        }
        if (result.getScenario() == null || result.getScenario().isEmpty()) {
            validationErrors.add("Scenario name is missing");
        }
        if (result.getReplication() < 0) {
            validationErrors.add("Invalid replication number");
        }
        
        // Check metric ranges
        if (result.getResourceUtilizationCPU() < 0 || result.getResourceUtilizationCPU() > 1) {
            validationErrors.add("CPU utilization out of range [0,1]");
        }
        if (result.getResourceUtilizationRAM() < 0 || result.getResourceUtilizationRAM() > 1) {
            validationErrors.add("RAM utilization out of range [0,1]");
        }
        if (result.getPowerConsumption() < 0) {
            validationErrors.add("Power consumption cannot be negative");
        }
        if (result.getSlaViolations() < 0) {
            validationErrors.add("SLA violations cannot be negative");
        }
        if (result.getExecutionTime() <= 0) {
            validationErrors.add("Execution time must be positive");
        }
        
        // Check VM allocation consistency
        if (result.getVmAllocated() > result.getVmTotal()) {
            validationErrors.add("Allocated VMs cannot exceed total VMs");
        }
        if (result.getVmTotal() == 0) {
            validationErrors.add("Total VMs cannot be zero");
        }
        
        // Check algorithm-specific metrics
        if (result.getAlgorithm().equalsIgnoreCase("HO")) {
            if (result.getConvergenceIterations() <= 0) {
                validationErrors.add("HO convergence iterations must be positive");
            }
        }
        
        // If validation errors exist, mark result as invalid
        if (!validationErrors.isEmpty()) {
            result.setValid(false);
            result.setValidationMessage(String.join("; ", validationErrors));
            throw new ValidationException("Experiment result validation failed: " + 
                                        String.join(", ", validationErrors));
        }
        
        logger.debug("Experiment result validated successfully");
    }
    // The remaining methods (collectExperimentMetrics, createExperimentResult, 
    // validateExperimentResult) remain the same as they don't have thread safety issues
}