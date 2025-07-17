package org.puneet.cloudsimplus.hiippo.algorithm;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hippopotamus.java
 * 
 * Entity class representing a single hippopotamus (solution candidate) in the
 * Hippopotamus Optimization algorithm. Each instance encapsulates a complete
 * VM-to-host placement solution along with its fitness value and position
 * information for the optimization process.
 * 
 * <p>This class is designed to be memory-efficient for large-scale simulations
 * while providing all necessary functionality for the HO algorithm operations.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class Hippopotamus implements Cloneable, Comparable<Hippopotamus> {
    
    private static final Logger logger = LoggerFactory.getLogger(Hippopotamus.class);
    
    // ===================================================================================
    // INSTANCE VARIABLES
    // ===================================================================================
    
    /**
     * Unique identifier for this hippopotamus instance.
     * Used for tracking and debugging purposes.
     */
    private final int id;
    
    /**
     * Current position vector representing VM-to-host assignments.
     * Each element at index i represents the host ID assigned to VM i.
     * Values are constrained to valid host indices [0, numHosts-1].
     */
    private int[] position;
    
    /**
     * Current fitness value calculated by the multi-objective fitness function.
     * Lower values indicate better solutions (minimization problem).
     */
    private double fitness = Double.MAX_VALUE;
    
    /**
     * Best position found by this hippopotamus during its search.
     * Used for personal best tracking in the optimization process.
     */
    private int[] bestPosition;
    
    /**
     * Best fitness value achieved by this hippopotamus.
     * Corresponds to the bestPosition.
     */
    private double bestFitness = Double.MAX_VALUE;
    
    /**
     * Velocity vector for position updates.
     * Used in the velocity-based position update mechanism.
     */
    private double[] velocity;
    
    /**
     * Number of VMs in the problem instance.
     * Cached for performance optimization.
     */
    private final int vmCount;
    
    /**
     * Number of hosts in the problem instance.
     * Cached for performance optimization.
     */
    private final int hostCount;
    
    /**
     * Generation counter for tracking algorithm progress.
     * Incremented each time the position is updated.
     */
    private int generation = 0;
    
    /**
     * Flag indicating if this hippopotamus has been evaluated.
     * Prevents redundant fitness calculations.
     */
    private boolean evaluated = false;
    
    // ===================================================================================
    // CONSTRUCTORS
    // ===================================================================================
    
    /**
     * Creates a new hippopotamus with the specified dimensions.
     * 
     * @param id unique identifier for this hippopotamus
     * @param vmCount number of VMs in the problem
     * @param hostCount number of hosts in the problem
     * @throws IllegalArgumentException if vmCount or hostCount is non-positive
     */
    public Hippopotamus(int id, int vmCount, int hostCount) {
        validateDimensions(vmCount, hostCount);
        
        this.id = id;
        this.vmCount = vmCount;
        this.hostCount = hostCount;
        
        // Initialize arrays with optimized sizes
        this.position = new int[vmCount];
        this.bestPosition = new int[vmCount];
        this.velocity = new double[vmCount];
        
        // Initialize to random valid positions
        initializeRandomPosition();
        
        logger.debug("Created Hippopotamus {} with {} VMs and {} hosts", 
            id, vmCount, hostCount);
    }
    
    /**
     * Copy constructor for creating deep copies.
     * 
     * @param source the hippopotamus to copy from
     */
    public Hippopotamus(Hippopotamus source) {
        this.id = source.id;
        this.vmCount = source.vmCount;
        this.hostCount = source.hostCount;
        this.fitness = source.fitness;
        this.bestFitness = source.bestFitness;
        this.generation = source.generation;
        this.evaluated = source.evaluated;
        
        // Deep copy arrays
        this.position = source.position.clone();
        this.bestPosition = source.bestPosition.clone();
        this.velocity = source.velocity.clone();
    }
    
    // ===================================================================================
    // INITIALIZATION METHODS
    // ===================================================================================
    
    /**
     * Initializes the position vector with random valid host assignments.
     * Each VM is assigned to a random host index within valid bounds.
     */
    private void initializeRandomPosition() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (int i = 0; i < vmCount; i++) {
            position[i] = random.nextInt(hostCount);
            bestPosition[i] = position[i];
            velocity[i] = 0.0;
        }
        
        logger.trace("Initialized random position for Hippopotamus {}", id);
    }
    
    /**
     * Initializes the position vector with a specific assignment pattern.
     * Useful for creating seed solutions or testing specific configurations.
     * 
     * @param initialPosition array of host assignments for each VM
     * @throws IllegalArgumentException if position array length doesn't match vmCount
     * @throws IllegalArgumentException if any host index is out of bounds
     */
    public void initializePosition(int[] initialPosition) {
        validatePosition(initialPosition);
        
        System.arraycopy(initialPosition, 0, position, 0, vmCount);
        System.arraycopy(initialPosition, 0, bestPosition, 0, vmCount);
        
        // Reset fitness to force re-evaluation
        this.fitness = Double.MAX_VALUE;
        this.bestFitness = Double.MAX_VALUE;
        this.evaluated = false;
        
        logger.trace("Initialized specific position for Hippopotamus {}", id);
    }
    
    // ===================================================================================
    // POSITION UPDATE METHODS
    // ===================================================================================
    
    /**
     * Updates the position based on the HO algorithm's position update equation.
     * 
     * <p>The update equation is:</p>
     * <pre>
     * newPosition = currentPosition + α * (leaderPosition - currentPosition) +
     *               β * rand() * (preyPosition - currentPosition) +
     *               γ * levy() * currentPosition
     * </pre>
     * 
     * @param leader the leading hippopotamus (best solution in population)
     * @param prey a randomly selected prey hippopotamus
     * @param alpha leader influence parameter
     * @param beta prey influence parameter
     * @param gamma random walk parameter
     * @throws IllegalArgumentException if any parameter is null
     */
    public void updatePosition(Hippopotamus leader, Hippopotamus prey, 
                              double alpha, double beta, double gamma) {
        validateHippopotamus(leader, "leader");
        validateHippopotamus(prey, "prey");
        
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (int i = 0; i < vmCount; i++) {
            // Calculate velocity components
            double leaderInfluence = alpha * (leader.position[i] - position[i]);
            double preyInfluence = beta * random.nextDouble() * (prey.position[i] - position[i]);
            double randomWalk = gamma * generateLevyFlight() * position[i];
            
            // Update velocity
            velocity[i] = leaderInfluence + preyInfluence + randomWalk;
            
            // Update position with boundary handling
            double newPosition = position[i] + velocity[i];
            position[i] = clampToBounds((int) Math.round(newPosition));
        }
        
        generation++;
        evaluated = false; // Force re-evaluation
        
        logger.trace("Updated position for Hippopotamus {} (generation {})", id, generation);
    }
    
    /**
     * Generates a Levy flight random number for the random walk component.
     * Uses the Mantegna algorithm for efficient Levy flight generation.
     * 
     * @return Levy flight random number
     */
    private double generateLevyFlight() {
        double sigma = Math.pow(
            (Math.gamma(1 + AlgorithmConstants.LEVY_LAMBDA) * 
             Math.sin(Math.PI * AlgorithmConstants.LEVY_LAMBDA / 2)) /
            (Math.gamma((1 + AlgorithmConstants.LEVY_LAMBDA) / 2) * 
             AlgorithmConstants.LEVY_LAMBDA * Math.pow(2, (AlgorithmConstants.LEVY_LAMBDA - 1) / 2)),
            1 / AlgorithmConstants.LEVY_LAMBDA);
        
        double u = ThreadLocalRandom.current().nextGaussian() * sigma;
        double v = ThreadLocalRandom.current().nextGaussian();
        
        return u / Math.pow(Math.abs(v), 1 / AlgorithmConstants.LEVY_LAMBDA);
    }
    
    /**
     * Clamps a position value to valid host indices.
     * 
     * @param value the position value to clamp
     * @return valid host index in range [0, hostCount-1]
     */
    private int clampToBounds(int value) {
        return Math.max(0, Math.min(hostCount - 1, value));
    }
    
    // ===================================================================================
    // FITNESS MANAGEMENT
    // ===================================================================================
    
    /**
     * Updates the fitness value and maintains the best position record.
     * 
     * @param newFitness the calculated fitness value
     * @throws IllegalArgumentException if fitness is NaN or infinite
     */
    public void updateFitness(double newFitness) {
        if (Double.isNaN(newFitness) || Double.isInfinite(newFitness)) {
            throw new IllegalArgumentException("Fitness must be a finite number");
        }
        
        this.fitness = newFitness;
        this.evaluated = true;
        
        // Update best position if this is the best fitness so far
        if (newFitness < bestFitness) {
            bestFitness = newFitness;
            System.arraycopy(position, 0, bestPosition, 0, vmCount);
            
            logger.trace("New best fitness for Hippopotamus {}: {}", id, bestFitness);
        }
    }
    
    /**
     * Checks if this hippopotamus has been evaluated.
     * 
     * @return true if fitness has been calculated, false otherwise
     */
    public boolean isEvaluated() {
        return evaluated;
    }
    
    // ===================================================================================
    // UTILITY METHODS
    // ===================================================================================
    
    /**
     * Creates a deep copy of this hippopotamus.
     * 
     * @return a new Hippopotamus instance with identical state
     */
    @Override
    public Hippopotamus clone() {
        try {
            Hippopotamus cloned = (Hippopotamus) super.clone();
            cloned.position = this.position.clone();
            cloned.bestPosition = this.bestPosition.clone();
            cloned.velocity = this.velocity.clone();
            return cloned;
        } catch (CloneNotSupportedException e) {
            // Should never happen as we implement Cloneable
            throw new AssertionError("Clone not supported", e);
        }
    }
    
    /**
     * Compares this hippopotamus with another based on fitness.
     * Used for sorting populations by fitness.
     * 
     * @param other the other hippopotamus to compare with
     * @return negative if this is better (lower fitness), positive if worse
     */
    @Override
    public int compareTo(Hippopotamus other) {
        return Double.compare(this.fitness, other.fitness);
    }
    
    /**
     * Checks equality based on ID and current position.
     * 
     * @param obj the object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Hippopotamus that = (Hippopotamus) obj;
        return id == that.id && 
               Arrays.equals(position, that.position);
    }
    
    /**
     * Generates hash code based on ID and position.
     * 
     * @return hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, Arrays.hashCode(position));
    }
    
    /**
     * Returns a string representation of this hippopotamus.
     * 
     * @return string containing ID, fitness, and position summary
     */
    @Override
    public String toString() {
        return String.format("Hippopotamus{id=%d, fitness=%.6f, generation=%d, position=%s}",
            id, fitness, generation, Arrays.toString(position));
    }
    
    // ===================================================================================
    // VALIDATION METHODS
    // ===================================================================================
    
    /**
     * Validates VM and host counts.
     * 
     * @param vmCount number of VMs
     * @param hostCount number of hosts
     * @throws IllegalArgumentException if counts are invalid
     */
    private void validateDimensions(int vmCount, int hostCount) {
        if (vmCount <= 0) {
            throw new IllegalArgumentException("VM count must be positive");
        }
        if (hostCount <= 0) {
            throw new IllegalArgumentException("Host count must be positive");
        }
    }
    
    /**
     * Validates a position array.
     * 
     * @param position the position array to validate
     * @throws IllegalArgumentException if position is invalid
     */
    private void validatePosition(int[] position) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }
        if (position.length != vmCount) {
            throw new IllegalArgumentException(
                String.format("Position length must be %d, but was %d", vmCount, position.length));
        }
        for (int hostId : position) {
            if (hostId < 0 || hostId >= hostCount) {
                throw new IllegalArgumentException(
                    String.format("Host ID must be in range [0, %d], but was %d", hostCount - 1, hostId));
            }
        }
    }
    
    /**
     * Validates another hippopotamus instance.
     * 
     * @param hippo the hippopotamus to validate
     * @param name the parameter name for error messages
     * @throws IllegalArgumentException if hippo is null or incompatible
     */
    private void validateHippopotamus(Hippopotamus hippo, String name) {
        if (hippo == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
        if (hippo.vmCount != this.vmCount || hippo.hostCount != this.hostCount) {
            throw new IllegalArgumentException(
                String.format("%s has incompatible dimensions: expected [%d, %d], got [%d, %d]",
                    name, vmCount, hostCount, hippo.vmCount, hippo.hostCount));
        }
    }
    
    // ===================================================================================
    // GETTERS AND SETTERS
    // ===================================================================================
    
    /**
     * Gets the unique identifier for this hippopotamus.
     * 
     * @return the ID
     */
    public int getId() {
        return id;
    }
    
    /**
     * Gets the current position vector.
     * 
     * @return defensive copy of the position array
     */
    public int[] getPosition() {
        return position.clone();
    }
    
    /**
     * Gets the current fitness value.
     * 
     * @return the fitness value
     */
    public double getFitness() {
        return fitness;
    }
    
    /**
     * Gets the best position found by this hippopotamus.
     * 
     * @return defensive copy of the best position array
     */
    public int[] getBestPosition() {
        return bestPosition.clone();
    }
    
    /**
     * Gets the best fitness achieved by this hippopotamus.
     * 
     * @return the best fitness value
     */
    public double getBestFitness() {
        return bestFitness;
    }
    
    /**
     * Gets the current velocity vector.
     * 
     * @return defensive copy of the velocity array
     */
    public double[] getVelocity() {
        return velocity.clone();
    }
    
    /**
     * Gets the number of VMs.
     * 
     * @return the VM count
     */
    public int getVmCount() {
        return vmCount;
    }
    
    /**
     * Gets the number of hosts.
     * 
     * @return the host count
     */
    public int getHostCount() {
        return hostCount;
    }
    
    /**
     * Gets the current generation number.
     * 
     * @return the generation count
     */
    public int getGeneration() {
        return generation;
    }
}