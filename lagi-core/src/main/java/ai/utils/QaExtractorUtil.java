package ai.utils;

import ai.medusa.pojo.InstructionData;
import ai.vector.VectorStoreService;
import ai.vector.pojo.UpsertRecord;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QaExtractorUtil {
    private final static VectorStoreService vectorStoreService = new VectorStoreService();

    public static class ExtractionResult {
        private Map<String, String> qaMap;
        private List<String> unpairedSections;

        public ExtractionResult(Map<String, String> qaMap, List<String> unpairedSections) {
            this.qaMap = qaMap;
            this.unpairedSections = unpairedSections;
        }

        public Map<String, String> getQaMap() {
            return qaMap;
        }

        public List<String> getUnpairedSections() {
            return unpairedSections;
        }
    }

    public static boolean extractQATXT(String filePath, String category, Map<String, Object> metadatas,Integer chunkSize) {

        String fileContent = readFile(filePath);
        List<QaPair> qaPairs = extractQaPairs(fileContent);

        if (IsSpecification(qaPairs,fileContent.length())) {
            List<InstructionData> instructionDataList = new ArrayList<>();
            for (QaPair qaPair : qaPairs) {
                List<String> list = new ArrayList<>();
                String Q = qaPair.getQuestion();
                int start = 0;
                while (start < Q.length()) {
                    int end = Math.min(start + chunkSize, Q.length());
                    String text = Q.substring(start, end).replaceAll("\\s+", " ");
                    list.add(text);
                    start = end;
                }
                InstructionData instructionData = new InstructionData();
                instructionData.setInstruction(list);
                instructionData.setOutput(qaPair.getAnswer());
                instructionDataList.add(instructionData);
            }
            addQA(instructionDataList, category, convertToStringMap(metadatas));
            return true;
        }else {
            return false;
        }
    }

    public static boolean extractQA(String content, String category, Map<String, Object> metadatas,Integer chunkSize) {
        List<QaPair> qaPairs = extractQaPairs(content);

        if (qaPairs.size()>0&&IsSpecification(qaPairs,content.length())) {
            List<InstructionData> instructionDataList = new ArrayList<>();
            for (QaPair qaPair : qaPairs) {
                List<String> list = new ArrayList<>();
                String Q = qaPair.getQuestion();
                int start = 0;
                while (start < Q.length()) {
                    int end = Math.min(start + chunkSize, Q.length());
                    String text = Q.substring(start, end).replaceAll("\\s+", " ");
                    list.add(text);
                    start = end;
                }
                InstructionData instructionData = new InstructionData();
                instructionData.setInstruction(list);
                instructionData.setOutput(qaPair.getAnswer());
                instructionDataList.add(instructionData);
            }
            addQA(instructionDataList, category, convertToStringMap(metadatas));
            return true;
        }else {
            return false;
        }
    }

    private static String readFile1(String file) {
        String str = "";
        try {
            InputStream in = Files.newInputStream(Paths.get(file));
            BufferedInputStream bis = new BufferedInputStream(in);
            CharsetDetector cd = new CharsetDetector();
            cd.setText(bis);
            CharsetMatch cm = cd.detect();
            if (cm != null) {
                Reader reader = cm.getReader();
                str = IOUtils.toString(reader);
            } else {
                str = IOUtils.toString(in, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return str;
    }

    public static String readFile(String filePath) {
        StringBuilder content = new StringBuilder();
        try {
            content.append(Files.lines(Paths.get(filePath))
                    .collect(Collectors.joining("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content.toString();
    }

    private static Map<String, String> convertToStringMap(Map<String, Object> originalMap) {
        Map<String, String> stringMap = new HashMap<>();
        if (originalMap != null) {
            for (Map.Entry<String, Object> entry : originalMap.entrySet()) {
                String stringValue = String.valueOf(entry.getValue());
                stringMap.put(entry.getKey(), stringValue);
            }
        }
        return stringMap;
    }

    private static void addQA(List<InstructionData> instructionDataList, String category, Map<String, String> metadata) {
        Map<String, String> qaMap = new HashMap<>();
        for (InstructionData data : instructionDataList) {
            for (String instruction : data.getInstruction()) {
                instruction = instruction.trim();
                String output = data.getOutput().trim();
                qaMap.put(instruction, output);
                List<UpsertRecord> upsertRecords = new ArrayList<>();
                upsertRecords.add(UpsertRecord.newBuilder()
                        .withMetadata(metadata)
                        .withDocument(instruction)
                        .build());
                upsertRecords.add(UpsertRecord.newBuilder()
                        .withMetadata(new HashMap<>(metadata))
                        .withDocument(output)
                        .build());
                vectorStoreService.upsertCustomVectors(upsertRecords, category, true);
            }
        }
    }

    public static boolean IsSpecification(List<QaPair> qaPairs,Integer length2) {
        if (length2==null||length2<=0){
            return false;
        }
        boolean isValid = true;
        Integer heji = 0;
        for (QaPair qaPair : qaPairs) {
            String question = qaPair.getQuestion();
            String answer = qaPair.getAnswer();
            if (question.isEmpty()) {
                return false;
            }
            if (answer.isEmpty()) {
                return false;
            }
            heji += question.length()+answer.length();
        }
        if (heji>0&&length2>0){
            int maxLength = Math.max(heji, length2);
            int threshold = (int) (maxLength * 0.5);
            isValid = Math.min(heji, length2) >= threshold;
        }else {
            isValid = false;
        }
        if (isValid) {
            System.out.println("文档格式验证通过，所有问题和答案对匹配正确。");
        } else {
            System.out.println("文档格式不完整，存在不匹配的问答对。");
        }
        return isValid;
    }

    //提取问答对的方法
    private static List<QaPair> extractQaPairs(String text) {
        List<QaPair> qaPairs = new ArrayList<>();
        String[] parts = text.split("(?<=问|问题)[：:](?:题|：)?");

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            part = part.replaceAll("(问题|问)$", "");
            Pattern pattern = Pattern.compile("(?i)(答案[:：]|答[:：])"); // 匹配“答案：”或“答：”，忽略大小写
            Matcher matcher = pattern.matcher(part);
            int answerIndex = -1;

            if (matcher.find()) {
                answerIndex = matcher.start();
            }
            // 如果找到了匹配的“答：”或“答案：”
            if (answerIndex != -1) {
                String question = part.substring(0, answerIndex).trim();
                String answer = part.substring(answerIndex).trim();
                qaPairs.add(new QaPair(question, answer));
            } else {
                int lastQuestionMark = part.lastIndexOf("?");
                if (lastQuestionMark != -1) {
                    String question = part.substring(0, lastQuestionMark + 1).trim();
                    String answer = part.substring(lastQuestionMark + 1).trim();
                    qaPairs.add(new QaPair(question, answer));
                } else {
                    String[] lines = part.split("\n");
                    if (lines.length > 1) {
                        String question = lines[0].trim();
                        String answer = lines[1].trim();
                        qaPairs.add(new QaPair(question, answer));
                    } else {
                        qaPairs.add(new QaPair(part.trim(), ""));
                    }
                }
            }
        }
        return qaPairs;
    }

    static class QaPair {
        private String question;
        private String answer;

        public QaPair(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }
    }
    public static void main(String[] args) {
        String filePath = "C:\\Users\\ruiqing.luo\\Desktop\\测试用的文本.txt"; // 替换为你的文件路径
        String category = "category";
        Map<String, Object> metadatas = new HashMap<>();
        metadatas.put("filename", "测试用的文本");

        extractQA(filePath, category, metadatas,512);
    }
}