package ai.image.adapter.impl;

import ai.annotation.Img2Text;
import ai.common.ModelService;
import ai.common.exception.RRException;
import ai.common.pojo.FileRequest;
import ai.common.pojo.ImageToTextResponse;
import ai.image.adapter.IImage2TextAdapter;
import ai.openai.pojo.*;
import ai.utils.Base64Util;
import ai.vl.service.VlCompletionsService;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Img2Text(company = "private-dev", modelNames = "vl-image")
public class VlImageAdapter extends ModelService implements IImage2TextAdapter {

    @Override
    public ImageToTextResponse toText(FileRequest param) {
        File file = new File(param.getImageUrl());
        String url;
        if(file.exists()) {
            url = "data:image/png;base64," + Base64Util.fileToBase64(param.getImageUrl());
        } else if(param.getImageUrl().startsWith("http")) {
            url = param.getImageUrl();
        } else {
            throw new RRException("不支持的图片地址");
        }
        VlCompletionsService vlCompletionsService = new VlCompletionsService();
        boolean b = vlCompletionsService.hasAvailableAdapter();
        if(!b) {
            return null;
        }
        List<VlChatMessage> vlChatMessages = new ArrayList<>();
        VlChatMessage user = VlChatMessage.builder().role("user").build();
        VlChatContent describe = VlChatContent.builder().type("text").text("请用中文简单描述一下这张图片").build();
        VlChatContent imageUrl = VlChatContent.builder().type("image_url").image_url(VlChatContentImage.builder().url(url).build()).build();
        user.setContent(Lists.newArrayList(describe, imageUrl));
        vlChatMessages.add(user);
        VlChatCompletionRequest request = VlChatCompletionRequest.builder()
                .messages(vlChatMessages)
                .temperature(0.1).max_tokens(1024).stream(false).build();
        ChatCompletionResult completions = vlCompletionsService.completions(request);
        if (completions != null) {
            return ImageToTextResponse.success(completions.getChoices().get(0).getMessage().getContent());
        }
        return null;
    }
}
