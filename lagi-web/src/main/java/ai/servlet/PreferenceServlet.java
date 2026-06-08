package ai.servlet;

import ai.annotation.*;
import ai.common.ModelService;
import ai.config.ContextLoader;
import ai.config.pojo.ModelFunction;
import ai.dto.ModelInfo;
import ai.dto.ModelPreferenceDto;
import ai.dto.ModelsResponse;
import ai.llm.adapter.ILlmAdapter;
import ai.llm.utils.CacheManager;
import ai.llm.adapter.impl.ProxyLlmAdapter;
import ai.manager.*;
import ai.migrate.dao.UserModelPreferenceDao;
import ai.servlet.annotation.Body;
import ai.servlet.annotation.Get;
import ai.servlet.annotation.Param;
import ai.servlet.annotation.Post;
import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

public class PreferenceServlet extends RestfulServlet {

    private UserModelPreferenceDao userModelPreferenceDao = new UserModelPreferenceDao();

    private final Map<String, List<ModelInfo>> modelInfoMap = new HashMap<>();


    public PreferenceServlet(){
        List<ModelInfo> aLLMs = LlmManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, LLM.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aTTSs = TTSManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, TTS.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aASRs = ASRManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, ASR.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aImg2Texts = Image2TextManger.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, Img2Text.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aImgGens = ImageGenerationManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, ImgGen.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aImgEnhances = ImageEnhanceManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, ImgEnhance.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aImg2Videos = Image2VideoManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, Img2Video.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aText2Videos = Text2VideoManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, Text2Video.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aVideoEnhances= Video2EnhanceManger.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, VideoEnhance.class).stream()).collect(Collectors.toList());
        List<ModelInfo> aVideoTracks= Video2TrackManager.getInstance().getAllAdapters().stream().map(a-> (ModelService) a).flatMap(m->convert2ModelInfo(m, VideoTrack.class).stream()).collect(Collectors.toList());
        modelInfoMap.put("llm", aLLMs);
        modelInfoMap.put("tts", aTTSs);
        modelInfoMap.put("asr", aASRs);
        modelInfoMap.put("img2Text", aImg2Texts);
        modelInfoMap.put("imgGen", aImgGens);
        modelInfoMap.put("imgEnhance", aImgEnhances);
        modelInfoMap.put("img2Video", aImg2Videos);
        modelInfoMap.put("text2Video", aText2Videos);
        modelInfoMap.put("videoEnhance", aVideoEnhances);
        modelInfoMap.put("videoTrack", aVideoTracks);

    }

    private <A extends Annotation> List<ModelInfo> convert2ModelInfo(ModelService modelService, Class<A> annotationClass) {
        String [] modelNames = {};
        // Use the modelNames configured in the yaml file if they exist and are not empty
        String configuredModel = modelService.getModel();
        if (configuredModel != null && !configuredModel.trim().isEmpty()) {
            modelNames = Arrays.stream(configuredModel.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }

        return Arrays.stream(modelNames)
                .map(m-> ModelInfo.builder().model(m).enabled(modelService.getEnable()).company("").description("").build())
                .collect(Collectors.toList());
    }



    private void setActivate(List<ModelInfo> orDefault, String activeModel) {
        orDefault.stream().filter(o->o.getModel().equals(activeModel)).findAny().ifPresent(o->{
            o.setActivate(true);
        });
    }

    private String getModelName(String userModel, String defaultModel) {
        return userModel == null  || (!CacheManager.getInstance().get(userModel))? defaultModel : userModel;
    }

    private <T> String getModelService(AIManager<T> aiManager) {
        if(!aiManager.getAdapters().isEmpty()) {
            ModelService modelService =  aiManager.getAdapters().stream()
                    .map(a-> (ModelService) a)
                    .filter(a-> CacheManager.getInstance().get(a.getModel()))
                    .findAny()
                    .orElse(null);
            if (modelService != null) {
                return modelService.getModel();
            }
        }
        return null;
    }

    @Get("getModels")
    public ModelsResponse getModels(@Param("type") String type, @Param("userId") String userId, HttpServletRequest request) {
        ModelPreferenceDto preferenceRequest = loadUserPreference(userId, request);
        List<ModelInfo> orDefault = modelInfoMap.getOrDefault(type, Collections.emptyList()).stream()
                .map(a->{
                    a = ObjectUtil.cloneByStream(a);
                    a.setEnabled(a.getEnabled() && CacheManager.getInstance().get(a.getModel()));
                    return a;
                })
                .collect(Collectors.toList());

        if("llm".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getLlm(), getModelService(LlmManager.getInstance())));
        }
        if("asr".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getAsr(), getModelService(ASRManager.getInstance())));
        }
        if("tts".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getTts(), getModelService(TTSManager.getInstance())));
        }
        if("img2Text".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getImg2Text(), getModelService(Image2TextManger.getInstance())));
        }
        if("imgGen".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getImgGen(), getModelService(ImageGenerationManager.getInstance())));
        }
        if("imgEnhance".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getImgEnhance(), getModelService(ImageEnhanceManager.getInstance())));
        }
        if("img2Video".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getImg2Video(), getModelService(Image2VideoManager.getInstance())));
        }
        if("text2Video".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getText2Video(), getModelService(Text2VideoManager.getInstance())));
        }
        if("videoEnhance".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getVideoEnhance(), getModelService(Video2EnhanceManger.getInstance())));
        }
        if("videoTrack".equals(type)) {
            setActivate(orDefault, getModelName(preferenceRequest.getVideoTrack(), getModelService(Video2TrackManager.getInstance())));
        }
        return ModelsResponse.builder()
                .models(orDefault)
                .consoleDefaultModel(getConsoleDefaultModel(type))
                .build();
    }

    private String getConsoleDefaultModel(String type) {
        if (!"llm".equals(type) || ContextLoader.configuration == null
                || ContextLoader.configuration.getFunctions() == null) {
            return null;
        }
        ModelFunction chat = ContextLoader.configuration.getFunctions().getChat();
        if (chat == null) {
            return null;
        }
        String consoleDefaultModel = chat.getConsoleDefaultModel();
        if (consoleDefaultModel == null || consoleDefaultModel.trim().isEmpty()) {
            return null;
        }
        return consoleDefaultModel.trim();
    }

    private ModelPreferenceDto loadUserPreference(String fingerId, HttpServletRequest request) {
        HttpSession session = request.getSession();
        Object preference = session.getAttribute("preference");
        ModelPreferenceDto userModelPreference = null;
        if(preference == null) {
            userModelPreference = userModelPreferenceDao.getUserModelPreference(fingerId);
            session.setAttribute("preference", JSONUtil.toJsonStr(userModelPreference));
        } else {
            userModelPreference = JSONUtil.toBean((String) preference, ModelPreferenceDto.class);
        }
        return userModelPreference;
    }


    @Post("savePreference")
    public Integer savePreference(@Body ModelPreferenceDto preferenceRequest, HttpServletRequest request) {

        Integer res = userModelPreferenceDao.saveOrUpdate(preferenceRequest);
        request.getSession().removeAttribute("preference");
        loadUserPreference(preferenceRequest.getFinger(), request);
        return res;
    }

    @Get("getPreference")
    public ModelPreferenceDto getPreference(@Param("userId") String userId) {
        return userModelPreferenceDao.getUserModelPreference(userId);
    }


    @Get("clearPreference")
    public Integer clearPreference(@Param("userId") String userId, HttpServletRequest request) {
        Integer res = userModelPreferenceDao.remove(userId);
        if(res > 0) {
            HttpSession session = request.getSession();
            session.removeAttribute("preference");
        }
        return res;
    }

}
