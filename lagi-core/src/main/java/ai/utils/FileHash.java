package ai.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHash {

    public static void main(String[] args) {
        String pdfPath = "/Users/username/Documents/employee-training.pdf"; // 替换为你的文件路径
        try {
            String sha256 = calculateSHA256(pdfPath);
            System.out.println("SHA-256: " + sha256);
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * 计算文件的SHA-256值（推荐使用）
     *
     * @param filePath 文件路径
     * @return SHA-256字符串
     * @throws IOException 如果读取文件失败
     * @throws NoSuchAlgorithmException 如果没有找到算法
     */
    public static String calculateSHA256(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] dataBytes = new byte[4096];
            int nread = 0;
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }
        }
        byte[] mdbytes = md.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte mdbyte : mdbytes) {
            String hex = Integer.toHexString(0xFF & mdbyte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 计算文件的MD5值（已废弃，仅用于兼容旧代码，新代码请使用calculateSHA256）
     *
     * @param filePath 文件路径
     * @return MD5字符串
     * @throws IOException 如果读取文件失败
     * @throws NoSuchAlgorithmException 如果没有找到算法
     * @deprecated Use {@link #calculateSHA256(String)} instead
     */
    @Deprecated
    public static String calculateMD5(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] dataBytes = new byte[4096];
            int nread = 0;
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }
        }
        byte[] mdbytes = md.digest();

        StringBuilder hexString = new StringBuilder();
        for (byte mdbyte : mdbytes) {
            String hex = Integer.toHexString(0xFF & mdbyte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
