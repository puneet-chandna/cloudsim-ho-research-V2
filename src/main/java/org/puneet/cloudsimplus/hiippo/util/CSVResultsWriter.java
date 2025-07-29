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
    
    // Directory structure - will be set by RunManager
    private static String BASE_RESULTS_DIR;
    private static String RAW_RESULTS_DIR;
    private static String STATISTICAL_DIR;
    private static String PARAMETER_SENSITIVITY_DIR;
    private static String SCALABILITY_DIR;
    private static String CONVERGENCE_DIR;
    private static String COMPARISON_DIR;
    
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
        // Initialize with default paths first
        initializeDefaultPaths();
        initializeFileLocks();
    }
    
    /**
     * Initializes the CSVResultsWriter with run-specific paths.
     * This method should be called after RunManager is initialized.
     */
    public static void initializeWithRunManager() {
        try {
            RunManager runManager = RunManager.getInstance();
            
            // Set base directories
            BASE_RESULTS_DIR = runManager.getResultsPath().toString();
            RAW_RESULTS_DIR = runManager.getResultsSubdirectory("raw_results").toString();
            STATISTICAL_DIR = runManager.getResultsSubdirectory("statistical_analysis").toString();
            PARAMETER_SENSITIVITY_DIR = runManager.getResultsSubdirectory("parameter_sensitivity").toString();
            SCALABILITY_DIR = runManager.getResultsSubdirectory("scalability_analysis").toString();
            CONVERGENCE_DIR = runManager.getResultsSubdirectory("convergence_data").toString();
            COMPARISON_DIR = runManager.getResultsSubdirectory("comparison_data").toString();
            
            // Create directories if they don't exist
            createDirectoriesIfNotExist();
            
            logger.info("CSVResultsWriter initialized with run ID: {}", runManager.getRunId());
            logger.info("Results base directory: {}", BASE_RESULTS_DIR);
            
        } catch (Exception e) {
            logger.error("Failed to initialize CSVResultsWriter with RunManager", e);
            // Fall back to default paths
            initializeDefaultPaths();
        }
    }
    
    /**
     * Initializes with default paths for backward compatibility.
     */
    private static void initializeDefaultPaths() {
        BASE_RESULTS_DIR = "results";
        RAW_RESULTS_DIR = BASE_RESULTS_DIR + "/raw_results";
        STATISTICAL_DIR = BASE_RESULTS_DIR + "/statistical_analysis";
        PARAMETER_SENSITIVITY_DIR = BASE_RESULTS_DIR + "/parameter_sensitivity";
        SCALABILITY_DIR = BASE_RESULTS_DIR + "/scalability_analysis";
        CONVERGENCE_DIR = BASE_RESULTS_DIR + "/convergence_data";
        COMPARISON_DIR = BASE_RESULTS_DIR + "/comparison_data";
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
     * Creates all necessary directories for results storage.
     */
    private static void createDirectoriesIfNotExist() {
        try {
            Files.createDirectories(Paths.get(RAW_RESULTS_DIR));
            Files.createDirectories(Paths.get(STATISTICAL_DIR));
            Files.createDirectories(Paths.get(PARAMETER_SENSITIVITY_DIR));
            Files.createDirectories(Paths.get(SCALABILITY_DIR));
            Files.createDirectories(Paths.get(CONVERGENCE_DIR));
            Files.createDirectories(Paths.get(COMPARISON_DIR));
            
            logger.debug("Created all results directories");
        } catch (IOException e) {
            logger.error("Failed to create results directories", e);
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
        
        // DEBUG: Log the values being written to CSV
        logger.info("DEBUG: CSV Writing - Algorithm: {}, Scenario: {}, Replication: {}", 
                   result.getAlgorithm(), result.getScenario(), result.getReplication());
        logger.info("DEBUG: CSV Writing - VmAllocated: {}, VmTotal: {}", 
                   result.getVmAllocated(), result.getVmTotal());
        
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
        
        // DEBUG: Log the record being written
        logger.info("DEBUG: CSV Record: {}", String.join(",", record));
        
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
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                
                if (!fileExists) {
                    // Write header only for new files
                    printer.printRecord(format.getHeader());
                }
                
                for (String[] record : records) {
                    printer.printRecord(Arrays.asList(record).toArray());
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
     * Writes parameter tuning results to a CSV file. The data map should have a single key (sheet name) mapping to a list of row maps.
     * Each row map represents a row, with keys as column headers.
     */
    public void writeParameterTuningResults(String filePath, Map<String, List<Map<String, Object>>> data) {
        if (data == null || data.isEmpty()) return;
        for (Map.Entry<String, List<Map<String, Object>>> entry : data.entrySet()) {
            List<Map<String, Object>> rows = entry.getValue();
            if (rows == null || rows.isEmpty()) continue;
            Set<String> headers = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) headers.addAll(row.keySet());
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])))) {
                for (Map<String, Object> row : rows) {
                    List<Object> values = new ArrayList<>();
                    for (String h : headers) values.add(row.getOrDefault(h, ""));
                    printer.printRecord(values);
                }
            } catch (Exception e) {
                logger.error("Failed to write parameter tuning results to {}", filePath, e);
            }
        }
    }

    /**
     * Appends a single intermediate result row to a CSV file. If the file does not exist, headers are written.
     */
    public void appendIntermediateResult(String filePath, Map<String, Object> row) {
        if (row == null || row.isEmpty()) return;
        Set<String> headers = row.keySet();
        boolean fileExists = Files.exists(Paths.get(filePath));
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])))) {
            if (!fileExists) printer.printRecord(headers);
            List<Object> values = new ArrayList<>();
            for (String h : headers) values.add(row.getOrDefault(h, ""));
            printer.printRecord(values);
        } catch (Exception e) {
            logger.error("Failed to append intermediate result to {}", filePath, e);
        }
    }

    /**
     * Writes a summary row to a CSV file. Overwrites the file.
     */
    public void writeTuningSummary(String filePath, Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) return;
        Set<String> headers = summary.keySet();
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])))) {
            printer.printRecord(summary.values());
        } catch (Exception e) {
            logger.error("Failed to write tuning summary to {}", filePath, e);
        }
    }
    
    public static void writeSummaryStatistics(Map<String, List<ExperimentResult>> allResults) throws IOException {
        String filePath = STATISTICAL_DIR + "/summary_statistics.csv";
        CSVFormat format = CSVFormat.DEFAULT
            .withHeader("Algorithm", "Scenario", "Metric", "Mean", "StdDev", "Min", "Max", "Count")
            .withRecordSeparator("\n");
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            
            for (Map.Entry<String, List<ExperimentResult>> entry : allResults.entrySet()) {
                String algorithm = entry.getKey();
                List<ExperimentResult> results = entry.getValue();
                
                // Calculate statistics for each metric
                calculateAndWriteStatistics(printer, algorithm, results, "ResourceUtilCPU", 
                    ExperimentResult::getResourceUtilCPU);
                calculateAndWriteStatistics(printer, algorithm, results, "ResourceUtilRAM", 
                    ExperimentResult::getResourceUtilRAM);
                calculateAndWriteStatistics(printer, algorithm, results, "PowerConsumption", 
                    ExperimentResult::getPowerConsumption);
                calculateAndWriteStatistics(printer, algorithm, results, "ExecutionTime", 
                    ExperimentResult::getExecutionTime);
            }
        }
    }
    
    private static void calculateAndWriteStatistics(CSVPrinter printer, String algorithm, 
            List<ExperimentResult> results, String metricName, 
            java.util.function.Function<ExperimentResult, Double> getter) throws IOException {
        if (results.isEmpty()) return;
        
        List<Double> values = results.stream()
            .map(getter)
            .filter(v -> v != null && !Double.isNaN(v))
            .collect(Collectors.toList());
        
        if (values.isEmpty()) return;
        
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        printer.printRecord(algorithm, "ALL", metricName, mean, stdDev, min, max, values.size());
    }
    
    public static void writeScalabilityAnalysis(String algorithm, 
            Map<String, List<ExperimentResult>> allResults,
            Map<String, Object> scenarioSpecs) throws IOException {
        String filePath = SCALABILITY_DIR + "/scalability_analysis.csv";
        CSVFormat format = CSVFormat.DEFAULT
            .withHeader("Algorithm", "VmCount", "HostCount", "ExecutionTime", "MemoryUsage", 
                       "CpuUtilization", "QualityScore", "SuccessRate")
            .withRecordSeparator("\n");
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath));
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            
            for (Map.Entry<String, List<ExperimentResult>> entry : allResults.entrySet()) {
                List<ExperimentResult> results = entry.getValue();
                for (ExperimentResult result : results) {
                    // Extract scalability metrics from result
                    printer.printRecord(
                        algorithm,
                        result.getVmTotal(),
                        result.getHosts() != null ? result.getHosts().size() : 0,
                        result.getExecutionTime(),
                        0.0, // Memory usage placeholder
                        result.getResourceUtilCPU(),
                        0.0, // Quality score placeholder
                        1.0  // Success rate placeholder
                    );
                }
            }
        }
    }
    
    /**
     * Experiment result data structure for CSV writing.
     */
    public static class ExperimentResult {
        private String algorithm;
        private String scenario;
        private int replication;
        private double resourceUtilCPU;
        private double resourceUtilRAM;
        private double powerConsumption;
        private int slaViolations;
        private double executionTime;
        private int convergenceIterations;
        private int vmAllocated;
        private int vmTotal;
        private String experimentId;
        private LocalDateTime timestamp;
        private Map<String, Object> parameters;
        private boolean failed;
        private Map<String, Object> metrics;
        // Add for validation only (not for CSV):
        private List<org.cloudsimplus.vms.Vm> vms;
        private List<org.cloudsimplus.hosts.Host> hosts;

        public ExperimentResult() {
            this.algorithm = "";
            this.scenario = "";
            this.replication = 0;
            this.resourceUtilCPU = 0.0;
            this.resourceUtilRAM = 0.0;
            this.powerConsumption = 0.0;
            this.slaViolations = 0;
            this.executionTime = 0.0;
            this.convergenceIterations = 0;
            this.vmAllocated = 0;
            this.vmTotal = 0;
            this.experimentId = "";
            this.timestamp = LocalDateTime.now();
            this.parameters = new HashMap<>();
            this.failed = false;
            this.metrics = new HashMap<>();
            this.vms = null;
            this.hosts = null;
        }

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
            this.experimentId = "";
            this.timestamp = LocalDateTime.now();
            this.parameters = new HashMap<>();
            this.failed = false;
            this.metrics = new HashMap<>();
            this.vms = null;
            this.hosts = null;
        }
        // Overloaded constructor for validation
        public ExperimentResult(String algorithm, String scenario, int replication,
                              double resourceUtilCPU, double resourceUtilRAM,
                              double powerConsumption, int slaViolations,
                              double executionTime, int convergenceIterations,
                              int vmAllocated, int vmTotal,
                              List<org.cloudsimplus.vms.Vm> vms,
                              List<org.cloudsimplus.hosts.Host> hosts) {
            this(algorithm, scenario, replication, resourceUtilCPU, resourceUtilRAM, powerConsumption, slaViolations, executionTime, convergenceIterations, vmAllocated, vmTotal);
            this.vms = vms;
            this.hosts = hosts;
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
        // Validation only
        public List<org.cloudsimplus.vms.Vm> getVms() { return vms; }
        public List<org.cloudsimplus.hosts.Host> getHosts() { return hosts; }
        public void setVms(List<org.cloudsimplus.vms.Vm> vms) { this.vms = vms; }
        public void setHosts(List<org.cloudsimplus.hosts.Host> hosts) { this.hosts = hosts; }
        
        // Additional getters and setters
        public String getExperimentId() { return experimentId; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getParameters() { return parameters; }
        public boolean isFailed() { return failed; }
        public Map<String, Object> getMetrics() { return metrics; }
        
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        public void setScenario(String scenario) { this.scenario = scenario; }
        public void setReplication(int replication) { this.replication = replication; }
        public void setResourceUtilCPU(Double resourceUtilCPU) { this.resourceUtilCPU = resourceUtilCPU != null ? resourceUtilCPU : 0.0; }
        public void setResourceUtilRAM(Double resourceUtilRAM) { this.resourceUtilRAM = resourceUtilRAM != null ? resourceUtilRAM : 0.0; }
        public void setPowerConsumption(Double powerConsumption) { this.powerConsumption = powerConsumption != null ? powerConsumption : 0.0; }
        public void setSlaViolations(int slaViolations) { this.slaViolations = slaViolations; }
        public void setExecutionTime(double executionTime) { this.executionTime = executionTime; }
        public void setConvergenceIterations(int convergenceIterations) { this.convergenceIterations = convergenceIterations; }
        public void setVmAllocated(int vmAllocated) { this.vmAllocated = vmAllocated; }
        public void setVmTotal(int vmTotal) { this.vmTotal = vmTotal; }
        public void setExperimentId(String experimentId) { this.experimentId = experimentId; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public void setTimestamp(java.time.Instant timestamp) { this.timestamp = timestamp.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(); }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public void setFailed(boolean failed) { this.failed = failed; }
        public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
        
        // Additional setters for specific metrics
        public void setFinalFitness(double finalFitness) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("finalFitness", finalFitness); 
        }
        public void setAvgCloudletExecutionTime(Double avgCloudletExecutionTime) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("avgCloudletExecutionTime", avgCloudletExecutionTime); 
        }
        public void setAvgHostEfficiency(Double avgHostEfficiency) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("avgHostEfficiency", avgHostEfficiency); 
        }
        public void setPeakMemoryUsageMB(Double peakMemoryUsageMB) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("peakMemoryUsageMB", peakMemoryUsageMB); 
        }
        public void setValid(boolean valid) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("valid", valid); 
        }
        
        public void setResourceUtilizationCPU(Double resourceUtilCPU) { this.resourceUtilCPU = resourceUtilCPU != null ? resourceUtilCPU : 0.0; }
        public void setResourceUtilizationRAM(Double resourceUtilRAM) { this.resourceUtilRAM = resourceUtilRAM != null ? resourceUtilRAM : 0.0; }
        public void setAllocationTime(double allocationTime) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("allocationTime", allocationTime);
        }
        public void setMemoryUsed(long memoryUsed) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("memoryUsed", memoryUsed);
        }
        public void setSimulationTime(double simulationTime) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("simulationTime", simulationTime);
        }
        public void setValidationMessage(String validationMessage) { 
            if (this.metrics == null) this.metrics = new HashMap<>();
            this.metrics.put("validationMessage", validationMessage);
        }
        public Double getResourceUtilizationCPU() { return resourceUtilCPU; }
        public Double getResourceUtilizationRAM() { return resourceUtilRAM; }
    }
}