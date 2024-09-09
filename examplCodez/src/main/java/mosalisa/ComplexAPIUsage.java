package mosalisa;

import java.util.ArrayList;
import java.util.List;

public class ComplexAPIUsage {

    private List<String> data;

    public ComplexAPIUsage() {
        data = new ArrayList<>();
    }

    // Requires understanding of specific complex sequences of calls
    public void addData(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Value cannot be null or empty");
        }
        data.add(value);
    }

    public String fetchData(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        for (String value : data) {
            if (value.contains(query)) {
                return value;
            }
        }

        return null;
    }

    // Requires specific input format to fully test, i.e., a valid sequence of values in the list
    public String getFormattedData(String delimiter) {
        if (delimiter == null) {
            throw new IllegalArgumentException("Delimiter cannot be null");
        }

        StringBuilder builder = new StringBuilder();
        for (String value : data) {
            builder.append(value).append(delimiter);
        }

        if (builder.length() > 0) {
            builder.setLength(builder.length() - delimiter.length()); // Remove last delimiter
        }

        return builder.toString();
    }

    // Requires handling special strings and exception management
    public int processSpecialString(String input) throws UnsupportedOperationException {
        if (input.contains("@")) {
            throw new UnsupportedOperationException("Special characters not allowed");
        }
        return input.length();
    }

    // Complex sequence of method invocations
    public void complexSequence(String[] inputs, String delimiter) {
        if (inputs == null || inputs.length == 0 || delimiter == null) {
            throw new IllegalArgumentException("Invalid inputs");
        }

        for (String input : inputs) {
            addData(input);
        }

        String formattedData = getFormattedData(delimiter);
        System.out.println("Formatted data: " + formattedData);
    }
}