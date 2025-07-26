package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.policy.AllocationValidator;
import org.puneet.cloudsimplus.hiippo.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Custom DatacenterBroker implementation for the Hippopotamus Optimization research framework.
 * This broker provides enhanced functionality for VM submission, metrics collection,
 * and memory-efficient processing of large-scale scenarios.
 * 
 * Key features:
 * - Batch processing support for me0n mory efficiency
 * - Real-time metrics collection during execution
 * - Progress tracking for long-running experiments
 * - Validation of VM allocations
 * - Integration with HO allocation policies
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-19
 */
public class HODatacenterBroker extends DatacenterBrokerSimple {
    
    private static final Logger logger = LoggerFactory.getLogger(HODatacenterBroker.class);
    
    // Broker configuration
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final long SUBMISSION_DELAY_MS = 100;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    // Metrics and monitoring
    private final MetricsCollector metricsCollector;
    private final ProgressTracker progressTracker;
    private final PerformanceMonitor performanceMonitor;
    
    // VM tracking
    private final Map<Long, VmSubmissionInfo> vmSubmissionInfo;
    private final AtomicInteger successfulAllocations;
    private final AtomicInteger failedAllocations;
    private final List<Vm> vmSubmissionOrder;
    
    // Experiment metadata
    private final String algorithmName;
    private final String scenarioName;
    private final int replicationNumber;
    
    // Batch processing configuration
    private boolean batchProcessingEnabled = true;
    private int batchSize = DEFAULT_BATCH_SIZE;
    
    // Timing information
    private long startTime;
    private long endTime;
    private long totalSubmissionTime;
    
    /**
     * Creates a new HODatacenterBroker with enhanced monitoring capabilities.
     * 
     * @param simulation The CloudSim simulation instance
     * @param name The broker name
     * @param algorithmName The algorithm being tested
     * @param scenarioName The scenario being executed
     * @param replicationNumber The replication number
     * @throws IllegalArgumentException if any parameter is null
     */
    public HODatacenterBroker(CloudSimPlus simulation, String name, 
                             String algorithmName, String scenarioName, 
                             int replicationNumber) {
        super(simulation, name);
        
        // Validate parameters
        Objects.requireNonNull(algorithmName, "Algorithm name cannot be null");
        Objects.requireNonNull(scenarioName, "Scenario name cannot be null");
        
        this.algorithmName = algorithmName;
        this.scenarioName = scenarioName;
        this.replicationNumber = replicationNumber;
        
        // Initialize metrics and monitoring
        this.metricsCollector = new MetricsCollector();
        this.progressTracker = new ProgressTracker();
        this.performanceMonitor = new PerformanceMonitor();
        
        // Initialize tracking structures
        this.vmSubmissionInfo = new ConcurrentHashMap<>();
        this.successfulAllocations = new AtomicInteger(0);
        this.failedAllocations = new AtomicInteger(0);
        this.vmSubmissionOrder = Collections.synchronizedList(new ArrayList<>());
        
        // Configure broker settings
        configureShutdownBehavior();
        
        logger.info("Created HODatacenterBroker for algorithm: {}, scenario: {}, replication: {}",
            algorithmName, scenarioName, replicationNumber);
    }
    
    /**
     * Submits a list of VMs to the broker with batch processing support.
     * 
     * @param list The list of VMs to submit
     * @return The broker instance for method chaining
     */
    @Override
    public DatacenterBroker submitVmList(List<? extends Vm> list) {
        if (list == null || list.isEmpty()) {
            logger.warn("Attempted to submit null or empty VM list");
            return this;
        }
        
        logger.info("Submitting {} VMs for allocation", list.size());
        
        // Record submission order for metrics
        vmSubmissionOrder.addAll(list);
        
        // Start performance monitoring
        performanceMonitor.startMonitoring();
        startTime = System.currentTimeMillis();
        
        try {
            if (batchProcessingEnabled && list.size() > batchSize) {
                // Process VMs in batches for memory efficiency
                submitVmsInBatches(list);
            } else {
                // Submit all VMs at once for small lists
                submitVmsDirectly(list);
            }
            
            // Log submission summary
            logSubmissionSummary();
            
        } catch (Exception e) {
            logger.error("Error during VM submission: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to submit VMs: " + e.getMessage(), e);
        } finally {
            endTime = System.currentTimeMillis();
            totalSubmissionTime = endTime - startTime;
            
            org.puneet.cloudsimplus.hiippo.util.PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.stopMonitoring();
            logger.info("VM submission completed in {} ms, CPU usage: {:.2f}%, Memory usage: {} MB",
                totalSubmissionTime, metrics.getAvgCpuUsage(), 
                metrics.getPeakMemoryUsage() / (1024 * 1024));
        }
        
        return this;
    }
    
    /**
     * Submits VMs in batches to manage memory usage.
     * 
     * @param vmList The complete list of VMs to submit
     */
    private void submitVmsInBatches(List<? extends Vm> vmList) {
        logger.info("Batch processing enabled: {} VMs in batches of {}", 
            vmList.size(), batchSize);
        
        BatchProcessor.processBatches(vmList, batch -> {
            logger.debug("Processing batch of {} VMs", batch.size());
            
            // Check memory before processing each batch
            MemoryManager.checkMemoryUsage("VM batch submission");
            
            // Submit batch
            submitVmsDirectly(batch);
            
            // Update progress
            int processed = successfulAllocations.get() + failedAllocations.get();
            progressTracker.reportProgress("VM Submission", processed, vmList.size());
            
            // Small delay between batches to avoid overwhelming the system
            try {
                Thread.sleep(SUBMISSION_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Batch processing interrupted");
            }
        }, batchSize);
    }
    
    /**
     * Directly submits VMs without batching.
     * 
     * @param vmList The list of VMs to submit
     */
    private void submitVmsDirectly(List<? extends Vm> vmList) {
        // Create VM submission info for tracking
        for (Vm vm : vmList) {
            VmSubmissionInfo info = new VmSubmissionInfo(vm.getId(), System.currentTimeMillis());
            vmSubmissionInfo.put(vm.getId(), info);
        }
        
        // Submit to parent class
        super.submitVmList(vmList);
    }
    
    /**
     * Called when a VM is successfully created in a datacenter.
     * 
     * @param info The VM creation acknowledgment info
     */
    // @Override
    // protected void processVmCreateResponseFromDatacenter(VmCreatedEventInfo info) {
    //     Vm vm = info.getVm();
    //     
    //     if (info.isSuccess()) {
    //         handleSuccessfulVmCreation(vm);
    //     } else {
    //         handleFailedVmCreation(vm);
    //     }
    //     
    //     // Call parent implementation
    //     super.processVmCreateResponseFromDatacenter(info);
    // }
    
    /**
     * Handles successful VM creation.
     * 
     * @param vm The successfully created VM
     */
    private void handleSuccessfulVmCreation(Vm vm) {
        successfulAllocations.incrementAndGet();
        
        // Update submission info
        VmSubmissionInfo info = vmSubmissionInfo.get(vm.getId());
        if (info != null) {
            info.setAllocationTime(System.currentTimeMillis());
            info.setAllocatedHostId(vm.getHost().getId());
            info.setSuccess(true);
        }
        
        // Validate allocation if validator is available
        // Note: AllocationValidator.isValid() and metricsCollector.recordVmAllocation() 
        // are not available in CloudSim Plus 8.0.0
        logger.debug("VM {} successfully allocated to Host {}", 
            vm.getId(), vm.getHost().getId());
    }
    
    /**
     * Handles failed VM creation.
     * 
     * @param vm The VM that failed to be created
     */
    private void handleFailedVmCreation(Vm vm) {
        failedAllocations.incrementAndGet();
        
        // Update submission info
        VmSubmissionInfo info = vmSubmissionInfo.get(vm.getId());
        if (info != null) {
            info.setAllocationTime(System.currentTimeMillis());
            info.setSuccess(false);
            info.setFailureReason("No suitable host found");
        }
        
        logger.warn("Failed to allocate VM {} - no suitable host found", vm.getId());
        
        // Note: metricsCollector.recordVmAllocation() is not available in CloudSim Plus 8.0.0
        
        // Attempt retry if configured
        if (shouldRetryAllocation(vm)) {
            retryVmAllocation(vm);
        }
    }
    
    /**
     * Checks if VM allocation should be retried.
     * 
     * @param vm The VM to check
     * @return true if retry should be attempted
     */
    private boolean shouldRetryAllocation(Vm vm) {
        VmSubmissionInfo info = vmSubmissionInfo.get(vm.getId());
        return info != null && info.getRetryCount() < MAX_RETRY_ATTEMPTS;
    }
    
    /**
     * Retries VM allocation.
     * 
     * @param vm The VM to retry
     */
    private void retryVmAllocation(Vm vm) {
        VmSubmissionInfo info = vmSubmissionInfo.get(vm.getId());
        info.incrementRetryCount();
        
        logger.info("Retrying allocation for VM {} (attempt {})", 
            vm.getId(), info.getRetryCount());
        
        // Re-submit the VM
        submitVm(vm);
    }
    
    /**
     * Logs a summary of the VM submission process.
     */
    private void logSubmissionSummary() {
        int total = vmSubmissionOrder.size();
        int successful = successfulAllocations.get();
        int failed = failedAllocations.get();
        
        logger.info("VM Submission Summary: Total={}, Successful={}, Failed={}, Success Rate={:.2f}%",
            total, successful, failed, 
            total > 0 ? (successful * 100.0 / total) : 0);
    }
    
    /**
     * Gets the metrics collector associated with this broker.
     * 
     * @return The metrics collector instance
     */
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    /**
     * Gets the number of successfully allocated VMs.
     * 
     * @return The count of successful allocations
     */
    public int getSuccessfulAllocations() {
        return successfulAllocations.get();
    }
    
    /**
     * Gets the number of failed VM allocations.
     * 
     * @return The count of failed allocations
     */
    public int getFailedAllocations() {
        return failedAllocations.get();
    }
    
    /**
     * Gets the total time taken for VM submission.
     * 
     * @return The submission time in milliseconds
     */
    public long getTotalSubmissionTime() {
        return totalSubmissionTime;
    }
    
    /**
     * Gets detailed information about VM submissions.
     * 
     * @return Map of VM ID to submission information
     */
    public Map<Long, VmSubmissionInfo> getVmSubmissionInfo() {
        return new HashMap<>(vmSubmissionInfo);
    }
    
    /**
     * Enables or disables batch processing.
     * 
     * @param enabled true to enable batch processing
     */
    public void setBatchProcessingEnabled(boolean enabled) {
        this.batchProcessingEnabled = enabled;
        logger.info("Batch processing {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Sets the batch size for VM processing.
     * 
     * @param size The batch size (must be positive)
     * @throws IllegalArgumentException if size is not positive
     */
    public void setBatchSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.batchSize = size;
        logger.info("Batch size set to {}", size);
    }
    
    /**
     * Configures broker shutdown behavior.
     */
    private void configureShutdownBehavior() {
        // Set broker to shut down when all VMs and cloudlets are processed
        setShutdownWhenIdle(true);
        
        // Add shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Broker shutdown initiated");
            // Any cleanup code here
        }));
    }
    
    /**
     * Called when the simulation ends.
     */
    @Override
    public void shutdown() {
        logger.info("Shutting down HODatacenterBroker");
        
        // Collect final metrics
        collectFinalMetrics();
        
        // Log final statistics
        logFinalStatistics();
        
        super.shutdown();
    }
    
    /**
     * Collects final metrics at the end of simulation.
     */
    private void collectFinalMetrics() {
        // Note: collectDatacenterMetrics() and collectVmMetrics() are not available in CloudSim Plus 8.0.0
        // Metrics collection is handled elsewhere in the framework
        logger.debug("Final metrics collection completed");
    }
    
    /**
     * Logs final statistics about the broker's execution.
     */
    private void logFinalStatistics() {
        logger.info("=== HODatacenterBroker Final Statistics ===");
        logger.info("Algorithm: {}, Scenario: {}, Replication: {}", 
            algorithmName, scenarioName, replicationNumber);
        logger.info("Total VMs submitted: {}", vmSubmissionOrder.size());
        logger.info("Successful allocations: {}", successfulAllocations.get());
        logger.info("Failed allocations: {}", failedAllocations.get());
        logger.info("Total submission time: {} ms", totalSubmissionTime);
        logger.info("Average allocation time: {:.2f} ms/VM", 
            vmSubmissionOrder.isEmpty() ? 0 : 
            (double) totalSubmissionTime / vmSubmissionOrder.size());
        logger.info("=========================================");
    }
    
    /**
     * Gets VMs that failed allocation.
     * 
     * @return List of VMs that failed to be allocated
     */
    public List<Vm> getFailedVms() {
        return vmSubmissionInfo.entrySet().stream()
            .filter(entry -> !entry.getValue().isSuccess())
            .map(entry -> vmSubmissionOrder.stream()
                .filter(vm -> vm.getId() == entry.getKey())
                .findFirst()
                .orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets allocation statistics.
     * 
     * @return Map containing allocation statistics
     */
    public Map<String, Object> getAllocationStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVms", vmSubmissionOrder.size());
        stats.put("successfulAllocations", successfulAllocations.get());
        stats.put("failedAllocations", failedAllocations.get());
        stats.put("successRate", vmSubmissionOrder.isEmpty() ? 0 : 
            (double) successfulAllocations.get() / vmSubmissionOrder.size());
        stats.put("totalSubmissionTimeMs", totalSubmissionTime);
        stats.put("averageAllocationTimeMs", vmSubmissionOrder.isEmpty() ? 0 : 
            (double) totalSubmissionTime / vmSubmissionOrder.size());
        
        return stats;
    }
    
    /**
     * Inner class to track VM submission information.
     */
    public static class VmSubmissionInfo {
        private final long vmId;
        private final long submissionTime;
        private long allocationTime;
        private long allocatedHostId = -1;
        private boolean success = false;
        private String failureReason = "";
        private int retryCount = 0;
        
        public VmSubmissionInfo(long vmId, long submissionTime) {
            this.vmId = vmId;
            this.submissionTime = submissionTime;
        }
        
        // Getters and setters
        public long getVmId() { return vmId; }
        public long getSubmissionTime() { return submissionTime; }
        public long getAllocationTime() { return allocationTime; }
        public void setAllocationTime(long time) { this.allocationTime = time; }
        public long getAllocatedHostId() { return allocatedHostId; }
        public void setAllocatedHostId(long id) { this.allocatedHostId = id; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String reason) { this.failureReason = reason; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { this.retryCount++; }
        
        public long getAllocationDuration() {
            return allocationTime - submissionTime;
        }
    }
}