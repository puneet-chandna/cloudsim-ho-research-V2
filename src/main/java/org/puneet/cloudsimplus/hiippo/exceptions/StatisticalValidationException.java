package org.puneet.cloudsimplus.hiippo.exceptions;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exception class for statistical validation failures in the CloudSim HO Research Framework.
 * This exception handles errors related to statistical tests, confidence intervals,
 * significance testing, and statistical assumptions validation.
 * 
 * @author CloudSim Plus HO Research Framework
 * @version 1.0.0
 * @since CloudSim Plus 8.0.0
 */
public class StatisticalValidationException extends Exception implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final Logger LOGGER = Logger.getLogger(StatisticalValidationException.class.getName());
    
    /**
     * Types of statistical validation failures
     */
    public enum StatisticalErrorType {
        INSUFFICIENT_SAMPLE_SIZE("STAT001", "Sample size too small for statistical validity"),
        NORMALITY_VIOLATION("STAT002", "Data does not follow normal distribution"),
        HOMOGENEITY_VIOLATION("STAT003", "Variance homogeneity assumption violated"),
        INDEPENDENCE_VIOLATION("STAT004", "Independence assumption violated"),
        CONFIDENCE_INTERVAL_ERROR("STAT005", "Confidence interval calculation failed"),
        SIGNIFICANCE_TEST_ERROR("STAT006", "Significance test execution failed"),
        ANOVA_ASSUMPTION_VIOLATION("STAT007", "ANOVA assumptions not met"),
        EFFECT_SIZE_ERROR("STAT008", "Effect size calculation failed"),
        OUTLIER_DETECTION_ERROR("STAT009", "Outlier detection failed"),
        CORRELATION_ERROR("STAT010", "Correlation analysis failed"),
        REGRESSION_ERROR("STAT011", "Regression analysis failed"),
        DISTRIBUTION_FIT_ERROR("STAT012", "Distribution fitting failed"),
        INVALID_P_VALUE("STAT013", "Invalid p-value calculated"),
        INVALID_TEST_STATISTIC("STAT014", "Invalid test statistic calculated"),
        MISSING_DATA_ERROR("STAT015", "Missing data handling failed"),
        CONVERGENCE_TEST_ERROR("STAT016", "Convergence test failed"),
        INVALID_CONFIDENCE_LEVEL("STAT017", "Invalid confidence level specified"),
        DATA_QUALITY_ERROR("STAT018", "Data quality issues detected"),
        INSUFFICIENT_REPLICATIONS("STAT019", "Insufficient replications for analysis"),
        INVALID_STATISTICAL_MODEL("STAT020", "Invalid statistical model specified");
        
        private final String code;
        private final String description;
        
        StatisticalErrorType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Statistical test result information
     */
    public static class StatisticalTestResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private final String testName;
        private final double testStatistic;
        private final double pValue;
        private final double criticalValue;
        private final boolean rejected;
        private final Map<String, Double> additionalStats;
        
        public StatisticalTestResult(String testName, double testStatistic, 
                                   double pValue, double criticalValue, 
                                   boolean rejected) {
            this.testName = testName;
            this.testStatistic = testStatistic;
            this.pValue = pValue;
            this.criticalValue = criticalValue;
            this.rejected = rejected;
            this.additionalStats = new HashMap<>();
        }
        
        public void addStatistic(String name, double value) {
            additionalStats.put(name, value);
        }
        
        public String getTestName() {
            return testName;
        }
        
        public double getTestStatistic() {
            return testStatistic;
        }
        
        public double getPValue() {
            return pValue;
        }
        
        public double getCriticalValue() {
            return criticalValue;
        }
        
        public boolean isRejected() {
            return rejected;
        }
        
        public Map<String, Double> getAdditionalStats() {
            return Collections.unmodifiableMap(additionalStats);
        }
        
        @Override
        public String toString() {
            return String.format("%s: statistic=%.4f, p-value=%.4f, critical=%.4f, rejected=%s",
                    testName, testStatistic, pValue, criticalValue, rejected);
        }
    }
    
    private final StatisticalErrorType errorType;
    private final LocalDateTime timestamp;
    private final String datasetName;
    private final Integer sampleSize;
    private final Double significanceLevel;
    private final StatisticalTestResult testResult;
    private final Map<String, Object> statisticalContext;
    private final List<String> violatedAssumptions;
    
    /**
     * Constructs a new StatisticalValidationException with error type and message.
     * 
     * @param errorType The type of statistical error
     * @param message The detailed error message
     * @throws NullPointerException if errorType is null
     */
    public StatisticalValidationException(StatisticalErrorType errorType, String message) {
        this(errorType, message, null, null, null, null, null, null, new HashMap<>());
    }
    
    /**
     * Constructs a new StatisticalValidationException with error type, message, and cause.
     * 
     * @param errorType The type of statistical error
     * @param message The detailed error message
     * @param cause The underlying cause
     * @throws NullPointerException if errorType is null
     */
    public StatisticalValidationException(StatisticalErrorType errorType, 
                                        String message, Throwable cause) {
        this(errorType, message, null, null, null, null, cause, null, new HashMap<>());
    }
    
    /**
     * Full constructor with all parameters.
     * 
     * @param errorType The type of statistical error
     * @param message The detailed error message
     * @param datasetName Name of the dataset being analyzed
     * @param sampleSize Size of the sample
     * @param significanceLevel Significance level used
     * @param testResult Results of the statistical test
     * @param cause The underlying cause
     * @param violatedAssumptions List of violated assumptions
     * @param statisticalContext Additional statistical context
     * @throws NullPointerException if errorType is null
     */
    public StatisticalValidationException(StatisticalErrorType errorType, String message,
                                        String datasetName, Integer sampleSize,
                                        Double significanceLevel, StatisticalTestResult testResult,
                                        Throwable cause, List<String> violatedAssumptions,
                                        Map<String, Object> statisticalContext) {
        super(formatMessage(errorType, message, testResult), cause);
        
        Objects.requireNonNull(errorType, "Error type cannot be null");
        
        this.errorType = errorType;
        this.timestamp = LocalDateTime.now();
        this.datasetName = datasetName;
        this.sampleSize = sampleSize;
        this.significanceLevel = significanceLevel;
        this.testResult = testResult;
        this.violatedAssumptions = violatedAssumptions != null ? 
                new ArrayList<>(violatedAssumptions) : new ArrayList<>();
        this.statisticalContext = statisticalContext != null ? 
                new HashMap<>(statisticalContext) : new HashMap<>();
        
        logException();
    }
    
    /**
     * Creates an exception for insufficient sample size.
     * 
     * @param actualSize The actual sample size
     * @param requiredSize The required minimum sample size
     * @param testName The statistical test requiring the sample size
     * @return A new StatisticalValidationException
     */
    public static StatisticalValidationException insufficientSampleSize(
            int actualSize, int requiredSize, String testName) {
        
        String message = String.format(
                "Sample size %d is insufficient for %s (minimum required: %d)",
                actualSize, testName, requiredSize
        );
        
        StatisticalValidationException ex = new StatisticalValidationException(
                StatisticalErrorType.INSUFFICIENT_SAMPLE_SIZE, message
        );
        ex.addContext("actualSize", actualSize);
        ex.addContext("requiredSize", requiredSize);
        ex.addContext("testName", testName);
        
        return ex;
    }
    
    /**
     * Creates an exception for normality assumption violation.
     * 
     * @param datasetName The dataset that failed normality test
     * @param testName The normality test used
     * @param pValue The p-value from the normality test
     * @param significanceLevel The significance level used
     * @return A new StatisticalValidationException
     */
    public static StatisticalValidationException normalityViolation(
            String datasetName, String testName, double pValue, double significanceLevel) {
        
        String message = String.format(
                "Dataset '%s' failed %s normality test (p-value: %.4f < %.4f)",
                datasetName, testName, pValue, significanceLevel
        );
        
        StatisticalTestResult result = new StatisticalTestResult(
                testName, Double.NaN, pValue, significanceLevel, true
        );
        
        return new StatisticalValidationException(
                StatisticalErrorType.NORMALITY_VIOLATION, message,
                datasetName, null, significanceLevel, result, null,
                List.of("Normality"), new HashMap<>()
        );
    }
    
    /**
     * Creates an exception for ANOVA assumption violations.
     * 
     * @param violations List of violated assumptions
     * @param testResults Map of assumption tests and their results
     * @return A new StatisticalValidationException
     */
    public static StatisticalValidationException anovaAssumptionViolation(
            List<String> violations, Map<String, StatisticalTestResult> testResults) {
        
        String message = String.format(
                "ANOVA assumptions violated: %s",
                String.join(", ", violations)
        );
        
        StatisticalValidationException ex = new StatisticalValidationException(
                StatisticalErrorType.ANOVA_ASSUMPTION_VIOLATION, message
        );
        
        violations.forEach(ex::addViolatedAssumption);
        testResults.forEach((test, result) -> 
                ex.addContext("test_" + test, result.toString()));
        
        return ex;
    }
    
    /**
     * Creates an exception for invalid p-value.
     * 
     * @param pValue The invalid p-value
     * @param testName The test that produced the invalid p-value
     * @return A new StatisticalValidationException
     */
    public static StatisticalValidationException invalidPValue(double pValue, String testName) {
        String message = String.format(
                "Invalid p-value %.6f calculated for %s (must be in range [0, 1])",
                pValue, testName
        );
        
        StatisticalValidationException ex = new StatisticalValidationException(
                StatisticalErrorType.INVALID_P_VALUE, message
        );
        ex.addContext("pValue", pValue);
        ex.addContext("testName", testName);
        
        return ex;
    }
    
    /**
     * Creates an exception for confidence interval calculation errors.
     * 
     * @param datasetName The dataset name
     * @param confidenceLevel The confidence level
     * @param reason The reason for failure
     * @return A new StatisticalValidationException
     */
    public static StatisticalValidationException confidenceIntervalError(
            String datasetName, double confidenceLevel, String reason) {
        
        String message = String.format(
                "Failed to calculate %.1f%% confidence interval for '%s': %s",
                confidenceLevel * 100, datasetName, reason
        );
        
        return new StatisticalValidationException(
                StatisticalErrorType.CONFIDENCE_INTERVAL_ERROR, message,
                datasetName, null, 1 - confidenceLevel, null, null, null, new HashMap<>()
        );
    }
    
    /**
     * Creates an exception for insufficient replications.
     * 
     * @param actualReplications The actual number of replications
     * @param requiredReplications The required number of replications
     * @param confidenceLevel The target confidence level
     * @return A new StatisticalValidationException
     */
    public static StatisticalValidationException insufficientReplications(
            int actualReplications, int requiredReplications, double confidenceLevel) {
        
        String message = String.format(
                "Insufficient replications: %d provided, %d required for %.1f%% confidence",
                actualReplications, requiredReplications, confidenceLevel * 100
        );
        
        StatisticalValidationException ex = new StatisticalValidationException(
                StatisticalErrorType.INSUFFICIENT_REPLICATIONS, message
        );
        ex.addContext("actualReplications", actualReplications);
        ex.addContext("requiredReplications", requiredReplications);
        ex.addContext("confidenceLevel", confidenceLevel);
        
        return ex;
    }
    
    /**
     * Formats the exception message.
     * 
     * @param errorType The error type
     * @param message The base message
     * @param testResult Optional test result
     * @return Formatted message
     */
    private static String formatMessage(StatisticalErrorType errorType, String message,
                                      StatisticalTestResult testResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType.getCode()).append("] ");
        sb.append(errorType.getDescription());
        
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        
        if (testResult != null) {
            sb.append(" | Test: ").append(testResult);
        }
        
        return sb.toString();
    }
    
    /**
     * Logs the exception details.
     */
    private void logException() {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(Level.WARNING, String.format(
                    "StatisticalValidationException created: [%s] %s at %s",
                    errorType.getCode(),
                    getMessage(),
                    timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ), this);
            
            if (!violatedAssumptions.isEmpty() && LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Violated assumptions: " + String.join(", ", violatedAssumptions));
            }
        }
    }
    
    /**
     * Adds a violated assumption.
     * 
     * @param assumption The assumption that was violated
     */
    public void addViolatedAssumption(String assumption) {
        if (assumption != null && !assumption.isEmpty()) {
            violatedAssumptions.add(assumption);
        }
    }
    
    /**
     * Adds statistical context information.
     * 
     * @param key The context key
     * @param value The context value
     */
    public void addContext(String key, Object value) {
        if (key != null) {
            statisticalContext.put(key, value);
        }
    }
    
    /**
     * Gets the error type.
     * 
     * @return The statistical error type
     */
    public StatisticalErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * Gets the timestamp.
     * 
     * @return The exception timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the dataset name.
     * 
     * @return The dataset name, may be null
     */
    public String getDatasetName() {
        return datasetName;
    }
    
    /**
     * Gets the sample size.
     * 
     * @return The sample size, may be null
     */
    public Integer getSampleSize() {
        return sampleSize;
    }
    
    /**
     * Gets the significance level.
     * 
     * @return The significance level, may be null
     */
    public Double getSignificanceLevel() {
        return significanceLevel;
    }
    
    /**
     * Gets the statistical test result.
     * 
     * @return The test result, may be null
     */
    public StatisticalTestResult getTestResult() {
        return testResult;
    }
    
    /**
     * Gets the violated assumptions.
     * 
     * @return An unmodifiable list of violated assumptions
     */
    public List<String> getViolatedAssumptions() {
        return Collections.unmodifiableList(violatedAssumptions);
    }
    
    /**
     * Gets the statistical context.
     * 
     * @return An unmodifiable map of statistical context
     */
    public Map<String, Object> getStatisticalContext() {
        return Collections.unmodifiableMap(statisticalContext);
    }
    
    /**
     * Gets a detailed message for logging.
     * 
     * @return A detailed string representation
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("StatisticalValidationException Details:\n");
        sb.append("  Error Type: ").append(errorType.getCode()).append(" - ")
          .append(errorType.getDescription()).append("\n");
        sb.append("  Message: ").append(getMessage()).append("\n");
        sb.append("  Timestamp: ").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        
        if (datasetName != null) {
            sb.append("  Dataset: ").append(datasetName).append("\n");
        }
        
        if (sampleSize != null) {
            sb.append("  Sample Size: ").append(sampleSize).append("\n");
        }
        
        if (significanceLevel != null) {
            sb.append("  Significance Level: ").append(significanceLevel).append("\n");
        }
        
        if (testResult != null) {
            sb.append("  Test Result: ").append(testResult).append("\n");
        }
        
        if (!violatedAssumptions.isEmpty()) {
            sb.append("  Violated Assumptions: ").append(String.join(", ", violatedAssumptions)).append("\n");
        }
        
        if (!statisticalContext.isEmpty()) {
            sb.append("  Statistical Context:\n");
            statisticalContext.forEach((key, value) -> 
                    sb.append("    ").append(key).append(": ").append(value).append("\n"));
        }
        
        if (getCause() != null) {
            sb.append("  Cause: ").append(getCause().getClass().getName())
              .append(" - ").append(getCause().getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Checks if this is a critical statistical error.
     * 
     * @return true if critical, false otherwise
     */
    public boolean isCritical() {
        return switch (errorType) {
            case INSUFFICIENT_SAMPLE_SIZE, INVALID_P_VALUE, 
                 INVALID_TEST_STATISTIC, DATA_QUALITY_ERROR,
                 INSUFFICIENT_REPLICATIONS -> true;
            default -> false;
        };
    }
    
    /**
     * Checks if the error indicates invalid results.
     * 
     * @return true if results are invalid, false otherwise
     */
    public boolean invalidatesResults() {
        return isCritical() || !violatedAssumptions.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("StatisticalValidationException[type=%s, dataset=%s, timestamp=%s]: %s",
                errorType.getCode(),
                datasetName != null ? datasetName : "unknown",
                timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                getMessage());
    }
}