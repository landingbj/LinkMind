package ai.video.adapter.impl;

import ai.annotation.Img2Video;
import ai.annotation.VideoEnhance;
import ai.annotation.VideoTrack;
import ai.common.ModelService;
import ai.utils.HttpUtil;
import ai.video.adapter.Image2VideoAdapter;
import ai.video.adapter.Video2EnhanceAdapter;
import ai.video.adapter.Video2trackAdapter;
import ai.video.pojo.VideoEnhanceRequest;
import ai.video.pojo.VideoGeneratorRequest;
import ai.video.pojo.VideoJobResponse;
import ai.video.pojo.VideoResponse;
import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@VideoTrack(modelNames = "cascade-video")
@VideoEnhance(modelNames = "cascade-video")
@Img2Video(modelNames = "cascade-video")
public class LandingVideoCascadeAdapter extends ModelService implements Image2VideoAdapter, Video2EnhanceAdapter, Video2trackAdapter {
    private final Gson gson = new Gson();
    private static final String base_url = "https://ai.linkmind.top";
    private static final String image2videoUrl = base_url + "/image/image2video";
    private static final String enhanceUrl = base_url + "/video/video2enhance";
    private static final String videoTrackingUrl = base_url + "/video/video2tracking";

    @Override
    public VideoJobResponse image2Video(VideoGeneratorRequest videoGeneratorRequest) {
        String filePath = videoGeneratorRequest.getInputFileList().get(0).getUrl();
        String filePramName = "file";
        Map<String, String> formParmMap = new HashMap<>();
        List<File> fileList = new ArrayList<>();
        fileList.add(new File(filePath));
        String returnStr = HttpUtil.multipartUpload(image2videoUrl, filePramName, fileList, formParmMap, new HashMap<>());
        if (returnStr != null) {
            VideoResponse videoResponse = gson.fromJson(returnStr, VideoResponse.class);
            if (videoResponse.getStatus().equals("failed")) {
                return null;
            }
            return VideoJobResponse.builder().data(base_url + "/" + videoResponse.getSvdVideoUrl()).build();
        }
        return null;
    }

    @Override
    public VideoJobResponse enhance(VideoEnhanceRequest videoEnhanceRequest) {
        String filePath = videoEnhanceRequest.getVideoURL();
        String filePramName = "file";
        Map<String, String> formParmMap = new HashMap<>();
        List<File> fileList = new ArrayList<>();
        File file = new File(filePath);
        fileList.add(file);
        String returnStr = HttpUtil.multipartUpload(enhanceUrl, filePramName, fileList, formParmMap, new HashMap<>());
        if (returnStr != null) {
            VideoResponse videoResponse = gson.fromJson(returnStr, VideoResponse.class);
            if (videoResponse.getStatus().equals("failed")) {
                return null;
            }
            return VideoJobResponse.builder().data(base_url + "/" + videoResponse.getData()).build();
        }
        return null;
    }

    @Override
    public VideoJobResponse track(String videoUrl) {
        String filePath = videoUrl;
        String filePramName = "file";
        Map<String, String> formParmMap = new HashMap<>();
        List<File> fileList = new ArrayList<>();
        fileList.add(new File(filePath));
        String returnStr = HttpUtil.multipartUpload(videoTrackingUrl, filePramName, fileList, formParmMap, new HashMap<>());
        if (returnStr != null) {
            VideoResponse videoResponse = gson.fromJson(returnStr, VideoResponse.class);
            if (videoResponse.getStatus().equals("failed")) {
                return null;
            }
            return VideoJobResponse.builder().data(base_url + "/" + videoResponse.getData()).build();
        }
        return null;
    }
}
