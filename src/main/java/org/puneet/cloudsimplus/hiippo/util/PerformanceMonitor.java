package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors and tracks performance metrics during experiment execution.
 * Provides real-time monitoring of CPU, memory, and execution time metrics
 * with support for continuous monitoring and snapshot capture.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-22
 */
public class PerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    // JMX Beans for monitoring
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private static final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    // Monitoring intervals
    private static final long DEFAULT_MONITORING_INTERVAL_MS = 1000; // 1 second
    // Update thresholds to match project requirements
    private static final long MEMORY_WARNING_THRESHOLD = 4L * 1024 * 1024 * 1024; // 4GB
    private static final long MEMORY_CRITICAL_THRESHOLD = 5L * 1024 * 1024 * 1024; // 5GB
    private static final double CPU_WARNING_THRESHOLD = 0.9; // 90%
    
    // Performance metrics storage
    private final List<PerformanceSnapshot> snapshots;
    private final Map<String, Long> phaseStartTimes;
    private final Map<String, Long> phaseDurations;
    private final Map<String, PerformanceMetrics> phaseMetrics;
    
    // Monitoring state
    private final AtomicBoolean isMonitoring;
    private final AtomicLong startTime;
    private ScheduledExecutorService monitoringExecutor;
    private ScheduledFuture<?> monitoringTask;
    
    // Peak values tracking
    private long peakMemoryUsage;
    private double peakCpuUsage;
    private int peakThreadCount;
    
    // GC tracking
    private final Map<String, GCStats> gcStatsBeforeMonitoring;
    private final Map<String, GCStats> gcStatsDuringMonitoring;
    
    /**
     * Creates a new performance monitor instance.
     */
    public PerformanceMonitor() {
        this.snapshots = new CopyOnWriteArrayList<>();
        this.phaseStartTimes = new ConcurrentHashMap<>();
        this.phaseDurations = new ConcurrentHashMap<>();
        this.phaseMetrics = new ConcurrentHashMap<>();
        this.isMonitoring = new AtomicBoolean(false);
        this.startTime = new AtomicLong(0);
        this.gcStatsBeforeMonitoring = new HashMap<>();
        this.gcStatsDuringMonitoring = new ConcurrentHashMap<>();
        
        // Initialize peak values
        this.peakMemoryUsage = 0;
        this.peakCpuUsage = 0.0;
        this.peakThreadCount = 0;
        
        logger.debug("PerformanceMonitor initialized");
    }
    
    /**
     * Starts continuous performance monitoring.
     * 
     * @return true if monitoring started successfully, false if already monitoring
     */
    public boolean startMonitoring() {
        return startMonitoring(DEFAULT_MONITORING_INTERVAL_MS);
    }
    
    /**
     * Starts continuous performance monitoring with custom interval.
     * 
     * @param intervalMs Monitoring interval in milliseconds
     * @return true if monitoring started successfully, false if already monitoring
     */
    public boolean startMonitoring(long intervalMs) {
        if (!isMonitoring.compareAndSet(false, true)) {
            logger.warn("Performance monitoring already in progress");
            return false;
        }
        
        try {
            // Record start time
            startTime.set(System.currentTimeMillis());
            
            // Capture initial GC stats
            captureInitialGCStats();
            
            // Clear previous data
            snapshots.clear();
            phaseStartTimes.clear();
            phaseDurations.clear();
            phaseMetrics.clear();
            resetPeakValues();
            
            // Start monitoring executor
            monitoringExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PerformanceMonitor");
                t.setDaemon(true);
                return t;
            });
            
            // Schedule monitoring task
            monitoringTask = monitoringExecutor.scheduleAtFixedRate(
                this::captureSnapshot, 0, intervalMs, TimeUnit.MILLISECONDS);
            
            logger.info("Performance monitoring started with interval {}ms", intervalMs);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to start performance monitoring", e);
            isMonitoring.set(false);
            return false;
        }
    }
    
    /**
     * Stops performance monitoring and returns collected metrics.
     * 
     * @return Performance metrics collected during monitoring
     */
    public PerformanceMetrics stopMonitoring() {
        if (!isMonitoring.compareAndSet(true, false)) {
            logger.warn("Performance monitoring not in progress");
            return createEmptyMetrics();
        }
        
        try {
            // Stop monitoring task
            if (monitoringTask != null) {
                monitoringTask.cancel(false);
            }
            
            // Shutdown executor
            if (monitoringExecutor != null) {
                monitoringExecutor.shutdown();
                try {
                    if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        monitoringExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    monitoringExecutor.shutdownNow();
                }
            }
            
            // Capture final snapshot
            captureSnapshot();
            
            // Calculate total duration
            long duration = System.currentTimeMillis() - startTime.get();
            
            // Calculate GC impact
            Map<String, GCStats> gcImpact = calculateGCImpact();
            
            // Create performance metrics
            PerformanceMetrics metrics = createPerformanceMetrics(duration, gcImpact);
            
            logger.info("Performance monitoring stopped. Duration: {}ms, Snapshots: {}", 
                duration, snapshots.size());
            
            return metrics;
            
        } catch (Exception e) {
            logger.error("Error stopping performance monitoring", e);
            return createEmptyMetrics();
        }
    }
    
    /**
     * Starts monitoring a specific phase of execution.
     * 
     * @param phaseName Name of the phase to monitor
     */
    public void startPhase(String phaseName) {
        if (phaseName == null || phaseName.trim().isEmpty()) {
            logger.warn("Invalid phase name provided");
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        phaseStartTimes.put(phaseName, currentTime);
        
        logger.debug("Started monitoring phase: {}", phaseName);
    }
    
    /**
     * Ends monitoring a specific phase and records its metrics.
     * 
     * @param phaseName Name of the phase to end
     */
    public void endPhase(String phaseName) {
        if (phaseName == null || phaseName.trim().isEmpty()) {
            logger.warn("Invalid phase name provided");
            return;
        }
        
        Long startTime = phaseStartTimes.get(phaseName);
        if (startTime == null) {
            logger.warn("No start time found for phase: {}", phaseName);
            return;
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        phaseDurations.put(phaseName, duration);
        
        // Capture phase metrics
        PerformanceSnapshot snapshot = captureCurrentSnapshot();
        PerformanceMetrics metrics = new PerformanceMetrics(
            duration,
            snapshot.memoryUsage,
            snapshot.cpuUsage,
            snapshot.threadCount,
            peakMemoryUsage,
            peakCpuUsage,
            peakThreadCount,
            Collections.emptyMap()
        );
        
        phaseMetrics.put(phaseName, metrics);
        
        logger.debug("Ended monitoring phase: {} (duration: {}ms)", phaseName, duration);
    }
    
    /**
     * Captures a performance snapshot.
     */
    private void captureSnapshot() {
        try {
            PerformanceSnapshot snapshot = captureCurrentSnapshot();
            snapshots.add(snapshot);
            
            // CRITICAL FIX: Limit snapshots size to prevent memory leaks
            if (snapshots.size() > 1000) {
                snapshots.subList(0, snapshots.size() - 1000).clear();
            }
            
            // Update peak values
            updatePeakValues(snapshot);
            
            // Check for warnings
            checkPerformanceWarnings(snapshot);
            
        } catch (OutOfMemoryError e) {
            logger.error("OutOfMemoryError during monitoring - stopping continuous monitoring", e);
            stopMonitoring();
            throw new RuntimeException("Monitoring stopped due to memory constraints", e);
        } catch (Exception e) {
            logger.error("Error capturing performance snapshot", e);
        }
    }
    
    /**
     * Captures current performance metrics.
     * 
     * @return Current performance snapshot
     */
    private PerformanceSnapshot captureCurrentSnapshot() {
        long timestamp = System.currentTimeMillis();
        
        // Memory metrics
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        long totalMemory = heapUsage.getUsed() + nonHeapUsage.getUsed();
        
        // CPU metrics
        double cpuUsage = getCpuUsage();
        
        // Thread metrics
        int threadCount = threadBean.getThreadCount();
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        int deadlockCount = (deadlockedThreads != null) ? deadlockedThreads.length : 0;
        
        // GC metrics
        Map<String, GCStats> currentGCStats = captureCurrentGCStats();
        
        return new PerformanceSnapshot(
            timestamp,
            totalMemory,
            heapUsage.getUsed(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            cpuUsage,
            threadCount,
            deadlockCount,
            currentGCStats
        );
    }
    
    /**
     * Gets current CPU usage.
     * 
     * @return CPU usage as a percentage (0.0 to 1.0)
     */
    private double getCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double processCpuLoad = sunOsBean.getProcessCpuLoad();
            return processCpuLoad >= 0 ? processCpuLoad : 0.0;
        }
        
        // Fallback: use system load average
        double loadAverage = osBean.getSystemLoadAverage();
        int processors = osBean.getAvailableProcessors();
        return loadAverage >= 0 ? Math.min(loadAverage / processors, 1.0) : 0.0;
    }
    
    /**
     * Captures initial GC statistics.
     */
    private void captureInitialGCStats() {
        gcStatsBeforeMonitoring.clear();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            gcStatsBeforeMonitoring.put(
                gcBean.getName(),
                new GCStats(gcBean.getCollectionCount(), gcBean.getCollectionTime())
            );
        }
    }
    
    /**
     * Captures current GC statistics.
     * 
     * @return Map of GC collector names to their statistics
     */
    private Map<String, GCStats> captureCurrentGCStats() {
        Map<String, GCStats> currentStats = new HashMap<>();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            currentStats.put(
                gcBean.getName(),
                new GCStats(gcBean.getCollectionCount(), gcBean.getCollectionTime())
            );
        }
        return currentStats;
    }
    
    /**
     * Calculates GC impact during monitoring period.
     * 
     * @return Map of GC collector names to their impact statistics
     */
    private Map<String, GCStats> calculateGCImpact() {
        Map<String, GCStats> impact = new HashMap<>();
        Map<String, GCStats> finalStats = captureCurrentGCStats();
        
        for (Map.Entry<String, GCStats> entry : finalStats.entrySet()) {
            String collectorName = entry.getKey();
            GCStats current = entry.getValue();
            GCStats initial = gcStatsBeforeMonitoring.getOrDefault(
                collectorName, new GCStats(0, 0));
            
            long countDiff = current.count - initial.count;
            long timeDiff = current.time - initial.time;
            
            if (countDiff > 0 || timeDiff > 0) {
                impact.put(collectorName, new GCStats(countDiff, timeDiff));
            }
        }
        
        return impact;
    }
    
    /**
     * Updates peak values based on snapshot.
     * 
     * @param snapshot The performance snapshot
     */
    private void updatePeakValues(PerformanceSnapshot snapshot) {
        peakMemoryUsage = Math.max(peakMemoryUsage, snapshot.memoryUsage);
        peakCpuUsage = Math.max(peakCpuUsage, snapshot.cpuUsage);
        peakThreadCount = Math.max(peakThreadCount, snapshot.threadCount);
    }
    
    /**
     * Resets peak values.
     */
    private void resetPeakValues() {
        peakMemoryUsage = 0;
        peakCpuUsage = 0.0;
        peakThreadCount = 0;
    }
    
    /**
     * Checks for performance warnings in snapshot.
     * 
     * @param snapshot The performance snapshot to check
     */
    private void checkPerformanceWarnings(PerformanceSnapshot snapshot) {
        // Check memory usage
        if (snapshot.memoryUsage > MEMORY_CRITICAL_THRESHOLD) {
            logger.error("Memory usage critical: {} MB (threshold: {} MB)",
                snapshot.memoryUsage / (1024 * 1024),
                MEMORY_CRITICAL_THRESHOLD / (1024 * 1024));
        } else if (snapshot.memoryUsage > MEMORY_WARNING_THRESHOLD) {
            logger.warn("High memory usage detected: {} MB (threshold: {} MB)",
                snapshot.memoryUsage / (1024 * 1024),
                MEMORY_WARNING_THRESHOLD / (1024 * 1024));
        }
        
        // Check heap usage
        if (snapshot.heapMax > 0) {
            double heapUtilization = (double) snapshot.heapUsed / snapshot.heapMax;
            if (heapUtilization > 0.9) {
                logger.warn("High heap utilization: {}%", String.format("%.2f", heapUtilization * 100));
            }
        }
        
        // Check CPU usage
        if (snapshot.cpuUsage > CPU_WARNING_THRESHOLD) {
            logger.warn("High CPU usage detected: {}%", String.format("%.2f", snapshot.cpuUsage * 100));
        }
        
        // Check for deadlocks
        if (snapshot.deadlockCount > 0) {
            logger.error("Deadlocks detected: {} threads", snapshot.deadlockCount);
        }
    }
    
    /**
     * Creates performance metrics from collected data.
     * 
     * @param duration Total monitoring duration
     * @param gcImpact GC impact statistics
     * @return Performance metrics
     */
    private PerformanceMetrics createPerformanceMetrics(long duration, Map<String, GCStats> gcImpact) {
        // Calculate average values
        double avgMemory = snapshots.stream()
            .mapToLong(s -> s.memoryUsage)
            .average()
            .orElse(0.0);
            
        double avgCpu = snapshots.stream()
            .mapToDouble(s -> s.cpuUsage)
            .average()
            .orElse(0.0);
            
        double avgThreads = snapshots.stream()
            .mapToInt(s -> s.threadCount)
            .average()
            .orElse(0.0);
        
        return new PerformanceMetrics(
            duration,
            avgMemory,
            avgCpu,
            avgThreads,
            peakMemoryUsage,
            peakCpuUsage,
            peakThreadCount,
            gcImpact
        );
    }
    
    /**
     * Creates empty performance metrics.
     * 
     * @return Empty performance metrics
     */
    private PerformanceMetrics createEmptyMetrics() {
        return new PerformanceMetrics(0, 0, 0, 0, 0, 0, 0, Collections.emptyMap());
    }
    
    /**
     * Gets current memory usage information.
     * 
     * @return Memory usage info string
     */
    public static String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return String.format("Memory: Used=%dMB, Free=%dMB, Total=%dMB, Max=%dMB",
            usedMemory / (1024 * 1024),
            freeMemory / (1024 * 1024),
            totalMemory / (1024 * 1024),
            maxMemory / (1024 * 1024));
    }
    
    /**
     * Gets phase metrics for a specific phase.
     * 
     * @param phaseName Name of the phase
     * @return Performance metrics for the phase, or null if not found
     */
    public PerformanceMetrics getPhaseMetrics(String phaseName) {
        return phaseMetrics.get(phaseName);
    }
    
    /**
     * Gets all phase durations.
     * 
     * @return Map of phase names to their durations in milliseconds
     */
    public Map<String, Long> getPhaseDurations() {
        return new HashMap<>(phaseDurations);
    }
    
    /**
     * Checks if monitoring is currently active.
     * 
     * @return true if monitoring is active, false otherwise
     */
    public boolean isMonitoring() {
        return isMonitoring.get();
    }
    
    /**
     * Performance snapshot at a specific time.
     */
    private static class PerformanceSnapshot {
        final long timestamp;
        final long memoryUsage;
        final long heapUsed;
        final long heapMax;
        final long nonHeapUsed;
        final double cpuUsage;
        final int threadCount;
        final int deadlockCount;
        final Map<String, GCStats> gcStats;
        
        PerformanceSnapshot(long timestamp, long memoryUsage, long heapUsed, 
                          long heapMax, long nonHeapUsed, double cpuUsage, 
                          int threadCount, int deadlockCount, Map<String, GCStats> gcStats) {
            this.timestamp = timestamp;
            this.memoryUsage = memoryUsage;
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.cpuUsage = cpuUsage;
            this.threadCount = threadCount;
            this.deadlockCount = deadlockCount;
            this.gcStats = gcStats;
        }
    }
    
    /**
     * Garbage collection statistics.
     */
    private static class GCStats {
        final long count;
        final long time;
        
        GCStats(long count, long time) {
            this.count = count;
            this.time = time;
        }
    }
    
    /**
     * Performance metrics collected during monitoring.
     */
    public static class PerformanceMetrics {
        private final long duration;
        private final double avgMemoryUsage;
        private final double avgCpuUsage;
        private final double avgThreadCount;
        private final long peakMemoryUsage;
        private final double peakCpuUsage;
        private final int peakThreadCount;
        private final Map<String, GCStats> gcImpact;
        
        public PerformanceMetrics(long duration, double avgMemoryUsage, double avgCpuUsage,
                                double avgThreadCount, long peakMemoryUsage, double peakCpuUsage,
                                int peakThreadCount, Map<String, GCStats> gcImpact) {
            this.duration = duration;
            this.avgMemoryUsage = avgMemoryUsage;
            this.avgCpuUsage = avgCpuUsage;
            this.avgThreadCount = avgThreadCount;
            this.peakMemoryUsage = peakMemoryUsage;
            this.peakCpuUsage = peakCpuUsage;
            this.peakThreadCount = peakThreadCount;
            this.gcImpact = gcImpact;
        }
        
        // Getters
        public long getDuration() { return duration; }
        public double getAvgMemoryUsage() { return avgMemoryUsage; }
        public double getAvgCpuUsage() { return avgCpuUsage; }
        public double getAvgThreadCount() { return avgThreadCount; }
        public long getPeakMemoryUsage() { return peakMemoryUsage; }
        public double getPeakCpuUsage() { return peakCpuUsage; }
        public int getPeakThreadCount() { return peakThreadCount; }
        public Map<String, GCStats> getGcImpact() { return gcImpact; }
        
        /**
         * Gets total GC time during monitoring.
         * 
         * @return Total GC time in milliseconds
         */
        public long getTotalGcTime() {
            return gcImpact.values().stream()
                .mapToLong(stats -> stats.time)
                .sum();
        }
        
        /**
         * Gets total GC count during monitoring.
         * 
         * @return Total GC count
         */
        public long getTotalGcCount() {
            return gcImpact.values().stream()
                .mapToLong(stats -> stats.count)
                .sum();
        }
        
        @Override
        public String toString() {
            return String.format(
                "PerformanceMetrics[duration=%dms, avgMemory=%.2fMB, avgCPU=%.2f%%, " +
                "peakMemory=%dMB, peakCPU=%.2f%%, gcTime=%dms, gcCount=%d]",
                duration,
                avgMemoryUsage / (1024 * 1024),
                avgCpuUsage * 100,
                peakMemoryUsage / (1024 * 1024),
                peakCpuUsage * 100,
                getTotalGcTime(),
                getTotalGcCount()
            );
        }
        
        public double getExecutionTimeMillis() {
            return duration;
        }
        
        public double getPeakMemoryUsageMB() {
            return peakMemoryUsage / (1024.0 * 1024.0);
        }
    }
}