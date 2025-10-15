package ai.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * Load all files from a resource directory that match the given prefix and suffix.
     *
     * @param resourceDir the resource directory path (e.g., "/prompts/nodes")
     * @param prefix the file name prefix to match (e.g., "node_")
     * @param suffix the file name suffix to match (e.g., ".md")
     * @return a map with keys as the identifier part of filename (xxx from node_xxx.md) and values as file contents
     */
    public static Map<String, String> loadMultipleFromDirectory(String resourceDir, String prefix, String suffix) {
        Map<String, String> results = new HashMap<>();

        try {
            // Try to load from classpath
            Map<String, String> classpathContents = loadMultipleFromClasspathAsMap(resourceDir, prefix, suffix);
            if (classpathContents != null && !classpathContents.isEmpty()) {
                results.putAll(classpathContents);
                return results;
            }

            // Try to load from file paths
            Map<String, String> filePathContents = loadMultipleFromFilePathAsMap(RESOURCE_BASE_PATH + resourceDir, prefix, suffix);
            if (filePathContents != null && !filePathContents.isEmpty()) {
                results.putAll(filePathContents);
                return results;
            }

            // Try to load from parent file paths
            Map<String, String> parentFilePathContents = loadMultipleFromFilePathAsMap(RESOURCE_PARENT_PATH + resourceDir, prefix, suffix);
            if (parentFilePathContents != null && !parentFilePathContents.isEmpty()) {
                results.putAll(parentFilePathContents);
            }

        } catch (Exception e) {
            // Handle exception silently and return empty map
        }

        return results;
    }

    /**
     * Load multiple files from classpath directory and return as map.
     *
     * @param resourceDir the resource directory path
     * @param prefix the file name prefix to match
     * @param suffix the file name suffix to match
     * @return a map with keys as the identifier part of filename and values as file contents
     */
    private static Map<String, String> loadMultipleFromClasspathAsMap(String resourceDir, String prefix, String suffix) {
        Map<String, String> results = new HashMap<>();

        try {
            // Get the directory URL
            java.net.URL dirURL = ResourceUtil.class.getResource(resourceDir);
            if (dirURL == null) {
                return results;
            }

            // Handle JAR files
            if (dirURL.getProtocol().equals("jar")) {
                // For JAR files, we would need more complex handling which is beyond scope here
                // This is a simplified implementation
                return results;
            } else {
                // Handle file system directories
                java.io.File dir = new java.io.File(dirURL.toURI());
                if (dir.exists() && dir.isDirectory()) {
                    java.io.File[] files = dir.listFiles((d, name) ->
                            name.startsWith(prefix) && name.endsWith(suffix));

                    if (files != null) {
                        for (java.io.File file : files) {
                            try (InputStream inputStream = Files.newInputStream(file.toPath());
                                 InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                                 BufferedReader reader = new BufferedReader(inputStreamReader)) {

                                String fileName = file.getName();
                                // Extract identifier part (xxx from node_xxx.md)
                                String key = extractIdentifier(fileName, prefix, suffix);
                                String content = reader.lines().collect(Collectors.joining("\n"));
                                results.put(key, content);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Handle exception silently
        }

        return results;
    }

    /**
     * Load multiple files from file directory and return as map.
     *
     * @param filePathDir the file directory path
     * @param prefix the file name prefix to match
     * @param suffix the file name suffix to match
     * @return a map with keys as the identifier part of filename and values as file contents
     */
    private static Map<String, String> loadMultipleFromFilePathAsMap(String filePathDir, String prefix, String suffix) {
        Map<String, String> results = new HashMap<>();

        try {
            java.nio.file.Path dirPath = Paths.get(filePathDir);
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                Files.list(dirPath)
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.startsWith(prefix) && fileName.endsWith(suffix);
                        })
                        .forEach(path -> {
                            try {
                                String content = loadContentFromFilePath(path.toString());
                                String fileName = path.getFileName().toString();
                                // Extract identifier part (xxx from node_xxx.md)
                                String key = extractIdentifier(fileName, prefix, suffix);
                                results.put(key, content);
                            } catch (IOException e) {
                                // Skip files that cannot be loaded
                            }
                        });
            }
        } catch (Exception e) {
            // Handle exception silently
        }

        return results;
    }

    /**
     * Extract identifier from filename (e.g., get "xxx" from "node_xxx.md")
     *
     * @param fileName the full filename
     * @param prefix the prefix to remove
     * @param suffix the suffix to remove
     * @return the identifier part
     */
    private static String extractIdentifier(String fileName, String prefix, String suffix) {
        String result = fileName;
        if (result.startsWith(prefix)) {
            result = result.substring(prefix.length());
        }
        if (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }




    public static void main(String[] args) {
//        String prompt = loadAsString("/prompts/workflow_user_info_extract.md");
//        System.out.println(prompt);
        Map<String, String> map = loadMultipleFromDirectory("/prompts/nodes", "node_", ".md");
        System.out.println(map);
    }
}
