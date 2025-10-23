package ai.utils;


import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MarkdownToDocxConverter {

    private final Parser parser;

    public MarkdownToDocxConverter() {
        // 配置解析器以支持表格等GFM扩展
        List<Extension> extensions = Arrays.asList(TablesExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
    }

    /**
     * 将Markdown字符串转换为DOCX文档字节数组
     * @param markdownContent Markdown字符串内容
     * @return DOCX文档的字节数组
     * @throws IOException 如果处理过程中发生错误
     */
    public byte[] convert(String markdownContent) throws IOException {
        if (markdownContent == null || markdownContent.isEmpty()) {
            throw new IllegalArgumentException("Markdown content cannot be null or empty");
        }

        Node document = parser.parse(markdownContent);
        XWPFDocument doc = new XWPFDocument();
        processNode(document, doc);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        doc.close();
        return out.toByteArray();
    }

    /**
     * 将Markdown字符串转换并保存为DOCX文件
     * @param markdownContent Markdown字符串内容
     * @param filePath 输出的文件路径
     * @throws IOException 如果处理过程中发生错误
     */
    public void convertToFile(String markdownContent, String filePath) throws IOException {
        byte[] docxBytes = convert(markdownContent);

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(docxBytes);
        }
    }

    /**
     * 递归处理Markdown AST节点并添加到Word文档
     */
    private void processNode(Node node, XWPFDocument doc) {
        while (node != null) {
            if (node instanceof org.commonmark.node.Document) {
                processChildren(node, doc);
            } else if (node instanceof Paragraph) {
                processParagraph(node, doc);
            } else if (node instanceof Heading) {
                processHeading((Heading) node, doc);
            } else if (node instanceof BulletList || node instanceof OrderedList) {
                processList((ListBlock) node, doc);
            } else if (node instanceof Code) {
                processCode((Code) node, doc);
            } else if (node instanceof ThematicBreak) {
                processThematicBreak(doc);
            } else if (node instanceof TableBlock) {
                processTableBlock((TableBlock) node, doc);
            } else {
                // 忽略其他类型或处理为普通文本
                addTextToParagraph(doc, node, null);
            }
            node = node.getNext();
        }
    }

    private void processChildren(Node parent, XWPFDocument doc) {
        Node child = parent.getFirstChild();
        if (child != null) {
            processNode(child, doc);
        }
    }

    private void processParagraph(Node paragraph, XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        Node child = paragraph.getFirstChild();
        if (child != null) {
            applyInlineFormatting(child, para);
        }
    }

    private void processHeading(Heading heading, XWPFDocument doc) {
        int level = heading.getLevel();
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setBold(true);
        run.setFontSize(16 - (level - 1) * 2); // 模拟标题层级大小
        Node child = heading.getFirstChild();
        if (child != null) {
            appendText(run, child);
        }
    }

    private void processList(ListBlock list, XWPFDocument doc) {
        Node child = list.getFirstChild();
        while (child != null) {
            if (child instanceof ListItem) {
                // 列表项处理
                XWPFParagraph para = doc.createParagraph();
                String prefix = (list instanceof BulletList) ? "• " : "o "; // 简单符号
                XWPFRun run = para.createRun();
                run.setText(prefix);
                Node listItemChild = child.getFirstChild();
                if (listItemChild != null) {
                    applyInlineFormatting(listItemChild, para);
                }
            }
            child = child.getNext();
        }
    }

    private void processCode(Code code, XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setText(code.getLiteral());
        run.setFontFamily("Courier New");
        run.setBold(true);
    }

    private void processThematicBreak(XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setText("────────────────────────────────────────");
        run.addBreak();
    }

    private void processTableBlock(TableBlock tableBlock, XWPFDocument doc) {
        XWPFTable xwpfTable = doc.createTable(); // 创建空表格

        Node child = tableBlock.getFirstChild();
        while (child != null) {
            if (child instanceof TableRow) {
                // 处理行（在GFM表格扩展中，TableBlock直接包含TableRow）
                XWPFTableRow row = xwpfTable.createRow();
                Node cellNode = child.getFirstChild();

                // 检查是否是第一行来决定是否为表头
                boolean isHeaderRow = (tableBlock.getFirstChild() == child);

                while (cellNode != null) {
                    if (cellNode instanceof TableCell) {
                        XWPFTableCell cell = row.addNewTableCell();
                        Node textNode = cellNode.getFirstChild();
                        if (textNode != null) {
                            XWPFParagraph para = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
                            XWPFRun run = para.createRun();
                            if (isHeaderRow) {
                                run.setBold(true); // 表头加粗
                            }
                            appendText(run, textNode);
                        }
                    }
                    cellNode = cellNode.getNext();
                }
            }
            child = child.getNext();
        }
    }

    private void addTextToParagraph(XWPFDocument doc, Node node, XWPFParagraph existingPara) {
        XWPFParagraph para = existingPara != null ? existingPara : doc.createParagraph();
        XWPFRun run = para.createRun();
        appendText(run, node);
    }

    private void appendText(XWPFRun run, Node node) {
        if (node instanceof Text) {
            run.setText(((Text) node).getLiteral());
        } else if (node instanceof SoftLineBreak) {
            run.addBreak();
        } else if (node instanceof HardLineBreak) {
            run.addBreak(BreakType.TEXT_WRAPPING);
        } else {
            Node child = node.getFirstChild();
            while (child != null) {
                appendText(run, child);
                child = child.getNext();
            }
        }
    }

    private void applyInlineFormatting(Node node, XWPFParagraph para) {
        XWPFRun currentRun = para.createRun();
        applyInlineFormattingRecursive(node, currentRun);
    }

    private void applyInlineFormattingRecursive(Node node, XWPFRun baseRun) {
        if (node instanceof Text) {
            baseRun.setText(((Text) node).getLiteral());
        } else if (node instanceof Emphasis) {
            XWPFRun emphasisRun = baseRun.getParagraph().createRun();
            emphasisRun.setItalic(true);
            Node child = node.getFirstChild();
            while (child != null) {
                applyInlineFormattingRecursive(child, emphasisRun);
                child = child.getNext();
            }
        } else if (node instanceof StrongEmphasis) {
            XWPFRun strongRun = baseRun.getParagraph().createRun();
            strongRun.setBold(true);
            Node child = node.getFirstChild();
            while (child != null) {
                applyInlineFormattingRecursive(child, strongRun);
                child = child.getNext();
            }
        } else if (node instanceof Code) {
            XWPFRun codeRun = baseRun.getParagraph().createRun();
            codeRun.setFontFamily("Courier New");
            codeRun.setBold(true);
            codeRun.setText(((Code) node).getLiteral());
        } else {
            Node child = node.getFirstChild();
            while (child != null) {
                applyInlineFormattingRecursive(child, baseRun);
                child = child.getNext();
            }
        }
    }

    // 使用示例
    public static void main(String[] args) {
        try {
            String markdownContent = "# 工作流流程信息提取\n" +
                    "\n" +
                    "## 任务说明\n" +
                    "你是一个专业的流程分析助手。你的任务是将用户的自然语言描述转换为结构化的流程描述。\n" +
                    "\n" +
                    "## 输入\n" +
                    "用户会提供一段自然语言描述，描述他们想要实现的工作流程。\n" +
                    "\n" +
                    "## 输出要求\n" +
                    "请将用户的描述转换为结构化的流程描述，包含以下信息：\n" +
                    "\n" +
                    "### 1. 流程目标\n" +
                    "- 简要说明该工作流要实现什么功能\n" +
                    "\n" +
                    "### 2. 流程步骤\n" +
                    "按顺序列出具体的步骤，每个步骤需要说明：\n" +
                    "- **步骤序号**：从1开始编号\n" +
                    "- **步骤名称**：用简短的名称描述这个步骤\n" +
                    "- **步骤描述**：详细说明该步骤的具体操作\n" +
                    "- **输入信息**：该步骤需要什么输入（如用户query、上一步的输出、特定参数等）\n" +
                    "- **输出信息**：该步骤产生什么输出\n" +
                    "- **特殊说明**：如果有条件判断、循环等特殊逻辑，需要明确说明\n" +
                    "\n" +
                    "### 3. 数据流转\n" +
                    "说明数据在各个步骤之间如何流转，哪些步骤的输出会作为后续步骤的输入。\n" +
                    "\n" +
                    "### 4. 特殊逻辑\n" +
                    "如果流程中包含以下特殊逻辑，需要明确说明：\n" +
                    "- **条件判断**：什么条件下执行什么分支\n" +
                    "- **循环处理**：需要遍历什么数据，每次迭代做什么\n" +
                    "\n" +
                    "## 输出格式\n" +
                    "请使用清晰的结构化格式输出，使用标题、列表等markdown格式，确保易于理解和解析。\n" +
                    "\n" +
                    "## 示例\n" +
                    "\n" +
                    "### 输入\n" +
                    "\"用户输入问题后，先识别意图，如果是技术问题就搜索知识库，然后把知识库的结果传给LLM生成回答，如果是闲聊就直接用LLM回复\"\n" +
                    "\n" +
                    "### 输出\n" +
                    "```\n" +
                    "## 流程目标\n" +
                    "实现智能问答系统，根据用户问题类型选择不同的处理路径\n" +
                    "\n" +
                    "## 流程步骤\n" +
                    "\n" +
                    "**步骤1：接收用户输入**\n" +
                    "- 步骤名称：用户输入\n" +
                    "- 步骤描述：接收并处理用户的问题\n" +
                    "- 输入信息：用户的query文本\n" +
                    "- 输出信息：原始用户query\n" +
                    "- 特殊说明：这是流程的起始节点\n" +
                    "\n" +
                    "**步骤2：意图识别**\n" +
                    "- 步骤名称：识别用户意图\n" +
                    "- 步骤描述：分析用户的问题类型，判断是技术问题还是闲聊\n" +
                    "- 输入信息：用户query\n" +
                    "- 输出信息：意图分类结果（\"技术问题\" 或 \"闲聊\"）\n" +
                    "- 特殊说明：这是一个条件判断节点\n" +
                    "\n" +
                    "**步骤3a：知识库检索（技术问题分支）**\n" +
                    "- 步骤名称：搜索知识库\n" +
                    "- 步骤描述：在知识库中搜索相关技术文档和答案\n" +
                    "- 输入信息：用户query\n" +
                    "- 输出信息：知识库检索结果（相关文档内容）\n" +
                    "- 特殊说明：仅当意图为\"技术问题\"时执行\n" +
                    "\n" +
                    "**步骤4a：LLM生成回答（技术问题分支）**\n" +
                    "- 步骤名称：生成技术回答\n" +
                    "- 步骤描述：基于知识库检索结果和用户query，使用LLM生成专业回答\n" +
                    "- 输入信息：用户query + 知识库检索结果\n" +
                    "- 输出信息：LLM生成的回答文本\n" +
                    "- 特殊说明：需要将知识库内容作为上下文传递给LLM\n" +
                    "\n" +
                    "**步骤3b：LLM直接回复（闲聊分支）**\n" +
                    "- 步骤名称：闲聊回复\n" +
                    "- 步骤描述：使用LLM直接生成闲聊回复\n" +
                    "- 输入信息：用户query\n" +
                    "- 输出信息：LLM生成的回答文本\n" +
                    "- 特殊说明：仅当意图为\"闲聊\"时执行\n" +
                    "\n" +
                    "**步骤5：输出结果**\n" +
                    "- 步骤名称：返回结果\n" +
                    "- 步骤描述：将最终回答返回给用户\n" +
                    "- 输入信息：LLM生成的回答（来自步骤4a或3b）\n" +
                    "- 输出信息：最终回答\n" +
                    "- 特殊说明：这是流程的结束节点\n" +
                    "\n" +
                    "## 数据流转\n" +
                    "1. 用户输入 → 意图识别\n" +
                    "2. 意图识别 → 知识库检索（技术问题分支）或 LLM直接回复（闲聊分支）\n" +
                    "3. 知识库检索 → LLM生成回答（技术问题分支）\n" +
                    "4. LLM生成回答 / LLM直接回复 → 输出结果\n" +
                    "\n" +
                    "## 特殊逻辑\n" +
                    "**条件判断**：在意图识别后，根据结果分为两个分支：\n" +
                    "- 如果意图 = \"技术问题\"：执行 知识库检索 → LLM生成回答\n" +
                    "- 如果意图 = \"闲聊\"：执行 LLM直接回复\n" +
                    "两个分支最终都汇聚到输出结果节点\n" +
                    "```\n" +
                    "\n" +
                    "## 注意事项\n" +
                    "1. **使用提取的变量**：如果系统已经提取了开始节点和结束节点的变量信息，请在流程描述中使用这些变量名称\n" +
                    "2. 必须明确标识流程的开始和结束\n" +
                    "3. 如果有条件判断，要清楚说明每个分支的条件和执行路径\n" +
                    "4. 如果有循环，要说明循环的数据来源、循环体的操作、循环结束条件\n" +
                    "5. 要注意数据依赖关系，确保每个步骤的输入在前序步骤中已经产生\n" +
                    "6. 保持描述清晰、结构化，便于后续的节点匹配和JSON生成\n" +
                    "7. **开始步骤的输入**和**结束步骤的输出**要与已提取的变量保持一致\n";

            MarkdownToDocxConverter converter = new MarkdownToDocxConverter();

            // 方法1: 保存为文件
            converter.convertToFile(markdownContent, "C:\\Users\\Administrator\\Desktop\\output.docx");
            System.out.println("文档已保存为 output.docx");

            // 方法2: 获取字节数组后写入文件
            // byte[] docxBytes = converter.convert(markdownContent);
            // try (FileOutputStream fos = new FileOutputStream("output2.docx")) {
            //     fos.write(docxBytes);
            // }
            // System.out.println("文档已保存为 output2.docx");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}











