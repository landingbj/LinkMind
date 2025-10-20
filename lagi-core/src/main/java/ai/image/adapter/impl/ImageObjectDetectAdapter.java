package ai.image.adapter.impl;

import ai.common.ModelService;
import ai.image.adapter.IImageObjectDetectAdapter;
import ai.image.pojo.ImageDetectParam;
import ai.image.pojo.ObjectDetectResult;

public class ImageObjectDetectAdapter extends ModelService implements IImageObjectDetectAdapter {

    @Override
    public ObjectDetectResult detect(ImageDetectParam param) {
        return ObjectDetectResult.builder().result("object detect result" + param.getImageUrl()).build();
    }

}
