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
    public static String tests_class_path = "target/test-classes";
    private static boolean first_use = true;

    public static List<TestCase> compileAndCarveTests(String cutClassPath) {
        // Modifify the links to the jar paths if it is a system test
        if (Properties.IS_RUNNING_A_SYSTEM_TEST && first_use){
            tests_class_path = "../"+tests_class_path;
            first_use = false;
        }

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
        // Specify the options for the compiler
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
            clean_temp_files();
            if (carvedTestCases != null) {
                if (carvedTestCases.isEmpty()) {
                    return null;
                }
                writeToGPTLogFile("Carved TestCases: " + carvedTestCases.size() + "\n");
            }
            return carvedTestCases;
        } else {
            // Print compiler errors
            clean_temp_files();
            writeToGPTLogFile("GPT TEST COMPILATION: FAILED\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                writeToGPTLogFile("Error on line " + diagnostic.getLineNumber() + " in " + diagnostic.getSource().getName());
                writeToGPTLogFile(diagnostic.getMessage(null));
            }
            return null;
        }
    }

    public static boolean verifyCompilation(String cutClassPath, String className, String outputDir) throws ClassNotFoundException, MalformedURLException {
        // Modifify the links to the jar paths if it is a system test
        if (Properties.IS_RUNNING_A_SYSTEM_TEST && first_use){
            junit_path = "../"+junit_path;
            hamcrest_path = "../"+hamcrest_path;
            tests_class_path = "../"+tests_class_path;
            first_use = false;
        }
        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // Prepare a writer to capture compiler output
        StringWriter writer = new StringWriter();
        // Prepare output path for compiler
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        try {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File(tests_class_path)));
        } catch (IOException e) {
            System.out.println("Failed to set output directory: " + e.getMessage());
            return false;
        }
        // Set classpath
        String testClassPath = cutClassPath+";"+junit_path+";"+hamcrest_path;
        // Prepare the compilation task with the classpath
        Iterable<String> options = Arrays.asList("-classpath", testClassPath);
        JavaCompiler.CompilationTask task = compiler.getTask(writer, fileManager, null, options, null,
                Arrays.asList(new JavaSourceFromString(className, outputDir)));
        // Compile the source code
        boolean success = task.call();
        if (success) {
            return true;
        } else {
            // Print compiler errors
            System.out.println("Compilation failed:");
            //System.out.println(writer.toString());
            return false;
        }
    }

    private static void writeToGPTLogFile(String msg) {
        try (FileWriter fileWriter = new FileWriter(Properties.ML_REPORTS_DIR + "/GPT_LOG.txt", true)) {
            fileWriter.write(msg);
        } catch (IOException ignored) {
        }
    }

    private static void clean_temp_files() {
        // Delete generated class files
        Path path = Paths.get("." + File.separatorChar + "ClassTest.java");
        Path path1 = Paths.get("." + File.separatorChar + "ClassTest.class");
        try {
//            Files.delete(path);
//            Files.delete(path1);
        } catch (Exception ignored) {
        }
    }
}
