package org.evosuite.gpt;

import org.evosuite.Properties;
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
import java.util.Arrays;
import java.util.List;

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
                Arrays.asList(new JavaSourceFromString("ClassTest")));

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

            JUnitTestCarvedChromosomeFactory factory = new JUnitTestCarvedChromosomeFactory(null);
            List<TestCase> carvedTestCases = factory.getCarvedTestCases();
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
}
