package ai.audio.adapter.impl;

import ai.annotation.ASR;
import ai.annotation.TTS;
import ai.audio.adapter.IAudioAdapter;
import ai.common.ModelService;
import ai.common.pojo.AsrResult;
import ai.common.pojo.AudioRequestParam;
import ai.common.pojo.TTSRequestParam;
import ai.common.pojo.TTSResult;
import ai.utils.LagiGlobal;
import ai.utils.OkHttpUtil;
import com.google.gson.Gson;
import lombok.Data;
import okhttp3.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@TTS(company = "private-dev", modelNames = "tts")
@ASR(company = "private-dev", modelNames = "asr")
public class WhisperAudioAdapter extends ModelService implements IAudioAdapter {

    private final Gson gson = new Gson();

    private String getUploadUrl() {
        return getEndpoint() + "/upload_file";
    }
    private String getAudio2TextUrl() {
        return getEndpoint() + "/audio2text";
    }
    private String getText2Audio() {
        return getEndpoint() + "/text2audio";
    }


    @Override
    public AsrResult asr(File audio, AudioRequestParam param) {
        AsrResult result = new AsrResult();
        try {
            String filename = uploadFile(audio);
            Map<String, Object> bodyObj = new HashMap<>();
            bodyObj.put("filename", filename);
            bodyObj.put("language", "zh");
            String post = OkHttpUtil.post(getAudio2TextUrl(), null, gson.toJson(bodyObj));
            IAsrResult parse = gson.fromJson(post, IAsrResult.class);
            if(!(parse.getStatus().equals("success"))) {
                throw new RuntimeException("parse audio failed");
            }
            String textResult =  parse.getResult();
            result.setStatus(LagiGlobal.ASR_STATUS_SUCCESS);
            result.setResult(textResult);
        } catch (Exception e) {
            result.setStatus(LagiGlobal.ASR_STATUS_FAILURE);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    // TextToAudio
    @Override
    public TTSResult tts(TTSRequestParam param) {
        TTSResult result = new TTSResult();
        try {
            Map<String, Object> bodyObj = new HashMap<>();
            String text = replaceEmojisAndEmoticons(param.getText());
            text = text.substring(0, Math.min(text.length(), 256));
            bodyObj.put("emotion", param.getEmotion());
            bodyObj.put("text", text);
            String body = gson.toJson(bodyObj);
            String post = OkHttpUtil.post(getText2Audio(), body);
            ITtsResult apiResult = gson.fromJson(post, ITtsResult.class);
            if(!(apiResult.getStatus().equals("success"))) {
                throw new RuntimeException("Text to voiced voice processing failed");
            }
            String url =  apiResult.getData();
            result.setStatus(LagiGlobal.TTS_STATUS_SUCCESS);
            result.setResult(url);
        } catch (Exception e) {
            result.setStatus(LagiGlobal.TTS_STATUS_FAILURE);
            result.setMessage(e.getMessage());
        }
        return result;
    }

    @Data
    static
    class ITtsResult {
        private String status;
        private String data;
    }

    @Data
    static
    class IAsrResult {
        private String status;
        private String result;
    }

    @Data
    static
    class IUploadResult {
        private String status;
        private String filename;
    }


    private String uploadFile(File file) {
        // 创建 OkHttpClient 实例
        OkHttpClient client = new OkHttpClient();

        // 创建 RequestBody，用于封装文件
        RequestBody fileBody = RequestBody.create(MediaType.parse("audio/mpeg"), file);
        // 创建 MultipartBody，用于封装多个部分的数据
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(Headers.of("Content-Disposition", "form-data; name=\"file\"; filename=\"" + file.getName() + "\""),
                        fileBody)
                .build();
        // 创建请求
        Request request = new Request.Builder()
                .url(getUploadUrl())
                .post(requestBody)
                .build();

        // 发送请求
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                IUploadResult iUploadResult = gson.fromJson(response.body().string(), IUploadResult.class);
                return iUploadResult.getFilename();
            } else {
                throw new RuntimeException("Upload failed");
            }
        } catch (IOException e) {
            throw new RuntimeException("Upload failed");
        }
    }

    public static void main(String[] args) {
        WhisperAudioAdapter whisperAudioAdapter = new WhisperAudioAdapter();
        whisperAudioAdapter.setEndpoint("http://127.0.0.1:9100");
//        TTSRequestParam ttsRequestParam = new TTSRequestParam();
//        ttsRequestParam.setText("你好");
//        ttsRequestParam.setEmotion("default");
//        TTSResult tts = whisperAudioAdapter.tts(ttsRequestParam);
//        System.out.println(tts);
        AsrResult asr = whisperAudioAdapter.asr(new File("C:\\Users\\Administrator\\Desktop\\asaki.mp3"), null);
        System.out.println(asr);
    }
    
    /**
     * Clean text by keeping only Chinese characters, English letters, numbers, and punctuation marks
     * Remove all other characters including emojis, emoticons, and special symbols
     * This helps TTS systems to better understand and pronounce the content
     * 
     * @param text the original text containing various characters
     * @return cleaned text with only Chinese, English, numbers, and punctuation
     */
    private String replaceEmojisAndEmoticons(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Keep Chinese characters (CJK Unified Ideographs)
            if (isChinese(c)) {
                result.append(c);
            }
            // Keep English letters (a-z, A-Z)
            else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                result.append(c);
            }
            // Keep numbers (0-9)
            else if (c >= '0' && c <= '9') {
                result.append(c);
            }
            // Keep common punctuation marks
            else if (isPunctuation(c)) {
                result.append(c);
            }
            // Keep whitespace characters
            else if (Character.isWhitespace(c)) {
                result.append(c);
            }
            // Remove all other characters (emojis, symbols, etc.)
        }
        
        return result.toString().trim();
    }
    
    /**
     * Check if a character is a Chinese character
     * 
     * @param c the character to check
     * @return true if the character is Chinese, false otherwise
     */
    private boolean isChinese(char c) {
        // CJK Unified Ideographs (CJK基本汉字)
        if (c >= 0x4E00 && c <= 0x9FFF) {
            return true;
        }
        // CJK Extension A (CJK扩展A)
        if (c >= 0x3400 && c <= 0x4DBF) {
            return true;
        }
        // CJK Extension B (CJK扩展B)
        if (c >= 0x20000 && c <= 0x2A6DF) {
            return true;
        }
        // CJK Extension C (CJK扩展C)
        if (c >= 0x2A700 && c <= 0x2B73F) {
            return true;
        }
        // CJK Extension D (CJK扩展D)
        if (c >= 0x2B740 && c <= 0x2B81F) {
            return true;
        }
        // CJK Extension E (CJK扩展E)
        if (c >= 0x2B820 && c <= 0x2CEAF) {
            return true;
        }
        // CJK Extension F (CJK扩展F)
        if (c >= 0x2CEB0 && c <= 0x2EBEF) {
            return true;
        }
        // CJK Compatibility Ideographs (CJK兼容汉字)
        if (c >= 0xF900 && c <= 0xFAFF) {
            return true;
        }
        // CJK Compatibility Ideographs Supplement (CJK兼容汉字补充)
        if (c >= 0x2F800 && c <= 0x2FA1F) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a character is a common punctuation mark
     * 
     * @param c the character to check
     * @return true if the character is punctuation, false otherwise
     */
    private boolean isPunctuation(char c) {
        // Common punctuation marks
        switch (c) {
            case '.':
            case ',':
            case ';':
            case ':':
            case '!':
            case '?':
            case '"':
            case '\'':
            case '(':
            case ')':
            case '[':
            case ']':
            case '{':
            case '}':
            case '-':
            case '_':
            case '=':
            case '+':
            case '*':
            case '/':
            case '\\':
            case '|':
            case '&':
            case '%':
            case '$':
            case '#':
            case '@':
            case '^':
            case '~':
            case '`':
                return true;
            default:
                return false;
        }
    }
}
