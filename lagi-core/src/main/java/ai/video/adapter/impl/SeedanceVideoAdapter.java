package ai.video.adapter.impl;

import ai.annotation.Img2Video;
import ai.annotation.Text2Video;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.oss.UniversalOSS;
import ai.video.adapter.Image2VideoAdapter;
import ai.video.adapter.Text2VideoAdapter;
import ai.video.adapter.VideoQuery;
import ai.video.pojo.InputFile;
import ai.video.pojo.VideoGeneratorRequest;
import ai.video.pojo.VideoJobQueryResponse;
import ai.video.pojo.VideoJobResponse;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Volcengine Ark Seedance asynchronous video-generation adapter.
 *
 * <p>The configured {@code api_address} is the task-create endpoint. The default
 * is Ark's content-generation endpoint; task status is queried at
 * {@code {api_address}/{taskId}}. Image inputs must be public URLs, or an OSS
 * provider must be configured on the model entry to upload local files.</p>
 */
@Text2Video(modelNames = {
        "doubao-seedance-2-5", "doubao-seedance-2-0", "doubao-seedance-1-5-pro",
        "doubao-seedance-1-0-pro", "doubao-seedance-1-0-pro-fast"
})
@Img2Video(modelNames = {
        "doubao-seedance-2-5", "doubao-seedance-2-0", "doubao-seedance-1-5-pro",
        "doubao-seedance-1-0-pro", "doubao-seedance-1-0-pro-fast"
})
public class SeedanceVideoAdapter extends ModelService
        implements Text2VideoAdapter, Image2VideoAdapter, VideoQuery {

    public static final String DEFAULT_TASK_ENDPOINT =
            "https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks";
    private static final int POLL_INTERVAL_SECONDS = 5;
    private static final int MAX_POLL_ATTEMPTS = 120;
    private static final Logger log = LoggerFactory.getLogger(SeedanceVideoAdapter.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build();
    private UniversalOSS universalOSS;

    @Override
    public VideoJobResponse toVideo(ai.common.pojo.ImageGenerationRequest request) {
        if (request == null || StrUtil.isBlank(request.getPrompt())) {
            throw new RRException("Seedance text-to-video requires a prompt");
        }
        JSONArray content = new JSONArray();
        content.add(new JSONObject().set("type", "text").set("text", request.getPrompt()));
        return submitAndWait(buildTaskRequest(content));
    }

    @Override
    public VideoJobResponse image2Video(VideoGeneratorRequest request) {
        if (request == null || request.getInputFileList() == null || request.getInputFileList().isEmpty()) {
            throw new RRException("Seedance image-to-video requires at least one image");
        }

        JSONArray content = new JSONArray();
        if (request.getIntPutText() != null) {
            for (String text : request.getIntPutText()) {
                if (StrUtil.isNotBlank(text)) {
                    content.add(new JSONObject().set("type", "text").set("text", text));
                }
            }
        }
        for (InputFile input : request.getInputFileList()) {
            String imageUrl = resolveImageUrl(input);
            JSONObject imageUrlNode = new JSONObject().set("url", imageUrl);
            content.add(new JSONObject().set("type", "image_url").set("image_url", imageUrlNode));
        }
        return submitAndWait(buildTaskRequest(content));
    }

    @Override
    public VideoJobQueryResponse query(String jobId) {
        if (StrUtil.isBlank(jobId)) {
            return null;
        }
        try {
            JSONObject response = executeGet(taskEndpoint() + "/" + jobId);
            return toQueryResponse(jobId, response);
        } catch (IOException e) {
            log.warn("Seedance task query failed for {}: {}", jobId, e.getMessage());
            return VideoJobQueryResponse.builder().taskId(jobId).status(3).build();
        }
    }

    private VideoJobResponse submitAndWait(JSONObject request) {
        try {
            JSONObject response = executePost(taskEndpoint(), request);
            String taskId = firstString(response, "id", "task_id", "taskId");
            if (StrUtil.isBlank(taskId)) {
                throw new RRException("Seedance did not return a task id: " + response);
            }
            return waitForResult(taskId);
        } catch (IOException e) {
            throw new RRException("Seedance request failed: " + e.getMessage());
        }
    }

    private VideoJobResponse waitForResult(String taskId) {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            VideoJobQueryResponse result = query(taskId);
            if (result == null) {
                break;
            }
            if (result.getStatus() == 2 && StrUtil.isNotBlank(result.getVideoUrl())) {
                return VideoJobResponse.builder().status("succeeded").jobId(taskId)
                        .data(result.getVideoUrl()).build();
            }
            if (result.getStatus() >= 3) {
                return VideoJobResponse.builder().status("failed").jobId(taskId)
                        .message("Seedance task failed").build();
            }
            sleep();
        }
        return VideoJobResponse.builder().status("timeout").jobId(taskId)
                .message("Seedance task did not finish within 10 minutes").build();
    }

    private JSONObject buildTaskRequest(Object content) {
        return new JSONObject().set("model", getModel()).set("content", content);
    }

    private VideoJobQueryResponse toQueryResponse(String taskId, JSONObject response) {
        String status = firstString(response, "status", "state");
        String videoUrl = findVideoUrl(response);
        int normalizedStatus;
        if ("succeeded".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)
                || "completed".equalsIgnoreCase(status)) {
            normalizedStatus = 2;
        } else if ("failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)
                || "canceled".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
            normalizedStatus = 3;
        } else {
            normalizedStatus = 1;
        }
        return VideoJobQueryResponse.builder().taskId(taskId).status(normalizedStatus)
                .videoUrl(videoUrl).build();
    }

    private String resolveImageUrl(InputFile input) {
        if (input == null || StrUtil.isBlank(input.getUrl())) {
            throw new RRException("Seedance image input URL is empty");
        }
        if (isHttpUrl(input.getUrl())) {
            return input.getUrl();
        }
        if (universalOSS == null) {
            throw new RRException("Seedance local image input requires a configured OSS provider");
        }
        File file = new File(input.getUrl());
        if (!file.isFile()) {
            throw new RRException("Seedance image file does not exist: " + input.getUrl());
        }
        return universalOSS.upload("seedance/" + file.getName(), file);
    }

    private JSONObject executePost(String url, JSONObject payload) throws IOException {
        Request request = new Request.Builder().url(url).post(RequestBody.create(payload.toString(), JSON))
                .header("Authorization", "Bearer " + getApiKey())
                .header("Content-Type", "application/json").build();
        return execute(request);
    }

    private JSONObject executeGet(String url) throws IOException {
        Request request = new Request.Builder().url(url).get()
                .header("Authorization", "Bearer " + getApiKey()).build();
        return execute(request);
    }

    private JSONObject execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return JSONUtil.parseObj(body);
        }
    }

    private String taskEndpoint() {
        return StrUtil.isBlank(getApiAddress()) ? DEFAULT_TASK_ENDPOINT : getApiAddress().replaceAll("/+$", "");
    }

    private static String findVideoUrl(JSONObject response) {
        return findNestedVideoUrl(response);
    }

    private static String findNestedVideoUrl(Object node) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            for (String key : new String[] {"video_url", "videoUrl"}) {
                String value = object.getStr(key);
                if (StrUtil.isNotBlank(value)) {
                    return value;
                }
            }
            for (String key : object.keySet()) {
                String value = findNestedVideoUrl(object.get(key));
                if (StrUtil.isNotBlank(value)) {
                    return value;
                }
            }
        } else if (node instanceof JSONArray) {
            for (Object item : (JSONArray) node) {
                String value = findNestedVideoUrl(item);
                if (StrUtil.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.getStr(key);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isHttpUrl(String value) {
        try {
            String protocol = new URL(value).getProtocol();
            return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(POLL_INTERVAL_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
