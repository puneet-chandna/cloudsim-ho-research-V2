# Hippopotamus Optimization Algorithm for CloudSim VM Placement - Research-Grade Implementation 

## Project Overview
Implement a comprehensive research framework for the Hippopotamus Optimization (HO) algorithm applied to Virtual Machine (VM) placement optimization in CloudSim Plus. This Maven-based Java project focuses on academically rigorous implementation with statistical validation and comprehensive performance analysis suitable for publication, optimized for systems with 16GB RAM.

## Core Objectives
1. **Primary**: Implement HO algorithm for VM placement optimization in CloudSim Plus
2. **Secondary**: Compare performance against 3 baseline algorithms with statistical validation
3. **Tertiary**: Conduct parameter sensitivity analysis and scalability testing
4. **Metrics**: Measure resource utilization, SLA violations, power consumption, convergence, and stability
5. **Output**: Generate statistically validated CSV results with confidence intervals and significance tests

## Technical Environment
- **Runtime**: OpenJDK 21.0.7
- **Build Tool**: Maven 3.9.9
- **CloudSim**: cloudsim-plus 8.0.0
- **Data Storage**: CSV files with statistical analysis outputs
- **Statistical Analysis**: Apache Commons Math3 for statistical computations
- **Memory Configuration**: Optimized for 16GB RAM systems

## Optimized Project Structure
```
cloudsim-ho-research/
├── pom.xml
├── App.java
├── src/
│   ├── main/
│   │   ├── java/org/puneet/cloudsimplus/hiippo/
│   │   │   ├── App.java
│   │   │   ├── algorithm/
│   │   │   │   ├── HippopotamusOptimization.java
│   │   │   │   ├── Hippopotamus.java
│   │   │   │   ├── HippopotamusParameters.java
│   │   │   │   ├── ConvergenceAnalyzer.java
│   │   │   │   └── AlgorithmConstants.java
│   │   │   ├── baseline/
│   │   │   │   ├── FirstFitAllocation.java
│   │   │   │   ├── BestFitAllocation.java
│   │   │   │   └── GeneticAlgorithmAllocation.java
│   │   │   ├── policy/
│   │   │   │   ├── HippopotamusVmAllocationPolicy.java
│   │   │   │   ├── BaselineVmAllocationPolicy.java
│   │   │   │   └── AllocationValidator.java
│   │   │   ├── simulation/
│   │   │   │   ├── CloudSimHOSimulation.java
│   │   │   │   ├── HODatacenterBroker.java
│   │   │   │   ├── DatacenterFactory.java
│   │   │   │   ├── ScenarioGenerator.java
│   │   │   │   ├── TestScenarios.java
│   │   │   │   ├── ExperimentRunner.java
│   │   │   │   ├── ExperimentCoordinator.java
│   │   │   │   ├── ParameterTuner.java
│   │   │   │   └── ScalabilityTester.java
│   │   │   ├── statistical/
│   │   │   │   ├── StatisticalValidator.java
│   │   │   │   ├── ComparisonAnalyzer.java
│   │   │   │   ├── ConfidenceInterval.java
│   │   │   │   └── ANOVAResult.java
│   │   │   ├── util/
│   │   │   │   ├── MetricsCollector.java
│   │   │   │   ├── CSVResultsWriter.java
│   │   │   │   ├── ValidationUtils.java
│   │   │   │   ├── ResultValidator.java
│   │   │   │   ├── PerformanceMonitor.java
│   │   │   │   ├── ProgressTracker.java
│   │   │   │   ├── ExperimentConfig.java
│   │   │   │   ├── MemoryManager.java
│   │   │   │   └── BatchProcessor.java
│   │   │   └── exceptions/
│   │   │       ├── HippopotamusOptimizationException.java
│   │   │       ├── StatisticalValidationException.java
│   │   │       ├── ScalabilityTestException.java
│   │   │       └── ValidationException.java
│   │   └── resources/
│   │       ├── config.properties
│   │       ├── algorithm_parameters.properties
│   │       └── logback.xml
│   └── test/
│       └── java/org/puneet/cloudsimplus/hiippo/
│           ├── unit/
│           │   ├── HippopotamusOptimizationTest.java
│           │   ├── BaselineAlgorithmTest.java
│           │   └── MetricsCollectorTest.java
│           ├── integration/
│           │   ├── CloudSimIntegrationTest.java
│           │   └── AllocationPolicyTest.java
│           ├── performance/
│           │   ├── ScalabilityTest.java
│           │   ├── ParameterSensitivityTest.java
│           │   └── ConvergenceTest.java
│           └── statistical/
│               ├── StatisticalValidationTest.java
│               └── ComparisonTest.java
└── results/
    ├── raw_results/
    │   └── main_results.csv
    ├── statistical_analysis/
    │   ├── significance_tests.csv
    │   ├── confidence_intervals.csv
    │   └── effect_sizes.csv
    ├── parameter_sensitivity/
    │   ├── population_size_analysis.csv
    │   ├── iteration_count_analysis.csv
    │   └── convergence_analysis.csv
    ├── scalability_analysis/
    │   ├── time_complexity.csv
    │   └── quality_degradation.csv
    ├── convergence_data/
    │   └── convergence_data.csv
    └── comparison_data/
```

## Optimized Maven Configuration (pom.xml)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>org.puneet.cloudsimplus.hiippo</groupId>
    <artifactId>cloudsim-ho-research</artifactId>
    <version>1.0.0</version>
    
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <cloudsimplus.version>8.0.0</cloudsimplus.version>
    </properties>
    
    <dependencies>
        <!-- CloudSim Plus -->
        <dependency>
            <groupId>org.cloudsimplus</groupId>
            <artifactId>cloudsim-plus</artifactId>
            <version>${cloudsimplus.version}</version>
        </dependency>
        
        <!-- Apache Commons Math (Statistical Analysis) -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-math3</artifactId>
            <version>3.6.1</version>
        </dependency>
        
        <!-- Apache Commons CSV -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-csv</artifactId>
            <version>1.9.0</version>
        </dependency>
        
        <!-- Apache Commons Statistics -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-statistics-descriptive</artifactId>
            <version>1.0</version>
        </dependency>
        
        <!-- SLF4J Logging -->
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.4.8</version>
        </dependency>
        
        <!-- JUnit Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.9.3</version>
            <scope>test</scope>
        </dependency>
        
        <!-- AssertJ for Advanced Testing -->
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.24.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <compilerArgs>
                        <arg>-parameters</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.0.0-M7</version>
                <configuration>
                    <argLine>-Xmx3G</argLine>
                    <parallel>methods</parallel>
                    <threadCount>2</threadCount>
                </configuration>
            </plugin>
            
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>org.puneet.cloudsimplus.hiippo.App</mainClass>
                    <commandlineArgs>-Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=200</commandlineArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Core Algorithm Implementation Requirements

### 1. HippopotamusOptimization.java
**Purpose**: Main HO algorithm implementation with convergence tracking
```java
package org.puneet.cloudsimplus.hiippo.algorithm;

public class HippopotamusOptimization {
    // Algorithm parameters
    private final double alpha = 0.5; // Position update parameter
    private final double beta = 0.3;  // Exploration parameter
    private final double gamma = 0.2; // Exploitation parameter
    
    /**
     * Main optimization method
     * @param vms List of VMs to place
     * @param hosts List of available hosts
     * @return Optimal VM placement solution
     */
    public Solution optimize(List<Vm> vms, List<Host> hosts) {
        // Implementation with convergence tracking
    }
    
    /**
     * Position update equation
     * newPosition = currentPosition + alpha * (leaderPosition - currentPosition) + 
     *               beta * random() * (preyPosition - currentPosition) +
     *               gamma * levy() * (currentPosition)
     */
    private void updatePosition(Hippopotamus hippo, Hippopotamus leader, Hippopotamus prey) {
        // Implementation
    }
    
    /**
     * Multi-objective fitness function
     * fitness = w1 * (1 - resourceUtilization) + w2 * powerConsumption + w3 * slaViolations
     * Note: All objectives normalized to [0,1] range
     */
    private double evaluateFitness(Solution solution) {
        double w1 = 0.4, w2 = 0.3, w3 = 0.3; // Configurable weights
        // Implementation
    }
}
```

### 2. AlgorithmConstants.java
```java
package org.puneet.cloudsimplus.hiippo.algorithm;

public class AlgorithmConstants {
    // HO Algorithm parameters - Optimized for memory efficiency
    public static final int DEFAULT_POPULATION_SIZE = 20; // Reduced from 30
    public static final int DEFAULT_MAX_ITERATIONS = 50;  // Reduced from 100
    public static final double DEFAULT_CONVERGENCE_THRESHOLD = 0.001;
    
    // Position update parameters
    public static final double ALPHA = 0.5; // Leader influence
    public static final double BETA = 0.3;  // Prey influence
    public static final double GAMMA = 0.2; // Random walk influence
    
    // Fitness weights
    public static final double W_UTILIZATION = 0.4;
    public static final double W_POWER = 0.3;
    public static final double W_SLA = 0.3;
    
    // Memory management
    public static final int BATCH_SIZE = 10; // Process VMs in batches
    public static final long MEMORY_CHECK_INTERVAL = 5000; // Check memory every 5 seconds
}
```

### 3. ExperimentConfig.java (Optimized for 16GB Systems)
```java
package org.puneet.cloudsimplus.hiippo.util;

public class ExperimentConfig {
    public static final long RANDOM_SEED = 123456L;
    public static final int REPLICATION_COUNT = 30;
    public static final double CONFIDENCE_LEVEL = 0.95;
    public static final double SIGNIFICANCE_LEVEL = 0.05;
    
    // Memory management settings
    public static final long MAX_HEAP_SIZE = 6L * 1024 * 1024 * 1024; // 6GB
    public static final long MEMORY_WARNING_THRESHOLD = 5L * 1024 * 1024 * 1024; // 5GB
    public static final boolean ENABLE_BATCH_PROCESSING = true;
    
    private static final Map<Integer, Random> randomGenerators = new HashMap<>();
    
    public static void initializeRandomSeed(int replication) {
        long seed = RANDOM_SEED + replication;
        randomGenerators.put(replication, new Random(seed));
        // Set CloudSim Plus random seed
        RandomGenerator.setSeed(seed);
    }
    
    public static Random getRandomGenerator(int replication) {
        return randomGenerators.get(replication);
    }
    
    public static boolean shouldRunGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory > MEMORY_WARNING_THRESHOLD;
    }
}
```

### 4. MemoryManager.java (New Component)
```java
package org.puneet.cloudsimplus.hiippo.util;

public class MemoryManager {
    private static final Logger logger = LoggerFactory.getLogger(MemoryManager.class);
    
    public static void checkMemoryUsage(String phase) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        double usagePercentage = (usedMemory * 100.0) / maxMemory;
        
        logger.info("Memory Usage [{}]: {:.2f}% ({} MB / {} MB)", 
            phase, usagePercentage, 
            usedMemory / (1024 * 1024), 
            maxMemory / (1024 * 1024));
            
        if (usagePercentage > 85) {
            logger.warn("High memory usage detected. Running garbage collection...");
            System.gc();
            Thread.sleep(1000); // Give GC time to run
        }
    }
    
    public static boolean hasEnoughMemoryForScenario(int vmCount, int hostCount) {
        // Estimate memory requirement (rough estimate)
        long estimatedMemory = (vmCount * 50_000L) + (hostCount * 100_000L);
        Runtime runtime = Runtime.getRuntime();
        long availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        
        return availableMemory > estimatedMemory * 2; // Safety factor of 2
    }
}
```

### 5. Optimized Baseline Algorithms (Reduced to 3)

#### FirstFitAllocation.java
```java
package org.puneet.cloudsimplus.hiippo.baseline;

public class FirstFitAllocation extends BaselineVmAllocationPolicy {
    @Override
    public boolean allocateHostForVm(Vm vm) {
        for (Host host : getHostList()) {
            if (host.isSuitableForVm(vm)) {
                return allocateHostForVm(vm, host);
            }
        }
        return false;
    }
    
    @Override
    public String getName() {
        return "FirstFit";
    }
}
```

#### BestFitAllocation.java
```java
package org.puneet.cloudsimplus.hiippo.baseline;

public class BestFitAllocation extends BaselineVmAllocationPolicy {
    @Override
    public boolean allocateHostForVm(Vm vm) {
        Host bestHost = null;
        double minWaste = Double.MAX_VALUE;
        
        for (Host host : getHostList()) {
            if (host.isSuitableForVm(vm)) {
                double cpuWaste = host.getTotalMipsCapacity() - 
                    host.getVmScheduler().getAllocatedMips() - vm.getTotalMipsCapacity();
                double ramWaste = host.getRam().getAvailableResource() - vm.getRam().getCapacity();
                double totalWaste = cpuWaste + ramWaste;
                
                if (totalWaste < minWaste) {
                    minWaste = totalWaste;
                    bestHost = host;
                }
            }
        }
        
        return bestHost != null && allocateHostForVm(vm, bestHost);
    }
    
    @Override
    public String getName() {
        return "BestFit";
    }
}
```

#### GeneticAlgorithmAllocation.java
```java
package org.puneet.cloudsimplus.hiippo.baseline;

public class GeneticAlgorithmAllocation extends BaselineVmAllocationPolicy {
    private static final int POPULATION_SIZE = 30; // Reduced from 50
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    private static final int MAX_GENERATIONS = 50; // Reduced from 100
    private static final int TOURNAMENT_SIZE = 3; // Reduced from 5
    
    @Override
    public boolean allocateHostForVm(Vm vm) {
        // GA-based allocation
    }
    
    private Solution crossover(Solution parent1, Solution parent2) {
        // Single-point crossover implementation
    }
    
    private Solution mutate(Solution solution) {
        // Random mutation implementation
    }
    
    private Solution tournamentSelection(List<Solution> population) {
        // Tournament selection implementation
    }
    
    @Override
    public String getName() {
        return "GA";
    }
}
```

### 6. BatchProcessor.java (New Component for Memory Efficiency)
```java
package org.puneet.cloudsimplus.hiippo.util;

public class BatchProcessor {
    private static final int DEFAULT_BATCH_SIZE = 10;
    
    public static <T> void processBatches(List<T> items, Consumer<List<T>> processor) {
        processBatches(items, processor, DEFAULT_BATCH_SIZE);
    }
    
    public static <T> void processBatches(List<T> items, Consumer<List<T>> processor, int batchSize) {
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, end);
            
            // Check memory before processing batch
            MemoryManager.checkMemoryUsage("Batch " + (i/batchSize + 1));
            
            processor.accept(batch);
            
            // Clean up after batch
            if (ExperimentConfig.shouldRunGarbageCollection()) {
                System.gc();
            }
        }
    }
}
```

### 7. ExperimentCoordinator.java (Optimized Version)
```java
package org.puneet.cloudsimplus.hiippo.simulation;

public class ExperimentCoordinator {
    // Reduced algorithm set
    private final List<String> algorithms = Arrays.asList(
        "HO", "FirstFit", "BestFit", "GA"
    );
    
    // Reduced scenario set (removed XXL and XXXL)
    private final List<String> scenarios = Arrays.asList(
        "Micro", "Small", "Medium", "Large", "XLarge"
    );
    
    // Scenario specifications optimized for 16GB systems
    private final Map<String, ScenarioSpec> scenarioSpecs = Map.of(
        "Micro", new ScenarioSpec(10, 3),      // 10 VMs, 3 Hosts
        "Small", new ScenarioSpec(50, 10),     // 50 VMs, 10 Hosts
        "Medium", new ScenarioSpec(100, 20),   // 100 VMs, 20 Hosts
        "Large", new ScenarioSpec(200, 40),    // 200 VMs, 40 Hosts
        "XLarge", new ScenarioSpec(500, 100)   // 500 VMs, 100 Hosts
    );
    
    public void runCompleteExperiment() {
        ProgressTracker tracker = new ProgressTracker();
        int totalExperiments = algorithms.size() * scenarios.size() * 
                              ExperimentConfig.REPLICATION_COUNT;
        int completed = 0;
        
        // Process scenarios in order of size to avoid memory issues
        for (String scenario : scenarios) {
            // Check if we have enough memory for this scenario
            ScenarioSpec spec = scenarioSpecs.get(scenario);
            if (!MemoryManager.hasEnoughMemoryForScenario(spec.vmCount, spec.hostCount)) {
                logger.warn("Skipping scenario {} due to memory constraints", scenario);
                continue;
            }
            
            // Run all algorithms for this scenario
            runScenarioBatch(scenario, tracker, completed, totalExperiments);
            
            // Clean up between scenarios
            cleanupBetweenScenarios();
            
            completed += algorithms.size() * ExperimentConfig.REPLICATION_COUNT;
        }
        
        // Perform statistical analysis
        performStatisticalAnalysis();
        
        // Generate comparison reports
        generateComparisonReports();
    }
    
    private void runScenarioBatch(String scenario, ProgressTracker tracker, 
                                  int startCount, int totalCount) {
        logger.info("Starting scenario: {}", scenario);
        
        for (String algorithm : algorithms) {
            // Process replications in batches to manage memory
            BatchProcessor.processBatches(
                IntStream.range(0, ExperimentConfig.REPLICATION_COUNT).boxed().toList(),
                replicationBatch -> {
                    for (Integer rep : replicationBatch) {
                        runSingleExperiment(algorithm, scenario, rep);
                        tracker.reportProgress("Experiments", 
                            startCount + rep + 1, totalCount);
                    }
                },
                5 // Process 5 replications at a time
            );
        }
    }
    
    private void cleanupBetweenScenarios() {
        logger.info("Cleaning up between scenarios...");
        System.gc();
        try {
            Thread.sleep(3000); // Give system time to clean up
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void runSingleExperiment(String algorithm, String scenario, int replication) {
        try {
            // Initialize random seed for reproducibility
            ExperimentConfig.initializeRandomSeed(replication);
            
            // Create scenario
            TestScenario testScenario = TestScenarios.createScenario(
                scenario, scenarioSpecs.get(scenario));
            
            // Run experiment
            ExperimentResult result = ExperimentRunner.runExperiment(
                algorithm, testScenario, replication);
            
            // Validate results
            ResultValidator.validateResults(result);
            
            // Save results immediately (don't keep in memory)
            CSVResultsWriter.writeResult(result);
            
        } catch (Exception e) {
            logger.error("Failed experiment: {} {} rep {}", 
                algorithm, scenario, replication, e);
        }
    }
}

class ScenarioSpec {
    final int vmCount;
    final int hostCount;
    
    ScenarioSpec(int vmCount, int hostCount) {
        this.vmCount = vmCount;
        this.hostCount = hostCount;
    }
}
```

### 8. ScalabilityTester.java (Optimized Version)
```java
package org.puneet.cloudsimplus.hiippo.simulation;

public class ScalabilityTester {
    private static final String JVM_ARGS = "-Xmx6G -XX:+UseG1GC -XX:MaxGCPauseMillis=200";
    
    // Reduced maximum sizes for 16GB systems
    private static final int[] VM_COUNTS = {10, 50, 100, 200, 500};
    private static final int[] HOST_COUNTS = {3, 10, 20, 40, 100};
    
    @BeforeEach
    public void configureMemory() {
        // Configure JVM for large scenarios
        System.setProperty("java.vm.options", JVM_ARGS);
    }
    
    public void runScalabilityTest(String algorithm) {
        for (int i = 0; i < VM_COUNTS.length; i++) {
            int vmCount = VM_COUNTS[i];
            int hostCount = HOST_COUNTS[i];
            
            // Check memory before running
            if (!MemoryManager.hasEnoughMemoryForScenario(vmCount, hostCount)) {
                logger.warn("Skipping scalability test: VMs={}, Hosts={} (insufficient memory)", 
                    vmCount, hostCount);
                continue;
            }
            
            try {
                PerformanceMonitor monitor = new PerformanceMonitor();
                monitor.startMonitoring();
                
                // Run test
                ExperimentResult result = runExperiment(algorithm, vmCount, hostCount);
                
                PerformanceMetrics metrics = monitor.stopMonitoring();
                
                // Save scalability metrics
                CSVResultsWriter.writeScalabilityResult(algorithm, vmCount, hostCount, 
                    result, metrics);
                    
                // Clean up
                if (vmCount >= 200) {
                    cleanupAfterLargeTest();
                }
                
            } catch (OutOfMemoryError e) {
                logger.error("OOM for scenario: VMs={}, Hosts={}", vmCount, hostCount);
                cleanupAfterLargeTest();
                break; // Stop testing larger scenarios
            }
        }
    }
    
    private void cleanupAfterLargeTest() {
        System.gc();
        try {
            Thread.sleep(5000); // Give more time for cleanup
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 9. CSV Format Specifications (Unchanged)

#### main_results.csv
```csv
Algorithm,Scenario,Replication,ResourceUtilCPU,ResourceUtilRAM,PowerConsumption,SLAViolations,ExecutionTime,ConvergenceIterations,VmAllocated,VmTotal
HO,Small,1,0.85,0.78,1200.5,2,45.3,45,50,50
HO,Small,2,0.84,0.77,1195.2,3,44.8,48,50,50
FirstFit,Small,1,0.72,0.68,1450.3,8,2.1,1,50,50
```

### 10. App.java (Optimized Main Entry Point)
```java
package org.puneet.cloudsimplus.hiippo;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) {
        try {
            logger.info("Starting Hippopotamus Optimization Research Framework");
            logger.info("System Memory: {} MB total, {} MB max heap", 
                Runtime.getRuntime().totalMemory() / (1024 * 1024),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
            
            // Check system requirements
            if (!checkSystemRequirements()) {
                logger.error("System requirements not met");
                System.exit(1);
            }
            
            // Initialize experiment coordinator
            ExperimentCoordinator coordinator = new ExperimentCoordinator();
            
            // Configure experiments
            coordinator.configureExperiments();
            
            // Run complete experimental suite
            coordinator.runCompleteExperiment();
            
            logger.info("Experiment completed successfully");
            
        } catch (Exception e) {
            logger.error("Fatal error in experiment execution", e);
            System.exit(1);
        }
    }
    
    private static boolean checkSystemRequirements() {
        long maxHeap = Runtime.getRuntime().maxMemory();
        long requiredHeap = 5L * 1024 * 1024 * 1024; // 5GB minimum
        
        if (maxHeap < requiredHeap) {
            logger.error("Insufficient heap size. Required: {} MB, Available: {} MB",
                requiredHeap / (1024 * 1024), maxHeap / (1024 * 1024));
            return false;
        }
        
        return true;
    }
}
```

## Updated Experimental Design

### Optimized Scenario Specifications
1. **Micro Scale**: 10 VMs, 3 Hosts (Quick validation)
2. **Small Scale**: 50 VMs, 10 Hosts
3. **Medium Scale**: 100 VMs, 20 Hosts
4. **Large Scale**: 200 VMs, 40 Hosts
5. **X-Large Scale**: 500 VMs, 100 Hosts

### Reduced Algorithm Set
1. **HO**: Hippopotamus Optimization (main algorithm)
2. **FirstFit**: Classic first-fit heuristic
3. **BestFit**: Classic best-fit heuristic
4. **GA**: Genetic Algorithm (metaheuristic comparison)

### Experiment Statistics
- **Total Experiments**: 600 (4 algorithms × 5 scenarios × 30 replications)
- **Estimated Runtime**: 5-10 hours on 16GB system
- **Memory Usage**: Peak ~5-6GB

## Build and Execution Commands
```bash
# Build project
mvn clean compile

# Run complete experimental suite with optimized memory
mvn exec:java -Dexec.mainClass="org.puneet.cloudsimplus.hiippo.App" -Dexec.args="-Xmx6G -XX:+UseG1GC"

# Run specific test suites
mvn test -Dtest="*StatisticalValidationTest" -DargLine="-Xmx3G"

# Run with memory monitoring
mvn exec:java -Dexec.mainClass="org.puneet.cloudsimplus.hiippo.App" -Dexec.args="-Xmx6G -XX:+PrintGCDetails"

# Generate test reports
mvn surefire-report:report
```

## Performance Tips for 16GB Systems

1. **Close unnecessary applications** before running experiments
2. **Monitor system resources** using Task Manager or System Monitor
3. **Run experiments overnight** when system is idle
4. **Use SSD** for faster swap file access if needed
5. **Consider running scenarios separately** if memory issues occur:
   ```bash
   # Run only small scenarios
   mvn exec:java -Dexec.args="-Dscenarios=Micro,Small,Medium"
   ```
# Generation Order and Workflow
Stage 1: Project Foundation (Generate First)
1. pom.xml
   └── Complete Maven configuration with all dependencies
   
2. src/main/resources/
   ├── config.properties
   ├── algorithm_parameters.properties
   └── logback.xml
Stage 2: Core Utilities and Exceptions (No Dependencies)
3. exceptions/ (Package: org.puneet.cloudsimplus.hiippo.exceptions)
   ├── HippopotamusOptimizationException.java
   ├── ValidationException.java
   ├── StatisticalValidationException.java
   └── ScalabilityTestException.java

4. util/ (Package: org.puneet.cloudsimplus.hiippo.util)
   ├── ExperimentConfig.java
   ├── MemoryManager.java
   ├── BatchProcessor.java
   └── ProgressTracker.java
Stage 3: Algorithm Core (Depends on Stage 2)
5. algorithm/ (Package: org.puneet.cloudsimplus.hiippo.algorithm)
   ├── AlgorithmConstants.java
   ├── Hippopotamus.java (entity class)
   ├── HippopotamusParameters.java
   ├── HippopotamusOptimization.java
   └── ConvergenceAnalyzer.java
Stage 4: CloudSim Integration Base (Depends on Stage 2)
6. policy/ (Package: org.puneet.cloudsimplus.hiippo.policy)
   ├── BaselineVmAllocationPolicy.java (abstract)
   └── AllocationValidator.java

7. baseline/ (Package: org.puneet.cloudsimplus.hiippo.baseline)
   ├── FirstFitAllocation.java
   ├── BestFitAllocation.java
   └── GeneticAlgorithmAllocation.java
Stage 5: HO CloudSim Integration (Depends on Stages 3 & 4)
8. policy/
   └── HippopotamusVmAllocationPolicy.java
Stage 6: Simulation Components (Depends on Stages 2-5)
9. simulation/ (Package: org.puneet.cloudsimplus.hiippo.simulation)
   ├── DatacenterFactory.java
   ├── HODatacenterBroker.java
   ├── TestScenarios.java
   ├── ScenarioGenerator.java
   └── CloudSimHOSimulation.java
Stage 7: Metrics and Results (Depends on Stage 2)
10. util/
    ├── MetricsCollector.java
    ├── ValidationUtils.java
    ├── ResultValidator.java
    ├── PerformanceMonitor.java
    └── CSVResultsWriter.java
Stage 8: Statistical Analysis (Depends on Stage 7)
11. statistical/ (Package: org.puneet.cloudsimplus.hiippo.statistical)
    ├── ConfidenceInterval.java
    ├── ANOVAResult.java
    ├── StatisticalValidator.java
    └── ComparisonAnalyzer.java
Stage 9: Experiment Execution (Depends on All Previous)
12. simulation/
    ├── ExperimentRunner.java
    ├── ExperimentCoordinator.java
    ├── ParameterTuner.java
    └── ScalabilityTester.java
Stage 10: Main Application (Depends on Stage 9)
13. App.java (Package: org.puneet.cloudsimplus.hiippo)
Stage 11: Test Suite (After Main Implementation)
14. test/java/org/puneet/cloudsimplus/hiippo/
    ├── unit/*Test.java
    ├── integration/*Test.java
    ├── performance/*Test.java
    └── statistical/*Test.java
📊 File Dependency Graph
graph TD
    A[pom.xml] --> B[Resources]
    B --> C[Exceptions]
    C --> D[Core Utils]
    D --> E[Algorithm Core]
    D --> F[CloudSim Base]
    E --> G[HO Policy]
    F --> G
    G --> H[Simulation]
    D --> I[Metrics]
    I --> J[Statistical]
    H --> K[Experiment]
    J --> K
    K --> L[App.java]

🔗 Critical File Connections
1. Memory Management Chain
MemoryManager.java
    ↓ used by
BatchProcessor.java
    ↓ used by
ExperimentCoordinator.java
    ↓ controls
ScalabilityTester.java
2. Algorithm Execution Chain
AlgorithmConstants.java
    ↓ configures
HippopotamusOptimization.java
    ↓ used by
HippopotamusVmAllocationPolicy.java
    ↓ integrated in
CloudSimHOSimulation.java
3. Data Flow Chain
MetricsCollector.java
    ↓ collects
ExperimentRunner.java
    ↓ validates via
ResultValidator.java
    ↓ writes using
CSVResultsWriter.java

# 🚀 Execution Flow Diagram
App.main()
    ├── Check system requirements
    ├── Initialize ExperimentConfig
    ├── Create ExperimentCoordinator
    │   ├── For each algorithm
    │   │   ├── For each scenario
    │   │   │   ├── Check memory availability
    │   │   │   ├── For each replication (in batches)
    │   │   │   │   ├── Initialize random seed
    │   │   │   │   ├── Create TestScenario
    │   │   │   │   ├── Run ExperimentRunner
    │   │   │   │   │   ├── Create CloudSim simulation
    │   │   │   │   │   ├── Create Datacenter
    │   │   │   │   │   ├── Create Broker
    │   │   │   │   │   ├── Create VMs and Hosts
    │   │   │   │   │   ├── Apply allocation policy
    │   │   │   │   │   ├── Run simulation
    │   │   │   │   │   └── Collect metrics
    │   │   │   │   ├── Validate results
    │   │   │   │   └── Save to CSV
    │   │   │   └── Clean up between batches
    │   │   └── Major cleanup between scenarios
    │   └── Statistical analysis
    └── Generate final reports

## Critical Success Criteria 
1. **Memory efficiency**: Stay within 6GB heap limit
2. **Batch processing**: Process large scenarios in manageable chunks
3. **Graceful degradation**: Skip scenarios if memory insufficient
4. **Progress tracking**: Real-time updates with memory status
5. **Result validation**: Ensure all results are valid before saving
6. **Reproducibility**: Fixed seeds produce identical results
7. **Statistical validity**: 30 replications with proper analysis




