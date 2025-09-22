package ai.migrate.service;

import ai.common.pojo.*;
import ai.dto.ModelPreferenceDto;
import ai.image.pojo.ImageEnhanceRequest;
import ai.image.service.AllImageService;
import ai.translate.TranslateService;
import ai.utils.*;
import ai.video.pojo.*;
import ai.video.service.AllVideoService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ApiService {
    private Gson gson = new Gson();
    private AllVideoService videoService = new AllVideoService();
    private AllImageService allImageService = new AllImageService();
    private TranslateService translateService = new TranslateService();

    public String generateImage(String content, HttpServletRequest req) throws IOException {
        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String filePath = rootPath + "static/img/txt2img/";
        HttpSession session = req.getSession();
        ModelPreferenceDto preference = JSONUtil.toBean((String) session.getAttribute("preference"), ModelPreferenceDto.class);
        WhisperResponse whisperResponse1 = null;

        try {
            whisperResponse1 = generateImage(preference.getImgGen(), content, filePath);
        } catch (Exception e) {
            log.error("generateImage error", e);
        }

        Map<String, String> map = new HashMap<>();
        map.put("status", "success");
        if (whisperResponse1 == null) {
            map.put("result", "images/generate_image.png");
        } else {
            map.put("result", "static/img/txt2img/" + whisperResponse1.getMsg());
        }
        String result = gson.toJson(map);
        return result;
    }

    public WhisperResponse generateImage(String model, String content, String filePath) throws IOException {
        ImageGenerationRequest request = new ImageGenerationRequest();
        request.setModel(model);
        String english = translateService.toEnglish(content);
        if (english != null) {
            request.setPrompt(english);
        } else {
            request.setPrompt(content);
        }
        ImageGenerationResult imageGenerationResult = allImageService.generations(request);
        AtomicReference<WhisperResponse> whisperResponse1 = new AtomicReference<>();
        Optional.ofNullable(imageGenerationResult).ifPresent(r -> {
            if ("base64".equals(r.getDataType())) {
                try {
                    File file = ImageUtil.base64ToFile(r.getData().get(0).getBase64Image());
                    File img = new File(filePath, file.getName());
                    FileUtils.copyFile(file, img);
                    WhisperResponse whisperResponse = new WhisperResponse(1, img.getName());
                    whisperResponse1.set(whisperResponse);
                } catch (Exception ignored) {

                }
            } else {
                String url = r.getData().get(0).getUrl();
                File tempDir = new File(filePath);
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                whisperResponse1.set(DownloadUtils.downloadFile(url, "png", filePath));
            }
        });
        return whisperResponse1.get();
    }

    public String enhanceImage(String lastImageFile, HttpServletRequest req) throws IOException {
        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String filePath = rootPath + "static/img/enhance/";
        File tempDir = new File(filePath);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        JsonObject finalResult = new JsonObject();
        try {
            File file = new File(lastImageFile);
            String imageUrl = FileUploadUtil.enhanceImageUpload(file);
            ImageEnhanceResult enhance = allImageService.enhance(ImageEnhanceRequest.builder().imageUrl(imageUrl).build());
            WhisperResponse whisperResponse1;
            if (Objects.equals(enhance.getType(), "url")) {
                whisperResponse1 = DownloadUtils.downloadFile(enhance.getData(), "png", filePath);
            } else {
                File temp = ImageUtil.base64ToFile(enhance.getData());
                whisperResponse1 = new WhisperResponse(1, temp.getName());
                FileUtils.copyFile(temp, new File(filePath + whisperResponse1.getMsg()));
            }
            finalResult.addProperty("enhanceImageUrl", "static/img/enhance/" + whisperResponse1.getMsg());
        } catch (Exception e) {
            log.error("enhanceImage error: ", e);
            String outputPath = normalizePath(lastImageFile).replace(normalizePath(rootPath), "");
            finalResult.addProperty("enhanceImageUrl", outputPath);
        }
        finalResult.addProperty("status", "success");
        return gson.toJson(finalResult);
    }

    public String generateVideoByText(String content, HttpServletRequest req) throws IOException {
        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String filePath = rootPath + "static/img/txt2img/";
        HttpSession session = req.getSession();
        ModelPreferenceDto preference = JSONUtil.toBean((String) session.getAttribute("preference"), ModelPreferenceDto.class);
        WhisperResponse whisperResponse1 = generateImage(preference != null ? preference.getImgGen() : null, content.replaceAll("视频", "图片"), filePath);
        String imageFile = filePath + whisperResponse1.getMsg();
        return generateVideo(imageFile, req);
    }


    public String generateVideoByTextV2(String content, HttpServletRequest req) throws IOException {
        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String filePath = rootPath + "static/video/txt2video/";
        File file = new File(filePath);
        if (!file.exists()) {
            file.mkdirs();
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        try {
            HttpSession session = req.getSession();
            ModelPreferenceDto preference = JSONUtil.toBean((String) session.getAttribute("preference"), ModelPreferenceDto.class);
            VideoJobResponse video = videoService.toVideo(ImageGenerationRequest.builder().model(preference.getImgGen()).prompt(content).build());
            String data = video.getData();
            WhisperResponse whisperResponse = DownloadUtils.downloadFile(data, "mp4", filePath);
            result.addProperty("svdVideoUrl", "static/video/txt2video/" + whisperResponse.getMsg());
        } catch (Exception e) {
            log.error("generate video error", e);
            result.addProperty("svdVideoUrl", "video/generate_video.mp4");
        }

        return gson.toJson(result);
    }

    public String generateVideo(String lastImageFile, HttpServletRequest req) throws IOException {
        File file = new File(lastImageFile);
        VideoGeneratorRequest build = VideoGeneratorRequest.builder()
                .inputFileList(Lists.newArrayList(InputFile.builder().url(file.getAbsolutePath()).build()))
                .build();
        String imageUrl = videoService.image2Video(build).getData();

        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String filePath = rootPath + "static/img/svd/";
        File tempDir = new File(filePath);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        WhisperResponse whisperResponse1 = DownloadUtils.downloadFile(imageUrl, "mp4", filePath);
        JsonObject result = new JsonObject();
        result.addProperty("svdVideoUrl", "static/img/svd/" + whisperResponse1.getMsg());
        result.addProperty("status", "success");
        return gson.toJson(result);
    }

    public String imageToText(String lastImageFile, HttpServletRequest req) throws IOException {
        File file = new File(lastImageFile);
        ImageToTextResponse text = allImageService.toText(FileRequest.builder().imageUrl(file.getAbsolutePath()).build());
        if (text.getSamUrl() == null) {
            ServletContext context = req.getServletContext();
            String rootPath = context.getRealPath("");
            String filePath = rootPath + "static/img/split/";
            File tempDir = new File(filePath);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            String filename = UUID.randomUUID() + ".png";
            FileUtils.copyFile(file, new File(filePath, filename));
            text.setSamUrl("static/img/split/" + filename);
        }
        if (isEnglishWithSpacesAndPunctuation(text.getCaption())) {
            String chinese = translateService.toChinese(text.getCaption());
            if (chinese != null) {
                text.setCaption(chinese);
            }
        }
        return gson.toJson(text);
    }

    public static boolean isEnglishWithSpacesAndPunctuation(String text) {
        return text.matches("[a-zA-Z\\s.,!?'-]+");
    }

    public String motInference(String lastVideoFile, HttpServletRequest req) throws IOException {
        File file = new File(lastVideoFile);

        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String filePath = rootPath + "static/video/";
        File tempDir = new File(filePath);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        JsonObject result = new JsonObject();

        result.addProperty("type", "mot");
        result.addProperty("status", "success");
        try {
            VideoJobResponse track = videoService.track(VideoTackRequest.builder().videoUrl(file.getAbsolutePath()).build());
            WhisperResponse whisperResponse1 = DownloadUtils.downloadFile(track.getData(), "mp4", filePath);
            result.addProperty("data", "static/video/" + whisperResponse1.getMsg());
        } catch (Exception e) {
            log.error("video tracking error: ", e);
            result.addProperty("data", "video/generate_video.mp4");
        }

        return gson.toJson(result);
    }

    public String mmeditingInference(String lastVideoFile, HttpServletRequest req) throws IOException {
        File file = new File(lastVideoFile);
        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String filePath = rootPath + "static/video/";
        File tempDir = new File(filePath);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "success");
        result.addProperty("type", "mmediting");

        try {
            VideoEnhanceRequest videoEnhanceRequest = VideoEnhanceRequest.builder().videoURL(file.getAbsolutePath()).build();
            VideoJobResponse enhance = videoService.enhance(videoEnhanceRequest);
            WhisperResponse whisperResponse1 = DownloadUtils.downloadFile(enhance.getData(), "mp4", filePath);
            result.addProperty("data", "static/video/" + whisperResponse1.getMsg());
        } catch (Exception e) {
            log.error("video enhance error: ", e);
            result.addProperty("data", "video/generate_video.mp4");
        }
        return gson.toJson(result);
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return path.replace("\\", "/");
    }
}
