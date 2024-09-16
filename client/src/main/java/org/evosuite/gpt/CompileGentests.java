package org.evosuite.gpt;

import org.evosuite.Properties;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.factories.JUnitTestCarvedChromosomeFactory;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.nio.file.Paths;
import java.nio.file.Path;

public class CompileGentests {
    public static List<TestCase> compileTests(String testClassPath) throws ClassNotFoundException, MalformedURLException {
        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // Prepare a writer to capture compiler output
        StringWriter writer = new StringWriter();

        //TODO MAKE THIS AUTOMATIC
        testClassPath = "../examplCodez/target/classes;../target/dependency/junit-4.12.jar;../target/dependency/hamcrest-core-1.3.jar";

        // Prepare the compilation task with the classpath
        Iterable<String> options = Arrays.asList("-classpath", testClassPath);
        JavaCompiler.CompilationTask task = compiler.getTask(writer, null, null, options, null,
                Arrays.asList(new JavaSourceFromString("ClassTest", Properties.OUTPUT_DIR)));

        // Compile the source code
        boolean success = task.call();

        if (success) {
            System.out.println("Compilation complete.");
            File classesDir = new File("../master/");
            URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { classesDir.toURI().toURL() });
            Class<?> dynamicClass = Class.forName("ClassTest", true, classLoader);
            String canonicalName = dynamicClass.getCanonicalName();
            //System.out.println("Canonical Name: " + canonicalName);

            Properties.SELECTED_JUNIT = canonicalName;
            Properties.SEED_MUTATIONS = 0;
            Properties.SEED_CLONE = 1;

            // Set this property to true to avoid altering the current evosuite run's goals
            Properties.skip_fitness_calculation = true;
            JUnitTestCarvedChromosomeFactory factory = new JUnitTestCarvedChromosomeFactory(null); // <-- Causes spikes in goals
            List<TestCase> carvedTestCases = factory.getCarvedTestCases();
            Properties.skip_fitness_calculation = false;

            factory.getCarvedTestSuite();
            //System.out.println(carvedTestCases.size());
            return carvedTestCases;
        } else {
            // Print compiler errors
            System.out.println("Compilation failed:");
            //System.out.println(writer.toString());
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
