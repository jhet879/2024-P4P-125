package org.evosuite.ga.metaheuristics.mosa;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import org.evosuite.EvoSuite;
import org.evosuite.Properties;
import org.evosuite.SystemTestBase;
import org.evosuite.Properties.Criterion;
import org.evosuite.Properties.StoppingCondition;
import org.evosuite.TestGenerationContext;
import org.evosuite.classpath.ResourceList;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.junit.Assert;
import org.junit.Test;

public class DynaMOSAGASystemTest extends SystemTestBase {

    public List<Chromosome<?>> setup(StoppingCondition sc, int budget, String cut) {
        Properties.SELECTION_FUNCTION = Properties.SelectionFunction.RANK_CROWD_DISTANCE_TOURNAMENT;
        Properties.CRITERION = new Criterion[]{ Criterion.LINE, Criterion.BRANCH, Criterion.EXCEPTION,
                Criterion.WEAKMUTATION, Criterion.OUTPUT, Criterion.METHOD, Criterion.METHODNOEXCEPTION,
                Criterion.CBRANCH};

        Properties.GLOBAL_TIMEOUT = 3600;
        Properties.MINIMIZATION_TIMEOUT = 3600;
        Properties.EXTRA_TIMEOUT = 3600;
        //Properties.JUNIT_TESTS = true;

        System.out.println("\n\n########## EVOSUITE PROPERTIES: ##########");
        System.out.println("ALGORITHM: " + Properties.ALGORITHM);
        System.out.println("STRATEGY: " + Properties.STRATEGY);
        System.out.println("CRITERION: " + Properties.CRITERION.length + ":");
        for (int i = 0; i < Properties.CRITERION.length; i++) {
            System.out.println("- " + Properties.CRITERION[i]);
        }
        System.out.println("SELECTION FUNCTION: " + Properties.SELECTION_FUNCTION);
        System.out.println("TARGET CLASS: " + cut);
        System.out.println("##############################################\n\n");

        EvoSuite evosuite = new EvoSuite();
        String GPTKey = "";
        String[] command = new String[]{"-generateMOSuite", "-class", cut, "-projectCP", "../examplCodez/target/classes", "-gpt_key=" + GPTKey};

        // display stats?
        StringBuilder s = new StringBuilder();
        s.append(RuntimeVariable.Coverage);
        s.append(",");
        s.append(RuntimeVariable.BranchCoverage);
        s.append(",");
        s.append(RuntimeVariable.LineCoverage);
        s.append(",");
        s.append(RuntimeVariable.ExceptionCoverage);
        s.append(",");
        s.append(RuntimeVariable.WeakMutationScore);
        s.append(",");
        s.append(RuntimeVariable.OutputCoverage);
        s.append(",");
        s.append(RuntimeVariable.MethodCoverage);
        s.append(",");
        s.append(RuntimeVariable.MethodNoExceptionCoverage);
        s.append(",");
        s.append(RuntimeVariable.CBranchCoverage);
        s.append(",");
        s.append(RuntimeVariable.Covered_Goals);
        s.append(",");
        s.append(RuntimeVariable.Total_Goals);
        s.append(",");
        Properties.OUTPUT_VARIABLES = s.toString();
        // display stats?

        Object result = evosuite.parseCommandLine(command);
        Assert.assertNotNull(result);

        GeneticAlgorithm<?> ga = getGAFromResult(result);

//        System.out.println(Properties.GPT_KEY);
//        System.out.println("\n\n########## GENERATED TESTS: ##########");
//        System.out.println(ga.getBestIndividuals());
//        System.out.println("######################################\n\n");

        System.out.println("RANKING FUNCTION FROM GA: " + ga.getRankingFunction());

        ga.getPopulation();

        ga.getBestIndividual().getCoverage();

        return new ArrayList<>(ga.getBestIndividuals());
    }

    @Test
    public void testMOSALisa() {
        //Console.SystemOutPritnln("CANOICAL Name: " + stack.class.);
        List<Chromosome<?>> population = this.setup(null, 0, "mosalisa.ComplexAPIUsage");

        Assert.assertNotEquals(population.size(), 0);
    }

}
