package ai.video.adapter.impl;

import ai.annotation.VideoTrack;
import ai.common.ModelService;
import ai.utils.FileUtil;
import ai.utils.OkHttpUtil;
import ai.video.adapter.Video2trackAdapter;
import ai.video.pojo.VideoJobResponse;
import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@VideoTrack(company = "private-dev", modelNames = "yolo")
public class YoloVideoAdapter extends ModelService implements Video2trackAdapter {

    private Gson gson = new Gson();
    private final Integer RETRY = 600;

    public String getCreateTaskUrl() {
        return getEndpoint() +  "/video_task";
    }

    public String getTaskProgressUrl(String taskId) {
        return getEndpoint() +  "/video_progress" + "/" + taskId;
    }

    public String getDownLoadUrl(String taskId) {
        return getEndpoint() +  "/video_out_download" + "/" + taskId;
    }


    @Override
    public VideoJobResponse track(String videoUrl) {
        if(videoUrl == null){
            return VideoJobResponse.builder().status("fail").message("videoURL is null").build();
        }
        File tempFile = null;
        if(videoUrl.startsWith("http")){
            try {
                Path tempDirectory = Files.createTempDirectory("video-track");
                String absolutePath = Paths.get(tempDirectory.toString(), UUID.randomUUID().toString()).toFile().getAbsolutePath();
                tempFile = FileUtil.urlToFile(videoUrl, absolutePath);
            } catch (IOException e) {
                return null;
            }
        }
        File file;
        if (tempFile != null) {
            file = tempFile;
        } else {
            // 假设已经是本地路径
            file = new File(videoUrl);
        }
        try {
            String taskId = createVideoJob(file);
            int tryTimes = 0;
            double lastProgress = 0;
            boolean isSuccess = false;
            while (tryTimes < RETRY) {
                Thread.sleep(1000);
                IResult progressResult = getTaskProgress(taskId);
                if(progressResult.getCode() != 0) {
                    break;
                }
                Double progress = Double.valueOf(progressResult.getData());
                if (progress.equals(100.00)) {
                    isSuccess = true;
                    break;
                }
                if(lastProgress == progress) {
                    tryTimes ++;
                } else {
                    lastProgress = progress;
                    tryTimes = 0;
                }
            }
            if(isSuccess) {
                String downLoadUrl = getDownLoadUrl(taskId);
                return VideoJobResponse.builder().data(downLoadUrl).build();
            }
        } catch (Exception e) {
            log.error("create task failed {}", e.getMessage());
        }
        return null;
    }

    private IResult getTaskProgress(String taskId) {
        try {
            String s = OkHttpUtil.get(getTaskProgressUrl(taskId));
            return gson.fromJson(s, IResult.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final ConnectionPool CONNECTION_POOL = new ConnectionPool(
            100, // 最大空闲连接数
            120, // 保持连接的时间
            TimeUnit.MINUTES
    );

    private String createVideoJob(File file) {
        OkHttpClient client = new OkHttpClient.Builder()
                .writeTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
                .connectTimeout(1, TimeUnit.MINUTES)
                .connectionPool(CONNECTION_POOL)
                .build();

        // 创建 RequestBody，用于封装文件
        RequestBody fileBody = RequestBody.create(MediaType.parse("audio/mpeg"), file);
        // 创建 MultipartBody，用于封装多个部分的数据
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(Headers.of("Content-Disposition", "form-data; name=\"file\"; filename=\"" + file.getName() + "\""),
                        fileBody)
                .build();
        // 创建请求
        Request request = new Request.Builder()
                .url(getCreateTaskUrl())
                .post(requestBody)
                .build();

        // 发送请求
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                ICreateTaskResult iCreateTaskResult = gson.fromJson(body, ICreateTaskResult.class);
                Integer code = iCreateTaskResult.getCode();
                if(code != 0) {
                    throw new RuntimeException("create failed");
                }
                return iCreateTaskResult.getData().getId();
            } else {
                throw new RuntimeException("create failed");
            }
        } catch (IOException e) {
            throw new RuntimeException("create failed");
        }

    }

    @Data
    static
    class ICreateTaskResult {
        private Integer code;
        private IResultData data;
    }

    @Data
    static
    class IResultData {
        private String id;
        private String seconds;
    }

    @Data
    static
    class IResult {
        private Integer code;
        private String data;
    }

}
