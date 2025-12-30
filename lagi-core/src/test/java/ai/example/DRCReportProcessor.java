package ai.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DRCReportProcessor {
    // 定义表格数据结构
    static class ViolationRecord {
        String violationRule;
        String number;
        String blockOfViolation;
        String reason;
        String decision;

        ViolationRecord(String violationRule, String number, String blockOfViolation, String reason, String decision) {
            this.violationRule = violationRule;
            this.number = number;
            this.blockOfViolation = blockOfViolation;
            this.reason = reason;
            this.decision = decision;
        }

        // 生成 CSV 行
        String toCSV() {
            return String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    violationRule, number, blockOfViolation, reason, decision);
        }
    }

    public static void main(String[] args) {
        String inputFile = "C:\\Users\\24175\\Documents\\络明芯需求\\需求250414\\CUP.drc.results.txt"; // 输入 TXT 文件路径
        String outputFile = "C:\\Users\\24175\\Documents\\络明芯需求\\需求250414\\CUP.drc.results_output.csv"; // 输出 CSV 文件路径
        List<ViolationRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            String currentRule = null;
            String currentNumber = null;
            StringBuilder currentReason = null;
            String lastValidRule = null; // 保存当前有效块的规则名称
            boolean captureReason = false;
            boolean skipBlock = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 匹配错误数量行
                if (line.matches("\\d+\\s+\\d+\\s+\\d+\\s+[A-Za-z]+\\s+\\d+\\s+\\d+:\\d+:\\d+\\s+\\d+")) {
                    // 保存上一块的有效记录（如果存在）
                    if (!skipBlock && lastValidRule != null && currentNumber != null && currentReason != null) {
                        records.add(new ViolationRecord(
                                lastValidRule,
                                currentNumber,
                                "", // Block of Design Rule Violation 为空
                                currentReason.toString().trim(),
                                ""  // Decision 为空
                        ));
                    }
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 && parts[0].equals("0") && parts[1].equals("0")) {
                        skipBlock = true; // 标记跳过当前块
                        currentNumber = null;
                        currentReason = null;
                        captureReason = false;
                    } else if (parts.length >= 2) {
                        currentNumber = parts[0]; // 取第一个数字
                        skipBlock = false;
                        if (currentRule != null) {
                            lastValidRule = currentRule; // 更新有效规则名称
                        }
                    }
                    continue;
                }

                // 跳过 Rule File Pathname 和 Rule File Title
                if (line.startsWith("Rule File")) {
                    continue;
                }

                // 捕获 Reason 内容
                if (line.startsWith("@") && !skipBlock && lastValidRule != null) {
                    captureReason = true;
                    currentReason = new StringBuilder(line.substring(2).trim()); // 去掉 @ 和前导空格
                    continue;
                }
                if (captureReason && !line.startsWith("}") && !skipBlock) {
                    currentReason.append(" ").append(line.trim());
                    continue;
                }

                // 块结束
                if (line.startsWith("}") && !skipBlock && lastValidRule != null && currentNumber != null && currentReason != null) {
                    records.add(new ViolationRecord(
                            lastValidRule,
                            currentNumber,
                            "", // Block of Design Rule Violation 为空
                            currentReason.toString().trim(),
                            ""  // Decision 为空
                    ));
                    currentNumber = null;
                    currentReason = null;
                    captureReason = false;
                    skipBlock = false;
                    lastValidRule = null; // 重置 lastValidRule
                    continue;
                }

                // 匹配规则名称（放在最后，排除其他情况）
                if (!line.startsWith("{") && !line.startsWith("@") && !line.startsWith("}")) {
                    // 保存上一块的有效记录（如果存在）
                    if (!skipBlock && lastValidRule != null && currentNumber != null && currentReason != null) {
                        records.add(new ViolationRecord(
                                lastValidRule,
                                currentNumber,
                                "", // Block of Design Rule Violation 为空
                                currentReason.toString().trim(),
                                ""  // Decision 为空
                        ));
                    }
                    // 更新当前规则名称，去除 { 及其后续内容
                    currentRule = line.split("\\s*\\{")[0].trim();
                    currentNumber = null;
                    currentReason = null;
                    captureReason = false;
                    skipBlock = false;
                }
            }

            // 处理最后一个块
            if (!skipBlock && lastValidRule != null && currentNumber != null && currentReason != null) {
                records.add(new ViolationRecord(
                        lastValidRule,
                        currentNumber,
                        "", // Block of Design Rule Violation 为空
                        currentReason.toString().trim(),
                        ""  // Decision 为空
                ));
            }

            // 写入 CSV 文件
            try (FileWriter writer = new FileWriter(outputFile)) {
                // 写入表头
                writer.write("\"Violation Rule\",\"No.\",\"Block of Design Rule Violation\",\"Reason\",\"Decision\"\n");
                // 写入记录
                for (ViolationRecord record : records) {
                    writer.write(record.toCSV());
                }
                System.out.println("表格已生成：" + outputFile);
                System.out.println("记录总数：" + records.size());
            }

        } catch (IOException e) {
            System.err.println("处理文件时出错: " + e.getMessage());
        }
    }
}