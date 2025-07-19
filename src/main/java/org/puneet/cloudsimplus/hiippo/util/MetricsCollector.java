package org.puneet.cloudsimplus.hiippo.util;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSim;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Collects and aggregates metrics from CloudSim Plus simulations for the
 * Hippopotamus Optimization research framework.
 * 
 * This class is responsible for gathering all performance metrics including
 * resource utilization, power consumption, SLA violations, and execution statistics.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-20
 */
 */
public class MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);
    
    // Metric storage
    private final Map<String, Double> metrics;
    private final Map<String, List<Double>> timeSeriesMetrics;
    
    // Simulation components
    private CloudSim simulation;
    private List<Datacenter> datacenters;
    private DatacenterBroker broker;
    private long startTime;
    private long endTime;
    
    // SLA tracking
    private int slaViolations = 0;
    private final double SLA_CPU_THRESHOLD = 0.95; // 95% CPU utilization threshold
    private final double SLA_RESPONSE_TIME_THRESHOLD = 1.0; // 1 second response time
    
    // Convergence tracking
    private int convergenceIterations = 0;
    private List<Double> fitnessHistory;
    
    /**
     * Creates a new MetricsCollector instance
     */
    public MetricsCollector() {
        this.metrics = new ConcurrentHashMap<>();
        this.timeSeriesMetrics = new ConcurrentHashMap<>();
        this.fitnessHistory = new ArrayList<>();
        logger.debug("MetricsCollector initialized");
    }
    
    /**
     * Initializes the metrics collector with simulation components
     * 
     * @param simulation The CloudSim Plus simulation instance
     * @param datacenters List of datacenters in the simulation
     * @param broker The datacenter broker managing VMs and cloudlets
     */
    public void initialize(CloudSim simulation, List<Datacenter> datacenters, 
                          DatacenterBroker broker) {
        if (simulation == null) {
            throw new IllegalArgumentException("Simulation cannot be null");
        }
        if (datacenters == null || datacenters.isEmpty()) {
            throw new IllegalArgumentException("Datacenters list cannot be null or empty");
        }
        if (broker == null) {
            throw new IllegalArgumentException("Broker cannot be null");
        }
        
        this.simulation = simulation;
        this.datacenters = new ArrayList<>(datacenters);
        this.broker = broker;
        this.startTime = System.currentTimeMillis();
        
        logger.info("MetricsCollector initialized with {} datacenters", datacenters.size());
    }
    
    /**
     * Starts collecting metrics - should be called before simulation starts
     */
    public void startCollection() {
        startTime = System.currentTimeMillis();
        metrics.clear();
        timeSeriesMetrics.clear();
        slaViolations = 0;
        
        logger.info("Started metrics collection at {}", startTime);
    }
    
    /**
     * Stops collecting metrics and performs final calculations
     */
    public void stopCollection() {
        endTime = System.currentTimeMillis();
        collectFinalMetrics();
        
        logger.info("Stopped metrics collection at {}. Duration: {} ms", 
                   endTime, (endTime - startTime));
    }
    
    /**
     * Collects resource utilization metrics from all hosts
     * 
     * @return Map containing CPU and RAM utilization percentages
     */
    public Map<String, Double> collectResourceUtilization() {
        Map<String, Double> utilization = new HashMap<>();
        
        try {
            double totalCpuCapacity = 0;
            double totalCpuUsed = 0;
            double totalRamCapacity = 0;
            double totalRamUsed = 0;
            
            for (Datacenter datacenter : datacenters) {
                if (datacenter == null) continue;
                
                for (Host host : datacenter.getHostList()) {
                    if (host == null) continue;
                    
                    // CPU utilization
                    double hostCpuCapacity = host.getTotalMipsCapacity();
                    double hostCpuUsed = host.getCpuMipsUtilization();
                    
                    totalCpuCapacity += hostCpuCapacity;
                    totalCpuUsed += hostCpuUsed;
                    
                    // RAM utilization
                    double hostRamCapacity = host.getRam().getCapacity();
                    double hostRamUsed = host.getRam().getAllocatedResource();
                    
                    totalRamCapacity += hostRamCapacity;
                    totalRamUsed += hostRamUsed;
                    
                    // Check for SLA violations
                    checkHostSLAViolation(host, hostCpuUsed / hostCpuCapacity);
                }
            }
            
            // Calculate utilization percentages
            double cpuUtilization = totalCpuCapacity > 0 ? 
                (totalCpuUsed / totalCpuCapacity) : 0.0;
            double ramUtilization = totalRamCapacity > 0 ? 
                (totalRamUsed / totalRamCapacity) : 0.0;
            
            utilization.put("cpuUtilization", cpuUtilization);
            utilization.put("ramUtilization", ramUtilization);
            
            // Store in main metrics
            metrics.put("ResourceUtilCPU", cpuUtilization);
            metrics.put("ResourceUtilRAM", ramUtilization);
            
            logger.debug("Resource utilization - CPU: {:.2f}%, RAM: {:.2f}%", 
                        cpuUtilization * 100, ramUtilization * 100);
            
        } catch (Exception e) {
            logger.error("Error collecting resource utilization metrics", e);
            utilization.put("cpuUtilization", 0.0);
            utilization.put("ramUtilization", 0.0);
        }
        
        return utilization;
    }
    
    /**
     * Collects power consumption metrics from all hosts
     * 
     * @return Total power consumption in watts
     */
    public double collectPowerConsumption() {
        double totalPower = 0.0;
        
        try {
            for (Datacenter datacenter : datacenters) {
                if (datacenter == null) continue;
                
                for (Host host : datacenter.getHostList()) {
                    if (host == null) continue;
                    
                    // Get power consumption based on utilization
                    double utilization = host.getCpuMipsUtilization() / 
                                       host.getTotalMipsCapacity();
                    
                    // Check if host has power model
                    if (host.getPowerModel() != PowerModel.NULL) {
                        double hostPower = host.getPowerModel().getPower(utilization);
                        totalPower += hostPower;
                        
                        logger.trace("Host {} power consumption: {} W at {:.2f}% utilization", 
                                   host.getId(), hostPower, utilization * 100);
                    } else {
                        // Estimate power if no model available (linear approximation)
                        double idlePower = 100; // Watts
                        double maxPower = 250;  // Watts
                        double hostPower = idlePower + (maxPower - idlePower) * utilization;
                        totalPower += hostPower;
                    }
                }
            }
            
            metrics.put("PowerConsumption", totalPower);
            logger.debug("Total power consumption: {} W", totalPower);
            
        } catch (Exception e) {
            logger.error("Error collecting power consumption metrics", e);
        }
        
        return totalPower;
    }
    
    /**
     * Collects VM allocation statistics
     * 
     * @return Map containing VM allocation counts
     */
    public Map<String, Integer> collectVmAllocationStats() {
        Map<String, Integer> vmStats = new HashMap<>();
        
        try {
            List<Vm> allVms = broker.getVmCreatedList();
            List<Vm> waitingVms = broker.getVmWaitingList();
            int totalVms = allVms.size() + waitingVms.size();
            int allocatedVms = 0;
            
            // Count successfully allocated VMs
            for (Vm vm : allVms) {
                if (vm != null && vm.getHost() != Host.NULL) {
                    allocatedVms++;
                }
            }
            
            vmStats.put("vmAllocated", allocatedVms);
            vmStats.put("vmTotal", totalVms);
            vmStats.put("vmFailed", totalVms - allocatedVms);
            
            // Store in main metrics
            metrics.put("VmAllocated", (double) allocatedVms);
            metrics.put("VmTotal", (double) totalVms);
            
            logger.info("VM allocation stats - Allocated: {}/{}, Failed: {}", 
                       allocatedVms, totalVms, (totalVms - allocatedVms));
            
        } catch (Exception e) {
            logger.error("Error collecting VM allocation statistics", e);
            vmStats.put("vmAllocated", 0);
            vmStats.put("vmTotal", 0);
            vmStats.put("vmFailed", 0);
        }
        
        return vmStats;
    }
    
    /**
     * Collects SLA violation metrics
     * 
     * @return Number of SLA violations detected
     */
    public int collectSLAViolations() {
        try {
            // Additional SLA checks for cloudlets
            List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
            
            for (Cloudlet cloudlet : finishedCloudlets) {
                if (cloudlet == null) continue;
                
                // Check response time SLA
                double actualTime = cloudlet.getFinishTime() - cloudlet.getExecStartTime();
                double expectedTime = cloudlet.getLength() / cloudlet.getVm().getMips();
                
                if (actualTime > expectedTime * SLA_RESPONSE_TIME_THRESHOLD) {
                    slaViolations++;
                    logger.debug("SLA violation for Cloudlet {}: Response time {} > expected {}", 
                               cloudlet.getId(), actualTime, expectedTime);
                }
            }
            
            metrics.put("SLAViolations", (double) slaViolations);
            logger.info("Total SLA violations detected: {}", slaViolations);
            
        } catch (Exception e) {
            logger.error("Error collecting SLA violation metrics", e);
        }
        
        return slaViolations;
    }
    
    /**
     * Checks for SLA violations on a specific host
     * 
     * @param host The host to check
     * @param cpuUtilization Current CPU utilization ratio
     */
    private void checkHostSLAViolation(Host host, double cpuUtilization) {
        if (cpuUtilization > SLA_CPU_THRESHOLD) {
            slaViolations++;
            logger.debug("SLA violation on Host {}: CPU utilization {:.2f}% exceeds threshold", 
                       host.getId(), cpuUtilization * 100);
        }
    }
    
    /**
     * Sets the convergence iterations for optimization algorithms
     * 
     * @param iterations Number of iterations until convergence
     */
    public void setConvergenceIterations(int iterations) {
        if (iterations < 0) {
            logger.warn("Invalid convergence iterations: {}. Setting to 0.", iterations);
            iterations = 0;
        }
        
        this.convergenceIterations = iterations;
        metrics.put("ConvergenceIterations", (double) iterations);
        logger.debug("Convergence iterations set to: {}", iterations);
    }
    
    /**
     * Adds a fitness value to the convergence history
     * 
     * @param fitness The fitness value to record
     */
    public void recordFitnessValue(double fitness) {
        fitnessHistory.add(fitness);
        
        // Store as time series metric
        timeSeriesMetrics.computeIfAbsent("fitness", k -> new ArrayList<>()).add(fitness);
    }
    
    /**
     * Collects execution time metrics
     * 
     * @return Execution time in seconds
     */
    public double collectExecutionTime() {
        double executionTime = (endTime - startTime) / 1000.0; // Convert to seconds
        metrics.put("ExecutionTime", executionTime);
        
        logger.info("Total execution time: {} seconds", executionTime);
        return executionTime;
    }
    
    /**
     * Collects all final metrics after simulation completion
     */
    private void collectFinalMetrics() {
        try {
            // Collect all metric types
            collectResourceUtilization();
            collectPowerConsumption();
            collectVmAllocationStats();
            collectSLAViolations();
            collectExecutionTime();
            
            // Additional derived metrics
            calculateDerivedMetrics();
            
            logger.info("Final metrics collection completed. Total metrics: {}", 
                       metrics.size());
            
        } catch (Exception e) {
            logger.error("Error during final metrics collection", e);
        }
    }
    
    /**
     * Calculates additional derived metrics
     */
    private void calculateDerivedMetrics() {
        try {
            // VM allocation success rate
            double vmTotal = metrics.getOrDefault("VmTotal", 1.0);
            double vmAllocated = metrics.getOrDefault("VmAllocated", 0.0);
            double allocationSuccessRate = vmTotal > 0 ? (vmAllocated / vmTotal) : 0.0;
            metrics.put("AllocationSuccessRate", allocationSuccessRate);
            
            // Power efficiency (performance per watt)
            double cpuUtil = metrics.getOrDefault("ResourceUtilCPU", 0.0);
            double power = metrics.getOrDefault("PowerConsumption", 1.0);
            double powerEfficiency = power > 0 ? (cpuUtil / power) * 1000 : 0.0;
            metrics.put("PowerEfficiency", powerEfficiency);
            
            // SLA compliance rate
            double slaCompliance = 1.0 - (slaViolations / Math.max(vmTotal, 1.0));
            metrics.put("SLACompliance", Math.max(0.0, slaCompliance));
            
            logger.debug("Derived metrics calculated - Success Rate: {:.2f}%, " +
                        "Power Efficiency: {:.4f}, SLA Compliance: {:.2f}%",
                        allocationSuccessRate * 100, powerEfficiency, slaCompliance * 100);
            
        } catch (Exception e) {
            logger.error("Error calculating derived metrics", e);
        }
    }
    
    /**
     * Gets all collected metrics
     * 
     * @return Map of all metrics with their values
     */
    public Map<String, Double> getAllMetrics() {
        return new HashMap<>(metrics);
    }
    
    /**
     * Gets a specific metric value
     * 
     * @param metricName Name of the metric
     * @return Metric value or 0.0 if not found
     */
    public double getMetric(String metricName) {
        return metrics.getOrDefault(metricName, 0.0);
    }
    
    /**
     * Gets time series data for a specific metric
     * 
     * @param metricName Name of the time series metric
     * @return List of values over time or empty list if not found
     */
    public List<Double> getTimeSeriesMetric(String metricName) {
        return timeSeriesMetrics.getOrDefault(metricName, new ArrayList<>());
    }
    
    /**
     * Exports metrics summary as a formatted string
     * 
     * @return Formatted metrics summary
     */
    public String getMetricsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("\n=== Metrics Summary ===\n");
        
        // Resource utilization
        summary.append(String.format("CPU Utilization: %.2f%%\n", 
                                   getMetric("ResourceUtilCPU") * 100));
        summary.append(String.format("RAM Utilization: %.2f%%\n", 
                                   getMetric("ResourceUtilRAM") * 100));
        
        // Power metrics
        summary.append(String.format("Power Consumption: %.2f W\n", 
                                   getMetric("PowerConsumption")));
        summary.append(String.format("Power Efficiency: %.4f\n", 
                                   getMetric("PowerEfficiency")));
        
        // VM allocation
        summary.append(String.format("VMs Allocated: %d/%d (%.2f%%)\n", 
                                   getMetric("VmAllocated").intValue(),
                                   getMetric("VmTotal").intValue(),
                                   getMetric("AllocationSuccessRate") * 100));
        
        // SLA metrics
        summary.append(String.format("SLA Violations: %d\n", 
                                   getMetric("SLAViolations").intValue()));
        summary.append(String.format("SLA Compliance: %.2f%%\n", 
                                   getMetric("SLACompliance") * 100));
        
        // Performance metrics
        summary.append(String.format("Execution Time: %.3f seconds\n", 
                                   getMetric("ExecutionTime")));
        summary.append(String.format("Convergence Iterations: %d\n", 
                                   getMetric("ConvergenceIterations").intValue()));
        
        summary.append("=====================\n");
        
        return summary.toString();
    }
    
    /**
     * Validates collected metrics for consistency
     * 
     * @return true if metrics are valid, false otherwise
     */
    public boolean validateMetrics() {
        boolean valid = true;
        
        // Check for required metrics
        String[] requiredMetrics = {
            "ResourceUtilCPU", "ResourceUtilRAM", "PowerConsumption",
            "SLAViolations", "ExecutionTime", "VmAllocated", "VmTotal"
        };
        
        for (String metric : requiredMetrics) {
            if (!metrics.containsKey(metric)) {
                logger.error("Missing required metric: {}", metric);
                valid = false;
            }
        }
        
        // Validate metric ranges
        double cpuUtil = getMetric("ResourceUtilCPU");
        if (cpuUtil < 0 || cpuUtil > 1) {
            logger.error("Invalid CPU utilization: {}", cpuUtil);
            valid = false;
        }
        
        double ramUtil = getMetric("ResourceUtilRAM");
        if (ramUtil < 0 || ramUtil > 1) {
            logger.error("Invalid RAM utilization: {}", ramUtil);
            valid = false;
        }
        
        double power = getMetric("PowerConsumption");
        if (power < 0) {
            logger.error("Invalid power consumption: {}", power);
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Resets the metrics collector for reuse
     */
    public void reset() {
        metrics.clear();
        timeSeriesMetrics.clear();
        fitnessHistory.clear();
        slaViolations = 0;
        convergenceIterations = 0;
        startTime = 0;
        endTime = 0;
        
        logger.debug("MetricsCollector reset");
    }
    
    /**
     * Gets the fitness history for convergence analysis
     * 
     * @return List of fitness values over iterations
     */
    public List<Double> getFitnessHistory() {
        return new ArrayList<>(fitnessHistory);
    }
}