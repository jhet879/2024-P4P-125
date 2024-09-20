package org.evosuite.ga.metaheuristics.mosa;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import io.github.cdimascio.dotenv.Dotenv;
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
        Properties.WRITE_JUNIT_TIMEOUT = 3600;

        Properties.IS_RUNNING_A_SYSTEM_TEST = true;
        Properties.NEW_STATISTICS = true;
        Properties.STATISTICS_BACKEND = Properties.StatisticsBackend.DEBUG;
        // SET TO TRUE TO ENABLE SAVING OUTPUT TO FILE
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
        Dotenv dotenv = Dotenv.load();
        String GPTKey = dotenv.get("GPT_TOKEN");
//        String[] command = new String[]{"-generateMOSuite", "-class", cut, "-projectCP", "../examplCodez/target/classes",
//                "-gpt_key=" + GPTKey, "-use_codamosa", "-use_gpt_mutation", "-use_gpt_crossover", "-use_gpt_initial_pop", "-use_gpt_non_regression"};

//        String[] command = new String[]{"-generateMOSuite", "-class", cut, "-projectCP", "../examplCodez/target/classes",
//                "-gpt_key=" + GPTKey, "-use_codamosa", "-use_gpt_mutation", "-use_gpt_crossover", "-use_gpt_initial_pop"};

        String pathToCut = "D:\\Documents\\UOA\\2024\\Part 4 Project\\Repos\\2024-P4P-125\\examplCodez\\src\\main\\java\\mosalisa\\IncorrectOperations.java";
        String[] command = new String[]{"-generateMOSuite", "-class", cut, "-projectCP", "../examplCodez/target/classes", "-gpt_key=" + GPTKey, "-use_codamosa", "-use_gpt_initial_pop", "-path_to_cut", pathToCut};


        // display stats?
//        StringBuilder s = new StringBuilder();
//        s.append(RuntimeVariable.Coverage);
//        s.append(",");
//        s.append(RuntimeVariable.BranchCoverage);werq
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

        System.out.println("USE CODAMOSA: " + Properties.USE_CODAMOSA);
        System.out.println("USE GPT MUTATION: " + Properties.USE_GPT_MUTATION);
        System.out.println("USE GPT CROSSOVER: " + Properties.USE_GPT_CROSSOVER);
        System.out.println("USE GPT INITIAL POP: " + Properties.USE_GPT_INITIAL_POPULATION);
        System.out.println("USE GPT NON-REGRESSION: " + Properties.USE_GPT_NON_REGRESSION);

//        System.out.println("\n\n########## GENERATED TESTS: ##########");
        //System.out.println(ga.getBestIndividuals());
//        System.out.println(best.toString());
//        System.out.println("######################################\n\n");

        return new ArrayList<>(ga.getBestIndividuals());
    }

    @Test
    public void testMOSALisa() {
        //Console.SystemOutPritnln("CANOICAL Name: " + stack.class.);
        List<Chromosome<?>> population = this.setup(null, 0, "mosalisa.IncorrectOperations", true);

        Assert.assertNotEquals(population.size(), 0);
    }

    @Test
    public void testDefault() {
        List<Chromosome<?>> population = this.setup(null, 0, "mosalisa.IncorrectOperations", false);

        Assert.assertNotEquals(population.size(), 0);
    }

//    @Test
//    public void testGPT() {
//        Dotenv dotenv = Dotenv.load();
//        Properties.GPT_KEY = dotenv.get("GPT_TOKEN");
//
//        StringBuilder sb = new StringBuilder();
////        sb.append("Who is the president of the United States?");
////        sb.append("I have a test case, and I need to identify which lines could be deleted to potentially improve it. Please return an array of line numbers that can be deleted, based on the following:\n" +
////                "- The last line number I provide is the cutoff, and no statements beyond this line should be considered for deletion.\n" +
////                "- The array should be in the form [x0, x1, ..., xn], where each xi is a line number.\n" +
////                "- If no lines should be deleted, return an empty array: [].\n" +
////                "No further explanation is required, just the array of line numbers.\n" +
////                "Last allowed line number for deletion consideration is : 2\n");
////        sb.append("```\n");
////        sb.append("RecursiveParser recursiveParser0 = new RecursiveParser();\n");
////        sb.append("String string0 = \"\";\n");
////        sb.append("recursiveParser0.parse(string0);\n");
////        sb.append("```\n");
////        String initialGPTResponse = GPTRequest.chatGPT(sb.toString());
//        String initialGPTResponse = GPTRequest.chatGPT("Hello! My name is Robert!");
//        System.out.println(initialGPTResponse);
//        Assert.assertNotNull(initialGPTResponse);
//        initialGPTResponse = GPTRequest.chatGPT("What is the last thing I asked you?");
//        System.out.println(initialGPTResponse);
//        Assert.assertNotNull(initialGPTResponse);
////        System.out.println("UNFORMATTED RESPONSE:");
////        System.out.println(initialGPTResponse);
////        System.out.println("CODE ONLY:");
////        System.out.println(GPTRequest.get_code_only(initialGPTResponse));
//
//
//    }

}
