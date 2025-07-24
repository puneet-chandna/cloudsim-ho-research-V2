package org.puneet.cloudsimplus.hiippo;

import org.puneet.cloudsimplus.hiippo.simulation.ExperimentCoordinator;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.puneet.cloudsimplus.hiippo.exceptions.HippopotamusOptimizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the Hippopotamus Optimization Research Framework.
 * This application implements and evaluates the Hippopotamus Optimization (HO) algorithm
 * for VM placement optimization in CloudSim Plus, comparing it against baseline algorithms
 * with comprehensive statistical validation.
 * 
 * System Requirements:
 * - Java 21 or higher
 * - Minimum 16GB RAM (6GB heap space)
 * - CloudSim Plus 8.0.0
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-24
 */
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    // System requirement constants
    private static final long MINIMUM_HEAP_SIZE = 5L * 1024 * 1024 * 1024; // 5GB minimum
    private static final long RECOMMENDED_HEAP_SIZE = 6L * 1024 * 1024 * 1024; // 6GB recommended
    private static final int MINIMUM_JAVA_VERSION = 21;
    
    // Exit codes
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_SYSTEM_REQUIREMENTS_NOT_MET = 1;
    private static final int EXIT_INITIALIZATION_ERROR = 2;
    private static final int EXIT_EXECUTION_ERROR = 3;
    private static final int EXIT_UNEXPECTED_ERROR = 4;
    
    // Application metadata
    private static final String APPLICATION_NAME = "Hippopotamus Optimization Research Framework";
    private static final String VERSION = "1.0.0";
    private static final String BUILD_DATE = "2024-01-01";
    
    /**
     * Main entry point for the application.
     * 
     * @param args Command line arguments (currently not used)
     */
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Print application header
            printApplicationHeader();
            
            // Log system information
            logSystemInformation();
            
            // Check system requirements
            logger.info("Checking system requirements...");
            if (!checkSystemRequirements()) {
                logger.error("System requirements not met. Exiting...");
                System.exit(EXIT_SYSTEM_REQUIREMENTS_NOT_MET);
            }
            logger.info("System requirements check passed");
            
            // Create results directories
            logger.info("Creating results directories...");
            if (!createResultsDirectories()) {
                logger.error("Failed to create results directories. Exiting...");
                System.exit(EXIT_INITIALIZATION_ERROR);
            }
            logger.info("Results directories created successfully");
            
            // Initialize experiment configuration
            logger.info("Initializing experiment configuration...");
            if (!initializeConfiguration()) {
                logger.error("Failed to initialize configuration. Exiting...");
                System.exit(EXIT_INITIALIZATION_ERROR);
            }
            logger.info("Configuration initialized successfully");
            
            // Log experiment parameters
            logExperimentParameters();
            
            // Initialize memory monitoring
            startMemoryMonitoring();
            
            // Create and configure experiment coordinator
            logger.info("Creating experiment coordinator...");
            ExperimentCoordinator coordinator = createExperimentCoordinator();
            if (coordinator == null) {
                logger.error("Failed to create experiment coordinator. Exiting...");
                System.exit(EXIT_INITIALIZATION_ERROR);
            }
            logger.info("Experiment coordinator created successfully");
            
            // Configure experiments
            logger.info("Configuring experiments...");
            try {
                coordinator.configureExperiments();
                logger.info("Experiments configured successfully");
            } catch (Exception e) {
                logger.error("Failed to configure experiments", e);
                System.exit(EXIT_INITIALIZATION_ERROR);
            }
            
            // Run complete experimental suite
            logger.info("Starting experimental suite execution...");
            logger.info("This may take several hours to complete");
            logger.info("Progress will be reported periodically");
            
            try {
                coordinator.runCompleteExperiment();
                logger.info("Experimental suite completed successfully");
            } catch (OutOfMemoryError oom) {
                logger.error("Out of memory error during experiment execution", oom);
                logger.error("Consider reducing scenario sizes or increasing heap space");
                System.exit(EXIT_EXECUTION_ERROR);
            } catch (Exception e) {
                logger.error("Error during experiment execution", e);
                System.exit(EXIT_EXECUTION_ERROR);
            }
            
            // Calculate and log execution time
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;
            logExecutionSummary(executionTime);
            
            // Successful completion
            logger.info("All experiments completed successfully");
            logger.info("Results saved to: ./results/");
            System.exit(EXIT_SUCCESS);
            
        } catch (Exception e) {
            logger.error("Unexpected error in main application", e);
            System.exit(EXIT_UNEXPECTED_ERROR);
        } finally {
            // Ensure proper cleanup
            cleanup();
        }
    }
    
    /**
     * Prints the application header with version information.
     */
    private static void printApplicationHeader() {
        System.out.println("========================================");
        System.out.println(APPLICATION_NAME);
        System.out.println("Version: " + VERSION);
        System.out.println("Build Date: " + BUILD_DATE);
        System.out.println("========================================");
        System.out.println();
    }
    
    /**
     * Logs detailed system information for debugging and reproducibility.
     */
    private static void logSystemInformation() {
        logger.info("System Information:");
        
        // Java version
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        logger.info("  Java Version: {} ({})", javaVersion, javaVendor);
        
        // Operating system
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        logger.info("  Operating System: {} {} ({})", osName, osVersion, osArch);
        
        // Memory information
        Runtime runtime = Runtime.getRuntime();
        long maxHeap = runtime.maxMemory();
        long totalHeap = runtime.totalMemory();
        long freeHeap = runtime.freeMemory();
        logger.info("  Max Heap Size: {} MB", maxHeap / (1024 * 1024));
        logger.info("  Total Heap Size: {} MB", totalHeap / (1024 * 1024));
        logger.info("  Free Heap Size: {} MB", freeHeap / (1024 * 1024));
        
        // CPU information
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int processors = osBean.getAvailableProcessors();
        logger.info("  Available Processors: {}", processors);
        
        // System properties
        logger.info("  User Directory: {}", System.getProperty("user.dir"));
        logger.info("  Temp Directory: {}", System.getProperty("java.io.tmpdir"));
    }
    
    /**
     * Checks if the system meets the minimum requirements to run the experiments.
     * 
     * @return true if all requirements are met, false otherwise
     */
    private static boolean checkSystemRequirements() {
        boolean requirementsMet = true;
        
        // Check Java version
        String javaVersion = System.getProperty("java.version");
        try {
            int majorVersion = extractJavaMajorVersion(javaVersion);
            if (majorVersion < MINIMUM_JAVA_VERSION) {
                logger.error("Java version {} is below minimum required version {}", 
                    majorVersion, MINIMUM_JAVA_VERSION);
                requirementsMet = false;
            } else {
                logger.info("Java version check passed: {}", javaVersion);
            }
        } catch (Exception e) {
            logger.error("Failed to parse Java version: {}", javaVersion, e);
            requirementsMet = false;
        }
        
        // Check heap size
        long maxHeap = Runtime.getRuntime().maxMemory();
        if (maxHeap < MINIMUM_HEAP_SIZE) {
            logger.error("Insufficient heap size. Required: {} MB, Available: {} MB",
                MINIMUM_HEAP_SIZE / (1024 * 1024), maxHeap / (1024 * 1024));
            logger.error("Please run with JVM option: -Xmx6G");
            requirementsMet = false;
        } else if (maxHeap < RECOMMENDED_HEAP_SIZE) {
            logger.warn("Heap size {} MB is below recommended {} MB",
                maxHeap / (1024 * 1024), RECOMMENDED_HEAP_SIZE / (1024 * 1024));
            logger.warn("Some large scenarios may fail. Consider using -Xmx6G");
        } else {
            logger.info("Heap size check passed: {} MB", maxHeap / (1024 * 1024));
        }
        
        // Check available disk space
        File currentDir = new File(".");
        long freeSpace = currentDir.getUsableSpace();
        long requiredSpace = 1024L * 1024 * 1024; // 1GB for results
        if (freeSpace < requiredSpace) {
            logger.error("Insufficient disk space. Required: {} MB, Available: {} MB",
                requiredSpace / (1024 * 1024), freeSpace / (1024 * 1024));
            requirementsMet = false;
        } else {
            logger.info("Disk space check passed: {} MB available", 
                freeSpace / (1024 * 1024));
        }
        
        // Check write permissions
        File testFile = new File("test_write_permission.tmp");
        try {
            if (!testFile.createNewFile()) {
                logger.warn("Test file already exists, attempting to delete and recreate");
                testFile.delete();
                testFile.createNewFile();
            }
            testFile.delete();
            logger.info("Write permission check passed");
        } catch (IOException e) {
            logger.error("No write permission in current directory", e);
            requirementsMet = false;
        }
        
        return requirementsMet;
    }
    
    /**
     * Extracts the major version number from a Java version string.
     * 
     * @param versionString The Java version string
     * @return The major version number
     */
    private static int extractJavaMajorVersion(String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            throw new IllegalArgumentException("Version string is null or empty");
        }
        
        // Handle both old (1.8.0_xx) and new (11.0.x) version formats
        String[] parts = versionString.split("\\.");
        if (parts[0].equals("1")) {
            return Integer.parseInt(parts[1]);
        } else {
            return Integer.parseInt(parts[0]);
        }
    }
    
    /**
     * Creates the directory structure for storing experiment results.
     * 
     * @return true if all directories were created successfully, false otherwise
     */
    private static boolean createResultsDirectories() {
        List<String> directories = Arrays.asList(
            "results",
            "results/raw_results",
            "results/statistical_analysis",
            "results/parameter_sensitivity",
            "results/scalability_analysis",
            "results/convergence_data",
            "results/comparison_data",
            "results/logs",
            "results/backups"
        );
        
        boolean success = true;
        for (String dir : directories) {
            try {
                Path path = Paths.get(dir);
                Files.createDirectories(path);
                logger.debug("Created directory: {}", dir);
            } catch (IOException e) {
                logger.error("Failed to create directory: {}", dir, e);
                success = false;
            }
        }
        
        // Create a README file in the results directory
        try {
            Path readmePath = Paths.get("results/README.txt");
            List<String> readmeContent = Arrays.asList(
                "Hippopotamus Optimization Research Framework Results",
                "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "",
                "Directory Structure:",
                "- raw_results/: Raw experimental data in CSV format",
                "- statistical_analysis/: Statistical test results and confidence intervals",
                "- parameter_sensitivity/: Parameter sensitivity analysis results",
                "- scalability_analysis/: Scalability test results",
                "- convergence_data/: Algorithm convergence tracking data",
                "- comparison_data/: Algorithm comparison results",
                "- logs/: Detailed execution logs",
                "- backups/: Backup copies of important results"
            );
            Files.write(readmePath, readmeContent);
            logger.debug("Created README file in results directory");
        } catch (IOException e) {
            logger.warn("Failed to create README file", e);
            // Not critical, don't fail the operation
        }
        
        return success;
    }
    
    /**
     * Initializes the experiment configuration from properties files.
     * 
     * @return true if configuration was loaded successfully, false otherwise
     */
    private static boolean initializeConfiguration() {
        try {
            // Load main configuration
            Properties mainConfig = new Properties();
            mainConfig.load(App.class.getResourceAsStream("/config.properties"));
            
            // Load algorithm parameters
            Properties algorithmParams = new Properties();
            algorithmParams.load(App.class.getResourceAsStream("/algorithm_parameters.properties"));
            
            // Validate configuration
            if (!validateConfiguration(mainConfig, algorithmParams)) {
                logger.error("Configuration validation failed");
                return false;
            }
            
            // Log loaded configuration
            logger.debug("Loaded main configuration with {} properties", mainConfig.size());
            logger.debug("Loaded algorithm parameters with {} properties", algorithmParams.size());
            
            return true;
            
        } catch (IOException e) {
            logger.error("Failed to load configuration files", e);
            return false;
        } catch (NullPointerException e) {
            logger.error("Configuration files not found in resources", e);
            return false;
        }
    }
    
    /**
     * Validates the loaded configuration properties.
     * 
     * @param mainConfig Main configuration properties
     * @param algorithmParams Algorithm parameter properties
     * @return true if configuration is valid, false otherwise
     */
    private static boolean validateConfiguration(Properties mainConfig, Properties algorithmParams) {
        if (mainConfig == null || algorithmParams == null) {
            logger.error("Configuration properties are null");
            return false;
        }
        
        // Check for required properties
        List<String> requiredMainProps = Arrays.asList(
            "experiment.replications",
            "experiment.confidence.level",
            "experiment.significance.level"
        );
        
        for (String prop : requiredMainProps) {
            if (!mainConfig.containsKey(prop)) {
                logger.error("Missing required property: {}", prop);
                return false;
            }
        }
        
        List<String> requiredAlgorithmProps = Arrays.asList(
            "ho.population.size",
            "ho.max.iterations",
            "ho.convergence.threshold"
        );
        
        for (String prop : requiredAlgorithmProps) {
            if (!algorithmParams.containsKey(prop)) {
                logger.error("Missing required algorithm property: {}", prop);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Logs the experiment parameters for reproducibility.
     */
    private static void logExperimentParameters() {
        logger.info("Experiment Parameters:");
        logger.info("  Replications: {}", ExperimentConfig.REPLICATION_COUNT);
        logger.info("  Confidence Level: {}", ExperimentConfig.CONFIDENCE_LEVEL);
        logger.info("  Significance Level: {}", ExperimentConfig.SIGNIFICANCE_LEVEL);
        logger.info("  Random Seed: {}", ExperimentConfig.RANDOM_SEED);
        logger.info("  Batch Processing: {}", ExperimentConfig.ENABLE_BATCH_PROCESSING);
        logger.info("  Memory Warning Threshold: {} GB", 
            ExperimentConfig.MEMORY_WARNING_THRESHOLD / (1024.0 * 1024 * 1024));
    }
    
    /**
     * Starts a background thread to monitor memory usage during execution.
     */
    private static void startMemoryMonitoring() {
        Thread memoryMonitor = new Thread(() -> {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(TimeUnit.MINUTES.toMillis(5)); // Check every 5 minutes
                    
                    long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
                    long heapMax = memoryBean.getHeapMemoryUsage().getMax();
                    double usagePercentage = (heapUsed * 100.0) / heapMax;
                    
                    if (usagePercentage > 90) {
                        logger.warn("High memory usage detected: {}% ({} MB / {} MB)",
                            String.format("%.2f", usagePercentage), heapUsed / (1024 * 1024), heapMax / (1024 * 1024));
                        
                        // Try to free memory
                        System.gc();
                        logger.info("Garbage collection triggered due to high memory usage");
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        memoryMonitor.setDaemon(true);
        memoryMonitor.setName("Memory-Monitor");
        memoryMonitor.start();
        logger.debug("Memory monitoring thread started");
    }
    
    /**
     * Creates and configures the experiment coordinator.
     * 
     * @return Configured ExperimentCoordinator instance, or null if creation fails
     */
    private static ExperimentCoordinator createExperimentCoordinator() {
        try {
            ExperimentCoordinator coordinator = new ExperimentCoordinator();
            
            // Set any additional configuration if needed
            // coordinator.setProperty(...);
            
            // Validate coordinator state
            if (coordinator == null) {
                logger.error("Failed to instantiate ExperimentCoordinator");
                return null;
            }
            
            return coordinator;
            
        } catch (Exception e) {
            logger.error("Exception while creating ExperimentCoordinator", e);
            return null;
        }
    }
    
    /**
     * Logs the execution summary after all experiments complete.
     * 
     * @param executionTime Total execution time in milliseconds
     */
    private static void logExecutionSummary(long executionTime) {
        long hours = TimeUnit.MILLISECONDS.toHours(executionTime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(executionTime) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(executionTime) % 60;
        
        logger.info("========================================");
        logger.info("Execution Summary:");
        logger.info("  Total Execution Time: {}h {}m {}s", hours, minutes, seconds);
        
        // Memory statistics
        Runtime runtime = Runtime.getRuntime();
        long maxUsedMemory = runtime.totalMemory() - runtime.freeMemory();
        logger.info("  Peak Memory Usage: {} MB", maxUsedMemory / (1024 * 1024));
        
        // Results location
        logger.info("  Results Directory: {}/results/", System.getProperty("user.dir"));
        
        // Timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        logger.info("  Completion Time: {}", timestamp);
        logger.info("========================================");
    }
    
    /**
     * Performs cleanup operations before application exit.
     */
    private static void cleanup() {
        try {
            logger.info("Performing cleanup operations...");
            
            // Force garbage collection to free resources
            System.gc();
            
            // Allow time for any pending I/O operations
            Thread.sleep(1000);
            
            logger.info("Cleanup completed");
            
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
        }
    }
}