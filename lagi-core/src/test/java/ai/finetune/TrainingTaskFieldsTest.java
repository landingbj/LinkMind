package ai.finetune;

import ai.config.ContextLoader;
import ai.config.ModelStorageConfig;
import ai.database.impl.MysqlAdapter;
import ai.finetune.utils.ModelDatasetManager;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 训练任务字段入库测试
 * 测试 model_id、dataset_id、output_path 字段是否正确入库
 * 
 * 注意：
 * - 需要数据库连接的测试会在数据库不可用时跳过
 * - 确保测试环境中有 lagi.yml 配置文件
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TrainingTaskFieldsTest {

    private static ModelDatasetManager modelDatasetManager;
    private static MysqlAdapter mysqlAdapter;
    private static boolean databaseAvailable = false;
    
    @TempDir
    static Path tempDir;
    
    private static Long testModelId;
    private static Long testDatasetId;
    private static String testTaskId;
    private static String testModelPath;
    private static String testDatasetPath;
    
    // 静态代码块：在类加载时初始化配置
    static {
        try {
            // 加载配置上下文
            ContextLoader.loadContext();
            if (ContextLoader.configuration != null) {
                databaseAvailable = true;
                System.out.println("✓ 配置加载成功，数据库可用");
            } else {
                System.out.println("⚠ 配置加载失败，数据库不可用，将跳过需要数据库的测试");
            }
        } catch (Exception e) {
            System.out.println("⚠ 配置加载异常: " + e.getMessage());
            System.out.println("  将跳过需要数据库的测试");
            databaseAvailable = false;
        }
    }
    
    @BeforeAll
    static void setUp() throws IOException {
        // 初始化管理器
        try {
            modelDatasetManager = new ModelDatasetManager();
            mysqlAdapter = new MysqlAdapter("mysql");
            
            // 验证数据库是否可用
            if (databaseAvailable) {
                try {
                    modelDatasetManager.listModels(null);
                    System.out.println("✓ 数据库连接验证成功");
                    databaseAvailable = true;
                } catch (Exception e) {
                    System.out.println("⚠ 数据库连接验证失败: " + e.getMessage());
                    databaseAvailable = false;
                }
            }
        } catch (Exception e) {
            System.out.println("⚠ 初始化失败: " + e.getMessage());
            databaseAvailable = false;
        }
        
        // 创建测试文件
        testModelPath = createTestModelFile();
        testDatasetPath = createTestDatasetFile();
        
        testTaskId = "test-task-fields-" + System.currentTimeMillis();
    }
    
    @AfterAll
    static void tearDown() {
        // 清理测试数据
        if (databaseAvailable && testTaskId != null) {
            try {
                String deleteSql = "DELETE FROM ai_training_tasks WHERE task_id = ?";
                mysqlAdapter.executeUpdate(deleteSql, testTaskId);
                System.out.println("✓ 测试数据已清理");
            } catch (Exception e) {
                System.out.println("⚠ 清理测试数据失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 创建测试模型文件
     */
    private static String createTestModelFile() throws IOException {
        Path modelFile = tempDir.resolve("test_model.pt");
        Files.write(modelFile, "fake model content".getBytes());
        return modelFile.toString();
    }
    
    /**
     * 创建测试数据集文件
     */
    private static String createTestDatasetFile() throws IOException {
        Path datasetFile = tempDir.resolve("test_dataset.yaml");
        String yamlContent = "train: /path/to/train\nval: /path/to/val\nnc: 10";
        Files.write(datasetFile, yamlContent.getBytes());
        return datasetFile.toString();
    }
    
    @Test
    @Order(1)
    @DisplayName("测试1: 准备测试数据 - 上传模型和数据集")
    void testPrepareTestData() {
        System.out.println("\n========== 测试1: 准备测试数据 ==========");
        
        Assumptions.assumeTrue(databaseAvailable, "数据库不可用，跳过此测试");
        
        try {
            // 上传模型
            testModelId = modelDatasetManager.saveModel(
                "test_model_for_fields", 
                testModelPath, 
                "V1.0.0",
                null, null,
                "yolov8", "pytorch",
                1024L, "pt",
                "测试模型", 
                "test_user"
            );
            assertNotNull(testModelId, "模型ID不应为null");
            System.out.println("✓ 模型已上传，model_id: " + testModelId);
            
            // 上传数据集
            testDatasetId = modelDatasetManager.saveDataset(
                "test_dataset_for_fields",
                testDatasetPath,
                "测试数据集",
                "test_user",
                2048L,
                "yaml"
            );
            assertNotNull(testDatasetId, "数据集ID不应为null");
            System.out.println("✓ 数据集已上传，dataset_id: " + testDatasetId);
            
        } catch (Exception e) {
            fail("准备测试数据失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(2)
    @DisplayName("测试2: 创建训练任务 - 验证 model_id、dataset_id、output_path 入库")
    void testCreateTrainingTaskWithFields() {
        System.out.println("\n========== 测试2: 创建训练任务并验证字段入库 ==========");
        
        Assumptions.assumeTrue(databaseAvailable, "数据库不可用，跳过此测试");
        Assumptions.assumeTrue(testModelId != null && testDatasetId != null, 
                              "测试数据未准备，请先运行测试1");
        
        try {
            // 构建训练配置（模拟 YoloTrainerAdapter 中的逻辑）
            JSONObject config = new JSONObject();
            config.put("the_train_type", "train");
            config.put("task_id", testTaskId);
            config.put("track_id", "track-" + System.currentTimeMillis());
            config.put("model_name", "yolov8");
            config.put("user_id", "test_user");
            config.put("original_model_id", testModelId);
            config.put("original_dataset_id", testDatasetId);
            
            // 获取存储配置并生成 output_path
            ModelStorageConfig storageConfig = ModelStorageConfig.getInstance();
            String projectPath = storageConfig.getProjectPath();
            String outputPath = projectPath + "/" + testModelId;
            config.put("project", outputPath);
            
            // 模拟保存训练任务（直接插入数据库，模拟 YoloTrainerAdapter.saveTrainTask 的逻辑）
            String sql = "INSERT INTO ai_training_tasks " +
                    "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                    "container_name, container_id, docker_image, gpu_ids, use_gpu, " +
                    "dataset_path, dataset_name, model_path, epochs, batch_size, image_size, optimizer, " +
                    "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, " +
                    "model_id, dataset_id, output_path, config_json) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, ?)";
            
            String trackId = config.getStr("track_id");
            String modelName = config.getStr("model_name");
            String userId = config.getStr("user_id");
            Long modelId = config.getLong("original_model_id");
            Long datasetId = config.getLong("original_dataset_id");
            String outputPathValue = config.getStr("project");
            
            int result = mysqlAdapter.executeUpdate(sql,
                    testTaskId, trackId, modelName, "object_detection", "pytorch", "train",
                    "", "", "", "0", 1,
                    testDatasetPath, "test_dataset", testModelPath, 10, 2, "640", "SGD",
                    "starting", "0%", 0, 0, userId,
                    modelId, datasetId, outputPathValue, config.toString());
            
            assertEquals(1, result, "训练任务应该成功插入");
            System.out.println("✓ 训练任务已创建");
            
            // 查询并验证字段
            String querySql = "SELECT model_id, dataset_id, output_path FROM ai_training_tasks WHERE task_id = ?";
            List<Map<String, Object>> tasks = mysqlAdapter.select(querySql, testTaskId);
            
            assertNotNull(tasks, "查询结果不应为null");
            assertFalse(tasks.isEmpty(), "应该查询到任务记录");
            
            Map<String, Object> task = tasks.get(0);
            System.out.println("  查询到的任务信息:");
            System.out.println("    model_id: " + task.get("model_id"));
            System.out.println("    dataset_id: " + task.get("dataset_id"));
            System.out.println("    output_path: " + task.get("output_path"));
            
            // 验证字段
            assertNotNull(task.get("model_id"), "model_id 不应为null");
            assertEquals(testModelId, ((Number) task.get("model_id")).longValue(), 
                        "model_id 应该等于 " + testModelId);
            
            assertNotNull(task.get("dataset_id"), "dataset_id 不应为null");
            assertEquals(testDatasetId, ((Number) task.get("dataset_id")).longValue(), 
                        "dataset_id 应该等于 " + testDatasetId);
            
            assertNotNull(task.get("output_path"), "output_path 不应为null");
            assertEquals(outputPath, task.get("output_path"), 
                        "output_path 应该等于 " + outputPath);
            
            System.out.println("✓ 所有字段验证通过");
            
        } catch (Exception e) {
            fail("创建训练任务失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("测试3: 训练完成 - 验证 output_path 更新")
    void testUpdateOutputPathOnCompletion() {
        System.out.println("\n========== 测试3: 训练完成时更新 output_path ==========");
        
        Assumptions.assumeTrue(databaseAvailable, "数据库不可用，跳过此测试");
        Assumptions.assumeTrue(testTaskId != null, "测试任务未创建，请先运行测试2");
        
        try {
            // 模拟训练完成，更新 output_path（模拟 YoloTrainerAdapter.updateYoloTaskComplete 的逻辑）
            String trainDir = "/app/data/project/" + testModelId + "/train" + System.currentTimeMillis();
            
            // 先清空 output_path，模拟旧数据的情况
            String clearSql = "UPDATE ai_training_tasks SET output_path = NULL WHERE task_id = ?";
            mysqlAdapter.executeUpdate(clearSql, testTaskId);
            System.out.println("  已清空 output_path（模拟旧数据）");
            
            // 更新任务完成信息（如果 output_path 为空，使用 trainDir）
            String updateSql = "UPDATE ai_training_tasks " +
                    "SET status = ?, end_time = NOW(), train_dir = ?, " +
                    "output_path = COALESCE(output_path, ?), updated_at = NOW(), progress = '100%' " +
                    "WHERE task_id = ?";
            
            int result = mysqlAdapter.executeUpdate(updateSql,
                    "completed",
                    trainDir,
                    trainDir,  // 如果 output_path 为空，使用 trainDir
                    testTaskId);
            
            assertEquals(1, result, "更新应该成功");
            System.out.println("✓ 训练任务已完成，train_dir: " + trainDir);
            
            // 验证 output_path 已更新
            String querySql = "SELECT output_path, train_dir, status FROM ai_training_tasks WHERE task_id = ?";
            List<Map<String, Object>> tasks = mysqlAdapter.select(querySql, testTaskId);
            
            assertNotNull(tasks);
            assertFalse(tasks.isEmpty());
            
            Map<String, Object> task = tasks.get(0);
            System.out.println("  更新后的任务信息:");
            System.out.println("    status: " + task.get("status"));
            System.out.println("    train_dir: " + task.get("train_dir"));
            System.out.println("    output_path: " + task.get("output_path"));
            
            assertEquals("completed", task.get("status"), "状态应该是 completed");
            assertNotNull(task.get("output_path"), "output_path 不应为null");
            assertEquals(trainDir, task.get("output_path"), 
                        "output_path 应该等于 train_dir: " + trainDir);
            
            System.out.println("✓ output_path 更新验证通过");
            
        } catch (Exception e) {
            fail("更新训练任务失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("测试4: 验证 output_path 不为空时不覆盖")
    void testOutputPathNotOverwritten() {
        System.out.println("\n========== 测试4: 验证 output_path 不为空时不覆盖 ==========");
        
        Assumptions.assumeTrue(databaseAvailable, "数据库不可用，跳过此测试");
        Assumptions.assumeTrue(testTaskId != null, "测试任务未创建，请先运行测试2");
        
        try {
            // 设置一个已知的 output_path
            String originalOutputPath = "/app/data/project/" + testModelId;
            String updateSql1 = "UPDATE ai_training_tasks SET output_path = ? WHERE task_id = ?";
            mysqlAdapter.executeUpdate(updateSql1, originalOutputPath, testTaskId);
            System.out.println("  设置原始 output_path: " + originalOutputPath);
            
            // 模拟训练完成，但 output_path 已存在
            String newTrainDir = "/app/data/project/" + testModelId + "/train_new";
            String updateSql2 = "UPDATE ai_training_tasks " +
                    "SET status = ?, end_time = NOW(), train_dir = ?, " +
                    "output_path = COALESCE(output_path, ?), updated_at = NOW() " +
                    "WHERE task_id = ?";
            
            mysqlAdapter.executeUpdate(updateSql2,
                    "completed",
                    newTrainDir,
                    newTrainDir,  // 如果 output_path 为空，使用 newTrainDir
                    testTaskId);
            
            // 验证 output_path 没有被覆盖
            String querySql = "SELECT output_path, train_dir FROM ai_training_tasks WHERE task_id = ?";
            List<Map<String, Object>> tasks = mysqlAdapter.select(querySql, testTaskId);
            
            assertNotNull(tasks);
            assertFalse(tasks.isEmpty());
            
            Map<String, Object> task = tasks.get(0);
            System.out.println("  验证结果:");
            System.out.println("    train_dir: " + task.get("train_dir"));
            System.out.println("    output_path: " + task.get("output_path"));
            
            assertEquals(originalOutputPath, task.get("output_path"), 
                        "output_path 应该保持原值，不被覆盖");
            assertEquals(newTrainDir, task.get("train_dir"), 
                        "train_dir 应该更新为新值");
            
            System.out.println("✓ output_path 未被覆盖，验证通过");
            
        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("测试5: 验证没有 model_id 时的 output_path 生成")
    void testOutputPathWithoutModelId() {
        System.out.println("\n========== 测试5: 验证没有 model_id 时的 output_path ==========");
        
        Assumptions.assumeTrue(databaseAvailable, "数据库不可用，跳过此测试");
        
        try {
            String taskIdWithoutModel = "test-task-no-model-" + System.currentTimeMillis();
            
            // 创建没有 model_id 的训练任务
            String sql = "INSERT INTO ai_training_tasks " +
                    "(task_id, track_id, model_name, model_category, model_framework, task_type, " +
                    "container_name, container_id, docker_image, gpu_ids, use_gpu, " +
                    "dataset_path, dataset_name, model_path, epochs, batch_size, image_size, optimizer, " +
                    "status, progress, current_epoch, start_time, created_at, is_deleted, user_id, " +
                    "model_id, dataset_id, output_path, config_json) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, ?)";
            
            mysqlAdapter.executeUpdate(sql,
                    taskIdWithoutModel, "track-no-model", "yolov8", "object_detection", "pytorch", "train",
                    "", "", "", "0", 1,
                    testDatasetPath, "test_dataset", testModelPath, 10, 2, "640", "SGD",
                    "starting", "0%", 0, 0, "test_user",
                    null, null, null, "{}");
            
            // 查询验证
            String querySql = "SELECT model_id, dataset_id, output_path FROM ai_training_tasks WHERE task_id = ?";
            List<Map<String, Object>> tasks = mysqlAdapter.select(querySql, taskIdWithoutModel);
            
            assertNotNull(tasks);
            assertFalse(tasks.isEmpty());
            
            Map<String, Object> task = tasks.get(0);
            System.out.println("  没有 model_id 的任务信息:");
            System.out.println("    model_id: " + task.get("model_id"));
            System.out.println("    dataset_id: " + task.get("dataset_id"));
            System.out.println("    output_path: " + task.get("output_path"));
            
            assertNull(task.get("model_id"), "model_id 应该为 null");
            assertNull(task.get("dataset_id"), "dataset_id 应该为 null");
            assertNull(task.get("output_path"), "output_path 应该为 null（因为没有 model_id）");
            
            System.out.println("✓ 没有 model_id 时的字段验证通过");
            
            // 清理测试数据
            mysqlAdapter.executeUpdate("DELETE FROM ai_training_tasks WHERE task_id = ?", taskIdWithoutModel);
            
        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }
}
