package org.puneet.cloudsimplus.hiippo.simulation;

import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.CSVResultsWriter.ExperimentResult;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quick test class for running individual experiments with command line arguments.
 * Usage: java -cp target/classes org.puneet.cloudsimplus.hiippo.simulation.QuickTest <scenario> <algorithm> <replications>
 * Example: java -cp target/classes org.puneet.cloudsimplus.hiippo.simulation.QuickTest Micro HO 1
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-31
 */
public class QuickTest {
    private static final Logger logger = LoggerFactory.getLogger(QuickTest.class);
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java QuickTest <scenario> <algorithm> <replications>");
            System.out.println("Scenarios: Micro, Small, Medium, Large, XLarge, Enterprise");
            System.out.println("Algorithms: HO, FirstFit, GA");
            System.out.println("Example: java QuickTest Micro HO 1");
            System.exit(1);
        }
        
        String scenarioName = args[0];
        String algorithmName = args[1];
        int replications = Integer.parseInt(args[2]);
        
        logger.info("Starting Quick Test - Scenario: {}, Algorithm: {}, Replications: {}", 
            scenarioName, algorithmName, replications);
        
        try {
            // Initialize random seed for replication 0
            ExperimentConfig.initializeRandomSeed(0);
            
            // Get scenario specifications
            int vmCount = getVmCount(scenarioName);
            int hostCount = getHostCount(scenarioName);
            
            logger.info("Creating scenario: {} VMs, {} Hosts", vmCount, hostCount);
            
            // Create test scenario
            TestScenarios.TestScenario scenario = TestScenarios.createScenario(scenarioName, vmCount, hostCount);
            
            // Run experiments
            for (int i = 0; i < replications; i++) {
                logger.info("Running replication {}/{}", i + 1, replications);
                
                CloudSimHOSimulation simulation = new CloudSimHOSimulation(algorithmName, scenario, i);
                ExperimentResult result = simulation.runSimulation();
                
                logger.info("Replication {} completed:", i + 1);
                logger.info("  - VMs Allocated: {}/{}", result.getVmAllocated(), result.getVmTotal());
                logger.info("  - CPU Utilization: {}%", String.format("%.2f", result.getResourceUtilizationCPU() * 100));
                logger.info("  - RAM Utilization: {}%", String.format("%.2f", result.getResourceUtilizationRAM() * 100));
                logger.info("  - Execution Time: {}s", String.format("%.2f", result.getExecutionTime()));
                logger.info("  - Power Consumption: {}W", String.format("%.2f", result.getPowerConsumption()));
                logger.info("  - SLA Violations: {}", result.getSlaViolations());
            }
            
            logger.info("Quick test completed successfully!");
            
        } catch (Exception e) {
            logger.error("Quick test failed", e);
            System.exit(1);
        }
    }
    
    private static int getVmCount(String scenario) {
        return switch (scenario.toLowerCase()) {
            case "micro" -> 50;
            case "small" -> 200;
            case "medium" -> 500;
            case "large" -> 1000;
            case "xlarge" -> 2000;
            case "enterprise" -> 2500; // Reduced from 5000
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }
    
    private static int getHostCount(String scenario) {
        return switch (scenario.toLowerCase()) {
            case "micro" -> 10;
            case "small" -> 40;
            case "medium" -> 100;
            case "large" -> 200;
            case "xlarge" -> 400;
            case "enterprise" -> 500; // Reduced from 1000
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }
} 