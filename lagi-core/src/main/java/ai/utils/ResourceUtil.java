package ai.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Utility class for loading prompt templates from various sources.
 */
public class ResourceUtil {
    private static final String RESOURCE_BASE_PATH = "lagi-web/src/main/resources";
    private static final String RESOURCE_PARENT_PATH = "../lagi-web/src/main/resources";


    /**
     * Load content from a resource path.
     * Tries to load from the following sources in order:
     * 1. Classpath resource
     * 2. File path: lagi-web/src/main/resources + resPath
     * 3. File path: ../lagi-web/src/main/resources + resPath
     *
     * @param resPath the resource path (e.g., "/prompts/workflow_user_info_extract.md")
     * @return the content, or null if not found
     */
    public static String loadAsString(String resPath) {
        String content = loadFromClasspath(resPath);
        if (content != null) {
            return content;
        }

        content = loadFromFilePath(RESOURCE_BASE_PATH + resPath);
        if (content != null) {
            return content;
        }

        content = loadFromFilePath(RESOURCE_PARENT_PATH + resPath);
        return content;
    }

    /**
     * Load content from classpath resource.
     *
     * @param resPath the resource path
     * @return the content, or null if not found
     */
    private static String loadFromClasspath(String resPath) {
        try (InputStream inputStream = ResourceUtil.class.getResourceAsStream(resPath)) {
            if (inputStream == null) {
                return null;
            }

            try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(inputStreamReader)) {

                return reader.lines()
                        .collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Load content from file path.
     *
     * @param filePath the file path
     * @return the content, or null if not found
     */
    private static String loadFromFilePath(String filePath) {
        try {
            return loadContentFromFilePath(filePath);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Load content from a file path.
     *
     * @param filePath the file path to load from
     * @return the content of the file as a string
     * @throws IOException if an I/O error occurs
     */
    public static String loadContentFromFilePath(String filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(filePath));
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            return reader.lines()
                    .collect(Collectors.joining("\n"));
        }
    }

    public static void main(String[] args) {
        String prompt = loadAsString("/prompts/workflow_user_info_extract.md");
        System.out.println(prompt);
    }
}
