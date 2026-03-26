package ai.dto;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 训练任务更新请求DTO
 * 字段名严格保持下划线格式，与JSON参数完全一致
 */
@Data
public class TrainTaskUpdateDTO {

    // ===================== 必填字段 =====================
    /**
     * 任务ID (必填)
     */
    private String task_id;

    // ===================== 基础信息 =====================
    private String track_id;
    private String model_name;
    private String model_version;
    private String model_framework;
    private String model_category;
    private String task_type;

    // ===================== 容器/环境 =====================
    private String container_name;
    private String container_id;
    private String docker_image;
    private String gpu_ids;
    private Integer use_gpu;

    // ===================== 数据集相关 =====================
    private String dataset_path;
    private String dataset_name;
    private String dataset_type;
    private Integer num_classes;

    // ===================== 路径相关 =====================
    private String model_path;
    private String checkpoint_path;
    private String output_path;
    private String train_dir;
    private String weights_path;
    private String best_weights_path;
    private String log_file_path;

    // ===================== 训练超参数 =====================
    private Integer epochs;
    private Integer batch_size;
    private BigDecimal learning_rate;
    private String image_size;
    private String optimizer;

    // ===================== 训练状态/进度 =====================
    private String status;
    private String progress;
    private Integer current_epoch;
    private Integer current_step;
    private Integer total_steps;

    // ===================== 训练指标 =====================
    private BigDecimal train_loss;
    private BigDecimal val_loss;
    private BigDecimal train_acc;
    private BigDecimal val_acc;
    private BigDecimal best_metric;
    private String best_metric_name;

    // ===================== 时间/错误信息 =====================
    private String error_message;
    private String start_time;
    private String end_time;
    private Long estimated_time;

    // ===================== 系统字段 =====================
    private String created_at;
    private String updated_at;
    private String deleted_at;
    private Integer is_deleted;
    private String user_id;
    private String project_id;
    private Integer template_id;
    private Integer priority;
    private String tags;
    private String remark;
    private String config_json;
}
