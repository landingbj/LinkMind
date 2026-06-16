package ai.medusa.utils;

import ai.medusa.pojo.CacheItem;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CacheLoader {
    private static final Logger logger = LoggerFactory.getLogger(CacheLoader.class);
    private final Gson gson = new Gson();
    private static final String persistenceDirectory;

    private static volatile CacheLoader instance;

    private final List<CacheItem> loadedItems;

    static {
        persistenceDirectory = PromptCacheConfig.CACHE_PERSISTENT_PATH;
    }

    private CacheLoader() {
        this.loadedItems = new ArrayList<>();
    }

    public static CacheLoader getInstance() {
        if (instance == null) {
            synchronized (CacheLoader.class) {
                if (instance == null) {
                    instance = new CacheLoader();
                }
            }
        }
        return instance;
    }

    public List<CacheItem> loadFromFile(String jsonlFilePath) {
        List<CacheItem> items = new ArrayList<>();
        File file = new File(jsonlFilePath);

        if (!file.exists() || !file.isFile()) {
            logger.warn("File does not exist or is not a regular file: {}", jsonlFilePath);
            return Collections.emptyList();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    CacheItem item = gson.fromJson(line, CacheItem.class);
                    items.add(item);
                } catch (Exception e) {
                    logger.warn("Failed to parse line {} in file {}: {}", lineNumber, jsonlFilePath, e.getMessage());
                }
            }
            logger.debug("Successfully loaded {} items from {}", items.size(), jsonlFilePath);

        } catch (IOException e) {
            logger.error("Error reading file: {}", jsonlFilePath, e);
        }

        this.loadedItems.addAll(items);
        return items;
    }

    public void loadFromFiles() {
        File dir = new File(persistenceDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        int totalLoaded = loadBaseCache();

        try (Stream<Path> pathStream = Files.list(Paths.get(persistenceDirectory))) {
            List<Path> files = pathStream
                    .filter(path -> path.toString().endsWith(".jsonl") || path.toString().endsWith(".json"))
                    .collect(Collectors.toList());
            logger.info("Found {} JSONL files in {}", files.size(), persistenceDirectory);
            for (Path path : files) {
                List<CacheItem> fileItems = loadFromFile(path.toString());
                totalLoaded += fileItems.size();
            }
            logger.info("Total items loaded from all files: {}", totalLoaded);

        } catch (IOException e) {
            logger.error("Error listing files in directory: {}", persistenceDirectory, e);
        }
    }

    public List<CacheItem> getLoadedItems() {
        Set<CacheItem> uniqueItems = loadedItems.stream()
                .filter(item -> item.getChatCompletionResult() != null)
                .collect(Collectors.toSet());
        return Collections.unmodifiableList(new ArrayList<>(uniqueItems));
    }

    public void clearLoadedItems() {
        loadedItems.clear();
        logger.info("Cleared all loaded items from memory");
    }

    private int loadBaseCache() {
        int totalLoaded = 0;
        List<String> fileList = getBaseCacheFiles();
        for (String filePath : fileList) {
            List<CacheItem> fileItems = loadFromZipFile(filePath);
            totalLoaded += fileItems.size();
        }
        return totalLoaded;
    }

    private List<String> getBaseCacheFiles() {
        List<String> fileNames = new ArrayList<>();
        ClassLoader classLoader = CacheLoader.class.getClassLoader();
        String dirPath = "";
        URL dirUrl = classLoader.getResource(dirPath);
        if (dirUrl != null && "file".equals(dirUrl.getProtocol())) {
            try {
                Path path = Paths.get(dirUrl.toURI());
                try (Stream<Path> pathStream = Files.walk(path)) {
                    return pathStream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".model"))
                            .map(p -> p.toAbsolutePath().toString())
                            .collect(Collectors.toList());
                }
            } catch (URISyntaxException | IOException e) {
                logger.error("Error accessing resource files: {}", e.getMessage());
            }
        }
        return fileNames;
    }

    private List<CacheItem> loadFromZipFile(String zipFilePath) {
        List<CacheItem> items = new ArrayList<>();
        File zipFile = new File(zipFilePath);

        if (!zipFile.exists() || !zipFile.isFile()) {
            logger.warn("Model file does not exist or is not a regular file: {}", zipFilePath);
            return Collections.emptyList();
        }
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.entries().nextElement();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        CacheItem item = gson.fromJson(line, CacheItem.class);
                        items.add(item);
                    } catch (Exception e) {
                        logger.warn("Failed to parse line {} in model file {}: {}",
                                lineNumber, entry.getName(), e.getMessage());
                    }
                }
                logger.info("Successfully loaded {} items from {}", items.size(), entry.getName());
            }
        } catch (IOException e) {
            logger.error("Error reading model file: {}", zipFilePath, e);
        }
        this.loadedItems.addAll(items);
        return items;
    }
}