package org.evosuite.ga.operators.crossover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.gpt.GPTRequest;
import org.evosuite.utils.Randomness;

import java.util.ArrayList;

public class GPTCrossOver<T extends Chromosome<T>> extends CrossOverFunction<T> {
    public static int succesfulGPTCrossovers = 0;
    public static int gptCrossoverAttempts = 0;
    public static int testGPTCrossoverFNCalls = 0;

    @Override
    public void crossOver(T parent1, T parent2) throws ConstructionFailedException {
        testGPTCrossoverFNCalls++;

        if (parent1.size() < 2 || parent2.size() < 2) {
            return;
        }

        T t1 = parent1.clone();
        T t2 = parent2.clone();

        int pos1 = 0;
        int pos2 = 0;

        if (Randomness.nextDouble() <= Properties.GPT_CROSSOVER_USAGE_PROBABILITY) {
            gptCrossoverAttempts++;
            // USE GPT
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("For these two java test chromosomes, return the most suitable location for where the crossover operation should be performed.\n" +
                        "- The array should be in the form [x1, x2], where x1 is the position to perform crossover in chromosome 1, and x2 is the position to perform crossover in chromosome 2.\n" +
                        "- Do not add newlines or any other formatting.\n" +
                        "- Make sure not to pick the last line\n" +
                        "- No further explanation is required, just the array of crossover positions.\n");
                sb.append("\nChromosome 1: ```\n");
                sb.append(parent1);
                sb.append("\n```\n");
                sb.append("\nChromosome 2: ```\n");
                sb.append(parent2);
                sb.append("\n```\n");
                //System.out.println("GPT Request\n" + sb.toString() + "\n");
                String initialGPTResponse = GPTRequest.chatGPT(sb.toString());
                ArrayList<Integer> linesToDelete = extractArrayFromString(initialGPTResponse);

                pos1 = linesToDelete.get(0);
                pos2 = linesToDelete.get(1);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        if ((pos1 == 0  && pos2 == 0) || (pos1 == t2.size() || (pos2 == t1.size()))) {
            float splitPoint = Randomness.nextFloat();
            pos1 = ((int) Math.floor((t1.size() - 1) * splitPoint)) + 1;
            pos2 = ((int) Math.floor((t2.size() - 1) * splitPoint)) + 1;
        } else {
            succesfulGPTCrossovers++;
        }

        parent1.crossOver(t2, pos1, pos2);
        parent2.crossOver(t1, pos2, pos1);
    }

    public static ArrayList<Integer> extractArrayFromString(String input) {
        ArrayList<Integer> numbers = new ArrayList<>();

        String gptContent = "";
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode rootNode = objectMapper.readTree(input);
            JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
            gptContent = (contentNode.toString()).replace("\"", "");
        } catch (Exception e) {
            return numbers;
        }

        // Remove the square brackets
        gptContent = gptContent.replace("[", "").replace("]", "");
        gptContent = gptContent.replace("\"", "");

        // Split the string by commas and whitespace
        String[] numberStrings = gptContent.split(",\\s*");

        // Convert the string array into an ArrayList of Integers
        for (String numberString : numberStrings) {
            numbers.add(Integer.parseInt(numberString));
        }

        return numbers;

    }
}
