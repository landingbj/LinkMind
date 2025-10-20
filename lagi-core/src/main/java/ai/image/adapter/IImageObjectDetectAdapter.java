package ai.image.adapter;

import ai.image.pojo.ImageDetectParam;
import ai.image.pojo.ObjectDetectResult;

public interface IImageObjectDetectAdapter {
    ObjectDetectResult detect(ImageDetectParam imageUrl);
}
