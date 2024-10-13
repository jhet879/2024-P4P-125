package mosalisa;

import java.util.Stack;

public class RecursiveParser {

    private Stack<Integer> stateStack;

    public RecursiveParser() {
        this.stateStack = new Stack<>();
    }

    // The parse method is recursive and expects a complex sequence of well-formatted inputs
    public boolean parse(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        return parseRecursive(input, 0);
    }

    // Private recursive method that relies on specific input formats and internal state
    public boolean parseRecursive(String input, int depth) {
        if (depth > input.length()) {
            return false;
        }

        char currentChar = input.charAt(depth);

        if (currentChar == '(') {
            stateStack.push(depth);
            return parseRecursive(input, depth + 1);
        } else if (currentChar == ')') {
            if (stateStack.isEmpty()) {
                throw new IllegalStateException("Mismatched parentheses");
            }
            stateStack.pop();
            return parseRecursive(input, depth + 1);
        } else if (Character.isDigit(currentChar)) {
            return parseRecursive(input, depth + 1);
        } else {
            throw new UnsupportedOperationException("Unsupported character: " + currentChar);
        }
    }

    // Public method requiring specific input sequences
    public boolean isBalanced(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        try {
            boolean result = parse(input);
            return stateStack.isEmpty() && result;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // Method that expects very specific input patterns
    public int processNumericSequence(String input) {
        if (input == null || input.isEmpty() || !input.matches("[0-9]+")) {
            throw new IllegalArgumentException("Input must be a non-empty numeric string");
        }

        return input.length();
    }
}