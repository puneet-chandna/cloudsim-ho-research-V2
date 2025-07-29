package org.puneet.cloudsimplus.hiippo;

import org.puneet.cloudsimplus.hiippo.simulation.ExperimentCoordinator;
import org.puneet.cloudsimplus.hiippo.simulation.TestScenarios;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.RunManager;
import org.puneet.cloudsimplus.hiippo.util.CSVResultsWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for the Hippopotamus Optimization research framework.
 * Provides entry point for running experiments and demonstrations.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-22
 */
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) {
        logger.info("Starting Hippopotamus Optimization Research Framework");
        
        try {
            // Initialize framework components
            logger.info("Initializing framework components...");
            RunManager.getInstance();
            CSVResultsWriter.initializeWithRunManager();
            logger.info("Framework initialization completed successfully");
            
            // Run the complete experimental suite
            runCompleteExperimentalSuite();
            
        } catch (Exception e) {
            logger.error("Critical error in application execution", e);
            System.exit(1);
        }
    }
    
    /**
     * Runs the complete experimental suite with all algorithms and scenarios.
     */
    private static void runCompleteExperimentalSuite() {
        try {
            logger.info("Starting complete experimental suite...");
            
            // Create experiment coordinator
            ExperimentCoordinator coordinator = new ExperimentCoordinator(5, false);
            
            // CRITICAL: Configure experiments before running
            coordinator.configureExperiments();
            
            // Run the complete experiment
            coordinator.runCompleteExperiment();
            
            logger.info("Complete experimental suite finished successfully!");
            
        } catch (Exception e) {
            logger.error("Failed to run complete experimental suite", e);
            throw new RuntimeException("Experimental suite execution failed", e);
        }
    }
}