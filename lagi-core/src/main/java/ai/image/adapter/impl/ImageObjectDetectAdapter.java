package ai.image.adapter.impl;

import ai.common.ModelService;
import ai.image.adapter.IImageObjectDetectAdapter;
import ai.image.pojo.ImageDetectParam;
import ai.image.pojo.ObjectDetectResult;

public class ImageObjectDetectAdapter extends ModelService implements IImageObjectDetectAdapter {

    @Override
    public ObjectDetectResult detect(ImageDetectParam param) {
        String model1 = param.getModel();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if("helmet".equals(model1)) {
            return ObjectDetectResult.builder().result("1人未佩戴安全帽").build();
        } else if("uniform".equals(model1)) {
            return ObjectDetectResult.builder().result("1人未穿工作服").build();
        }
        return ObjectDetectResult.builder().result("检测失败").build();
    }

}
