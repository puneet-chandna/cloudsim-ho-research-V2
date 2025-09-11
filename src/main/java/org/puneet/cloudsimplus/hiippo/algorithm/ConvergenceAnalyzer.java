package org.puneet.cloudsimplus.hiippo.algorithm;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Analyzes convergence patterns for the Hippopotamus Optimization algorithm.
 * This class provides comprehensive convergence detection, statistical analysis,
 * and performance tracking for research-grade experimental validation.
 * 
 * <p>The analyzer uses multiple convergence criteria including:
 * <ul>
 *   <li>Fitness improvement threshold</li>
 *   <li>Standard deviation of recent fitness values</li>
 *   <li>Trend analysis using linear regression</li>
 *   <li>Plateau detection</li>
 * </ul>
 * </p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class ConvergenceAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ConvergenceAnalyzer.class);
    
    // Convergence detection parameters
    private final int windowSize;
    private final double improvementThreshold;
    private final double stdDevThreshold;
    private final double slopeThreshold;
    private final int plateauIterations;
    
    // Analysis data structures
    private final LinkedList<Double> fitnessWindow;
    private final DescriptiveStatistics stats;
    private final SimpleRegression regression;
    
    // Convergence metrics
    private int iterationCount;
    private double bestFitness;
    private int iterationsWithoutImprovement;
    private long convergenceTime;
    private boolean hasConverged;
    
    // Performance tracking
    private final List<ConvergenceMetric> convergenceMetrics;
    private final Map<Integer, Double> improvementRates;
    
    /**
     * Creates a new ConvergenceAnalyzer with default parameters.
     * 
     * @param windowSize the size of the sliding window for analysis
     */
    public ConvergenceAnalyzer(int windowSize) {
        // CRITICAL FIX: Relax convergence criteria to allow proper optimization
        this(windowSize, 0.01, 0.001, 0.0001, 20);
    }
    
    /**
     * Creates a new ConvergenceAnalyzer with custom parameters.
     * 
     * @param windowSize the size of the sliding window
     * @param improvementThreshold the minimum improvement to consider progress
     * @param stdDevThreshold the standard deviation threshold for convergence
     * @param slopeThreshold the slope threshold for trend analysis
     * @param plateauIterations the number of iterations to detect plateau
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public ConvergenceAnalyzer(int windowSize, double improvementThreshold,
                              double stdDevThreshold, double slopeThreshold,
                              int plateauIterations) {
        validateParameters(windowSize, improvementThreshold, stdDevThreshold,
                          slopeThreshold, plateauIterations);
        
        this.windowSize = windowSize;
        this.improvementThreshold = improvementThreshold;
        this.stdDevThreshold = stdDevThreshold;
        this.slopeThreshold = slopeThreshold;
        this.plateauIterations = plateauIterations;
        
        this.fitnessWindow = new LinkedList<>();
        this.stats = new DescriptiveStatistics(windowSize);
        this.regression = new SimpleRegression();
        
        this.iterationCount = 0;
        this.bestFitness = Double.MAX_VALUE;
        this.iterationsWithoutImprovement = 0;
        this.convergenceTime = -1;
        this.hasConverged = false;
        
        this.convergenceMetrics = new ArrayList<>();
        this.improvementRates = new HashMap<>();
        
        logger.info("Initialized ConvergenceAnalyzer with window size: {}", windowSize);
    }
    
    /**
     * Validates the constructor parameters.
     */
    private void validateParameters(int windowSize, double improvementThreshold,
                                   double stdDevThreshold, double slopeThreshold,
                                   int plateauIterations) {
        if (windowSize <= 0 || windowSize > 100) {
            throw new IllegalArgumentException(
                "Window size must be between 1 and 100, got: " + windowSize);
        }
        
        if (improvementThreshold < 0 || improvementThreshold > 1) {
            throw new IllegalArgumentException(
                "Improvement threshold must be between 0 and 1, got: " + improvementThreshold);
        }
        
        if (stdDevThreshold < 0 || stdDevThreshold > 1) {
            throw new IllegalArgumentException(
                "Standard deviation threshold must be between 0 and 1, got: " + stdDevThreshold);
        }
        
        if (slopeThreshold < 0 || slopeThreshold > 1) {
            throw new IllegalArgumentException(
                "Slope threshold must be between 0 and 1, got: " + slopeThreshold);
        }
        
        if (plateauIterations <= 0 || plateauIterations > 100) {
            throw new IllegalArgumentException(
                "Plateau iterations must be between 1 and 100, got: " + plateauIterations);
        }
    }
    
    /**
     * Checks if the algorithm has converged based on multiple criteria.
     * 
     * @param fitnessHistory the complete fitness history
     * @return true if converged, false otherwise
     */
    public boolean checkConvergence(List<Double> fitnessHistory) {
        if (fitnessHistory == null || fitnessHistory.isEmpty()) {
            return false;
        }
        
        // Get current fitness
        double currentFitness = fitnessHistory.get(fitnessHistory.size() - 1);
        iterationCount = fitnessHistory.size();
        
        // Update best fitness
        if (currentFitness < bestFitness) {
            double improvement = bestFitness - currentFitness;
            bestFitness = currentFitness;
            iterationsWithoutImprovement = 0;
            
            // Track improvement rate
            double improvementRate = improvement / bestFitness;
            improvementRates.put(iterationCount, improvementRate);
            
            logger.trace("New best fitness: {} at iteration {}", bestFitness, iterationCount);
        } else {
            iterationsWithoutImprovement++;
        }
        
        // Update fitness window
        updateFitnessWindow(currentFitness);
        
        // Check convergence criteria
        boolean converged = checkAllConvergenceCriteria();
        
        // Record convergence time
        if (converged && !hasConverged) {
            convergenceTime = System.currentTimeMillis();
            hasConverged = true;
            logger.info("Convergence detected at iteration {} with fitness {}", 
                       iterationCount, bestFitness);
        }
        
        // Create and store convergence metric
        ConvergenceMetric metric = createConvergenceMetric(currentFitness, converged);
        convergenceMetrics.add(metric);
        
        return converged;
    }
    
    /**
     * Updates the sliding window of fitness values.
     * 
     * @param fitness the new fitness value
     */
    private void updateFitnessWindow(double fitness) {
        fitnessWindow.addLast(fitness);
        stats.addValue(fitness);
        
        // Maintain window size
        if (fitnessWindow.size() > windowSize) {
            double removed = fitnessWindow.removeFirst();
            stats.removeMostRecentValue();
        }
        
        // Update regression for trend analysis
        regression.clear();
        for (int i = 0; i < fitnessWindow.size(); i++) {
            regression.addData(i, fitnessWindow.get(i));
        }
    }
    
    /**
     * Checks all convergence criteria.
     * 
     * @return true if all criteria indicate convergence
     */
    private boolean checkAllConvergenceCriteria() {
        // Need sufficient data
        if (fitnessWindow.size() < windowSize) {
            return false;
        }
        
        // Criterion 1: Fitness improvement threshold
        boolean improvementCriterion = checkImprovementCriterion();
        
        // Criterion 2: Standard deviation threshold
        boolean stdDevCriterion = checkStandardDeviationCriterion();
        
        // Criterion 3: Trend analysis (slope)
        boolean trendCriterion = checkTrendCriterion();
        
        // Criterion 4: Plateau detection
        boolean plateauCriterion = checkPlateauCriterion();
        
        logger.trace("Convergence criteria - Improvement: {}, StdDev: {}, Trend: {}, Plateau: {}",
                    improvementCriterion, stdDevCriterion, trendCriterion, plateauCriterion);
        
        // Require at least 3 out of 4 criteria to be met
        int criteriaMetCount = 0;
        if (improvementCriterion) criteriaMetCount++;
        if (stdDevCriterion) criteriaMetCount++;
        if (trendCriterion) criteriaMetCount++;
        if (plateauCriterion) criteriaMetCount++;
        
        return criteriaMetCount >= 3;
    }
    
    /**
     * Checks if fitness improvement is below threshold.
     * 
     * @return true if improvement is minimal
     */
    private boolean checkImprovementCriterion() {
        if (improvementRates.isEmpty()) {
            return false;
        }
        
        // Get recent improvement rates
        List<Double> recentImprovements = new ArrayList<>();
        for (int i = Math.max(0, iterationCount - windowSize); i < iterationCount; i++) {
            if (improvementRates.containsKey(i)) {
                recentImprovements.add(improvementRates.get(i));
            }
        }
        
        if (recentImprovements.isEmpty()) {
            return true; // No improvements recently
        }
        
        double avgImprovement = recentImprovements.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        return avgImprovement < improvementThreshold;
    }
    
    /**
     * Checks if standard deviation of fitness values is below threshold.
     * 
     * @return true if fitness values are stable
     */
    private boolean checkStandardDeviationCriterion() {
        double stdDev = stats.getStandardDeviation();
        double mean = stats.getMean();
        
        // Normalize standard deviation by mean to make it scale-invariant
        double normalizedStdDev = mean > 0 ? stdDev / mean : stdDev;
        
        return normalizedStdDev < stdDevThreshold;
    }
    
    /**
     * Checks if the trend (slope) indicates convergence.
     * 
     * @return true if fitness trend is flat
     */
    private boolean checkTrendCriterion() {
        if (regression.getN() < 3) {
            return false;
        }
        
        double slope = regression.getSlope();
        double intercept = regression.getIntercept();
        
        // Normalize slope by the intercept for scale invariance
        double normalizedSlope = intercept > 0 ? Math.abs(slope) / intercept : Math.abs(slope);
        
        return normalizedSlope < slopeThreshold;
    }
    
    /**
     * Checks if the algorithm has reached a plateau.
     * 
     * @return true if no improvement for specified iterations
     */
    private boolean checkPlateauCriterion() {
        return iterationsWithoutImprovement >= plateauIterations;
    }
    
    /**
     * Creates a convergence metric for the current iteration.
     * 
     * @param currentFitness the current fitness value
     * @param converged whether convergence is detected
     * @return the convergence metric
     */
    private ConvergenceMetric createConvergenceMetric(double currentFitness, boolean converged) {
        ConvergenceMetric metric = new ConvergenceMetric();
        
        metric.iteration = iterationCount;
        metric.fitness = currentFitness;
        metric.bestFitness = bestFitness;
        metric.standardDeviation = stats.getStandardDeviation();
        metric.mean = stats.getMean();
        metric.slope = regression.getN() >= 3 ? regression.getSlope() : 0.0;
        metric.iterationsWithoutImprovement = iterationsWithoutImprovement;
        metric.converged = converged;
        metric.timestamp = System.currentTimeMillis();
        
        return metric;
    }
    
    /**
     * Gets the convergence rate (iterations to convergence).
     * 
     * @return the number of iterations to convergence, or -1 if not converged
     */
    public int getConvergenceRate() {
        return hasConverged ? iterationCount : -1;
    }
    
    /**
     * Gets the relative improvement over iterations.
     * 
     * @return the improvement percentage from initial to best fitness
     */
    public double getRelativeImprovement() {
        if (convergenceMetrics.isEmpty()) {
            return 0.0;
        }
        
        double initialFitness = convergenceMetrics.get(0).fitness;
        return (initialFitness - bestFitness) / initialFitness * 100.0;
    }
    
    /**
     * Gets the stability score based on fitness variance.
     * 
     * @return stability score between 0 (unstable) and 1 (stable)
     */
    public double getStabilityScore() {
        if (stats.getN() < 2) {
            return 0.0;
        }
        
        double cv = stats.getStandardDeviation() / stats.getMean();
        return Math.max(0.0, 1.0 - cv);
    }
    
    /**
     * Gets detailed convergence analysis report.
     * 
     * @return convergence analysis report
     */
    public ConvergenceReport getConvergenceReport() {
        ConvergenceReport report = new ConvergenceReport();
        
        report.hasConverged = hasConverged;
        report.convergenceIteration = hasConverged ? iterationCount : -1;
        report.convergenceTime = convergenceTime;
        report.bestFitness = bestFitness;
        report.finalFitness = fitnessWindow.isEmpty() ? 0.0 : fitnessWindow.getLast();
        report.relativeImprovement = getRelativeImprovement();
        report.stabilityScore = getStabilityScore();
        report.averageImprovement = calculateAverageImprovement();
        report.convergenceRate = calculateConvergenceRate();
        
        return report;
    }
    
    /**
     * Calculates the average improvement per iteration.
     * 
     * @return average improvement
     */
    private double calculateAverageImprovement() {
        if (improvementRates.isEmpty()) {
            return 0.0;
        }
        
        return improvementRates.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Calculates the convergence rate (fitness reduction per iteration).
     * 
     * @return convergence rate
     */
    private double calculateConvergenceRate() {
        if (convergenceMetrics.size() < 2) {
            return 0.0;
        }
        
        double initialFitness = convergenceMetrics.get(0).fitness;
        int iterations = convergenceMetrics.size();
        
        return (initialFitness - bestFitness) / iterations;
    }
    
    /**
     * Gets the complete convergence metrics history.
     * 
     * @return list of convergence metrics
     */
    public List<ConvergenceMetric> getConvergenceMetrics() {
        return new ArrayList<>(convergenceMetrics);
    }
    
    /**
     * Exports convergence data for analysis.
     * 
     * @return map of convergence data
     */
    public Map<String, Object> exportConvergenceData() {
        Map<String, Object> data = new HashMap<>();
        
        data.put("hasConverged", hasConverged);
        data.put("convergenceIteration", getConvergenceRate());
        data.put("bestFitness", bestFitness);
        data.put("relativeImprovement", getRelativeImprovement());
        data.put("stabilityScore", getStabilityScore());
        data.put("windowSize", windowSize);
        data.put("improvementThreshold", improvementThreshold);
        data.put("stdDevThreshold", stdDevThreshold);
        data.put("slopeThreshold", slopeThreshold);
        data.put("plateauIterations", plateauIterations);
        
        // Add fitness window data
        data.put("fitnessWindow", new ArrayList<>(fitnessWindow));
        
        // Add statistics
        Map<String, Double> statistics = new HashMap<>();
        statistics.put("mean", stats.getMean());
        statistics.put("stdDev", stats.getStandardDeviation());
        statistics.put("min", stats.getMin());
        statistics.put("max", stats.getMax());
        data.put("statistics", statistics);
        
        return data;
    }
    
    /**
     * Resets the analyzer for a new optimization run.
     */
    public void reset() {
        fitnessWindow.clear();
        stats.clear();
        regression.clear();
        convergenceMetrics.clear();
        improvementRates.clear();
        
        iterationCount = 0;
        bestFitness = Double.MAX_VALUE;
        iterationsWithoutImprovement = 0;
        convergenceTime = -1;
        hasConverged = false;
        
        logger.debug("ConvergenceAnalyzer reset");
    }
    
    /**
     * Represents a single convergence metric at a specific iteration.
     */
    public static class ConvergenceMetric {
        public int iteration;
        public double fitness;
        public double bestFitness;
        public double standardDeviation;
        public double mean;
        public double slope;
        public int iterationsWithoutImprovement;
        public boolean converged;
        public long timestamp;
        
        @Override
        public String toString() {
            return String.format(
                "ConvergenceMetric{iteration=%d, fitness=%.6f, bestFitness=%.6f, " +
                "stdDev=%.6f, converged=%s}",
                iteration, fitness, bestFitness, standardDeviation, converged);
        }
    }
    
    /**
     * Comprehensive convergence analysis report.
     */
    public static class ConvergenceReport {
        public boolean hasConverged;
        public int convergenceIteration;
        public long convergenceTime;
        public double bestFitness;
        public double finalFitness;
        public double relativeImprovement;
        public double stabilityScore;
        public double averageImprovement;
        public double convergenceRate;
        
        @Override
        public String toString() {
            return String.format(
                "ConvergenceReport{converged=%s, iteration=%d, bestFitness=%.6f, " +
                "improvement=%.2f%%, stability=%.3f}",
                hasConverged, convergenceIteration, bestFitness,
                relativeImprovement, stabilityScore);
        }
    }
}