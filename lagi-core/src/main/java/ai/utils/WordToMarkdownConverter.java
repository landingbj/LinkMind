package ai.utils;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class WordToMarkdownConverter {

    /**
     * 将 Word 文档转换为 Markdown 字符串
     * @param filePath Word 文档路径
     * @return Markdown 字符串
     * @throws IOException IO异常
     * @throws InvalidFormatException 格式异常
     */
    public String convertToMarkdown(String filePath) throws IOException, InvalidFormatException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }

        if (filePath.toLowerCase().endsWith(".docx")) {
            return convertDocxToMarkdown(file);
        } else if (filePath.toLowerCase().endsWith(".doc")) {
            return convertDocToMarkdown(file);
        } else {
            throw new IllegalArgumentException("不支持的文件格式，仅支持 .doc 和 .docx");
        }
    }

    /**
     * 转换 docx 文档为 Markdown
     */
    private String convertDocxToMarkdown(File file) throws IOException, InvalidFormatException {
        try (InputStream is = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(OPCPackage.open(is))) {

            StringBuilder markdown = new StringBuilder();

            // 处理段落
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                processParagraph(paragraph, markdown);
            }

            // 处理表格
            List<XWPFTable> tables = document.getTables();
            for (XWPFTable table : tables) {
                processTable(table, markdown);
            }

            return markdown.toString().trim();
        }
    }

    /**
     * 转换 doc 文档为 Markdown
     * 注意：doc 格式处理相对简单，高级格式可能无法完全转换
     */
    private String convertDocToMarkdown(File file) throws IOException {
        try (InputStream is = new FileInputStream(file);
             HWPFDocument document = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(document)) {

            StringBuilder markdown = new StringBuilder();
            String[] paragraphs = extractor.getParagraphText();

            for (String paragraph : paragraphs) {
                if (paragraph != null && !paragraph.trim().isEmpty()) {
                    // 简单处理，实际应用中需要更复杂的逻辑
                    markdown.append(paragraph.trim()).append("\n\n");
                }
            }

            return markdown.toString().trim();
        }
    }

    private String repeat(String str, int times) {
        if (times <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 处理段落
     */
    private void processParagraph(XWPFParagraph paragraph, StringBuilder markdown) {
        String text = paragraph.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // 处理标题
        int headingLevel = getHeadingLevel(paragraph);
        if (headingLevel > 0 && headingLevel <= 6) {
            markdown.append( repeat("#", headingLevel)).append(" ").append(text).append("\n\n");
            return;
        }

        // 处理列表
        if (isListItem(paragraph)) {
            String bullet = getListBullet(paragraph);
            markdown.append(bullet).append(" ").append(text).append("\n");
            return;
        }

        // 普通段落
        markdown.append(text).append("\n\n");
    }

    /**
     * 处理表格
     */
    private void processTable(XWPFTable table, StringBuilder markdown) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return;
        }

        // 处理表头
        XWPFTableRow headerRow = rows.get(0);
        processTableRow(headerRow, markdown, true);

        // 表头分隔线
        markdown.append("|");
        for (int i = 0; i < headerRow.getTableCells().size(); i++) {
            markdown.append("---|");
        }
        markdown.append("\n");

        // 处理表体
        for (int i = 1; i < rows.size(); i++) {
            processTableRow(rows.get(i), markdown, false);
        }

        markdown.append("\n");
    }

    /**
     * 处理表格行
     */
    private void processTableRow(XWPFTableRow row, StringBuilder markdown, boolean isHeader) {
        markdown.append("|");
        for (XWPFTableCell cell : row.getTableCells()) {
            String cellText = cell.getText().replace("\n", " ").trim();
            markdown.append(cellText).append("|");
        }
        markdown.append("\n");
    }

    /**
     * 获取标题级别
     */
    private int getHeadingLevel(XWPFParagraph paragraph) {
        String styleName = paragraph.getStyle();
        if (styleName == null) {
            return 0;
        }

        if (styleName.startsWith("Heading")) {
            try {
                return Integer.parseInt(styleName.substring(7));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 判断是否为列表项
     */
    private boolean isListItem(XWPFParagraph paragraph) {
        return paragraph.getCTP().getPPr() != null
                && paragraph.getCTP().getPPr().getNumPr() != null;
    }

    /**
     * 获取列表符号
     */
    private String getListBullet(XWPFParagraph paragraph) {
        // 简化处理，实际应用中需要根据列表类型返回不同符号
        // 可以通过 paragraph.getCTP().getPPr().getNumPr() 获取更详细的列表信息
        return "-"; // 默认使用减号作为列表符号
    }
}