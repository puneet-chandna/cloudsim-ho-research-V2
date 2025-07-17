package org.puneet.cloudsimplus.hiippo.baseline;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.policy.BaselineVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Best Fit VM Allocation Policy Implementation for CloudSim Plus.
 * 
 * This class implements the Best Fit algorithm for VM placement, which selects 
 * the host that minimizes resource waste for each VM. The algorithm calculates 
 * waste based on multiple resources: CPU, RAM, Bandwidth, and Storage.
 * 
 * Features:
 * - Multi-resource waste calculation with configurable weights
 * - Performance caching with proper invalidation
 * - Comprehensive statistics collection for research
 * - Detailed logging for debugging and analysis
 * - Experimental controls for research flexibility
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-18
 */
public class BestFitAllocation extends BaselineVmAllocationPolicy {
    
    private static final Logger logger = LoggerFactory.getLogger(BestFitAllocation.class);
    
    /**
     * Default weight factors for resource waste calculation (0.0 to 1.0)
     */
    private double cpuWeight = 0.25;
    private double ramWeight = 0.25;
    private double bwWeight = 0.25;
    private double storageWeight = 0.25;
    
    /**
     * Minimum threshold for considering a host suitable (to avoid near-zero capacity hosts)
     */
    private static final double MIN_CAPACITY_THRESHOLD = 0.01;
    
    /**
     * Cache for host waste calculations to improve performance
     */
    private final Map<String, Double> wasteCache = new ConcurrentHashMap<>();
    
    /**
     * Flag to enable/disable caching (useful for experiments)
     */
    private boolean cachingEnabled = true;
    
    /**
     * Statistics tracking for algorithm performance
     */
    private final AtomicLong totalAllocations = new AtomicLong(0);
    private final AtomicLong successfulAllocations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    /**
     * Resource-specific wastage statistics
     */
    private final Map<String, Double> totalResourceWastage = new ConcurrentHashMap<>();
    private final Map<String, Long> resourceWastageCount = new ConcurrentHashMap<>();
    
    /**
     * Timing statistics
     */
    private final AtomicLong totalAllocationTime = new AtomicLong(0);
    private final AtomicLong totalHostSearchTime = new AtomicLong(0);
    
    /**
     * Default constructor for BestFitAllocation.
     */
    public BestFitAllocation() {
        super();
        initializeStatistics();
        logger.info("Initialized BestFit allocation policy with weights - CPU: {}, RAM: {}, BW: {}, Storage: {}", 
            cpuWeight, ramWeight, bwWeight, storageWeight);
    }
    
    /**
     * Constructor with custom resource weights.
     * 
     * @param cpuWeight Weight for CPU resource (0.0 to 1.0)
     * @param ramWeight Weight for RAM resource (0.0 to 1.0)
     * @param bwWeight Weight for Bandwidth resource (0.0 to 1.0)
     * @param storageWeight Weight for Storage resource (0.0 to 1.0)
     */
    public BestFitAllocation(double cpuWeight, double ramWeight, double bwWeight, double storageWeight) {
        super();
        setResourceWeights(cpuWeight, ramWeight, bwWeight, storageWeight);
        initializeStatistics();
        logger.info("Initialized BestFit allocation policy with custom weights - CPU: {}, RAM: {}, BW: {}, Storage: {}", 
            cpuWeight, ramWeight, bwWeight, storageWeight);
    }
    
    /**
     * Initializes statistics tracking structures.
     */
    private void initializeStatistics() {
        totalResourceWastage.put("cpu", 0.0);
        totalResourceWastage.put("ram", 0.0);
        totalResourceWastage.put("bw", 0.0);
        totalResourceWastage.put("storage", 0.0);
        
        resourceWastageCount.put("cpu", 0L);
        resourceWastageCount.put("ram", 0L);
        resourceWastageCount.put("bw", 0L);
        resourceWastageCount.put("storage", 0L);
    }
    
    /**
     * Sets custom resource weights for waste calculation.
     * Weights are normalized to sum to 1.0.
     * 
     * @param cpuWeight Weight for CPU resource
     * @param ramWeight Weight for RAM resource
     * @param bwWeight Weight for Bandwidth resource
     * @param storageWeight Weight for Storage resource
     */
    public void setResourceWeights(double cpuWeight, double ramWeight, double bwWeight, double storageWeight) {
        double sum = cpuWeight + ramWeight + bwWeight + storageWeight;
        if (sum <= 0) {
            throw new IllegalArgumentException("Sum of weights must be positive");
        }
        
        // Normalize weights
        this.cpuWeight = cpuWeight / sum;
        this.ramWeight = ramWeight / sum;
        this.bwWeight = bwWeight / sum;
        this.storageWeight = storageWeight / sum;
        
        logger.debug("Resource weights updated - CPU: {}, RAM: {}, BW: {}, Storage: {}", 
            this.cpuWeight, this.ramWeight, this.bwWeight, this.storageWeight);
    }
    
    /**
     * Enables or disables caching for experimental purposes.
     * 
     * @param enabled true to enable caching, false to disable
     */
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
        if (!enabled) {
            wasteCache.clear();
        }
        logger.info("Caching {} for BestFit allocation", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Allocates a host for the specified VM using the Best Fit algorithm.
     * 
     * The algorithm:
     * 1. Iterates through all available hosts
     * 2. Calculates multi-resource waste for each suitable host
     * 3. Selects the host with minimum waste
     * 4. Allocates the VM to the selected host
     * 
     * @param vm The VM to allocate
     * @return true if allocation was successful, false otherwise
     * @throws IllegalArgumentException if vm is null
     */
    @Override
    public boolean allocateHostForVm(Vm vm) {
        logger.debug("Starting BestFit allocation for VM {} (MIPS: {}, RAM: {} MB, BW: {} Mbps, Storage: {} GB)",
            vm.getId(), vm.getTotalMipsCapacity(), vm.getRam().getCapacity(),
            vm.getBw().getCapacity(), vm.getStorage().getCapacity());
        
        // Validate input
        if (vm == null) {
            logger.error("Cannot allocate null VM");
            throw new IllegalArgumentException("VM cannot be null");
        }
        
        totalAllocations.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        try {
            // Check if VM is already allocated
            if (vm.getHost() != Host.NULL) {
                logger.warn("VM {} is already allocated to Host {}", vm.getId(), vm.getHost().getId());
                return false;
            }
            
            // Find the best host
            long searchStartTime = System.currentTimeMillis();
            Host bestHost = findBestHostForVm(vm);
            totalHostSearchTime.addAndGet(System.currentTimeMillis() - searchStartTime);
            
            if (bestHost == null) {
                logger.warn("No suitable host found for VM {} - all hosts either full or unsuitable", 
                    vm.getId());
                return false;
            }
            
            // Calculate waste before allocation for statistics
            ResourceWastage wastage = calculateDetailedResourceWastage(bestHost, vm);
            
            // Attempt allocation
            boolean allocated = allocateHostForVm(vm, bestHost);
            
            if (allocated) {
                successfulAllocations.incrementAndGet();
                long allocationTime = System.currentTimeMillis() - startTime;
                totalAllocationTime.addAndGet(allocationTime);
                
                logger.info("Successfully allocated VM {} to Host {} (total waste: {:.4f}, time: {} ms)",
                    vm.getId(), bestHost.getId(), wastage.total, allocationTime);
                
                // Update resource-specific statistics
                updateResourceStatistics(wastage);
                
                // Log resource utilization after allocation
                logResourceUtilization(bestHost, vm);
                
                // Clear cache entries for this host
                if (cachingEnabled) {
                    clearCacheForHost(bestHost);
                }
            } else {
                logger.error("Failed to allocate VM {} to selected Host {} despite suitability check",
                    vm.getId(), bestHost.getId());
            }
            
            return allocated;
            
        } catch (Exception e) {
            logger.error("Unexpected error during BestFit allocation for VM {}", vm.getId(), e);
            return false;
        }
    }
    
    /**
     * Finds the best host for the given VM based on minimum resource waste.
     * 
     * @param vm The VM to place
     * @return The best host or null if no suitable host found
     */
    private Host findBestHostForVm(Vm vm) {
        Host bestHost = null;
        double minWaste = Double.MAX_VALUE;
        int hostsChecked = 0;
        int suitableHosts = 0;
        
        List<Host> hostList = getHostList();
        logger.debug("Searching among {} hosts for VM {}", hostList.size(), vm.getId());
        
        for (Host host : hostList) {
            hostsChecked++;
            
            // Skip if host is not suitable
            if (!isHostSuitableForVm(host, vm)) {
                logger.trace("Host {} not suitable for VM {} (MIPS available: {}, RAM available: {})",
                    host.getId(), vm.getId(), 
                    host.getMips() - host.getTotalAllocatedMips(),
                    host.getRam().getAvailableResource());
                continue;
            }
            
            suitableHosts++;
            
            // Calculate resource waste
            double waste = calculateResourceWastage(host, vm);
            logger.trace("Host {} waste for VM {}: {:.4f}", host.getId(), vm.getId(), waste);
            
            // Update best host if this one has less waste
            if (waste < minWaste) {
                minWaste = waste;
                bestHost = host;
                logger.trace("New best host found: Host {} with waste {:.4f}", 
                    host.getId(), waste);
            }
        }
        
        logger.debug("Host search complete for VM {} - Checked: {}, Suitable: {}, Best: {}",
            vm.getId(), hostsChecked, suitableHosts, 
            bestHost != null ? bestHost.getId() : "none");
        
        return bestHost;
    }
    
    /**
     * Checks if a host is suitable for a VM with additional validation.
     * 
     * @param host The host to check
     * @param vm The VM to place
     * @return true if host can accommodate the VM
     */
    private boolean isHostSuitableForVm(Host host, Vm vm) {
        if (host == null || vm == null) {
            return false;
        }
        
        // Basic suitability check
        if (!host.isSuitableForVm(vm)) {
            return false;
        }
        
        // Additional checks for near-capacity situations
        double availableMips = host.getMips() - host.getTotalAllocatedMips();
        double availableRam = host.getRam().getAvailableResource();
        double availableBw = host.getBw().getAvailableResource();
        double availableStorage = host.getStorage().getAvailableResource();
        
        // Ensure host has minimum capacity threshold
        if (availableMips < vm.getTotalMipsCapacity() * (1 + MIN_CAPACITY_THRESHOLD) ||
            availableRam < vm.getRam().getCapacity() * (1 + MIN_CAPACITY_THRESHOLD) ||
            availableBw < vm.getBw().getCapacity() * (1 + MIN_CAPACITY_THRESHOLD) ||
            availableStorage < vm.getStorage().getCapacity() * (1 + MIN_CAPACITY_THRESHOLD)) {
            logger.trace("Host {} rejected due to insufficient capacity margin", host.getId());
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculates the total resource wastage for placing a VM on a host.
     * Uses weighted combination of multiple resources.
     * 
     * @param host The target host
     * @param vm The VM to place
     * @return The calculated waste value (lower is better)
     */
    private double calculateResourceWastage(Host host, Vm vm) {
        if (cachingEnabled) {
            String cacheKey = generateCacheKey(host, vm);
            
            // Check cache first
            Double cachedWaste = wasteCache.get(cacheKey);
            if (cachedWaste != null) {
                cacheHits.incrementAndGet();
                return cachedWaste;
            }
            
            cacheMisses.incrementAndGet();
        }
        
        ResourceWastage wastage = calculateDetailedResourceWastage(host, vm);
        
        if (cachingEnabled) {
            // Cache the result
            String cacheKey = generateCacheKey(host, vm);
            wasteCache.put(cacheKey, wastage.total);
        }
        
        return wastage.total;
    }
    
    /**
     * Calculates detailed resource wastage for all resource types.
     * 
     * @param host The target host
     * @param vm The VM to place
     * @return Detailed wastage information
     */
    private ResourceWastage calculateDetailedResourceWastage(Host host, Vm vm) {
        if (host == null || vm == null) {
            return new ResourceWastage(Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0);
        }
        
        try {
            // Calculate CPU wastage
            double hostTotalMips = host.getTotalMipsCapacity();
            double hostUsedMips = host.getVmScheduler().getAllocatedMips();
            double vmMips = vm.getTotalMipsCapacity();
            double cpuWastage = Math.max(0, hostTotalMips - (hostUsedMips + vmMips));
            
            // Calculate RAM wastage
            double hostTotalRam = host.getRam().getCapacity();
            double hostUsedRam = host.getRam().getAllocatedResource();
            double vmRam = vm.getRam().getCapacity();
            double ramWastage = Math.max(0, hostTotalRam - (hostUsedRam + vmRam));
            
            // Calculate BW wastage
            double hostTotalBw = host.getBw().getCapacity();
            double hostUsedBw = host.getBw().getAllocatedResource();
            double vmBw = vm.getBw().getCapacity();
            double bwWastage = Math.max(0, hostTotalBw - (hostUsedBw + vmBw));
            
            // Calculate storage wastage
            double hostTotalStorage = host.getStorage().getCapacity();
            double hostUsedStorage = host.getStorage().getAllocatedResource();
            double vmStorage = vm.getStorage().getCapacity();
            double storageWastage = Math.max(0, hostTotalStorage - (hostUsedStorage + vmStorage));
            
            // Normalize wastage values to [0,1] range
            double normalizedCpuWastage = hostTotalMips > 0 ? cpuWastage / hostTotalMips : 0;
            double normalizedRamWastage = hostTotalRam > 0 ? ramWastage / hostTotalRam : 0;
            double normalizedBwWastage = hostTotalBw > 0 ? bwWastage / hostTotalBw : 0;
            double normalizedStorageWastage = hostTotalStorage > 0 ? storageWastage / hostTotalStorage : 0;
            
            // Weighted sum of normalized wastage
            double totalWastage = (cpuWeight * normalizedCpuWastage) + 
                                (ramWeight * normalizedRamWastage) + 
                                (bwWeight * normalizedBwWastage) + 
                                (storageWeight * normalizedStorageWastage);
            
            logger.trace("Host {} wastage for VM {}: CPU={:.4f}, RAM={:.4f}, BW={:.4f}, Storage={:.4f}, Total={:.4f}",
                host.getId(), vm.getId(), normalizedCpuWastage, normalizedRamWastage, 
                normalizedBwWastage, normalizedStorageWastage, totalWastage);
            
            return new ResourceWastage(totalWastage, normalizedCpuWastage, 
                normalizedRamWastage, normalizedBwWastage, normalizedStorageWastage);
            
        } catch (Exception e) {
            logger.error("Error calculating resource wastage for host {} and VM {}", 
                host.getId(), vm.getId(), e);
            return new ResourceWastage(Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0);
        }
    }
    
    /**
     * Updates resource-specific wastage statistics.
     * 
     * @param wastage The wastage information to record
     */
    private void updateResourceStatistics(ResourceWastage wastage) {
        totalResourceWastage.merge("cpu", wastage.cpu, Double::sum);
        totalResourceWastage.merge("ram", wastage.ram, Double::sum);
        totalResourceWastage.merge("bw", wastage.bw, Double::sum);
        totalResourceWastage.merge("storage", wastage.storage, Double::sum);
        
        resourceWastageCount.merge("cpu", 1L, Long::sum);
        resourceWastageCount.merge("ram", 1L, Long::sum);
        resourceWastageCount.merge("bw", 1L, Long::sum);
        resourceWastageCount.merge("storage", 1L, Long::sum);
    }
    
    /**
     * Logs the resource utilization of a host after VM allocation.
     * 
     * @param host The host that received the VM
     * @param vm The VM that was allocated
     */
    private void logResourceUtilization(Host host, Vm vm) {
        try {
            double cpuUtilization = (host.getVmScheduler().getAllocatedMips() / host.getTotalMipsCapacity()) * 100;
            double ramUtilization = (host.getRam().getAllocatedResource() / host.getRam().getCapacity()) * 100;
            double bwUtilization = (host.getBw().getAllocatedResource() / host.getBw().getCapacity()) * 100;
            double storageUtilization = (host.getStorage().getAllocatedResource() / host.getStorage().getCapacity()) * 100;
            
            logger.debug("Host {} utilization after VM {} allocation: CPU={:.2f}%, RAM={:.2f}%, BW={:.2f}%, Storage={:.2f}%",
                host.getId(), vm.getId(), cpuUtilization, ramUtilization, bwUtilization, storageUtilization);
                
        } catch (Exception e) {
            logger.error("Error logging resource utilization", e);
        }
    }
    
    /**
     * Generates a cache key for host-VM combination.
     * Includes host's current allocation state for accuracy.
     * 
     * @param host The host
     * @param vm The VM
     * @return A unique cache key
     */
    private String generateCacheKey(Host host, Vm vm) {
        // Include host's current allocation state in the key
        return String.format("h%d-vm%d-cpu%.2f-ram%.2f-bw%.2f-storage%.2f", 
            host.getId(), 
            vm.getId(),
            host.getTotalAllocatedMips(),
            host.getRam().getAllocatedResource(),
            host.getBw().getAllocatedResource(),
            host.getStorage().getAllocatedResource());
    }
    
    /**
     * Clears cache entries for a specific host.
     * 
     * @param host The host whose cache entries should be cleared
     */
    private void clearCacheForHost(Host host) {
        String hostPrefix = String.format("h%d-", host.getId());
        int removed = 0;
        
        Iterator<String> iterator = wasteCache.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (key.startsWith(hostPrefix)) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            logger.trace("Cleared {} cache entries for Host {}", removed, host.getId());
        }
    }
    
    /**
     * Returns the name of this allocation policy.
     * 
     * @return "BestFit"
     */
    @Override
    public String getName() {
        return "BestFit";
    }
    
    /**
     * Gets a description of this allocation algorithm.
     * 
     * @return A detailed description of the Best Fit algorithm
     */
    public String getDescription() {
        return "Best Fit allocation algorithm that places VMs on hosts with minimum " +
               "remaining resources after allocation, optimizing multi-resource utilization";
    }
    
    /**
     * Deallocates all VMs from all hosts and clears internal state.
     * This method ensures proper cleanup of resources.
     */
    @Override
    public void deallocateAllVms() {
        logger.info("Deallocating all VMs from BestFit policy");
        super.deallocateAllVms();
        wasteCache.clear();
        logger.debug("Cleared waste cache with {} entries", wasteCache.size());
    }
    
    /**
     * Gets the current statistics of the BestFit algorithm.
     * 
     * @return A map containing algorithm statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Basic allocation statistics
        long total = totalAllocations.get();
        long successful = successfulAllocations.get();
        stats.put("totalAllocations", total);
        stats.put("successfulAllocations", successful);
        stats.put("successRate", total > 0 ? (double) successful / total : 0.0);
        
        // Cache statistics
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        stats.put("cacheEnabled", cachingEnabled);
        stats.put("cacheHits", hits);
        stats.put("cacheMisses", misses);
        stats.put("cacheHitRate", (hits + misses) > 0 ? (double) hits / (hits + misses) : 0.0);
        stats.put("currentCacheSize", wasteCache.size());
        
        // Timing statistics
        stats.put("avgAllocationTime", total > 0 ? 
            (double) totalAllocationTime.get() / total : 0.0);
        stats.put("avgHostSearchTime", total > 0 ? 
            (double) totalHostSearchTime.get() / total : 0.0);
        
        // Resource-specific wastage statistics
        Map<String, Double> avgWastage = new HashMap<>();
        for (String resource : Arrays.asList("cpu", "ram", "bw", "storage")) {
            Long count = resourceWastageCount.get(resource);
            Double totalWastage = totalResourceWastage.get(resource);
            avgWastage.put(resource, count > 0 ? totalWastage / count : 0.0);
        }
        stats.put("avgResourceWastage", avgWastage);
        
        // Resource weights
        Map<String, Double> weights = new HashMap<>();
        weights.put("cpu", cpuWeight);
        weights.put("ram", ramWeight);
        weights.put("bw", bwWeight);
        weights.put("storage", storageWeight);
        stats.put("resourceWeights", weights);
        
        logger.info("BestFit statistics: Total: {}, Success: {} ({:.2f}%), Cache hit rate: {:.2f}%, Avg allocation time: {:.2f}ms",
            total, successful,
            (double) successful / Math.max(1, total) * 100,
            (double) hits / Math.max(1, hits + misses) * 100,
            total > 0 ? (double) totalAllocationTime.get() / total : 0.0);
        
        return stats;
    }
    
    /**
     * Resets the internal statistics counters.
     * Useful for running multiple experiments.
     */
    public void resetStatistics() {
        logger.debug("Resetting BestFit allocation statistics");
        
        // Reset counters
        totalAllocations.set(0);
        successfulAllocations.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        totalAllocationTime.set(0);
        totalHostSearchTime.set(0);
        
        // Clear cache
        wasteCache.clear();
        
        // Reset resource statistics
        initializeStatistics();
    }
    
    /**
     * Returns a string representation of this allocation policy.
     * 
     * @return String representation including current statistics
     */
    @Override
    public String toString() {
        long total = totalAllocations.get();
        long successful = successfulAllocations.get();
        return String.format("BestFitAllocation[total=%d, success=%d, rate=%.2f%%, hosts=%d, caching=%s]",
            total, successful,
            total > 0 ? (double) successful / total * 100 : 0,
            getHostList().size(),
            cachingEnabled ? "enabled" : "disabled");
    }
    
    /**
     * Inner class to hold detailed resource wastage information.
     */
    private static class ResourceWastage {
        final double total;
        final double cpu;
        final double ram;
        final double bw;
        final double storage;
        
        ResourceWastage(double total, double cpu, double ram, double bw, double storage) {
            this.total = total;
            this.cpu = cpu;
            this.ram = ram;
            this.bw = bw;
            this.storage = storage;
        }
    }
}