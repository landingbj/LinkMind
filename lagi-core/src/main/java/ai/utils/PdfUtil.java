package ai.utils;

import ai.common.pojo.TextBlock;
import ai.vector.FileService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;


@Slf4j
public class PdfUtil {

    private static final Lock convertLock = new ReentrantLock();
    private static final String SOFFICE_PATH = "D:\\Tools\\LibreOffice\\program\\soffice.exe";
//    private static final String SOFFICE_PATH = "soffice";
    private static final int ZOOM_SIZE = 1;
    private static final float DPI = 300;

    public static final LRUCache<String, String> pdfStrCache = new LRUCache<>(100);
    public static final LRUCache<String, List<TextBlock>> pdfTextBlockCache = new LRUCache<>(100);
    public static ReentrantLock lock = new ReentrantLock();

    public static String convertToPdf(String docPath, String pdfPath) {
        try {
            convertLock.lock();
            int exitCode = getExitCode(docPath, pdfPath);

            if (exitCode == 0) {
                System.out.println("Conversion successful.");
            } else {
                System.err.println("Conversion failed with exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            convertLock.unlock();
        }

        return pdfPath;
    }

    private static int getExitCode(String docPath, String pdfPath) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                SOFFICE_PATH,
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                pdfPath,
                docPath
        );

        processBuilder.redirectErrorStream(true);

        Process process = null;
        try {
            process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }




    public static List<TextBlock> getWordsCoordinateByPath(String pdfPath, String searchWord) {
        List<TextBlock>  data = new ArrayList<>();
        try {
            PDDocument document = PDDocument.load(new File(pdfPath));
            PDFTextExtractor extractor = new PDFTextExtractor(searchWord, false);
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            extractor.setSortByPosition(true);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                extractor.setStartPage(i + 1);
                extractor.setEndPage(i + 1);
                pdfTextStripper.setStartPage(i+1);
                pdfTextStripper.setEndPage(i+1);
                String key = pdfPath + "-" + i;
                String text = pdfStrCache.get(key);
                if(text == null) {
                    text = pdfTextStripper.getText(document).replaceAll("\\s+", "");
                    pdfStrCache.put(key, text);
                }
                extractor.setCursorPoint(0);
                extractor.setCurrentPageText(text);
                extractor.getText(document);
                boolean done = extractor.getDone();
                if(done) {
                    return extractor.getTextBlocks();
                }
            }
            extractor.getTextBlocks().clear();
            document.close();
            return data;
        } catch (IOException e) {
            log.error("Error reading PDF file: {}", e.getMessage());
        }
        return data;
    }

    public static List<List<TextBlock>> getAllWordsCoordinateByPath(String pdfPath, String searchWord) {
        List<List<TextBlock>>  data = new ArrayList<>();
        try {
            PDDocument document = PDDocument.load(new File(pdfPath));
            PDFTextExtractor extractor = new PDFTextExtractor(searchWord, true);
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            extractor.setSortByPosition(true);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                extractor.setStartPage(i + 1);
                extractor.setEndPage(i + 1);
                pdfTextStripper.setStartPage(i+1);
                pdfTextStripper.setEndPage(i+1);
                String key = pdfPath + "-" + i;
                String text = pdfStrCache.get(key);
                if(text == null) {
                    text =pdfTextStripper.getText(document).replaceAll("\\s+", "");
                    pdfStrCache.put(key, text);
                }
                extractor.setCursorPoint(0);
                extractor.setCurrentPageText(text);
                extractor.getText(document);
            }
            document.close();
            return extractor.getResults();
        } catch (IOException e) {
            log.error("Error reading PDF file: {}", e.getMessage());
        }
        return data;
    }

    private static List<List<Float>> convert(List<TextBlock> blocks) {
        return blocks.stream().map(block -> {
            float pageNo = (float) block.getPageNo();
            List<Float> list = new ArrayList<>();
            list.add(block.getX());
            list.add(block.getY());
            list.add(pageNo);
            return list;
        }).collect(Collectors.toList());
    }

    public static List<List<TextBlock>> searchFromCache1(String path, String searchWord, boolean isAll) {
        List<List<TextBlock>> res = new ArrayList<>();
        List<TextBlock> textBlockFromCache = getTextBlockFromCache(path);
        StringBuilder read = new StringBuilder();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < textBlockFromCache.size(); i++) {
            String text = textBlockFromCache.get(i).getText();
            read.append(text);
            counts.add(text.length());
            if(read.toString().contains(searchWord)) {
                int countLen = 0;
                int j = counts.size() - 1;
                int m = 0;
                for (; j >= 0; j--) {
                    countLen += counts.get(j);
                    m ++;
                    if(countLen >= searchWord.length()) {
                        break;
                    }
                }
                List<TextBlock> textBlocks = textBlockFromCache.subList(i + 1 - m, i + 1);
                res.add(textBlocks);
                counts.clear();
                read = new StringBuilder();
                if(!isAll) {
                    break;
                }
            }
        }
        return res;
    }

        public static List<List<TextBlock>> searchFromCache(String path, String searchWord, boolean isAll) {
            List<List<TextBlock>> res = new ArrayList<>();
            List<TextBlock> textBlockFromCache = getTextBlockFromCache(path);
            StringBuilder read = new StringBuilder();
            List<Integer> counts = new ArrayList<>();
            StringBuilder read1 = new StringBuilder();
            for (int i = 0; i < textBlockFromCache.size(); i++) {
                String text = textBlockFromCache.get(i).getText();
                read1.append(text);
            }
            String gonggong = findMaxCommonSubstring(read1.toString(), searchWord);

            if (gonggong != null && gonggong.length() >= searchWord.length() * 0.6) {
                return searchFromCache1(path, gonggong, isAll);
            } else {
                return res;
            }
        }

    // 找到最长的公共子串
    private static String findMaxCommonSubstring(String text, String searchWord) {
        // 截取前60%与后60%的部分
        int frontLength = (int) (searchWord.length() * 0.3);
        String frontSubstring = searchWord.substring(0, frontLength);
        String backSubstring = searchWord.substring(searchWord.length() - frontLength);

        int[] frontPosition = findMaxCommonSubstringPosition(text, frontSubstring);
        int[] backPosition = findMaxCommonSubstringPosition(text, backSubstring);

        // 如果任何一个部分没有找到匹配的子串，则直接返回空字符串
        if (frontPosition[0] == -1 || backPosition[0] == -1) {
            return "";
        }

        int minStart = Math.min(frontPosition[0], backPosition[0]);
        int maxEnd = Math.max(frontPosition[1], backPosition[1]);

        return text.substring(minStart, maxEnd + 1);
    }

    // 找到最大公共子串以及它在text中的位置
    private static int[] findMaxCommonSubstringPosition(String text, String searchWord) {
        int textLength = text.length();
        int searchWordLength = searchWord.length();

        // dp[i][j] 表示 text[0..i-1] 与 searchWord[0..j-1] 的公共子串的长度
        int[][] dp = new int[textLength + 1][searchWordLength + 1];
        int maxLength = 0;
        int endIndexInText = -1;

        // 填充 dp 表格，找到最大公共子串
        for (int i = 1; i <= textLength; i++) {
            for (int j = 1; j <= searchWordLength; j++) {
                if (text.charAt(i - 1) == searchWord.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    if (dp[i][j] > maxLength) {
                        maxLength = dp[i][j];
                        endIndexInText = i - 1;  // 记录最大公共子串的结束位置
                    }
                }
            }
        }
        if (maxLength > 0) {
            int startIndexInText = endIndexInText - maxLength + 1;
            return new int[] {startIndexInText, endIndexInText};
        }
        return new int[] {-1, -1};
    }

        public static List<TextBlock> getTextBlockFromCache(String pdfPath){
        List<TextBlock>  textBlocks = new ArrayList<>();
        if(pdfTextBlockCache.containsKey(pdfPath)) {
            return pdfTextBlockCache.get(pdfPath);
        }
        try {
            boolean b = lock.tryLock(30, TimeUnit.SECONDS);
            if(!b) {
                throw new RuntimeException("lock timeout");
            }
            if(pdfTextBlockCache.containsKey(pdfPath)) {
                return pdfTextBlockCache.get(pdfPath);
            }
            FileService fileService = new FileService();
            try {
                File file = new File(pdfPath);
                PDDocument document = PDDocument.load(file);
                String fileContent = fileService.getFileContent(file);
                PDFTextBlockExtractor pdfTextBlockExtractor = new PDFTextBlockExtractor(fileContent);
                pdfTextBlockExtractor.setSortByPosition(true);
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    pdfTextBlockExtractor.setStartPage(i + 1);
                    pdfTextBlockExtractor.setEndPage(i + 1);
                    pdfTextBlockExtractor.getText(document);
                }
                textBlocks = pdfTextBlockExtractor.getTextBlocks();
                document.close();
                pdfTextBlockCache.put(pdfPath, textBlocks);
            } catch (IOException e) {
                log.error("get text block from cache error {}", pdfPath, e);
            }

        } catch (InterruptedException e) {
            log.error("get lock error {}", pdfPath, e);
        } finally {
            lock.unlock();
        }
        return textBlocks;
    }

    public static String getCroppedPageImage(String pdfPath, String pageDir, int pageIndex, int x0, int y0, int x1, int y1) throws IOException {
        // 加载PDF文档
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {

            // 创建PDF渲染器
            PDFRenderer renderer = new PDFRenderer(document);

            // 渲染页面为图像
//            BufferedImage fullPageImage = renderer.renderImageWithDPI(pageIndex, DPI );
            BufferedImage fullPageImage = renderer.renderImage(pageIndex, ZOOM_SIZE);
            int width = fullPageImage.getWidth();
            int height = fullPageImage.getHeight();
            x0 = Math.max(x0, 0);
            y0 = Math.max(y0, 0);
            x1 = Math.min(width, x1);
            y1 = Math.min(height, y1);
//            // 计算裁剪区域
            int croppedWidth = (x1 - x0) * ZOOM_SIZE ;
            int croppedHeight = (y1 - y0) * ZOOM_SIZE;
            int croppedX = x0 * ZOOM_SIZE ;
            int croppedY = y0 * ZOOM_SIZE;

//            // 计算裁剪区域
//            int croppedWidth = (x1 - x0) ;
//            int croppedHeight = (y1 - y0) ;
//            int croppedX = x0 ;
//            int croppedY = y0 ;
            String pageImagePath = pageDir + "/" + (pageIndex + 1) + "_" + x0 + "_" + y0 + "_" + x1 + "_" + y1 + ".png";
            System.out.println(pageImagePath);
            // 裁剪图像
            BufferedImage croppedImage = fullPageImage.getSubimage(croppedX, croppedY, croppedWidth, croppedHeight);
            System.out.println(pageImagePath + "    finished");
            // 保存图像
            File pageFile = new File(pageImagePath);
            ImageIO.write(croppedImage, "png", pageFile);
            return pageImagePath;
        }
    }

    public static void main(String[] args) {
//        String keyword = "本标准代替Q/BJCE-204.03-06-2019《员工培训管理办法》，自本标准实施之日起，原标\n" +
//                "准作废。\n" +
//                "本标准的附录A、附录B、附录C、附录D、附录E、附录F、附录G为规范性附录。\n" +
//                "本标准由清洁能源公司党委组织部（人力资源部）提出并归口管理。\n" +
//                "本标准起草部室：党委组织部（人力资源部）\n" +
//                "本标准起草人：马宴维\n" +
//                "本标准修改人：王晓东\n" +
//                "本标准审核人：曲 展\n" +
//                "本标准复核人：曹满胜\n" +
//                "本标准批准人：张凤阳\n" +
//                "本标准于2011年12月31日首次发布，2013年10月第一次修编，2016年7月第二次修编，2019\n" +
//                "年11月第三次修编,2022年3月第四次修编。\n" +
//                "人力资源管理 Q/BJCE-204.03-06-2022\n" +
//                "1\n" +
//                "员工培训管理办法\n" +
//                "1 范围\n" +
//                "本标准规定了清洁能源公司培训管理的职责、管理内容与方法、报告和记录、检查与考\n" +
//                "核。\n" +
//                "本标准适用于清洁能源公司培训管理工作。\n" +
//                "2 规范性引用文件\n" +
//                "下列文件对于本文件的应用是必不可少的。凡是注日期的引用文件，仅注日期的版本适用\n" +
//                "于本文件。凡是不注日期的引用文件，其最新版本（包括所有的修改单）适用于本文件。";
//        String keyword =  "轴控箱1（2、3）的电池箱温度1分钟平均值超过60℃（回滞温度50℃）";
        String keyword =  "1、出现最大值一";
//        keyword = keyword.replaceAll("\\s+", "");
//        List<List<TextBlock>> allWordsCoordinateByPath = getAllWordsCoordinateByPath("C:\\Users\\Administrator\\Desktop\\京能\\风力发电机组故障处理手册.pdf", keyword);
//        System.out.println(allWordsCoordinateByPath.size());
        String filepath = "C:\\Users\\Administrator\\Desktop\\京能\\风力发电机组故障处理手册.pdf";
        long start = System.currentTimeMillis();
        List<List<TextBlock>> lists = searchFromCache(filepath, keyword, true);
        long end = System.currentTimeMillis();
//        long start1 = System.currentTimeMillis();
//        lists = searchFromCache(filepath, keyword, true);
//        long end1 = System.currentTimeMillis();
//        long start2 = System.currentTimeMillis();
//        lists = searchFromCache(filepath, keyword, false);
//        long end2 = System.currentTimeMillis();
        System.out.println("1 cost :" + (end -start));
//        System.out.println("2 cost :" + (end1 -start1));
//        System.out.println("3 cost :" + (end2 -start2));
        lists.forEach(list -> list.forEach(System.out::println));
    }
}
