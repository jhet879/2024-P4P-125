package mosalisa;

public class IncorrectOperations {

    // Method name suggests adding 5, but it adds 6 instead
    public int add5(int number) {
        return number + 6;
    }

    // Method name suggests subtracting 2, but it subtracts 3 instead
    public int subtract2(int number) {
        return number - 3;
    }

    // Method name suggests multiplying by 3, but it multiplies by 4 instead
    public int multiplyBy3(int number) {
        return number * 4;
    }

    // Method name suggests concatenating strings, but it reverses them instead
    public String concatenate(String str1, String str2) {
        return new StringBuilder(str1 + str2).reverse().toString();
    }

    // Method name suggests converting to uppercase, but it converts to lowercase instead
    public String toUpperCase(String input) {
        return input.toLowerCase();
    }

    // Method name suggests checking if a string is empty, but checks if it contains "empty" instead
    public boolean isEmpty(String input) {
        return input.contains("empty");
    }

    // Method name suggests returning the length of the string, but returns a constant value instead
    public int getLength(String input) {
        return 10; // Returns a fixed value regardless of the input
    }

    // Method name suggests dividing by 2, but divides by 3 instead
    public double divideBy2(double number) {
        return number / 3;
    }
}

