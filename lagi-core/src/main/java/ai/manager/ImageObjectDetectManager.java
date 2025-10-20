package ai.manager;


import ai.image.adapter.IImageObjectDetectAdapter;

public class ImageObjectDetectManager extends AIManager<IImageObjectDetectAdapter> {

    private static final ImageObjectDetectManager INSTANCE = new ImageObjectDetectManager();
    private ImageObjectDetectManager() {

    }
    public static ImageObjectDetectManager getInstance() {
        return INSTANCE;
    }

}
