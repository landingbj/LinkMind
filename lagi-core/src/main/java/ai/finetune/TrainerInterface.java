package ai.finetune;


import cn.hutool.json.JSONObject;

public interface TrainerInterface {

    String startTraining(String taskId, String trackId, JSONObject config);
//    String pauseContainer(String containerId);
//    String resumeContainer(String containerId);
//    String stopContainer(String containerId);
    String removeContainer(String containerId);
    String getContainerStatus(String containerId);
    String getContainerLogs(String containerId, int lines);
    String evaluate(JSONObject config);
    String predict(JSONObject config);
    //String exportModel(JSONObject config);
}
