
package org.puneet.cloudsimplus.hiippo.unit;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.algorithm.HippopotamusOptimization;
import org.puneet.cloudsimplus.hiippo.algorithm.HippopotamusParameters;
import org.puneet.cloudsimplus.hiippo.policy.HippopotamusVmAllocationPolicy;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HippopotamusOptimizationTest {

    private HippopotamusOptimization hippopotamusOptimization;
    private HippopotamusParameters parameters;
    private List<Vm> vmList;
    private List<Host> hostList;

    @BeforeEach
    void setUp() {
        parameters = new HippopotamusParameters();
        parameters.setPopulationSize(10);
        parameters.setMaxIterations(20);
        hippopotamusOptimization = new HippopotamusOptimization(parameters);

        vmList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            vmList.add(new VmSimple(1000, 1));
        }

        hostList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            // Create hosts with proper PE list instead of empty ArrayList
            List<org.cloudsimplus.resources.Pe> peList = new ArrayList<>();
            peList.add(new org.cloudsimplus.resources.PeSimple(1000));
            peList.add(new org.cloudsimplus.resources.PeSimple(1000));
            hostList.add(new HostSimple(10000, 8192, 100000, peList));
        }
    }

    @Test
    void testHippopotamusOptimizationNotNull() {
        assertNotNull(hippopotamusOptimization);
    }

    @Test
    void testOptimize() {
        HippopotamusVmAllocationPolicy.Solution solution = hippopotamusOptimization.optimize(vmList, hostList);
        assertNotNull(solution);
        assertEquals(vmList.size(), solution.getAllocations().size());
    }

    @Test
    void testOptimizeWithEmptyVmList() {
        assertThrows(IllegalArgumentException.class, () -> {
            hippopotamusOptimization.optimize(new ArrayList<>(), hostList);
        });
    }

    @Test
    void testOptimizeWithEmptyHostList() {
        assertThrows(IllegalArgumentException.class, () -> {
            hippopotamusOptimization.optimize(vmList, new ArrayList<>());
        });
    }

    @Test
    void testEvaluateFitness() {
        HippopotamusVmAllocationPolicy.Solution solution = new HippopotamusVmAllocationPolicy.Solution();
        solution.addMapping(vmList.get(0), hostList.get(0));
        double fitness = hippopotamusOptimization.evaluateFitness(solution);
        assertTrue(fitness >= 0);
    }

    @Test
    void testConvergenceHistory() {
        hippopotamusOptimization.optimize(vmList, hostList);
        List<Double> convergenceHistory = hippopotamusOptimization.getConvergenceHistory();
        assertNotNull(convergenceHistory);
        assertFalse(convergenceHistory.isEmpty());
    }
}