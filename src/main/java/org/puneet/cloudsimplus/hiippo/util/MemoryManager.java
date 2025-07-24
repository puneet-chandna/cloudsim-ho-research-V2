package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized memory management utility for the Hippopotamus Optimization research framework.
 * Provides real-time memory monitoring, garbage collection coordination, and memory-aware
 * batch processing capabilities optimized for 16GB RAM systems.
 * 
 * <p>This manager prevents OutOfMemoryError during large-scale experiments by:</p>
 * <ul>
 *   <li>Continuous memory usage monitoring</li>
 *   <li>Proactive garbage collection triggering</li>
 *   <li>Memory-based batch size adjustment</li>
 *   <li>Graceful degradation for memory-intensive scenarios</li>
 * </ul>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public final class MemoryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryManager.class);
    
    // ===================================================================================
    // MEMORY CONSTANTS
    // ===================================================================================
    
    /** Memory MXBean for JVM memory monitoring */
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    
    /** Scheduled executor for background memory monitoring */
    private static final ScheduledExecutorService MONITOR_EXECUTOR = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MemoryMonitor");
            t.setDaemon(true);
            return t;
        });
    
    /** Atomic flag to prevent concurrent GC operations */
    private static final AtomicBoolean GC_IN_PROGRESS = new AtomicBoolean(false);
    
    /** Memory threshold for aggressive GC (90% of max heap) */
    private static final double AGGRESSIVE_GC_THRESHOLD = 0.90;
    
    /** Memory threshold for warning messages (80% of max heap) */
    private static final double WARNING_THRESHOLD = 0.80;
    
    /** Memory threshold for batch size reduction (85% of max heap) */
    private static final double BATCH_REDUCTION_THRESHOLD = 0.85;
    
    /** Minimum batch size to prevent excessive overhead */
    private static final int MIN_BATCH_SIZE = 5;
    
    /** Maximum batch size for memory-intensive operations */
    private static final int MAX_BATCH_SIZE = 50;
    
    // ===================================================================================
    // MEMORY STATISTICS
    // ===================================================================================
    
    /** Current memory usage percentage */
    private static volatile double currentMemoryUsage = 0.0;
    
    /** Peak memory usage observed during execution */
    private static volatile double peakMemoryUsage = 0.0;
    
    /** Number of garbage collections triggered */
    private static volatile int gcCount = 0;
    
    /** Total time spent in garbage collection (milliseconds) */
    private static volatile long gcTime = 0;
    
    /** Last GC timestamp */
    private static volatile long lastGCTime = 0;
    
    // ===================================================================================
    // CONSTRUCTOR - PREVENT INSTANTIATION
    // ===================================================================================
    
    private MemoryManager() {
        throw new AssertionError("MemoryManager is a utility class and cannot be instantiated");
    }
    
    // ===================================================================================
    // INITIALIZATION AND SHUTDOWN
    // ===================================================================================
    
    /**
     * Initializes the memory manager with background monitoring.
     * Must be called before any memory-intensive operations.
     */
    public static void initialize() {
        logger.info("Initializing Memory Manager for 16GB system optimization");
        
        // Start background memory monitoring
        startMemoryMonitoring();
        
        // Log initial memory state
        logMemoryState("Initialization");
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(MemoryManager::shutdown));
    }
    
    /**
     * Shuts down the memory manager and releases resources.
     */
    public static void shutdown() {
        logger.info("Shutting down Memory Manager");
        
        // Stop background monitoring
        MONITOR_EXECUTOR.shutdown();
        try {
            if (!MONITOR_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                MONITOR_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            MONITOR_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Log final memory statistics
        logFinalStatistics();
    }
    
    // ===================================================================================
    // MEMORY MONITORING
    // ===================================================================================
    
    /**
     * Starts background memory monitoring at regular intervals.
     */
    private static void startMemoryMonitoring() {
        MONITOR_EXECUTOR.scheduleAtFixedRate(
            MemoryManager::updateMemoryStats,
            0,
            ExperimentConfig.MEMORY_CHECK_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Updates memory usage statistics from the JVM.
     */
    private static void updateMemoryStats() {
        try {
            MemoryUsage heapUsage = MEMORY_BEAN.getHeapMemoryUsage();
            long used = heapUsage.getUsed();
            long max = heapUsage.getMax();
            
            if (max > 0) {
                currentMemoryUsage = (double) used / max;
                peakMemoryUsage = Math.max(peakMemoryUsage, currentMemoryUsage);
                
                // Check for critical memory usage
                if (currentMemoryUsage >= AGGRESSIVE_GC_THRESHOLD) {
                    triggerGarbageCollection("Critical memory usage detected");
                }
            }
        } catch (Exception e) {
            logger.error("Error updating memory stats", e);
        }
    }
    
    /**
     * Gets current memory usage as a percentage.
     * 
     * @return Memory usage percentage (0.0 to 1.0)
     */
    public static double getCurrentMemoryUsage() {
        return currentMemoryUsage;
    }
    
    /**
     * Gets peak memory usage observed during execution.
     * 
     * @return Peak memory usage percentage (0.0 to 1.0)
     */
    public static double getPeakMemoryUsage() {
        return peakMemoryUsage;
    }
    
    // ===================================================================================
    // GARBAGE COLLECTION MANAGEMENT
    // ===================================================================================
    
    /**
     * Triggers garbage collection if not already in progress.
     * 
     * @param reason Reason for triggering GC
     * @return true if GC was triggered, false if already in progress
     */
    public static boolean triggerGarbageCollection(String reason) {
        if (GC_IN_PROGRESS.compareAndSet(false, true)) {
            try {
                long startTime = System.currentTimeMillis();
                
                logger.warn("Triggering garbage collection: {}", reason);
                System.gc();
                
                // Wait for GC to complete
                Thread.sleep(1000);
                
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;
                
                gcCount++;
                gcTime += duration;
                lastGCTime = endTime;
                
                logger.info("Garbage collection completed in {} ms", duration);
                
                // Update memory stats after GC
                updateMemoryStats();
                
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("GC interrupted", e);
                return false;
            } finally {
                GC_IN_PROGRESS.set(false);
            }
        }
        return false;
    }
    
    /**
     * Forces garbage collection regardless of current state.
     * Use sparingly - only for critical memory situations.
     */
    public static void forceGarbageCollection() {
        logger.warn("Forcing garbage collection");
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        updateMemoryStats();
    }
    
    // ===================================================================================
    // MEMORY CHECKING AND VALIDATION
    // ===================================================================================
    
    /**
     * Checks if the system has sufficient memory for a given scenario.
     * 
     * @param vmCount Number of VMs
     * @param hostCount Number of hosts
     * @return true if memory is sufficient
     */
    public static boolean checkMemoryAvailability(int vmCount, int hostCount) {
        long estimated = ExperimentConfig.estimateMemoryRequirement(vmCount, hostCount);
        MemoryUsage heapUsage = MEMORY_BEAN.getHeapMemoryUsage();
        long available = heapUsage.getMax() - heapUsage.getUsed();
        
        boolean sufficient = available > estimated * 2; // 2x safety factor
        
        logger.debug("Memory availability check - VMs: {}, Hosts: {}, " +
                    "Estimated: {} MB, Available: {} MB, Sufficient: {}",
            vmCount, hostCount,
            estimated / (1024 * 1024),
            available / (1024 * 1024),
            sufficient);
        
        return sufficient;
    }
    
    /**
     * Checks if there is enough memory available for the given scenario (VMs and Hosts).
     * Delegates to ExperimentConfig.hasEnoughMemoryForScenario for estimation logic.
     *
     * @param vmCount Number of VMs
     * @param hostCount Number of Hosts
     * @return true if enough memory is available, false otherwise
     */
    public static boolean hasEnoughMemoryForScenario(int vmCount, int hostCount) {
        return org.puneet.cloudsimplus.hiippo.util.ExperimentConfig.hasEnoughMemoryForScenario(vmCount, hostCount);
    }
    
    /**
     * Checks memory usage and logs a warning if threshold is exceeded.
     * 
     * @param phase Current execution phase for logging context
     */
    public static void checkMemoryUsage(String phase) {
        updateMemoryStats();
        
        if (currentMemoryUsage >= WARNING_THRESHOLD) {
            logger.warn("High memory usage during {}: {:.1f}% (Peak: {:.1f}%)",
                phase, currentMemoryUsage * 100, peakMemoryUsage * 100);
            
            if (currentMemoryUsage >= BATCH_REDUCTION_THRESHOLD) {
                logger.warn("Consider reducing batch size for memory optimization");
            }
        } else {
            logger.debug("Memory usage during {}: {:.1f}%", phase, currentMemoryUsage * 100);
        }
    }
    
    // ===================================================================================
    // BATCH SIZE OPTIMIZATION
    // ===================================================================================
    
    /**
     * Calculates optimal batch size based on current memory usage.
     * 
     * @param requestedSize Original requested batch size
     * @return Optimized batch size based on memory availability
     */
    public static int optimizeBatchSize(int requestedSize) {
        updateMemoryStats();
        
        int optimizedSize = requestedSize;
        
        if (currentMemoryUsage >= BATCH_REDUCTION_THRESHOLD) {
            // Reduce batch size proportionally to memory usage
            double reductionFactor = 1.0 - (currentMemoryUsage - BATCH_REDUCTION_THRESHOLD) / 0.15;
            optimizedSize = (int) (requestedSize * reductionFactor);
            optimizedSize = Math.max(optimizedSize, MIN_BATCH_SIZE);
        } else if (currentMemoryUsage < 0.5) {
            // Increase batch size if memory is abundant
            optimizedSize = Math.min(requestedSize * 2, MAX_BATCH_SIZE);
        }
        
        if (optimizedSize != requestedSize) {
            logger.info("Batch size optimized: {} -> {} (memory: {:.1f}%)",
                requestedSize, optimizedSize, currentMemoryUsage * 100);
        }
        
        return optimizedSize;
    }
    
    /**
     * Gets recommended batch size for current memory conditions.
     * 
     * @return Recommended batch size
     */
    public static int getRecommendedBatchSize() {
        return optimizeBatchSize(ExperimentConfig.DEFAULT_BATCH_SIZE);
    }
    
    // ===================================================================================
    // MEMORY PRESSURE HANDLING
    // ===================================================================================
    
    /**
     * Waits for memory pressure to reduce before proceeding.
     * 
     * @param maxWaitMillis Maximum time to wait in milliseconds
     * @return true if memory pressure reduced, false if timeout
     */
    public static boolean waitForMemoryPressureReduction(long maxWaitMillis) {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            updateMemoryStats();
            
            if (currentMemoryUsage < WARNING_THRESHOLD) {
                return true;
            }
            
            triggerGarbageCollection("Memory pressure reduction");
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        logger.warn("Memory pressure reduction timeout after {} ms", maxWaitMillis);
        return false;
    }
    
    /**
     * Performs emergency memory cleanup for critical situations.
     */
    public static void emergencyMemoryCleanup() {
        logger.error("Emergency memory cleanup initiated");
        
        // Force multiple GC cycles
        for (int i = 0; i < 3; i++) {
            forceGarbageCollection();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        updateMemoryStats();
        logger.error("Emergency cleanup completed - memory usage: {:.1f}%", 
            currentMemoryUsage * 100);
    }
    
    // ===================================================================================
    // LOGGING AND STATISTICS
    // ===================================================================================
    
    /**
     * Logs current memory state with context.
     * 
     * @param context Context for the log message
     */
    public static void logMemoryState(String context) {
        updateMemoryStats();
        
        MemoryUsage heapUsage = MEMORY_BEAN.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = MEMORY_BEAN.getNonHeapMemoryUsage();
        
        logger.info("Memory State [{}]: Heap={}/{} MB ({:.1f}%), Non-Heap={}/{} MB, GC={} ({} ms)",
            context,
            heapUsage.getUsed() / (1024 * 1024),
            heapUsage.getMax() / (1024 * 1024),
            currentMemoryUsage * 100,
            nonHeapUsage.getUsed() / (1024 * 1024),
            nonHeapUsage.getMax() / (1024 * 1024),
            gcCount,
            gcTime);
    }
    
    /**
     * Logs final memory statistics at application shutdown.
     */
    private static void logFinalStatistics() {
        updateMemoryStats();
        
        logger.info("=== Final Memory Statistics ===");
        logger.info("Peak Memory Usage: {:.1f}%", peakMemoryUsage * 100);
        logger.info("Garbage Collections: {}", gcCount);
        logger.info("Total GC Time: {} ms", gcTime);
        logger.info("Average GC Time: {} ms", gcCount > 0 ? gcTime / gcCount : 0);
        logger.info("Final Memory Usage: {:.1f}%", currentMemoryUsage * 100);
    }
    
    /**
     * Gets a comprehensive memory report.
     * 
     * @return Memory report as a formatted string
     */
    public static String getMemoryReport() {
        StringBuilder report = new StringBuilder();
        report.append("Memory Report:\n");
        report.append(String.format("  Current Usage: %.1f%%\n", currentMemoryUsage * 100));
        report.append(String.format("  Peak Usage: %.1f%%\n", peakMemoryUsage * 100));
        report.append(String.format("  GC Count: %d\n", gcCount));
        report.append(String.format("  GC Time: %d ms\n", gcTime));
        report.append(String.format("  Recommended Batch Size: %d\n", getRecommendedBatchSize()));
        
        return report.toString();
    }
    
    // ===================================================================================
    // MEMORY ALLOCATION TRACKING
    // ===================================================================================
    
    /**
     * Tracks memory allocation for debugging purposes.
     * 
     * @param bytes Number of bytes being allocated
     * @param description Description of the allocation
     */
    public static void trackAllocation(long bytes, String description) {
        if (logger.isDebugEnabled()) {
            updateMemoryStats();
            logger.debug("Allocating {} MB for {} (current usage: {:.1f}%)",
                bytes / (1024 * 1024), description, currentMemoryUsage * 100);
        }
    }
    
    /**
     * Tracks memory deallocation for debugging purposes.
     * 
     * @param bytes Number of bytes being deallocated
     * @param description Description of the deallocation
     */
    public static void trackDeallocation(long bytes, String description) {
        if (logger.isDebugEnabled()) {
            updateMemoryStats();
            logger.debug("Deallocating {} MB for {} (current usage: {:.1f}%)",
                bytes / (1024 * 1024), description, currentMemoryUsage * 100);
        }
    }
}