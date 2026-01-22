package ai.finetune.utils;

import ai.config.TrainingConfig;
import ai.database.impl.MysqlAdapter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型版本管理工具类
 * 支持版本号递增：V1.0.0 -> V2.0.0 (major) / V1.1.0 (minor) / V1.0.1 (patch)
 */
@Slf4j
public class ModelVersionManager {
    
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V?(\\d+)\\.(\\d+)\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
    
    /**
     * 递增版本号
     * @param currentVersion 当前版本号，如 "V1.0.0"
     * @param incrementType 递增类型
     * @return 新版本号，如 "V2.0.0"
     */
    public static String incrementVersion(String currentVersion, TrainingConfig.VersionIncrementType incrementType) {
        if (currentVersion == null || currentVersion.trim().isEmpty()) {
            return "V1.0.0";
        }
        
        // 移除前缀 "V"（如果存在）
        String versionStr = currentVersion.trim().toUpperCase();
        if (versionStr.startsWith("V")) {
            versionStr = versionStr.substring(1);
        }
        
        Matcher matcher = VERSION_PATTERN.matcher(versionStr);
        if (!matcher.matches()) {
            log.warn("版本号格式不正确: {}, 返回默认版本 V1.0.0", currentVersion);
            return "V1.0.0";
        }
        
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        
        switch (incrementType) {
            case MAJOR:
                major++;
                minor = 0;
                patch = 0;
                break;
            case MINOR:
                minor++;
                patch = 0;
                break;
            case PATCH:
                patch++;
                break;
            default:
                log.warn("未知的版本递增类型: {}, 使用 MINOR", incrementType);
                minor++;
                patch = 0;
        }
        
        return String.format("V%d.%d.%d", major, minor, patch);
    }
    
    /**
     * 使用配置中的递增类型递增版本号
     */
    public static String incrementVersion(String currentVersion) {
        TrainingConfig config = TrainingConfig.getInstance();
        return incrementVersion(currentVersion, config.getVersionIncrement());
    }
    
    /**
     * 验证版本号格式
     */
    public static boolean isValidVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return false;
        }
        
        String versionStr = version.trim().toUpperCase();
        if (versionStr.startsWith("V")) {
            versionStr = versionStr.substring(1);
        }
        
        return VERSION_PATTERN.matcher(versionStr).matches();
    }
    
    /**
     * 获取初始版本号
     */
    public static String getInitialVersion() {
        return "V1.0.0";
    }
    
    /**
     * 生成新版本号（智能版本，确保唯一性）
     * 根据配置的策略生成版本号：
     * - LATEST: 查询该模型的最新版本号，然后递增，如果冲突则继续递增直到唯一
     * - TIMESTAMP: 版本号+时间戳后缀
     * - SEQUENCE: 版本号+序号后缀
     * - TASKID: 版本号+任务ID后缀
     * 
     * @param modelName 模型名称（用于查询该模型的所有版本）
     * @param baseVersion 基础版本号（如果为null，则查询最新版本）
     * @param taskId 训练任务ID（用于TASKID策略）
     * @return 新版本号
     */
    public static String generateNewVersion(String modelName, String baseVersion, String taskId) {
        TrainingConfig config = TrainingConfig.getInstance();
        TrainingConfig.VersionStrategy strategy = config.getVersionStrategy();
        
        switch (strategy) {
            case LATEST:
                return generateVersionFromLatest(modelName, baseVersion);
            case TIMESTAMP:
                return generateVersionWithTimestamp(baseVersion != null ? baseVersion : "V1.0.0");
            case SEQUENCE:
                return generateVersionWithSequence(modelName, baseVersion != null ? baseVersion : "V1.0.0");
            case TASKID:
                return generateVersionWithTaskId(baseVersion != null ? baseVersion : "V1.0.0", taskId);
            default:
                log.warn("未知的版本号生成策略: {}, 使用 LATEST", strategy);
                return generateVersionFromLatest(modelName, baseVersion);
        }
    }
    
    /**
     * 基于最新版本号生成新版本（推荐策略）
     * 1. 查询该模型的所有版本，找到最新版本号
     * 2. 基于最新版本号递增
     * 3. 如果递增后的版本号已存在，继续递增直到找到唯一版本号
     */
    private static String generateVersionFromLatest(String modelName, String baseVersion) {
        try {
            MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
            
            // 如果提供了基础版本号，先尝试递增
            String candidateVersion = baseVersion;
            if (candidateVersion == null || candidateVersion.isEmpty()) {
                // 查询该模型的最新版本号
                String sql = "SELECT version FROM models WHERE name = ? AND is_deleted = 0 " +
                           "ORDER BY created_at DESC LIMIT 1";
                List<Map<String, Object>> results = mysqlAdapter.select(sql, modelName);
                if (results != null && !results.isEmpty()) {
                    candidateVersion = (String) results.get(0).get("version");
                } else {
                    // 如果没有找到任何版本，使用初始版本
                    candidateVersion = "V1.0.0";
                }
            }
            
            // 递增版本号
            TrainingConfig config = TrainingConfig.getInstance();
            candidateVersion = incrementVersion(candidateVersion, config.getVersionIncrement());
            
            // 检查版本号是否已存在，如果存在则继续递增直到找到唯一版本号
            int maxAttempts = 100; // 防止无限循环
            int attempts = 0;
            while (attempts < maxAttempts) {
                String checkSql = "SELECT COUNT(*) AS cnt FROM models WHERE name = ? AND version = ? AND is_deleted = 0";
                List<Map<String, Object>> checkResults = mysqlAdapter.select(checkSql, modelName, candidateVersion);
                if (checkResults != null && !checkResults.isEmpty()) {
                    Object cntObj = checkResults.get(0).get("cnt");
                    if (cntObj instanceof Number && ((Number) cntObj).intValue() == 0) {
                        // 版本号唯一，返回
                        log.debug("生成唯一版本号: modelName={}, version={}", modelName, candidateVersion);
                        return candidateVersion;
                    }
                }
                
                // 版本号已存在，继续递增
                candidateVersion = incrementVersion(candidateVersion, config.getVersionIncrement());
                attempts++;
            }
            
            log.warn("无法生成唯一版本号（尝试{}次后放弃）: modelName={}, 使用时间戳策略", maxAttempts, modelName);
            // 如果尝试多次后仍然冲突，使用时间戳策略作为后备
            return generateVersionWithTimestamp(candidateVersion);
            
        } catch (Exception e) {
            log.error("基于最新版本生成新版本号失败: modelName={}, baseVersion={}", modelName, baseVersion, e);
            // 失败时使用时间戳策略作为后备
            return generateVersionWithTimestamp(baseVersion != null ? baseVersion : "V1.0.0");
        }
    }
    
    /**
     * 生成带时间戳的版本号：V1.1.0-20260121-143022
     */
    private static String generateVersionWithTimestamp(String baseVersion) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        TrainingConfig config = TrainingConfig.getInstance();
        String incrementedVersion = incrementVersion(baseVersion, config.getVersionIncrement());
        return incrementedVersion + "-" + timestamp;
    }
    
    /**
     * 生成带序号的版本号：V1.1.0-1, V1.1.0-2
     */
    private static String generateVersionWithSequence(String modelName, String baseVersion) {
        try {
            MysqlAdapter mysqlAdapter = MysqlAdapter.getInstance();
            TrainingConfig config = TrainingConfig.getInstance();
            String incrementedVersion = incrementVersion(baseVersion, config.getVersionIncrement());
            
            // 查询该版本号前缀下已存在的序号
            String prefix = incrementedVersion + "-";
            String sql = "SELECT version FROM models WHERE name = ? AND version LIKE ? AND is_deleted = 0 " +
                       "ORDER BY version DESC LIMIT 1";
            List<Map<String, Object>> results = mysqlAdapter.select(sql, modelName, prefix + "%");
            
            int nextSequence = 1;
            if (results != null && !results.isEmpty()) {
                String lastVersion = (String) results.get(0).get("version");
                // 提取序号：V1.1.0-5 -> 5
                try {
                    String sequenceStr = lastVersion.substring(prefix.length());
                    nextSequence = Integer.parseInt(sequenceStr) + 1;
                } catch (Exception e) {
                    log.debug("解析序号失败: version={}", lastVersion, e);
                }
            }
            
            return incrementedVersion + "-" + nextSequence;
            
        } catch (Exception e) {
            log.error("生成带序号的版本号失败: modelName={}, baseVersion={}", modelName, baseVersion, e);
            return generateVersionWithTimestamp(baseVersion);
        }
    }
    
    /**
     * 生成带任务ID的版本号：V1.1.0-task_xxx
     */
    private static String generateVersionWithTaskId(String baseVersion, String taskId) {
        TrainingConfig config = TrainingConfig.getInstance();
        String incrementedVersion = incrementVersion(baseVersion, config.getVersionIncrement());
        if (taskId != null && !taskId.isEmpty()) {
            // 提取任务ID的简短形式（去掉前缀和后缀，只保留核心部分）
            String shortTaskId = taskId;
            if (taskId.contains("_")) {
                String[] parts = taskId.split("_");
                if (parts.length > 0) {
                    // 取最后一部分（通常是唯一标识）
                    shortTaskId = parts[parts.length - 1];
                }
            }
            return incrementedVersion + "-" + shortTaskId;
        } else {
            // 如果没有任务ID，使用时间戳作为后备
            return generateVersionWithTimestamp(baseVersion);
        }
    }
}
