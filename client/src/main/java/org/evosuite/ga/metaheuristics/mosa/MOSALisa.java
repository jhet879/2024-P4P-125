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

import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.comparators.OnlyCrowdingComparator;
import org.evosuite.ga.metaheuristics.mosa.structural.MultiCriteriaManager;
import org.evosuite.ga.operators.ranking.CrowdingDistance;
import org.evosuite.gpt.JDecompiler;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.statements.*;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @TODO UPDATE
 *
 * Implementation of the DynaMOSA (Many Objective Sorting Algorithm) described in the paper
 * "Automated Test Case Generation as a Many-Objective Optimisation Problem with Dynamic Selection
 * of the Targets".
 *
 * @author Annibale Panichella, Fitsum M. Kifetew, Paolo Tonella
 */
public class MOSALisa extends AbstractMOSA {

    private static final long serialVersionUID = 146182080947267628L;

    private static final Logger logger = LoggerFactory.getLogger(MOSALisa.class);

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
    public MOSALisa(ChromosomeFactory<TestChromosome> factory) {
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


            if (totalStalls >= 30) {
                LinkedHashMap<TestFitnessFunction, Double> rankedGoals;
                rankedGoals = this.goalsManager.getLowFitnessBranches(this.population);
                if (!rankedGoals.isEmpty())
                {
                    invokeGPT(rankedGoals);
                }
                totalStalls = 0;
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
        logger.info("Number of offsprings = {}", offspringPopulation.size());
        return offspringPopulation;
    }

    private String extractClassName(String targetClass) {
        int dotIndex = targetClass.lastIndexOf(".");
        return targetClass.substring(dotIndex + 1);
    }

    private void invokeGPT(LinkedHashMap<TestFitnessFunction, Double> rankedGoals) {
        System.out.println("Invoking GPT...\n");
        System.out.println("Fitness Functions:");
        System.out.println(rankedGoals);
        String pathToClass = Properties.CP + "/" + Properties.PROJECT_PREFIX + "/" + extractClassName(Properties.TARGET_CLASS) + ".class";
        System.out.println("Path to Class: " + pathToClass);
        // Decompile the class file and get it as a string
        String classAsString = JDecompiler.decompileAndPrintClassFiles(pathToClass);
        System.out.println(classAsString);
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
    public void generateSolution() {
        logger.debug("executing generateSolution function");

        // Set up the targets to cover, which are initially free of any control dependencies.
        // We are trying to optimize for multiple targets at the same time.
        this.goalsManager = new MultiCriteriaManager(this.fitnessFunctions);

        LoggingUtils.getEvoLogger().info("* Initial Number of Goals in MOSALisa = " +
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
            this.evolve();
            this.notifyIteration();
        }

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
