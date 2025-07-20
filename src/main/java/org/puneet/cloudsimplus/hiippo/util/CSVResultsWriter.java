package org.puneet.cloudsimplus.hiippo.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Comprehensive CSV writer for experimental results with statistical analysis support.
 * Handles all result types including main results, statistical analysis, parameter sensitivity,
 * scalability analysis, and convergence data.
 * 
 * <p>This class provides thread-safe CSV writing capabilities with automatic file creation,
 * backup generation, and data validation. It supports batch writing for memory efficiency
 * and maintains data integrity across multiple experiment runs.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-20
 */
public class CSVResultsWriter {
    private static final Logger logger = LoggerFactory.getLogger(CSVResultsWriter.class);
    
    // Directory structure
    private static final String BASE_RESULTS_DIR = "results";
    private static final String RAW_RESULTS_DIR = BASE_RESULTS_DIR + "/raw_results";
    private static final String STATISTICAL_DIR = BASE_RESULTS_DIR + "/statistical_analysis";
    private static final String PARAMETER_SENSITIVITY_DIR = BASE_RESULTS_DIR + "/parameter_sensitivity";
    private static final String SCALABILITY_DIR = BASE_RESULTS_DIR + "/scalability_analysis";
    private static final String CONVERGENCE_DIR = BASE_RESULTS_DIR + "/convergence_data";
    private static final String COMPARISON_DIR = BASE_RESULTS_DIR + "/comparison_data";
    
    // File names
    private static final String MAIN_RESULTS_FILE = "main_results.csv";
    private static final String SIGNIFICANCE_TESTS_FILE = "significance_tests.csv";
    private static final String CONFIDENCE_INTERVALS_FILE = "confidence_intervals.csv";
    private static final String EFFECT_SIZES_FILE = "effect_sizes.csv";
    private static final String POPULATION_ANALYSIS_FILE = "population_size_analysis.csv";
    private static final String ITERATION_ANALYSIS_FILE = "iteration_count_analysis.csv";
    private static final String CONVERGENCE_ANALYSIS_FILE = "convergence_analysis.csv";
    private static final String TIME_COMPLEXITY_FILE = "time_complexity.csv";
    private static final String QUALITY_DEGRADATION_FILE = "quality_degradation.csv";
    private static final String CONVERGENCE_DATA_FILE = "convergence_data.csv";
    
    // CSV formats
    private static final CSVFormat MAIN_FORMAT = CSVFormat.DEFAULT
        .withHeader("Algorithm", "Scenario", "Replication", "ResourceUtilCPU", "ResourceUtilRAM",
                   "PowerConsumption", "SLAViolations", "ExecutionTime", "ConvergenceIterations",
                   "VmAllocated", "VmTotal", "Timestamp")
        .withRecordSeparator("\n");
    
    private static final CSVFormat STATISTICAL_FORMAT = CSVFormat.DEFAULT
        .withHeader("Algorithm1", "Algorithm2", "Scenario", "Metric", "PValue", "Significant",
                   "EffectSize", "ConfidenceIntervalLower", "ConfidenceIntervalUpper", "Timestamp")
        .withRecordSeparator("\n");
    
    private static final CSVFormat CONFIDENCE_INTERVAL_FORMAT = CSVFormat.DEFAULT
        .withHeader("Algorithm", "Scenario", "Metric", "Mean", "StdDev", "ConfidenceLevel",
                   "LowerBound", "UpperBound", "SampleSize", "Timestamp")
        .withRecordSeparator("\n");
    
    private static final CSVFormat SCALABILITY_FORMAT = CSVFormat.DEFAULT
        .withHeader("Algorithm", "VmCount", "HostCount", "ExecutionTime", "MemoryUsage",
                   "CpuUtilization", "QualityScore", "SuccessRate", "Timestamp")
        .withRecordSeparator("\n");
    
    private static final CSVFormat CONVERGENCE_FORMAT = CSVFormat.DEFAULT
        .withHeader("Algorithm", "Scenario", "Replication", "Iteration", "BestFitness",
                   "AverageFitness", "PopulationDiversity", "ImprovementRate", "Timestamp")
        .withRecordSeparator("\n");
    
    // Thread-safe file locks
    private static final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();
    
    // Batch processing buffer
    private static final Map<String, List<String[]>> writeBuffers = new ConcurrentHashMap<>();
    private static final int BATCH_SIZE = 100;
    
    // Statistics tracking
    private static final Map<String, Integer> recordCounts = new ConcurrentHashMap<>();
    
    static {
        initializeDirectories();
        initializeFileLocks();
    }
    
    /**
     * Initializes all required directories for results storage.
     * Creates directories if they don't exist and handles permission issues.
     */
    private static void initializeDirectories() {
        try {
            List<String> directories = Arrays.asList(
                RAW_RESULTS_DIR, STATISTICAL_DIR, PARAMETER_SENSITIVITY_DIR,
                SCALABILITY_DIR, CONVERGENCE_DIR, COMPARISON_DIR
            );
            
            for (String dir : directories) {
                Path path = Paths.get(dir);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    logger.info("Created directory: {}", path.toAbsolutePath());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to create results directories", e);
            throw new RuntimeException("Cannot initialize results directories", e);
        }
    }
    
    /**
     * Initializes file locks for thread-safe writing.
     */
    private static void initializeFileLocks() {
        List<String> files = Arrays.asList(
            MAIN_RESULTS_FILE, SIGNIFICANCE_TESTS_FILE, CONFIDENCE_INTERVALS_FILE,
            EFFECT_SIZES_FILE, POPULATION_ANALYSIS_FILE, ITERATION_ANALYSIS_FILE,
            CONVERGENCE_ANALYSIS_FILE, TIME_COMPLEXITY_FILE, QUALITY_DEGRADATION_FILE,
            CONVERGENCE_DATA_FILE
        );
        
        for (String file : files) {
            fileLocks.put(file, new ReentrantLock());
        }
    }
    
    /**
     * Writes a single experiment result to the main results file.
     * 
     * @param result The experiment result to write
     * @throws IOException if writing fails
     */
    public static void writeResult(ExperimentResult result) throws IOException {
        Objects.requireNonNull(result, "Result cannot be null");
        
        String[] record = new String[] {
            result.getAlgorithm(),
            result.getScenario(),
            String.valueOf(result.getReplication()),
            String.format("%.4f", result.getResourceUtilCPU()),
            String.format("%.4f", result.getResourceUtilRAM()),
            String.format("%.2f", result.getPowerConsumption()),
            String.valueOf(result.getSlaViolations()),
            String.format("%.2f", result.getExecutionTime()),
            String.valueOf(result.getConvergenceIterations()),
            String.valueOf(result.getVmAllocated()),
            String.valueOf(result.getVmTotal()),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        };
        
        writeRecord(RAW_RESULTS_DIR + "/" + MAIN_RESULTS_FILE, MAIN_FORMAT, record);
    }
    
    /**
     * Writes statistical comparison results between algorithms.
     * 
     * @param algorithm1 First algorithm name
     * @param algorithm2 Second algorithm name
     * @param scenario Scenario name
     * @param metric Metric being compared
     * @param pValue P-value from statistical test
     * @param significant Whether the difference is statistically significant
     * @param effectSize Effect size measure
     * @param confidenceInterval Confidence interval for the difference
     * @throws IOException if writing fails
     */
    public static void writeSignificanceTest(String algorithm1, String algorithm2, String scenario,
                                           String metric, double pValue, boolean significant,
                                           double effectSize, double[] confidenceInterval) 
                                           throws IOException {
        Objects.requireNonNull(algorithm1, "Algorithm1 cannot be null");
        Objects.requireNonNull(algorithm2, "Algorithm2 cannot be null");
        Objects.requireNonNull(scenario, "Scenario cannot be null");
        Objects.requireNonNull(metric, "Metric cannot be null");
        Objects.requireNonNull(confidenceInterval, "Confidence interval cannot be null");
        
        String[] record = new String[] {
            algorithm1,
            algorithm2,
            scenario,
            metric,
            String.format("%.6f", pValue),
            String.valueOf(significant),
            String.format("%.4f", effectSize),
            String.format("%.4f", confidenceInterval[0]),
            String.format("%.4f", confidenceInterval[1]),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        };
        
        writeRecord(STATISTICAL_DIR + "/" + SIGNIFICANCE_TESTS_FILE, STATISTICAL_FORMAT, record);
    }
    
    /**
     * Writes confidence interval results for a specific metric.
     * 
     * @param algorithm Algorithm name
     * @param scenario Scenario name
     * @param metric Metric name
     * @param mean Mean value
     * @param stdDev Standard deviation
     * @param confidenceLevel Confidence level (e.g., 0.95)
     * @param lowerBound Lower bound of confidence interval
     * @param upperBound Upper bound of confidence interval
     * @param sampleSize Sample size used for calculation
     * @throws IOException if writing fails
     */
    public static void writeConfidenceInterval(String algorithm, String scenario, String metric,
                                             double mean, double stdDev, double confidenceLevel,
                                             double lowerBound, double upperBound, int sampleSize) 
                                             throws IOException {
        String[] record = new String[] {
            algorithm,
            scenario,
            metric,
            String.format("%.4f", mean),
            String.format("%.4f", stdDev),
            String.format("%.2f", confidenceLevel),
            String.format("%.4f", lowerBound),
            String.format("%.4f", upperBound),
            String.valueOf(sampleSize),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        };
        
        writeRecord(STATISTICAL_DIR + "/" + CONFIDENCE_INTERVALS_FILE, CONFIDENCE_INTERVAL_FORMAT, record);
    }
    
    /**
     * Writes scalability test results.
     * 
     * @param algorithm Algorithm name
     * @param vmCount Number of VMs
     * @param hostCount Number of hosts
     * @param executionTime Execution time in seconds
     * @param memoryUsage Memory usage in MB
     * @param cpuUtilization CPU utilization percentage
     * @param qualityScore Quality score (normalized)
     * @param successRate Success rate percentage
     * @throws IOException if writing fails
     */
    public static void writeScalabilityResult(String algorithm, int vmCount, int hostCount,
                                            double executionTime, double memoryUsage,
                                            double cpuUtilization, double qualityScore,
                                            double successRate) throws IOException {
        String[] record = new String[] {
            algorithm,
            String.valueOf(vmCount),
            String.valueOf(hostCount),
            String.format("%.2f", executionTime),
            String.format("%.2f", memoryUsage),
            String.format("%.2f", cpuUtilization),
            String.format("%.4f", qualityScore),
            String.format("%.2f", successRate),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        };
        
        writeRecord(SCALABILITY_DIR + "/" + TIME_COMPLEXITY_FILE, SCALABILITY_FORMAT, record);
    }
    
    /**
     * Writes convergence data for a specific algorithm run.
     * 
     * @param algorithm Algorithm name
     * @param scenario Scenario name
     * @param replication Replication number
     * @param iteration Iteration number
     * @param bestFitness Best fitness value at this iteration
     * @param averageFitness Average fitness of population
     * @param populationDiversity Population diversity measure
     * @param improvementRate Rate of improvement from previous iteration
     * @throws IOException if writing fails
     */
    public static void writeConvergenceData(String algorithm, String scenario, int replication,
                                          int iteration, double bestFitness, double averageFitness,
                                          double populationDiversity, double improvementRate) 
                                          throws IOException {
        String[] record = new String[] {
            algorithm,
            scenario,
            String.valueOf(replication),
            String.valueOf(iteration),
            String.format("%.6f", bestFitness),
            String.format("%.6f", averageFitness),
            String.format("%.4f", populationDiversity),
            String.format("%.6f", improvementRate),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        };
        
        writeRecord(CONVERGENCE_DIR + "/" + CONVERGENCE_DATA_FILE, CONVERGENCE_FORMAT, record);
    }
    
    /**
     * Writes parameter sensitivity analysis results.
     * 
     * @param parameter Parameter name
     * @param value Parameter value
     * @param scenario Scenario name
     * @param metric Metric being measured
     * @param meanValue Mean value for this parameter
     * @param stdDev Standard deviation
     * @param sampleSize Number of samples
     * @throws IOException if writing fails
     */
    public static void writeParameterSensitivity(String parameter, double value, String scenario,
                                               String metric, double meanValue, double stdDev,
                                               int sampleSize) throws IOException {
        String[] record = new String[] {
            parameter,
            String.format("%.4f", value),
            scenario,
            metric,
            String.format("%.4f", meanValue),
            String.format("%.4f", stdDev),
            String.valueOf(sampleSize),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        };
        
        String fileName = parameter.toLowerCase().contains("population") ? 
            POPULATION_ANALYSIS_FILE : ITERATION_ANALYSIS_FILE;
        
        writeRecord(PARAMETER_SENSITIVITY_DIR + "/" + fileName, 
                   CSVFormat.DEFAULT.withHeader("Parameter", "Value", "Scenario", "Metric",
                       "MeanValue", "StdDev", "SampleSize", "Timestamp"), record);
    }
    
    /**
     * Writes a batch of records efficiently.
     * 
     * @param filePath Path to the CSV file
     * @param format CSV format specification
     * @param records List of records to write
     * @throws IOException if writing fails
     */
    public static void writeBatch(String filePath, CSVFormat format, List<String[]> records) 
                                 throws IOException {
        Objects.requireNonNull(filePath, "File path cannot be null");
        Objects.requireNonNull(format, "Format cannot be null");
        Objects.requireNonNull(records, "Records cannot be null");
        
        if (records.isEmpty()) {
            return;
        }
        
        ReentrantLock lock = fileLocks.getOrDefault(Paths.get(filePath).getFileName().toString(), 
                                                   new ReentrantLock());
        lock.lock();
        
        try {
            Path path = Paths.get(filePath);
            boolean fileExists = Files.exists(path);
            
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
                 CSVPrinter printer = new CSVPrinter(writer, format)) {
                
                if (!fileExists) {
                    printer.printRecord(format.getHeader());
                }
                
                for (String[] record : records) {
                    printer.printRecord((Object[]) record);
                }
                
                printer.flush();
            }
            
            // Update record count
            String fileName = path.getFileName().toString();
            recordCounts.merge(fileName, records.size(), Integer::sum);
            
            logger.debug("Wrote {} records to {}", records.size(), filePath);
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Reads all records from a CSV file.
     * 
     * @param filePath Path to the CSV file
     * @return List of CSV records
     * @throws IOException if reading fails
     */
    public static List<CSVRecord> readRecords(String filePath) throws IOException {
        Objects.requireNonNull(filePath, "File path cannot be null");
        
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        
        try (Reader reader = Files.newBufferedReader(path)) {
            return CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(reader)
                .getRecords();
        }
    }
    
    /**
     * Creates a backup of all results files.
     * 
     * @param backupName Name for the backup directory
     * @throws IOException if backup creation fails
     */
    public static void createBackup(String backupName) throws IOException {
        Objects.requireNonNull(backupName, "Backup name cannot be null");
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupDir = BASE_RESULTS_DIR + "/backups/" + backupName + "_" + timestamp;
        
        Files.createDirectories(Paths.get(backupDir));
        
        // Copy all files to backup directory
        List<String> directories = Arrays.asList(
            RAW_RESULTS_DIR, STATISTICAL_DIR, PARAMETER_SENSITIVITY_DIR,
            SCALABILITY_DIR, CONVERGENCE_DIR, COMPARISON_DIR
        );
        
        for (String dir : directories) {
            Path sourceDir = Paths.get(dir);
            if (Files.exists(sourceDir)) {
                Path targetDir = Paths.get(backupDir, sourceDir.getFileName().toString());
                Files.createDirectories(targetDir);
                
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
                    for (Path file : stream) {
                        Files.copy(file, targetDir.resolve(file.getFileName()), 
                                  StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        
        logger.info("Created backup at: {}", backupDir);
    }
    
    /**
     * Gets summary statistics for all result files.
     * 
     * @return Map of file names to record counts
     */
    public static Map<String, Integer> getSummary() {
        return new HashMap<>(recordCounts);
    }
    
    /**
     * Validates the integrity of all CSV files.
     * 
     * @return List of validation errors
     */
    public static List<String> validateFiles() {
        List<String> errors = new ArrayList<>();
        
        List<String> files = Arrays.asList(
            RAW_RESULTS_DIR + "/" + MAIN_RESULTS_FILE,
            STATISTICAL_DIR + "/" + SIGNIFICANCE_TESTS_FILE,
            STATISTICAL_DIR + "/" + CONFIDENCE_INTERVALS_FILE,
            SCALABILITY_DIR + "/" + TIME_COMPLEXITY_FILE,
            CONVERGENCE_DIR + "/" + CONVERGENCE_DATA_FILE
        );
        
        for (String file : files) {
            Path path = Paths.get(file);
            if (Files.exists(path)) {
                try {
                    List<CSVRecord> records = readRecords(file);
                    if (records.isEmpty()) {
                        errors.add("Empty file: " + file);
                    }
                } catch (IOException e) {
                    errors.add("Invalid file: " + file + " - " + e.getMessage());
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Writes a single record to a CSV file.
     * 
     * @param filePath Path to the CSV file
     * @param format CSV format specification
     * @param record Record to write
     * @throws IOException if writing fails
     */
    private static void writeRecord(String filePath, CSVFormat format, String[] record) 
                                   throws IOException {
        writeBatch(filePath, format, Collections.singletonList(record));
    }
    
    /**
     * Clears all result files (use with caution).
     */
    public static void clearAllResults() {
        logger.warn("Clearing all result files");
        
        List<String> directories = Arrays.asList(
            RAW_RESULTS_DIR, STATISTICAL_DIR, PARAMETER_SENSITIVITY_DIR,
            SCALABILITY_DIR, CONVERGENCE_DIR, COMPARISON_DIR
        );
        
        for (String dir : directories) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(dir))) {
                for (Path file : stream) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                logger.error("Failed to clear directory: {}", dir, e);
            }
        }
        
        recordCounts.clear();
        logger.info("All result files cleared");
    }
    
    /**
     * Experiment result data structure for CSV writing.
     */
    public static class ExperimentResult {
        private final String algorithm;
        private final String scenario;
        private final int replication;
        private final double resourceUtilCPU;
        private final double resourceUtilRAM;
        private final double powerConsumption;
        private final int slaViolations;
        private final double executionTime;
        private final int convergenceIterations;
        private final int vmAllocated;
        private final int vmTotal;
        
        public ExperimentResult(String algorithm, String scenario, int replication,
                              double resourceUtilCPU, double resourceUtilRAM,
                              double powerConsumption, int slaViolations,
                              double executionTime, int convergenceIterations,
                              int vmAllocated, int vmTotal) {
            this.algorithm = Objects.requireNonNull(algorithm);
            this.scenario = Objects.requireNonNull(scenario);
            this.replication = replication;
            this.resourceUtilCPU = resourceUtilCPU;
            this.resourceUtilRAM = resourceUtilRAM;
            this.powerConsumption = powerConsumption;
            this.slaViolations = slaViolations;
            this.executionTime = executionTime;
            this.convergenceIterations = convergenceIterations;
            this.vmAllocated = vmAllocated;
            this.vmTotal = vmTotal;
        }
        
        // Getters
        public String getAlgorithm() { return algorithm; }
        public String getScenario() { return scenario; }
        public int getReplication() { return replication; }
        public double getResourceUtilCPU() { return resourceUtilCPU; }
        public double getResourceUtilRAM() { return resourceUtilRAM; }
        public double getPowerConsumption() { return powerConsumption; }
        public int getSlaViolations() { return slaViolations; }
        public double getExecutionTime() { return executionTime; }
        public int getConvergenceIterations() { return convergenceIterations; }
        public int getVmAllocated() { return vmAllocated; }
        public int getVmTotal() { return vmTotal; }
    }
}