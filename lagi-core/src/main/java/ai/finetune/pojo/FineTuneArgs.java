package ai.finetune.pojo;

import lombok.*;

//import javax.validation.constraints.NotBlank;
//import javax.validation.constraints.NotEmpty;
//import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class FineTuneArgs {

//    @NotBlank(message = "model name can not be blank")
    private String model_name;
//    @NotBlank(message = "model path can not be blank")
    private String model_path;
    private String cache_dir;
    private Integer preprocessing_num_workers;
    private String finetuning_type;
//    @NotBlank(message = "template can not be blank")
    private String template;
    private String rope_scaling;
    private String booster;
    private String dataset_dir;
//    @NotNull(message = "dataset can not be null")
//    @NotEmpty(message = "dataset can not be empty")
    private List<String> dataset;
    private Integer cutoff_len;
    private Double learning_rate;
    private Double num_train_epochs;
    private Integer max_samples;
    private Integer batch_size;
    private Integer gradient_accumulation_steps;
    private String lr_scheduler_type;
    private Double max_grad_norm;
    private Integer logging_steps;
    private Integer warmup_steps;
    private String neftune_alpha;
    private String packing;
    private String neat_packing;
    private String train_on_prompt;
    private String mask_history;
    private String resize_vocab;

    private Boolean use_llama_pro;
    private String report_to;
    private Boolean use_galore;
    private Boolean use_apollo;
    private Boolean use_badam;
    private Boolean use_swanlab;
    private String output_dir;
    /**
     *  fp16 bf16 pure_bf16
     */
    private String compute_type;
    private Boolean plot_loss;
    private Boolean trust_remote_code;
    private Long ddp_timeout;
    private String include_num_input_tokens_seen;

    private String extra_args;

    // checkpoints
    private List<String> checkpoint_path;
    // quantization
    private Integer quantization_bit;
    private String quantization_method;
    // freeze config
    private String freeze_trainable_layers;
    private String freeze_trainable_modules;
    private String freeze_extra_modules;
    // lora config
    private String lora_rank;
    private String lora_alpha;
    private String lora_dropout;
    private String loraplus_lr_ratio;
    private String create_new_adapter;
    private String use_rslora;
    private String use_dora;
    private String use_pissa;
    private String lora_target;
    private String additional_target;


    // rlhf config
    private String stage;
    private List<String> reward_model;

    private String ppo_score_norm;
    private String ppo_whiten_rewards;
    private String pref_beta;
    private String pref_ftx;
    private String pref_loss;
    // galore config
    private String galore_rank;
    private String galore_update_interval;
    private String galore_scale;
    private String galore_target;

    // apollo config
    private String apollo_rank;
    private String apollo_update_interval;
    private String apollo_scale;
    private String apollo_target;

    // badam config
    private String badam_mode;
    private String badam_switch_mode;
    private String badam_switch_interval;
    private String badam_update_ratio;

    // report_to
//    private String report_to;
//    private String report_to;
    // swanlab config
    private String swanlab_project;
    private String swanlab_run_name;
    private String swanlab_workspace;
    private String swanlab_api_key;
    private String swanlab_mode;
    // eval config
//    private String stage; // 0-1
    private Double val_size;
    private Integer save_steps;
    private Integer per_device_train_batch_size;

    // ds config
    private String ds_stage;
    private Boolean ds_offload;

    private Boolean overwrite_cache;
    private Boolean overwrite_output_dir;
}
