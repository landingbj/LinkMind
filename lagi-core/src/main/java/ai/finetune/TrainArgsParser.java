package ai.finetune;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ai.finetune.pojo.FineTuneArgs;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class TrainArgsParser {


    private final FineTuneArgs fineTuneArgs;
    private final String baseDir;

    public TrainArgsParser(String baseDir, FineTuneArgs fineTuneArgs) {
        this.fineTuneArgs = fineTuneArgs;
        this.baseDir = baseDir;
    }

    class SMap<k, v> extends HashMap<k, v> {

        @Override
        public v put(k key, v value) {
            if(value == null) {
                return null;
            }
            return super.put(key, value);
        }
    }

    public Map<String, Object> parseTrainArgs() {

        Map<String, Object> args = new SMap<>();
        args.put("stage",fineTuneArgs.getStage());
        args.put("do_train", true);
        args.put("model_name_or_path", fineTuneArgs.getModel_path());
        args.put("cache_dir", fineTuneArgs.getCache_dir());
        args.put("preprocessing_num_workers", 16);
        args.put("finetuning_type", fineTuneArgs.getFinetuning_type());
        args.put("template", fineTuneArgs.getTemplate());
        args.put("rope_scaling", "none".equals(fineTuneArgs.getRope_scaling()) ? null : fineTuneArgs.getRope_scaling());
        args.put("flash_attn", "flashattn2".equals(fineTuneArgs.getBooster()) ? "fa2" : "auto");
        args.put("use_unsloth", "unsloth".equals(fineTuneArgs.getBooster()));
        args.put("enable_liger_kernel", "liger_kernel".equals(fineTuneArgs.getBooster()));
        args.put("dataset_dir", fineTuneArgs.getDataset_dir());
        args.put("dataset", String.join(",", fineTuneArgs.getDataset()));
        args.put("cutoff_len", fineTuneArgs.getCutoff_len());
        args.put("learning_rate", fineTuneArgs.getLearning_rate());
        args.put("num_train_epochs", fineTuneArgs.getNum_train_epochs());
        args.put("max_samples", fineTuneArgs.getMax_samples());
        args.put("per_device_train_batch_size", fineTuneArgs.getBatch_size());
        args.put("gradient_accumulation_steps", fineTuneArgs.getGradient_accumulation_steps());
        args.put("lr_scheduler_type", fineTuneArgs.getLr_scheduler_type());
        args.put("max_grad_norm", fineTuneArgs.getMax_grad_norm());
        args.put("logging_steps", fineTuneArgs.getLogging_steps());
        args.put("save_steps", fineTuneArgs.getSave_steps());
        args.put("warmup_steps", fineTuneArgs.getWarmup_steps());
        args.put("neftune_noise_alpha", fineTuneArgs.getNeftune_alpha() != null ? fineTuneArgs.getNeftune_alpha() : null);
        args.put("packing", fineTuneArgs.getPacking() != null ? fineTuneArgs.getPacking() : fineTuneArgs.getNeat_packing());
        args.put("neat_packing", fineTuneArgs.getNeat_packing());
        args.put("train_on_prompt", fineTuneArgs.getTrain_on_prompt());
        args.put("mask_history", fineTuneArgs.getMask_history());
        args.put("resize_vocab", fineTuneArgs.getResize_vocab());
        args.put("use_llama_pro", fineTuneArgs.getUse_llama_pro());
        args.put("report_to", fineTuneArgs.getReport_to());
        args.put("use_galore", fineTuneArgs.getUse_galore());
        args.put("use_apollo", fineTuneArgs.getUse_apollo());
        args.put("use_badam", fineTuneArgs.getUse_badam());
        args.put("use_swanlab", fineTuneArgs.getUse_swanlab());
        args.put("output_dir", fineTuneArgs.getOutput_dir());
        args.put("fp16", "fp16".equals(fineTuneArgs.getCompute_type()));
        args.put("bf16", "bf16".equals(fineTuneArgs.getCompute_type()));
        args.put("pure_bf16", "pure_bf16".equals(fineTuneArgs.getCompute_type()));
        args.put("plot_loss", true);
        args.put("trust_remote_code", true);
        args.put("ddp_timeout", 180000000);
        args.put("include_num_input_tokens_seen", isTransformersVersionEqualTo4_46() ? false : true);  // FIXME
        args.put("overwrite_cache", fineTuneArgs.getOverwrite_cache());
        args.put("overwrite_output_dir", fineTuneArgs.getOverwrite_output_dir());
        if(!StrUtil.isBlank(fineTuneArgs.getExtra_args())) {
            args.putAll(new JSONObject(fineTuneArgs.getExtra_args().toString()).toMap());
        }

        // checkpoints
        if (fineTuneArgs.getCheckpoint_path() != null) {
            if (PEFT_METHODS.contains(fineTuneArgs.getFinetuning_type())) {  // list
                List<String> checkpointPaths = (List<String>) fineTuneArgs.getCheckpoint_path();
                args.put("adapter_name_or_path", checkpointPaths.stream()
                        .map(adapter -> getSaveDir(fineTuneArgs.getModel_name().toString(), fineTuneArgs.getFinetuning_type().toString(), adapter))
                        .collect(Collectors.joining(",")));
            } else {  // str
                args.put("model_name_or_path", getSaveDir(fineTuneArgs.getModel_name().toString(), fineTuneArgs.getFinetuning_type().toString(), fineTuneArgs.getCheckpoint_path().toString()));
            }
        }

        // quantization
        if (fineTuneArgs.getQuantization_bit() == null) {
            args.put("quantization_bit", fineTuneArgs.getQuantization_bit());
            args.put("quantization_method", fineTuneArgs.getQuantization_method());
            args.put("double_quantization", !isTorchNpuAvailable());
        }

        // freeze config
        if ("freeze".equals(args.get("finetuning_type"))) {
            args.put("freeze_trainable_layers", fineTuneArgs.getFreeze_trainable_layers());
            args.put("freeze_trainable_modules", fineTuneArgs.getFreeze_trainable_modules());
            args.put("freeze_extra_modules", fineTuneArgs.getFreeze_extra_modules() != null ? fineTuneArgs.getFreeze_extra_modules() : null);
        }

        // lora config
        if ("lora".equals(args.get("finetuning_type"))) {
            args.put("lora_rank", fineTuneArgs.getLora_rank());
            args.put("lora_alpha", fineTuneArgs.getLora_alpha());
            args.put("lora_dropout", fineTuneArgs.getLora_dropout());
            args.put("loraplus_lr_ratio", fineTuneArgs.getLoraplus_lr_ratio() != null ? fineTuneArgs.getLoraplus_lr_ratio() : null);
            args.put("create_new_adapter", fineTuneArgs.getCreate_new_adapter());
            args.put("use_rslora", fineTuneArgs.getUse_rslora());
            args.put("use_dora", fineTuneArgs.getUse_dora());
            args.put("pissa_init", fineTuneArgs.getUse_pissa());
            args.put("pissa_convert", fineTuneArgs.getUse_pissa());
            args.put("lora_target", fineTuneArgs.getLora_target() != null ? fineTuneArgs.getLora_target() : "all");
            args.put("additional_target", fineTuneArgs.getAdditional_target() != null ? fineTuneArgs.getAdditional_target() : null);

            if (Boolean.TRUE.equals(args.get("use_llama_pro"))) {
                args.put("freeze_trainable_layers", fineTuneArgs.getFreeze_trainable_layers());
            }
        }

        // rlhf config
        if ("ppo".equals(args.get("stage"))) {
            if (PEFT_METHODS.contains(fineTuneArgs.getFinetuning_type())) {
                List<String> rewardModels = (List<String>) fineTuneArgs.getReward_model();
                args.put("reward_model", rewardModels.stream()
                        .map(adapter -> getSaveDir(fineTuneArgs.getModel_name().toString(), fineTuneArgs.getFinetuning_type().toString(), adapter))
                        .collect(Collectors.joining(",")));
            } else {
                args.put("reward_model", getSaveDir(fineTuneArgs.getModel_name().toString(), fineTuneArgs.getFinetuning_type().toString(), fineTuneArgs.getReward_model().toString()));
            }

            args.put("reward_model_type", "lora".equals(fineTuneArgs.getFinetuning_type()) ? "lora" : "full");
            args.put("ppo_score_norm", fineTuneArgs.getPpo_score_norm());
            args.put("ppo_whiten_rewards", fineTuneArgs.getPpo_whiten_rewards());
            args.put("top_k", 0);
            args.put("top_p", 0.9);
        } else if ("dpo".equals(args.get("stage")) || "kto".equals(args.get("stage"))) {
            args.put("pref_beta", fineTuneArgs.getPref_beta());
            args.put("pref_ftx", fineTuneArgs.getPref_ftx());
            args.put("pref_loss", fineTuneArgs.getPref_loss());
        }

        // galore config
        if (Boolean.TRUE.equals(args.get("use_galore"))) {
            args.put("galore_rank", fineTuneArgs.getGalore_rank());
            args.put("galore_update_interval", fineTuneArgs.getGalore_update_interval());
            args.put("galore_scale", fineTuneArgs.getGalore_scale());
            args.put("galore_target", fineTuneArgs.getGalore_target());
        }

        // apollo config
        if (Boolean.TRUE.equals(args.get("use_apollo"))) {
            args.put("apollo_rank", fineTuneArgs.getApollo_rank());
            args.put("apollo_update_interval", fineTuneArgs.getApollo_update_interval());
            args.put("apollo_scale", fineTuneArgs.getApollo_scale());
            args.put("apollo_target", fineTuneArgs.getApollo_target());
        }

        // badam config
        if (Boolean.TRUE.equals(args.get("use_badam"))) {
            args.put("badam_mode", fineTuneArgs.getBadam_mode());
            args.put("badam_switch_mode", fineTuneArgs.getBadam_switch_mode());
            args.put("badam_switch_interval", fineTuneArgs.getBadam_switch_interval());
            args.put("badam_update_ratio", fineTuneArgs.getBadam_update_ratio());
        }

        // report_to
        if ("none".equals(args.get("report_to"))) {
            args.put("report_to", "none");
        } else if ("all".equals(args.get("report_to"))) {
            args.put("report_to", "all");
        }

        // swanlab config
        if (Boolean.TRUE.equals(fineTuneArgs.getUse_swanlab())) {
            args.put("swanlab_project", fineTuneArgs.getSwanlab_project());
            args.put("swanlab_run_name", fineTuneArgs.getSwanlab_run_name());
            args.put("swanlab_workspace", fineTuneArgs.getSwanlab_workspace());
            args.put("swanlab_api_key", fineTuneArgs.getSwanlab_api_key());
            args.put("swanlab_mode", fineTuneArgs.getSwanlab_mode());
        }

        // eval config
        if (fineTuneArgs.getVal_size() > 1e-6 && !"ppo".equals(args.get("stage"))) {
            args.put("val_size", fineTuneArgs.getVal_size());
            args.put("eval_strategy", "steps");
            args.put("eval_steps", args.get("save_steps"));
            args.put("per_device_eval_batch_size", args.get("per_device_train_batch_size"));
        }

        // ds config
        if (StrUtil.isNotBlank(fineTuneArgs.getDs_stage())) {
            String dsStage = fineTuneArgs.getDs_stage();
            String dsOffload = Boolean.TRUE.equals(fineTuneArgs.getDs_offload()) ? "offload_" : "";
            args.put("deepspeed", Paths.get(baseDir, DEFAULT_CACHE_DIR, String.format("ds_z%s_%sconfig.json", dsStage, dsOffload)).toAbsolutePath().toString());
        }
        return args;
    }


    public void saveMapToYaml( String filePath) {
        Path path = Paths.get(filePath);
        File file = path.getParent().toFile();
        if (!file.exists()) {
            file.mkdirs();
        }
        Map<String, Object> data = parseTrainArgs();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // 使用块样式
        Yaml yaml = new Yaml(options);
        try (FileWriter writer = new FileWriter(filePath)) {
            yaml.dump(data, writer);
            System.out.println("Map已成功保存为YAML文件: " + filePath);
        } catch (IOException e) {
            System.err.println("保存YAML文件时发生错误: " + e.getMessage());
        }
    }

    private Map<String, Object> loadConfig() {
        // Implement the logic to load the configuration
        return new HashMap<>();
    }

    private String getSaveDir(String modelName, String finetuningType, String outputDir) {
        // Implement the logic to get the save directory
        return outputDir + "/" + modelName + "/" + finetuningType;
    }

    private boolean isTransformersVersionEqualTo4_46() {
        // Implement the logic to check the transformers version
        return false;
    }

    private boolean isTorchNpuAvailable() {
        // Implement the logic to check if torch NPU is available
        return true;
    }

    private static final Map<String, Object> TRAINING_STAGES = new HashMap<>();
    private static final List<String> PEFT_METHODS = Lists.newArrayList("method1", "method2");  // Replace with actual methods
    private static final String DEFAULT_CACHE_DIR = "/examples/deepspeed";  // Replace with actual cache directory

    // ConfigManager class
    public static void main(String[] args) {
        FineTuneArgs fineTuneArgs1 = new FineTuneArgs();
        TrainArgsParser parser = new TrainArgsParser("", fineTuneArgs1);
        Map<String, Object> trainArgs = parser.parseTrainArgs();
        System.out.println(trainArgs);
    }
}
