package ai.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileUtil {

    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /**
     * 读取文件内容，作为字符串返回
     */
    public static String readFileAsString(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException(filePath);
        }

        if (file.length() > 1024 * 1024 * 1024) {
            throw new IOException("File is too large");
        }

        StringBuilder sb = new StringBuilder((int) (file.length()));
        // 创建字节输入流
        FileInputStream fis = new FileInputStream(filePath);
        // 创建一个长度为10240的Buffer
        byte[] bbuf = new byte[10240];
        // 用于保存实际读取的字节数
        int hasRead = 0;
        while ( (hasRead = fis.read(bbuf)) > 0 ) {
            sb.append(new String(bbuf, 0, hasRead));
        }
        fis.close();
        return sb.toString();
    }

    /**
     * 根据文件路径读取byte[] 数组
     */
    public static byte[] readFileByBytes(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException(filePath);
        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) file.length());
            BufferedInputStream in = null;

            try {
                in = new BufferedInputStream(new FileInputStream(file));
                short bufSize = 1024;
                byte[] buffer = new byte[bufSize];
                int len1;
                while (-1 != (len1 = in.read(buffer, 0, bufSize))) {
                    bos.write(buffer, 0, len1);
                }

                byte[] var7 = bos.toByteArray();
                return var7;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException var14) {
                    var14.printStackTrace();
                }

                bos.close();
            }
        }
    }




    public static File urlToFile(String imageUrl, String dstFilePath) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // 获取 Content-Type 来确定文件类型
            String contentType = connection.getContentType();
            String fileExtension = getFileExtensionFromContentType(contentType);

            if (fileExtension == null && imageUrl.contains(".")) {
                // 如果无法从 Content-Type 获取，则尝试从 URL 路径获取
                String path = url.getPath();
                fileExtension = path.substring(path.lastIndexOf("."));
            }

            if (fileExtension == null) {
                fileExtension = ".tmp"; // 默认后缀
            }

            InputStream inputStream = url.openStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            byte[] imageBytes = outputStream.toByteArray();

            // 写入本地文件，使用正确的扩展名
            File file = new File(dstFilePath + fileExtension);
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                fileOutputStream.write(imageBytes);
            }
            return file;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getFileExtensionFromContentType(String contentType) {
        if (contentType == null) return null;

        // 图片类型
        if (contentType.contains("image/jpeg")) return ".jpg";
        if (contentType.contains("image/png")) return ".png";
        if (contentType.contains("image/gif")) return ".gif";
        if (contentType.contains("image/webp")) return ".webp";

        // 视频类型
        if (contentType.contains("video/mp4")) return ".mp4";
        if (contentType.contains("video/quicktime")) return ".mov";
        if (contentType.contains("video/x-msvideo")) return ".avi";
        if (contentType.contains("video/webm")) return ".webm";

        // 音频类型
        if (contentType.contains("audio/mpeg")) return ".mp3";
        if (contentType.contains("audio/wav")) return ".wav";
        if (contentType.contains("audio/ogg")) return ".ogg";

        // 文档类型
        if (contentType.contains("application/pdf")) return ".pdf";
        if (contentType.contains("application/msword")) return ".doc";
        if (contentType.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            return ".docx";
        if (contentType.contains("application/vnd.ms-excel")) return ".xls";
        if (contentType.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            return ".xlsx";
        if (contentType.contains("application/vnd.ms-powerpoint")) return ".ppt";
        if (contentType.contains("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
            return ".pptx";

        // 其他常见类型
        if (contentType.contains("application/zip")) return ".zip";
        if (contentType.contains("application/x-rar-compressed")) return ".rar";
        if (contentType.contains("application/json")) return ".json";
        if (contentType.contains("text/plain")) return ".txt";
        if (contentType.contains("text/html")) return ".html";
        if (contentType.contains("application/xml") || contentType.contains("text/xml")) return ".xml";

        return null;
    }
}
