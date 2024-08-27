package org.evosuite.gpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evosuite.Properties;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GPTRequest {

    public static String chatGPT(String prompt) {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = Properties.GPT_KEY;
        String model = "gpt-4o-mini";

        try {
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
            jsonMap.put("model", "gpt-4o-mini");
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

            // calls the method to extract the message.
            return response.toString();

        } catch (IOException e) {
            throw new RuntimeException(e);
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
}

