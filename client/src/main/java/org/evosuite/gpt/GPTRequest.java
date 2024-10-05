package org.evosuite.gpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evosuite.Properties;

import javax.tools.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GPTRequest {

    static int request_counter = 0;
    static boolean first_entry = true;

    public static String chatGPT(String prompt) {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = Properties.GPT_KEY;
        String model = "gpt-4o-mini";
        request_counter++;

        Path directory = Paths.get(Properties.ML_REPORTS_DIR);
        Path filepath = Paths.get(Properties.ML_REPORTS_DIR + "/GPT_LOG.txt");
        // Ensure the directory exists
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
                Files.createFile(filepath);
            } catch (Exception ignored) {
            }
        } else if (first_entry) {
            try {
                Files.delete(filepath);
                first_entry = false;
            } catch (IOException ignored) {
            }
        }
        try {
            writeToGPTLogFile("== REQUEST: " + request_counter + " ==\n");
            writeToGPTLogFile("== PROMPT ==\n");
            writeToGPTLogFile(prompt);
            writeToGPTLogFile("============\n");
//            writeToGPTLogFile(prompt + "\n");
            URL obj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // The request body
            // Create the JSON payload using Jackson
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> jsonMap = new HashMap<>();
//            jsonMap.put("model", "gpt-4o-mini");
//            jsonMap.put("model", "gpt-4");
            jsonMap.put("model", "gpt-4o");
            jsonMap.put("messages", new Map[]{message});

            String jsonInputString = objectMapper.writeValueAsString(jsonMap);

            try(OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Response from ChatGPT
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;

            StringBuffer response = new StringBuffer();

            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            br.close();
            Thread.sleep(100);

            writeToGPTLogFile("RESPONSE: " + response + "\n");
            // calls the method to extract the message.
            return response.toString();
        } catch (Exception e) {
            writeToGPTLogFile("GPT REQUEST FAILURE: " + e.getMessage() + "\n");
            throw new RuntimeException(e);
        }
    }

    private static void writeToGPTLogFile(String msg) {
        try (FileWriter fileWriter = new FileWriter(Properties.ML_REPORTS_DIR + "/GPT_LOG.txt", true)) {
            fileWriter.write(msg);
        } catch (IOException ignored) {
        }
    }

    public static String extractMessageFromJSONResponse(String response) {
        int start = response.indexOf("content")+ 11;

        int end = response.indexOf("\"", start);

        return response.substring(start, end);

    }

    public static String get_code_only(String gptReponse){
        // Replace double newlines with single newlines
        String formattedResponse = gptReponse.replace("\\n", "\n");
        String Delimiter = "```";

        int startIndex = formattedResponse.indexOf(Delimiter);
        if (startIndex == -1) {
            return null;  // Start delimiter not found
        }

        startIndex += Delimiter.length();
        int endIndex = formattedResponse.indexOf(Delimiter, startIndex);
        if (endIndex == -1) {
            return null;  // End delimiter not found
        }

        return formattedResponse.substring(startIndex, endIndex);
    }

    public static void writeGPTtoFile(String gptReponse){
        String output_file = "ClassTest.java";
        try (PrintWriter out = new PrintWriter(output_file)) {
            out.println(gptReponse);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeGPTtoFile(String gptReponse, String fileName, String outputPath){
        try (PrintWriter out = new PrintWriter(outputPath + File.separatorChar + fileName + ".java")) {
            out.println(gptReponse);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static String cleanResponse(String gptResponse){
        // Find the index of the first newline character
        int newlineIndex = gptResponse.indexOf('\n');
        String formattedResponse;

        // If there is no newline or the first line is not "java", return the original string
        if (newlineIndex == -1 || !gptResponse.substring(0, newlineIndex).trim().equals("java")) {
            formattedResponse = gptResponse;
        } else {
            // Return the string starting after the first newline character
            formattedResponse = gptResponse.substring(newlineIndex + 1);
        }

        formattedResponse = formattedResponse.replace("\\\"", "\"");

        return formattedResponse;
    }

    public static ArrayList<Integer> extractArrayFromString(String input) {
        ArrayList<Integer> numbers = new ArrayList<>();
        String gptContent;
        ObjectMapper objectMapper = new ObjectMapper();
        // Extract content from response
        try {
            JsonNode rootNode = objectMapper.readTree(input);
            JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
            gptContent = (contentNode.toString()).replace("\"", "");
            gptContent = gptContent.replace(" ","");
            gptContent = gptContent.replace("\\n","");
            gptContent = gptContent.replace("\\","");
            gptContent = gptContent.replace("json","");
            gptContent = gptContent.replace("```","");
        } catch (Exception e) {
            return numbers;
        }
        if (gptContent.matches("\\[]")) {
            return numbers;
        }
        // Remove the square brackets
        gptContent = gptContent.replace("[", "").replace("]", "");
        // Split the string by commas and whitespace
        String[] numberStrings = gptContent.split(",\\s*");
        // Convert the string array into an ArrayList of Integers
        for (String numberString : numberStrings) {
            numbers.add(Integer.parseInt(numberString));
        }
        return numbers;
    }

    public static ArrayList<int[]> extractTuplesFromString(String input) {
        ArrayList<int[]> numbers = new ArrayList<>();
        String gptContent;
        ObjectMapper objectMapper = new ObjectMapper();
        // Extract content from response
        try {
            JsonNode rootNode = objectMapper.readTree(input);
            JsonNode contentNode = rootNode.path("choices").get(0).path("message").path("content");
            gptContent = (contentNode.toString()).replace("\"", "");
            gptContent = gptContent.replaceAll("\\[|]|\\s+", "");
            String[] pairs = gptContent.split("},\\{");
            // Extract numbers into array
            for (String pair : pairs) {
                pair = pair.replaceAll("[{}]", "");
                String[] nums = pair.split(",");
                int[] arr = new int[]{Integer.parseInt(nums[0]), Integer.parseInt(nums[1])};
                numbers.add(arr);
            }
        } catch (Exception e) {
            return numbers;
        }
        return numbers;
    }
}

