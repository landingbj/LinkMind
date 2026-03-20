package ai.utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class UrlDownloadZipUtil {
    /**
     * 从指定 URL 下载文件到本地目标文件。
     * @param url 下载地址（http/https）
     * @param targetFile 下载保存路径
     * @param timeoutMillis 连接/响应超时
     * @param retryCount 下载失败重试次数
     * @param cleanFailedTempFile 下载失败时是否清理
     */
    public static void downloadFileFromUrl(
            String url,
            File targetFile,
            int timeoutMillis,
            int retryCount,
            boolean cleanFailedTempFile
    ) throws Exception {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url不能为空");
        }
        if (targetFile == null) {
            throw new IllegalArgumentException("targetFile不能为空");
        }

        boolean success = false;

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(timeoutMillis))
                .setResponseTimeout(Timeout.ofMilliseconds(timeoutMillis))
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet httpGet = new HttpGet(url);

            int currentRetry = 0;
            while (currentRetry < retryCount) {
                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    int code = response.getCode();
                    if (code == 200) {
                        if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
                            targetFile.getParentFile().mkdirs();
                        }
                        try (OutputStream out = new FileOutputStream(targetFile)) {
                            if (response.getEntity() != null) {
                                response.getEntity().writeTo(out);
                            }
                        }
                        success = true;
                        return;
                    }

                    currentRetry++;
                    log.warn("URL下载失败，状态码：{}，重试次数：{}", code, currentRetry);
                    Thread.sleep(1000);
                } catch (Exception e) {
                    currentRetry++;
                    log.warn("URL下载异常，重试次数：{}", currentRetry, e);
                    Thread.sleep(1000);
                }
            }

            throw new RuntimeException("URL下载失败，已重试" + retryCount + "次");
        } finally {
            if (cleanFailedTempFile && !success && targetFile.exists()) {
                boolean deleted = targetFile.delete();
                log.warn("URL下载失败清理临时文件: {} deleted={}", targetFile.getAbsolutePath(), deleted);
            }
        }
    }

    /**
     * 解压 ZIP 到指定目录，返回解压条目数。
     * @param zipFile 待解压 zip 文件
     * @param targetDir 解压目标目录（会自动创建）
     * @return 解压的条目数量（文件 + 目录数量）
     */
    public static int unzipFile(File zipFile, File targetDir) throws IOException {
        if (zipFile == null || !zipFile.exists() || !zipFile.canRead()) {
            throw new IOException("ZIP文件不存在或不可读: " + (zipFile != null ? zipFile.getAbsolutePath() : "null"));
        }
        if (targetDir == null) {
            throw new IOException("目标目录不能为null");
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("创建目标目录失败: " + targetDir.getAbsolutePath());
        }

        int count = 0;
        Path targetDirPath = targetDir.toPath().normalize();
        byte[] buffer = new byte[8192];

        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                String entryName = zipEntry.getName();

                Path targetPath = targetDirPath.resolve(entryName).normalize();

                // ZipSlip 防护
                if (!targetPath.startsWith(targetDirPath)) {
                    log.warn("跳过不安全的ZIP条目: {}", entryName);
                    zis.closeEntry();
                    continue;
                }

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(targetPath);
                    count++;
                } else {
                    Path parent = targetPath.getParent();
                    if (parent != null) Files.createDirectories(parent);

                    try (OutputStream fos = new FileOutputStream(targetPath.toFile())) {
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    count++;
                }

                zis.closeEntry();
            }
        }

        return count;
    }

    /**
     * 将 zip 解压到 {@code targetBaseDir} 下的一个唯一目录，并在解压完成后删除 zip 文件。
     * @param zipFile 待解压 zip 文件
     * @param targetBaseDir 解压的根目录
     * @param uniquePrefix 解压子目录名前缀（例如：modelName），会拼接时间戳
     * @return 解压后的唯一根目录
     */
    public static File unzipFileToUniqueDirAndDeleteZip(File zipFile, File targetBaseDir, String uniquePrefix) throws IOException {
        if (zipFile == null || !zipFile.exists() || !zipFile.canRead()) {
            throw new IOException("ZIP文件不存在或不可读: " + (zipFile != null ? zipFile.getAbsolutePath() : "null"));
        }
        if (targetBaseDir == null) {
            throw new IOException("targetBaseDir不能为空");
        }

        String prefix = (uniquePrefix == null || uniquePrefix.trim().isEmpty()) ? "model" : uniquePrefix.trim();
        File targetDir = new File(targetBaseDir, prefix + "_" + System.currentTimeMillis());
        // mkdirs: 会同时创建 targetBaseDir
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("创建模型解压目录失败: " + targetDir.getAbsolutePath());
        }

        int extracted = unzipFile(zipFile, targetDir);
        boolean deleted = zipFile.delete();
        log.info("ZIP解压完成: extracted={}, zipDeleted={}, targetDir={}", extracted, deleted, targetDir.getAbsolutePath());
        return targetDir;
    }
}
