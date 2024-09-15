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
import org.evosuite.gpt.GPTRequest;
import org.evosuite.runtime.instrumentation.RuntimeInstrumentation;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.junit.Assert;
import org.junit.Test;

public class DynaMOSAGASystemTest extends SystemTestBase {

    public List<Chromosome<?>> setup(StoppingCondition sc, int budget, String cut, Boolean useML) {
        Properties.getInstance().resetToDefaults();
        Properties.CLIENT_ON_THREAD = true;



        Properties.SELECTION_FUNCTION = Properties.SelectionFunction.RANK_CROWD_DISTANCE_TOURNAMENT;
//        Properties.CRITERION = new Criterion[]{ Criterion.LINE, Criterion.BRANCH, Criterion.EXCEPTION,
//                Criterion.WEAKMUTATION, Criterion.OUTPUT, Criterion.METHOD, Criterion.METHODNOEXCEPTION,
//                Criterion.CBRANCH};

        Properties.GLOBAL_TIMEOUT = 3600;
        Properties.MINIMIZATION_TIMEOUT = 3600;
        Properties.EXTRA_TIMEOUT = 3600;
//        Properties.JUNIT_TESTS = true;

        //
        Properties.IS_RUNNING_A_SYSTEM_TEST = true;
        Properties.NEW_STATISTICS = true;
        Properties.STATISTICS_BACKEND = Properties.StatisticsBackend.DEBUG;
        Properties.JUNIT_TESTS = false;

        if (!useML) {
            Properties.ALGORITHM = Properties.Algorithm.DYNAMOSA;
        }

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
//        StringBuilder s = new StringBuilder();
//        s.append(RuntimeVariable.Coverage);
//        s.append(",");
//        s.append(RuntimeVariable.BranchCoverage);
//        s.append(",");
//        s.append(RuntimeVariable.LineCoverage);
//        s.append(",");
//        s.append(RuntimeVariable.ExceptionCoverage);
//        s.append(",");
//        s.append(RuntimeVariable.WeakMutationScore);
//        s.append(",");
//        s.append(RuntimeVariable.OutputCoverage);
//        s.append(",");
//        s.append(RuntimeVariable.MethodCoverage);
//        s.append(",");
//        s.append(RuntimeVariable.MethodNoExceptionCoverage);
//        s.append(",");
//        s.append(RuntimeVariable.CBranchCoverage);
//        s.append(",");
//        s.append(RuntimeVariable.Covered_Goals);
//        s.append(",");
//        s.append(RuntimeVariable.Total_Goals);
//        s.append(",");
//        Properties.OUTPUT_VARIABLES = s.toString();
        // display stats?

        Object result = evosuite.parseCommandLine(command);
        Assert.assertNotNull(result);

        GeneticAlgorithm<TestSuiteChromosome> ga = getGAFromResult(result);

        TestSuiteChromosome best = ga.getBestIndividual();

//        System.out.println("\n\n########## GENERATED TESTS: ##########");
        //System.out.println(ga.getBestIndividuals());
//        System.out.println(best.toString());
//        System.out.println("######################################\n\n");

        return new ArrayList<>(ga.getBestIndividuals());
    }

    @Test
    public void testMOSALisa() {
        //Console.SystemOutPritnln("CANOICAL Name: " + stack.class.);
        List<Chromosome<?>> population = this.setup(null, 0, "mosalisa.RecursiveParser", true);

        Assert.assertNotEquals(population.size(), 0);
    }

    @Test
    public void testDefault() {
        List<Chromosome<?>> population = this.setup(null, 0, "mosalisa.RecursiveParser", false);

        Assert.assertNotEquals(population.size(), 0);
    }

    @Test
    public void testGPT() {
        Properties.GPT_KEY = "";

        StringBuilder sb = new StringBuilder();
        sb.append("Who is the president of the United States?");
        sb.append("I have a test case, and I need to identify which lines could be deleted to potentially improve it. Please return an array of line numbers that can be deleted, based on the following:\n" +
                "- The last line number I provide is the cutoff, and no statements beyond this line should be considered for deletion.\n" +
                "- The array should be in the form [x0, x1, ..., xn], where each xi is a line number.\n" +
                "- If no lines should be deleted, return an empty array: [].\n" +
                "No further explanation is required, just the array of line numbers.\n" +
                "Last allowed line number for deletion consideration is : 2\n");
        sb.append("```\n");
        sb.append("RecursiveParser recursiveParser0 = new RecursiveParser();\n");
        sb.append("String string0 = \"\";\n");
        sb.append("recursiveParser0.parse(string0);\n");
        sb.append("```\n");

        String initialGPTResponse = GPTRequest.chatGPT(sb.toString());
        Assert.assertNotNull(initialGPTResponse);
        System.out.println("UNFORMATTED RESPONSE:");
        System.out.println(initialGPTResponse);
        System.out.println("CODE ONLY:");
        System.out.println(GPTRequest.get_code_only(initialGPTResponse));
    }

}
