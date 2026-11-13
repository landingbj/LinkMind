package ai.audio.adapter.impl;

import ai.annotation.ASR;
import ai.annotation.TTS;
import ai.audio.adapter.IAudioAdapter;
import ai.common.ModelService;
import ai.common.pojo.*;
import ai.utils.ApiInvokeUtil;
import ai.utils.LagiGlobal;
import ai.utils.OkHttpUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import okhttp3.*;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ASR(company = "landingbj", modelNames = "cascade-asr")
@TTS(company = "landingbj", modelNames = "cascade-tts")
public class LandingAudioCascadeAdapter extends ModelService implements IAudioAdapter {

    private Gson gson = new Gson();

    private final String BASE_URL = "https://ai.linkmind.top";

    private final String ASR_URL =  BASE_URL + "/search/uploadVoice";
    private final String TTS_URL =  BASE_URL + "/audio/text2Voice";

//    private final String TTS_URL = BASE_URL + "/v1/tts";


    @Override
    public AsrResult asr(File audio, AudioRequestParam param) {
        OkHttpClient client = new OkHttpClient();
        // 创建文件对象

        // 创建 MediaType 对象
        MediaType mediaType = MediaType.parse("audio/wav");

        // 创建 MultipartBody.Builder 对象
        MultipartBody.Builder requestBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audioFile", audio.getName(), RequestBody.create(mediaType, audio));

        // 构建 RequestBody 对象
        RequestBody requestBody = requestBodyBuilder.build();

        HashMap<String, String> params = convert2Map(param);
        // 创建 Request 对象
        Request request = new Request.Builder()
                .url(ApiInvokeUtil.buildUrlByQuery(ASR_URL, params))
                .post(requestBody)
                .build();

        // 发送请求并处理响应
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String string = response.body().string();
                Map<String, String> map = gson.fromJson(string, new TypeToken<Map<String, String>>() {
                }.getType());
                String s = map.get("code");
                if("0".equals(s)) {
                    AsrResult asrResult = new AsrResult();
                    asrResult.setStatus(LagiGlobal.ASR_STATUS_SUCCESS);
                    asrResult.setResult(map.get("msg"));
                    return asrResult;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private  HashMap<String, String> convert2Map(AudioRequestParam param) {
        HashMap<String, String> params = new HashMap<>();
        if(params.get("model") != null) {
            params.put("model", param.getModel());
        }
        if (param.getAppkey() != null) {
            params.put("appkey", param.getAppkey());
        }
        if(param.getFormat() != null) {
            params.put("format", param.getFormat());
        }
        if(param.getSample_rate() != null) {
            params.put("sample_rate", param.getSample_rate().toString());
        }
        if(param.getVocabulary_id() != null) {
            params.put("vocabulary_id", param.getVocabulary_id());
        }
        if(param.getCustomization_id() != null) {
            params.put("customization_id", param.getCustomization_id());
        }
        if(param.getEnable_punctuation_prediction() != null) {
            params.put("enable_punctuation_prediction", param.getEnable_punctuation_prediction().toString());
        }
        if(param.getEnable_inverse_text_normalization() != null) {
            params.put("enable_inverse_text_normalization", param.getEnable_inverse_text_normalization().toString());
        }
        if(param.getEnable_voice_detection() != null) {
            params.put("enable_voice_detection", param.getEnable_voice_detection().toString());
        }
        if(param.getDisfluency() != null) {
            params.put("disfluency", param.getDisfluency().toString());
        }
        if (param.getAudio_address() != null) {
            params.put("audio_address", param.getAudio_address());
        }
        return params;
    }


    @Override
    public TTSResult tts(TTSRequestParam param) {
        try {
            String post = OkHttpUtil.post(TTS_URL, gson.toJson(param));
            Map <String, String> map = gson.fromJson(post, new TypeToken< Map <String, String>>(){}.getType());
            if("success".equals(map.get("status"))) {
                String s = map.get("data");
                TTSResult ttsResult = new TTSResult();
                ttsResult.setStatus(LagiGlobal.TTS_STATUS_SUCCESS);
                ttsResult.setResult(BASE_URL + "/" + s);
                return ttsResult;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        LandingAudioCascadeAdapter landingAudioCascadeAdapter = new LandingAudioCascadeAdapter();
        TTSRequestParam ttsRequestParam = new TTSRequestParam();
        ttsRequestParam.setText("你好");
        TTSResult tts = landingAudioCascadeAdapter.tts(ttsRequestParam);
        System.out.println(tts);
    }
}
