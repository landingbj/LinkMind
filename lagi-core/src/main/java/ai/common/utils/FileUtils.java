package ai.common.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static String sha256sum(File file) {
        return sha256sum(file.getAbsolutePath());
    }

    public static String sha256sum(String file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] mdBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : mdBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Error computing SHA-256 hash", e);
        }
    }

    @Deprecated
    public static String md5sum(File file) {
        return md5sum(file.getAbsolutePath());
    }

    @Deprecated
    public static String md5sum(String file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] mdBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : mdBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Error computing MD5 hash", e);
        }
    }

    public static String getOsTempDir() {
        return System.getProperty("java.io.tmpdir");
    }

    public static void writeTextToFile(String path, String text) throws IOException {
        ai.utils.FileUtils.writeStringToFile(new File(path), text, StandardCharsets.UTF_8);
    }

    public static String readTextFromFile(String path) throws IOException {
        return ai.utils.FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
    }

    public static List<String> listFiles(String directoryPath, boolean recursive) {
        List<String> filePaths = new ArrayList<>();
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            listFilesHelper(directory, recursive, filePaths);
        }
        return filePaths;
    }

    private static void listFilesHelper(File directory, boolean recursive, List<String> filePaths) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && recursive) {
                    listFilesHelper(file, true, filePaths);
                } else if (file.isFile()) {
                    filePaths.add(file.getAbsolutePath());
                }
            }
        }
    }
}
