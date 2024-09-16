package org.evosuite.gpt;

import org.evosuite.Properties;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class JavaSourceFromString extends SimpleJavaFileObject {
    private final String code;

    public JavaSourceFromString(String name, String outputDir) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        String content = "";
        Path filePath = Paths.get(outputDir + "//" + name +".java");
        try {
            // Read all bytes from the file and convert them to a string
            content = new String(Files.readAllBytes(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.code = content;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
