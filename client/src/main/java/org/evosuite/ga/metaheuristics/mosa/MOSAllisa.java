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

import com.fasterxml.jackson.databind.annotation.JsonAppend;
import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.comparators.OnlyCrowdingComparator;
import org.evosuite.ga.metaheuristics.mosa.structural.MultiCriteriaManager;
import org.evosuite.ga.operators.crossover.GPTCrossOver;
import org.evosuite.ga.operators.ranking.CrowdingDistance;
import org.evosuite.gpt.CompileGentests;
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
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    public static int totalCODAMOSAGPTCalls = 0;
    public static int successfulCODAMOSAGPTCalls = 0;
    public int gptTestsAddedToOffSpringPop = 0;
    public int totalGPTTestsAddedToSortedPop = 0;
    public static int totalCrossoverCalls = 0;
    public static int succesfulGPTCrossovers = 0;
    public static int gptCrossoverAttempts = 0;

    static String algo_test_gen_prompt = "Given the Java class under test, lines of the class where test goals have not been met " +
            "generate stand-alone (no @Before, all the tests are self-contained) tests that can cover these goals, combine " +
            "tests wherever possible. Call the class, 'ClassTest'. Do not add any import/package statements, only add imports " +
            "required for JUnit and any exceptions that are used.\nClass under test:\n```\n%s\n```\nlinesToCover:\n%s";

    static String initial_test_gen_prompt = "Given the Java class under test, and some coverage criterion, generate %d " +
            "stand-alone (no @Before, all the tests are self-contained) tests that can cover these criterion, combine " +
            "tests wherever possible. Call the class, 'ClassTest'. Do not add any package statements. Use org.junit.Test" +
            "and org.junit.Assert.* for the imports.\nClass under test:\n```\n%s\n```\nCriterion:\n%s";

    private final Lock gpt_lock = new ReentrantLock();

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
                        totalCODAMOSAGPTCalls++;
                        gpt_lock.lock();
                        List<TestCase> gptTestCases = invokeGPT(rankedGoals, false);
                        gpt_lock.unlock();
                        if (gptTestCases != null) {
                            successfulCODAMOSAGPTCalls++;
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
        } catch (IOException e) {
            System.out.println("IO ERROR");
            return carvedTestCases;
        }
        // Prepare the request for ChatGPT
        StringBuilder sb = new StringBuilder();
        String gptString;
        if (isForInitialPop) {
            for (Properties.Criterion crit : Properties.CRITERION) {
                sb.append(crit + "\n");
            }
            gptString = String.format(initial_test_gen_prompt, Properties.POPULATION, classAsString, sb);
        } else {
            for (TestFitnessFunction test_func : goals) {
                sb.append(test_func + "\n");
            }
            gptString = String.format(algo_test_gen_prompt, classAsString, sb);
        }
        // Make call to GPT
        String initialGPTResponse = GPTRequest.chatGPT(gptString);
        String formattedResponse = GPTRequest.get_code_only(initialGPTResponse);
        formattedResponse = GPTRequest.cleanResponse(formattedResponse);
        // TODO DETERMINE IF THIS IS SUFFICIENT
        formattedResponse = "import " + Properties.TARGET_CLASS + ";\n" + formattedResponse;
        GPTRequest.writeGPTtoFile(formattedResponse);

        String log_msg = "CARVING: FAILED\n\n";

        try {
            // Carve the testcases from the gpt response
            carvedTestCases = CompileGentests.compileAndCarveTests(Properties.CP);
        } catch (Exception e) {
            try (FileWriter fileWriter = new FileWriter(Properties.ML_REPORTS_DIR + "/GPT_LOG.txt", true)) {
                fileWriter.write(log_msg);
            } catch (IOException ignored) {
            }
            return carvedTestCases;
        }
        if (carvedTestCases != null) {
            log_msg = "CARVING: SUCCESS\n\n";
        }

        try (FileWriter fileWriter = new FileWriter(Properties.ML_REPORTS_DIR + "/GPT_LOG.txt", true)) {
            fileWriter.write(log_msg);
        } catch (IOException ignored) {
        }

        return  carvedTestCases;
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
            int carvedTests = 0;
            int retries = 0;
            Properties.SEARCH_BUDGET = 3600;
            boolean success = false;
//            Set<TestFitnessFunction> goals;
//            goals = this.goalsManager.getCurrentGoals();
            // Keep trying to generate tests with GPT until it is successful
            while (!success) {
                carvedTests = 0;
                this.population.clear();
                List<TestCase> gptTestCases = invokeGPT(null, true);
                if (gptTestCases != null) {
                    // ## REPLACE WITH ITS OWN VARIABLE
                    // successfulCarvedGPTCalls++;
                    for (TestCase tc : gptTestCases) {
                        TestChromosome testChromosome = new TestChromosome();
                        testChromosome.setTestCase(tc);
                        testChromosome.set_gpt_status(true);
                        this.calculateFitness(testChromosome);
                        //System.out.println("AF " + testChromosome.getFitness());
                        population.add(testChromosome);
                        carvedTests++;
                    }
                    success = true;
                } else {
                    retries++;
                    logger.warn("Failed to generated tests for initial population, retrying [" + retries + "]...");
                }
            }
            logger.warn("Successfully generated [" + carvedTests + "] tests for the initial population");
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
            this.evolve();
            this.notifyIteration();
        }

        if (Properties.USE_CODAMOSA || Properties.USE_GPT_MUTATION || Properties.USE_GPT_CROSSOVER ||
                Properties.USE_GPT_INITIAL_POPULATION || Properties.USE_GPT_NON_REGRESSION) {
            Path directory = Paths.get(Properties.ML_REPORTS_DIR);
            Path filepath = Paths.get(Properties.ML_REPORTS_DIR + "/report.txt");
            // Ensure the directory exists
            if (Files.exists(filepath)) {
                try {
                    Files.delete(filepath);
                } catch (Exception ignored) {
                }
            }
            try {
                Files.createDirectories(directory);
                Files.createFile(filepath);
            } catch (Exception ignored) {
            }

            // Open or create the file, and append to its contents
            try (FileWriter fileWriter = new FileWriter(filepath.toString(), true)) {
                fileWriter.write("#### MOSALLISA STATS ####\n\n");
                if (Properties.USE_CODAMOSA) {
                    fileWriter.write("- CODAMOSA\n");
                    fileWriter.write("  - Successfully Carved: " + successfulCODAMOSAGPTCalls + "/" + totalCODAMOSAGPTCalls + "\n\n");
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
                }
                if (Properties.USE_GPT_NON_REGRESSION) {
                    fileWriter.write("GPT NON-REGRESSION\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
