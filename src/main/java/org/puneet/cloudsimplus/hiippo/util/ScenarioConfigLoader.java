package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads scenario specifications from config.properties file.
 * Provides centralized configuration management for experiment scenarios.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-08-05
 */
public class ScenarioConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ScenarioConfigLoader.class);
    
    private static final String CONFIG_FILE = "config.properties";
    private static final String SCENARIOS_ENABLED_KEY = "scenarios.enabled";
    private static final String SCENARIO_VMS_SUFFIX = ".vms";
    private static final String SCENARIO_HOSTS_SUFFIX = ".hosts";
    private static final String SCENARIO_COMPLEXITY_SUFFIX = ".complexity";
    
    private final Properties config;
    
    /**
     * Creates a new ScenarioConfigLoader and loads the configuration.
     * 
     * @throws RuntimeException if configuration cannot be loaded
     */
    public ScenarioConfigLoader() {
        this.config = loadConfiguration();
    }
    
    /**
     * Loads configuration from config.properties file.
     * 
     * @return Properties object containing configuration
     * @throws RuntimeException if configuration cannot be loaded
     */
    private Properties loadConfiguration() {
        Properties props = new Properties();
        
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new RuntimeException("Configuration file " + CONFIG_FILE + " not found in classpath");
            }
            
            props.load(inputStream);
            logger.info("Successfully loaded configuration from {}", CONFIG_FILE);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration from " + CONFIG_FILE, e);
        }
        
        return props;
    }
    
    /**
     * Gets the list of enabled scenarios from configuration.
     * 
     * @return List of enabled scenario names
     */
    public List<String> getEnabledScenarios() {
        String enabledScenarios = config.getProperty(SCENARIOS_ENABLED_KEY, "");
        
        if (enabledScenarios.trim().isEmpty()) {
            logger.warn("No scenarios enabled in configuration");
            return new ArrayList<>();
        }
        
        List<String> scenarios = Arrays.stream(enabledScenarios.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
        
        logger.debug("Loaded enabled scenarios: {}", scenarios);
        return scenarios;
    }
    
    /**
     * Gets scenario specifications for all enabled scenarios.
     * 
     * @return Map of scenario name to ScenarioSpec
     */
    public Map<String, ScenarioSpec> getScenarioSpecifications() {
        Map<String, ScenarioSpec> specs = new HashMap<>();
        List<String> enabledScenarios = getEnabledScenarios();
        
        for (String scenario : enabledScenarios) {
            try {
                ScenarioSpec spec = getScenarioSpec(scenario);
                specs.put(scenario, spec);
                logger.debug("Loaded scenario specification for {}: {}", scenario, spec);
            } catch (Exception e) {
                logger.error("Failed to load scenario specification for {}: {}", scenario, e.getMessage());
            }
        }
        
        return specs;
    }
    
    /**
     * Gets scenario specification for a specific scenario.
     * 
     * @param scenarioName the scenario name
     * @return ScenarioSpec for the scenario
     * @throws IllegalArgumentException if scenario configuration is invalid
     */
    public ScenarioSpec getScenarioSpec(String scenarioName) {
        String vmKey = "scenario." + scenarioName.toLowerCase() + SCENARIO_VMS_SUFFIX;
        String hostKey = "scenario." + scenarioName.toLowerCase() + SCENARIO_HOSTS_SUFFIX;
        String complexityKey = "scenario." + scenarioName.toLowerCase() + SCENARIO_COMPLEXITY_SUFFIX;
        
        String vmCountStr = config.getProperty(vmKey);
        String hostCountStr = config.getProperty(hostKey);
        String complexityStr = config.getProperty(complexityKey);
        
        if (vmCountStr == null || hostCountStr == null || complexityStr == null) {
            throw new IllegalArgumentException(
                String.format("Missing configuration for scenario %s. Required keys: %s, %s, %s",
                    scenarioName, vmKey, hostKey, complexityKey));
        }
        
        try {
            int vmCount = Integer.parseInt(vmCountStr);
            int hostCount = Integer.parseInt(hostCountStr);
            int complexity = Integer.parseInt(complexityStr);
            
            if (vmCount <= 0 || hostCount <= 0 || complexity <= 0) {
                throw new IllegalArgumentException(
                    String.format("Invalid values for scenario %s: VMs=%d, Hosts=%d, Complexity=%d",
                        scenarioName, vmCount, hostCount, complexity));
            }
            
            return new ScenarioSpec(scenarioName, vmCount, hostCount, complexity);
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("Invalid numeric values for scenario %s: VMs=%s, Hosts=%s, Complexity=%s",
                    scenarioName, vmCountStr, hostCountStr, complexityStr), e);
        }
    }
    
    /**
     * Validates that all enabled scenarios have valid configurations.
     * 
     * @throws RuntimeException if any scenario configuration is invalid
     */
    public void validateConfiguration() {
        List<String> enabledScenarios = getEnabledScenarios();
        
        if (enabledScenarios.isEmpty()) {
            throw new RuntimeException("No scenarios enabled in configuration");
        }
        
        for (String scenario : enabledScenarios) {
            try {
                getScenarioSpec(scenario);
            } catch (Exception e) {
                throw new RuntimeException("Invalid configuration for scenario " + scenario, e);
            }
        }
        
        logger.info("Configuration validation passed for {} scenarios", enabledScenarios.size());
    }
    
    /**
     * Gets a property value from the configuration.
     * 
     * @param key the property key
     * @param defaultValue the default value if key not found
     * @return the property value
     */
    public String getProperty(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }
    
    /**
     * Gets a property value from the configuration.
     * 
     * @param key the property key
     * @return the property value or null if not found
     */
    public String getProperty(String key) {
        return config.getProperty(key);
    }
    
    /**
     * Scenario specification data class.
     */
    public static class ScenarioSpec {
        public final String name;
        public final int vmCount;
        public final int hostCount;
        public final int complexityLevel;
        
        public ScenarioSpec(String name, int vmCount, int hostCount, int complexityLevel) {
            this.name = name;
            this.vmCount = vmCount;
            this.hostCount = hostCount;
            this.complexityLevel = complexityLevel;
        }
        
        @Override
        public String toString() {
            return String.format("ScenarioSpec{name='%s', vms=%d, hosts=%d, complexity=%d}",
                name, vmCount, hostCount, complexityLevel);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ScenarioSpec that = (ScenarioSpec) obj;
            return vmCount == that.vmCount && 
                   hostCount == that.hostCount && 
                   complexityLevel == that.complexityLevel && 
                   Objects.equals(name, that.name);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(name, vmCount, hostCount, complexityLevel);
        }
    }
} 