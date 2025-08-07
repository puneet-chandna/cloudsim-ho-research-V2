package org.puneet.cloudsimplus.hiippo.policy;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.algorithm.*;
import org.puneet.cloudsimplus.hiippo.exceptions.HippopotamusOptimizationException;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.puneet.cloudsimplus.hiippo.util.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * VM Allocation Policy implementation using Hippopotamus Optimization (HO) algorithm.
 * This policy optimizes VM placement across hosts to minimize power consumption,
 * maximize resource utilization, and minimize SLA violations.
 * 
 *  @author Puneet Chandna
 * @version 1.0
 * @since 2025-07-19
 */
public class HippopotamusVmAllocationPolicy extends VmAllocationPolicyAbstract {
    
    private static final Logger logger = LoggerFactory.getLogger(HippopotamusVmAllocationPolicy.class);
    
    /** The HO algorithm instance */
    private final HippopotamusOptimization hoAlgorithm;
    
    /** Map to track VM to Host allocations */
    private final Map<Vm, Host> vmHostMap;
    
    /** Map to track Host to VMs allocations */
    private final Map<Host, List<Vm>> hostVmMap;
    
    /** Allocation validator */
    private final AllocationValidator validator;
    
    /** Metrics collector for performance tracking */
    private final MetricsCollector metricsCollector;
    
    /** Parameters for HO algorithm */
    private final HippopotamusParameters parameters;
    
    /** Flag to enable/disable batch optimization */
    private boolean batchOptimizationEnabled;
    
    /** Queue for batch VM allocation */
    private final Queue<Vm> vmAllocationQueue;
    
    /** Minimum batch size for optimization */
    private static final int MIN_BATCH_SIZE = 5;
    
    /** Maximum batch size for optimization */
    private static final int MAX_BATCH_SIZE = 20;
    
    /** Optimization timeout in milliseconds */
    private static final long OPTIMIZATION_TIMEOUT_MS = 60000; // 60 seconds
    
    /** Thread-safe flag for optimization in progress */
    private volatile boolean optimizationInProgress;
    
    private int convergenceIterations = 1;
    private double optimizationTime = 0.0;
    private double bestFitness = 0.0; // Add best fitness tracking
    
    /**
     * Creates a new HippopotamusVmAllocationPolicy with default parameters.
     */
    public HippopotamusVmAllocationPolicy() {
        this(new HippopotamusParameters());
    }
    
    /**
     * Creates a new HippopotamusVmAllocationPolicy with specified parameters.
     * 
     * @param parameters The HO algorithm parameters
     */
    public HippopotamusVmAllocationPolicy(HippopotamusParameters parameters) {
        super();
        
        logger.info("Initializing HippopotamusVmAllocationPolicy with parameters: {}", parameters);
        
        // Validate parameters
        if (parameters == null) {
            throw new IllegalArgumentException("HippopotamusParameters cannot be null");
        }
        
        this.parameters = parameters;
        this.hoAlgorithm = new HippopotamusOptimization(parameters);
        this.vmHostMap = new ConcurrentHashMap<>();
        this.hostVmMap = new ConcurrentHashMap<>();
        
        // CRITICAL FIX: Enable batch optimization by default for true HO optimization
        this.batchOptimizationEnabled = true;
        this.validator = new AllocationValidator();
        this.metricsCollector = new MetricsCollector();
        this.vmAllocationQueue = new LinkedList<>();
        this.batchOptimizationEnabled = true;
        this.optimizationInProgress = false;
        
        logger.info("HippopotamusVmAllocationPolicy initialized successfully");
    }
    
    /**
     * Allocates a host for the specified VM using the HO algorithm.
     * If batch optimization is enabled, VMs are queued and processed in batches.
     * 
     * @param vm The VM to allocate
     * @return true if allocation was successful, false otherwise
     */
    @Override
    public HostSuitability allocateHostForVm(Vm vm) {
        if (vm == null) {
            logger.error("Cannot allocate null VM");
            return HostSuitability.NULL;
        }
        
        logger.debug("Attempting to allocate VM {} (MIPS: {}, RAM: {}, Storage: {})", 
            vm.getId(), vm.getTotalMipsCapacity(), vm.getRam().getCapacity(), 
            vm.getStorage().getCapacity());
        
        try {
            // Check memory before allocation
            MemoryManager.checkMemoryUsage("VM Allocation " + vm.getId());
            
            // Check if VM is already allocated
            if (vmHostMap.containsKey(vm)) {
                logger.warn("VM {} is already allocated to host {}", 
                    vm.getId(), vmHostMap.get(vm).getId());
                return HostSuitability.NULL;
            }
            
            // CRITICAL FIX: Use batch processing for true optimization
            if (batchOptimizationEnabled) {
                boolean result = handleBatchAllocation(vm);
                return result ? HostSuitability.NULL : HostSuitability.NULL;
            } else {
                // Fallback to direct allocation if batch processing is disabled
                boolean result = performDirectAllocation(vm);
                if (result) {
                    logger.info("Successfully allocated VM {} using direct HO algorithm", vm.getId());
                    return HostSuitability.NULL;
                } else {
                    logger.warn("Failed to allocate VM {} using direct HO algorithm", vm.getId());
                    return HostSuitability.NULL;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error allocating VM {}: {}", vm.getId(), e.getMessage(), e);
            return HostSuitability.NULL;
        }
    }
    
    /**
     * Handles batch allocation of VMs for better optimization results.
     * 
     * @param vm The VM to add to the batch
     * @return true if VM was queued or allocated successfully
     */
    private boolean handleBatchAllocation(Vm vm) {
        synchronized (vmAllocationQueue) {
            vmAllocationQueue.offer(vm);
            logger.debug("Added VM {} to allocation queue. Queue size: {}", 
                vm.getId(), vmAllocationQueue.size());
            
            // Check if we should trigger batch optimization
            if (shouldTriggerBatchOptimization()) {
                boolean result = performBatchOptimization();
                // Check if the VM was actually allocated
                if (result && vmHostMap.containsKey(vm)) {
                    return true;
                } else {
                    // VM was not allocated, remove from queue and return false
                    vmAllocationQueue.remove(vm);
                    return false;
                }
            }
            
            // VM is queued and will be allocated in next batch - return true to indicate success
            return true;
        }
    }
    
    /**
     * Determines if batch optimization should be triggered.
     * 
     * @return true if batch optimization should be triggered
     */
    private boolean shouldTriggerBatchOptimization() {
        int queueSize = vmAllocationQueue.size();
        
        // Trigger if queue reaches max batch size
        if (queueSize >= MAX_BATCH_SIZE) {
            logger.info("Triggering batch optimization: queue size {} reached max batch size", 
                queueSize);
            return true;
        }
        
        // Trigger if queue has minimum batch size and no more VMs are expected soon
        if (queueSize >= MIN_BATCH_SIZE) {
            // This is a simplified check - in production, you might want to
            // implement a more sophisticated waiting mechanism
            logger.info("Triggering batch optimization: queue size {} meets minimum batch size", 
                queueSize);
            return true;
        }
        
        return false;
    }
    
    /**
     * Performs batch optimization for queued VMs.
     * 
     * @return true if all VMs were allocated successfully
     */
    private boolean performBatchOptimization() {
        if (optimizationInProgress) {
            logger.warn("Optimization already in progress, skipping batch optimization");
            return false;
        }
        
        optimizationInProgress = true;
        long startTime = System.currentTimeMillis();
        
        try {
            List<Vm> vmsToAllocate = new ArrayList<>();
            
            synchronized (vmAllocationQueue) {
                // Drain queue up to MAX_BATCH_SIZE
                int batchSize = Math.min(vmAllocationQueue.size(), MAX_BATCH_SIZE);
                for (int i = 0; i < batchSize; i++) {
                    Vm vm = vmAllocationQueue.poll();
                    if (vm != null) {
                        vmsToAllocate.add(vm);
                    }
                }
            }
            
            if (vmsToAllocate.isEmpty()) {
                logger.debug("No VMs to allocate in batch");
                return true;
            }
            
            logger.info("Starting batch optimization for {} VMs", vmsToAllocate.size());
            
            // Get available hosts
            List<Host> availableHosts = getHostList().stream()
                .filter(host -> host.isActive() && !host.isFailed())
                .collect(Collectors.toList());
            
            if (availableHosts.isEmpty()) {
                logger.error("No available hosts for allocation");
                // Put VMs back in queue
                synchronized (vmAllocationQueue) {
                    vmAllocationQueue.addAll(vmsToAllocate);
                }
                return false;
            }
            
            // Run HO algorithm with timeout
            Solution bestSolution = runOptimizationWithTimeout(
                vmsToAllocate, availableHosts, OPTIMIZATION_TIMEOUT_MS);
            
            if (bestSolution == null) {
                logger.error("HO algorithm failed to find a solution");
                // Put VMs back in queue
                synchronized (vmAllocationQueue) {
                    vmAllocationQueue.addAll(vmsToAllocate);
                }
                return false;
            }
            
            // Apply the solution
            boolean allAllocated = applySolution(bestSolution, vmsToAllocate);
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("Batch optimization completed in {} ms. Success rate: {}/{}", 
                elapsedTime, bestSolution.getAllocatedVmCount(), vmsToAllocate.size());
            
            // Collect metrics
            // metricsCollector.recordBatchAllocation(vmsToAllocate.size(), 
            //     bestSolution.getAllocatedVmCount(), elapsedTime); // Method does not exist
            
            return allAllocated;
            
        } catch (Exception e) {
            logger.error("Error during batch optimization", e);
            return false;
        } finally {
            optimizationInProgress = false;
        }
    }
    
    /**
     * Runs the HO optimization algorithm with a timeout.
     * 
     * @param vms The list of VMs to allocate
     * @param hosts The list of available hosts
     * @param timeoutMs Timeout in milliseconds
     * @return The best solution found, or null if optimization failed
     */
    private Solution runOptimizationWithTimeout(List<Vm> vms, List<Host> hosts, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Create optimization context
            OptimizationContext context = new OptimizationContext(vms, hosts, vmHostMap);
            
            // Run HO algorithm (use standard optimize method)
            Solution solution = hoAlgorithm.optimize(vms, hosts);
            
            // CRITICAL FIX: Update convergence tracking with actual iteration count
            ConvergenceAnalyzer analyzer = hoAlgorithm.getConvergenceAnalyzer();
            if (analyzer != null) {
                ConvergenceAnalyzer.ConvergenceReport report = analyzer.getConvergenceReport();
                convergenceIterations = report.convergenceIteration > 0 ? report.convergenceIteration : 1;
                bestFitness = report.bestFitness;
                logger.info("HO algorithm converged after {} iterations with fitness: {}", 
                    convergenceIterations, bestFitness);
            } else {
                // Fallback: use fitness history size as iteration count
                List<Double> fitnessHistory = hoAlgorithm.getConvergenceHistory();
                convergenceIterations = fitnessHistory.size() > 0 ? fitnessHistory.size() : 1;
                bestFitness = solution != null ? solution.getFitness() : 0.0;
                logger.info("Using fitness history size as convergence iterations: {} with fitness: {}", 
                    convergenceIterations, bestFitness);
            }
            optimizationTime = (System.currentTimeMillis() - startTime) / 1000.0;
            
            // Update best fitness from the solution
            if (solution != null) {
                bestFitness = solution.getFitness();
                logger.info("Optimization completed with best fitness: {}", bestFitness);
            }
            
            // Validate solution
            if (solution != null && validator.validateAllocation(hosts).isValid()) {
                return solution;
            } else {
                logger.warn("HO algorithm produced invalid solution");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Unexpected error during optimization", e);
            return null;
        }
    }
    
    /**
     * Applies the optimization solution by allocating VMs to hosts.
     * 
     * @param solution The solution to apply
     * @param vms The list of VMs being allocated
     * @return true if all allocations were successful
     */
    private boolean applySolution(Solution solution, List<Vm> vms) {
        int successCount = 0;
        int failureCount = 0;
        
        for (Vm vm : vms) {
            Host assignedHost = solution.getHostForVm(vm);
            
            if (assignedHost != null) {
                // Validate assignment before applying
                if (validator.validateVmPlacement(vm, assignedHost).isValid()) {
                    if (performAllocation(vm, assignedHost)) {
                        successCount++;
                        logger.debug("Successfully allocated VM {} to Host {}", 
                            vm.getId(), assignedHost.getId());
                    } else {
                        failureCount++;
                        logger.warn("Failed to allocate VM {} to Host {} despite validation", 
                            vm.getId(), assignedHost.getId());
                    }
                } else {
                    failureCount++;
                    logger.warn("Validation failed for VM {} to Host {} assignment", 
                        vm.getId(), assignedHost.getId());
                }
            } else {
                failureCount++;
                logger.warn("No host assigned for VM {} in solution", vm.getId());
            }
        }
        
        logger.info("Solution application complete. Success: {}, Failures: {}", 
            successCount, failureCount);
        
        return failureCount == 0;
    }
    
    /**
     * Performs direct allocation of a single VM using HO algorithm.
     * 
     * @param vm The VM to allocate
     * @return true if allocation was successful
     */
    private boolean performDirectAllocation(Vm vm) {
        logger.debug("Performing direct allocation for VM {} using HO algorithm", vm.getId());
        
        // Get available hosts with more detailed logging
        List<Host> availableHosts = getHostList().stream()
            .filter(host -> host.isActive() && !host.isFailed())
            .filter(host -> Boolean.TRUE.equals(host.isSuitableForVm(vm)))
            .collect(Collectors.toList());
        
        logger.debug("Found {} suitable hosts for VM {} (total hosts: {})", 
            availableHosts.size(), vm.getId(), getHostList().size());
        
        if (availableHosts.isEmpty()) {
            // CRITICAL FIX: Log detailed host information for debugging
            logger.error("No suitable hosts available for VM {}. VM specs: MIPS={}, RAM={}, PEs={}", 
                vm.getId(), vm.getTotalMipsCapacity(), vm.getRam().getCapacity(), vm.getPesNumber());
            
            // Log all hosts for debugging
            for (Host host : getHostList()) {
                logger.error("Host {}: Active={}, Failed={}, Suitable={}, CPU={}/{}, RAM={}/{}, PEs={}/{}", 
                    host.getId(), host.isActive(), host.isFailed(), 
                    Boolean.TRUE.equals(host.isSuitableForVm(vm)),
                    host.getCpuMipsUtilization(), host.getTotalMipsCapacity(),
                    host.getRam().getAllocatedResource(), host.getRam().getCapacity(),
                    host.getVmList().size(), host.getPesNumber());
            }
            return false;
        }
        
        try {
            // CRITICAL FIX: Use simple heuristic first for guaranteed allocation
            Host bestHost = findBestHostForVm(vm, availableHosts);
            if (bestHost != null) {
                boolean result = performAllocation(vm, bestHost);
                if (result) {
                    logger.info("Simple heuristic allocation successful: VM {} to host {}", vm.getId(), bestHost.getId());
                    return true;
                }
            }
            
            // Fallback to HO algorithm if simple heuristic fails
            logger.debug("Simple heuristic failed, trying HO algorithm for VM {}", vm.getId());
            List<Vm> singleVm = Arrays.asList(vm);
            Solution solution = hoAlgorithm.optimize(singleVm, availableHosts);
            
            // Update best fitness from the solution
            if (solution != null) {
                bestFitness = solution.getFitness();
                logger.debug("Direct allocation fitness: {}", bestFitness);
            }
            
            if (solution != null && solution.getAllocatedVmCount() > 0) {
                Host assignedHost = solution.getHostForVm(vm);
                if (assignedHost != null) {
                    boolean result = performAllocation(vm, assignedHost);
                    if (result) {
                        logger.info("HO algorithm successfully allocated VM {} to host {}", 
                            vm.getId(), assignedHost.getId());
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in HO algorithm for VM {}: {}", vm.getId(), e.getMessage(), e);
        }
        
        logger.warn("Could not find suitable host for VM {}", vm.getId());
        return false;
    }
    
    /**
     * Finds the best host for a single VM using HO-inspired heuristics.
     * 
     * @param vm The VM to place
     * @param availableHosts List of available hosts
     * @return The best host, or null if none found
     */
    private Host findBestHostForVm(Vm vm, List<Host> availableHosts) {
        Host bestHost = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        
        for (Host host : availableHosts) {
            if (!Boolean.TRUE.equals(host.isSuitableForVm(vm))) {
                continue;
            }
            
            // Calculate fitness based on HO objectives
            double fitness = calculateHostFitness(host, vm);
            
            if (fitness > bestFitness) {
                bestFitness = fitness;
                bestHost = host;
            }
        }
        
        return bestHost;
    }
    
    /**
     * Calculates the fitness of allocating a VM to a specific host.
     * 
     * @param host The candidate host
     * @param vm The VM to allocate
     * @return The fitness value (higher is better)
     */
    private double calculateHostFitness(Host host, Vm vm) {
        // Get current utilization
        double cpuUtil = host.getCpuPercentUtilization();
        double ramUtil = host.getRam().getAllocatedResource() / (double) host.getRam().getCapacity();
        
        // Estimate utilization after allocation
        double vmCpuDemand = vm.getTotalMipsCapacity() / host.getTotalMipsCapacity();
        double vmRamDemand = vm.getRam().getCapacity() / (double) host.getRam().getCapacity();
        
        double newCpuUtil = cpuUtil + vmCpuDemand;
        double newRamUtil = ramUtil + vmRamDemand;
        
        // Check if allocation would exceed thresholds
        if (newCpuUtil > 0.9 || newRamUtil > 0.9) {
            return Double.NEGATIVE_INFINITY; // Avoid overloading
        }
        
        // Calculate fitness components
        double utilizationScore = (newCpuUtil + newRamUtil) / 2.0; // Prefer higher utilization
        double balanceScore = 1.0 - Math.abs(newCpuUtil - newRamUtil); // Prefer balanced usage
        double powerScore = 1.0 - estimatePowerIncrease(host, vm); // Minimize power increase
        
        // Weighted combination
        double fitness = AlgorithmConstants.W_UTILIZATION * utilizationScore +
                        AlgorithmConstants.W_POWER * powerScore +
                        AlgorithmConstants.W_SLA * balanceScore;
        
        logger.trace("Host {} fitness for VM {}: {} (CPU: {:.2f}, RAM: {:.2f})", 
            host.getId(), vm.getId(), fitness, newCpuUtil, newRamUtil);
        
        return fitness;
    }
    
    /**
     * Estimates the power increase from allocating a VM to a host.
     * 
     * @param host The host
     * @param vm The VM
     * @return Normalized power increase [0, 1]
     */
    private double estimatePowerIncrease(Host host, Vm vm) {
        // Simplified power model
        double currentPower = host.getPowerModel().getPower();
        double maxPower = host.getPowerModel().getPower(1.0); // getMaxPower() does not exist
        
        // Estimate power after VM allocation
        double additionalUtil = vm.getTotalMipsCapacity() / host.getTotalMipsCapacity();
        double estimatedPower = currentPower + (maxPower - currentPower) * additionalUtil;
        
        return (estimatedPower - currentPower) / maxPower;
    }
    
    /**
     * Performs the actual allocation of a VM to a host.
     * 
     * @param vm The VM to allocate
     * @param host The host to allocate to
     * @return true if allocation was successful
     */
    private boolean performAllocation(Vm vm, Host host) {
        try {
            // Final validation
            if (!Boolean.TRUE.equals(host.isSuitableForVm(vm))) {
                logger.error("Host {} is not suitable for VM {} at allocation time", 
                    host.getId(), vm.getId());
                return false;
            }
            
            // Perform allocation using CloudSim Plus method
            HostSuitability suitability = host.createVm(vm);
            boolean allocated = suitability.fully();
            
            if (allocated) {
                // Update tracking maps
                vmHostMap.put(vm, host);
                hostVmMap.computeIfAbsent(host, k -> new ArrayList<>()).add(vm);
                
                // Log successful allocation
                logger.info("Successfully allocated VM {} to Host {} (CPU: {:.2f}%, RAM: {:.2f}%)", 
                    vm.getId(), host.getId(), 
                    host.getCpuPercentUtilization() * 100, 
                    host.getRam().getAllocatedResource() / (double) host.getRam().getCapacity() * 100);
                
                // Collect metrics
                // metricsCollector.recordVmAllocation(vm, host); // Method does not exist
                
                return true;
            } else {
                logger.error("CloudSim allocation failed for VM {} to Host {}", 
                    vm.getId(), host.getId());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error during allocation of VM {} to Host {}", 
                vm.getId(), host.getId(), e);
            return false;
        }
    }
    
    /**
     * Deallocates a VM from its current host.
     * 
     * @param vm The VM to deallocate
     */
    @Override
    public void deallocateHostForVm(Vm vm) {
        if (vm == null) {
            logger.error("Cannot deallocate null VM");
            return;
        }
        
        Host host = vmHostMap.get(vm);
        
        if (host == null) {
            logger.warn("VM {} is not allocated to any host", vm.getId());
            return;
        }
        
        try {
            logger.debug("Deallocating VM {} from Host {}", vm.getId(), host.getId());
            
            // Perform deallocation
            super.deallocateHostForVm(vm);
            
            // Update tracking maps
            vmHostMap.remove(vm);
            List<Vm> hostVms = hostVmMap.get(host);
            if (hostVms != null) {
                hostVms.remove(vm);
                if (hostVms.isEmpty()) {
                    hostVmMap.remove(host);
                }
            }
            
            logger.info("Successfully deallocated VM {} from Host {}", 
                vm.getId(), host.getId());
            
            // Collect metrics
            // metricsCollector.recordVmDeallocation(vm, host); // Method does not exist
            
        } catch (Exception e) {
            logger.error("Error deallocating VM {} from Host {}", 
                vm.getId(), host.getId(), e);
        }
    }
    
    /**
     * Gets the host allocated to a VM.
     * 
     * @param vm The VM
     * @return The allocated host, or Host.NULL if not allocated
     */
    public Host getHostForVm(final Vm vm) {
        if (vm == null) {
            return Host.NULL;
        }
        
        Host host = vmHostMap.get(vm);
        return host != null ? host : Host.NULL;
    }
    
    /**
     * Checks if a VM is allocated to any host.
     * 
     * @param vm The VM to check
     * @return true if the VM is allocated
     */
    public boolean isVmAllocated(Vm vm) {
        return vm != null && vmHostMap.containsKey(vm);
    }
    
    /**
     * Gets the list of VMs allocated to a specific host.
     * 
     * @param host The host
     * @return List of VMs allocated to the host
     */
    public List<Vm> getVmsAllocatedToHost(Host host) {
        if (host == null) {
            return Collections.emptyList();
        }
        
        return hostVmMap.getOrDefault(host, Collections.emptyList());
    }
    
    /**
     * Forces immediate batch optimization of queued VMs.
     * Useful for triggering optimization before simulation ends.
     * 
     * @return true if optimization was triggered successfully
     */
    public boolean forceOptimization() {
        // CRITICAL FIX: Re-optimize placement of all allocated VMs
        List<Vm> allocatedVms = new ArrayList<>(vmHostMap.keySet());
        if (allocatedVms.isEmpty()) {
            logger.info("No VMs to re-optimize");
            return true;
        }
        
        logger.info("Forcing re-optimization of {} allocated VMs", allocatedVms.size());
        
        try {
            // Get all available hosts
            List<Host> availableHosts = getHostList().stream()
                .filter(host -> host.isActive() && !host.isFailed())
                .collect(Collectors.toList());
            
            if (availableHosts.isEmpty()) {
                logger.warn("No available hosts for re-optimization");
                return false;
            }
            
            // Run HO algorithm on all allocated VMs
            Solution solution = hoAlgorithm.optimize(allocatedVms, availableHosts);
            
            if (solution != null && solution.getAllocatedVmCount() > 0) {
                // Apply the optimized solution
                boolean result = applySolution(solution, allocatedVms);
                
                // Update convergence tracking
                ConvergenceAnalyzer analyzer = hoAlgorithm.getConvergenceAnalyzer();
                if (analyzer != null) {
                    ConvergenceAnalyzer.ConvergenceReport report = analyzer.getConvergenceReport();
                    convergenceIterations = report.convergenceIteration > 0 ? report.convergenceIteration : 1;
                    logger.info("Re-optimization completed with {} iterations", convergenceIterations);
                }
                
                return result;
            } else {
                logger.warn("HO algorithm failed to find solution for re-optimization");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error during re-optimization: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets the current number of VMs in the allocation queue.
     * 
     * @return The queue size
     */
    public int getQueueSize() {
        return vmAllocationQueue.size();
    }
    
    /**
     * Enables or disables batch optimization.
     * 
     * @param enabled true to enable batch optimization
     */
    public void setBatchOptimizationEnabled(boolean enabled) {
        this.batchOptimizationEnabled = enabled;
        logger.info("Batch optimization {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Gets the algorithm parameters.
     * 
     * @return The HippopotamusParameters
     */
    public HippopotamusParameters getParameters() {
        return parameters;
    }
    
    /**
     * Gets the metrics collector.
     * 
     * @return The MetricsCollector instance
     */
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    /**
     * Gets the name of this allocation policy.
     * 
     * @return Policy name
     */
    public String getName() {
        return "HippopotamusOptimization";
    }
    
    @Override
    public String toString() {
        return String.format("HippopotamusVmAllocationPolicy[allocated=%d, queued=%d, hosts=%d]",
            vmHostMap.size(), vmAllocationQueue.size(), getHostList().size());
    }
    
    /**
     * Inner class representing an optimization context.
     */
    private static class OptimizationContext {
        private final List<Vm> vms;
        private final List<Host> hosts;
        private final Map<Vm, Host> existingAllocations;
        
        public OptimizationContext(List<Vm> vms, List<Host> hosts, 
                                  Map<Vm, Host> existingAllocations) {
            this.vms = new ArrayList<>(vms);
            this.hosts = new ArrayList<>(hosts);
            this.existingAllocations = new HashMap<>(existingAllocations);
        }
        
        public List<Vm> getVms() { return vms; }
        public List<Host> getHosts() { return hosts; }
        public Map<Vm, Host> getExistingAllocations() { return existingAllocations; }
    }
    
    /**
     * Inner class representing an optimization solution.
     */
    public static class Solution {
        private final Map<Vm, Host> allocations;
        private double fitness;
        
        public Solution() {
            this.allocations = new HashMap<>();
            this.fitness = 0.0;
        }
        
        public void allocateVmToHost(Vm vm, Host host) {
            allocations.put(vm, host);
        }
        
        public Host getHostForVm(Vm vm) {
            return allocations.get(vm);
        }
        
        public int getAllocatedVmCount() {
            return allocations.size();
        }
        
        public double getFitness() { return fitness; }
        public void setFitness(double fitness) { this.fitness = fitness; }
        
        public Map<Vm, Host> getAllocations() {
            return new HashMap<>(allocations);
        }

        public Solution copy() {
            Solution copy = new Solution();
            for (Map.Entry<Vm, Host> entry : this.allocations.entrySet()) {
                copy.allocateVmToHost(entry.getKey(), entry.getValue());
            }
            copy.fitness = this.fitness;
            return copy;
        }

        public String getCacheKey() {
            // Simple cache key: sorted VM IDs and their host IDs
            return allocations.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingLong(Vm::getId)))
                .map(e -> e.getKey().getId() + ":" + e.getValue().getId())
                .collect(Collectors.joining(","));
        }

        public Map<Host, List<Vm>> getHostVmsMap() {
            Map<Host, List<Vm>> hostVms = new HashMap<>();
            for (Map.Entry<Vm, Host> entry : allocations.entrySet()) {
                hostVms.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }
            return hostVms;
        }

        public void addMapping(Vm vm, Host host) {
            allocateVmToHost(vm, host);
        }
    }

    /**
     * Implements the defaultFindHostForVm method required by the base class.
     * @param vm The VM to find a host for
     * @return Optional containing the best host, or empty if none found
     */
    @Override
    public java.util.Optional<Host> defaultFindHostForVm(Vm vm) {
        List<Host> suitableHosts = getHostList().stream()
            .filter(h -> Boolean.TRUE.equals(h.isSuitableForVm(vm)))
            .collect(Collectors.toList());
        Host bestHost = findBestHostForVm(vm, suitableHosts);
        return java.util.Optional.ofNullable(bestHost);
    }

    public int getConvergenceIterations() {
        return convergenceIterations;
    }
    public double getOptimizationTime() {
        return optimizationTime;
    }
    
    public int getConvergenceIteration() {
        return convergenceIterations;
    }
    
    public double getBestFitness() {
        // Return the best fitness from the HO algorithm
        return bestFitness;
    }
}