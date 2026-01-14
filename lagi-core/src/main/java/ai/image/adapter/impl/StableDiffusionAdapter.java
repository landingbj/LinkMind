package ai.image.adapter.impl;

import ai.annotation.ImgGen;
import ai.common.ModelService;
import ai.common.pojo.ImageGenerationData;
import ai.common.pojo.ImageGenerationRequest;
import ai.common.pojo.ImageGenerationResult;
import ai.image.adapter.IImageGenerationAdapter;
import ai.image.util.ImageUrlToBase64Util;
import ai.utils.OkHttpUtil;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@ImgGen(modelNames = "stable-diffusion")
public class StableDiffusionAdapter extends ModelService implements IImageGenerationAdapter {

    private static final Logger log = LoggerFactory.getLogger(StableDiffusionAdapter.class);
    private Gson gson = new Gson();

    private String getText2ImageUrl() {
        return getEndpoint() + "/txt2img";
    }

    @Override
    public ImageGenerationResult generations(ImageGenerationRequest request) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("prompt", request.getPrompt());
        bodyMap.put("num_inference_steps", request.getStep());
        String body = gson.toJson(bodyMap);
        try {
            String post = OkHttpUtil.post(getText2ImageUrl(), body);
            IText2ImageResult result = gson.fromJson(post, IText2ImageResult.class);
            if(!result.getStatus().equals("success")) {
                throw new RuntimeException("Text to image failed");
            }
            String url = getEndpoint() + "/" + result.getResult();
            String base64Image = ImageUrlToBase64Util.imageUrlToBase64(url);
            ImageGenerationData data = ImageGenerationData.builder()
                    .base64Image(base64Image)
                    .url(url)
                    .build();
            return ImageGenerationResult.builder()
                    .data(Lists.newArrayList(data))
                    .dataType("base64")
                    .created(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("Stable diffusion adapter error: {}", e.getMessage());
        }
        return null;
    }

    @Data
    static
    class IText2ImageResult {
        private String status;
        private String result;
    }
}
