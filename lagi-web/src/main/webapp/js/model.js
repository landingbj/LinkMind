
let model_templte_map = {
    "Qwen1.5-7B": {
        "name":"Qwen1.5-7B",
        "path":"qwen/Qwen1.5-7B",
        "template":"qwen"
    },
    "DeepSeek-R1-Distill-Qwen-1.5B": {
        "name":"DeepSeek-R1-Distill-Qwen-1.5B",
        "path":"deepseek/DeepSeek-R1-Distill-Qwen-1.5B",
        "template":"deepseek3"
    }
}


let develop = $('#myDevelop');

let manager_models = [];

let currentModelIndex = -1;

let currentModelId = -1;

$(document).ready(function(){
    $('#sel-model').on('change', function(){
        const selectedValue = $(this).val();
        $('#train-form input[name="template"]').val(model_templte_map[selectedValue]["template"]);
        $('#train-form input[name="output-dir"]').val(selectedValue);
    });
    // 模态框的显示与隐藏
    
    var develop_btn = $('#addModeldevelop-btn');
    var span = $('.develop-content .close');

    develop_btn.on('click', function() {
        develop.show();
    });

    span.on('click', function() {
        develop.hide();
    });

    $(window).on('click', function(event) {
        if ($(event.target).is(develop)) {
            develop.hide();
        }
    });

    // 表单提交处理
    $('#modelForm').on('submit', function(event) {
        event.preventDefault();
        addDevelop();
    });

    // 复制模型ID按钮
    $('#modelList').on('click', '.copy-develop-btn', function() {
        copyDevelopedAddress(this);
    });

    // 开启/停止按钮
    $('#modelList').on('click', '.toggle-develop-btn', function() {
        var status = $(this).closest('.model-card').find('.model-status').text().split(': ')[1];
        if (status === '停止') {
            runningDevelop(this);
        } else {
            stopDevelop(this);
        }
    });

    // 删除按钮
    $('#modelList').on('click', '.delete-develop-btn', function() {
        delDevelop(this);
    });


    /************************************** */

    // 显示模态框
    function showModal(title, isEdit) {
        $('#modalTitle').text(title);
        if (isEdit) {
            const model = manager_models[currentModelIndex];
            $('#modelName').val(model.modelName);
            $('#isOnline').prop('checked', model.isOnline == 1 ? true: false);
            if (model.isOnline == 1) {
                $('#onlineFields').show();
                $('#apiKey').val(model.apiKey);
                $('#modelType').val(model.modelType);
                $('#offlineFields').hide();
            } else {
                $('#offlineFields').show();
                $('#modelEndpoint').val(model.endpoint);
                $('#onlineFields').hide();
            }
        } else {
            $('#modeManagerlForm')[0].reset();
            $('#onlineFields').hide();
            $('#offlineFields').show();
        }
        $('#modelModal').show();
    }



    // 切换线上/线下模型字段显示
    $('#isOnline').change(function() {
        if ($(this).is(':checked')) {
            $('#onlineFields').show();
            $('#offlineFields').hide(); 
        } else {
            $('#offlineFields').show();
            $('#onlineFields').hide();
        }
    });

    // 添加/编辑模型
    $('#modeManagerlForm').submit(function(event) {
        event.preventDefault();
        if(currentModelId == -1) {
            addManagerModel();
        } else {
            let modelData = {
                modelName :  $('#modelName').val(),
                isOnline : $('#isOnline').is(':checked') ? 1 : 0,
                apiKey : $('#isOnline').is(':checked') ? $('#apiKey').val() : '',
                modelType: $('#isOnline').is(':checked') ? $('#modelType').val() : '',
                modelEndpoint : $('#isOnline').is(':checked') ? '' : $('#modelEndpoint').val(),
                status: 0,
            }
            updateManagerModel(currentModelId, modelData, true);
        }
    });



    // 添加模型按钮点击事件
    $('#addModelBtn').click(function() {
        currentModelIndex = -1;
        currentModelId = -1;
        showModal('添加模型', false);
    });

    // 编辑模型按钮点击事件
    $(document).on('click', '.editBtn', function() {
        currentModelIndex = $(this).data('index');
        currentModelId = $(this).data('id');
        showModal('编辑模型', true);
    });

    // 删除模型按钮点击事件
    $(document).on('click', '.deleteBtn', function() {
        // const index = $(this).data('index');
        // manager_models.splice(index, 1);
        // renderTable();
        delManagerModel(this);
    });

    // 开启/关闭模型按钮点击事件
    $(document).on('click', '.toggleBtn', function() {
        const index = $(this).data('index');
        //  反轉 01
        manager_models[index].status = manager_models[index].status == 1 ?  0 : 1;
        renderTable();
        updateManagerModel($(this).data('id'), manager_models[index], false);
    });

    // 关闭模态框
    $('.modal-manager .close').click(hideModal);
    $('#modelModal').click(function(event) {
        if ($(event.target).is('#modelModal')) {
            hideModal();
        }
    });
});

function showActivateModule(el) {
    el.removeClass('model-modules-nav-not-active').addClass('model-modules-nav-active');
}

function showNotActivateModule(el) {
    el.removeClass('model-modules-nav-active').addClass('model-modules-nav-not-active');
}


function loadUserModule(el, model, callback) {
    // console.log("loadUserModule", this);
    let modules =  $('.model-module');

    let titles = $('.model-modules-title');
    for (let index = 0; index < titles.length; index++) {
        const title = titles[index];
        if(title == el) {
            showActivateModule($(title));
        } else {
            showNotActivateModule($(title));
        }
    }
    showSelectModuleById();
    window[callback]();
    function showSelectModuleById() {
        for (let index = 0; index < modules.length; index++) {
            const module = modules[index];
            const id = module.id;
            if (id == model) {
                $(module).show();
            } else {
                $(module).hide();
            }
        }
    }
}

/*************************finetue************************************/
function loadFinetuneData() {
    // alert('加载微调数据');
    getUserDatasets();
    getModel();
}

function getUserDatasets() {
    let userId = getCookie('userId');
    let url = `/model/getDataSetInfo?userId=${userId}`;
    fetch(url)
    .then(response => response.json())
    .then(data => {
        const keys = Object.keys(data.data);
        $('#sel-datasets').empty();
        for(let i = 0; i < keys.length; i++) {
            $('#sel-datasets').append(`<label>${keys[i]}:<input type="checkbox" name="datasets" value="${keys[i]}"></label>`);
        }
    })
    .catch(error => {
        console.log('加载用户datasets 失败');
    });
}




function getModel() {
    let url = `/model/getModels`;
    fetch(url)
    .then(response => response.json())
    .then(data => {
        let supportModels = data.data;
        let models = supportModels["models"];
        $('#sel-model').empty();
        for (let index = 0; index < models.length; index++) {
            const model = models[index];
            $('#sel-model').append(`<option value="${model["name"]}">${model["name"]}</option>`);
            model_templte_map[model["name"]] = model;
            if(index == 0) {
                $('#train-form input[name="template"]').val(model_templte_map[model["name"]]["template"]);
                $('#train-form input[name="output-dir"]').val(model["name"]);
            }
        }
    })
    .catch(error => {
        console.log('加载模型列表失败');
    });
}




function uploadUserDatasets() {
    const fileInput = document.getElementById('up-datasets');
    const file = fileInput.files[0];

    if (!file) {
        alert('请先选择一个文件');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);
    let userId = getCookie('userId');
    let url = `/model/uploadDataSet?userId=${userId}`
    fetch(url, {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('网络响应不是OK');
        }
        getUserDatasets();
        return response.json();
    })
    .then(data => {
        document.getElementById('up-datasets-status').innerText = `上传成功`;
    })
    .catch(error => {
        console.error('上传失败:', error);
        document.getElementById('up-datasets-status').innerText = `上传失败}`;
    });
}


function deleteUserDatasets() {
    let userId = getCookie('userId');
    let url = `/model/deleteDataset`;
    let checkedValues = $('#sel-datasets input[type="checkbox"]:checked').map(function() {
        return this.value;
    }).get();
    if(checkedValues.length == 0) {
        alert('请选择要删除的数据集');
        return;
    }
    let params = {
        "userId" : userId,
        "datasetNames": checkedValues
    };
    fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(params)}
    )
    .then(response => response.json())
    .then(data => {
        alert('删除用户datasets 成功');
        getUserDatasets();
    })
    .catch(error => {
        alert('删除用户datasets 失败');
    });
}


function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}




    /**
 * 将JSON字符串解析后与目标对象进行深度合并
 * @param {string} jsonString - 要解析的JSON字符串
 * @param {Object} target - 目标对象，合并后的结果将保存在此对象
 * @param {boolean} [deep=false] - 是否执行深度合并，默认为false
 * @returns {Object} 合并后的目标对象
 */
function mergeJson(jsonString, target, deep = false) {
    try {
        // 解析JSON字符串
        const source = JSON.parse(jsonString);
        
        // 合并对象属性
        return mergeObjects(source, target, deep);
    } catch (error) {
        console.error('JSON解析错误:', error);
        return target;
    }
}
    
/**
 * 合并两个对象的属性
 * @param {Object} source - 源对象
 * @param {Object} target - 目标对象
 * @param {boolean} deep - 是否执行深度合并
 * @returns {Object} 合并后的目标对象
 */
function mergeObjects(source, target, deep) {
    for (const key in source) {
        if (Object.prototype.hasOwnProperty.call(source, key)) {
            const sourceValue = source[key];
            const targetValue = target[key];
            
            if (deep && typeof sourceValue === 'object' && sourceValue !== null && 
                typeof targetValue === 'object' && targetValue !== null) {
                // 递归合并子对象
                target[key] = mergeObjects(sourceValue, targetValue, true);
            } else {
                // 直接赋值
                target[key] = sourceValue;
            }
        }
    }
    return target;
}

    
async function doTrain(el) {
    disabledTrain(el);
    // await sleep(6000);
    let userId = getCookie('userId');
    const inputValues = {};
    $('#train-form input').each(function(){
        const $this = $(this);
        const name = $this.attr('name');
        const value = $this.val();
        if ($this.is(':checkbox') && !$this.is(':checked')) {
            return; // 跳过未选中的checkbox
        }
        inputValues[name] = value;
    });
    let datasets = $('#sel-datasets input[type="checkbox"]:checked').map(function() {
        return this.value;
    }).get();
    const selectValues = {};
    $('#train-form select').each(function(){
        selectValues[$(this).attr('name')] = $(this).val();
    });
    let stage = selectValues["stage"];
    let finetuning_type = selectValues["finetuning_type"];
    let params = {
        "userId":userId,
        "fineTuneArgs" : {
            "stage" :stage,
            "model_name" : selectValues["sel-model"],
            "finetuning_type" : finetuning_type,
            "template" : inputValues["template"], //"qwen",
            "lora_target":"all",
            "dataset" : datasets,
            "cutoff_len" : Number(inputValues["cutoff-length"]), //1024,
            "max_samples": Number(inputValues["max-samples"]), //1000,
            "overwrite_cache" : true,
            // "preprocessing_num_workers" : 16,
            "logging_steps":10,
            "save_steps":500,
            "plot_loss":true,
            "overwrite_output_dir": true,
            "batch_size": Number(inputValues["batch-size"]), //1,
            "gradient_accumulation_steps": Number(inputValues["gradient-accumulation"]), //2,
            "learning_rate" : Number(inputValues["learning-rate"]), // 0.0001,
            "num_train_epochs" : Number(inputValues["epochs"]), //3.0,
            "lr_scheduler_type" : selectValues["LR-scheduler"] , // "cosine",
            "warmup_steps" : 1,
            "maximum_gradient_norm" : Number(inputValues["maximum-gradient-norm"]), // 1.0
            "compute_type" : selectValues["compute-type"], //"fp16",
            "val_size" : Number(inputValues["val-size"]), //0.1,
            "per_device_eval_batch_size": 1,
            "evaluation_strategy" : "steps",
            "eval_steps" : 500,
            "ds_stage": "0",
            "output_dir": inputValues["output-dir"]
        }
    }
    // extra arguments
    let logging_steps =  Number(inputValues["logging_steps"]);
    let save_steps =  Number(inputValues["save_steps"]);
    let warmup_steps =  Number(inputValues["warmup_steps"]);
    let NEFTune_alpha =  inputValues["NEFTune_alpha"];
    let extra_arguments =  inputValues["extra_arguments"];
    let enable_thinking =  inputValues["enable_thinking"];
    mergeJson(extra_arguments, params["fineTuneArgs"], true);
    if(logging_steps && logging_steps.length > 0) {
        params["fineTuneArgs"]["logging_steps"] = logging_steps;
    }
    if(save_steps && save_steps.length > 0) {
        params["fineTuneArgs"]["save_steps"] = save_steps;
    }
    if(warmup_steps && warmup_steps.length > 0) {
        params["fineTuneArgs"]["warmup_steps"] = warmup_steps;
    }
    if(enable_thinking && enable_thinking.length > 0) {
        params["fineTuneArgs"]["enable_thinking"] = true;
    }
    // freeze 配置
    if(finetuning_type == "freeze") {
        let freeze_trainable_layers =  Number(inputValues["freeze_trainable_layers"]);
        let freeze_trainable_modules =  inputValues["freeze_trainable_modules"];
        let freeze_extra_modules =  inputValues["freeze_extra_modules"];
        if(freeze_trainable_layers) {
            params["fineTuneArgs"]["freeze_trainable_layers"] = freeze_trainable_layers;
        }
        if(freeze_trainable_modules && freeze_trainable_modules.length > 0) {
            params["fineTuneArgs"]["freeze_trainable_modules"] = freeze_trainable_modules;
        }
        if(freeze_extra_modules && freeze_extra_modules.length > 0) {
            params["fineTuneArgs"]["freeze_extra_modules"] = freeze_extra_modules;
        }
    }
    
    // lora 配置
    if(finetuning_type == "lora") {
        let lora_rank =  Number(inputValues["lora_rank"]);
        let lora_alpha =  Number(inputValues["lora_alpha"]);
        let lora_dropout =  Number(inputValues["lora_dropout"]);
        let loraplus_lr_ratio =  Number(inputValues["loraplus_lr_ratio"]);
        let create_new_adapter =  inputValues["create_new_adapter"];
        if(lora_rank ) {
            params["fineTuneArgs"]["lora_rank"] = lora_rank;
        }
        if(lora_alpha) {
            params["fineTuneArgs"]["lora_alpha"] = lora_alpha;
        }
        if(lora_dropout) {
            params["fineTuneArgs"]["lora_dropout"] = lora_dropout;
        }
        if(loraplus_lr_ratio) {
            params["fineTuneArgs"]["loraplus_lr_ratio"] = loraplus_lr_ratio;
        }
        if(create_new_adapter && create_new_adapter.length > 0) {
            params["fineTuneArgs"]["create_new_adapter"] = true;
        }
        let use_rslora =  inputValues["use_rslora"];
        let use_dora =  inputValues["use_dora"];
        let pissa_init =  inputValues["pissa_init"];
        let lora_target =  inputValues["lora_target"];
        let additional_target =  inputValues["additional_target"];
        if(use_rslora && use_rslora.length > 0) {
            params["fineTuneArgs"]["use_rslora"] = true;
        }
        if(use_dora && use_dora.length > 0) {
            params["fineTuneArgs"]["use_dora"] = true;
        }
        if(pissa_init && pissa_init.length > 0) {
            params["fineTuneArgs"]["pissa_init"] = true;
        }
        if(lora_target && lora_target.length > 0) {
            params["fineTuneArgs"]["lora_target"] = lora_target;
        }
        if(additional_target && additional_target.length > 0) {
            params["fineTuneArgs"]["additional_target"] = additional_target;
        }
    }
    console.log("params", params);
    // RLHF 配置
    if(stage == "rm") {
        let pref_beta =  Number(inputValues["pref_beta"]);
        let pref_ftx =  Number(inputValues["pref_ftx"]);
        let pref_loss =  selectValues["pref_loss"];
        let ref_model =  inputValues["ref_model"];
        if(pref_beta) {
            params["fineTuneArgs"]["pref_beta"] = pref_beta;
        }
        if(pref_ftx) {
            params["fineTuneArgs"]["pref_ftx"] = pref_ftx;
        }
        if(pref_loss && pref_loss.length > 0) {
            params["fineTuneArgs"]["pref_loss"] = pref_loss;
        }
        if(ref_model && ref_model.length > 0) {
            params["fineTuneArgs"]["ref_model"] = ref_model;
        }
        let ppo_score_norm =  inputValues["ppo_score_norm"];
        let ppo_whiten_rewards =  inputValues["ppo_whiten_rewards"];
        if(ppo_score_norm && ppo_score_norm.length > 0) {
            params["fineTuneArgs"]["ppo_score_norm"] = true;
        }
        if(ppo_whiten_rewards && ppo_whiten_rewards.length > 0) {
            params["fineTuneArgs"]["ppo_whiten_rewards"] = true;
        }
    }

    try {
        checkTrainParams(params);
    } catch(error) {
        alert(error);
        enableTrain(el);
        return ;
    }

    function disabledTrain(el) {
        $(el).prop('disabled', true).css("opacity", 0.5);
    }

    function enableTrain(el) {
        $(el).prop('disabled', false).css("opacity", 1);
    }

    lossChart.clean();
    $('#train-view-content').empty();
    $('#train-view-content').append(`<div class="loading-box">正在启动训练...<span><span></div>`);
    try {
        const response = await fetch('model/train', {
            method: "POST",
            cache: "no-cache",
            keepalive: true,
            headers: {
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
            },
            body: JSON.stringify(params),
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        $('#train-view-content').empty();
        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');

        function readStream(params, el) {
            return reader.read().then(({ done, value }) => {
                if (done) {
                    // getLastTrainLossPng(params, $('#train-view-content'));
                    console.log('Connection closed by server');
                    enableTrain(el);
                    return;
                }
                let msg = decoder.decode(value, { stream: true });
                msg =  msg.slice(5);
                if(msg.includes('[DONE]') ) {
                    console.log('Connection closed by server');
                    $('#train-view-content').append(`<p>${msg}</p>`);
                    scrollToButton();
                    enableTrain(el);
                    return;
                }
                try {
                    let json =  JSON.parse(msg.replaceAll("'", "\""));
                    let loss = json["loss"];
                    let epoch = json["epoch"];
                    if(loss && epoch) {
                        lossChart.drawPoint(loss, epoch);
                    }
                } catch (e) {
                }
                $('#train-view-content').append(`<p>${msg}</p>`);
                scrollToButton();
                return readStream(params, el);
            }).catch(error => {
                console.error('Connection error:', error);
                enableTrain(el);
            });
        }

        readStream(params, el);
    } catch (error) {
        console.error('Fetch error:', error);
        enableTrain(el);
    }
}





class LossChart{
    constructor(id) {
        this.chart = echarts.init(document.getElementById(id));
        this.lossValues = [];
        this.epochs = [];
        this.option = {
            title: {
                text: 'training loss',
                left: 'center', // 标题水平居中
                textStyle: {
                    fontSize: 10 // 设置标题文字大小，可根据需要调整
                }
            },
            grid: {
                left: '15%', // 调整图表左侧边距，为 y 轴标签留出更多空间
                right: '0',
                top: '0',
                bottom: '10%'
            },
            xAxis: {
                name: 'epoch',
                type: 'category',
                data: this.epochs
            },
            yAxis: {
                name: 'loss',
                type: 'value'
            },
            series: [{
                name: 'loss',
                type: 'line',
                data: this.lossValues
            }]
        };
        this.chart.setOption(this.option);
    }

    drawPoint(loss, epoch) {
        this.epochs.push(epoch);
        this.lossValues.push(loss);
        this.option.xAxis.data = this.epochs;
        this.option.series[0].data = this.lossValues;
        this.chart.setOption(this.option);
    }

    clean() {
        this.epochs = [];
        this.lossValues = [];
        this.option.xAxis.data = this.epochs;
        this.option.series[0].data = this.lossValues;
        this.chart.setOption(this.option);
    }
}


const lossChart =  new LossChart('loss-canvas');


function getLastTrainLossPng(trainParam , el) {
    let params = {
        "userId":trainParam["userId"],
        "fineTuneArgs" : {
            "output_dir": trainParam["fineTuneArgs"]["output_dir"]
        }
    }
    fetch('model/getLastTrainLossPng', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let res = data.data;
        if(res) {
            el.append(`<image src="data:image/png;base64,${res}"></image>`);
            scrollToButton();
        } else {
            console.log('获取损失图片失败.');
        }
    })
    .catch(error => {
        console.log('获取损失图片失败');
    });
}

function isNumeric(str) {
    return !isNaN(parseFloat(str)) && isFinite(str);
}


function checkTrainParams(params) {
    let fineTuneArgs =  params["fineTuneArgs"];
    if( !(fineTuneArgs["dataset"]) || fineTuneArgs["dataset"].length == 0) {
        // throw new Error(`请选择一个训练的数据集`);
    }
    if(!(fineTuneArgs["template"])) {
        throw new Error(`template 不可为空`);
    }
    if(!isNumeric(fineTuneArgs["cutoff_len"])) {
        throw new Error(`cutoff_len 不可为空或非数字`);
    }
    if(!isNumeric(fineTuneArgs["max_samples"])) {
        throw new Error(`cutoff_len 不可为空或非数字`);
    }
    if(!isNumeric(fineTuneArgs["batch_size"])) {
        throw new Error(`batch-size 不可为空或非数字`);
    }
    if(!isNumeric(fineTuneArgs["gradient_accumulation_steps"] )) {
        throw new Error(`gradient_accumulation_steps 不可为空或非数字`);
    }
    if(!isNumeric(fineTuneArgs["learning_rate"])) {
        throw new Error(`learning_rate 不可为空或非数字`);
    }
    if(!isNumeric(fineTuneArgs["num_train_epochs"])) {
        throw new Error(`num_train_epochs 不可为空或非数字`);
    }
    if(!(fineTuneArgs["lr_scheduler_type"])) {
        throw new Error(`lr_scheduler_type 不可为空或数字`);
    }
    if(!isNumeric(fineTuneArgs["maximum_gradient_norm"])) {
        throw new Error(`maximum_gradient_norm 不可为空或非数字`);
    }
    if(!(fineTuneArgs["compute_type"])) {
        throw new Error(`compute_type 不可为空或数字`);
    }
    if(!isNumeric(fineTuneArgs["val_size"])) {
        throw new Error(`val_size 不可为空或非数字`);
    }
    if(!(fineTuneArgs["output_dir"])) {
        throw new Error(`output_dir 不可为空或数字`);
    }
}


function scrollToButton() {
    var div = $('#train-view-content');
    div.scrollTop(div.prop('scrollHeight'));
}

/*************************develop************************************/
function loadDevelopData() {
    // alert('加载模型部署数据');
    getDevelop();
}


function addDevelop() {
    let userId = getCookie("userId");
    let modelPath = $('#modelPath').val();
    let adapterPath = $('#adapterPath').val();
    let params = {
        "userId" : userId,
        "modelPath":modelPath,
        "adapterPath": adapterPath
    }
    fetch('model/addDevelop', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let res = data.data;
        if(res) {
            getDevelop();
            $('#modelForm')[0].reset();
            develop.hide();
        } else {
            alert('创建启动失败');
        }
    })
    .catch(error => {
        testAddDevelop();
        alert('创建启动失败');
    });
}


function testAddDevelop() {
    let modelPath = "deeepseek";
    let text1 = "停止";
    let statusClass = "pulished";
    let id = '-1';
    let text2 = '启动';
    let newModelCard = `
            <div class="model-card">
                <div class="model-card-info">
                    <div></div>
                    <div class="model-name">模型名: ${modelPath}</div>
                </div>
                <div class="model-status-container"><div class="model-status">是否启动: ${text1}</div> <div class="status-indicator ${statusClass}"></div></div>
                <div class="model-actions">
                    <button class="copy-develop-btn" data-id="${id}">复制模型地址</button>
                    <button class="toggle-develop-btn ${statusClass}" data-id="${id}">${text2}</button>
                    <button class="delete-develop-btn" data-id="${id}" >删除</button>
                </div>
            </div>
        `;
    $('#modelList').append(newModelCard);
}


function delDevelop(el) {
    let id =  $(el).data('id');
    let params = {
        "id": id,
    }
    fetch('model/delDevelop', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let res = data.data;
        if(res) {
            $(el).closest('.model-card').remove();
        }
    })
    .catch(error => {
        alert('删除部署失败');
    });
}


const runningToggleMap = {
    "0": "启动",
    "1": "停止",
    "2": "启动"
}

const runningMap = {
    "0": "停止",
    "1": "启动",
    "2": "启动中"
}

const runningToggleImg = {
    "0": "images/start.png",
    "1": "images/stop.png",
    "2": "images/loading.png",
}

const runningStyleClass = {
    "0": "published",
    "1": "unpublished",
    "2": "publishing",
}

function loadDevelopPageEle(cardInfo) {
    let id = cardInfo["id"];
    let modelPath = cardInfo["modelPath"];
    let status = cardInfo["running"];
    let statusClass = runningStyleClass[status];
    let text1 = runningMap[status];
    let text2 = runningToggleMap[status];
    let img =  runningToggleImg[status];
    return [id, modelPath, statusClass, img, text1, text2]
}


function loadDevelopCard(cardInfo) {
    
    // let id = cardInfo["id"];
    // let modelPath = cardInfo["modelPath"];
    // let status = cardInfo["running"];
    // let statusClass = runningStyleClass[status];
    // let text1 = runningToggleMap[status];
    // let text2 = runningMap[status];
    let [id,  modelPath,  statusClass, img, text1,  text2]   = loadDevelopPageEle(cardInfo);
    let html = `
        <div class="model-card">
        <div class="model-card-info">
            <div class="model-card-icon">
            <img src="images/deepseek.png" alt="">
            </div>
            <div class="model-card-detail">
            <div class="model-name">模型名: ${modelPath}</div>
            <div><span>大语言模型</span></div>
            </div>
        </div>
        <div class="model-status-container"><div class="model-status">是否启动: ${text1}</div> <div class="status-indicator ${statusClass}"></div></div>
        <div class="model-actions">
            <button class="copy-develop-btn" data-id="${id}"><img src="images/copy.png" alt=""> 复制模型地址</button> 
            <button class="toggle-develop-btn ${statusClass}" data-id="${id}"><img src="${img}" alt=""><span>${text2}</span></button>
            <button class="delete-develop-btn" data-id="${id}" ><img src="images/delete.png" alt=""> 删除</button>
        </div>
        </div>
    `
    $('#modelList').append(html);
}


function getDevelop() {
    let userId = getCookie("userId");
    let url = `/model/getDevelop?userId=${userId}`;
    fetch(url)
    .then(response => response.json())
    .then(data => {
        let develops = data.data;
        $('#modelList').empty();
        for (let index = 0; index < develops.length; index++) {
            loadDevelopCard(develops[index]);
        //     let modal_develop = develops[index];
        //     let modelPath = modal_develop["modelPath"];
        //     let statusClass = modal_develop["running"] == 1 ? "unpublished" : "published";
        //     let port = modal_develop["port"];
        //     let id = modal_develop["id"];
        //     let text1 = modal_develop["running"] == 1? "启动": "停止"; 
        //     let text2 = modal_develop["running"] == 1? "停止": "开启"; 
        //     let newModelCard = `
        //         <div class="model-card">
        //             <div class="model-name">模型名: ${modelPath}</div>
        //             <div class="model-status-container"><div class="model-status">是否启动: ${text1}</div> <div class="status-indicator ${statusClass}"></div></div>
        //             <div class="model-actions">
        //                 <button class="copy-develop-btn" data-id="${id}">复制模型地址</button>
        //                 <button class="toggle-develop-btn ${statusClass}" data-id="${id}">${text2}</button>
        //                 <button class="delete-develop-btn" data-id="${id}" >删除</button>
        //             </div>
        //         </div>
        //     `;
        //     $('#modelList').append(newModelCard);
        }
    })
    .catch(error => {
        console.log('加载模型列表失败');
    });
}


function updateRenderDevelop(el, info) {
    let [xId,  modelPath,  statusClass,  img,  text1,  text2]   = loadDevelopPageEle(info);
    $(el).closest('.model-card').find('.model-status').text(`是否启动: ${text1}`);
    $(el).children("img")[0].src = img;
    $($(el).children("span")[0]).text(text2);
    let keys = Object.keys(runningStyleClass);
    for(let i = 0; i < keys.length; i++) {
        const key = keys[i];
        const clazz = runningStyleClass[key];
        if(clazz == statusClass) {
            $(el).addClass(clazz);
            $(el).closest('.model-card').find('.status-indicator').addClass(clazz);
        } else  {
            $(el).removeClass(clazz);
            $(el).closest('.model-card').find('.status-indicator').removeClass(clazz);
        }
    }
}

function runningDevelop(el) {
    let id =  $(el).data('id');
    let params = {
        "id":id,
    }

    let info = {
        id : "id",
        running: "2",
        modelPath: ""
    }
    updateRenderDevelop(el, info);
    fetch('model/start', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let status = data.data;
        if(status) {
            info.running = 1;
        } else {
            info.running = 0;
            alert('启动模型失败');
        }
        updateRenderDevelop(el, info);
    })
    .catch(error => {
        info.running = 0;
        updateRenderDevelop(el, info);
        alert('启动模型失败');
    });
}

function stopDevelop(el) {
    let id =  $(el).data('id');
    let userId = getCookie("userId");
    let params = {
        "id":id,
        "userId": userId
    }
    let info = {
        id : "id",
        running: "2",
        modelPath: ""
    }
    fetch('model/stop', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let status = data.data;
        if(status) {
            info.running = 0;
        } else {
            info.running = 1;
        }
        updateRenderDevelop(el, info);
    })
    .catch(error => {
        info.running = 1;
        updateRenderDevelop(el, info);
        alert('停止模型失败');
    });
}


function copyDevelopedAddress(el) {
    // var modelId = $(el).closest('.model-card').find('.model-name').text().split(': ')[1];
    const id = $(el).data("id");
    fetch(`model/getDevelopedAddress?id=${id}`)
    .then(response => response.json())
    .then(data => {
        let address = data.data;
        if(address) {
            navigator.clipboard.writeText(address).then(function() {
                alert('模型地址已复制');
            }, function(err) {
                console.error('无法复制文本: ', err);
            });
        }
    })
    .catch(error => {
        console.log('加载模型列表失败');
    });
}

/*************************start model manager************************************/
function loadDevelopManagerData() {
    // alert('加载模型管理数据');
    renderModelType();
    getManagerModels();
}

function addManagerModel() {
    const userId =  getCookie("userId");
    const name = $('#modelName').val();
    const isOnline = $('#isOnline').is(':checked') ? 1 : 0;
    const apiKey = isOnline ? $('#apiKey').val() : '';
    const modelType = isOnline ? $('#modelType').val() : '';
    const modelEndpoint = !isOnline ? $('#modelEndpoint').val() : '';
    let params = {
        userId: userId,
        modelName: name,
        online: isOnline,
        apiKey: apiKey,
        modelType: modelType,
        endpoint: modelEndpoint,
        status: 0,
    }
    fetch('model/addManagerModel', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let res = data.data;
        if(res) {
            hideModal();
            getManagerModels();
        } else {
            alert('添加模型失败');
        }
    })
    .catch(error => {
        alert('添加模型失败');
    });
}

function delManagerModel(el) {
    const id =  $(el).data("id");
    let params = {
        "id": id
    }
    fetch('model/delManagerModel', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let res = data.data;
        if(res) {
            getManagerModels();
        }
    })
    .catch(error => {
        alert('删除模型失败');
    });
}



function updateManagerModel(id, modelData, fresh) {
    const userId =  getCookie("userId");
    const modelName = modelData["modelName"];
    const isOnline = modelData["isOnline"] != undefined ? modelData["isOnline"]: modelData["online"] ;
    const apiKey =  modelData["apiKey"];
    const modelType = modelData["modelType"];
    const modelEndpoint =  modelData["modelEndpoint"] ? modelData["modelEndpoint"] : modelData["endpoint"];
    const status =  modelData["status"];
    let params = {
        id: id,
        userId: userId,
        modelName: modelName,
        online: isOnline,
        apiKey: apiKey,
        modelType: modelType,
        endpoint: modelEndpoint,
        status: status,
    }
    fetch('model/updateManagerModel', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let res = data.data;
        if(res) {
            // if (currentModelIndex === -1) {
            //     manager_models.push({ name, isOnline, apiKey, modelType, endpoint, status: true });
            // } else {
            //     manager_models[currentModelIndex] = { name, isOnline, apiKey, modelType, endpoint, enabled: manager_models[currentModelIndex].status };
            // }
            // renderTable();
            // hideModal();
            if(fresh) {
                getManagerModels();
                hideModal();
            }
        } else {
            alert('更新模型失败');
        }
    })
    .catch(error => {
        alert('更新模型失败');
    });
}

function getManagerModels() {
    const userId =  getCookie("userId");
    let params = {
        userId: userId,
    }
    fetch('model/getManagerModels', {
        method: "POST",
        body: JSON.stringify(params),
    })
    .then(response => response.json())
    .then(data => {
        let res = data.data;
        if(res) {
            manager_models = res;
            renderTable();
        }
    })
    .catch(error => {
        console.log('获取管理模型失败');
    });
}


function renderModelType() {
    let ModelTypesEums = [
        "Baichuan",
        "Claude",
        "DeepSeek",
        "Doubao",
        "Ernie",
        "Gemini",
        "GPT",
        "GPTAzure",
        "Moonshot",
        "Qwen",
        "Sense",
        "Spark",
        "Zhipu"
    ];
    $("#modelType").empty();
    for (let i = 0; i < ModelTypesEums.length; i++) {
        const modelType = ModelTypesEums[i];
        $("#modelType").append(`<option value="${modelType}">${modelType}</option>`);
    }
    $("#modelType").val('Qwen');
}


function renderTable() {
    const tbody = $('#modelTable tbody');
    tbody.empty();
    manager_models.forEach((model, index) => {
        const row = $('<tr>');
        row.append(`<td>${model.modelName}</td>`);
        row.append(`<td>${model.online == 1 ? '是' : '否'}</td>`);
        row.append(`<td><span>${model.apiKey ? "*************************" : '-'}</span> ${model.apiKey ? `<button class="show-eyes" onclick="toggleApiKey(this, ${index})"  data-show="0" ></button>`: ''}</td>`);
        row.append(`<td>${model.modelType || '-'}</td>`);
        row.append(`<td>${model.endpoint || '-'}</td>`);
        row.append(`<td><button class="toggleBtn" data-index="${index}" data-id="${model.id}" >${model.status ? '开启' : '关闭'}</button></td>`);
        row.append(`<td>
            <button class="editBtn" data-index="${index}" data-id="${model.id}" >编辑</button>
            <button class="deleteBtn" data-index="${index}" data-id="${model.id}">删除</button>
        </td>`);
        tbody.append(row);
    });
}

function toggleApiKey(el, index) {
    let apiKeyEl = $(el).parent().find('span');
    let toggleBtn = $(el);
    let show =  toggleBtn.data("show");
    if(show == "0") {
        toggleBtn.data("show", 1);
        toggleBtn.removeClass("show-eyes").addClass("hide-eyes");
        apiKeyEl.text(manager_models[index].apiKey);
    } else {
        toggleBtn.data("show", 0);
        toggleBtn.removeClass("hide-eyes").addClass("show-eyes");
        apiKeyEl.text("*************************");
    }
}

 // 隐藏模态框
 function hideModal() {
    currentModelId = -1;
    currentModelIndex = -1;
    $('#modelModal').hide();
}


/*************************stop model manager************************************/

/*************************start model upload************************************/
function loadUserUploadModelData() {
    // alert('加载模型管理数据');
    // renderModelType();
    // getManagerModels();

}


document.addEventListener('DOMContentLoaded', () => {
    // 上传配置 - 针对百GB文件进行优化
    const config = {
      chunkSize: 100 * 1024 * 1024, // 默认100MB分片，适合百GB文件
      concurrency: 5, // 默认并发上传5个分片
      uploadUrl: '/upload/model', // 上传API地址
      checkUrl: '/upload/model/check', // 检查文件API地址
      maxRetries: 5, // 最大重试次数
      retryDelay: 2000, // 初始重试延迟(ms)
      maxFileSize: 100 * 1024 * 1024 * 1024 // 100GB文件大小限制
    };

    // 更新配置
    document.getElementById('chunk-size').addEventListener('change', (e) => {
      config.chunkSize = parseInt(e.target.value) * 1024 * 1024;
    });

    document.getElementById('concurrency').addEventListener('change', (e) => {
      config.concurrency = parseInt(e.target.value);
    });

    // 文件上传队列
    const uploadQueue = [];
    let activeUploads = 0;

    // 文件拖放区域
    const fileDropArea = document.getElementById('file-drop-area');
    const fileInput = document.getElementById('file-input');

    // 拖放事件
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
      fileDropArea.addEventListener(eventName, preventDefaults, false);
    });

    function preventDefaults(e) {
      e.preventDefault();
      e.stopPropagation();
    }

    ['dragenter', 'dragover'].forEach(eventName => {
      fileDropArea.addEventListener(eventName, highlight, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
      fileDropArea.addEventListener(eventName, unhighlight, false);
    });

    function highlight() {
      fileDropArea.classList.add('file-drop-active');
    }

    function unhighlight() {
      fileDropArea.classList.remove('file-drop-active');
    }

    // 处理拖放文件
    fileDropArea.addEventListener('drop', handleDrop, false);
    function handleDrop(e) {
      const dt = e.dataTransfer;
      const files = dt.files;
      handleFiles(files);
    }

    // 处理选择文件
    fileInput.addEventListener('change', (e) => {
      handleFiles(e.target.files);
    });

    // 处理文件
    function handleFiles(files) {
      if (files.length === 0) return;

      Array.from(files).forEach(file => {
        // 检查文件大小限制
        if (file.size > config.maxFileSize) {
          alert(`文件 "${file.name}" 超过${formatFileSize(config.maxFileSize)}大小限制，无法上传`);
          return;
        }

        addFileToQueue(file);
      });
    }

    // 添加文件到上传队列
    function addFileToQueue(file) {
      // 检查文件是否已在队列中
      if (uploadQueue.some(item => item.file.name === file.name && item.file.size === file.size)) {
        alert(`文件 "${file.name}" 已在上传队列中`);
        return;
      }

      // 创建文件上传项
      const fileItem = {
        file,
        fileId: generateFileId(file),
        chunks: [],
        uploadedChunks: new Set(),
        progress: 0,
        status: 'pending', // pending, uploading, paused, completed, failed
        abortController: null,
        element: createFileElement(file),
        stats: {
          startTime: null,
          lastUpdateTime: null,
          bytesUploaded: 0,
          speed: 0,
          estimatedTime: 0
        }
      };

      // 计算分片
      calculateChunks(fileItem);

      // 添加到队列
      uploadQueue.push(fileItem);

      // 添加到DOM
      const uploadQueueElement = document.getElementById('upload-queue');
      if (uploadQueueElement.querySelector('.upload-queue-empty')) {
        uploadQueueElement.innerHTML = '';
      }
      uploadQueueElement.appendChild(fileItem.element);

      // 开始上传
      setTimeout(() => {
        startUpload(fileItem);
      }, 500);
    }

    // 生成文件唯一标识
    function generateFileId(file) {
      // 使用文件名、大小和修改时间生成唯一ID
      return `${file.name}-${file.size}-${file.lastModified}`;
    }

    // 计算文件分片
    function calculateChunks(fileItem) {
      const { file, fileId } = fileItem;
      const totalChunks = Math.ceil(file.size / config.chunkSize);

      for (let i = 0; i < totalChunks; i++) {
        const start = i * config.chunkSize;
        const end = Math.min(start + config.chunkSize, file.size);

        fileItem.chunks.push({
          index: i,
          start,
          end,
          size: end - start,
          fileId,
          fileName: file.name,
          status: 'pending', // pending, uploading, completed, failed
          retryCount: 0
        });
      }
    }

    // 创建文件元素
    function createFileElement(file) {
      const fileSize = formatFileSize(file.size);
      const fileItem = document.createElement('div');
      fileItem.className = 'upload-item animate-fadeIn';
      fileItem.innerHTML = `
        <div class="upload-item-header">
          <div class="upload-item-icon">
            <i class="fa fa-file-text-o"></i>
          </div>
          <div class="upload-item-info">
            <div class="upload-item-title">
              <div class="upload-item-filename">${file.name}</div>
              <div class="upload-item-filesize">${fileSize}</div>
            </div>
            <div class="upload-item-progress">
              <div class="upload-item-progress-bar" style="width: 0%"></div>
            </div>
            <div class="speed-indicator hidden">
              <div class="speed-wave"></div>
            </div>
            <div class="upload-item-details">
              <div class="upload-item-speed">速度: -- MB/s</div>
              <div class="upload-item-eta">剩余时间: 计算中...</div>
            </div>
          </div>
        </div>
        <div class="upload-item-footer">
          <div class="upload-item-status pending">等待中</div>
          <div class="upload-item-actions">
            <button class="upload-item-action pause upload-pause-btn hidden">
              <i class="fa fa-pause"></i>
            </button>
            <button class="upload-item-action resume upload-resume-btn hidden">
              <i class="fa fa-play"></i>
            </button>
            <button class="upload-item-action cancel upload-cancel-btn">
              <i class="fa fa-times"></i>
            </button>
          </div>
        </div>
      `;

      // 添加事件监听
      const pauseBtn = fileItem.querySelector('.upload-pause-btn');
      const resumeBtn = fileItem.querySelector('.upload-resume-btn');
      const cancelBtn = fileItem.querySelector('.upload-cancel-btn');

      pauseBtn.addEventListener('click', () => {
        const fileId = generateFileId(file);
        pauseUpload(fileId);
      });

      resumeBtn.addEventListener('click', () => {
        const fileId = generateFileId(file);
        resumeUpload(fileId);
      });

      cancelBtn.addEventListener('click', () => {
        const fileId = generateFileId(file);
        cancelUpload(fileId);
      });

      return fileItem;
    }

    // 更新文件状态
    function updateFileStatus(fileId, status, progress = 0) {
      const fileItem = uploadQueue.find(item => item.fileId === fileId);
      if (!fileItem) return;

      fileItem.status = status;
      fileItem.progress = progress;

      const element = fileItem.element;
      const progressBar = element.querySelector('.upload-item-progress-bar');
      const statusText = element.querySelector('.upload-item-status');
      const pauseBtn = element.querySelector('.upload-pause-btn');
      const resumeBtn = element.querySelector('.upload-resume-btn');
      const speedIndicator = element.querySelector('.speed-indicator');
      const speedText = element.querySelector('.upload-item-speed');
      const etaText = element.querySelector('.upload-item-eta');

      // 更新进度条
      progressBar.style.width = `${progress}%`;

      // 更新状态文本和按钮显示
      statusText.className = `upload-item-status ${status}`;
      switch (status) {
        case 'pending':
          statusText.textContent = '等待中';
          pauseBtn.classList.add('hidden');
          resumeBtn.classList.add('hidden');
          speedIndicator.classList.add('hidden');
          break;
        case 'uploading':
          statusText.textContent = '上传中';
          pauseBtn.classList.remove('hidden');
          resumeBtn.classList.add('hidden');
          speedIndicator.classList.remove('hidden');
          updateSpeedStats(fileItem);
          break;
        case 'paused':
          statusText.textContent = '已暂停';
          pauseBtn.classList.add('hidden');
          resumeBtn.classList.remove('hidden');
          speedIndicator.classList.add('hidden');
          break;
        case 'completed':
          statusText.textContent = '已完成';
          pauseBtn.classList.add('hidden');
          resumeBtn.classList.add('hidden');
          speedIndicator.classList.add('hidden');
          speedText.textContent = '速度: -- MB/s';
          etaText.textContent = '剩余时间: --';
          break;
        case 'failed':
          statusText.textContent = '上传失败';
          pauseBtn.classList.add('hidden');
          resumeBtn.classList.add('hidden');
          speedIndicator.classList.add('hidden');
          break;
      }
    }

    // 更新速度统计
    function updateSpeedStats(fileItem) {
      const element = fileItem.element;
      const speedText = element.querySelector('.upload-item-speed');
      const etaText = element.querySelector('.upload-item-eta');
      
      // 第一次更新
      if (!fileItem.stats.startTime) {
        fileItem.stats.startTime = Date.now();
        fileItem.stats.lastUpdateTime = fileItem.stats.startTime;
        fileItem.stats.bytesUploaded = 0;
      }
      
      const now = Date.now();
      const timeElapsed = (now - fileItem.stats.lastUpdateTime) / 1000; // 秒
      const bytesSinceLastUpdate = Array.from(fileItem.uploadedChunks).length * config.chunkSize - fileItem.stats.bytesUploaded;
      
      // 计算速度 (MB/s)
      if (timeElapsed > 0) {
        fileItem.stats.speed = (bytesSinceLastUpdate / (1024 * 1024)) / timeElapsed;
        fileItem.stats.bytesUploaded = Array.from(fileItem.uploadedChunks).length * config.chunkSize;
        fileItem.stats.lastUpdateTime = now;
      }
      
      // 计算估计剩余时间
      const bytesRemaining = fileItem.file.size - fileItem.stats.bytesUploaded;
      if (fileItem.stats.speed > 0) {
        fileItem.stats.estimatedTime = bytesRemaining / (fileItem.stats.speed * 1024 * 1024); // 秒
      }
      
      // 更新显示
      speedText.textContent = `速度: ${fileItem.stats.speed.toFixed(2)} MB/s`;
      etaText.textContent = `剩余时间: ${formatTime(fileItem.stats.estimatedTime)}`;
      
      // 继续更新
      if (fileItem.status === 'uploading') {
        requestAnimationFrame(() => updateSpeedStats(fileItem));
      }
    }

    // 格式化时间
    function formatTime(seconds) {
      if (isNaN(seconds) || seconds === Infinity) return '--';
      
      if (seconds < 60) {
        return `${Math.round(seconds)} 秒`;
      } else if (seconds < 3600) {
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = Math.round(seconds % 60);
        return `${minutes} 分 ${remainingSeconds} 秒`;
      } else {
        const hours = Math.floor(seconds / 3600);
        const remainingMinutes = Math.floor((seconds % 3600) / 60);
        return `${hours} 小时 ${remainingMinutes} 分`;
      }
    }

    // 格式化文件大小
    function formatFileSize(bytes) {
      if (bytes === 0) return '0 Bytes';
      
      const k = 1024;
      const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // 开始上传
    async function startUpload(fileItem) {
      try {
        // 初始化统计信息
        fileItem.stats = {
          startTime: null,
          lastUpdateTime: null,
          bytesUploaded: 0,
          speed: 0,
          estimatedTime: 0
        };
        
        // 检查文件是否已上传
        const existingUpload = await checkExistingUpload(fileItem);
        if (existingUpload.completed) {
          updateFileStatus(fileItem.fileId, 'completed', 100);
          return;
        }

        // 更新已上传的分片
        fileItem.uploadedChunks = new Set(existingUpload.uploadedChunks || []);

        // 更新进度
        const progress = calculateProgress(fileItem);
        updateFileStatus(fileItem.fileId, 'uploading', progress);

        // 开始上传分片
        uploadChunks(fileItem);
      } catch (error) {
        console.error('检查文件失败:', error);
        updateFileStatus(fileItem.fileId, 'failed');
      }
    }

    // 检查文件是否已部分上传
    async function checkExistingUpload(fileItem) {
      try {
        const response = await fetch(`${config.checkUrl}?fileId=${fileItem.fileId}`);
        if (!response.ok) {
          throw new Error(`检查文件状态失败: ${response.statusText}`);
        }
        return await response.json();
      } catch (error) {
        console.error('检查文件状态网络错误:', error);
        // 网络错误时假设没有已上传的分片
        return { completed: false, uploadedChunks: [] };
      }
    }

    // 上传分片
    async function uploadChunks(fileItem) {
      // 如果文件不是上传状态，不继续
      if (fileItem.status !== 'uploading') return;

      // 获取待上传的分片
      const pendingChunks = fileItem.chunks
        .filter(chunk => !fileItem.uploadedChunks.has(chunk.index) && chunk.status !== 'uploading');

      // 如果没有待上传的分片，检查是否全部完成
      if (pendingChunks.length === 0) {
        checkUploadCompletion(fileItem);
        return;
      }

      // 控制并发上传数量
      const chunksToUpload = pendingChunks.slice(0, Math.max(0, config.concurrency - activeUploads));
      
      // 上传每个分片
      for (const chunk of chunksToUpload) {
        // 如果文件状态不再是上传中，停止上传
        if (fileItem.status !== 'uploading') break;
        
        uploadChunk(fileItem, chunk);
      }
    }

    // 上传单个分片
    async function uploadChunk(fileItem, chunk) {
      // 更新分片状态
      chunk.status = 'uploading';
      activeUploads++;

      // 创建新的AbortController
      fileItem.abortController = new AbortController();
      const signal = fileItem.abortController.signal;

      try {
        // 准备表单数据
        const formData = new FormData();
        const chunkBlob = fileItem.file.slice(chunk.start, chunk.end);
        
        formData.append('file', chunkBlob);
        formData.append('fileName', chunk.fileName);
        formData.append('fileId', chunk.fileId);
        formData.append('chunkIndex', chunk.index);
        formData.append('totalChunks', fileItem.chunks.length);
        formData.append('chunkSize', chunk.size);
        formData.append('totalSize', fileItem.file.size);

        // 记录开始时间
        const startTime = Date.now();
        
        // 上传分片
        const response = await fetch(config.uploadUrl, {
          method: 'POST',
          body: formData,
          signal
        });

        if (!response.ok) {
          throw new Error(`上传失败: ${response.statusText}`);
        }

        // 记录上传时间
        const uploadTime = (Date.now() - startTime) / 1000;
        console.log(`分片 ${chunk.index} 上传完成，耗时: ${uploadTime.toFixed(2)}s，大小: ${formatFileSize(chunk.size)}`);

        // 标记分片为已上传
        fileItem.uploadedChunks.add(chunk.index);
        chunk.status = 'completed';

        // 更新进度
        const progress = calculateProgress(fileItem);
        updateFileStatus(fileItem.fileId, 'uploading', progress);

        // 继续上传其他分片
        uploadChunks(fileItem);
      } catch (error) {
        if (error.name === 'AbortError') {
          // 上传被中止
          chunk.status = 'pending';
        } else {
          // 上传失败，重试
          console.error(`分片 ${chunk.index} 上传失败:`, error);
          chunk.status = 'failed';
          chunk.retryCount++;

          if (chunk.retryCount <= config.maxRetries) {
            // 指数退避重试策略
            const delay = config.retryDelay * Math.pow(2, chunk.retryCount - 1);
            setTimeout(() => {
              uploadChunk(fileItem, chunk);
            }, delay);
          } else {
            // 重试次数过多，标记整个文件上传失败
            updateFileStatus(fileItem.fileId, 'failed');
          }
        }
      } finally {
        activeUploads--;
      }
    }

    // 计算上传进度
    function calculateProgress(fileItem) {
      const uploadedChunks = fileItem.uploadedChunks.size;
      const totalChunks = fileItem.chunks.length;
      return Math.round((uploadedChunks / totalChunks) * 100);
    }

    // 检查上传是否完成
    function checkUploadCompletion(fileItem) {
      const uploadedChunks = fileItem.uploadedChunks.size;
      const totalChunks = fileItem.chunks.length;

      if (uploadedChunks === totalChunks) {
        // 所有分片都已上传完成，触发合并请求
        mergeChunks(fileItem);
      }
    }

    // 合并分片
    async function mergeChunks(fileItem) {
      try {
        const response = await fetch(`${config.uploadUrl}/merge`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            fileId: fileItem.fileId,
            fileName: fileItem.file.name,
            totalChunks: fileItem.chunks.length,
            totalSize: fileItem.file.size
          })
        });

        if (!response.ok) {
          throw new Error(`合并分片失败: ${response.statusText}`);
        }

        // 记录总上传时间
        const totalTime = (Date.now() - fileItem.stats.startTime) / 1000;
        console.log(`文件 ${fileItem.file.name} 上传完成，总耗时: ${formatTime(totalTime)}`);
        
        // 更新状态为完成
        updateFileStatus(fileItem.fileId, 'completed', 100);
      } catch (error) {
        console.error('合并分片失败:', error);
        updateFileStatus(fileItem.fileId, 'failed');
      }
    }

    // 暂停上传
    function pauseUpload(fileId) {
      const fileItem = uploadQueue.find(item => item.fileId === fileId);
      if (!fileItem || fileItem.status !== 'uploading') return;

      // 中止当前上传
      if (fileItem.abortController) {
        fileItem.abortController.abort();
      }

      // 更新状态
      fileItem.status = 'paused';
      updateFileStatus(fileId, 'paused', fileItem.progress);
    }

    // 恢复上传
    function resumeUpload(fileId) {
      const fileItem = uploadQueue.find(item => item.fileId === fileId);
      if (!fileItem || fileItem.status !== 'paused') return;

      // 更新状态
      fileItem.status = 'uploading';
      updateFileStatus(fileId, 'uploading', fileItem.progress);

      // 继续上传
      uploadChunks(fileItem);
    }

    // 取消上传
    function cancelUpload(fileId) {
      const fileItemIndex = uploadQueue.findIndex(item => item.fileId === fileId);
      if (fileItemIndex === -1) return;

      const fileItem = uploadQueue[fileItemIndex];

      // 中止当前上传
      if (fileItem.abortController) {
        fileItem.abortController.abort();
      }

      // 从DOM中移除
      if (fileItem.element && fileItem.element.parentNode) {
        fileItem.element.parentNode.removeChild(fileItem.element);
      }

      // 从队列中移除
      uploadQueue.splice(fileItemIndex, 1);

      // 如果队列为空，显示空状态
      const uploadQueueElement = document.getElementById('upload-queue');
      if (uploadQueue.length === 0) {
        uploadQueueElement.innerHTML = `
          <div class="upload-queue-empty">
            <i class="fa fa-folder-open-o"></i>
            <p>暂无上传任务</p>
          </div>
        `;
      }
    }
  });