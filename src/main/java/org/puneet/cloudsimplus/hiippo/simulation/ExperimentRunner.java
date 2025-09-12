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
import org.puneet.cloudsimplus.hiippo.policy.BaselineVmAllocationPolicy;
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
        DatacenterSimple datacenter;
        try {
            datacenter = createDatacenter(simulation, scenario, algorithmName);
        } catch (ValidationException e) {
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.INVALID_HOST_CONFIG, "Failed to create datacenter", e);
        } catch (HippopotamusOptimizationException e) {
            throw e; // Re-throw as is
        }
        logger.debug("Created datacenter with {} hosts", scenario.getHosts().size());
        
        // Create broker
        DatacenterBroker broker;
        try {
            broker = createBroker(simulation, algorithmName);
        } catch (HippopotamusOptimizationException e) {
            throw e; // Re-throw as is
        }
        logger.debug("Created broker for algorithm: {}", algorithmName);
        
        // CRITICAL DEBUG: Verify broker and datacenter connection
        logger.info("DEBUG: Created broker with ID: {}", broker.getId());
        
        // Create thread-local copies of VMs and cloudlets to avoid shared state
        List<Vm> vms = createVmCopies(scenario.getVms(), simulation);
        List<Cloudlet> cloudlets = createCloudletCopies(scenario.getCloudlets(), broker);
        
        logger.info("DEBUG: Created {} VMs and {} cloudlets for algorithm {}", 
            vms.size(), cloudlets.size(), algorithmName);
        
        // Submit VMs and cloudlets
        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        logger.info("DEBUG: Submitted {} VMs and {} cloudlets to broker for algorithm {}", 
            vms.size(), cloudlets.size(), algorithmName);
        
        // CRITICAL DEBUG: Check broker-datacenter connection
        logger.info("DEBUG: Broker ID: {}, Datacenter ID: {}", broker.getId(), datacenter.getId());
        logger.info("DEBUG: Simulation entities: {}", simulation.getEntityList().size());
        
        // CRITICAL FIX: Ensure broker is connected to datacenter
        // In CloudSim Plus, this should happen automatically, but let's be explicit
        try {
            // Force broker to find datacenters in the simulation
            broker.setShutdownWhenIdle(false); // Prevent early shutdown
            logger.info("DEBUG: Broker shutdown behavior configured");
        } catch (Exception e) {
            logger.warn("DEBUG: Could not configure broker shutdown behavior: {}", e.getMessage());
        }
        
        // Create metrics collector
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // Create performance monitor
        PerformanceMonitor performanceMonitor = new PerformanceMonitor();
        performanceMonitor.startMonitoring();
        
        // CRITICAL FIX: Create utilization sampling mechanism
        Map<Host, List<Double>> hostCpuUtilizationSamples = new HashMap<>();
        Map<Host, List<Double>> hostRamUtilizationSamples = new HashMap<>();
        
        // Initialize sampling maps for all hosts
        for (Host host : datacenter.getHostList()) {
            hostCpuUtilizationSamples.put(host, new ArrayList<>());
            hostRamUtilizationSamples.put(host, new ArrayList<>());
        }
        
        // Add clock tick listener to sample utilization during simulation
        // Use a much smaller sampling interval to ensure we capture utilization
        double samplingInterval = 0.5; // Sample every 0.5 simulation time unit for more frequent sampling
        simulation.addOnClockTickListener(evt -> {
            double currentTime = simulation.clock();
            // Sample more frequently at the beginning and then every few time units
            if (currentTime > 0 && (currentTime <= 100 || currentTime % samplingInterval == 0)) {
                for (Host host : datacenter.getHostList()) {
                    // Sample CPU utilization (calculate manually to ensure decimal form)
                    double totalMips = host.getTotalMipsCapacity();
                    double allocatedMips = host.getTotalAllocatedMips();
                    double cpuUtil = totalMips > 0 ? allocatedMips / totalMips : 0.0;
                    // Ensure the value is in decimal form (0-1), not percentage
                    cpuUtil = Math.min(1.0, Math.max(0.0, cpuUtil));
                    hostCpuUtilizationSamples.get(host).add(cpuUtil);
                    
                    // Sample RAM utilization (calculate manually and ensure it's in decimal form)
                    double totalRam = host.getRam().getCapacity();
                    double allocatedRam = host.getRam().getAllocatedResource();
                    double ramUtil = totalRam > 0 ? allocatedRam / totalRam : 0.0;
                    // Ensure the value is in decimal form (0-1), not percentage
                    ramUtil = Math.min(1.0, Math.max(0.0, ramUtil));
                    hostRamUtilizationSamples.get(host).add(ramUtil);
                }
                
                // Log detailed utilization information every 100 time units (less frequently)
                if (currentTime % 100 == 0) {
                    double totalCpuUtil = 0.0;
                    double totalRamUtil = 0.0;
                    int activeHosts = 0;
                    
                    for (Host host : datacenter.getHostList()) {
                        double totalMips = host.getTotalMipsCapacity();
                        double allocatedMips = host.getTotalAllocatedMips();
                        double totalRam = host.getRam().getCapacity();
                        double allocatedRam = host.getRam().getAllocatedResource();
                        
                        if (totalMips > 0) {
                            double cpuUtil = allocatedMips / totalMips;
                            double ramUtil = totalRam > 0 ? allocatedRam / totalRam : 0.0;
                            totalCpuUtil += cpuUtil;
                            totalRamUtil += ramUtil;
                            activeHosts++;
                            
                            logger.debug("Host {} at time {}: CPU={}%, RAM={}%, VMs={}", 
                                host.getId(), currentTime,
                                String.format("%.2f", cpuUtil * 100),
                                String.format("%.2f", ramUtil * 100),
                                host.getVmList().size());
                        }
                    }
                    
                    if (activeHosts > 0) {
                        logger.debug("Average utilization at time {}: CPU={}%, RAM={}%, Active hosts: {}", 
                            currentTime,
                            String.format("%.2f", (totalCpuUtil / activeHosts) * 100),
                            String.format("%.2f", (totalRamUtil / activeHosts) * 100),
                            activeHosts);
                    }
                }
                
                // Log sampling progress less frequently
                if (currentTime % 100 == 0) {
                    logger.debug("Sampled utilization at time {} - CPU samples: {}, RAM samples: {}", 
                        currentTime, hostCpuUtilizationSamples.values().stream().mapToInt(List::size).sum(),
                        hostRamUtilizationSamples.values().stream().mapToInt(List::size).sum());
                }
            }
        });
        
        // Run simulation with progress tracking
        Future<?> progressTask = runSimulationWithProgress(simulation, experimentId, startTime);
        
        try {
            // Start simulation
            simulation.start();
            
            // CRITICAL FIX: Wait for cloudlets to actually start executing and show utilization
            // We need to wait for a reasonable amount of simulation time
            double simulationTime = simulation.clock();
            logger.debug("Simulation started, current time: {}", simulationTime);
            
            // Wait for cloudlets to be assigned to VMs and start executing
            double targetSimulationTime = 50.0; // Wait for 50 time units for cloudlets to start
            long startWaitTime = System.currentTimeMillis();
            long maxWaitTime = 30000; // 30 seconds max wait
            
            while (simulation.isRunning() && simulation.clock() < targetSimulationTime) {
                Thread.sleep(50); // Small delay to avoid busy waiting
                
                // Debug: Log simulation progress (less frequently)
                double currentTime = simulation.clock();
                if (currentTime % 50 == 0) {
                    logger.debug("Simulation time: {}, running: {}", currentTime, simulation.isRunning());
                }
                
                // Check if we've been waiting too long
                if (System.currentTimeMillis() - startWaitTime > maxWaitTime) {
                    logger.warn("Simulation taking too long to reach target time, proceeding anyway");
                    break;
                }
            }
            
            // Now wait for cloudlets to execute and show meaningful utilization
            // We want to capture utilization while cloudlets are actively running
            double executionTime = 500.0; // Let cloudlets run for 500 more time units to ensure meaningful utilization
            double finalTargetTime = simulation.clock() + executionTime;
            
            logger.info("Starting utilization sampling phase. Current time: {}, target: {}", 
                simulation.clock(), finalTargetTime);
            
            while (simulation.isRunning() && simulation.clock() < finalTargetTime) {
                Thread.sleep(100); // Check every 100ms
                
                            // Debug: Log simulation progress during utilization phase (less frequently)
            double currentTime = simulation.clock();
            if (currentTime % 100 == 0) {
                // Check cloudlet execution status during simulation
                List<Cloudlet> submittedCloudlets = broker.getCloudletSubmittedList();
                long runningCloudlets = submittedCloudlets.stream()
                    .filter(c -> c.getStatus() == Cloudlet.Status.INEXEC)
                    .count();
                long finishedCloudlets = submittedCloudlets.stream()
                    .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS)
                    .count();
                
                logger.debug("Utilization sampling phase - time: {}, running: {}, cloudlets: {}/{} running, {} finished", 
                    currentTime, simulation.isRunning(), runningCloudlets, submittedCloudlets.size(), finishedCloudlets);
            }
                
                // Check if we've been waiting too long
                if (System.currentTimeMillis() - startWaitTime > maxWaitTime) {
                    logger.warn("Simulation taking too long, proceeding with current samples");
                    break;
                }
            }
            
            // Wait for progress task to complete
            cancelProgressTask(progressTask);
            
            // Log utilization sampling results
            int totalCpuSamples = hostCpuUtilizationSamples.values().stream().mapToInt(List::size).sum();
            int totalRamSamples = hostRamUtilizationSamples.values().stream().mapToInt(List::size).sum();
            logger.info("Utilization sampling completed - CPU samples: {}, RAM samples: {}", 
                totalCpuSamples, totalRamSamples);
            
            // CRITICAL DEBUG: Check cloudlet status to understand why utilization might be 0
            List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
            List<Cloudlet> submittedCloudlets = broker.getCloudletSubmittedList();
            logger.info("Cloudlet Status - Submitted: {}, Finished: {}, Remaining: {}", 
                submittedCloudlets.size(), finishedCloudlets.size(), 
                submittedCloudlets.size() - finishedCloudlets.size());
            
            // Check if any cloudlets are still running
            long runningCloudlets = submittedCloudlets.stream()
                .filter(c -> c.getStatus() == Cloudlet.Status.INEXEC)
                .count();
            logger.info("Running cloudlets: {}", runningCloudlets);
            
            // Log some cloudlet details for debugging
            if (!finishedCloudlets.isEmpty()) {
                Cloudlet sampleCloudlet = finishedCloudlets.get(0);
                logger.info("Sample finished cloudlet - ID: {}, Length: {}, Finish time: {}", 
                    sampleCloudlet.getId(), sampleCloudlet.getLength(), 
                    sampleCloudlet.getFinishTime());
            }
            
            // CRITICAL FIX: If no samples were collected, manually sample utilization at the end
            if (totalCpuSamples == 0 || totalRamSamples == 0) {
                logger.warn("No utilization samples collected via clock tick listener, performing manual sampling");
                
                // Manually sample utilization from all hosts
                for (Host host : datacenter.getHostList()) {
                    // Sample CPU utilization (calculate manually to ensure decimal form)
                    double totalMips = host.getTotalMipsCapacity();
                    double allocatedMips = host.getTotalAllocatedMips();
                    double cpuUtil = totalMips > 0 ? allocatedMips / totalMips : 0.0;
                    // Ensure the value is in decimal form (0-1), not percentage
                    cpuUtil = Math.min(1.0, Math.max(0.0, cpuUtil));
                    hostCpuUtilizationSamples.get(host).add(cpuUtil);
                    
                    // Sample RAM utilization (calculate manually and ensure it's in decimal form)
                    double totalRam = host.getRam().getCapacity();
                    double allocatedRam = host.getRam().getAllocatedResource();
                    double ramUtil = totalRam > 0 ? allocatedRam / totalRam : 0.0;
                    // Ensure the value is in decimal form (0-1), not percentage
                    ramUtil = Math.min(1.0, Math.max(0.0, ramUtil));
                    hostRamUtilizationSamples.get(host).add(ramUtil);
                    
                    logger.debug("Manual sampling - Host {}: CPU={}%, RAM={}%", 
                        host.getId(),
                        String.format("%.2f", cpuUtil * 100),
                        String.format("%.2f", ramUtil * 100));
                }
                
                totalCpuSamples = hostCpuUtilizationSamples.values().stream().mapToInt(List::size).sum();
                totalRamSamples = hostRamUtilizationSamples.values().stream().mapToInt(List::size).sum();
                logger.info("Manual sampling completed - CPU samples: {}, RAM samples: {}", 
                    totalCpuSamples, totalRamSamples);
            }
            
            // Debug: Log final simulation state
            double safeTime = simulation.clock();
            if (Double.isInfinite(safeTime) || Double.isNaN(safeTime) || safeTime >= Double.MAX_VALUE / 2) {
                safeTime = 0.0; // fallback if simulator returned invalid time
            }
            logger.info("Final simulation state - time: {}, running: {}", 
                safeTime, simulation.isRunning());
            
        } catch (Exception e) {
            cancelProgressTask(progressTask);
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.UNKNOWN, "Simulation execution failed", e);
        }
        
        // Stop monitoring
        PerformanceMetrics performanceMetrics = performanceMonitor.stopMonitoring();
        
        // CRITICAL FIX: Pass utilization samples to metrics collection
        Map<String, Double> metrics = collectExperimentMetrics(
            simulation, broker, datacenter, metricsCollector, performanceMetrics,
            hostCpuUtilizationSamples, hostRamUtilizationSamples);
        
        // Calculate execution time
        Duration executionTime = Duration.between(startTime, Instant.now());
        
        // CRITICAL FIX: Force optimization for algorithms to ensure proper execution
        VmAllocationPolicy policy = datacenter.getVmAllocationPolicy();
        if (algorithmName.equalsIgnoreCase("HO") && policy instanceof HippopotamusVmAllocationPolicy) {
            HippopotamusVmAllocationPolicy hoPolicy = (HippopotamusVmAllocationPolicy) policy;
            logger.info("Forcing final optimization for HO algorithm with {} queued VMs", hoPolicy.getQueueSize());
            boolean optimizationResult = hoPolicy.forceOptimization();
            logger.info("Final HO optimization result: {}", optimizationResult);
        } else if (algorithmName.equalsIgnoreCase("GA") && policy instanceof GeneticAlgorithmAllocation) {
            GeneticAlgorithmAllocation gaPolicy = (GeneticAlgorithmAllocation) policy;
            logger.info("Forcing final optimization for GA algorithm");
            boolean optimizationResult = gaPolicy.forceOptimization();
            logger.info("Final GA optimization result: {}", optimizationResult);
        }
        
        // CRITICAL FIX: Add delay to ensure all VMs are properly allocated
        try {
            Thread.sleep(100); // Small delay to ensure allocation completion
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Create and return result
        return createExperimentResult(
            algorithmName, scenario.getName(), replication, metrics, 
            executionTime, broker, policy, datacenter);
    }
    
    /**
     * Creates copies of VMs to avoid shared state between experiments.
     */
    private List<Vm> createVmCopies(List<Vm> originalVms, CloudSimPlus simulation) {
        List<Vm> copies = new ArrayList<>();
        logger.info("DEBUG: Creating VM copies from {} original VMs", originalVms.size());
        
        for (Vm original : originalVms) {
            Vm copy = new VmSimple(original.getMips(), original.getPesNumber());
            copy.setRam(original.getRam().getCapacity());
            copy.setBw(original.getBw().getCapacity());
            copy.setSize(original.getStorage().getCapacity());
            copy.setDescription(original.getDescription());
            copies.add(copy);
            
            logger.debug("DEBUG: Created VM copy - ID: {}, MIPS: {}, PEs: {}, RAM: {} MB", 
                copy.getId(), copy.getMips(), copy.getPesNumber(), copy.getRam().getCapacity());
        }
        
        logger.info("DEBUG: Successfully created {} VM copies", copies.size());
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
                long lastProgressTime = System.currentTimeMillis();
                double lastSimulationTime = 0.0;
                
                while (simulation.isRunning() && 
                       !Thread.currentThread().isInterrupted()) {
                    
                    // Only update progress every 5 seconds to avoid spam
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastProgressTime < 5000) {
                        Thread.sleep(100);
                        continue;
                    }
                    
                    double currentSimulationTime = simulation.clock();
                    
                    // Calculate meaningful progress based on simulation time
                    // Target simulation time is 550.0 (50 + 500), so progress is current/target
                    int progress = (int) Math.min(100, (currentSimulationTime / 550.0) * 100);
                    
                    // Only log if there's actual progress or significant time has passed
                    if (progress > 0 || currentSimulationTime > lastSimulationTime + 50) {
                        logger.debug("Simulation {} progress: {}% (time: {})", experimentId, progress, currentSimulationTime);
                        lastSimulationTime = currentSimulationTime;
                    }
                    
                    lastProgressTime = currentTime;
                    
                    // Check timeout
                    if (Duration.between(startTime, Instant.now()).getSeconds() > SIMULATION_TIMEOUT_SECONDS) {
                        logger.error("Simulation {} timeout - forcing termination", experimentId);
                        simulation.terminate();
                        break;
                    }
                    
                    Thread.sleep(100);
                }
                
                logger.debug("Simulation {} completed", experimentId);
                progressFuture.complete(null);
                
            } catch (InterruptedException e) {
                logger.debug("Progress tracking interrupted for simulation {}", experimentId);
                Thread.currentThread().interrupt();
                progressFuture.completeExceptionally(e);
            } catch (Exception e) {
                logger.error("Error in progress tracking for simulation {}", experimentId, e);
                progressFuture.completeExceptionally(e);
            }
        });
        
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
    private void handleRetry(int experimentId, int attempt) throws HippopotamusOptimizationException {
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
    
    private static DatacenterSimple createDatacenter(CloudSimPlus simulation, TestScenario scenario, String algorithmName) throws ValidationException, HippopotamusOptimizationException {
        if (scenario.getHosts() == null || scenario.getHosts().isEmpty()) {
            throw new ValidationException("Test scenario must contain at least one host");
        }
        
        try {
            // Use DatacenterFactory to create DatacenterSimple with allocation policy in constructor
            VmAllocationPolicy allocationPolicy = createAllocationPolicy(algorithmName, scenario.getHosts());
            
            // Create datacenter with the allocation policy
            List<Host> hostList = new ArrayList<>(scenario.getHosts()); // Create a copy
            DatacenterSimple datacenter = new DatacenterSimple(simulation, hostList, allocationPolicy);
            
            // CRITICAL DEBUG: Verify allocation policy is set
            logger.info("DEBUG: Datacenter created with allocation policy: {}", 
                datacenter.getVmAllocationPolicy().getClass().getSimpleName());
            logger.info("DEBUG: Allocation policy has {} hosts", 
                datacenter.getVmAllocationPolicy().getHostList().size());
            
            // Set the datacenter in the allocation policy
            if (allocationPolicy instanceof BaselineVmAllocationPolicy) {
                BaselineVmAllocationPolicy baselinePolicy = (BaselineVmAllocationPolicy) allocationPolicy;
                baselinePolicy.setDatacenter(datacenter);
                logger.info("DEBUG: Set datacenter in baseline allocation policy");
            }
            
            return datacenter;
        } catch (Exception e) {
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.INVALID_HOST_CONFIG, "Failed to create datacenter", e);
        }
    }
    
    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algorithmName) throws HippopotamusOptimizationException {
        try {
            HODatacenterBroker broker = new HODatacenterBroker(simulation, algorithmName + "_Broker", algorithmName, "Unknown", 0);
            broker.setName(algorithmName + "_Broker_" + Thread.currentThread().getId());
            return broker;
        } catch (Exception e) {
            throw new HippopotamusOptimizationException(HippopotamusOptimizationException.ErrorCode.INITIALIZATION_FAILURE, "Failed to create broker", e);
        }
    }
    
    private static VmAllocationPolicy createAllocationPolicy(String algorithmName, 
                                                           List<Host> hosts) throws ValidationException, HippopotamusOptimizationException {
        if (hosts == null || hosts.isEmpty()) {
            throw new ValidationException("Host list cannot be null or empty");
        }
        
        logger.debug("Creating allocation policy for algorithm: {}", algorithmName);
        
        try {
            VmAllocationPolicy policy = switch (algorithmName.toUpperCase()) {
                case "HO" -> {
                    // CRITICAL FIX: Use small-scale parameters for faster execution
                    HippopotamusParameters params = HippopotamusParameters.createSmallScale();
                    logger.info("Using small-scale HO parameters: Population={}, Iterations={}, Batch={}", 
                        params.getPopulationSize(), params.getMaxIterations(), params.getBatchSize());
                    yield new HippopotamusVmAllocationPolicy(params);
                }
                case "FIRSTFIT" -> new FirstFitAllocation();
                case "BESTFIT" -> new BestFitAllocation();
                case "GA" -> new GeneticAlgorithmAllocation();
                default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
            };
            
            // CRITICAL FIX: Set host list for allocation policies that need it
            if (policy instanceof BaselineVmAllocationPolicy) {
                BaselineVmAllocationPolicy baselinePolicy = (BaselineVmAllocationPolicy) policy;
                baselinePolicy.setHostList(hosts);
                baselinePolicy.setDetailedLogging(true); // Enable detailed logging
                logger.debug("Set {} hosts in baseline allocation policy with detailed logging", hosts.size());
            }
            
            // CRITICAL FIX: For BestFit specifically, ensure proper host setup
            if (policy instanceof BestFitAllocation) {
                BestFitAllocation bestFit = (BestFitAllocation) policy;
                bestFit.setHostList(hosts);
                logger.debug("Manually set {} hosts in BestFit allocation policy", hosts.size());
            }
            
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
                                                              PerformanceMetrics performance,
                                                              Map<Host, List<Double>> hostCpuUtilizationSamples,
                                                              Map<Host, List<Double>> hostRamUtilizationSamples) {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            // CRITICAL FIX: Calculate resource utilization from sampled data during simulation
            double cpuUtilization = 0.0;
            double ramUtilization = 0.0;
            int activeHosts = 0;
            int totalCpuSamples = 0;
            int totalRamSamples = 0;
            
            // Calculate average utilization from all samples collected during simulation
            for (Host host : datacenter.getHostList()) {
                List<Double> cpuSamples = hostCpuUtilizationSamples.get(host);
                List<Double> ramSamples = hostRamUtilizationSamples.get(host);
                
                if (cpuSamples != null && !cpuSamples.isEmpty()) {
                    double hostAvgCpuUtil = cpuSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    cpuUtilization += hostAvgCpuUtil;
                    totalCpuSamples += cpuSamples.size();
                    activeHosts++;
                    
                    logger.debug("Host {} CPU utilization samples: {} (avg: {}%)", 
                        host.getId(), cpuSamples.size(), String.format("%.2f", hostAvgCpuUtil * 100));
                }
                
                if (ramSamples != null && !ramSamples.isEmpty()) {
                    double hostAvgRamUtil = ramSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    ramUtilization += hostAvgRamUtil;
                    totalRamSamples += ramSamples.size();
                    
                    logger.debug("Host {} RAM utilization samples: {} (avg: {}%)", 
                        host.getId(), ramSamples.size(), String.format("%.2f", hostAvgRamUtil * 100));
                }
            }
            
            // Calculate overall averages
            cpuUtilization = activeHosts > 0 ? cpuUtilization / activeHosts : 0.0;
            ramUtilization = activeHosts > 0 ? ramUtilization / activeHosts : 0.0;
            
            logger.info("Resource Utilization Analysis:");
            logger.info("  Active Hosts: {}", activeHosts);
            logger.info("  Total CPU Samples: {}", totalCpuSamples);
            logger.info("  Total RAM Samples: {}", totalRamSamples);
            logger.info("  Average CPU Utilization: {}%", String.format("%.2f", cpuUtilization * 100));
            logger.info("  Average RAM Utilization: {}%", String.format("%.2f", ramUtilization * 100));
            
            // If no samples were collected, fall back to end-of-simulation calculation
            if (totalCpuSamples == 0) {
                logger.warn("No CPU utilization samples collected, using fallback calculation");
                cpuUtilization = calculateFallbackCpuUtilization(datacenter);
            }
            
            if (totalRamSamples == 0) {
                logger.warn("No RAM utilization samples collected, using fallback calculation");
                ramUtilization = calculateFallbackRamUtilization(datacenter);
            }
            
            metrics.put("cpuUtilization", cpuUtilization);
            metrics.put("ramUtilization", ramUtilization);
            
            // Power consumption
            double powerConsumption = collector.calculateTotalPowerConsumption(
                datacenter.getHostList());
            metrics.put("powerConsumption", powerConsumption);
            
            // SLA violations - REMOVED: This was overriding the correct SLA violations 
            // calculated by CloudSimHOSimulation. The SLA violations are already 
            // calculated correctly in the simulation results.
            // int slaViolations = collector.countSLAViolations(broker.getCloudletFinishedList());
            // metrics.put("slaViolations", (double) slaViolations);
            
            // VM allocation success - use broker's VM lists instead of host VM lists
            // The broker's getVmCreatedList() contains VMs that were successfully allocated
            long allocatedVms = broker.getVmCreatedList().size();
            long totalVms = broker.getVmCreatedList().size() + broker.getVmFailedList().size();
            
            // DEBUG: Log VM allocation details
            logger.info("DEBUG: VM Allocation Details:");
            logger.info("DEBUG:   Total VMs created (allocated): {}", broker.getVmCreatedList().size());
            logger.info("DEBUG:   Total VMs failed: {}", broker.getVmFailedList().size());
            logger.info("DEBUG:   VMs currently on hosts: {}", datacenter.getHostList().stream()
                .mapToLong(host -> host.getVmList().size()).sum());
            logger.info("DEBUG:   Host details:");
            for (Host host : datacenter.getHostList()) {
                logger.info("DEBUG:     Host {}: {} VMs", host.getId(), host.getVmList().size());
            }
            
            metrics.put("vmAllocationSuccess", totalVms > 0 ? (double) allocatedVms / totalVms : 0.0);
            metrics.put("vmAllocated", (double) allocatedVms);
            metrics.put("vmTotal", (double) totalVms);
            
            // Execution time
            metrics.put("executionTime", (double) performance.getExecutionTimeMillis());
            
            // Convergence iterations (for optimization algorithms)
            int convergenceIterations = 1; // Default for non-optimization algorithms
            // Note: For now using default value, can be enhanced later with actual iteration tracking
            metrics.put("convergenceIterations", (double) convergenceIterations);
            
        } catch (Exception e) {
            logger.error("Error collecting experiment metrics", e);
            // Set default values
            metrics.put("cpuUtilization", 0.0);
            metrics.put("ramUtilization", 0.0);
            metrics.put("powerConsumption", 0.0);
            metrics.put("slaViolations", 0.0);
            metrics.put("vmAllocationSuccess", 0.0);
            metrics.put("vmAllocated", 0.0);
            metrics.put("vmTotal", 0.0);
            metrics.put("executionTime", 0.0);
            metrics.put("convergenceIterations", 0.0);
        }
        
        return metrics;
    }
    
    /**
     * Fallback method to calculate CPU utilization when no samples are available.
     */
    private static double calculateFallbackCpuUtilization(DatacenterSimple datacenter) {
        double totalUtilization = 0.0;
        int activeHosts = 0;
        
        logger.info("Calculating fallback CPU utilization for {} hosts", datacenter.getHostList().size());
        
        for (Host host : datacenter.getHostList()) {
            double totalMips = host.getTotalMipsCapacity();
            double allocatedMips = host.getTotalAllocatedMips();
            
            logger.debug("Host {}: Total MIPS: {}, Allocated MIPS: {}, VMs: {}", 
                host.getId(), totalMips, allocatedMips, host.getVmList().size());
            
            if (totalMips > 0) {
                double hostUtil = allocatedMips / totalMips;
                totalUtilization += hostUtil;
                activeHosts++;
                
                logger.debug("Host {} CPU utilization: {}%", host.getId(), String.format("%.2f", hostUtil * 100));
            }
        }
        
        double avgUtil = activeHosts > 0 ? totalUtilization / activeHosts : 0.0;
        logger.info("Fallback CPU utilization: {}% (from {} active hosts)", String.format("%.2f", avgUtil * 100), activeHosts);
        
        return avgUtil;
    }
    
    /**
     * Fallback method to calculate RAM utilization when no samples are available.
     */
    private static double calculateFallbackRamUtilization(DatacenterSimple datacenter) {
        double totalUtilization = 0.0;
        int activeHosts = 0;
        
        logger.info("Calculating fallback RAM utilization for {} hosts", datacenter.getHostList().size());
        
        for (Host host : datacenter.getHostList()) {
            double totalRam = host.getRam().getCapacity();
            double allocatedRam = host.getRam().getAllocatedResource();
            
            logger.debug("Host {}: Total RAM: {}, Allocated RAM: {}, VMs: {}", 
                host.getId(), totalRam, allocatedRam, host.getVmList().size());
            
            if (totalRam > 0) {
                double hostUtil = allocatedRam / totalRam;
                totalUtilization += hostUtil;
                activeHosts++;
                
                logger.debug("Host {} RAM utilization: {}%", host.getId(), String.format("%.2f", hostUtil * 100));
            }
        }
        
        double avgUtil = activeHosts > 0 ? totalUtilization / activeHosts : 0.0;
        logger.info("Fallback RAM utilization: {}% (from {} active hosts)", String.format("%.2f", avgUtil * 100), activeHosts);
        
        return avgUtil;
    }

    private static ExperimentResult createExperimentResult(String algorithmName,
                                                         String scenarioName,
                                                         int replication,
                                                         Map<String, Double> metrics,
                                                         Duration executionTime,
                                                         DatacenterBroker broker,
                                                         VmAllocationPolicy policy,
                                                         DatacenterSimple datacenter) {
        ExperimentResult result = new ExperimentResult();
        
        // Basic information
        result.setAlgorithm(algorithmName);
        result.setScenario(scenarioName);
        result.setReplication(replication);
        result.setTimestamp(Instant.now());
        
        // Metrics - store as decimal values (0-1) for validation, but display as percentages
        result.setResourceUtilizationCPU(metrics.getOrDefault("cpuUtilization", 0.0));
        result.setResourceUtilizationRAM(metrics.getOrDefault("ramUtilization", 0.0));
        result.setPowerConsumption(metrics.getOrDefault("powerConsumption", 0.0));
        result.setSlaViolations(metrics.getOrDefault("slaViolations", 0.0).intValue());
        result.setExecutionTime(executionTime.toMillis() / 1000.0); // Convert to seconds
        
        // VM allocation metrics
        result.setVmAllocated(metrics.getOrDefault("vmAllocated", 0.0).intValue());
        result.setVmTotal(metrics.getOrDefault("vmTotal", 0.0).intValue());
        
        // VM allocation metrics - use broker's VM lists instead of host VM lists
        // The broker's getVmCreatedList() contains VMs that were successfully allocated
        int totalVms = broker.getVmCreatedList().size() + broker.getVmFailedList().size();
        int allocatedVms = broker.getVmCreatedList().size(); // VMs that were successfully allocated
        
        // DEBUG: Log VM allocation details
        logger.info("DEBUG: VM Allocation Details - Created: {}, Failed: {}, Total: {}", 
                   broker.getVmCreatedList().size(), broker.getVmFailedList().size(), totalVms);
        
        // If no VMs were created or failed, check if VMs were submitted at all
        if (totalVms == 0) {
            logger.warn("DEBUG: No VMs found in broker lists. Checking scenario VM count...");
            // Fallback to scenario VM count if broker lists are empty
            Map<String, int[]> scenarioSpecs = ExperimentConfig.getScenarioSpecifications();
            int expectedVms = scenarioSpecs.containsKey(scenarioName) ? scenarioSpecs.get(scenarioName)[0] : 0;
            totalVms = expectedVms;
            allocatedVms = 0; // Assume none allocated if broker lists are empty
            logger.warn("DEBUG: Using scenario VM count as fallback: {} (expected: {})", totalVms, expectedVms);
        }
        
        result.setVmAllocated(allocatedVms);
        result.setVmTotal(totalVms);
        
        // Set VMs and hosts for validation - use the VMs that were created and the hosts from datacenter
        // Even though VMs are deallocated now, we use the created VMs list for validation
        result.setVms(broker.getVmCreatedList());
        result.setHosts(datacenter.getHostList());
        
        // DEBUG: Log the values being set in ExperimentResult
        logger.info("DEBUG: ExperimentResult VM values - allocatedVms: {}, totalVms: {}", allocatedVms, totalVms);
        logger.info("DEBUG: ExperimentResult after setting - getVmAllocated(): {}, getVmTotal(): {}", 
                   result.getVmAllocated(), result.getVmTotal());
        logger.info("DEBUG: ExperimentResult VMs and Hosts - VMs: {}, Hosts: {}", 
                   result.getVms() != null ? result.getVms().size() : "null", 
                   result.getHosts() != null ? result.getHosts().size() : "null");
        
        // Algorithm-specific metrics
        if (algorithmName.equalsIgnoreCase("HO") && 
            policy instanceof HippopotamusVmAllocationPolicy hoPolicy) {
            result.setConvergenceIterations(hoPolicy.getConvergenceIteration());
            result.setFinalFitness(hoPolicy.getBestFitness());
        } else if (algorithmName.equalsIgnoreCase("GA") && 
                   policy instanceof GeneticAlgorithmAllocation gaPolicy) {
            result.setConvergenceIterations(gaPolicy.getConvergenceIterations());
            result.setFinalFitness(gaPolicy.getOptimizationTime()); // Use optimization time as fitness proxy
        } else {
            result.setConvergenceIterations(1); // Non-iterative algorithms
            result.setFinalFitness(0.0);
        }
        
        // Add additional fitness metrics for convergence analysis
        if (result.getMetrics() != null) {
            // Set default values for missing metrics
            if (!result.getMetrics().containsKey("improvementRate")) {
                result.getMetrics().put("improvementRate", 0.0);
            }
            if (!result.getMetrics().containsKey("averageFitness")) {
                result.getMetrics().put("averageFitness", 0.0);
            }
            if (!result.getMetrics().containsKey("populationDiversity")) {
                result.getMetrics().put("populationDiversity", 0.0);
            }
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
    private static void validateExperimentResult(ExperimentResult result) throws ValidationException {
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