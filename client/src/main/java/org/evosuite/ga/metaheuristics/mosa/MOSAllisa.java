/*
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.ga.metaheuristics.mosa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.comparators.OnlyCrowdingComparator;
import org.evosuite.ga.metaheuristics.mosa.structural.MultiCriteriaManager;
import org.evosuite.ga.operators.crossover.GPTCrossOver;
import org.evosuite.ga.operators.ranking.CrowdingDistance;
import org.evosuite.ga.stoppingconditions.GlobalTimeStoppingCondition;
import org.evosuite.gpt.CompileGentests;
import org.evosuite.statistics.OutputVariable;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.strategy.TestGenerationStrategy;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.statements.*;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.*;

import org.evosuite.gpt.*;

/**
 * @TODO UPDATE
 *
 * Implementation of the DynaMOSA (Many Objective Sorting Algorithm) described in the paper
 * "Automated Test Case Generation as a Many-Objective Optimisation Problem with Dynamic Selection
 * of the Targets".
 *
 * @author Annibale Panichella, Fitsum M. Kifetew, Paolo Tonella
 */
public class MOSAllisa extends AbstractMOSA {

    private static final long serialVersionUID = 146182080947267628L;

    private static final Logger logger = LoggerFactory.getLogger(MOSAllisa.class);

    public static boolean first_entry = true;

    public static int totalCODAMOSACarvingCalls = 0;
    public static int successfulCODAMOSACarvingCalls = 0;
    public static int totalGPTCarvingCalls = 0;
    public static int successfulGPTCarvingCalls = 0;
    public static int gptTestsAddedToOffSpringPop = 0;
    public static int totalCrossoverCalls = 0;
    public static int succesfulGPTCrossovers = 0;
    public static int gptCrossoverAttempts = 0;
    public static int codamosaCalls = 0;
    public static int GPTInitialPopRetries = 0;
    public static int GPTInitialPopCarved = 0;
    public static int stat_iterations = 0;

    static String algo_test_gen_prompt = "Given the Java class under test (note that the class may be cut off) and lines " +
            " of the class where test goals have not been met:\n" +
            "- Generate stand-alone JUnit 4 tests that can cover these goals.\n" +
            "- The tests should be self-contained, meaning no @Before methods should be used.\n" +
            "- Name the test class 'ClassTest'.\n" +
            "- **IMPORTANT:** Do NOT include any references to private methods or fields in the tests unless they " +
            "can be accessed through public methods.\n" +
            "- Only public and protected methods and fields may be used in the tests.\n" +
            "- Do not use Mockito." +
            "- **IMPORTANT:** Import the necessary classes from this classpath, %s, including the class under test: %s.\n" +
            "- **IMPORTANT:** Additionally, import any other required java classes.\n" +
            "\nClass under test:\n```\n%s\n```\nlinesToCover:\n%s";

    static String initial_test_gen_prompt = "Given the Java class under test (note that the class may be cut off) and the " +
            "coverage criterion:\n" +
            "- Generate %d stand-alone JUnit 4 tests that can cover these goals.\n" +
            "- The tests should be self-contained, meaning no @Before methods should be used.\n" +
            "- Name the test class 'ClassTest'.\n" +
            "- **IMPORTANT:** Do NOT include any references to private methods or fields in the tests unless they " +
            "can be accessed through public methods.\n" +
            "- Only public and protected methods and fields may be used in the tests.\n" +
            "- Do not use Mockito." +
            "- **IMPORTANT:** Import the necessary classes from this classpath, %s, including the class under test: %s.\n" +
            "- **IMPORTANT:** Only import and use the classes that are used in the class under test.\n" +
            "- **IMPORTANT:** Ensure that the resulting test suite will be compilable as-is. All imports must be accounted for.\n" +
            "\nClass under test:\n```\n%s\n```\nCriterion:\n%s";

    /**
     * Manager to determine the test goals to consider at each generation
     */
    protected MultiCriteriaManager goalsManager = null;

    protected CrowdingDistance<TestChromosome> distance = new CrowdingDistance<>();

    /**
     * Constructor based on the abstract class {@link AbstractMOSA}.
     *
     * @param factory
     */
    public MOSAllisa(ChromosomeFactory<TestChromosome> factory) {
        super(factory);
    }

    /**
     * Copied from AbstractMOSA.java (private method)
     * When a test case is changed via crossover and/or mutation, it can contains some
     * primitive variables that are not used as input (or to store the output) of method calls.
     * Thus, this method removes all these "trash" statements.
     *
     * @param chromosome
     * @return true or false depending on whether "unused variables" are removed
     */
    private boolean removeUnusedVariables(TestChromosome chromosome) {
        final int sizeBefore = chromosome.size();
        final TestCase t = chromosome.getTestCase();
        final List<Integer> toDelete = new ArrayList<>(chromosome.size());
        boolean hasDeleted = false;

        int num = 0;
        for (Statement s : t) {
            final VariableReference var = s.getReturnValue();
            final boolean delete = s instanceof PrimitiveStatement || s instanceof ArrayStatement;
            if (!t.hasReferences(var) && delete) {
                toDelete.add(num);
                hasDeleted = true;
            }
            num++;
        }
        toDelete.sort(Collections.reverseOrder());
        for (int position : toDelete) {
            t.remove(position);
        }
        final int sizeAfter = chromosome.size();
        if (hasDeleted) {
            logger.debug("Removed {} unused statements", (sizeBefore - sizeAfter));
        }
        return hasDeleted;
    }

    /**
     * Copied from AbstractMOSA.java (private method)
     * This method clears the cached results for a specific chromosome (e.g., fitness function
     * values computed in previous generations). Since a test case is changed via crossover
     * and/or mutation, previous data must be recomputed.
     *
     * @param chromosome TestChromosome to clean
     */
    private void clearCachedResults(TestChromosome chromosome) {
        chromosome.clearCachedMutationResults();
        chromosome.clearCachedResults();
        chromosome.clearMutationHistory();
        chromosome.getFitnessValues().clear();
    }

    /**
     * Copied from AbstractMOSA.java (private method)
     * This method checks whether the test has only primitive type statements. Indeed,
     * crossover and mutation can lead to tests with no method calls (methods or constructors
     * call), thus, when executed they will never cover something in the class under test.
     *
     * @param test to check
     * @return true if the test has at least one method or constructor call (i.e., the test may
     * cover something when executed; false otherwise
     */
    private boolean hasMethodCall(TestChromosome test) {
        boolean flag = false;
        TestCase tc = test.getTestCase();
        for (Statement s : tc) {
            if (s instanceof MethodStatement) {
                MethodStatement ms = (MethodStatement) s;
                boolean isTargetMethod = ms.getDeclaringClassName().equals(Properties.TARGET_CLASS);
                if (isTargetMethod) {
                    return true;
                }
            }
            if (s instanceof ConstructorStatement) {
                ConstructorStatement ms = (ConstructorStatement) s;
                boolean isTargetMethod = ms.getDeclaringClassName().equals(Properties.TARGET_CLASS);
                if (isTargetMethod) {
                    return true;
                }
            }
        }
        return flag;
    }

    /**
     * Copied from AbstractMOSA.java (private method)
     * Method used to mutate an offspring.
     *
     * @param offspring the offspring chromosome
     * @param parent    the parent chromosome that {@code offspring} was created from
     */
    private void mutate(TestChromosome offspring, TestChromosome parent) {
        offspring.mutate();
        if (!offspring.isChanged()) {
            // if offspring is not changed, we try to mutate it once again
            offspring.mutate();
        }
        if (!this.hasMethodCall(offspring)) {
            offspring.setTestCase(parent.getTestCase().clone());
            boolean changed = offspring.mutationInsert();
            if (changed) {
                offspring.getTestCase().forEach(Statement::isValid);
            }
            offspring.setChanged(changed);
        }
        this.notifyMutation(offspring);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<TestChromosome> breedNextGeneration() {
        List<TestChromosome> offspringPopulation = new ArrayList<>(Properties.POPULATION);
        int totalStalls = 0;
        // we apply only Properties.POPULATION/2 iterations since in each generation
        // we generate two offsprings
        for (int i = 0; i < Properties.POPULATION / 2 && !this.isFinished(); i++) {
            // select best individuals

            /*
             * the same individual could be selected twice! Is this a problem for crossover?
             * Because crossing over an individual with itself will most certainly give you the
             * same individual again...
             */

            TestChromosome parent1 = this.selectionFunction.select(this.population);
            TestChromosome parent2 = this.selectionFunction.select(this.population);

            TestChromosome offspring1 = parent1.clone();
            TestChromosome offspring2 = parent2.clone();
            // apply crossover
            if (Randomness.nextDouble() <= Properties.CROSSOVER_RATE) {
                try {
                    this.crossoverFunction.crossOver(offspring1, offspring2);
                    totalCrossoverCalls++;
                } catch (ConstructionFailedException e) {
                    logger.debug("CrossOver failed.");
                    continue;
                }
            }

            this.removeUnusedVariables(offspring1);
            this.removeUnusedVariables(offspring2);

            // apply mutation on offspring1
            this.mutate(offspring1, parent1);
            if (offspring1.isChanged()) {
                this.clearCachedResults(offspring1);
                offspring1.updateAge(this.currentIteration);
                this.calculateFitness(offspring1);
                offspringPopulation.add(offspring1);
                if (offspring1.getFitness() <= parent1.getFitness()){
                    totalStalls += 1;
                }
            }

            // apply mutation on offspring2
            this.mutate(offspring2, parent2);
            if (offspring2.isChanged()) {
                this.clearCachedResults(offspring2);
                offspring2.updateAge(this.currentIteration);
                this.calculateFitness(offspring2);
                offspringPopulation.add(offspring2);
                if (offspring2.getFitness() <= parent2.getFitness()){
                    totalStalls += 1;
                }
            }

            if (Properties.USE_CODAMOSA) {
                if (totalStalls >= 30) {
                    Set<TestFitnessFunction> rankedGoals;
                    rankedGoals = this.goalsManager.getLowFitnessBranches(this.population);
                    if (!rankedGoals.isEmpty()) {
                        codamosaCalls++;
                        List<TestCase> gptTestCases = invokeGPT(rankedGoals, false);
                        if (gptTestCases != null) {
                            if (!gptTestCases.isEmpty()) {
                                successfulCODAMOSACarvingCalls++;
                                for (TestCase tc : gptTestCases) {
                                    gptTestsAddedToOffSpringPop++;
                                    TestChromosome testChromosome = new TestChromosome();
                                    testChromosome.setTestCase(tc);
                                    testChromosome.set_gpt_status(true);
                                    this.calculateFitness(testChromosome);
                                    offspringPopulation.add(testChromosome);
                                }
                            }
                        }
                    }
                    totalStalls = 0;
                }
            }
        }
        // Add new randomly generate tests
        for (int i = 0; i < Properties.POPULATION * Properties.P_TEST_INSERTION; i++) {
            final TestChromosome tch;
            if (this.getCoveredGoals().size() == 0 || Randomness.nextBoolean()) {
                tch = this.chromosomeFactory.getChromosome();
                tch.setChanged(true);
            } else {
                tch = Randomness.choice(this.getSolutions()).clone();
                tch.mutate();
//				tch.mutate(); // TODO why is it mutated twice?
            }
            if (tch.isChanged()) {
                tch.updateAge(this.currentIteration);
                this.calculateFitness(tch);
                offspringPopulation.add(tch);
            }
        }
        logger.trace("Number of offsprings = {}", offspringPopulation.size());
        return offspringPopulation;
    }

    private String extractClassName(String targetClass) {
        int dotIndex = targetClass.lastIndexOf(".");
        return targetClass.substring(dotIndex + 1);
    }

    private List<TestCase> invokeGPT(Set<TestFitnessFunction> goals, Boolean isForInitialPop) {
        List<TestCase> carvedTestCases = new ArrayList<>();

        // Get the class as a string
        String classAsString;
        try {
            classAsString = new String(Files.readAllBytes(Paths.get(Properties.PATH_TO_CUT)));
            // Trim class if it is too large
            if (classAsString.length() > 35000) {
                classAsString = classAsString.substring(0, 35000);
            }
            writeToGPTLogFile("CLASS LENGTH: " + classAsString.length() + "\n");
        } catch (IOException e) {
            System.out.println("IO ERROR");
            writeToGPTLogFile("FAILED TO GET CLASS AS STRING\n");
            return carvedTestCases;
        }

        // Prepare the request for ChatGPT
        StringBuilder sb = new StringBuilder();
        String gptString;
        if (isForInitialPop) {
            for (Properties.Criterion crit : Properties.CRITERION) {
                sb.append(crit + "\n");
            }
            gptString = String.format(initial_test_gen_prompt, Properties.POPULATION, Properties.CP, Properties.TARGET_CLASS, classAsString, sb);
        } else {
            for (TestFitnessFunction test_func : goals) {
                sb.append(test_func + "\n");
            }
            String fitnessFuncs = sb.toString();
            // Trim fitness functions if it is too large
            if (fitnessFuncs.length() > 5000) {
                fitnessFuncs = fitnessFuncs.substring(0, 5000);
            }
            writeToGPTLogFile("FITNESS FUNC LENGTH: " + fitnessFuncs.length() + "\n");
            gptString = String.format(algo_test_gen_prompt, Properties.CP, Properties.TARGET_CLASS,classAsString, fitnessFuncs);
        }
        int carving_attempt_count = 0;
        while (carving_attempt_count < 3) {
            if (!isForInitialPop) {
                totalCODAMOSACarvingCalls++;
            }
            int gpt_fail_counter = 0;
            int delay = 30000;
            String initialGPTResponse = "";
            // Make 3 attempts at calling GPT
            while (gpt_fail_counter < 3) {
                // Make call to GPT
                initialGPTResponse = GPTRequest.chatGPT(gptString, GPTRequest.GPT_4O);
                totalGPTCarvingCalls++;
                if (!initialGPTResponse.equals("FAIL")){
                    break;
                }
                gpt_fail_counter++;
                try {
                    Thread.sleep(delay);
                } catch (Exception ignored) {
                }
            }
            if (initialGPTResponse.equals("FAIL")) {
                writeToGPTLogFile("EXCEEDED GPT REQUEST ATTEMPTS\n");
                return carvedTestCases;
            }
            successfulGPTCarvingCalls++;
            String formattedResponse = GPTRequest.get_code_only(initialGPTResponse);
            formattedResponse = GPTRequest.cleanResponse(formattedResponse);
            // TODO DETERMINE IF THIS IS SUFFICIENT
    //        formattedResponse = "import " + Properties.TARGET_CLASS + ";\n" + formattedResponse;
            GPTRequest.writeGPTtoFile(formattedResponse);

            try {
                // Carve the testcases from the gpt response
                carvedTestCases = CompileGentests.compileAndCarveTests(Properties.CP);
                if (carvedTestCases != null) {
                    if (!carvedTestCases.isEmpty()) {
                        writeToGPTLogFile("CARVING: SUCCESS\n");
                        break;
                    } else {
                        carving_attempt_count++;
                        writeToGPTLogFile("CARVING: FAILED + " + carving_attempt_count + "\n");
                        Thread.sleep(5000);
                    }
                } else {
                    carving_attempt_count++;
                    writeToGPTLogFile("CARVING: FAILED + " + carving_attempt_count + "\n");
                    Thread.sleep(5000);
                }
            } catch (Exception ignored) {
                carving_attempt_count++;
                writeToGPTLogFile("CARVING: FAILED + " + carving_attempt_count + "\n");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored1) {
                }
            }
        }
        writeToGPTLogFile("\n");
        return  carvedTestCases;
    }

    private static void writeToGPTLogFile(String msg) {
        try (FileWriter fileWriter = new FileWriter(Properties.ML_REPORTS_DIR + "/GPT_LOG.txt", true)) {
            fileWriter.write(msg);
        } catch (IOException ignored) {
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void evolve() {
        // Generate offspring, compute their fitness, update the archive and coverage goals.
        List<TestChromosome> offspringPopulation = this.breedNextGeneration();

        // Create the union of parents and offspring
        List<TestChromosome> union = new ArrayList<>(this.population.size() + offspringPopulation.size());
        union.addAll(this.population);
        union.addAll(offspringPopulation);

        // Ranking the union
        logger.debug("Union Size = {}", union.size());

        // Ranking the union using the best rank algorithm (modified version of the non dominated
        // sorting algorithm)
        this.rankingFunction.computeRankingAssignment(union, this.goalsManager.getCurrentGoals());

        // let's form the next population using "preference sorting and non-dominated sorting" on the
        // updated set of goals
        int remain = Math.max(Properties.POPULATION, this.rankingFunction.getSubfront(0).size());
        int index = 0;
        this.population.clear();

        // Obtain the first front
        List<TestChromosome> front = this.rankingFunction.getSubfront(index);

        // Successively iterate through the fronts (starting with the first non-dominated front)
        // and insert their members into the population for the next generation. This is done until
        // all fronts have been processed or we hit a front that is too big to fit into the next
        // population as a whole.
        while ((remain > 0) && (remain >= front.size()) && !front.isEmpty()) {
            // Assign crowding distance to individuals
            this.distance.fastEpsilonDominanceAssignment(front, this.goalsManager.getCurrentGoals());

            // Add the individuals of this front
            this.population.addAll(front);

            // Decrement remain
            remain = remain - front.size();

            // Obtain the next front
            index++;
            if (remain > 0) {
                front = this.rankingFunction.getSubfront(index);
            }
        }

        // In case the population for the next generation has not been filled up completely yet,
        // we insert the best individuals from the current front (the one that was too big to fit
        // entirely) until there are no more free places left. To this end, and in an effort to
        // promote diversity, we consider those individuals with a higher crowding distance as
        // being better.
        if (remain > 0 && !front.isEmpty()) { // front contains individuals to insert
            this.distance.fastEpsilonDominanceAssignment(front, this.goalsManager.getCurrentGoals());
            front.sort(new OnlyCrowdingComparator<>());
            for (int k = 0; k < remain; k++) {
                this.population.add(front.get(k));
            }
        }

        this.currentIteration++;
        //logger.debug("N. fronts = {}", ranking.getNumberOfSubfronts());
        //logger.debug("1* front size = {}", ranking.getSubfront(0).size());
        logger.debug("Covered goals = {}", goalsManager.getCoveredGoals().size());
        logger.debug("Current goals = {}", goalsManager.getCurrentGoals().size());
        logger.debug("Uncovered goals = {}", goalsManager.getUncoveredGoals().size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializePopulation() {
        logger.info("executing initializePopulation function");

        this.notifySearchStarted();
        this.currentIteration = 0;

        if (!Properties.USE_GPT_INITIAL_POPULATION) {
            // Create a random parent population P0
            this.generateInitialPopulation(Properties.POPULATION);
        } else {
            boolean success = false;
            // Keep trying to generate tests with GPT until it is successful
            while (!success) {
                // Switch to normal generation after 2 tries (10 in total which is done in invokeGPT)
                if (GPTInitialPopRetries >= 2) {
                    writeToGPTLogFile("Failed to generate tests for initial population after [" + GPTInitialPopRetries + "] attempts, switching to regular generation...\n");
                    this.generateInitialPopulation(Properties.POPULATION);
                    this.calculateFitness();
                    this.notifyIteration();
                    return;
                }
                GPTInitialPopRetries++;
                GPTInitialPopCarved = 0;
                this.population.clear();
                List<TestCase> gptTestCases = invokeGPT(null, true);
                if (gptTestCases != null) {
                    if (!gptTestCases.isEmpty()) {
                        // ## REPLACE WITH ITS OWN VARIABLE
                        // successfulCarvedGPTCalls++;
                        for (TestCase tc : gptTestCases) {
                            TestChromosome testChromosome = new TestChromosome();
                            testChromosome.setTestCase(tc);
                            testChromosome.set_gpt_status(true);
                            this.calculateFitness(testChromosome);
                            //System.out.println("AF " + testChromosome.getFitness());
                            population.add(testChromosome);
                            GPTInitialPopCarved++;
                        }
                        success = true;
                    }
                } else {
                    if (GPTInitialPopRetries != 2) {
                        writeToGPTLogFile("Failed to generated tests for initial population, retrying [" + GPTInitialPopRetries + "]...\n");
                    }
                }
            }
            writeToGPTLogFile("Successfully generated [" + GPTInitialPopCarved + "] tests for the initial population\n");
        }
        // Determine fitness
        this.calculateFitness();
        this.notifyIteration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generateSolution() {
        logger.debug("executing generateSolution function");

        // Create logging file
        Path directory = Paths.get(Properties.ML_REPORTS_DIR);
        Path filepath = Paths.get(Properties.ML_REPORTS_DIR + File.separator + "GPT_LOG.txt");
        // Ensure the directory exists
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
                Files.createFile(filepath);
                first_entry = false;
            } catch (Exception ignored) {
            }
        } else if (first_entry) {
            try {
                Files.delete(filepath);
                first_entry = false;
            } catch (IOException ignored) {
            }
        }

        if (Properties.USE_GPT_CROSSOVER) {
            this.setCrossOverFunction(new GPTCrossOver());
        }

        // Set up the targets to cover, which are initially free of any control dependencies.
        // We are trying to optimize for multiple targets at the same time.
        this.goalsManager = new MultiCriteriaManager(this.fitnessFunctions);

        LoggingUtils.getEvoLogger().info("* Initial Number of Goals in MOSAllisa = " +
                this.goalsManager.getCurrentGoals().size() + " / " + this.getUncoveredGoals().size());

        logger.debug("Initial Number of Goals = " + this.goalsManager.getCurrentGoals().size());

        if (this.population.isEmpty()) {
            // Initialize the population by creating solutions at random.
            this.initializePopulation();
        }

        // Compute the fitness for each population member, update the coverage information and the
        // set of goals to cover. Finally, update the archive.
        // this.calculateFitness(); // Not required, already done by this.initializePopulation();

        // Calculate dominance ranks and crowding distance. This is required to decide which
        // individuals should be used for mutation and crossover in the first iteration of the main
        // search loop.
        this.rankingFunction.computeRankingAssignment(this.population, this.goalsManager.getCurrentGoals());
        for (int i = 0; i < this.rankingFunction.getNumberOfSubfronts(); i++) {
            this.distance.fastEpsilonDominanceAssignment(this.rankingFunction.getSubfront(i), this.goalsManager.getCurrentGoals());
        }

        this.resetStoppingConditions();

        LocalTime currentTime;
        // Evolve the population generation by generation until all gaols have been covered or the
        // search budget has been consumed.
        while (!isFinished() && this.goalsManager.getUncoveredGoals().size() > 0) {
//            System.out.println("CYCLE: " + this.currentIteration + " GOALS: " + this.getTotalNumberOfGoals());
//            System.out.println("COVERED GOALS[" + this.goalsManager.getCoveredGoals().size() + "]: " +this.goalsManager.getCoveredGoals());
//            System.out.println("UNCOVERED GOALS[" + this.goalsManager.getUncoveredGoals().size() + "]: " +this.goalsManager.getUncoveredGoals());
//            System.out.println();
//            int gpt_counter = 0;
//            for (TestChromosome tc : this.population) {
//                if (tc.get_gpt_status()){
//                    gpt_counter++;
//                }
//            }
//            totalGPTTestsAddedToSortedPop += gpt_counter;
//            System.out.println("GPT GENERATED TESTS IN POPULATION: " + gpt_counter);
            stat_iterations++;
            currentTime = LocalTime.now();
            writeToGPTLogFile("#### NEW EVOLUTION ITERATION -" + currentTime + "- ####\n");
            this.evolve();
            this.notifyIteration();
        }

        if (Properties.USE_CODAMOSA || Properties.USE_GPT_MUTATION || Properties.USE_GPT_CROSSOVER ||
                Properties.USE_GPT_INITIAL_POPULATION || Properties.USE_GPT_NON_REGRESSION) {
            Path filepath1 = Paths.get(Properties.ML_REPORTS_DIR + File.separator + "report.txt");
            // Ensure the directory exists
            if (Files.exists(filepath1)) {
                try {
                    Files.delete(filepath1);
                } catch (Exception ignored) {
                }
            }
            try {
                Files.createFile(filepath1);
            } catch (Exception ignored) {
            }
            stat_iterations = currentIteration;
            // Open or create the file, and append to its contents
            try (FileWriter fileWriter = new FileWriter(filepath1.toString(), true)) {
                fileWriter.write("#### MOSALLISA STATS ####\n\n");
                fileWriter.write("Iterations: " + currentIteration + "\n");
                fileWriter.write("Successful GPT Requests (Carving Related): " + successfulGPTCarvingCalls + "/" + totalGPTCarvingCalls + "\n\n");
                if (Properties.USE_CODAMOSA) {
                    fileWriter.write("- CODAMOSA\n");
                    fileWriter.write("  - CODAMOSA Calls: " + codamosaCalls + "\n");
                    fileWriter.write("  - Successfully Carved: " + successfulCODAMOSACarvingCalls + "/" + totalCODAMOSACarvingCalls + "\n\n");
                }
                if (Properties.USE_GPT_MUTATION) {
                    fileWriter.write("- MUTATION STATS\n");
                    fileWriter.write("  - Standard:\n");
                    fileWriter.write("    - Deletes: " + TestChromosome.Mdelete + "/" + TestChromosome.AT_Mdelete +
                            " Changes: " + TestChromosome.Mchange + "/" + TestChromosome.AT_Mchange + " Inserts: " +
                            TestChromosome.Minsert + "/" + TestChromosome.AT_Minsert + "\n");
                    fileWriter.write("  - GPT:\n");
                    fileWriter.write("    - Deletes: " + TestChromosome.GPTMdelete + "/" + TestChromosome.AT_GPTMdelete +
                            " Changes: " + TestChromosome.GPTMchange + "/" + TestChromosome.AT_GPTMchange +
                            " Inserts: " + TestChromosome.GPTMinsert + "/" + TestChromosome.AT_GPTMinsert + "\n\n");
                }
                if (Properties.USE_GPT_CROSSOVER) {
                    fileWriter.write("- GPT CROSSOVER STATS\n");
                    fileWriter.write("  - Successful GPT Calls: " + succesfulGPTCrossovers + "/" + gptCrossoverAttempts + "\n");
                    fileWriter.write("  - Total Crossovers: " + totalCrossoverCalls + "\n\n");
                }
                if (Properties.USE_GPT_INITIAL_POPULATION) {
                    fileWriter.write("- GPT INITIAL POPULATION\n");
                    fileWriter.write("  - Attempts: " + GPTInitialPopRetries + "\n");
                    fileWriter.write("  - Carved Tests: " + GPTInitialPopCarved + "\n\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            Map<String, Object> data = new HashMap<>();
            // COVERAGE METRICS
            // SUCCESS RATE OF CODAMOSA CALLS
            if (totalCODAMOSACarvingCalls == 0) {
                data.put("cm_success", 0);
            } else {
                data.put("cm_success", ((double) successfulCODAMOSACarvingCalls / totalCODAMOSACarvingCalls));
            }
            // TOTAL CARVED TESTS FOR CODAMOSA
            data.put("cm_carved", MOSAllisa.gptTestsAddedToOffSpringPop);
            // TOTAL CARVED TESTS FOR INITIAL POPULATION
            data.put("init_carved", MOSAllisa.GPTInitialPopCarved);
            // SUCCESS RATE OF GPT CALLS (CODAMOSA & INITIAL POP)
            if (MOSAllisa.totalGPTCarvingCalls == 0) {
                data.put("cm_gpt", 0);
            } else {
                data.put("cm_gpt", ((double) successfulGPTCarvingCalls / totalGPTCarvingCalls));
            }
            // SUCCESS RATE OF CROSSOVER CALLS
            if (MOSAllisa.gptCrossoverAttempts == 0) {
                data.put("crov_success", 0);
            } else {
                data.put("crov_success", ((double) succesfulGPTCrossovers / gptCrossoverAttempts));
            }
            // SUCESS RATES OF MUTATION CALLS
            if (TestChromosome.AT_GPTMdelete == 0) {
                data.put("mut_del_success", 0);
            } else {
                data.put("mut_del_success", ((double) TestChromosome.GPTMdelete / TestChromosome.AT_GPTMdelete));
            }
            if (TestChromosome.AT_GPTMchange == 0) {
                data.put("mut_cha_success", 0);
            } else {
                data.put("mut_cha_success", ((double) TestChromosome.GPTMchange / TestChromosome.AT_GPTMchange));
            }
            if (TestChromosome.AT_GPTMinsert == 0) {
                data.put("mut_ins_success", 0);
            } else {
                data.put("mut_ins_success", ((double) TestChromosome.GPTMinsert / TestChromosome.AT_GPTMinsert));
            }
            data.put("iterations", stat_iterations);
            ObjectMapper mapper = new ObjectMapper();
//            String jsonString = mapper.writeValueAsString(data);
//            System.out.println(jsonString);
            File file = new File(Properties.ML_REPORTS_DIR + File.separator + "mosallisa_stats.json");
            mapper.writeValue(file, data);
        } catch (Exception ignored) {
            CompileGentests.writeToGPTLogFile("Failed to save mosallisa JSON stats file " + ignored);
        }

        writeToGPTLogFile("#### FINISHED EVOLUTION ####\n");
        this.resetStoppingConditions();

        System.out.println("Iterations: " + this.currentIteration);
//        System.out.println("Total GPT Tests Added to Offspring Population: " + gptTestsAddedToOffSpringPop);
//        System.out.println("Running sum of gpt tests added to sorted population: " + totalGPTTestsAddedToSortedPop);
//        System.out.println("TOTAL SUCCESSFUL MUTATIONS: " + TestChromosome.Mchanged);
        this.notifySearchFinished();
    }
    /**
     * Calculates the fitness for the given individual. Also updates the list of targets to cover,
     * as well as the population of best solutions in the archive.
     *
     * @param c the chromosome whose fitness to compute
     */
    @Override
    protected void calculateFitness(TestChromosome c) {
        if (!isFinished()) {
            // this also updates the archive and the targets
            this.goalsManager.calculateFitness(c, this);
            this.notifyEvaluation(c);
        }
    }

    @Override
    public List<? extends FitnessFunction<TestChromosome>> getFitnessFunctions() {
        List<TestFitnessFunction> testFitnessFunctions = new ArrayList<>(goalsManager.getCoveredGoals());
        testFitnessFunctions.addAll(goalsManager.getUncoveredGoals());
        return testFitnessFunctions;
    }
}
