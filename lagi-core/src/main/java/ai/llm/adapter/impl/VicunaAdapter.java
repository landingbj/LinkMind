package ai.llm.adapter.impl;

import ai.annotation.LLM;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@LLM(modelNames = {"vicuna-13b","vicuna-7b","vicuna-7b-16k","vicuna-13B-16k","vicuna-33B"})
@LLM(modelNames = {"DeepSeek-R1-Distill-Qwen-1.5B","DeepSeek-R1-Distill-Qwen-7B","DeepSeek-R1-Distill-Qwen-14B","DeepSeek-R1-Distill-Qwen-32B","DeepSeek-R1-Distill-Llama-8B","DeepSeek-R1-Distill-Llama-70B"})
public class VicunaAdapter extends OpenAIStandardAdapter {
    private static final Logger logger = LoggerFactory.getLogger(VicunaAdapter.class);
}
