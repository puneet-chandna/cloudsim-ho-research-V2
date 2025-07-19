package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
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
import org.cloudsimplus.utilizationmodels.UtilizationModelStochastic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.BatchProcessor;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Advanced scenario generator for creating diverse experimental scenarios.
 * Supports various workload patterns, heterogeneity levels, and special test cases.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-20
 */
public class ScenarioGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioGenerator.class);
    
    // Scenario types
    public enum ScenarioType {
        HOMOGENEOUS("Homogeneous VMs and Hosts"),
        HETEROGENEOUS("Mixed VM and Host configurations"),
        BURSTY("Bursty workload patterns"),
        STEADY("Steady workload patterns"),
        OVERSUBSCRIBED("More VM capacity than host capacity"),
        UNDERSUBSCRIBED("More host capacity than VM capacity"),
        IMBALANCED("Imbalanced VM sizes"),
        STRESS_TEST("Maximum stress on system"),
        ENERGY_EFFICIENT("Energy efficiency focused"),
        HIGH_AVAILABILITY("High availability requirements");
        
        private final String description;
        
        ScenarioType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Workload patterns
    public enum WorkloadPattern {
        CONSTANT("Constant utilization"),
        PERIODIC("Periodic fluctuations"),
        RANDOM("Random variations"),
        INCREASING("Gradually increasing"),
        DECREASING("Gradually decreasing"),
        SPIKE("Sudden spikes"),
        WAVE("Wave-like pattern");
        
        private final String description;
        
        WorkloadPattern(String description) {
            this.description = description;
        }
    }
    
    // VM size distribution
    public enum VmSizeDistribution {
        UNIFORM("All VMs same size"),
        NORMAL("Normal distribution of sizes"),
        BIMODAL("Two peaks - small and large"),
        EXPONENTIAL("Many small, few large"),
        CUSTOM("Custom distribution");
        
        private final String description;
        
        VmSizeDistribution(String description) {
            this.description = description;
        }
    }
    
    private final Random random;
    private final AtomicInteger vmIdCounter = new AtomicInteger(0);
    private final AtomicInteger hostIdCounter = new AtomicInteger(0);
    private final AtomicInteger cloudletIdCounter = new AtomicInteger(0);
    
    /**
     * Creates a new scenario generator with specified random seed
     * 
     * @param seed Random seed for reproducibility
     */
    public ScenarioGenerator(long seed) {
        this.random = new Random(seed);
        logger.info("Initialized ScenarioGenerator with seed: {}", seed);
    }
    
    /**
     * Creates a new scenario generator using experiment configuration
     * 
     * @param replication Replication number for seed generation
     */
    public ScenarioGenerator(int replication) {
        this(ExperimentConfig.RANDOM_SEED + replication);
    }
    
    /**
     * Generates a scenario with specified parameters
     * 
     * @param name Scenario name
     * @param vmCount Number of VMs
     * @param hostCount Number of hosts
     * @param type Scenario type
     * @param workloadPattern Workload pattern
     * @param vmDistribution VM size distribution
     * @return Generated test scenario
     * @throws ValidationException if parameters are invalid
     */
    public TestScenarios.TestScenario generateScenario(
            String name,
            int vmCount,
            int hostCount,
            ScenarioType type,
            WorkloadPattern workloadPattern,
            VmSizeDistribution vmDistribution) {
        
        logger.info("Generating scenario: {} - Type: {}, VMs: {}, Hosts: {}, Workload: {}, Distribution: {}",
            name, type, vmCount, hostCount, workloadPattern, vmDistribution);
        
        try {
            // Validate parameters
            validateScenarioParameters(vmCount, hostCount);
            
            // Check memory availability
            if (!MemoryManager.hasEnoughMemoryForScenario(vmCount, hostCount)) {
                throw new ValidationException(
                    String.format("Insufficient memory for scenario: %s (VMs: %d, Hosts: %d)",
                        name, vmCount, hostCount));
            }
            
            MemoryManager.checkMemoryUsage("Before generating scenario: " + name);
            
            // Generate components based on scenario type
            List<Host> hosts = generateHosts(hostCount, type);
            List<Vm> vms = generateVms(vmCount, vmDistribution, type);
            List<Cloudlet> cloudlets = generateCloudlets(vms, workloadPattern);
            
            // Apply scenario-specific modifications
            applyScenarioTypeModifications(vms, hosts, type);
            
            // Validate generated scenario
            validateGeneratedScenario(vms, hosts, cloudlets, name);
            
            TestScenarios.TestScenario scenario = 
                new TestScenarios.TestScenario(name, vms, hosts, cloudlets);
            
            logger.info("Successfully generated scenario: {} - Total VM MIPS: {}, Total Host MIPS: {}",
                name, calculateTotalMips(vms), calculateTotalMips(hosts));
            
            MemoryManager.checkMemoryUsage("After generating scenario: " + name);
            
            return scenario;
            
        } catch (Exception e) {
            logger.error("Failed to generate scenario: {}", name, e);
            throw new ValidationException("Failed to generate scenario: " + name, e);
        }
    }
    
    /**
     * Generates scenarios for parameter sensitivity analysis
     * 
     * @param baseVmCount Base number of VMs
     * @param baseHostCount Base number of hosts
     * @param parameter Parameter to vary
     * @param values Parameter values to test
     * @return List of scenarios with varied parameter
     */
    public List<TestScenarios.TestScenario> generateParameterSensitivityScenarios(
            int baseVmCount,
            int baseHostCount,
            String parameter,
            double[] values) {
        
        logger.info("Generating parameter sensitivity scenarios for: {} with {} values",
            parameter, values.length);
        
        List<TestScenarios.TestScenario> scenarios = new ArrayList<>();
        
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            String scenarioName = String.format("ParamSensitivity_%s_%.2f", parameter, value);
            
            try {
                // Adjust counts based on parameter
                int vmCount = baseVmCount;
                int hostCount = baseHostCount;
                
                if (parameter.equals("VM_HOST_RATIO")) {
                    vmCount = (int) (baseHostCount * value);
                } else if (parameter.equals("SCALE_FACTOR")) {
                    vmCount = (int) (baseVmCount * value);
                    hostCount = (int) (baseHostCount * value);
                }
                
                TestScenarios.TestScenario scenario = generateScenario(
                    scenarioName,
                    vmCount,
                    hostCount,
                    ScenarioType.HETEROGENEOUS,
                    WorkloadPattern.RANDOM,
                    VmSizeDistribution.NORMAL
                );
                
                scenarios.add(scenario);
                
            } catch (Exception e) {
                logger.warn("Failed to generate scenario for parameter value: {} = {}",
                    parameter, value, e);
            }
        }
        
        logger.info("Generated {} parameter sensitivity scenarios", scenarios.size());
        return scenarios;
    }
    
    /**
     * Generates scenarios for scalability testing
     * 
     * @param scaleSizes Array of scale sizes to test
     * @return List of scalability test scenarios
     */
    public List<TestScenarios.TestScenario> generateScalabilityScenarios(int[] scaleSizes) {
        logger.info("Generating scalability scenarios for {} different scales", scaleSizes.length);
        
        List<TestScenarios.TestScenario> scenarios = new ArrayList<>();
        
        for (int scale : scaleSizes) {
            // Calculate proportional host count (roughly 5:1 VM to host ratio)
            int hostCount = Math.max(1, scale / 5);
            String scenarioName = String.format("Scalability_%dVMs_%dHosts", scale, hostCount);
            
            try {
                TestScenarios.TestScenario scenario = generateScenario(
                    scenarioName,
                    scale,
                    hostCount,
                    ScenarioType.HETEROGENEOUS,
                    WorkloadPattern.RANDOM,
                    VmSizeDistribution.NORMAL
                );
                
                scenarios.add(scenario);
                
            } catch (Exception e) {
                logger.warn("Failed to generate scalability scenario for scale: {}", scale, e);
                // Stop generating larger scenarios if memory issues
                if (e.getMessage().contains("memory")) {
                    logger.warn("Stopping scalability scenario generation due to memory constraints");
                    break;
                }
            }
        }
        
        logger.info("Generated {} scalability scenarios", scenarios.size());
        return scenarios;
    }
    
    /**
     * Generates hosts based on scenario type
     * 
     * @param count Number of hosts to generate
     * @param type Scenario type
     * @return List of generated hosts
     */
    private List<Host> generateHosts(int count, ScenarioType type) {
        logger.debug("Generating {} hosts for scenario type: {}", count, type);
        
        List<Host> hosts = new ArrayList<>();
        
        // Process in batches for memory efficiency
        BatchProcessor.processBatches(
            IntStream.range(0, count).boxed().collect(Collectors.toList()),
            batch -> {
                for (Integer i : batch) {
                    Host host = createHost(i, type);
                    hosts.add(host);
                }
            },
            10 // Process 10 hosts at a time
        );
        
        return hosts;
    }
    
    /**
     * Creates a single host based on scenario type
     * 
     * @param id Host ID
     * @param type Scenario type
     * @return Created host
     */
    private Host createHost(int id, ScenarioType type) {
        int hostId = hostIdCounter.getAndIncrement();
        
        // Determine host configuration based on scenario type
        int pes;
        long mipsPerPe;
        long ram;
        long bw;
        long storage;
        double maxPower;
        double staticPowerPercent;
        
        switch (type) {
            case HOMOGENEOUS:
                pes = 8;
                mipsPerPe = 3000;
                ram = 16384;
                bw = 10000;
                storage = 1000000;
                maxPower = 300;
                staticPowerPercent = 0.7;
                break;
                
            case ENERGY_EFFICIENT:
                pes = 4 + random.nextInt(5); // 4-8 PEs
                mipsPerPe = 2000 + random.nextInt(1000); // 2000-3000 MIPS
                ram = 8192 + random.nextInt(8192); // 8-16 GB
                bw = 5000 + random.nextInt(5000);
                storage = 500000 + random.nextInt(500000);
                maxPower = 150 + random.nextDouble() * 100; // 150-250W
                staticPowerPercent = 0.5 + random.nextDouble() * 0.2; // 0.5-0.7
                break;
                
            case STRESS_TEST:
                pes = 16;
                mipsPerPe = 4000;
                ram = 32768;
                bw = 20000;
                storage = 2000000;
                maxPower = 500;
                staticPowerPercent = 0.8;
                break;
                
            default: // HETEROGENEOUS and others
                pes = 4 + random.nextInt(13); // 4-16 PEs
                mipsPerPe = 2000 + random.nextInt(2000); // 2000-4000 MIPS
                ram = 8192 + random.nextInt(24576); // 8-32 GB
                bw = 5000 + random.nextInt(15000);
                storage = 500000 + random.nextInt(1500000);
                maxPower = 200 + random.nextDouble() * 300; // 200-500W
                staticPowerPercent = 0.6 + random.nextDouble() * 0.3; // 0.6-0.9
                break;
        }
        
        // Create PEs
        List<Pe> peList = IntStream.range(0, pes)
            .mapToObj(peId -> new PeSimple(mipsPerPe, new PeProvisionerSimple()))
            .collect(Collectors.toList());
        
        // Create host
        Host host = new HostSimple(ram, bw, storage, peList);
        host.setId(hostId);
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.setRamProvisioner(new ResourceProvisionerSimple());
        host.setBwProvisioner(new ResourceProvisionerSimple());
        
        // Set power model
        PowerModelHost powerModel = new PowerModelHostSimple(maxPower, staticPowerPercent);
        host.setPowerModel(powerModel);
        
        // Enable state history
        host.enableStateHistory();
        
        return host;
    }
    
    /**
     * Generates VMs based on size distribution and scenario type
     * 
     * @param count Number of VMs to generate
     * @param distribution VM size distribution
     * @param type Scenario type
     * @return List of generated VMs
     */
    private List<Vm> generateVms(int count, VmSizeDistribution distribution, ScenarioType type) {
        logger.debug("Generating {} VMs with distribution: {}", count, distribution);
        
        List<Vm> vms = new ArrayList<>();
        
        // Generate VM sizes based on distribution
        int[] vmSizes = generateVmSizes(count, distribution);
        
        // Process in batches for memory efficiency
        BatchProcessor.processBatches(
            IntStream.range(0, count).boxed().collect(Collectors.toList()),
            batch -> {
                for (Integer i : batch) {
                    Vm vm = createVm(i, vmSizes[i], type);
                    vms.add(vm);
                }
            },
            20 // Process 20 VMs at a time
        );
        
        return vms;
    }
    
    /**
     * Generates VM sizes based on distribution
     * 
     * @param count Number of VMs
     * @param distribution Size distribution
     * @return Array of VM sizes (0=small, 1=medium, 2=large, 3=xlarge)
     */
    private int[] generateVmSizes(int count, VmSizeDistribution distribution) {
        int[] sizes = new int[count];
        
        switch (distribution) {
            case UNIFORM:
                Arrays.fill(sizes, 1); // All medium
                break;
                
            case NORMAL:
                // Normal distribution centered on medium
                for (int i = 0; i < count; i++) {
                    double gaussian = random.nextGaussian() * 0.8 + 1.5;
                    sizes[i] = Math.max(0, Math.min(3, (int) Math.round(gaussian)));
                }
                break;
                
            case BIMODAL:
                // 40% small, 20% medium, 40% large
                for (int i = 0; i < count; i++) {
                    double rand = random.nextDouble();
                    if (rand < 0.4) sizes[i] = 0;
                    else if (rand < 0.6) sizes[i] = 1;
                    else sizes[i] = 2;
                }
                break;
                
            case EXPONENTIAL:
                // Many small, few large
                for (int i = 0; i < count; i++) {
                    double exp = -Math.log(1.0 - random.nextDouble()) / 2.0;
                    sizes[i] = Math.min(3, (int) exp);
                }
                break;
                
            default:
                // Random distribution
                for (int i = 0; i < count; i++) {
                    sizes[i] = random.nextInt(4);
                }
        }
        
        return sizes;
    }
    
    /**
     * Creates a single VM based on size and scenario type
     * 
     * @param index VM index
     * @param size VM size (0=small, 1=medium, 2=large, 3=xlarge)
     * @param type Scenario type
     * @return Created VM
     */
    private Vm createVm(int index, int size, ScenarioType type) {
        int vmId = vmIdCounter.getAndIncrement();
        
        // Base configurations
        long[] mipsValues = {1000, 2000, 4000, 8000};
        int[] pesValues = {1, 2, 4, 8};
        long[] ramValues = {512, 2048, 4096, 8192};
        long[] bwValues = {100, 200, 400, 800};
        long[] storageValues = {1000, 2000, 4000, 8000};
        
        // Get base values
        long mips = mipsValues[size];
        int pes = pesValues[size];
        long ram = ramValues[size];
        long bw = bwValues[size];
        long storage = storageValues[size];
        
        // Apply variations based on scenario type
        if (type == ScenarioType.HETEROGENEOUS || type == ScenarioType.IMBALANCED) {
            // Add ±20% variation
            mips = (long) (mips * (0.8 + random.nextDouble() * 0.4));
            ram = (long) (ram * (0.8 + random.nextDouble() * 0.4));
        }
        
        // Create VM
        Vm vm = new VmSimple(vmId, mips, pes);
        vm.setRam(ram)
          .setBw(bw)
          .setSize(storage)
          .setCloudletScheduler(new CloudletSchedulerTimeShared());
        
        vm.setDescription(String.format("VM_Size%d_%d", size, vmId));
        
        return vm;
    }
    
    /**
     * Generates cloudlets based on workload pattern
     * 
     * @param vms List of VMs
     * @param pattern Workload pattern
     * @return List of generated cloudlets
     */
    private List<Cloudlet> generateCloudlets(List<Vm> vms, WorkloadPattern pattern) {
        logger.debug("Generating {} cloudlets with pattern: {}", vms.size(), pattern);
        
        return IntStream.range(0, vms.size())
            .mapToObj(i -> createCloudlet(vms.get(i), pattern))
            .collect(Collectors.toList());
    }
    
    /**
     * Creates a cloudlet for a VM with specified workload pattern
     * 
     * @param vm Target VM
     * @param pattern Workload pattern
     * @return Created cloudlet
     */
    private Cloudlet createCloudlet(Vm vm, WorkloadPattern pattern) {
        int cloudletId = cloudletIdCounter.getAndIncrement();
        
        // Base cloudlet length proportional to VM capacity
        long baseLength = (long) (vm.getTotalMipsCapacity() * 1000);
        long length = (long) (baseLength * (0.8 + random.nextDouble() * 0.4));
        
        // Create utilization models based on pattern
        UtilizationModel cpuModel = createUtilizationModel(pattern, 0.1, 0.9);
        UtilizationModel ramModel = createUtilizationModel(pattern, 0.2, 0.8);
        UtilizationModel bwModel = createUtilizationModel(pattern, 0.1, 0.5);
        
        Cloudlet cloudlet = new CloudletSimple(cloudletId, length, (int) vm.getNumberOfPes());
        cloudlet.setFileSize(300)
            .setOutputSize(300)
            .setUtilizationModelCpu(cpuModel)
            .setUtilizationModelRam(ramModel)
            .setUtilizationModelBw(bwModel);
        
        return cloudlet;
    }
    
    /**
     * Creates utilization model based on workload pattern
     * 
     * @param pattern Workload pattern
     * @param min Minimum utilization
     * @param max Maximum utilization
     * @return Utilization model
     */
    private UtilizationModel createUtilizationModel(WorkloadPattern pattern, double min, double max) {
        switch (pattern) {
            case CONSTANT:
                return new UtilizationModelDynamic((min + max) / 2);
                
            case RANDOM:
                return new UtilizationModelStochastic();
                
            case INCREASING:
                return new UtilizationModelDynamic(min)
                    .setMaxResourceUtilization(max)
                    .setUtilizationUpdateFunction(time -> min + (max - min) * time / 1000.0);
                
            case DECREASING:
                return new UtilizationModelDynamic(max)
                    .setMaxResourceUtilization(max)
                    .setUtilizationUpdateFunction(time -> max - (max - min) * time / 1000.0);
                
            case PERIODIC:
                return new UtilizationModelDynamic(min)
                    .setMaxResourceUtilization(max)
                    .setUtilizationUpdateFunction(time -> 
                        min + (max - min) * (1 + Math.sin(time / 100.0)) / 2);
                
            case SPIKE:
                return new UtilizationModelDynamic(min)
                    .setMaxResourceUtilization(max)
                    .setUtilizationUpdateFunction(time -> {
                        // Spike every 300 time units
                        return ((time % 300) < 50) ? max : min;
                    });
                
            case WAVE:
                return new UtilizationModelDynamic(min)
                    .setMaxResourceUtilization(max)
                    .setUtilizationUpdateFunction(time -> 
                        min + (max - min) * Math.pow(Math.sin(time / 150.0), 2));
                
            default:
                return new UtilizationModelDynamic(0.5);
        }
    }
    
    /**
     * Applies scenario-specific modifications to VMs and hosts
     * 
     * @param vms List of VMs
     * @param hosts List of hosts
     * @param type Scenario type
     */
    private void applyScenarioTypeModifications(List<Vm> vms, List<Host> hosts, ScenarioType type) {
        switch (type) {
            case OVERSUBSCRIBED:
                // Increase VM requirements by 20%
                vms.forEach(vm -> {
                    long newMips = (long) (vm.getMips() * 1.2);
                    vm.setMips(newMips);
                });
                break;
                
            case UNDERSUBSCRIBED:
                // Reduce VM requirements by 30%
                vms.forEach(vm -> {
                    long newMips = (long) (vm.getMips() * 0.7);
                    vm.setMips(newMips);
                });
                break;
                
            case HIGH_AVAILABILITY:
                // Require redundancy - effectively double VM requirements
                vms.forEach(vm -> {
                    vm.setDescription(vm.getDescription() + "_HA");
                });
                break;
                
            case IMBALANCED:
                // Make some VMs much larger
                for (int i = 0; i < vms.size() / 10; i++) {
                    Vm vm = vms.get(random.nextInt(vms.size()));
                    vm.setMips(vm.getMips() * 3);
                    vm.setRam(vm.getRam() * 3);
                }
                break;
                
            default:
                // No modifications needed
                break;
        }
    }
    
    /**
     * Validates scenario parameters
     * 
     * @param vmCount Number of VMs
     * @param hostCount Number of hosts
     * @throws ValidationException if parameters are invalid
     */
    private void validateScenarioParameters(int vmCount, int hostCount) {
        if (vmCount <= 0) {
            throw new ValidationException("VM count must be positive: " + vmCount);
        }
        
        if (hostCount <= 0) {
            throw new ValidationException("Host count must be positive: " + hostCount);
        }
        
        if (vmCount > 10000) {
            throw new ValidationException("VM count too large for 16GB system: " + vmCount);
        }
        
        if (hostCount > 1000) {
            throw new ValidationException("Host count too large for 16GB system: " + hostCount);
        }
    }
    
    /**
     * Validates generated scenario
     * 
     * @param vms List of VMs
     * @param hosts List of hosts
     * @param cloudlets List of cloudlets
     * @param name Scenario name
     * @throws ValidationException if scenario is invalid
     */
    private void validateGeneratedScenario(List<Vm> vms, List<Host> hosts, 
                                         List<Cloudlet> cloudlets, String name) {
        if (vms.isEmpty()) {
            throw new ValidationException("No VMs generated for scenario: " + name);
        }
        
        if (hosts.isEmpty()) {
            throw new ValidationException("No hosts generated for scenario: " + name);
        }
        
        if (cloudlets.size() != vms.size()) {
            throw new ValidationException(
                String.format("Cloudlet count (%d) doesn't match VM count (%d) for scenario: %s",
                    cloudlets.size(), vms.size(), name));
        }
        
        // Validate IDs are unique
        Set<Long> vmIds = vms.stream().map(Vm::getId).collect(Collectors.toSet());
        if (vmIds.size() != vms.size()) {
            throw new ValidationException("Duplicate VM IDs in scenario: " + name);
        }
        
        Set<Long> hostIds = hosts.stream().map(Host::getId).collect(Collectors.toSet());
        if (hostIds.size() != hosts.size()) {
            throw new ValidationException("Duplicate host IDs in scenario: " + name);
        }
    }
    
    /**
     * Calculates total MIPS for VMs
     * 
     * @param vms List of VMs
     * @return Total MIPS capacity
     */
    private double calculateTotalMips(List<Vm> vms) {
        return vms.stream()
            .mapToDouble(Vm::getTotalMipsCapacity)
            .sum();
    }
    
    /**
     * Calculates total MIPS for hosts
     * 
     * @param hosts List of hosts
     * @return Total MIPS capacity
     */
    private double calculateTotalMips(List<Host> hosts) {
        return hosts.stream()
            .mapToDouble(Host::getTotalMipsCapacity)
            .sum();
    }
    
    /**
     * Resets ID counters (useful for testing)
     */
    public void resetCounters() {
        vmIdCounter.set(0);
        hostIdCounter.set(0);
        cloudletIdCounter.set(0);
        logger.debug("Reset all ID counters");
    }
}