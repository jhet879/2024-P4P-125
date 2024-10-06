package org.evosuite.ga.operators.crossover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.metaheuristics.mosa.MOSAllisa;
import org.evosuite.gpt.GPTRequest;
import org.evosuite.utils.Randomness;

import java.util.ArrayList;

public class GPTCrossOver<T extends Chromosome<T>> extends CrossOverFunction<T> {


    public static String gptCrossoverPrompt = "For these two java test chromosomes, return the most suitable location for " +
            "where the crossover operation should be performed.\n" +
            "- The array should be in the form [x1, x2], where x1 is the position to perform crossover in chromosome 1, " +
            "and x2 is the position to perform crossover in chromosome 2.\n" +
            "- Do not add newlines or any other formatting.\n" +
            "- Make sure not to pick the last line\n" +
            "- No further explanation is required, just the array of crossover positions.\n" +
            "\nChromosome 1: ```\n%s\n```\n" +
            "\nChromosome 2: ```\n%s\n```\n";

    @Override
    public void crossOver(T parent1, T parent2) throws ConstructionFailedException {
        if (parent1.size() < 2 || parent2.size() < 2) {
            return;
        }
        T t1 = parent1.clone();
        T t2 = parent2.clone();
        int pos1 = 0;
        int pos2 = 0;
        if (Randomness.nextDouble() <= Properties.GPT_CROSSOVER_USAGE_PROBABILITY) {
            MOSAllisa.gptCrossoverAttempts++;
            // USE GPT
            try {
                // Make request to GPT
                String gptPrompt = String.format(gptCrossoverPrompt, parent1, parent2);
                String initialGPTResponse = GPTRequest.chatGPT(gptPrompt, GPTRequest.GPT_4O_MINI);
                // Extract lines from response
                ArrayList<Integer> linesToDelete = GPTRequest.extractArrayFromString(initialGPTResponse);
                pos1 = linesToDelete.get(0);
                pos2 = linesToDelete.get(1);
            } catch (Exception ignored) {
            }
        }
        // Use default crossover method (single point) if GPT wasn't selected, or invalid position was selected.
        if ((pos1 == 0  && pos2 == 0) || (pos1 > t1.size() || (pos2 > t2.size()))) {
            float splitPoint = Randomness.nextFloat();
            pos1 = ((int) Math.floor((t1.size() - 1) * splitPoint)) + 1;
            pos2 = ((int) Math.floor((t2.size() - 1) * splitPoint)) + 1;
        } else {
            MOSAllisa.succesfulGPTCrossovers++;
        }
        // Perform crossover
        parent1.crossOver(t2, pos1, pos2);
        parent2.crossOver(t1, pos2, pos1);
    }
}
