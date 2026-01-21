package ai.finetune.utils;

import ai.config.TrainingConfig;
import ai.database.impl.MysqlAdapter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 训练后处理服务
 * 在训练完成后自动入库新模型
 */
@Slf4j
public class TrainingPostProcessor {
    
    private final MysqlAdapter mysqlAdapter;
    private final ModelDatasetManager modelDatasetManager;
    private final TrainingConfig trainingConfig;
    
    public TrainingPostProcessor() {
        this.mysqlAdapter = MysqlAdapter.getInstance();
        this.modelDatasetManager = new ModelDatasetManager();
        this.trainingConfig = TrainingConfig.getInstance();
    }
    
    /**
     * 处理训练完成后的自动入库
     * @param taskId 训练任务ID
     */
    public void processTrainingCompletion(String taskId) {
        try {
            // 查询训练任务信息
            String sql = "SELECT * FROM ai_training_tasks WHERE task_id = ? AND is_deleted = 0";
            List<Map<String, Object>> tasks = mysqlAdapter.select(sql, taskId);
            
            if (tasks == null || tasks.isEmpty()) {
                log.warn("训练任务不存在: taskId={}", taskId);
                return;
            }
            
            Map<String, Object> task = tasks.get(0);
            String status = (String) task.get("status");
            
            // 只处理已完成的任务（Docker 轮询可能写入 exited/finished）
            boolean isDoneStatus = "completed".equalsIgnoreCase(status)
                    || "exited".equalsIgnoreCase(status)
                    || "finished".equalsIgnoreCase(status);
            if (!isDoneStatus) {
                log.debug("训练任务未完成，跳过处理: taskId={}, status={}", taskId, status);
                return;
            }
            
            // 检查是否启用自动入库
            if (!trainingConfig.isAutoSaveAfterTraining()) {
                log.debug("自动入库功能已禁用，跳过处理: taskId={}", taskId);
                return;
            }
            
            // 检查是否已处理过：查询models表中是否已有该taskId相关的模型
            String checkSql = "SELECT id FROM models WHERE description LIKE ? AND is_deleted = 0 LIMIT 1";
            List<Map<String, Object>> existingModels = mysqlAdapter.select(checkSql, "%训练任务ID: " + taskId + "%");
            if (existingModels != null && !existingModels.isEmpty()) {
                log.debug("训练任务已处理过，已存在相关模型: taskId={}", taskId);
                return;
            }
            
            // 获取训练输出路径
            String trainDir = (String) task.get("train_dir");
            if (trainDir == null || trainDir.isEmpty()) {
                log.warn("训练输出目录为空，无法自动入库: taskId={}", taskId);
                return;
            }
            
            // 查找训练后的模型文件（通常是best.pt或weights/best.pt）
            String modelPath = findTrainedModelFile(trainDir);
            if (modelPath == null) {
                log.warn("未找到训练后的模型文件: taskId={}, trainDir={}", taskId, trainDir);
                return;
            }
            
            // 获取原始模型ID
            Object modelIdObj = task.get("model_id");
            Long originalModelId = null;
            if (modelIdObj != null) {
                if (modelIdObj instanceof Number) {
                    originalModelId = ((Number) modelIdObj).longValue();
                } else {
                    try {
                        originalModelId = Long.parseLong(modelIdObj.toString());
                    } catch (NumberFormatException e) {
                        log.warn("无效的model_id: {}", modelIdObj);
                    }
                }
            }
            
            log.info("训练完成处理: taskId={}, model_id={}, trainDir={}, modelPath={}", 
                    taskId, originalModelId, trainDir, modelPath);
            
            // 如果提供了原始模型ID，则复制模型信息并递增版本
            if (originalModelId != null) {
                log.info("开始保存训练后的模型: originalModelId={}, newModelPath={}", originalModelId, modelPath);
                Long newModelId = modelDatasetManager.saveTrainedModel(originalModelId, modelPath, taskId);
                if (newModelId != null) {
                    // 更新训练任务的model_id为新模型ID
                    String updateSql = "UPDATE ai_training_tasks SET model_id = ? WHERE task_id = ?";
                    mysqlAdapter.executeUpdate(updateSql, newModelId, taskId);
                    log.info("训练后模型已自动入库: taskId={}, originalModelId={}, newModelId={}, newModelPath={}", 
                            taskId, originalModelId, newModelId, modelPath);
                } else {
                    log.error("保存训练后的模型失败: taskId={}, originalModelId={}, modelPath={}", 
                            taskId, originalModelId, modelPath);
                }
            } else {
                // 如果未提供模型ID，将模型保存到默认目录
                String modelName = (String) task.get("model_name");
                if (modelName == null || modelName.isEmpty()) {
                    modelName = "untracked_model";
                }
                String savedPath = modelDatasetManager.saveUntrackedModel(modelName, modelPath, taskId);
                if (savedPath != null) {
                    log.info("未跟踪模型已保存: taskId={}, path={}", taskId, savedPath);
                }
            }
            
        } catch (Exception e) {
            log.error("处理训练完成后的自动入库失败: taskId={}", taskId, e);
        }
    }
    
    /**
     * 查找训练后的模型文件
     * 通常位于 train_dir/weights/best.pt 或 train_dir/best.pt
     */
    private String findTrainedModelFile(String trainDir) {
        try {
            Path trainPath = Paths.get(trainDir);
            if (!Files.exists(trainPath)) {
                return null;
            }
            
            // 尝试查找 weights/best.pt
            Path weightsBest = trainPath.resolve("weights").resolve("best.pt");
            if (Files.exists(weightsBest)) {
                return weightsBest.toString();
            }
            
            // 尝试查找 best.pt
            Path best = trainPath.resolve("best.pt");
            if (Files.exists(best)) {
                return best.toString();
            }
            
            // 尝试查找 last.pt
            Path last = trainPath.resolve("weights").resolve("last.pt");
            if (Files.exists(last)) {
                return last.toString();
            }
            
            // 尝试查找 trainPath 下的所有 .pt 文件
            try {
                java.util.stream.Stream<Path> ptFiles = Files.walk(trainPath)
                        .filter(p -> p.toString().endsWith(".pt"))
                        .filter(Files::isRegularFile);
                
                java.util.Optional<Path> firstPt = ptFiles.findFirst();
                if (firstPt.isPresent()) {
                    return firstPt.get().toString();
                }
            } catch (Exception e) {
                log.warn("查找.pt文件失败: trainDir={}", trainDir, e);
            }
            
        } catch (Exception e) {
            log.error("查找训练模型文件失败: trainDir={}", trainDir, e);
        }
        
        return null;
    }
}
