package ai.finetune.service.impl;

import ai.finetune.service.TrainerService;
import cn.hutool.json.JSONObject;

import java.util.concurrent.Future;

public class K8sTrainerServiceImpl implements TrainerService {
    @Override
    public Future<String> startTrainingTask(JSONObject config) {
        return null;
    }

    @Override
    public Future<String> startEvaluationTask(JSONObject config) {
        return null;
    }

    @Override
    public Future<String> startPredictionTask(JSONObject config) {
        return null;
    }

    @Override
    public Future<String> startConvertTask(JSONObject config) {
        return null;
    }

    @Override
    public String pauseTask(String taskId) {
        return "";
    }

    @Override
    public String stopTask(String taskId) {
        return "";
    }

    @Override
    public String getTaskStatus(String taskId) {
        return "";
    }

    @Override
    public Future<String> resumeTask(String taskId) {
        return null;
    }

    @Override
    public String removeTask(String taskId) {
        return "";
    }

    @Override
    public String getTaskLogs(String taskId, Integer lastLines) {
        return "";
    }

    @Override
    public String getRunningTaskInfo() {
        return "";
    }
}
