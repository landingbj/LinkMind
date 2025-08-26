package ai.servlet.passenger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 图片转视频工具类
 * 用于将乘客图片集合转换为视频文件
 */
public class ImageToVideoConverter {
    
    /**
     * 将图片URL列表转换为视频文件
     * @param imageUrls 图片URL列表
     * @param outputDir 输出目录
     * @param frameRate 帧率，默认1fps
     * @return 生成的视频文件
     * @throws IOException 转换异常
     */
    public static File convertImagesToVideo(List<String> imageUrls, String outputDir, int frameRate) throws IOException {
        System.out.println("[FFmpeg转换] 开始转换图片为视频，图片数量: " + imageUrls.size() + ", 输出目录: " + outputDir + ", 帧率: " + frameRate);
        
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new IllegalArgumentException("图片URL列表不能为空");
        }
        
        // 创建输出目录
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        // 生成输出文件名
        String outputFileName = "passenger_video_" + UUID.randomUUID().toString() + ".mp4";
        File outputFile = new File(outputDirectory, outputFileName);
        
        System.out.println("[FFmpeg转换] 输出文件路径: " + outputFile.getAbsolutePath());
        
        try {
            // 使用FFmpeg将图片转换为视频
            // 这里需要系统安装FFmpeg
            String ffmpegCommand = buildFFmpegCommand(imageUrls, outputFile.getAbsolutePath(), frameRate);
            
            System.out.println("[FFmpeg转换] 开始创建临时图片目录并下载图片");
            File tempDir = createTempImageDirectory(imageUrls);
            System.out.println("[FFmpeg转换] 临时图片目录创建完成: " + tempDir.getAbsolutePath());
            
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("ffmpeg", "-y", "-framerate", String.valueOf(frameRate), 
                                 "-i", "%d.jpg", "-c:v", "libx264", "-pix_fmt", "yuv420p", 
                                 outputFile.getAbsolutePath());
            
            // 设置工作目录为临时图片目录
            processBuilder.directory(tempDir);
            
            System.out.println("[FFmpeg转换] 开始执行FFmpeg命令，工作目录: " + tempDir.getAbsolutePath());
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                System.err.println("[FFmpeg转换] FFmpeg转换失败，退出码: " + exitCode);
                throw new IOException("FFmpeg转换失败，退出码: " + exitCode);
            }
            
            System.out.println("[FFmpeg转换] FFmpeg转换成功，退出码: " + exitCode);
            
            // 清理临时文件
            cleanupTempFiles(tempDir);
            
            return outputFile;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("视频转换被中断", e);
        }
    }
    
    /**
     * 将图片URL列表转换为视频文件（使用默认帧率1fps）
     * @param imageUrls 图片URL列表
     * @param outputDir 输出目录
     * @return 生成的视频文件
     * @throws IOException 转换异常
     */
    public static File convertImagesToVideo(List<String> imageUrls, String outputDir) throws IOException {
        return convertImagesToVideo(imageUrls, outputDir, 1);
    }
    
    /**
     * 构建FFmpeg命令
     * @param imageUrls 图片URL列表
     * @param outputPath 输出文件路径
     * @param frameRate 帧率
     * @return FFmpeg命令字符串
     */
    private static String buildFFmpegCommand(List<String> imageUrls, String outputPath, int frameRate) {
        StringBuilder command = new StringBuilder();
        command.append("ffmpeg -y -framerate ").append(frameRate);
        command.append(" -i %d.jpg -c:v libx264 -pix_fmt yuv420p ");
        command.append(outputPath);
        return command.toString();
    }
    
    /**
     * 创建临时图片目录并下载图片
     * @param imageUrls 图片URL列表
     * @return 临时目录
     * @throws IOException 创建异常
     */
    private static File createTempImageDirectory(List<String> imageUrls) throws IOException {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "passenger_images_" + UUID.randomUUID().toString());
        tempDir.mkdirs();
        
        // 下载图片到临时目录
        for (int i = 0; i < imageUrls.size(); i++) {
            String imageUrl = imageUrls.get(i);
            File imageFile = new File(tempDir, (i + 1) + ".jpg");
            downloadImage(imageUrl, imageFile);
        }
        
        return tempDir;
    }
    
    /**
     * 下载图片文件
     * @param imageUrl 图片URL
     * @param outputFile 输出文件
     * @throws IOException 下载异常
     */
    private static void downloadImage(String imageUrl, File outputFile) throws IOException {
        try (java.io.InputStream in = new java.net.URL(imageUrl).openStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(outputFile)) {
            
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
    
    /**
     * 清理临时文件
     * @param tempDir 临时目录
     */
    private static void cleanupTempFiles(File tempDir) {
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            tempDir.delete();
        }
    }
}
