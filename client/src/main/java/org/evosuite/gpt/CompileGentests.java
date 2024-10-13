package org.evosuite.gpt;

import org.evosuite.Properties;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.factories.JUnitTestCarvedChromosomeFactory;

import javax.tools.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.nio.file.Paths;
import java.nio.file.Path;

public class CompileGentests {

    public static String junit_path = "junit-4.12.jar";
    public static String hamcrest_path = "hamcrest-core-1.3.jar";

    public static List<TestCase> compileAndCarveTests() {
        File junit_jar = new File(junit_path);
        File hamcrest_jar = new File(hamcrest_path);
        // Check if the jars are present
        if (!junit_jar.exists() || !hamcrest_jar.exists()){
            writeToGPTLogFile("COULD NOT LOCATE DEPENDENCY JARS\n");
            return null;
        }

        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            writeToGPTLogFile("NO COMPILER AVAILABLE");
            return null;
        }

        // Set classpath
        String compilerClassPath = Properties.CP + File.pathSeparator + junit_path + File.pathSeparator + hamcrest_path;
        compilerClassPath = compilerClassPath.replace('/', File.separatorChar);
        compilerClassPath = compilerClassPath.replace(';', File.pathSeparatorChar);

        // Prepare the compilation task with the classpath
        Iterable<String> options;
        writeToGPTLogFile("Compiler classpath: " + compilerClassPath + "\n");
        writeToGPTLogFile("user.dir: " + System.getProperty("user.dir") + "\n");
        writeToGPTLogFile("Class prefix: " + Properties.CLASS_PREFIX + "\n");

        // Set output dir for compiler
        File outputDirFile = new File(Properties.CLASS_PREFIX);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        options = Arrays.asList("-classpath", compilerClassPath, "-d", Properties.CLASS_PREFIX);
        writeToGPTLogFile("Compiler options: " + options + "\n");

        // Get the compilation units (i.e., the files to compile)
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(Arrays.asList("ClassTest.java"));
        // Diagnostic listener to capture errors
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // Compile the file with the classpath
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
        // Compile the code
        boolean success = task.call();

        // Carve tests if compilation was successful
        if (success) {
            writeToGPTLogFile("GPT TEST COMPILATION: SUCCESS\n");
            // Load the class
            File classesDir = new File(Properties.CLASS_PREFIX);
            Class<?> dynamicClass;
            try {
                URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { classesDir.toURI().toURL() });
                dynamicClass = Class.forName("ClassTest", true, classLoader);
            } catch (Exception ignored) {
                writeToGPTLogFile("FAILED TO LOAD CLASSTEST: " + ignored.getMessage() + "\n");
                return null;
            }
            writeToGPTLogFile("SUCCESSFULLY LOADED CLASSTEST\n");
            String canonicalName = dynamicClass.getCanonicalName();
            // Set default properties for carving
            Properties.SELECTED_JUNIT = canonicalName;
            Properties.SEED_MUTATIONS = 0;
            Properties.SEED_CLONE = 1;
            // Set this property to true to avoid altering the current evosuite run's goals
            Properties.skip_fitness_calculation = true;
            // Carve the tests
            JUnitTestCarvedChromosomeFactory factory = new JUnitTestCarvedChromosomeFactory(null);
            List<TestCase> carvedTestCases = factory.getCarvedTestCases();
            // Reset the property
            Properties.skip_fitness_calculation = false;
            deleteFolder("." + File.separator + Properties.CLASS_PREFIX);
            deleteFile("ClassTest.java");
            if (carvedTestCases != null) {
                if (carvedTestCases.isEmpty()) {
                    return null;
                }
                writeToGPTLogFile("Carved TestCases: " + carvedTestCases.size() + "\n");
            }
            return carvedTestCases;
        } else {
            // Print compiler errors
            deleteFolder("." + File.separator + Properties.CLASS_PREFIX);
            deleteFile("ClassTest.java");
            writeToGPTLogFile("GPT TEST COMPILATION: FAILED\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                writeToGPTLogFile("Error on line " + diagnostic.getLineNumber() + " in " + diagnostic.getSource().getName() + "\n");
                writeToGPTLogFile(diagnostic.getMessage(null) + "\n");
            }
            return null;
        }
    }

    public static boolean verifyCompilation(String className) {
        File junit_jar = new File(junit_path);
        File hamcrest_jar = new File(hamcrest_path);
        // Check if the jars are present
        if (!junit_jar.exists() || !hamcrest_jar.exists()){
            writeToGPTLogFile("COULD NOT LOCATE DEPENDENCY JARS\n");
            return false;
        }

        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            writeToGPTLogFile("NO COMPILER AVAILABLE");
            return false;
        }

        // Set classpath
        String compilerClassPath = Properties.CP + File.pathSeparator + junit_path + File.pathSeparator + hamcrest_path;
        compilerClassPath = compilerClassPath.replace('/', File.separatorChar);
        compilerClassPath = compilerClassPath.replace(';', File.pathSeparatorChar);
        // Prepare the compilation task with the classpath
        Iterable<String> options;
        writeToGPTLogFile("Compiler classpath: " + compilerClassPath + "\n");
        writeToGPTLogFile("user.dir: " + System.getProperty("user.dir") + "\n");
        writeToGPTLogFile("Class prefix: " + Properties.CLASS_PREFIX + "\n");

        // Set output dir for compiler
        File outputDirFile = new File("." + File.separator + Properties.ML_TESTS_DIR);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        options = Arrays.asList("-classpath", compilerClassPath, "-d", "." + File.separator + Properties.ML_TESTS_DIR);
        writeToGPTLogFile("Compiler options: " + options + "\n");

        // Get the compilation units (i.e., the files to compile)
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(Arrays.asList(className));
        // Diagnostic listener to capture errors
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        // Compile the file with the classpath
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
        // Compile the code
        boolean success = task.call();

        // Carve tests if compilation was successful
        if (success) {
            writeToGPTLogFile("GPT NON-REG TEST COMPILATION: SUCESS\n");
            return true;
        } else {
            // Print compiler errors
            deleteFolder("." + File.separator + Properties.ML_TESTS_DIR);
            deleteFile(className);
            writeToGPTLogFile("GPT NON-REG TEST COMPILATION: FAILED\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                writeToGPTLogFile("Error on line " + diagnostic.getLineNumber() + " in " + diagnostic.getSource().getName() + "\n");
                writeToGPTLogFile(diagnostic.getMessage(null) + "\n");
            }
            return false;
        }
    }

    public static void writeToGPTLogFile(String msg) {
        try (FileWriter fileWriter = new FileWriter(Properties.ML_REPORTS_DIR + "/GPT_LOG.txt", true)) {
            fileWriter.write(msg);
        } catch (IOException ignored) {
        }
    }

    private static void deleteFile(String file) {
        Path path = Paths.get(file);
        try {
            Files.delete(path);
        } catch (Exception ignored) {
            writeToGPTLogFile("Failed to delete the gpt generated source file\n");
        }
    }

    public static void deleteFolder(String dir) {
        Path path = Paths.get(dir);
        try {
            Files.walk(path).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception ignored) {
                    writeToGPTLogFile("Failed to delete carving compilation dir\n");
                }
                writeToGPTLogFile("Deleted\n");
            });
        } catch (Exception ignored) {
            writeToGPTLogFile("Failed to delete carving compilation dir\n");
        }
    }
}
