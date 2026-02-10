package ai.finetune.service;

import cn.hutool.json.JSONObject;

import java.util.concurrent.Future;

public interface TrainerService {

    /**
     * 启动训练任务
     * @param config
     * @return
     */
    Future<String> startTrainingTask(JSONObject config);


    /**
     * 启动评估任务
     * @param config
     * @return
     */
    Future<String> startEvaluationTask(JSONObject config);

    /**
     * 启动预测任务
     * @param config
     * @return
     */
    Future<String> startPredictionTask(JSONObject config);


    /**
     * 启动模型转换任务
     * @param config
     * @return
     */
    Future<String> startConvertTask(JSONObject config);

    /**
     * 停止训练任务
     * @param taskId 任务 ID
     * @return
     */
    String pauseTask(String taskId);

    /**
     * 停止训练任务
     * @param taskId 任务 ID
     * @return
     */
    String stopTask(String taskId);

    /**
     * 获取任务状态
     * @param taskId 任务 ID
     * @return
     */
    String getTaskStatus(String taskId);

    Future<String> resumeTask(String taskId);

    String removeTask(String taskId);

    String getTaskLogs(String taskId, Integer lastLines);

    String getRunningTaskInfo();

    JSONObject getResourceInfo(String taskId);

}
