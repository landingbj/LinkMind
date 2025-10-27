package ai.vector.loader.impl;

import ai.common.pojo.FileChunkResponse;
import ai.common.pojo.Response;
import ai.utils.ChapterExtractorUtil;
import ai.utils.OrdinanceExtractorUtil;
import ai.utils.QaExtractorUtil;
import ai.utils.SectionExtractorUtil;
import ai.utils.pdf.PdfUtil;
import ai.vector.FileService;
import ai.vector.loader.DocumentLoader;
import ai.vector.loader.pojo.SplitConfig;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class PdfLoader implements DocumentLoader {
    @Override
    public List<List<FileChunkResponse.Document>> load(String path, SplitConfig splitConfig) {
        List<FileChunkResponse.Document> documents = docLoad(path, splitConfig);
        List<List<FileChunkResponse.Document>> res = new ArrayList<>();
        if (documents != null && !documents.isEmpty()) {
            res.add(documents);
        }
        return res;
    }

    public List<FileChunkResponse.Document> docLoad(String path, SplitConfig splitConfig) {
        File file = new File(path);
        FileService fileService = new FileService();
        try {
            String content = null;
            InputStream in = Files.newInputStream(file.toPath());
            content = PdfUtil.webPdfParse(in);
            if (content == null || content.trim().isEmpty()) {
                System.out.println("扫描件");
                return FileService.getChunkDocumentScannedPDF(file, splitConfig.getChunkSizeForMixUp());
            }
            if (hasImages(file)) {
                FileChunkResponse response = fileService.extractContent(file);
                if (response != null && response.getStatus().equals("success")) {
                    return response.getData();
                }
            }
            Response response = fileService.toMarkdown(file);
            if (response != null && response.getStatus().equals("success")) {
                content = response.getData();
                content = content != null ? FileService.removeDirectory(content) : content;
            } else {
                content = content
                        .replaceAll("(\r?\n){2,}", "\n")
                        .replaceAll("(?<=\r?\n)\\s*", "")
                        .replaceAll("(?<![.!?;:。！？；：\\s\\d])\r?\n", "");
                if (StrUtil.isNotBlank(content)) {
                    content = FileService.removeDirectory(content);
                } else {
                    return FileService.getChunkDocumentScannedPDF(file, splitConfig.getChunkSizeForMixUp());
                }
            }
            // qa 对
            if (QaExtractorUtil.extractQA(content, splitConfig.getCategory(), splitConfig.getExtra(), splitConfig.getChunkSizeForText())) {
                return Collections.emptyList();
            }

            if (ChapterExtractorUtil.isChapterDocument(content)) {
                return ChapterExtractorUtil.getChunkDocument(content, splitConfig.getChunkSizeForText());
            } else if (SectionExtractorUtil.isChapterDocument(content, splitConfig.getChunkSizeForText())) {
                return SectionExtractorUtil.getChunkDocument(content, splitConfig.getChunkSizeForText());
            } else if (OrdinanceExtractorUtil.isOrdinanceDocument(content)) {
                return OrdinanceExtractorUtil.getChunkDocument(content, splitConfig.getChunkSizeForText());
            } else {
                return fileService.splitContentChunks(splitConfig.getChunkSizeForText(), content);
            }
        } catch (Exception e) {
            log.error("load pdf file error", e);
        }
        return Collections.emptyList();
    }

    public static boolean hasImages(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                for (COSName xObjectName : resources.getXObjectNames()) {
                    if (resources.isImageXObject(xObjectName)) {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(xObjectName);
                        if (image != null) {
                            return true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
