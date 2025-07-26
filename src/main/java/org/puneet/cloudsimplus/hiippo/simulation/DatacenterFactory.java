package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.PowerMeter;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Factory class for creating CloudSim datacenters with appropriate configurations
 * for the Hippopotamus Optimization research framework.
 * 
 * This factory creates datacenters optimized for different scenario sizes
 * and supports power-aware simulations with memory-efficient configurations.
 * 
 * @author Puneet Chandna
 * @version 1.0
 * @since 2025-07-19
 */
public class DatacenterFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(DatacenterFactory.class);
    
    // Datacenter characteristics constants
    private static final double COST_PER_SECOND = 3.0;
    private static final double COST_PER_RAM = 0.05;
    private static final double COST_PER_STORAGE = 0.001;
    private static final double COST_PER_BW = 0.01;
    private static final double SCHEDULING_INTERVAL = 0.1;
    
    // Power model constants (based on real server specifications)
    private static final double MAX_POWER_WATTS = 250.0;
    private static final double STATIC_POWER_PERCENT = 0.7; // 70% of max power
    private static final double STARTUP_DELAY = 0.0;
    private static final double SHUTDOWN_DELAY = 0.0;
    private static final double STARTUP_POWER = 0.0;
    private static final double SHUTDOWN_POWER = 0.0;
    
    /**
     * Creates a datacenter with the specified number of hosts and VM allocation policy.
     * 
     * @param simulation The CloudSim simulation instance
     * @param hostCount The number of hosts to create in the datacenter
     * @param vmAllocationPolicy The VM allocation policy to use
     * @param datacenterName Name identifier for the datacenter
     * @return A fully configured Datacenter instance
     * @throws IllegalArgumentException if parameters are null or out of range
     */
    public static Datacenter createDatacenter(
            CloudSimPlus simulation,
            int hostCount,
            VmAllocationPolicy vmAllocationPolicy,
            String datacenterName) {
        
        logger.info("Creating datacenter '{}' with {} hosts", datacenterName, hostCount);
        
        // Validate parameters
        validateParameters(simulation, hostCount, vmAllocationPolicy, datacenterName);
        
        // Check memory before creating large datacenters
        if (hostCount > 50) {
            MemoryManager.checkMemoryUsage("Before creating datacenter");
        }
        
        // Create hosts for the datacenter
        List<Host> hostList = createHosts(simulation, hostCount);
        
        // Create and configure the datacenter
        DatacenterSimple datacenter = new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
        datacenter.setName(datacenterName);
        datacenter.setSchedulingInterval(SCHEDULING_INTERVAL);
        
        // Set datacenter characteristics
        datacenter.getCharacteristics()
            .setCostPerSecond(COST_PER_SECOND)
            .setCostPerMem(COST_PER_RAM)
            .setCostPerStorage(COST_PER_STORAGE)
            .setCostPerBw(COST_PER_BW);
        
        // Enable power measurements
        configurePowerMeasurement(datacenter);
        
        logger.info("Successfully created datacenter '{}' with {} hosts, total capacity: {} MIPS, {} MB RAM",
            datacenterName, 
            hostList.size(),
            hostList.stream().mapToDouble(Host::getTotalMipsCapacity).sum(),
            hostList.stream().mapToLong(host -> host.getRam().getCapacity()).sum()
        );
        
        return datacenter;
    }
    
    /**
     * Creates a datacenter with scenario-specific configurations.
     * 
     * @param simulation The CloudSim simulation instance
     * @param scenario The scenario name (Micro, Small, Medium, Large, XLarge)
     * @param vmAllocationPolicy The VM allocation policy to use
     * @return A configured Datacenter instance
     * @throws IllegalArgumentException if scenario is unknown or parameters are invalid
     */
    public static Datacenter createDatacenterForScenario(
            CloudSimPlus simulation,
            String scenario,
            VmAllocationPolicy vmAllocationPolicy) {
        
        Objects.requireNonNull(scenario, "Scenario cannot be null");
        // Early scenario validation using ExperimentConfig
        if (!ExperimentConfig.isScenarioEnabled(scenario)) {
            throw new IllegalArgumentException("Scenario '" + scenario + "' is not enabled in config.properties");
        }
        int hostCount;
        try {
            hostCount = getHostCountForScenario(scenario);
        } catch (ValidationException e) {
            throw new RuntimeException(e);
        }
        String datacenterName = "DC_" + scenario;
        
        logger.info("Creating datacenter for scenario '{}' with {} hosts", scenario, hostCount);
        
        return createDatacenter(simulation, hostCount, vmAllocationPolicy, datacenterName);
    }
    
    /**
     * Creates a list of hosts with standardized configurations.
     * 
     * @param simulation The CloudSim simulation instance
     * @param count The number of hosts to create
     * @return List of configured Host instances
     * @throws IllegalArgumentException if host creation fails
     */
    public static List<Host> createHosts(CloudSimPlus simulation, int count) {
        logger.debug("Creating {} hosts for simulation", count);
        
        List<Host> hostList = new ArrayList<>(count);
        
        // Use parallel stream for large host counts to improve performance
        if (count > 100 && Runtime.getRuntime().availableProcessors() > 2) {
            hostList = IntStream.range(0, count)
                .parallel()
                .mapToObj(i -> createHost(simulation, i, "timeshared"))
                .toList();
        } else {
            // Sequential creation for smaller counts
            for (int i = 0; i < count; i++) {
                hostList.add(createHost(simulation, i, "timeshared"));
                
                // Log progress for large counts
                if (count > 50 && i % 50 == 0 && i > 0) {
                    logger.debug("Created {}/{} hosts", i, count);
                }
            }
        }
        
        logger.debug("Successfully created {} hosts", hostList.size());
        return hostList;
    }
    
    /**
     * Creates a single host with standardized configuration.
     * 
     * @param simulation The CloudSim simulation instance
     * @param id The unique identifier for the host
     * @return A configured Host instance
     */
    private static Host createHost(CloudSimPlus simulation, int id, String schedulerType) {
        List<Pe> peList = createPeList();
        
        HostSimple host = new HostSimple(
            ExperimentConfig.getHostRam(),
            ExperimentConfig.getHostBw(),
            ExperimentConfig.getHostStorage(),
            peList
        );
        host.setId(id);
        // Set scheduler based on parameter
        switch (schedulerType.toLowerCase()) {
            case "spaceshared":
                // Default is space-shared, do not set scheduler
                break;
            case "timeshared":
            default:
                host.setVmScheduler(new VmSchedulerTimeShared());
                break;
        }
        host.setRamProvisioner(new ResourceProvisionerSimple());
        host.setBwProvisioner(new ResourceProvisionerSimple());
        
        // Configure power model
        PowerModelHost powerModel = createPowerModel();
        host.setPowerModel(powerModel);
        
        return host;
    }
    
    /**
     * Creates a list of Processing Elements (PEs) for a host.
     * 
     * @return List of configured PE instances
     */
    private static List<Pe> createPeList() {
        int pes = ExperimentConfig.getHostPes();
        long mips = ExperimentConfig.getHostMips();
        List<Pe> peList = new ArrayList<>(pes);
        for (int i = 0; i < pes; i++) {
            peList.add(new PeSimple(mips));
        }
        return peList;
    }
    
    /**
     * Creates a power model for hosts based on linear power consumption.
     * 
     * @return Configured PowerModelHost instance
     */
    private static PowerModelHost createPowerModel() {
        return new PowerModelHostSimple(MAX_POWER_WATTS, STATIC_POWER_PERCENT)
            .setStartupDelay(STARTUP_DELAY)
            .setShutDownDelay(SHUTDOWN_DELAY)
            .setStartupPower(STARTUP_POWER)
            .setShutDownPower(SHUTDOWN_POWER);
    }
    
    /**
     * Configures power measurement for the datacenter.
     * 
     * @param datacenter The datacenter to configure
     */
    private static void configurePowerMeasurement(Datacenter datacenter) {
        // Power measurement is enabled by setting a PowerModelHost on each host.
        // No explicit PowerMeter or config flag needed in CloudSim Plus 8.0.0
        logger.debug("Power measurement enabled for datacenter '{}' (by PowerModelHost)", datacenter.getName());
    }
    
    /**
     * Validates input parameters for datacenter creation.
     * 
     * @param simulation The CloudSim simulation instance
     * @param hostCount The number of hosts
     * @param vmAllocationPolicy The VM allocation policy
     * @param datacenterName The datacenter name
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private static void validateParameters(
            CloudSimPlus simulation,
            int hostCount,
            VmAllocationPolicy vmAllocationPolicy,
            String datacenterName) {
        
        if (simulation == null) {
            throw new IllegalArgumentException("CloudSim simulation instance cannot be null");
        }
        
        if (vmAllocationPolicy == null) {
            throw new IllegalArgumentException("VM allocation policy cannot be null");
        }
        
        if (datacenterName == null || datacenterName.trim().isEmpty()) {
            throw new IllegalArgumentException("Datacenter name cannot be null or empty");
        }
        
        if (hostCount <= 0) {
            throw new IllegalArgumentException("Host count must be positive, got: " + hostCount);
        }
        
        if (hostCount > 1000) {
            logger.warn("Creating datacenter with {} hosts may require significant memory", hostCount);
            
            // Check if we have enough memory for this many hosts
            if (!MemoryManager.hasEnoughMemoryForScenario(0, hostCount)) {
                throw new IllegalArgumentException(
                    "Insufficient memory to create datacenter with " + hostCount + " hosts");
            }
        }
    }
    
    /**
     * Determines the host count for a given scenario.
     * 
     * @param scenario The scenario name
     * @return The number of hosts for the scenario
     * @throws ValidationException if scenario is unknown
     */
    private static int getHostCountForScenario(String scenario) throws ValidationException {
        return switch (scenario.toLowerCase()) {
            case "micro" -> 3;
            case "small" -> 10;
            case "medium" -> 20;
            case "large" -> 40;
            case "xlarge" -> 100;
            default -> throw new ValidationException("Unknown scenario: " + scenario);
        };
    }
    
    /**
     * Creates a datacenter with custom host specifications.
     * 
     * @param simulation The CloudSim simulation instance
     * @param hostSpecs List of custom host specifications
     * @param vmAllocationPolicy The VM allocation policy
     * @param datacenterName The datacenter name
     * @return A configured Datacenter instance
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static Datacenter createCustomDatacenter(
            CloudSimPlus simulation,
            List<HostSpec> hostSpecs,
            VmAllocationPolicy vmAllocationPolicy,
            String datacenterName) {
        
        logger.info("Creating custom datacenter '{}' with {} custom hosts", 
            datacenterName, hostSpecs.size());
        
        Objects.requireNonNull(hostSpecs, "Host specifications cannot be null");
        
        if (hostSpecs.isEmpty()) {
            throw new IllegalArgumentException("Host specifications list cannot be empty");
        }
        
        List<Host> hostList = new ArrayList<>(hostSpecs.size());
        
        for (int i = 0; i < hostSpecs.size(); i++) {
            HostSpec spec = hostSpecs.get(i);
            Host host = createCustomHost(simulation, i, spec);
            hostList.add(host);
        }
        DatacenterSimple datacenter = new DatacenterSimple(simulation, hostList, vmAllocationPolicy);
        datacenter.setName(datacenterName);
        datacenter.setSchedulingInterval(SCHEDULING_INTERVAL);
        return datacenter;
    }
    
    /**
     * Creates a host with custom specifications.
     * 
     * @param simulation The CloudSim simulation instance
     * @param id The host ID
     * @param spec The host specification
     * @return A configured Host instance
     */
    private static Host createCustomHost(CloudSimPlus simulation, int id, HostSpec spec) {
        List<Pe> peList = new ArrayList<>(spec.getPeCount());
        for (int i = 0; i < spec.getPeCount(); i++) {
            peList.add(new PeSimple(spec.getMipsPerPe()));
        }
        
        HostSimple host = new HostSimple(spec.getRam(), spec.getBw(), spec.getStorage(), peList);
        host.setId(id);
        host.setVmScheduler(new VmSchedulerTimeShared());
        
        return host;
    }
    
    /**
     * Inner class representing custom host specifications.
     */
    public static class HostSpec {
        private final int peCount;
        private final long mipsPerPe;
        private final long ram;
        private final long storage;
        private final long bw;
        
        public HostSpec(int peCount, long mipsPerPe, long ram, long storage, long bw) {
            this.peCount = peCount;
            this.mipsPerPe = mipsPerPe;
            this.ram = ram;
            this.storage = storage;
            this.bw = bw;
        }
        
        // Getters
        public int getPeCount() { return peCount; }
        public long getMipsPerPe() { return mipsPerPe; }
        public long getRam() { return ram; }
        public long getStorage() { return storage; }
        public long getBw() { return bw; }
    }
}