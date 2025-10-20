package ai.image.service;

import ai.common.pojo.*;
import ai.image.adapter.IImage2TextAdapter;
import ai.image.adapter.IImageGenerationAdapter;
import ai.image.adapter.IImageObjectDetectAdapter;
import ai.image.adapter.ImageEnhanceAdapter;
import ai.image.pojo.ImageDetectParam;
import ai.image.pojo.ImageEnhanceRequest;
import ai.image.pojo.ObjectDetectResult;
import ai.manager.Image2TextManger;
import ai.manager.ImageEnhanceManager;
import ai.manager.ImageGenerationManager;
import ai.manager.ImageObjectDetectManager;

import java.util.List;

public class AllImageService implements IImage2TextAdapter, ImageEnhanceAdapter, IImageGenerationAdapter, IImageObjectDetectAdapter {

    @Override
    public ImageToTextResponse toText(FileRequest param) {
        if(param.getModel() != null) {
            IImage2TextAdapter adapter = Image2TextManger.getInstance().getAdapter(param.getModel());
            if(adapter != null) {
                return adapter.toText(param);
            }
        }
        List<IImage2TextAdapter> adapters = Image2TextManger.getInstance().getAdapters();
        for (IImage2TextAdapter adapter : adapters) {
            if(adapter != null) {
                return adapter.toText(param);
            }
        }
        return null;
    }

    @Override
    public ImageEnhanceResult enhance(ImageEnhanceRequest imageEnhanceRequest) {
        if(imageEnhanceRequest.getModel() != null) {
            ImageEnhanceAdapter adapter = ImageEnhanceManager.getInstance().getAdapter(imageEnhanceRequest.getModel());
            if(adapter != null) {
                return adapter.enhance(imageEnhanceRequest);
            }
        }
        List<ImageEnhanceAdapter> adapters = ImageEnhanceManager.getInstance().getAdapters();
        for (ImageEnhanceAdapter adapter : adapters) {
            if(adapter != null) {
                return adapter.enhance(imageEnhanceRequest);
            }
        }
        return null;
    }

    @Override
    public ImageGenerationResult generations(ImageGenerationRequest request) {
        if(request.getModel() != null) {
            IImageGenerationAdapter adapter = ImageGenerationManager.getInstance().getAdapter(request.getModel());
            if(adapter != null) {
                return adapter.generations(request);
            }
        }
        List<IImageGenerationAdapter> adapters = ImageGenerationManager.getInstance().getAdapters();
        for (IImageGenerationAdapter adapter : adapters) {
            if(adapter != null) {
                return adapter.generations(request);
            }
        }
        return null;
    }

    @Override
    public ObjectDetectResult detect(ImageDetectParam param) {
        if(param.getModel() != null) {
            IImageObjectDetectAdapter adapter = ImageObjectDetectManager.getInstance().getAdapter(param.getModel());
            if(adapter != null) {
                return adapter.detect(param);
            }
        }
        List<IImageObjectDetectAdapter> adapters = ImageObjectDetectManager.getInstance().getAdapters();
        for (IImageObjectDetectAdapter adapter : adapters) {
            if(adapter != null) {
                return adapter.detect(param);
            }
        }
        return null;
    }
}
