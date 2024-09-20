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
import java.util.List;
import java.nio.file.Paths;
import java.nio.file.Path;

public class CompileGentests {

    public static String junit_path = "target/dependency/junit-4.12.jar";
    public static String hamcrest_path = "target/dependency/hamcrest-core-1.3.jar";
    private static boolean first_use = true;

    public static List<TestCase> compileAndCarveTests(String cutClassPath) throws ClassNotFoundException, MalformedURLException {
        // Modifify the links to the jar paths if it is a system test
        if (Properties.IS_RUNNING_A_SYSTEM_TEST && first_use){
            junit_path = "../"+junit_path;
            hamcrest_path = "../"+hamcrest_path;
            first_use = false;
        }
        File junit_jar = new File(junit_path);
        File hamcrest_jar = new File(hamcrest_path);
        // Check if the jars are present
        if (!junit_jar.exists() || !hamcrest_jar.exists()){
            System.out.println("Could not find jars");
            return null;
        }
        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // Prepare a writer to capture compiler output
        StringWriter writer = new StringWriter();
        // Set classpath
        String compilerClassPath = cutClassPath+";"+junit_path+";"+hamcrest_path;
        // Prepare output path for compiler
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File(cutClassPath)));
        } catch (IOException e) {
            System.out.println("Failed to set output directory: " + e.getMessage());
            return null;
        }
        // Prepare the compilation task with the classpath
        Iterable<String> options = Arrays.asList("-classpath", compilerClassPath);
        JavaCompiler.CompilationTask task = compiler.getTask(writer, fileManager, null, options, null,
                Arrays.asList(new JavaSourceFromString("ClassTest", Properties.OUTPUT_DIR)));
        // Compile the source code
        boolean success = task.call();
        // Carve tests if compilation was successful
        if (success) {
            // Load the class
            File classesDir = new File(cutClassPath);
            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { classesDir.toURI().toURL() });
            Class<?> dynamicClass = Class.forName("ClassTest", true, classLoader);
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
            return carvedTestCases;
        } else {
            // Print compiler errors
            System.out.println("Compilation failed:");
            System.out.println(writer.toString());
            return null;
        }
    }

    public static boolean verifyCompilation(String testClassPath, String className, String outputDir) throws ClassNotFoundException, MalformedURLException {
        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // Prepare a writer to capture compiler output
        StringWriter writer = new StringWriter();

        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        String classOutputDir = "../target/test-classes";
        Path classOutputPath = Paths.get(classOutputDir);
        try {
            //TODO MAKE IT DELETE THE FILE IF IT ALREADY EXISTS
            if (Files.notExists(classOutputPath)) {
                Files.createDirectories(classOutputPath);  // Creates the directory along with any necessary parent directories
            }
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File(classOutputDir)));
        } catch (IOException e) {
            System.out.println("Failed to set output directory: " + e.getMessage());
        }

        //TODO MAKE THIS AUTOMATIC
        testClassPath = "../examplCodez/target/classes;../target/dependency/junit-4.12.jar;../target/dependency/hamcrest-core-1.3.jar;../runtime/target/classes;../target/test-classes";

        // Prepare the compilation task with the classpath
        Iterable<String> options = Arrays.asList("-classpath", testClassPath);

        JavaCompiler.CompilationTask task = compiler.getTask(writer, fileManager, null, options, null,
                Arrays.asList(new JavaSourceFromString(className.replace("REGRESSION", "scaffolding"), outputDir)));
        // Compile the source code
        boolean success = task.call();

        JavaCompiler.CompilationTask task1 = compiler.getTask(writer, null, null, options, null,
                Arrays.asList(new JavaSourceFromString(className, outputDir)));

        // Compile the source code
        success = task1.call();

        if (success) {
            System.out.println("Compilation complete.");
            return true;
        } else {
            // Print compiler errors
            System.out.println("Compilation failed:");
            //System.out.println(writer.toString());
            return false;
        }
    }
}
