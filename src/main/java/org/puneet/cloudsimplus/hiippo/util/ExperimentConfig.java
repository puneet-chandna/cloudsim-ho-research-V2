package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized configuration management for the Hippopotamus Optimization research framework.
 * Provides experiment-wide settings, memory management parameters, and reproducible random seed handling.
 * 
 * <p>This class is designed specifically for 16GB RAM systems with optimized memory usage
 * and batch processing capabilities to prevent OutOfMemoryError during large-scale experiments.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public final class ExperimentConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(ExperimentConfig.class);
    
    // ===================================================================================
    // EXPERIMENT CONFIGURATION CONSTANTS
    // ===================================================================================
    
    /** Random seed for reproducible experiments */
    public static final long RANDOM_SEED = 123456L;
    
    // Experiment configuration - Increased for statistical significance and real cloud simulation
    public static final int REPLICATION_COUNT = 10;  
    public static final long MAX_HEAP_SIZE = 50L * 1024 * 1024 * 1024; // 50GB
    public static final long MEMORY_WARNING_THRESHOLD = 45L * 1024 * 1024 * 1024; // 45GB
    
    /** Confidence level for statistical analysis (95% = standard in research) */
    public static final double CONFIDENCE_LEVEL = 0.95;
    
    /** Significance level for hypothesis testing (α = 0.05) */
    public static final double SIGNIFICANCE_LEVEL = 0.05;
    
    /** Enable report generation for experiments */
    public static final boolean ENABLE_REPORT_GENERATION = true;
    
    // ===================================================================================
    // MEMORY MANAGEMENT SETTINGS (Optimized for 16GB Systems)
    // ===================================================================================
    
    /** Enable batch processing for memory-intensive operations */
    public static final boolean ENABLE_BATCH_PROCESSING = true;
    
    /** Default batch size for VM processing (optimized for research scale) */
    public static final int DEFAULT_BATCH_SIZE = 5;  // Reduced batch size
    
    /** Memory check interval in milliseconds during experiments */
    public static final long MEMORY_CHECK_INTERVAL = 5000;
    
    // ===================================================================================
    // CLOUDSIM CONFIGURATION
    // ===================================================================================
    
    /** Default datacenter characteristics */
    public static final String DATACENTER_ARCH = "x86";
    public static final String DATACENTER_OS = "Linux";
    public static final String DATACENTER_VMM = "Xen";
    
    /** Time zone for simulation */
    public static final double TIME_ZONE = 5.0;
    
    /** Cost per CPU second (in arbitrary units) */
    public static final double COST_PER_CPU = 3.0;
    
    /** Cost per memory unit (in arbitrary units) */
    public static final double COST_PER_MEM = 0.05;
    
    /** Cost per storage unit (in arbitrary units) */
    public static final double COST_PER_STORAGE = 0.001;
    
    /** Cost per bandwidth unit (in arbitrary units) */
    public static final double COST_PER_BW = 0.0;
    
    // ===================================================================================
    // VM AND HOST CONFIGURATION RANGES
    // ===================================================================================
    
    /** VM MIPS range (Millions of Instructions Per Second) */
    public static final int VM_MIPS_MIN = 1000;
    public static final int VM_MIPS_MAX = 2000;
    
    /** VM RAM range (in MB) */
    public static final int VM_RAM_MIN = 512;
    public static final int VM_RAM_MAX = 2048;
    
    /** VM bandwidth range (in Mbps) */
    public static final long VM_BW_MIN = 1000;
    public static final long VM_BW_MAX = 10000;
    
    /** VM storage range (in MB) */
    public static final long VM_SIZE_MIN = 10000;
    public static final long VM_SIZE_MAX = 100000;
    
    /** Host MIPS range (per PE) */
    public static final int HOST_MIPS_MIN = 1000;
    public static final int HOST_MIPS_MAX = 3000;
    
    /** Host RAM range (in MB) */
    public static final int HOST_RAM_MIN = 2048;
    public static final int HOST_RAM_MAX = 16384;
    
    /** Host storage range (in MB) */
    public static final long HOST_STORAGE_MIN = 1000000;
    public static final long HOST_STORAGE_MAX = 10000000;
    
    /** Host bandwidth range (in Mbps) */
    public static final long HOST_BW_MIN = 10000;
    public static final long HOST_BW_MAX = 100000;
    
    // ===================================================================================
    // ALGORITHM CONFIGURATION
    // ===================================================================================
    
    /** Default population size for HO algorithm (optimized for memory) */
    public static final int DEFAULT_POPULATION_SIZE = 20;
    
    /** Maximum iterations for HO algorithm (reduced for 16GB systems) */
    public static final int DEFAULT_MAX_ITERATIONS = 50;
    
    /** Convergence threshold for HO algorithm */
    public static final double DEFAULT_CONVERGENCE_THRESHOLD = 0.001;
    
    /** Default GA population size (baseline algorithm) */
    public static final int GA_POPULATION_SIZE = 30;
    
    /** Default GA generations (baseline algorithm) */
    public static final int GA_MAX_GENERATIONS = 50;
    
    // ===================================================================================
    // FILE PATH CONFIGURATION
    // ===================================================================================
    
    /** Base directory for all results */
    public static final String RESULTS_BASE_DIR = "results/";
    
    /** Directory for raw experimental results */
    public static final String RAW_RESULTS_DIR = RESULTS_BASE_DIR + "raw_results/";
    
    /** Directory for statistical analysis results */
    public static final String STATISTICAL_DIR = RESULTS_BASE_DIR + "statistical_analysis/";
    
    /** Directory for parameter sensitivity analysis */
    public static final String PARAM_SENSITIVITY_DIR = RESULTS_BASE_DIR + "parameter_sensitivity/";
    
    /** Directory for scalability analysis */
    public static final String SCALABILITY_DIR = RESULTS_BASE_DIR + "scalability_analysis/";
    
    /** Directory for convergence data */
    public static final String CONVERGENCE_DIR = RESULTS_BASE_DIR + "convergence_data/";
    
    /** Directory for comparison data */
    public static final String COMPARISON_DIR = RESULTS_BASE_DIR + "comparison_data/";
    
    // ===================================================================================
    // INTERNAL STATE MANAGEMENT
    // ===================================================================================
    
    /** Thread-safe map of random generators for each replication */
    private static final Map<Integer, Random> randomGenerators = new ConcurrentHashMap<>();
    
    /** Flag to track if configuration has been initialized */
    private static volatile boolean initialized = false;
    
    /** Lock for thread-safe initialization */
    private static final Object initializationLock = new Object();
    
    // ===================================================================================
    // CONFIG PROPERTIES LOADING
    // ===================================================================================
    private static final Properties properties = new Properties();
    private static final Set<String> enabledScenarios = new HashSet<>();
    static {
        try (InputStream input = ExperimentConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
                String scenarios = properties.getProperty("scenarios.enabled", "");
                enabledScenarios.clear();
                for (String s : scenarios.split(",")) {
                    if (!s.isBlank()) enabledScenarios.add(s.trim());
                }
            } else {
                throw new RuntimeException("config.properties not found in classpath");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static boolean isScenarioEnabled(String scenario) {
        return enabledScenarios.contains(scenario.trim());
    }

    public static int getHostCountForScenario(String scenario) {
        String key = "scenario." + scenario.toLowerCase() + ".hosts";
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Host count for scenario '" + scenario + "' not found in config.properties");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid host count for scenario '" + scenario + "': " + value);
        }
    }

    public static long getHostMips() {
        return getLongProperty("host.mips", 10000); // Fixed: Use 10000 MIPS as per config.properties
    }
    public static long getHostRam() {
        return getLongProperty("host.ram", 16384);
    }
    public static long getHostStorage() {
        return getLongProperty("host.storage", 1000000);
    }
    public static long getHostBw() {
        return getLongProperty("host.bandwidth", 10000);
    }
    public static int getHostPes() {
        return getIntProperty("host.pes", 8);
    }
    private static long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for '" + key + "' in config.properties: " + value);
        }
    }
    private static int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value for '" + key + "' in config.properties: " + value);
        }
    }
    
    // ===================================================================================
    // CONSTRUCTOR - PREVENT INSTANTIATION
    // ===================================================================================
    
    private ExperimentConfig() {
        throw new AssertionError("ExperimentConfig is a utility class and cannot be instantiated");
    }
    
    // ===================================================================================
    // RANDOM SEED MANAGEMENT
    // ===================================================================================
    
    /**
     * Initializes the random seed for a specific replication.
     * Ensures reproducible experiments by creating deterministic random sequences.
     * 
     * @param replication The replication number (0 to REPLICATION_COUNT-1)
     * @throws IllegalArgumentException if replication is negative or >= REPLICATION_COUNT
     */
    public static void initializeRandomSeed(int replication) {
        if (replication < 0 || replication >= REPLICATION_COUNT) {
            throw new IllegalArgumentException(
                "Replication must be between 0 and " + (REPLICATION_COUNT - 1));
        }
        
        long seed = RANDOM_SEED + replication;
        Random random = new Random(seed);
        randomGenerators.put(replication, random);
        
        // Also set CloudSim Plus random seed for consistency
        // (RandomGenerator class not found, skipping setting CloudSim Plus random seed)
        
        logger.debug("Initialized random seed for replication {}: {}", replication, seed);
    }
    
    /**
     * Gets the random generator for a specific replication.
     * 
     * @param replication The replication number
     * @return Random generator initialized with the appropriate seed
     * @throws IllegalStateException if the replication hasn't been initialized
     */
    public static Random getRandomGenerator(int replication) {
        Random random = randomGenerators.get(replication);
        if (random == null) {
            throw new IllegalStateException(
                "Random generator not initialized for replication " + replication);
        }
        return random;
    }
    
    // ===================================================================================
    // MEMORY MANAGEMENT UTILITIES
    // ===================================================================================
    
    /**
     * Checks if garbage collection should be triggered based on current memory usage.
     * 
     * @return true if memory usage exceeds warning threshold
     */
    public static boolean shouldRunGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return usedMemory > MEMORY_WARNING_THRESHOLD;
    }
    
    /**
     * Gets current memory usage statistics.
     * 
     * @return Memory usage information as a formatted string
     */
    public static String getMemoryUsageStats() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return String.format(
            "Memory: Used=%d MB (%.1f%%), Free=%d MB, Total=%d MB, Max=%d MB",
            usedMemory / (1024 * 1024),
            (usedMemory * 100.0) / maxMemory,
            freeMemory / (1024 * 1024),
            totalMemory / (1024 * 1024),
            maxMemory / (1024 * 1024)
        );
    }
    
    /**
     * Estimates memory requirement for a given scenario.
     * 
     * @param vmCount Number of VMs in the scenario
     * @param hostCount Number of hosts in the scenario
     * @return Estimated memory requirement in bytes
     */
    public static long estimateMemoryRequirement(int vmCount, int hostCount) {
        // Conservative estimate: 50KB per VM + 100KB per Host + overhead
        long vmMemory = vmCount * 50_000L;
        long hostMemory = hostCount * 100_000L;
        long overhead = 100_000_000L; // 100MB overhead
        
        return vmMemory + hostMemory + overhead;
    }
    
    /**
     * Checks if the system has enough memory for a given scenario.
     * 
     * @param vmCount Number of VMs
     * @param hostCount Number of hosts
     * @return true if sufficient memory is available
     */
    public static boolean hasEnoughMemoryForScenario(int vmCount, int hostCount) {
        long estimated = estimateMemoryRequirement(vmCount, hostCount);
        Runtime runtime = Runtime.getRuntime();
        long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        
        // Require 2x safety factor
        boolean sufficient = available > estimated * 2;
        
        logger.debug("Memory check: VMs={}, Hosts={}, Estimated={} MB, Available={} MB, Sufficient={}",
            vmCount, hostCount, estimated / (1024 * 1024), available / (1024 * 1024), sufficient);
        
        return sufficient;
    }
    
    // ===================================================================================
    // CONFIGURATION VALIDATION
    // ===================================================================================
    
    /**
     * Validates the entire configuration for consistency.
     * 
     * @throws IllegalStateException if any configuration is invalid
     */
    public static void validateConfiguration() {
        // Check memory settings
        if (MAX_HEAP_SIZE > Runtime.getRuntime().maxMemory()) {
            throw new IllegalStateException(
                "Configured heap size exceeds JVM maximum: " + MAX_HEAP_SIZE + " > " + 
                Runtime.getRuntime().maxMemory());
        }
        
        // Check replication count
        if (REPLICATION_COUNT < 10) {
            logger.warn("Replication count is low for statistical validity: {}", REPLICATION_COUNT);
        }
        
        // Check batch processing
        if (DEFAULT_BATCH_SIZE <= 0) {
            throw new IllegalStateException("Batch size must be positive");
        }
        
        // Check file paths
        validateDirectory(RESULTS_BASE_DIR);
        validateDirectory(RAW_RESULTS_DIR);
        validateDirectory(STATISTICAL_DIR);
        validateDirectory(PARAM_SENSITIVITY_DIR);
        validateDirectory(SCALABILITY_DIR);
        validateDirectory(CONVERGENCE_DIR);
        validateDirectory(COMPARISON_DIR);
        
        logger.info("Configuration validation completed successfully");
    }
    
    /**
     * Validates that a directory exists or can be created.
     * 
     * @param directoryPath Path to validate
     * @throws IllegalStateException if directory cannot be created
     */
    private static void validateDirectory(String directoryPath) {
        java.io.File dir = new java.io.File(directoryPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + directoryPath);
        }
    }
    
    // ===================================================================================
    // UTILITY METHODS
    // ===================================================================================
    
    /**
     * Gets the scenario specifications optimized for 16GB systems.
     * 
     * @return Map of scenario names to VM/Host counts
     */
    public static Map<String, int[]> getScenarioSpecifications() {
        return Map.of(
            "Micro", new int[]{10, 3},      // 10 VMs, 3 Hosts
            "Small", new int[]{50, 10},     // 50 VMs, 10 Hosts
            "Medium", new int[]{100, 20},   // 100 VMs, 20 Hosts
            "Large", new int[]{200, 40},    // 200 VMs, 40 Hosts
            "XLarge", new int[]{500, 100}   // 500 VMs, 100 Hosts
        );
    }
    
    /**
     * Gets the list of algorithms to be tested.
     * 
     * @return List of algorithm names
     */
    public static java.util.List<String> getAlgorithms() {
        return java.util.Arrays.asList("HO", "FirstFit", "BestFit", "GA");
    }
    
    /**
     * Initializes the entire configuration system.
     * Should be called once at application startup.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        
        synchronized (initializationLock) {
            if (initialized) {
                return;
            }
            
            logger.info("Initializing Experiment Configuration");
            logger.info("System: Java {}, Max Heap: {} MB", 
                System.getProperty("java.version"),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
            
            // Create directories
            validateDirectory(RESULTS_BASE_DIR);
            
            // Validate configuration
            validateConfiguration();
            
            initialized = true;
            logger.info("Configuration initialized successfully");
        }
    }
    
    /**
     * Resets the configuration (for testing purposes).
     */
    public static void reset() {
        synchronized (initializationLock) {
            randomGenerators.clear();
            initialized = false;
            logger.info("Configuration reset");
        }
    }
}
