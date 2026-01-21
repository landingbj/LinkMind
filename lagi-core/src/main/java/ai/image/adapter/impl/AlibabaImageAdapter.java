package ai.image.adapter.impl;

import ai.annotation.Img2Text;
import ai.annotation.ImgGen;
import ai.common.ModelService;
import ai.common.pojo.FileRequest;
import ai.common.pojo.ImageGenerationData;
import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.common.pojo.ImageToTextResponse;
import ai.image.adapter.IImage2TextAdapter;
import ai.image.adapter.IImageGenerationAdapter;
import ai.utils.Base64Util;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Img2Text(modelNames = "qwen3-vl-flash")
@ImgGen(modelNames = "qwen-image")
public class AlibabaImageAdapter extends ModelService implements IImage2TextAdapter, IImageGenerationAdapter {
    private final Logger logger = LoggerFactory.getLogger(AlibabaImageAdapter.class);
    private static final String DASHSCOPE_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DASHSCOPE_IMG_GEN_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String DEFAULT_PROMPT = "用简短的中文描述图片";
    private final Gson gson = new Gson();

    @Override
    public boolean verify() {
        return getApiKey() != null && !getApiKey().startsWith("you");
    }

    @Override
    public ImageToTextResponse toText(FileRequest param) {
        try {
            String modelName = param.getModel() != null ? param.getModel() : getModel();
            if (modelName == null) {
                modelName = "qwen3-vl-flash";
            }
            String prompt = DEFAULT_PROMPT;
            if (param.getExtendParam() != null && param.getExtendParam().get("prompt") != null) {
                prompt = param.getExtendParam().get("prompt").toString();
            }
            List<ContentItem> contentItems = new ArrayList<>();
            String url = "data:image/png;base64," + Base64Util.fileToBase64(param.getImageUrl());
            contentItems.add(ContentItem.builder()
                    .type("image_url")
                    .imageUrl(ImageUrlContent.builder().url(url).build())
                    .build());
            contentItems.add(ContentItem.builder()
                    .type("text")
                    .text(prompt)
                    .build());

            List<Message> messages = new ArrayList<>();
            messages.add(Message.builder()
                    .role("user")
                    .content(contentItems)
                    .build());
            DashScopeRequest request = DashScopeRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .build();

            String requestBody = gson.toJson(request);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + getApiKey());
            headers.put("Content-Type", "application/json");

            String response = OkHttpUtil.post(DASHSCOPE_API_URL, headers, new HashMap<>(), requestBody);

            DashScopeResponse dashScopeResponse = gson.fromJson(response, DashScopeResponse.class);
            if (dashScopeResponse != null && dashScopeResponse.getChoices() != null
                    && !dashScopeResponse.getChoices().isEmpty()) {
                String content = dashScopeResponse.getChoices().get(0).getMessage().getContent();
                return ImageToTextResponse.success(content);
            }
            logger.error("Alibaba DashScope API returned empty or invalid response");
            return ImageToTextResponse.error();
        } catch (Exception e) {
            logger.error("Error calling Alibaba DashScope API: {}", e.getMessage(), e);
            return ImageToTextResponse.error();
        }
    }

    @Override
    public ImageGenerationResult generations(ImageGenerationRequest request) {
        try {
            String modelName = request.getModel() != null ? request.getModel() : getModel();
            if (modelName == null) {
                modelName = "qwen-image";
            }

            List<ImgGenContentItem> contentItems = new ArrayList<>();
            contentItems.add(ImgGenContentItem.builder().text(request.getPrompt()).build());

            List<ImgGenMessage> messages = new ArrayList<>();
            messages.add(ImgGenMessage.builder()
                    .role("user")
                    .content(contentItems)
                    .build());

            ImgGenRequest imgGenRequest = ImgGenRequest.builder()
                    .model(modelName)
                    .input(ImgGenInput.builder().messages(messages).build())
                    .build();

            String requestBody = gson.toJson(imgGenRequest);
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + getApiKey());
            headers.put("Content-Type", "application/json");

            String response = OkHttpUtil.post(DASHSCOPE_IMG_GEN_URL, headers, new HashMap<>(), requestBody);

            ImgGenResponse imgGenResponse = gson.fromJson(response, ImgGenResponse.class);
            if (imgGenResponse != null && imgGenResponse.getOutput() != null
                    && imgGenResponse.getOutput().getChoices() != null
                    && !imgGenResponse.getOutput().getChoices().isEmpty()) {
                List<ImageGenerationData> dataList = new ArrayList<>();
                for (ImgGenChoice choice : imgGenResponse.getOutput().getChoices()) {
                    if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                        for (ImgGenResponseContent content : choice.getMessage().getContent()) {
                            if (content.getImage() != null) {
                                dataList.add(ImageGenerationData.builder().url(content.getImage()).build());
                            }
                        }
                    }
                }
                if (!dataList.isEmpty()) {
                    return ImageGenerationResult.builder()
                            .created(System.currentTimeMillis())
                            .data(dataList)
                            .dataType("url")
                            .build();
                }
            }
            logger.error("Alibaba DashScope image generation API returned empty or invalid response");
            return null;
        } catch (Exception e) {
            logger.error("Error calling Alibaba DashScope image generation API: {}", e.getMessage(), e);
            return null;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class DashScopeRequest {
        private String model;
        private List<Message> messages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class Message {
        private String role;
        private List<ContentItem> content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ContentItem {
        private String type;
        @SerializedName("image_url")
        private ImageUrlContent imageUrl;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImageUrlContent {
        private String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class DashScopeResponse {
        private List<Choice> choices;
        private String object;
        private Usage usage;
        private Long created;
        @SerializedName("system_fingerprint")
        private String systemFingerprint;
        private String model;
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Choice {
        private ResponseMessage message;
        @SerializedName("finish_reason")
        private String finishReason;
        private Integer index;
        private Object logprobs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ResponseMessage {
        private String content;
        @SerializedName("reasoning_content")
        private String reasoningContent;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Usage {
        @SerializedName("prompt_tokens")
        private Integer promptTokens;
        @SerializedName("completion_tokens")
        private Integer completionTokens;
        @SerializedName("total_tokens")
        private Integer totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenRequest {
        private String model;
        private ImgGenInput input;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenInput {
        private List<ImgGenMessage> messages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenMessage {
        private String role;
        private List<ImgGenContentItem> content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenContentItem {
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenResponse {
        private ImgGenOutput output;
        private ImgGenUsage usage;
        @SerializedName("request_id")
        private String requestId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenOutput {
        private List<ImgGenChoice> choices;
        @SerializedName("task_metric")
        private ImgGenTaskMetric taskMetric;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenChoice {
        @SerializedName("finish_reason")
        private String finishReason;
        private ImgGenResponseMessage message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenResponseMessage {
        private String role;
        private List<ImgGenResponseContent> content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenResponseContent {
        private String image;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenTaskMetric {
        @SerializedName("TOTAL")
        private Integer total;
        @SerializedName("FAILED")
        private Integer failed;
        @SerializedName("SUCCEEDED")
        private Integer succeeded;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImgGenUsage {
        private Integer width;
        @SerializedName("image_count")
        private Integer imageCount;
        private Integer height;
    }
}
