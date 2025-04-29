package ai.utils;

import ai.common.pojo.FileChunkResponse;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PptUtil {

    private static class SlideInfo {
        private String textContent;
        private String imagePath;

        public SlideInfo(String textContent, String imagePath) {
            this.textContent = textContent;
            this.imagePath = imagePath;
        }

        public String getTextContent() {
            return textContent;
        }

        public String getImagePath() {
            return imagePath;
        }

        @Override
        public String toString() {
            return "SlideInfo{textContent='" + textContent + "', imagePath='" + imagePath + "'}";
        }
    }
    public static List<SlideInfo> readPpt(String filePath) throws IOException {
        List<SlideInfo> slideInfoList = new ArrayList<>();
        File pptFile = new File(filePath);
        String outDir = pptFile.getPath().replace(".pptx", "").replace(".ppt", "");
        File outputFolder = new File(outDir);
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        try (FileInputStream fis = new FileInputStream(filePath)) {
            if (filePath.endsWith(".pptx")) {
                XMLSlideShow ppt = new XMLSlideShow(fis);
                List<XSLFSlide> slides = ppt.getSlides();
                for (int i = 0; i < slides.size(); i++) {
                    XSLFSlide slide = slides.get(i);
                    StringBuilder textContent = new StringBuilder();
                    for (XSLFShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFTextShape) {
                            XSLFTextShape textShape = (XSLFTextShape) shape;
                            textContent.append(textShape.getText()).append("\n");
                        }
                    }
                    int width = ppt.getPageSize().width;
                    int height = ppt.getPageSize().height;
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = img.createGraphics();
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    // 背景填充白色
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, width, height);
                    slide.draw(graphics);
                    String imagePath = outDir + "/slide_" + (i + 1) + ".png";
                    ImageIO.write(img, "PNG", new FileOutputStream(imagePath));
                    String normalizedPath = imagePath.replace("\\", "/");
                    String basePath = normalizedPath.substring(normalizedPath.indexOf("/upload"));
                    SlideInfo slideInfo = new SlideInfo(textContent.toString(), basePath);
                    slideInfoList.add(slideInfo);
                }
            } else if (filePath.endsWith(".ppt")) {
                HSLFSlideShow ppt = new HSLFSlideShow(fis);
                List<HSLFSlide> slides = ppt.getSlides();
                for (int i = 0; i < slides.size(); i++) {
                    HSLFSlide slide = slides.get(i);
                    StringBuilder textContent = new StringBuilder();
                    for (Object shapeObj : slide.getShapes()) {
                        if (shapeObj instanceof org.apache.poi.hslf.usermodel.HSLFTextShape) {
                            org.apache.poi.hslf.usermodel.HSLFTextShape textShape = (org.apache.poi.hslf.usermodel.HSLFTextShape) shapeObj;
                            textContent.append(textShape.getText()).append("\n");
                        }
                    }
                    int width = ppt.getPageSize().width;
                    int height = ppt.getPageSize().height;
                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = img.createGraphics();
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, width, height);
                    slide.draw(graphics);
                    String imagePath = outDir + "/slide_" + (i + 1) + ".png";
                    ImageIO.write(img, "PNG", new FileOutputStream(imagePath));
                    String normalizedPath = imagePath.replace("\\", "/");
                    String basePath = normalizedPath.substring(normalizedPath.indexOf("/upload"));
                    SlideInfo slideInfo = new SlideInfo(textContent.toString(), basePath);
                    slideInfoList.add(slideInfo);
                }
            }
        }
        return slideInfoList;
    }

    public static String getPptContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file)) {
            if (file.getName().endsWith(".pptx")) {
                XMLSlideShow ppt = new XMLSlideShow(fis);
                List<XSLFSlide> slides = ppt.getSlides();
                for (XSLFSlide slide : slides) {
                    for (XSLFShape shape : slide.getShapes()) {
                        if (shape instanceof XSLFTextShape) {
                            XSLFTextShape textShape = (XSLFTextShape) shape;
                            content.append(textShape.getText()).append("\n");
                        }
                    }
                }
            } else if (file.getName().endsWith(".ppt")) {
                HSLFSlideShow ppt = new HSLFSlideShow(fis);
                List<HSLFSlide> slides = ppt.getSlides();
                for (HSLFSlide slide : slides) {
                    for (Object shapeObj : slide.getShapes()) {
                        if (shapeObj instanceof org.apache.poi.hslf.usermodel.HSLFTextShape) {
                            org.apache.poi.hslf.usermodel.HSLFTextShape textShape = (org.apache.poi.hslf.usermodel.HSLFTextShape) shapeObj;
                            content.append(textShape.getText()).append("\n");
                        }
                    }
                }
            }
        }
        return content.toString();
    }

    public static List<FileChunkResponse.Document> getChunkDocumentPpt(File file, Integer pageSize) throws IOException {
        List<FileChunkResponse.Document> result = new ArrayList<>();
        List<SlideInfo> slideInfoList = readPpt(file.getPath());
        for (SlideInfo slideInfo : slideInfoList) {
            String msg = slideInfo.getTextContent().replaceAll("\\n+", "\n");
            int start = 0;
            while (start < msg.length()) {
                int end = Math.min(start + pageSize, msg.length());
                int lastSentenceEnd = Math.max(msg.lastIndexOf('.', end), msg.lastIndexOf('\n', end));
                if (lastSentenceEnd != -1 && lastSentenceEnd > start) {
                    end = lastSentenceEnd + 1;
                }
                String text = msg.substring(start, end).replaceAll("\\s+", " ");
                FileChunkResponse.Document doc = new FileChunkResponse.Document();
                doc.setText(text);
                List<FileChunkResponse.Image> list = new ArrayList<>();
                FileChunkResponse.Image image = new FileChunkResponse.Image();
                image.setPath(slideInfo.getImagePath());
                list.add(image);
                doc.setImages(list);
                result.add(doc);
                start = end;
            }

        }
        return result;
    }
    public static void main(String[] args) throws IOException {
        //"C:\Users\ruiqing.luo\Desktop\rag调优\政务数据\营业前消防安全检查.pptx"
        String filePath = "C:\\Users\\ruiqing.luo\\Desktop\\中移-大模型中间件交流演示\\中移集成-交流演示.pptx";
        List<FileChunkResponse.Document> rr= getChunkDocumentPpt(new File(filePath), 512);
        for (FileChunkResponse.Document document : rr) {
            System.out.println(document.getText());
            System.out.println(document.getImages());
            System.out.println("------------------------------");
        }
//        for (SlideInfo slideInfo : readPpt(filePath)) {
//            System.out.println(slideInfo.getTextContent().replaceAll("\\n+", "\n"));
//            System.out.println(slideInfo.getImagePath());
//        }
    }

}
