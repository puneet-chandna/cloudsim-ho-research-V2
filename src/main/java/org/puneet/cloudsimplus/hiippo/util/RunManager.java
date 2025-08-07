package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Manages run-specific configurations including unique run IDs and result folders.
 * Each execution of the application gets a unique run ID and corresponding result folder.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-01-26
 */
public class RunManager {
    private static final Logger logger = LoggerFactory.getLogger(RunManager.class);
    
    private static final String RUN_ID_PREFIX = "run";
    private static final String RESULTS_BASE_DIR = "results";
    private static final String LOGS_DIR = "logs";
    
    private static volatile RunManager instance;
    private static final Object lock = new Object();
    
    private final String runId;
    private final String runTimestamp;
    private final Path resultsPath;
    private final Path logsPath;
    private final Path runLogsPath;
    
    /**
     * Private constructor to enforce singleton pattern.
     */
    private RunManager() {
        this.runId = generateRunId();
        this.runTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        this.resultsPath = Paths.get(RESULTS_BASE_DIR, runId);
        this.logsPath = Paths.get(LOGS_DIR);
        this.runLogsPath = resultsPath.resolve("logs");
        
        // Set MDC for logging context
        MDC.put("run.id", runId);
        
        logger.info("RunManager initialized with Run ID: {}", runId);
    }
    
    /**
     * Gets the singleton instance of RunManager.
     * 
     * @return RunManager instance
     */
    public static RunManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new RunManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Generates a unique run ID.
     * 
     * @return Unique run ID string
     */
    private String generateRunId() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return RUN_ID_PREFIX + "_" + uuid + "_" + 
               LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
    
    /**
     * Gets the current run ID.
     * 
     * @return Run ID string
     */
    public String getRunId() {
        return runId;
    }
    
    /**
     * Gets the run timestamp.
     * 
     * @return Run timestamp string
     */
    public String getRunTimestamp() {
        return runTimestamp;
    }
    
    /**
     * Gets the base results path for this run.
     * 
     * @return Results path
     */
    public Path getResultsPath() {
        return resultsPath;
    }
    
    /**
     * Gets the logs path.
     * 
     * @return Logs path
     */
    public Path getLogsPath() {
        return logsPath;
    }
    
    /**
     * Gets the run-specific logs path.
     * 
     * @return Run-specific logs path
     */
    public Path getRunLogsPath() {
        return runLogsPath;
    }
    
    /**
     * Creates the run-specific directory structure.
     * 
     * @return true if directories were created successfully, false otherwise
     */
    public boolean createRunDirectories() {
        try {
            List<String> directories = List.of(
                resultsPath.toString(),
                resultsPath.resolve("raw_results").toString(),
                resultsPath.resolve("statistical_analysis").toString(),
                resultsPath.resolve("parameter_sensitivity").toString(),
                resultsPath.resolve("scalability_analysis").toString(),
                resultsPath.resolve("convergence_data").toString(),
                resultsPath.resolve("comparison_data").toString(),
                runLogsPath.toString(),
                resultsPath.resolve("backups").toString(),
                logsPath.toString()
            );
            
            for (String dir : directories) {
                Path path = Paths.get(dir);
                Files.createDirectories(path);
                logger.debug("Created directory: {}", path.toAbsolutePath());
            }
            
            // Create run-specific README file
            createRunReadme();
            
            logger.info("Run directories created successfully: {}", resultsPath.toAbsolutePath());
            logger.info("Run-specific logs directory: {}", runLogsPath.toAbsolutePath());
            return true;
            
        } catch (IOException e) {
            logger.error("Failed to create run directories", e);
            return false;
        }
    }
    
    /**
     * Copies log files to the run-specific logs directory.
     * This should be called at the end of the experiment.
     */
    public void copyLogsToRunDirectory() {
        try {
            logger.info("Copying log files to run-specific directory: {}", runLogsPath);
            
            // List of log files to copy
            List<String> logFiles = List.of(
                "run-" + runId + ".log",
                "errors.log", 
                "performance-metrics.log",
                "memory-usage.log"
            );
            
            for (String logFile : logFiles) {
                Path sourcePath = logsPath.resolve(logFile);
                Path targetPath = runLogsPath.resolve(logFile);
                
                if (Files.exists(sourcePath)) {
                    Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Copied log file: {} -> {}", sourcePath, targetPath);
                } else {
                    logger.debug("Log file not found: {}", sourcePath);
                }
            }
            
            logger.info("Log files copied successfully to run directory");
            
        } catch (IOException e) {
            logger.error("Failed to copy log files to run directory", e);
        }
    }
    
    /**
     * Creates a README file for this run.
     */
    private void createRunReadme() {
        try {
            Path readmePath = resultsPath.resolve("README.txt");
            List<String> readmeContent = List.of(
                "Hippopotamus Optimization Research Framework - Run Results",
                "Run ID: " + runId,
                "Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "",
                "Directory Structure:",
                "- raw_results/: Raw experimental data in CSV format",
                "- statistical_analysis/: Statistical test results and confidence intervals",
                "- parameter_sensitivity/: Parameter sensitivity analysis results",
                "- scalability_analysis/: Scalability test results",
                "- convergence_data/: Algorithm convergence tracking data",
                "- comparison_data/: Algorithm comparison results",
                "- logs/: Run-specific execution logs (this folder)",
                "- backups/: Backup copies of important results",
                "",
                "Log Files:",
                "- run-" + runId + ".log: Main execution log for this run",
                "- errors.log: Error messages and exceptions",
                "- performance-metrics.log: Performance monitoring data",
                "- memory-usage.log: Memory usage tracking"
            );
            Files.write(readmePath, readmeContent);
            logger.debug("Created README file for run: {}", runId);
        } catch (IOException e) {
            logger.warn("Failed to create README file for run", e);
        }
    }
    
    /**
     * Gets the path for a specific results subdirectory.
     * 
     * @param subdirectory Subdirectory name
     * @return Full path to the subdirectory
     */
    public Path getResultsSubdirectory(String subdirectory) {
        return resultsPath.resolve(subdirectory);
    }
    
    /**
     * Gets the path for the main results CSV file.
     * 
     * @return Path to main results CSV
     */
    public Path getMainResultsPath() {
        return resultsPath.resolve("raw_results").resolve("main_results.csv");
    }
    
    /**
     * Gets the path for the run-specific log file.
     * 
     * @return Path to run log file
     */
    public Path getRunLogPath() {
        return runLogsPath.resolve("run-" + runId + ".log");
    }
    
    /**
     * Cleans up the run manager and removes MDC context.
     */
    public void cleanup() {
        MDC.remove("run.id");
        logger.info("RunManager cleanup completed for Run ID: {}", runId);
    }
    
    /**
     * Resets the singleton instance (for testing purposes).
     */
    public static void reset() {
        synchronized (lock) {
            if (instance != null) {
                instance.cleanup();
            }
            instance = null;
        }
    }
} 