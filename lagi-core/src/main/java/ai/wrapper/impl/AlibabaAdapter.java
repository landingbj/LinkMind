package ai.wrapper.impl;


import ai.wrapper.CommonAdapter;
import ai.wrapper.IWrapper;

import java.util.HashMap;
import java.util.Map;

public class AlibabaAdapter extends CommonAdapter implements IWrapper {
    @Override
    protected Map<String, String> getModelDriverMap() {
        Map<String, String> modelDriverMap = new HashMap<>();
        modelDriverMap.put("qwen-turbo", "ai.llm.adapter.impl.QwenAdapter");
        modelDriverMap.put("qwen-plus", "ai.llm.adapter.impl.QwenAdapter");
        modelDriverMap.put("qwen-max", "ai.llm.adapter.impl.QwenAdapter");
        modelDriverMap.put("qwen-max-1201", "ai.llm.adapter.impl.QwenAdapter");
        modelDriverMap.put("qwen-max-longcontext", "ai.llm.adapter.impl.QwenAdapter");
        modelDriverMap.put("qwen3.5-plus", "ai.llm.adapter.impl.QwenAdapter");

        modelDriverMap.put("asr", "ai.audio.adapter.impl.AlibabaAudioAdapter");

        modelDriverMap.put("vision", "ai.video.adapter.impl.AlibabaVisionAdapter");

        modelDriverMap.put("ocr", "ai.ocr.impl.AlibabaLangOcrAdapter");
        return modelDriverMap;
    }
}
