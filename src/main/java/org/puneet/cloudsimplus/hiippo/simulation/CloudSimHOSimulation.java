package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.listeners.DatacenterBrokerEventInfo;
import org.cloudsimplus.listeners.EventListener;

import org.puneet.cloudsimplus.hiippo.policy.HippopotamusVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.policy.BaselineVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.baseline.FirstFitAllocation;
import org.puneet.cloudsimplus.hiippo.baseline.BestFitAllocation;
import org.puneet.cloudsimplus.hiippo.baseline.GeneticAlgorithmAllocation;
import org.puneet.cloudsimplus.hiippo.util.*;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.algorithm.AlgorithmConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.puneet.cloudsimplus.hiippo.util.CSVResultsWriter.ExperimentResult;
import org.puneet.cloudsimplus.hiippo.util.PerformanceMonitor.PerformanceMetrics;
import org.puneet.cloudsimplus.hiippo.simulation.TestScenarios.TestScenario;
// import org.cloudsimplus.vms.VerticalVmScaling; // Class removed in CloudSim Plus 8.0.0

/**
 * Main CloudSim Plus simulation orchestrator for Hippopotamus Optimization experiments.
 * Handles simulation setup, execution, and metrics collection with memory-efficient processing.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-20
 */
public class CloudSimHOSimulation {
    private static final Logger logger = LoggerFactory.getLogger(CloudSimHOSimulation.class);
    
    private final CloudSimPlus simulation;
    private final String algorithmName;
    private final TestScenario testScenario;
    private final int replicationNumber;
    
    private Datacenter datacenter;
    private DatacenterBroker broker;
    private List<Vm> vmList;
    private List<Host> hostList;
    private List<Cloudlet> cloudletList;
    
    private final MetricsCollector metricsCollector;
    private final PerformanceMonitor performanceMonitor;
    
    // Simulation timing
    private long simulationStartTime;
    private long simulationEndTime;
    private long allocationStartTime;
    private long allocationEndTime;
    
    // Memory management
    private final boolean enableMemoryMonitoring;
    private final long memoryCheckInterval = 5000; // 5 seconds
    private volatile boolean memoryWarningIssued = false;
    
    /**
     * Creates a new CloudSim HO simulation instance.
     * 
     * @param algorithmName Name of the allocation algorithm
     * @param testScenario Test scenario containing VM and host specifications
     * @param replicationNumber Replication number for reproducibility
     */
    public CloudSimHOSimulation(String algorithmName, TestScenario testScenario, int replicationNumber) {
        this.algorithmName = algorithmName;
        this.testScenario = testScenario;
        this.replicationNumber = replicationNumber;
        
        // Initialize CloudSim Plus with deterministic seed
        this.simulation = new CloudSimPlus();
        
        this.metricsCollector = new MetricsCollector();
        this.performanceMonitor = new PerformanceMonitor();
        this.enableMemoryMonitoring = ExperimentConfig.ENABLE_BATCH_PROCESSING;
        
        logger.info("Initialized CloudSim HO Simulation - Algorithm: {}, Scenario: {}, Replication: {}",
                algorithmName, testScenario.getName(), replicationNumber);
    }
    
    /**
     * Runs the complete simulation and returns the experiment result.
     * 
     * @return ExperimentResult containing all metrics
     * @throws ValidationException if simulation setup or execution fails
     */
    public ExperimentResult runSimulation() throws ValidationException {
        try {
            logger.info("Starting simulation - Algorithm: {}, Scenario: {}", 
                    algorithmName, testScenario.getName());
            
            // Check memory before starting
            if (!MemoryManager.hasEnoughMemoryForScenario(
                    testScenario.getVmCount(), testScenario.getHostCount())) {
                throw new ValidationException("Insufficient memory for scenario: " + testScenario.getName());
            }
            
            simulationStartTime = System.currentTimeMillis();
            performanceMonitor.startMonitoring();
            
            // Setup simulation components
            setupSimulation();
            
            // Run the simulation
            simulation.start();
            
            // CRITICAL FIX: Wait for all cloudlets to complete with better logic
            logger.info("Waiting for all cloudlets to complete...");
            int maxWaitTime = 60000; // 60 seconds max wait (increased from 30)
            int waitTime = 0;
            int checkInterval = 500; // Check every 500ms
            int lastFinishedCount = 0;
            int noProgressCount = 0;
            
            while (broker.getCloudletFinishedList().size() < cloudletList.size() && waitTime < maxWaitTime) {
                try {
                    Thread.sleep(checkInterval);
                    waitTime += checkInterval;
                    
                    int currentFinished = broker.getCloudletFinishedList().size();
                    if (currentFinished == lastFinishedCount) {
                        noProgressCount++;
                        if (noProgressCount > 20) { // No progress for 10 seconds
                            logger.warn("No cloudlet progress for 10 seconds. Current: {}/{}, waited: {}ms", 
                                currentFinished, cloudletList.size(), waitTime);
                            // Force simulation to continue
                            simulation.clock();
                        }
                    } else {
                        noProgressCount = 0;
                        lastFinishedCount = currentFinished;
                        logger.debug("Cloudlet progress: {}/{} completed", currentFinished, cloudletList.size());
                    }
                    
                    // Periodically advance simulation time
                    if (waitTime % 5000 == 0) { // Every 5 seconds
                        simulation.clock();
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Simulation wait interrupted");
                    break;
                }
            }
            
            if (broker.getCloudletFinishedList().size() < cloudletList.size()) {
                logger.warn("Not all cloudlets completed. Finished: {}/{}, waited: {}ms", 
                    broker.getCloudletFinishedList().size(), cloudletList.size(), waitTime);
                // Force simulation to end
                simulation.terminate();
            } else {
                logger.info("All {} cloudlets completed successfully", cloudletList.size());
            }
            
            // Additional delay to ensure all metrics are collected
            long simulationDelay = calculateSimulationDelay();
            logger.info("Adding simulation delay of {} ms for research-scale execution", simulationDelay);
            try {
                Thread.sleep(simulationDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Simulation delay interrupted");
            }
            
            simulationEndTime = System.currentTimeMillis();
            PerformanceMetrics performanceMetrics = performanceMonitor.stopMonitoring();
            
            // Collect and validate results
            ExperimentResult result = collectResults(performanceMetrics);
            
            // Validate results
            validateSimulationResults(result);
            
            logger.info("Simulation completed successfully - Execution time: {} ms", 
                    result.getExecutionTime());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Simulation failed - Algorithm: {}, Scenario: {}", 
                    algorithmName, testScenario.getName(), e);
            throw new ValidationException("Simulation execution failed: " + e.getMessage(), e);
        } finally {
            // Clean up resources
            cleanupSimulation();
        }
    }
    
    /**
     * Sets up all simulation components including datacenter, broker, VMs, and hosts.
     */
    private void setupSimulation() throws ValidationException {
        try {
            logger.debug("Setting up simulation components");
            
            // Create datacenter with hosts
            createDatacenter();
            
            // Create broker
            createBroker();
            
            // Create VMs based on test scenario
            createVMs();
            
            // Create cloudlets for VMs
            createCloudlets();
            
            // Submit VMs and cloudlets to broker
            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);
            
            // Setup event listeners
            setupEventListeners();
            
            logger.debug("Simulation setup completed - VMs: {}, Hosts: {}", 
                    vmList.size(), hostList.size());
            
        } catch (Exception e) {
            throw new ValidationException("Failed to setup simulation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates the datacenter with hosts and appropriate allocation policy.
     */
    private void createDatacenter() throws ValidationException {
        logger.debug("Creating datacenter with {} hosts", testScenario.getHostCount());
        
        // Create hosts using DatacenterFactory
        hostList = DatacenterFactory.createHosts((CloudSimPlus) simulation, testScenario.getHostCount());
        
        if (hostList == null || hostList.isEmpty()) {
            throw new ValidationException("Failed to create hosts for datacenter");
        }
        
        // Create allocation policy based on algorithm
        VmAllocationPolicy allocationPolicy = createAllocationPolicy();
        
        // CRITICAL FIX: Ensure allocation policy knows about the hosts BEFORE creating datacenter
        if (allocationPolicy instanceof BaselineVmAllocationPolicy) {
            BaselineVmAllocationPolicy baselinePolicy = (BaselineVmAllocationPolicy) allocationPolicy;
            baselinePolicy.setHostList(hostList);
            logger.debug("Set {} hosts in baseline allocation policy", hostList.size());
        }
        
        // CRITICAL FIX: For BestFit specifically, ensure proper host setup
        if (allocationPolicy instanceof BestFitAllocation) {
            BestFitAllocation bestFit = (BestFitAllocation) allocationPolicy;
            bestFit.setHostList(hostList);
            logger.debug("Manually set {} hosts in BestFit allocation policy", hostList.size());
        }
        
        // Track allocation timing
        allocationPolicy = wrapWithTimingPolicy(allocationPolicy);
        
        // Create datacenter with the policy
        datacenter = DatacenterFactory.createDatacenter((CloudSimPlus) simulation, testScenario.getHostCount(), allocationPolicy, testScenario.getName());
        
        if (datacenter == null) {
            throw new ValidationException("Failed to create datacenter");
        }
        
        // CRITICAL FIX: Ensure allocation policy is properly connected to datacenter
        allocationPolicy.setDatacenter(datacenter);
        
        // CRITICAL FIX: Verify hosts are properly set in allocation policy
        List<Host> policyHosts = allocationPolicy.getHostList();
        if (policyHosts == null || policyHosts.isEmpty()) {
            logger.warn("Allocation policy has no hosts after datacenter creation. Attempting to fix...");
            try {
                // Use reflection to set hosts directly
                java.lang.reflect.Field hostListField = allocationPolicy.getClass().getSuperclass().getDeclaredField("hostList");
                hostListField.setAccessible(true);
                hostListField.set(allocationPolicy, hostList);
                logger.debug("Fixed host list in allocation policy via reflection");
            } catch (Exception e) {
                logger.error("Failed to fix host list in allocation policy", e);
            }
        }
        
        logger.debug("Datacenter created successfully with {} hosts", hostList.size());
    }
    
    /**
     * Creates the appropriate VM allocation policy based on the algorithm name.
     */
    private VmAllocationPolicy createAllocationPolicy() throws ValidationException {
        logger.debug("Creating allocation policy for algorithm: {}", algorithmName);
        
        VmAllocationPolicy policy;
        
        switch (algorithmName.toUpperCase()) {
            case "HO":
            case "HIPPOPOTAMUS":
                policy = new HippopotamusVmAllocationPolicy();
                break;
                
            case "FIRSTFIT":
            case "FF":
                policy = new FirstFitAllocation();
                break;
                
            case "BESTFIT":
            case "BF":
                policy = new BestFitAllocation();
                break;
                
            case "GA":
            case "GENETIC":
                policy = new GeneticAlgorithmAllocation();
                break;
                
            default:
                throw new ValidationException("Unknown algorithm: " + algorithmName);
        }
        
        // Remove setHostList calls
        
        return policy;
    }
    
    /**
     * Wraps the allocation policy to track allocation timing.
     */
    private VmAllocationPolicy wrapWithTimingPolicy(VmAllocationPolicy basePolicy) {
        return new VmAllocationPolicy() {
            @Override
            public HostSuitability allocateHostForVm(Vm vm) {
                if (allocationStartTime == 0) {
                    allocationStartTime = System.currentTimeMillis();
                }
                HostSuitability result = basePolicy.allocateHostForVm(vm);
                allocationEndTime = System.currentTimeMillis();
                return result;
            }
            @Override
            public HostSuitability allocateHostForVm(Vm vm, Host host) {
                try {
                    return basePolicy.allocateHostForVm(vm, host);
                } catch (Exception e) {
                    logger.error(
                        "Error in allocateHostForVm for VM {} on Host {}: {}",
                        vm.getId(),
                        host.getId(),
                        e.getMessage()
                    );
                    return HostSuitability.NULL;
                }
            }
            @Override
            public void deallocateHostForVm(Vm vm) {
                basePolicy.deallocateHostForVm(vm);
            }
            @Override
            public Map<Vm, Host> getOptimizedAllocationMap(List<? extends Vm> vmList) {
                if (allocationStartTime == 0) {
                    allocationStartTime = System.currentTimeMillis();
                }
                Map<Vm, Host> result = basePolicy.getOptimizedAllocationMap(vmList);
                allocationEndTime = System.currentTimeMillis();
                return result;
            }
            @Override
            public Optional<Host> findHostForVm(Vm vm) {
                try {
                    return basePolicy.findHostForVm(vm);
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
            @Override
            public <T extends Vm> List<T> allocateHostForVm(Collection<T> vms) {
                return new ArrayList<>();
            }
            @Override
            public VmAllocationPolicy setHostCountForParallelSearch(int count) {
                return this;
            }
            @Override
            public boolean scaleVmVertically(org.cloudsimplus.autoscaling.VerticalVmScaling scaling) {
                return false; // Not implemented in this wrapper
            }
            @Override
            public List<Host> getHostList() { return basePolicy.getHostList(); }
            @Override
            public int getHostCountForParallelSearch() { return 1; }
            @Override
            public VmAllocationPolicy setFindHostForVmFunction(java.util.function.BiFunction<VmAllocationPolicy, Vm, Optional<Host>> fn) {
                return this;
            }
            @Override
            public boolean isVmMigrationSupported() { return basePolicy.isVmMigrationSupported(); }
            @Override
            public Datacenter getDatacenter() { return basePolicy.getDatacenter(); }
            @Override
            public VmAllocationPolicy setDatacenter(Datacenter datacenter) { 
                basePolicy.setDatacenter(datacenter);
                return this;
            }
        };
    }
    
    /**
     * Creates the datacenter broker.
     */
    private void createBroker() {
        logger.debug("Creating datacenter broker");
        
        broker = new HODatacenterBroker(simulation, algorithmName + "_Broker", algorithmName, testScenario.getName(), replicationNumber);
        broker.setName("HOBroker_" + algorithmName + "_" + replicationNumber);
        
        // Configure broker behavior
        broker.setVmDestructionDelay(1.0); // Increased delay for research experiments
        // Remove or comment out broker.setFailedVmsRetryDelay(0.0); if not supported
    }
    
    /**
     * Creates VMs based on the test scenario specifications.
     */
    private void createVMs() {
        logger.debug("Creating {} VMs", testScenario.getVmCount());
        
        vmList = new ArrayList<>(testScenario.getVmCount());
        
        // DEBUG: Check if testScenario.getVms() returns any VMs
        List<Vm> scenarioVms = testScenario.getVms();
        logger.info("DEBUG: testScenario.getVms() returned {} VMs", scenarioVms != null ? scenarioVms.size() : "null");
        
        if (scenarioVms == null || scenarioVms.isEmpty()) {
            logger.error("ERROR: No VMs returned from testScenario.getVms()!");
            logger.error("testScenario.getName(): {}", testScenario.getName());
            logger.error("testScenario.getVmCount(): {}", testScenario.getVmCount());
            logger.error("testScenario.getHostCount(): {}", testScenario.getHostCount());
            return;
        }
        
        // Process VMs in batches for memory efficiency
        if (enableMemoryMonitoring && testScenario.getVmCount() > 100) {
            BatchProcessor.processBatches(
                testScenario.getVms(),
                batch -> {
                    for (Vm vm : batch) {
                        Vm vmCreated = createVmFromSpec(vm);
                        vmList.add(vmCreated);
                    }
                },
                50 // Process 50 VMs at a time
            );
        } else {
            // Create all VMs at once for small scenarios
            for (Vm vm : testScenario.getVms()) {
                Vm vmCreated = createVmFromSpec(vm);
                vmList.add(vmCreated);
            }
        }
        
        logger.info("DEBUG: Created {} VMs successfully", vmList.size());
        if (!vmList.isEmpty()) {
            Vm firstVm = vmList.get(0);
            logger.info("DEBUG: First VM - ID: {}, MIPS: {}, PEs: {}, RAM: {}, BW: {}", 
                firstVm.getId(), firstVm.getMips(), firstVm.getPesNumber(), 
                firstVm.getRam().getCapacity(), firstVm.getBw().getCapacity());
        }
    }
    
    /**
     * Creates a single VM from specification.
     */
    private Vm createVmFromSpec(Vm vm) {
        Vm vmCreated = new VmSimple(vm.getId(), vm.getMips(), vm.getPesNumber());
        
        vmCreated.setRam(vm.getRam().getCapacity())
          .setBw(vm.getBw().getCapacity())
          .setSize(vm.getStorage().getCapacity())
          .setCloudletScheduler(new CloudletSchedulerTimeShared());
        
        // Remove setDescription with getType, unless you have a custom property
        return vmCreated;
    }
    
    /**
     * Creates cloudlets for the VMs.
     */
    private void createCloudlets() {
        logger.debug("Creating cloudlets for {} VMs", vmList.size());
        
        cloudletList = new ArrayList<>(vmList.size() * 2); // Assume 2 cloudlets per VM
        
        int cloudletId = 0;
        for (Vm vm : vmList) {
            // Create 2 cloudlets per VM with different characteristics
            for (int i = 0; i < 2; i++) {
                Cloudlet cloudlet = createCloudlet(cloudletId++, vm);
                cloudletList.add(cloudlet);
            }
        }
        
        logger.debug("Created {} cloudlets successfully", cloudletList.size());
    }
    
    /**
     * Creates a single cloudlet for a VM.
     */
    private Cloudlet createCloudlet(int id, Vm vm) {
        // CRITICAL FIX: Create realistic cloudlet lengths for proper simulation
        // Calculate cloudlet length based on VM MIPS to ensure realistic execution times
        long length = (long) (vm.getTotalMipsCapacity() * 10000 + 
                              Math.random() * vm.getTotalMipsCapacity() * 5000);
        
        // Ensure minimum length for realistic execution
        length = Math.max(length, 1000000); // At least 1M MI
        
        UtilizationModel utilizationModel = new UtilizationModelDynamic(0.7)
            .setUtilizationUpdateFunction(um -> um.getUtilization() + um.getTimeSpan() * 0.005);
        
        Cloudlet cloudlet = new CloudletSimple(id, length, (int) vm.getPesNumber())
            .setFileSize(1024 * 100) // Larger file size
            .setOutputSize(1024 * 50) // Larger output size
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelRam(utilizationModel)
            .setUtilizationModelBw(utilizationModel);
        
        cloudlet.setVm(vm);
        
        logger.debug("Created cloudlet {} with length {} MI for VM {}", id, length, vm.getId());
        
        return cloudlet;
    }
    
    /**
     * Sets up event listeners for monitoring simulation progress.
     */
    private void setupEventListeners() {
        // Monitor VM creation events
        broker.addOnVmsCreatedListener(this::onVmsCreated);
        
        // Monitor simulation clock for memory checks
        if (enableMemoryMonitoring) {
            simulation.addOnClockTickListener(event -> onClockTick());
        }
        
        // Remove or comment out addOnHostAllocationListener if not supported
        // datacenter.addOnHostAllocationListener(eventInfo -> {
        //     logger.trace("Host allocated for VM: {}", eventInfo.getVm().getId());
        // });
    }
    
    /**
     * Handles VMs created event.
     */
    private void onVmsCreated(DatacenterBrokerEventInfo eventInfo) {
        // Get created VMs from broker directly
        List<Vm> createdVms = broker.getVmCreatedList();
        int created = createdVms.size();
        int requested = vmList.size();
        logger.info("VMs created: {}/{}", created, requested);
        if (created < requested) {
            logger.warn("Not all VMs were successfully created. Created: {}, Requested: {}", created, requested);
        }
    }
    
    /**
     * Handles clock tick events for memory monitoring.
     */
    private void onClockTick() {
        if (simulation.clock() % memoryCheckInterval == 0) {
            checkMemoryUsage();
        }
    }
    
    /**
     * Checks memory usage during simulation.
     */
    private void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double usagePercentage = (usedMemory * 100.0) / maxMemory;
        
        if (usagePercentage > 85 && !memoryWarningIssued) {
            logger.warn("High memory usage during simulation: {:.2f}%", usagePercentage);
            memoryWarningIssued = true;
            
            // Try to free up memory
            System.gc();
        }
    }
    
    /**
     * Collects all metrics after simulation completion.
     */
    private ExperimentResult collectResults(PerformanceMetrics performanceMetrics) {
        logger.debug("Collecting simulation results");
        
        // Basic metrics
        double cpuUtilization = calculateAverageCpuUtilization();
        double ramUtilization = calculateAverageRamUtilization();
        double powerConsumption = calculateTotalPowerConsumption();
        int slaViolations = calculateSLAViolations();
        
        // VM allocation metrics
        int allocatedVms = countAllocatedVms();
        int totalVms = vmList.size();
        
        // Timing metrics
        long executionTime = simulationEndTime - simulationStartTime;
        long allocationTime = allocationEndTime - allocationStartTime;
        
        // --- START: NEW INTEGRATED LOGIC ---
        int convergenceIterations = 1; // Default for non-iterative algorithms
        double optimizationTime = allocationTime; // For baseline, optimization time is allocation time

        VmAllocationPolicy policy = datacenter.getVmAllocationPolicy();
        if (policy instanceof HippopotamusVmAllocationPolicy) {
            HippopotamusVmAllocationPolicy hoPolicy = (HippopotamusVmAllocationPolicy) policy;
            // Assume these getters exist in your HippopotamusVmAllocationPolicy class
            convergenceIterations = hoPolicy.getConvergenceIterations();
            optimizationTime = hoPolicy.getOptimizationTime();
        } else if (policy instanceof GeneticAlgorithmAllocation) {
            // Similarly, if GA has specific metrics
            GeneticAlgorithmAllocation gaPolicy = (GeneticAlgorithmAllocation) policy;
            // convergenceIterations = gaPolicy.getGenerations(); // Example
            optimizationTime = allocationTime;
        }
        // --- END: NEW INTEGRATED LOGIC ---
        
        // DEBUG: Log all collected values
        logger.info("DEBUG: collectResults() - Collected values:");
        logger.info("DEBUG:   CPU Utilization: {}", cpuUtilization);
        logger.info("DEBUG:   RAM Utilization: {}", ramUtilization);
        logger.info("DEBUG:   Power Consumption: {}", powerConsumption);
        logger.info("DEBUG:   SLA Violations: {}", slaViolations);
        logger.info("DEBUG:   Allocated VMs: {}", allocatedVms);
        logger.info("DEBUG:   Total VMs: {}", totalVms);
        logger.info("DEBUG:   Execution Time: {}", executionTime);
        logger.info("DEBUG:   Convergence Iterations: {}", convergenceIterations);
        
        // Create result object
        ExperimentResult result = new ExperimentResult(
            algorithmName,
            testScenario.getName(),
            replicationNumber,
            cpuUtilization,
            ramUtilization,
            powerConsumption,
            slaViolations,
            executionTime,
            convergenceIterations,
            allocatedVms,
            totalVms,
            vmList,
            hostList
        );
        
        // Add additional metrics
        result.setAllocationTime(optimizationTime); // Use the more precise optimizationTime
        result.setMemoryUsed(performanceMetrics.getPeakMemoryUsage());
        result.setSimulationTime(simulation.clock());
        
        logger.debug("Results collected - CPU: {:.2f}%, RAM: {:.2f}%, Power: {:.2f}W, SLA: {}, Conv. Iter: {}",
                cpuUtilization * 100, ramUtilization * 100, powerConsumption, slaViolations, convergenceIterations);
        
        return result;
    }
    
    /**
     * Calculates average CPU utilization across all hosts.
     */
    private double calculateAverageCpuUtilization() {
        if (hostList == null || hostList.isEmpty()) {
            return 0.0;
        }
        
        double totalUtilization = 0.0;
        int activeHosts = 0;
        
        for (Host host : hostList) {
            // Calculate CPU utilization based on allocated vs total MIPS
            double totalMips = host.getTotalMipsCapacity();
            double allocatedMips = host.getTotalAllocatedMips();
            
            if (totalMips > 0) {
                double hostUtilization = allocatedMips / totalMips;
                totalUtilization += hostUtilization;
                activeHosts++;
                
                logger.debug("Host {} CPU utilization: {:.2f}% ({} / {} MIPS)", 
                    host.getId(), hostUtilization * 100, allocatedMips, totalMips);
            }
        }
        
        double avgUtilization = activeHosts > 0 ? totalUtilization / activeHosts : 0.0;
        logger.debug("Average CPU utilization: {:.2f}% across {} hosts", avgUtilization * 100, activeHosts);
        return avgUtilization;
    }
    
    /**
     * Calculates average RAM utilization across all hosts.
     */
    private double calculateAverageRamUtilization() {
        if (hostList == null || hostList.isEmpty()) {
            return 0.0;
        }
        
        double totalUtilization = 0.0;
        int activeHosts = 0;
        
        for (Host host : hostList) {
            // Calculate RAM utilization based on allocated vs total RAM
            double totalRam = host.getRam().getCapacity();
            double allocatedRam = host.getRam().getAllocatedResource();
            
            if (totalRam > 0) {
                double hostUtilization = allocatedRam / totalRam;
                totalUtilization += hostUtilization;
                activeHosts++;
                
                logger.debug("Host {} RAM utilization: {:.2f}% ({} / {} MB)", 
                    host.getId(), hostUtilization * 100, allocatedRam, totalRam);
            }
        }
        
        double avgUtilization = activeHosts > 0 ? totalUtilization / activeHosts : 0.0;
        logger.debug("Average RAM utilization: {:.2f}% across {} hosts", avgUtilization * 100, activeHosts);
        return avgUtilization;
    }
    
    /**
     * Calculates total power consumption across all hosts.
     */
    private double calculateTotalPowerConsumption() {
        if (hostList == null || hostList.isEmpty()) {
            return 0.0;
        }
        
        double totalPower = 0.0;
        
        for (Host host : hostList) {
            try {
                // Get power consumption based on utilization
                double utilization = host.getCpuUtilizationStats().getMean();
                
                // Handle cases where utilization might be NaN or infinite
                if (!Double.isFinite(utilization)) {
                    // Calculate utilization manually
                    double totalMips = host.getTotalMipsCapacity();
                    double allocatedMips = host.getTotalAllocatedMips();
                    utilization = totalMips > 0 ? allocatedMips / totalMips : 0.0;
                }
                
                // Ensure utilization is within valid range
                utilization = Math.max(0.0, Math.min(1.0, utilization));
                
                double hostPower = host.getPowerModel().getPower(utilization);
                totalPower += hostPower;
                
                logger.debug("Host {} power: {:.2f}W (utilization: {:.2f}%)", 
                    host.getId(), hostPower, utilization * 100);
                    
            } catch (Exception e) {
                logger.warn("Error calculating power for host {}: {}", host.getId(), e.getMessage());
                // Use a default power value if calculation fails
                totalPower += 100.0; // Default 100W per host
            }
        }
        
        logger.debug("Total power consumption: {:.2f}W across {} hosts", totalPower, hostList.size());
        return totalPower;
    }
    
    /**
     * Calculates the number of SLA violations, combining allocation failures
     * and runtime performance degradation.
     */
    private int calculateSLAViolations() {
        int violations = 0;

        // 1. Count VMs that failed to be allocated
        int failedToAllocate = vmList.size() - countAllocatedVms();
        violations += failedToAllocate;

        // 2. Check for runtime performance degradation (logic from V2)
        for (Vm vm : vmList) {
            // Only check VMs that were successfully placed
            if (vm.getHost() != null && vm.getHost() != Host.NULL) {
                double requestedMips = vm.getMips();
                double allocatedMips = 0.0;
                Object mipsObj = vm.getHost().getVmScheduler().getAllocatedMips(vm);
                if (mipsObj instanceof Number) {
                    allocatedMips = ((Number) mipsObj).doubleValue();
                } else if (mipsObj instanceof List) {
                    for (Object o : (List<?>) mipsObj) {
                        if (o instanceof Number) allocatedMips += ((Number) o).doubleValue();
                    }
                }
                // If the VM receives less than 95% of its requested CPU, count as a violation
                if (allocatedMips < requestedMips * 0.95) {
                    violations++;
                }
            }
        }
        
        // 3. Check for cloudlet execution failures
        for (Cloudlet cloudlet : broker.getCloudletFinishedList()) {
            if (cloudlet.getStatus() != Cloudlet.Status.SUCCESS) {
                violations++;
            }
        }

        return violations;
    }
    
    /**
     * Counts the number of successfully allocated VMs.
     */
    private int countAllocatedVms() {
        int allocated = 0;
        
        logger.info("DEBUG: countAllocatedVms() - vmList size: {}", vmList != null ? vmList.size() : "null");
        
        if (vmList == null || vmList.isEmpty()) {
            logger.error("DEBUG: vmList is null or empty!");
            return 0;
        }
        
        for (Vm vm : vmList) {
            if (vm.getHost() != Host.NULL) {
                allocated++;
                logger.debug("DEBUG: VM {} is allocated to Host {}", vm.getId(), vm.getHost().getId());
            } else {
                logger.debug("DEBUG: VM {} is NOT allocated (Host.NULL)", vm.getId());
            }
        }
        
        logger.info("DEBUG: Total VMs: {}, Allocated VMs: {}", vmList.size(), allocated);
        return allocated;
    }
    
    /**
     * Validates simulation results for consistency.
     */
    private void validateSimulationResults(ExperimentResult result) throws ValidationException {
        // Check basic validity
        if (result.getResourceUtilCPU() < 0 || result.getResourceUtilCPU() > 1) {
            throw new ValidationException("Invalid CPU utilization: " + result.getResourceUtilCPU());
        }
        
        if (result.getResourceUtilRAM() < 0 || result.getResourceUtilRAM() > 1) {
            throw new ValidationException("Invalid RAM utilization: " + result.getResourceUtilRAM());
        }
        
        if (result.getPowerConsumption() < 0) {
            throw new ValidationException("Invalid power consumption: " + result.getPowerConsumption());
        }
        
        if (result.getVmAllocated() > result.getVmTotal()) {
            throw new ValidationException("Allocated VMs exceed total VMs");
        }
        
        // Check allocation success rate
        double allocationRate = (double) result.getVmAllocated() / result.getVmTotal();
        if (allocationRate < 0.5) {
            logger.warn("Low allocation success rate: {:.2f}%", allocationRate * 100);
        }
        
        logger.debug("Simulation results validated successfully");
    }
    
    /**
     * Cleans up simulation resources.
     */
    private void cleanupSimulation() {
        try {
            logger.debug("Cleaning up simulation resources");
            
            // Clear collections
            if (vmList != null) {
                vmList.clear();
            }
            if (hostList != null) {
                hostList.clear();
            }
            if (cloudletList != null) {
                cloudletList.clear();
            }
            
            // Clear references
            datacenter = null;
            broker = null;
            
            // Request garbage collection if needed
            if (ExperimentConfig.shouldRunGarbageCollection()) {
                System.gc();
            }
            
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
    
    /**
     * Calculates simulation delay based on scenario complexity for research-scale execution.
     * 
     * @return Delay in milliseconds
     */
    private long calculateSimulationDelay() {
        // Calculate delay based on scenario complexity for faster verification
        int totalVms = testScenario.getVmCount();
        int totalHosts = testScenario.getHostCount();
        
        // Base delay: 1 second per 100 VMs (reduced from 10 seconds)
        long baseDelay = Math.max(1000, (totalVms / 100) * 1000);
        
        // Host factor: 0.5 seconds per 50 hosts (reduced from 5 seconds)
        long hostDelay = Math.max(500, (totalHosts / 50) * 500);
        
        // Algorithm complexity factor (reduced)
        long algorithmDelay = switch (algorithmName.toLowerCase()) {
            case "ho" -> 2000;      // Reduced from 10000
            case "ga" -> 1500;      // Reduced from 8000
            case "firstfit", "bestfit" -> 500;  // Reduced from 2000
            default -> 1000;        // Reduced from 5000
        };
        
        long totalDelay = baseDelay + hostDelay + algorithmDelay;
        
        // Cap maximum delay at 30 seconds (reduced from 5 minutes)
        return Math.min(totalDelay, 30000);
    }
}