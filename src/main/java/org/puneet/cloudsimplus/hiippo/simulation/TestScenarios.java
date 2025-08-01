package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHost;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.provisioners.PeProvisionerSimple;
import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Factory class for creating test scenarios with different scales and configurations.
 * Provides standardized VM and Host configurations for experimental consistency.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-19
 */
public class TestScenarios {
    private static final Logger logger = LoggerFactory.getLogger(TestScenarios.class);
    
    // VM Configuration Types
    private static final String VM_SMALL = "SMALL";
    private static final String VM_MEDIUM = "MEDIUM";
    private static final String VM_LARGE = "LARGE";
    private static final String VM_XLARGE = "XLARGE";
    
    // Host Configuration Types
    private static final String HOST_BASIC = "BASIC";
    private static final String HOST_STANDARD = "STANDARD";
    private static final String HOST_POWERFUL = "POWERFUL";
    
    // VM Configurations (MIPS, PEs, RAM MB, BW Mbps, Storage MB)
    private static final Map<String, VmConfig> VM_CONFIGS = Map.of(
        VM_SMALL, new VmConfig(1000, 1, 512, 100, 1000),
        VM_MEDIUM, new VmConfig(2000, 2, 2048, 200, 2000),
        VM_LARGE, new VmConfig(4000, 4, 4096, 400, 4000),
        VM_XLARGE, new VmConfig(8000, 8, 8192, 800, 8000)
    );
    
    // Host Configurations (PEs, MIPS per PE, RAM MB, BW Mbps, Storage MB, Max Power W, Static Power %)
    private static final Map<String, HostConfig> HOST_CONFIGS = Map.of(
        HOST_BASIC, new HostConfig(4, 3000, 8192, 1000, 100000, 250, 175),      // 70% of 250W = 175W
        HOST_STANDARD, new HostConfig(8, 3000, 16384, 2000, 200000, 300, 210), // 70% of 300W = 210W
        HOST_POWERFUL, new HostConfig(16, 3000, 32768, 4000, 400000, 400, 280) // 70% of 400W = 280W
    );
    
    /**
     * Creates a test scenario with specified name and specification
     * 
     * @param scenarioName Name of the scenario (Micro, Small, Medium, Large, XLarge)
     * @param spec Scenario specification with VM and Host counts
     * @return TestScenario object ready for simulation
     * @throws ValidationException if scenario parameters are invalid
     */
    public static TestScenario createScenario(String scenarioName, int vmCount, int hostCount) throws ValidationException {
        ScenarioSpec spec = new ScenarioSpec(vmCount, hostCount);
        logger.info("Creating test scenario: {} with {} VMs and {} Hosts", 
            scenarioName, spec.vmCount, spec.hostCount);
            
        try {
            // Check memory availability before creating scenario
            if (!MemoryManager.hasEnoughMemoryForScenario(spec.vmCount, spec.hostCount)) {
                // Try emergency memory cleanup
                MemoryManager.emergencyMemoryCleanup();
                
                // Wait a bit for cleanup to complete
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Re-check after cleanup
                if (!MemoryManager.hasEnoughMemoryForScenario(spec.vmCount, spec.hostCount)) {
                    throw new ValidationException(
                        "Insufficient memory for scenario: " + scenarioName + 
                        " (VMs: " + spec.vmCount + ", Hosts: " + spec.hostCount + ")");
                }
            }
            
            MemoryManager.checkMemoryUsage("Before scenario creation: " + scenarioName);
            
            // Create VMs with mixed configurations
            List<Vm> vms = createMixedVms(spec.vmCount);
            
            // Create Hosts with appropriate capacity
            List<Host> hosts = createHostsForScenario(spec.hostCount, spec.vmCount);
            
            // Create Cloudlets for the VMs
            List<Cloudlet> cloudlets = createCloudletsForVms(vms);
            
            // Validate scenario configuration
            validateScenario(vms, hosts, scenarioName);
            
            TestScenario scenario = new TestScenario(scenarioName, vms, hosts, cloudlets);
            
            logger.info("Successfully created scenario: {} - Total VM capacity: {} MIPS, " +
                "Total Host capacity: {} MIPS", 
                scenarioName, calculateTotalVmMips(vms), calculateTotalHostMips(hosts));
                
            MemoryManager.checkMemoryUsage("After scenario creation: " + scenarioName);
            
            return scenario;
            
        } catch (Exception e) {
            logger.error("Failed to create scenario: {}", scenarioName, e);
            throw new ValidationException("Failed to create scenario: " + scenarioName, e);
        }
    }
    
    /**
     * Creates a mixed set of VMs with different configurations
     * Distribution: 40% Small, 30% Medium, 20% Large, 10% XLarge
     * 
     * @param vmCount Total number of VMs to create
     * @return List of configured VMs
     */
    private static List<Vm> createMixedVms(int vmCount) {
        logger.debug("Creating {} VMs with mixed configurations", vmCount);
        
        List<Vm> vms = new ArrayList<>();
        Random random = ExperimentConfig.getRandomGenerator(0);
        
        // Calculate VM distribution - reduce XLARGE VMs for better allocation
        int smallCount = (int) (vmCount * 0.5);   // 50% SMALL (increased from 40%)
        int mediumCount = (int) (vmCount * 0.3);  // 30% MEDIUM (unchanged)
        int largeCount = (int) (vmCount * 0.15);  // 15% LARGE (reduced from 20%)
        int xlargeCount = vmCount - smallCount - mediumCount - largeCount; // 5% XLARGE (reduced from 10%)
        
        // Create VMs batch by batch to manage memory
        vms.addAll(createVmBatch(0, smallCount, VM_SMALL, random));
        vms.addAll(createVmBatch(smallCount, mediumCount, VM_MEDIUM, random));
        vms.addAll(createVmBatch(smallCount + mediumCount, largeCount, VM_LARGE, random));
        vms.addAll(createVmBatch(smallCount + mediumCount + largeCount, 
            xlargeCount, VM_XLARGE, random));
        
        // Shuffle VMs for random distribution
        Collections.shuffle(vms, random);
        
        logger.debug("Created VM distribution - Small: {}, Medium: {}, Large: {}, XLarge: {}",
            smallCount, mediumCount, largeCount, xlargeCount);
        
        return vms;
    }
    
    /**
     * Creates a batch of VMs with specified configuration
     * 
     * @param startId Starting ID for VMs
     * @param count Number of VMs to create
     * @param type VM configuration type
     * @param random Random generator for variations
     * @return List of created VMs
     */
    private static List<Vm> createVmBatch(int startId, int count, String type, Random random) {
        VmConfig config = VM_CONFIGS.get(type);
        
        return IntStream.range(startId, startId + count)
            .mapToObj(i -> {
                // Add ±10% variation to MIPS for realistic scenarios
                double mipsVariation = 0.9 + (random.nextDouble() * 0.2);
                long mips = (long) (config.mips * mipsVariation);
                
                Vm vm = new VmSimple(i, mips, config.pes);
                vm.setRam(config.ram)
                  .setBw(config.bw)
                  .setSize(config.storage)
                  .setCloudletScheduler(new CloudletSchedulerTimeShared());
                
                vm.setDescription(String.format("VM_%s_%d", type, i));
                
                return vm;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Creates hosts with appropriate capacity for the scenario
     * 
     * @param hostCount Number of hosts to create
     * @param vmCount Number of VMs (to determine host capacity)
     * @return List of configured hosts
     */
    private static List<Host> createHostsForScenario(int hostCount, int vmCount) {
        logger.debug("Creating {} hosts for {} VMs", hostCount, vmCount);
        
        List<Host> hosts = new ArrayList<>();
        
        // Determine host type distribution based on VM to Host ratio
        double vmHostRatio = (double) vmCount / hostCount;
        
        String hostType;
        if (vmHostRatio < 3) {
            hostType = HOST_BASIC;
        } else if (vmHostRatio < 6) {
            hostType = HOST_STANDARD;
        } else {
            hostType = HOST_POWERFUL;
        }
        
        // Create hosts with better distribution for large scenarios
        // Ensure enough powerful hosts for XLARGE VMs
        int powerfulCount = (int) Math.ceil(hostCount * 0.4); // 40% powerful hosts (increased from 20%)
        int standardCount = (int) Math.ceil(hostCount * 0.4); // 40% standard hosts (increased from 50%)
        int basicCount = hostCount - powerfulCount - standardCount; // 20% basic hosts (reduced from 30%)
        
        hosts.addAll(createHostBatch(0, basicCount, HOST_BASIC));
        hosts.addAll(createHostBatch(basicCount, standardCount, HOST_STANDARD));
        hosts.addAll(createHostBatch(basicCount + standardCount, powerfulCount, HOST_POWERFUL));
        
        // Shuffle hosts for random distribution
        Collections.shuffle(hosts, ExperimentConfig.getRandomGenerator(0));
        
        logger.debug("Created host distribution - Basic: {}, Standard: {}, Powerful: {}",
            basicCount, standardCount, powerfulCount);
        
        return hosts;
    }
    
    /**
     * Creates a batch of hosts with specified configuration
     * 
     * @param startId Starting ID for hosts
     * @param count Number of hosts to create
     * @param type Host configuration type
     * @return List of created hosts
     */
    private static List<Host> createHostBatch(int startId, int count, String type) {
        if (count <= 0) return new ArrayList<>();
        
        HostConfig config = HOST_CONFIGS.get(type);
        
        return IntStream.range(startId, startId + count)
            .mapToObj(i -> {
                List<Pe> peList = IntStream.range(0, config.pes)
                    .mapToObj(peId -> new PeSimple(config.mipsPerPe, 
                        new PeProvisionerSimple()))
                    .collect(Collectors.toList());
                
                Host host = new HostSimple(config.ram, config.bw, config.storage, peList);
                host.setId(i);
                host.setVmScheduler(new VmSchedulerTimeShared());
                host.setRamProvisioner(new ResourceProvisionerSimple());
                host.setBwProvisioner(new ResourceProvisionerSimple());
                
                // Set power model
                PowerModelHost powerModel = new PowerModelHostSimple(
                    config.maxPower, config.staticPowerPercent);
                host.setPowerModel(powerModel);
                
                // Enable state history for monitoring
                // host.enableStateHistory(); // Method removed in CloudSim Plus 8.0.0
                
                return host;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Creates cloudlets for VMs with realistic execution patterns
     * 
     * @param vms List of VMs to create cloudlets for
     * @return List of cloudlets
     */
    private static List<Cloudlet> createCloudletsForVms(List<Vm> vms) {
        logger.debug("Creating {} cloudlets for VMs", vms.size());
        
        Random random = ExperimentConfig.getRandomGenerator(0);
        List<Cloudlet> cloudlets = new ArrayList<>();
        
        // Create cloudlets for VMs with much longer execution time to show resource utilization
        for (int i = 0; i < vms.size(); i++) {
            Vm vm = vms.get(i);
            
            // CRITICAL FIX: Create much longer cloudlets to ensure meaningful resource utilization
            // Use a multiplier that ensures cloudlets run for a significant amount of simulation time
            long baseLength = (long) (vm.getMips() * 10000000); // Increased to 10M for much longer execution
            long length = baseLength + random.nextInt((int)(baseLength * 0.5)); // Add some randomness
            
            // Ensure minimum length to prevent instant completion
            length = Math.max(length, 1000000); // At least 1M instructions
            
            // Create cloudlet with full resource utilization
            Cloudlet cloudlet = new CloudletSimple(i, length, (int) vm.getPesNumber())
                .setFileSize(10000)  // Increased file size for more realistic I/O
                .setOutputSize(10000) // Increased output size
                .setUtilizationModelCpu(new UtilizationModelFull())  // Use 100% CPU
                .setUtilizationModelRam(new UtilizationModelFull())  // Use 100% RAM
                .setUtilizationModelBw(new UtilizationModelFull());  // Use 100% bandwidth
            
            cloudlets.add(cloudlet);
            
            logger.debug("Created cloudlet {} with length {} for VM {} (MIPS: {})", 
                i, length, vm.getId(), vm.getMips());
        }
        
        logger.info("Created {} cloudlets with average length: {}", 
            cloudlets.size(), 
            cloudlets.stream().mapToLong(Cloudlet::getLength).average().orElse(0));
        
        return cloudlets;
    }
    
    /**
     * Validates scenario configuration
     * 
     * @param vms List of VMs
     * @param hosts List of hosts
     * @param scenarioName Name of scenario
     * @throws ValidationException if scenario is invalid
     */
    private static void validateScenario(List<Vm> vms, List<Host> hosts, String scenarioName) throws ValidationException {
        if (vms == null || vms.isEmpty()) {
            throw new ValidationException("No VMs created for scenario: " + scenarioName);
        }
        
        if (hosts == null || hosts.isEmpty()) {
            throw new ValidationException("No hosts created for scenario: " + scenarioName);
        }
        
        // Check if total host capacity can accommodate all VMs
        double totalVmMips = calculateTotalVmMips(vms);
        double totalHostMips = calculateTotalHostMips(hosts);
        
        if (totalVmMips > totalHostMips) {
            logger.warn("Scenario {} has insufficient host capacity. VM MIPS: {}, Host MIPS: {}",
                scenarioName, totalVmMips, totalHostMips);
            // This is acceptable as it tests oversubscription scenarios
        }
        
        double capacityRatio = totalHostMips / totalVmMips;
        logger.info("Scenario {} capacity ratio: {:.2f} (Host/VM MIPS)", 
            scenarioName, capacityRatio);
        
        // Validate individual components
        for (Vm vm : vms) {
            if (vm.getTotalMipsCapacity() <= 0) {
                throw new ValidationException("Invalid VM configuration: " + vm.getId());
            }
        }
        
        for (Host host : hosts) {
            if (host.getTotalMipsCapacity() <= 0) {
                throw new ValidationException("Invalid host configuration: " + host.getId());
            }
        }
    }
    
    /**
     * Calculates total MIPS capacity of all VMs
     * 
     * @param vms List of VMs
     * @return Total MIPS capacity
     */
    private static double calculateTotalVmMips(List<Vm> vms) {
        return vms.stream()
            .mapToDouble(Vm::getTotalMipsCapacity)
            .sum();
    }
    
    /**
     * Calculates total MIPS capacity of all hosts
     * 
     * @param hosts List of hosts
     * @return Total MIPS capacity
     */
    private static double calculateTotalHostMips(List<Host> hosts) {
        return hosts.stream()
            .mapToDouble(Host::getTotalMipsCapacity)
            .sum();
    }
    
    /**
     * Creates a TestScenario from a ScenarioGenerator-generated scenario.
     * This allows advanced users to use ScenarioGenerator for custom scenarios
     * while maintaining compatibility with the main experiment flow.
     *
     * Example usage:
     *   ScenarioGenerator generator = new ScenarioGenerator(seed);
     *   TestScenarios.TestScenario scenario = TestScenarios.fromScenarioGenerator(
     *       generator.generateScenario(...));
     *
     * @param scenario ScenarioGenerator-generated scenario
     * @return TestScenario compatible with the main experiment flow
     */
    public static TestScenario fromScenarioGenerator(TestScenario scenario) {
        // This is a pass-through, but allows for future adaptation if needed
        return scenario;
    }
    
    /**
     * Inner class representing VM configuration
     */
    private static class VmConfig {
        final long mips;
        final int pes;
        final long ram;
        final long bw;
        final long storage;
        
        VmConfig(long mips, int pes, long ram, long bw, long storage) {
            this.mips = mips;
            this.pes = pes;
            this.ram = ram;
            this.bw = bw;
            this.storage = storage;
        }
    }
    
    /**
     * Inner class representing Host configuration
     */
    private static class ScenarioSpec {
        final int vmCount;
        final int hostCount;
        
        ScenarioSpec(int vmCount, int hostCount) {
            this.vmCount = vmCount;
            this.hostCount = hostCount;
        }
    }
    
    private static class HostConfig {
        final int pes;
        final long mipsPerPe;
        final long ram;
        final long bw;
        final long storage;
        final double maxPower;
        final double staticPowerPercent;
        
        HostConfig(int pes, long mipsPerPe, long ram, long bw, long storage, 
                   double maxPower, double staticPowerPercent) {
            this.pes = pes;
            this.mipsPerPe = mipsPerPe;
            this.ram = ram;
            this.bw = bw;
            this.storage = storage;
            this.maxPower = maxPower;
            this.staticPowerPercent = staticPowerPercent;
        }
    }
    
    /**
     * Test scenario container class
     */
    public static class TestScenario {
        private final String name;
        private final List<Vm> vms;
        private final List<Host> hosts;
        private final List<Cloudlet> cloudlets;
        private final long creationTimestamp;
        
        public TestScenario(String name, List<Vm> vms, List<Host> hosts, List<Cloudlet> cloudlets) {
            this.name = Objects.requireNonNull(name, "Scenario name cannot be null");
            this.vms = Collections.unmodifiableList(
                Objects.requireNonNull(vms, "VMs list cannot be null"));
            this.hosts = Collections.unmodifiableList(
                Objects.requireNonNull(hosts, "Hosts list cannot be null"));
            this.cloudlets = Collections.unmodifiableList(
                Objects.requireNonNull(cloudlets, "Cloudlets list cannot be null"));
            this.creationTimestamp = System.currentTimeMillis();
        }
        
        public String getName() { return name; }
        public List<Vm> getVms() { return vms; }
        public List<Host> getHosts() { return hosts; }
        public List<Cloudlet> getCloudlets() { return cloudlets; }
        public long getCreationTimestamp() { return creationTimestamp; }
        
        public int getVmCount() { return vms.size(); }
        public int getHostCount() { return hosts.size(); }
        
        @Override
        public String toString() {
            return String.format("TestScenario[name=%s, vms=%d, hosts=%d, cloudlets=%d]",
                name, vms.size(), hosts.size(), cloudlets.size());
        }
    }
}