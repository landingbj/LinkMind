package ai.servlet;

import ai.audio.pojo.AudioRequest;
import ai.audio.pojo.AudioTrain;
import ai.audio.pojo.AudioTrainStatus;
import ai.audio.pojo.UploadRequest;
import ai.audio.service.AudioService;
import ai.common.exception.RRException;
import ai.common.pojo.TTSRequestParam;
import ai.common.pojo.TTSResult;
import ai.common.pojo.Text2VoiceEntity;
import ai.response.RestfulResponse;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import ai.utils.DownloadUtils;
import ai.utils.WhisperResponse;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@MultipartConfig
public class AudioServlet extends RestfulServlet {
    protected Gson gson = new Gson();
    private static final long serialVersionUID = 1L;

    private final Logger log = LoggerFactory.getLogger(AudioServlet.class);

    private static LoadingCache<Text2VoiceEntity, String> cache;
    private final static int CACHE_SIZE = 100;
    private final static long EXPIRE_SECONDS = 60 * 60 * 24 * 7;

    static {
        cache = initCache(CACHE_SIZE, EXPIRE_SECONDS);
    }

    private final AudioService audioService = new AudioService();


    @Post("text2Voice")
    public void text2Voice(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Text2VoiceEntity text2VoiceEntity = gson.fromJson(requestToJson(req), Text2VoiceEntity.class);
        Map<String, String> map = new HashMap<>();
        map.put("status", "success");
        try {
            TTSRequestParam ttsRequestParam = new TTSRequestParam();
            BeanUtil.copyProperties(text2VoiceEntity, ttsRequestParam);
            TTSResult result = audioService.tts(ttsRequestParam);
            String voiceUrl = result.getResult();
            ServletContext context = req.getServletContext();
            String rootPath = context.getRealPath("");
            String filePath = rootPath + "static/voice/";
            File tempDir = new File(filePath);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            String filename = UUID.randomUUID() + "." + "wav";
            WhisperResponse whisperResponse = DownloadUtils.downloadVoiceFile(voiceUrl, "wav", filePath, filename);
            map.put("data", "static/voice/" + whisperResponse.getMsg());
        } catch (Exception e) {
            log.error("tts error: ", e);
            map.put("data", "static/tts.wav");
        }
        String json = gson.toJson(map);
        responsePrint(resp, json);
    }

    @Post("audioTrain")
    public RestfulResponse<Object> audioTrain(HttpServletRequest req) throws IOException {
        if (!ServletFileUpload.isMultipartContent(req)) {
            return RestfulResponse.error("缺少音频文件");
        }
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        UploadRequest uploadRequest = UploadRequest.builder().audioTrains(Lists.newArrayList()).build();
        ServletContext context = req.getServletContext();
        String rootPath = context.getRealPath("");
        String fileDirPath = rootPath + "static/voice/";
        File tempDir = new File(fileDirPath);
        if (!tempDir.exists()) {
            boolean mkdir = tempDir.mkdirs();
            if (!mkdir) {
                return RestfulResponse.error("创建文件夹失败");
            }
        }
        try {
            List<FileItem> items = upload.parseRequest(req);
            for (FileItem item : items) {
                if (item.isFormField()) {
                    // 处理普通表单字段
                    String fieldName = item.getFieldName();
                    String fieldValue = item.getString();
                    BeanUtil.setFieldValue(uploadRequest, fieldName, fieldValue);
                    System.out.println("Field: " + fieldName + ", Value: " + fieldValue);
                } else {
                    // 处理文件上传
                    String fileName = FilenameUtils.getName(item.getName()); // 使用 Apache Commons IO 的 FilenameUtils 来获取文件名
                    File storeFile = new File(fileDirPath, fileName);
                    item.write(storeFile);
                    System.out.println("Uploaded file: " + fileName + " -> " + storeFile.getAbsolutePath());
                    uploadRequest.getAudioTrains().add(AudioTrain.builder().file(storeFile).build());
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        try {
            audioService.train(uploadRequest);
            return RestfulResponse.sucecced(null);
        } catch (RRException e) {
            return RestfulResponse.error(e.getMessage());
        }
    }

    @Post("trainStatus")
    public AudioTrainStatus audioTrainStatus(@Body AudioRequest audioRequest) {
        return audioService.query(audioRequest);
    }

    @Post("getSpeakerIds")
    public List<String> getSpeakerIds(@Param String model) {
        String others = audioService.getOthers(model);
        if (StrUtil.isBlank(others)) {
            return Collections.emptyList();
        }
        return Arrays.stream(others.split(",")).collect(Collectors.toList());
    }

    private static LoadingCache<Text2VoiceEntity, String> initCache(int maximumSize, long expireSeconds) {
        CacheLoader<Text2VoiceEntity, String> loader;
        loader = new CacheLoader<Text2VoiceEntity, String>() {
            @Override
            public String load(Text2VoiceEntity key) {
                return key.toString();
            }
        };
        LoadingCache<Text2VoiceEntity, String> cache = CacheBuilder.newBuilder().maximumSize(maximumSize)
                .expireAfterWrite(expireSeconds, TimeUnit.SECONDS).build(loader);
        return cache;
    }
}
