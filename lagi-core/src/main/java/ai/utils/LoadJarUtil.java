package ai.utils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

@Slf4j
public class LoadJarUtil {
    private static final String[] LAGI_EXTENSION_JARS = {
            "lagi-extension-1.0.0-with-deps.jar"
    };

//    private static final String DOWNLOAD_URL = "http://localhost:8000";
    private static final String DOWNLOAD_URL = "https://cdn.linkmind.top/extension";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder().build();

    public static synchronized URLClassLoader loadAllJarsFromFolder(String jarFolderPath) {
        File jarFolder = new File(jarFolderPath);
        if (!jarFolder.exists()) {
            jarFolder.mkdirs();
            log.info("Created jar folder: {}", jarFolder.getAbsolutePath());
        }
        if (!jarFolder.isDirectory()) {
            log.warn("Jar folder path is not a directory: {}", jarFolder.getAbsolutePath());
            return null;
        }
        log.info("Start loading extension jars from folder: {}", jarFolder.getAbsolutePath());
        List<File> jarFiles = new ArrayList<>();
        for (String jarName : LAGI_EXTENSION_JARS) {
            File extensionJar = new File(jarFolder, jarName);
            if (ensureJarReady(extensionJar)) {
                jarFiles.add(extensionJar);
                log.info("Extension jar is ready: {}", extensionJar.getAbsolutePath());
            } else {
                log.warn("Extension jar is unavailable after check/download: {}", extensionJar.getAbsolutePath());
            }
        }
        try {
            URL[] urls = new URL[jarFiles.size()];
            for (int i = 0; i < jarFiles.size(); i++) {
                urls[i] = jarFiles.get(i).toURI().toURL();
            }
            log.info("Loaded {} extension jar(s).", jarFiles.size());
            return new URLClassLoader(urls, LoadJarUtil.class.getClassLoader());
        } catch (MalformedURLException ignored) {
            log.error("Failed to build URLClassLoader for extension jars.", ignored);
        }
        return null;
    }

    private static boolean ensureJarReady(File jarFile) {
        if (isCompleteJar(jarFile)) {
            log.debug("Jar already valid: {}", jarFile.getAbsolutePath());
            return true;
        }
        log.info("Jar missing or invalid, start download: {}", jarFile.getAbsolutePath());
        return downloadJar(jarFile);
    }

    private static boolean isCompleteJar(File jarFile) {
        if (!jarFile.exists() || !jarFile.isFile() || jarFile.length() == 0) {
            return false;
        }
        try (JarFile ignored = new JarFile(jarFile)) {
            return true;
        } catch (IOException ignored) {
            log.warn("Jar is not complete/corrupted: {}", jarFile.getAbsolutePath());
            return false;
        }
    }

    private static boolean downloadJar(File jarFile) {
        String downloadLink = buildDownloadUrl(jarFile.getName());
        Path targetPath = jarFile.toPath();
        Path tempPath = targetPath.resolveSibling(jarFile.getName() + ".download");
        Request request = new Request.Builder().url(downloadLink).get().build();
        log.info("Downloading jar from: {}", downloadLink);
        try {
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Download failed, http status: {}, url: {}", response.code(), downloadLink);
                    return false;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    log.error("Download failed, empty response body: {}", downloadLink);
                    return false;
                }
                try (ResponseBody responseBody = body) {
                    Files.copy(responseBody.byteStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (!isCompleteJar(tempPath.toFile())) {
                Files.deleteIfExists(tempPath);
                log.error("Downloaded file is not a complete jar: {}", tempPath);
                return false;
            }
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Download and replace jar success: {}", targetPath);
            return true;
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignore) {
            }
            log.error("Download jar failed: {}", downloadLink, ignored);
            return false;
        }
    }

    private static String buildDownloadUrl(String jarName) {
        if (DOWNLOAD_URL.endsWith("/")) {
            return DOWNLOAD_URL + jarName;
        }
        return DOWNLOAD_URL + "/" + jarName;
    }
}
