package ai.image.adapter.impl;

import ai.annotation.Img2Text;
import ai.annotation.ImgEnhance;
import ai.annotation.ImgGen;
import ai.common.ModelService;
import ai.common.pojo.*;
import ai.image.adapter.IImage2TextAdapter;
import ai.image.adapter.IImageGenerationAdapter;
import ai.image.adapter.ImageEnhanceAdapter;
import ai.image.pojo.ImageEnhanceRequest;
import ai.manager.OSSManager;
import ai.oss.UniversalOSS;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;

import java.io.File;


@ImgEnhance(modelNames = "cascade-image")
@Img2Text(modelNames = "cascade-image")
@ImgGen(modelNames = "cascade-image")
public class LandingImageCascadeAdapter extends ModelService implements IImageGenerationAdapter, ImageEnhanceAdapter, IImage2TextAdapter {

    private final String base_url = "https://ai.linkmind.top/v1/image";

    private final String generate_url = base_url + "/generations";

    private final String enhance_url = base_url + "/image2enhance";

    private final String text_url  = base_url + "/image2text";

    private Gson gson = new Gson();

    private UniversalOSS oss = OSSManager.getInstance().getOss("landing");


    @Override
    public ImageGenerationResult generations(ImageGenerationRequest request) {
        try {
            String post = OkHttpUtil.post(generate_url, gson.toJson(request));
            return gson.fromJson(post, ImageGenerationResult.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public ImageEnhanceResult enhance(ImageEnhanceRequest imageEnhanceRequest) {
        try {
            if (!imageEnhanceRequest.getImageUrl().startsWith("http")) {
                String upload = oss.upload("img2enhance", new File(imageEnhanceRequest.getImageUrl()));
                imageEnhanceRequest.setImageUrl(upload);
            }
            String post = OkHttpUtil.post(enhance_url, gson.toJson(imageEnhanceRequest));
            return gson.fromJson(post, ImageEnhanceResult.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ImageToTextResponse toText(FileRequest param) {
        try {
            String imageUrl = param.getImageUrl();
            if (!imageUrl.startsWith("http")) {
                String upload = oss.upload("img2txt", new File(imageUrl));
                param.setImageUrl(upload);
            }
            String post = OkHttpUtil.post(text_url, gson.toJson(param));
            return gson.fromJson(post, ImageToTextResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
