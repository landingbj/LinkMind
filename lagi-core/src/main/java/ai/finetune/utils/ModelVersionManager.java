package ai.finetune.utils;

import ai.config.TrainingConfig;
import lombok.extern.slf4j.Slf4j;

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
}
