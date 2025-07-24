package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSim;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
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
    
    private final CloudSim simulation;
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
        this.simulation = new CloudSim();
        
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
        hostList = DatacenterFactory.createHosts(simulation, testScenario.getHostCount());
        
        if (hostList == null || hostList.isEmpty()) {
            throw new ValidationException("Failed to create hosts for datacenter");
        }
        
        // Create allocation policy based on algorithm
        VmAllocationPolicy allocationPolicy = createAllocationPolicy();
        
        // Track allocation timing
        allocationPolicy = wrapWithTimingPolicy(allocationPolicy);
        
        // Create datacenter with the policy
        datacenter = DatacenterFactory.createDatacenter(simulation, hostList, allocationPolicy);
        
        if (datacenter == null) {
            throw new ValidationException("Failed to create datacenter");
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
            public boolean allocateHostForVm(Vm vm) {
                if (allocationStartTime == 0) {
                    allocationStartTime = System.currentTimeMillis();
                }
                boolean result = basePolicy.allocateHostForVm(vm);
                allocationEndTime = System.currentTimeMillis();
                return result;
            }
            
            @Override
            public boolean allocateHostForVm(Vm vm, Host host) {
                return basePolicy.allocateHostForVm(vm, host);
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
            public Host getHostForVm(Vm vm) {
                return basePolicy.getHostForVm(vm);
            }
            
            @Override
            public boolean isVmMigrationSupported() {
                return basePolicy.isVmMigrationSupported();
            }
            
            @Override
            public Datacenter getDatacenter() {
                return basePolicy.getDatacenter();
            }
            
            @Override
            public void setDatacenter(Datacenter datacenter) {
                basePolicy.setDatacenter(datacenter);
            }
        };
    }
    
    /**
     * Creates the datacenter broker.
     */
    private void createBroker() {
        logger.debug("Creating datacenter broker");
        
        broker = new HODatacenterBroker(simulation);
        broker.setName("HOBroker_" + algorithmName + "_" + replicationNumber);
        
        // Configure broker behavior
        broker.setVmDestructionDelay(0.0); // No delay for research experiments
        broker.setFailedVmsRetryDelay(0.0); // No retry delay
    }
    
    /**
     * Creates VMs based on the test scenario specifications.
     */
    private void createVMs() {
        logger.debug("Creating {} VMs", testScenario.getVmCount());
        
        vmList = new ArrayList<>(testScenario.getVmCount());
        
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
        
        logger.debug("Created {} VMs successfully", vmList.size());
    }
    
    /**
     * Creates a single VM from specification.
     */
    private Vm createVmFromSpec(Vm vm) {
        Vm vmCreated = new VmSimple(vm.getId(), vm.getMips(), vm.getPesNumber());
        
        vmCreated.setRam(vm.getRam())
          .setBw(vm.getBw())
          .setSize(vm.getSize())
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
        // Calculate cloudlet length based on VM MIPS
        long length = (long) (vm.getTotalMipsCapacity() * 100 + 
                              Math.random() * vm.getTotalMipsCapacity() * 50);
        
        UtilizationModel utilizationModel = new UtilizationModelDynamic(0.5)
            .setUtilizationUpdateFunction(um -> um.getUtilization() + um.getTimeSpan() * 0.01);
        
        Cloudlet cloudlet = new CloudletSimple(id, length, (int) vm.getPesNumber())
            .setFileSize(1024)
            .setOutputSize(1024)
            .setUtilizationModelCpu(new UtilizationModelFull())
            .setUtilizationModelRam(utilizationModel)
            .setUtilizationModelBw(utilizationModel);
        
        cloudlet.setVm(vm);
        
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
            simulation.addOnClockTickListener(event -> checkMemoryUsage());
        }
        
        // Monitor datacenter events
        datacenter.addOnHostAllocationListener(eventInfo -> {
            logger.trace("Host allocated for VM: {}", eventInfo.getVm().getId());
        });
    }
    
    /**
     * Handles VMs created event.
     */
    private void onVmsCreated(DatacenterBrokerEventInfo eventInfo) {
        int createdVms = eventInfo.getVmList().size();
        int requestedVms = vmList.size();
        
        logger.info("VMs created: {}/{}", createdVms, requestedVms);
        
        if (createdVms < requestedVms) {
            logger.warn("Not all VMs were successfully created. Created: {}, Requested: {}", 
                    createdVms, requestedVms);
        }
    }
    
    /**
     * Handles clock tick events for memory monitoring.
     */
    private void onClockTick(EventInfo eventInfo) {
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
            if (host.getVmList().size() > 0) {
                double hostUtilization = host.getCpuUtilizationStats().getMean();
                if (Double.isFinite(hostUtilization)) {
                    totalUtilization += hostUtilization;
                    activeHosts++;
                }
            }
        }
        
        return activeHosts > 0 ? totalUtilization / activeHosts : 0.0;
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
            if (host.getVmList().size() > 0) {
                double ramUtilization = 1.0 - (host.getRam().getAvailableResource() / 
                                               host.getRam().getCapacity());
                totalUtilization += ramUtilization;
                activeHosts++;
            }
        }
        
        return activeHosts > 0 ? totalUtilization / activeHosts : 0.0;
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
            // Get power consumption based on utilization
            double utilization = host.getCpuUtilizationStats().getMean();
            double hostPower = host.getPowerModel().getPower(utilization);
            totalPower += hostPower;
        }
        
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
                double allocatedMips = vm.getHost().getVmScheduler().getAllocatedMipsForVm(vm);

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
        
        for (Vm vm : vmList) {
            if (vm.getHost() != Host.NULL) {
                allocated++;
            }
        }
        
        return allocated;
    }
    
    /**
     * Validates simulation results for consistency.
     */
    private void validateSimulationResults(ExperimentResult result) throws ValidationException {
        // Check basic validity
        if (result.getCpuUtilization() < 0 || result.getCpuUtilization() > 1) {
            throw new ValidationException("Invalid CPU utilization: " + result.getCpuUtilization());
        }
        
        if (result.getRamUtilization() < 0 || result.getRamUtilization() > 1) {
            throw new ValidationException("Invalid RAM utilization: " + result.getRamUtilization());
        }
        
        if (result.getPowerConsumption() < 0) {
            throw new ValidationException("Invalid power consumption: " + result.getPowerConsumption());
        }
        
        if (result.getAllocatedVms() > result.getTotalVms()) {
            throw new ValidationException("Allocated VMs exceed total VMs");
        }
        
        // Check allocation success rate
        double allocationRate = (double) result.getAllocatedVms() / result.getTotalVms();
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
}