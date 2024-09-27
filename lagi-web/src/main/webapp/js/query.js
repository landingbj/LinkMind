let queryLock = false;
var PromptDialog = 0;
// 绑定页面回车事件
$('#queryContent').keydown(function (event) {
    if (event.keyCode === 13) {
        event.preventDefault();
        textQuery();
    }
});

function textQuery() {
    if (queryLock) {
        alert("有对话正在进行请耐心等待");
        return;
    }
    queryLock = true;
    disableQueryBtn();
    let question = $('#queryBox textarea').val();
    if (isBlank(question)) {
        alert("请输入有效字符串！！！");
        $('#queryBox textarea').val('');
        enableQueryBtn();
        querying = false;
        queryLock = false;
        return;
    }

    // 隐藏非对话内容
    hideHelloContent();
    $('#queryBox textarea').val('');
    let conversation = {user: {question: question}, robot: {answer: ''}}
    sleep(200).then(() => {
        if (currentPromptDialog !== undefined && currentPromptDialog.key === SOCIAL_NAV_KEY) {
            socialAgentsConversation(question);
        } else {
            let robotAnswerJq = newConversation(conversation);
            getTextResult(question.trim(), robotAnswerJq, conversation);
        }
    })
}

const GET_QR_CODE = "GET_QR_CODE";
const TIMER_WHO = "TIMER_WHO";
const TIMER_WHAT = "TIMER_WHAT";
const TIMER_WHEN = "TIMER_WHEN";
const ROBOT_ENABLE = "ROBOT_ENABLE";
const SOCIAL_AUTH_APP = [];
const SOCIAL_CHANEL = {};
const SOCIAL_PROMPT_STEPS = new Map();
SOCIAL_PROMPT_STEPS.set(GET_QR_CODE, 0);
SOCIAL_PROMPT_STEPS.set(TIMER_WHO, 0);
SOCIAL_PROMPT_STEPS.set(TIMER_WHAT, 0);
SOCIAL_PROMPT_STEPS.set(TIMER_WHEN, 0);
SOCIAL_PROMPT_STEPS.set(ROBOT_ENABLE, 0);
const SOCIAL_APP_MAP = new Map();

const TIMER_DATA = {};

function setSocialPromptStepDone(step) {
    SOCIAL_PROMPT_STEPS.set(step, 1);
}

function resetSocialPromptStep() {
    SOCIAL_PROMPT_STEPS.forEach((value, key) => {
        SOCIAL_PROMPT_STEPS.set(key, 0);
    });
    SOCIAL_AUTH_APP.length = 0;
}

function getNextSocialPromptStep() {
    let nextStep = '';
    for (let [key, value] of SOCIAL_PROMPT_STEPS) {
        if (value === 0) {
            nextStep = key;
            break;
        }
    }
    return nextStep;
}

function socialAgentsConversation(question) {
    let questionHtml = '<div>' + question + '</div>';
    addUserDialog(questionHtml);
    let nextStep = getNextSocialPromptStep();
    nextPrompt(nextStep, question);
}

function nextPrompt(action, prompt) {
    if (action === TIMER_WHO) {
        TIMER_DATA["contact"] = prompt;
        addRobotDialog( '请问您想发什么消息？</br>');
        setSocialPromptStepDone(action);
        unlockInput();
        return;
    } else if (action === TIMER_WHAT) {
        TIMER_DATA["message"] = prompt;
        addRobotDialog( '现在吗？还是之后具体什么时间？</br>');
        setSocialPromptStepDone(action);
        unlockInput();
        return;
    } else if (action === TIMER_WHEN) {
        getStandardTime(action, prompt);
        return;
    }  else if (action === ROBOT_ENABLE) {
        startRobot(action, prompt);
        return;
    }
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "/v1/rpa/nextPrompt",
        data: JSON.stringify({"action": action, "prompt": prompt}),
        success: function (res) {
            if (res.status === "failed") {

            } else {
                if (action === GET_QR_CODE) {
                    let appIdList = res.appId.split(',');
                    SOCIAL_CHANEL["appIdList"] = JSON.parse(JSON.stringify(appIdList));;
                    let username = res.username;
                    let channelId = res.channelId;
                    if (appIdList.length > 0) {
                        SOCIAL_AUTH_APP.push(...appIdList);
                        let appId = appIdList[0];
                        SOCIAL_CHANEL["appId"] = appId;
                        SOCIAL_CHANEL["username"] = username;
                        SOCIAL_CHANEL["channelId"] = channelId;
                        getLoginQrCode(appId, username);
                    }
                }
            }
        },
        error: function () {
            returnFailedResponse();
        }
    });
}

function getStandardTime(action, prompt) {
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "/v1/rpa/getStandardTime",
        data: JSON.stringify({"action": action, "prompt": prompt}),
        success: function (res) {
            if (res.status === "success") {
                TIMER_DATA["sendTime"] = res.data;
                TIMER_DATA["appId"] = SOCIAL_CHANEL["appId"];
                TIMER_DATA["channelId"]= SOCIAL_CHANEL["channelId"];
                addRobotDialog( '已收到您的指令，请等待好消息。</br>');
                setSocialPromptStepDone(action);
                addTimerTask();
            } else {
                addRobotDialog( '现在吗？还是之后具体什么时间？</br>');
            }
            unlockInput();
        },
        error: function () {
            returnFailedResponse();
        }
    });
}

function startRobot(prompt, action) {
    let startRobotRequest = {
        prompt: prompt,
        appIdList: SOCIAL_CHANEL["appIdList"],
        username: SOCIAL_CHANEL["username"],
    };

    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "/v1/rpa/startRobot",
        data: JSON.stringify(startRobotRequest),
        success: function (res) {
            if (res.status === "success" && res.robotEnable) {
                addRobotDialog( '好的，协助您默认打理半个小时。</br>');
            }
            setSocialPromptStepDone(action);
            resetSocialPromptStep();
        },
        error: function () {
            returnFailedResponse();
        }
    });
}

function addTimerTask() {
    console.log('TIMER_DATA', TIMER_DATA)
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "/v1/rpa/addTimerTask",
        data: JSON.stringify(TIMER_DATA),
        success: function (res) {
            if (res.status === "failed") {
            } else {
            }
            addRobotDialog( '您需要将后续会话，委托给助理自动答复吗？</br>');
        },
        error: function () {
            returnFailedResponse();
        }
    });
}

function getLoginQrCode(appId, username) {
    $.ajax({
        type: "GET",
        contentType: "application/json;charset=utf-8",
        url: "/v1/rpa/getLoginQrCode",
        data: {"appId": appId, "username": username},
        success: function (res) {
            if (res.status === 10) {
                let appName = SOCIAL_APP_MAP.get(appId);
                let qrCodeUrl = res.image_url;
                let html = '<div>请扫描以下'+ appName + '的二维码授权：</div></br><img src="' + qrCodeUrl + '" alt="二维码" />';
                addRobotDialog(html + '</br>');
                getLoginStatus(appId, username);
            }
        },
        error: function () {
            returnFailedResponse();
        }
    });
}

function returnFailedResponse() {
    addRobotDialog('调用失败!</br>');
    unlockInput();
}

function unlockInput() {
    $('#queryBox textarea').val('');
    queryLock = false;
}

function getLoginStatus(appId, username) {
    let html = '';
    $.ajax({
        type: "GET",
        contentType: "application/json;charset=utf-8",
        url: "/v1/rpa/getLoginStatus",
        data: {"appId": appId, "username": username},
        success: function (res) {
            SOCIAL_AUTH_APP.shift();
            if (SOCIAL_AUTH_APP.length > 0) {
                let nextAppId = SOCIAL_AUTH_APP[0];
                let nextUsername = SOCIAL_CHANEL["username"];
                getLoginQrCode(nextAppId, nextUsername);
            } else {
                setSocialPromptStepDone(GET_QR_CODE);
                unlockInput();
                addRobotDialog( '请问您想给谁发消息(需要您存在的通讯录中的人名或群名)。</br>');
            }
            console.log(res);
        },
        error: function () {
            returnFailedResponse();
        }
    });
}

const CONVERSATION_CONTEXT = [];

function getTextResult(question, robootAnswerJq, conversation) {
    var result = '';
    var paras = {
        "rag": rag,
        "category": window.category,
        "messages": CONVERSATION_CONTEXT.concat([
            {"role": "user", "content": question}
        ]),
        "temperature": 0.8,
        "max_tokens": 1024,
        "stream": true
    };

    var queryUrl = "search/detectIntent";
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: queryUrl,
        data: JSON.stringify(paras),
        success: function (res) {
            let answer = '';
            if (res != null && res.status === "success") {
                // 判断文生图
                if (res.result !== undefined) {
                    result = `
                        <img src='${res.result}' alt='Image' style="width: 320px;">
                    `
                    robootAnswerJq.html(result);
                    answer = result;
                    let p = robootAnswerJq.parent().parent().parent();
                    p.children('.idx').children('.appendVoice').children('audio').hide();
                    p.children('.idx').children('.appendVoice').children('select').hide();
                }
                // 判断生成指令集
                else if (res.instructions != null) {
                    var instructions = JSON.stringify(res.instructions, null, 2);
                    result = syntaxHighlight(instructions);
                    robootAnswerJq.html("<pre>" + result + "</pre>");
                    answer = result;
                }
                // 判断图生文
                else if (res.samUrl != null) {
                    result = "您所上传的图片的意思是：<br><b>类别</b>：" + res.classification + "<br><b>描述</b>：" + res.caption + "<br>" +
                        "<b>分割后的图片</b>：  <img src='" + res.samUrl + "' alt='Image'><br>";
                    robootAnswerJq.html(result);
                    let p = robootAnswerJq.parent().parent().parent();
                    p.children('.idx').children('.appendVoice').children('audio').hide();
                    p.children('.idx').children('.appendVoice').children('select').hide();
                    answer = result;
                } else if (res.enhanceImageUrl != null) {
                    result = "加强后的图片如下：<br>" + "<img src='" + res.enhanceImageUrl + "' alt='Image'><br>";
                    robootAnswerJq.html(result);
                    answer = result;
                } else if (res.svdVideoUrl != null) {
                    result = "<video id='media' src='" + res.svdVideoUrl + "' controls width='400px' height='400px'></video>";
                    robootAnswerJq.html(result);
                    answer = result;
                } else if (res.type != null && res.type === 'mot') {
                    result = "<video id='media' src='" + res.data + "' controls width='400px' height='400px'></video>";
                    robootAnswerJq.html(result);
                    answer = result;
                } else if (res.type != null && res.type === 'mmediting') {
                    result = "<video id='media' src='" + res.data + "' controls width='400px' height='400px'></video>";
                    robootAnswerJq.html(result);
                    answer = result;
                } else {
                    if (paras["stream"]) {
                        streamOutput(paras, question, robootAnswerJq);
                    } else {
                        generalOutput(paras, question, robootAnswerJq);
                    }
                }
            } else {
                robootAnswerJq.html("调用失败！");
                answer = '调用失败! ';
            }
            $('#queryBox textarea').val('');
            queryLock = false;
            conversation.robot.answer = answer;
            addConv(conversation);
        },
        error: function () {
            $('#queryBox textarea').val('');
            queryLock = false;
            robootAnswerJq.html("调用失败！");
            conversation.robot.answer = "调用失败！";
            addConv(conversation);
        }

    });
    return result;
}

function generalOutput(paras, question, robootAnswerJq) {
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "v1/chat/completions",
        data: JSON.stringify(paras),
        success: function (res) {

            if (res === null || res.status === "failed") {
                robootAnswerJq.html("调用失败！");
                return;
            }
            var json = res.data[0];
            var a = `
                    <a style="color: #666;text-decoration: none;" href="uploadFile/downloadFile?filePath=${json.filepath}&fileName=${json.filename}">${json.filename}</a>
                    `
            var t = json.text;
            t = t.replaceAll("\n", "<br>");
            result = `
                            ${t} <br>
                            ${json.imageList != undefined ? `<img src='${json.imageList[0]}' alt='Image'>` : ""}
                            ${json.filename != undefined ? `附件:${a}` : ""}<br>
                    `
            CONVERSATION_CONTEXT.push({"role": "user", "content": question});
            CONVERSATION_CONTEXT.push({"role": "assistant", "content": json.text});

            txtTovoice(json.text, "default");
            robootAnswerJq.html(result);
            enableQueryBtn();
            querying = false;
        }
    });
}

function streamOutput(paras, question, robootAnswerJq) {
    async function generateStream(paras) {
        const response = await fetch('v1/chat/completions', {
            method: "POST",
            cache: "no-cache",
            keepalive: true,
            headers: {
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
            },
            body: JSON.stringify(paras),
        });

        const reader = response.body.getReader();
        let fullText = '';
        let flag = true;
        while (flag) {
            const {value, done} = await reader.read();
            let res =  new TextDecoder().decode(value);
            if(res.startsWith("error:")) {
                robootAnswerJq.html(res.replaceAll('error:', ''));
                return;
            }
            let chunkStr = new TextDecoder().decode(value).replaceAll('data: ', '').trim();
            const chunkArray = chunkStr.split("\n\n");
            for (let i = 0; i < chunkArray.length; i++) {
                let chunk = chunkArray[i];
                if (chunk === "[DONE]") {
                    CONVERSATION_CONTEXT.push({"role": "user", "content": question});
                    CONVERSATION_CONTEXT.push({"role": "assistant", "content": fullText});
                    flag = false;
                    break;
                }
                
                let json = JSON.parse(chunk);
                if (json.choices === undefined) {
                    queryLock = false;
                    robootAnswerJq.html("调用失败！");
                    break
                }
                if (json.choices.length === 0) {
                    continue;
                }
                let chatMessage = json.choices[0].message;
                let a = '';
                if (chatMessage.filename === undefined){

                }else{
                    //var a = '<ul style="list-style:none;padding-left:5px;">';
                    a = '';
                    let isFirst = true; // 标记是否是第一个文件名

                    for (let i = 0; i < chatMessage.filename.length; i++) {
                        let marginLeft = isFirst ? '0' : '50px';
                        a += `<a class="filename" style="list-style:none;color: #666;text-decoration: none;display: inline-block; " href="uploadFile/downloadFile?filePath=${chatMessage.filepath[i]}&fileName=${chatMessage.filename[i]}">${chatMessage.filename[i]}</a>`;
                        isFirst = false;
                        //console.log("这里的路径是："+chatMessage.filepath[i]);
                        //a+=`<a style="color: #666;text-decoration: none;" href="uploadFile/downloadFile?filePath=${chatMessage.filepath[i]}&fileName=${chatMessage.filename[i]}">${chatMessage.filename[i]}</a><br>`;
                    }
                    //a +='</ul>'
                }

                if (chatMessage.content === undefined) {
                    continue;
                }
                let t = chatMessage.content;
                t = t.replaceAll("\n", "<br>");
                fullText += t;
                result = `
                        ${fullText} <br>
                        ${chatMessage.imageList !== undefined && chatMessage.imageList.length > 0 ? `<img src='${chatMessage.imageList[0]}' alt='Image'>` : ""}
                        ${chatMessage.filename !== undefined ? `<div style="display: flex;"><div style="width:50px;flex:1">附件:</div><div style="width:600px;flex:17 padding-left:5px">${a}</div></div>` : ""}<br>
                        ${chatMessage.context || chatMessage.contextChunkIds ?  `<div class="context-box"><div class="loading-box">正在索引文档&nbsp;&nbsp;<span></span></div><a style="float: right; cursor: pointer; color:cornflowerblue" onClick="retry(${CONVERSATION_CONTEXT.length + 1})">更多通用回答</a></div>` : ""}<br>`
                        // `;
                if(chatMessage.contextChunkIds) {
                    if(chatMessage.contextChunkIds instanceof Array) {
                        // filterChunk(chatMessage.filename, chatMessage.filepath, chatMessage.contextChunkIds, fullText, robootAnswerJq);
                        // result += `<div class="loading-box">正在索引文档&nbsp;&nbsp;<span></span></div><br>`;
                        getCropRect(chatMessage.contextChunkIds, fullText, robootAnswerJq);
                    }
                }
                robootAnswerJq.html(result);
            }
        }
    }
    

    generateStream(paras).then(r => {
        let lastAnswer = CONVERSATION_CONTEXT[CONVERSATION_CONTEXT.length - 1]["content"]
        txtTovoice(lastAnswer, "default");
        enableQueryBtn();
        querying = false;
    }).catch((err) => {
        console.error(err);
        enableQueryBtn();
        querying = false;
        queryLock = false;
        if(!robootAnswerJq.text) {
            robootAnswerJq.html("调用失败！");
        }
    });
}

async function filterChunk(filenames, filePaths, contextChunkIds, result, jqObj) {
    return new Promise((resolve, reject) => {
        console.log("chunks : " + contextChunkIds);
        
        var params = {
            "category": window.category,
            'chunkIds' : contextChunkIds,
            'result': result
        }
        $.ajax({
            type: "POST",
            contentType: "application/json;charset=utf-8",
            url: "pdf/filterChunk",
            data: JSON.stringify(params),
            success: function (res) {
                jqObj.children('.loading-box').remove();
                if(res.code !== 0) {
                    console.log(res);
                    jqObj.apppend(`<div style="float: left; color:red;">未获取到文件截图</div>`);
                    return;
                } 
                let data = res.data;
                if(!(data instanceof Array)) {
                    console.log(data);
                    jqObj.apppend(`<div style="float: left;">未获取到截图</div>`);
                    return ;
                }
                let html = `<div  style="float: left;">${(function(){
                    let h = '';
                    for(let i = 0; i < data.length; i++) {
                        let cropData =  data[i];
                        let chunk =  cropData.chunk;
                        let filename =  cropData.filename;
                        let filePath =  cropData.filePath;
                        h += `<a style="cursor: pointer; margin-left:12px" data-name="${filename}" data-chunk="${chunk}" data-path="${filePath}" data-result="${result}"  data-url="" onclick=cropFromFile(this) >${i+1}</a>`;
                    }
                    return h;
                })()}</div><br>`;
                jqObj.append(html);
            }
        });
    });
}

async function getCropRect(contextChunkIds, result, jqObj) {
    return new Promise((resolve, reject) => {
        if(contextChunkIds.length === 0) {
            return ;
        }
        let chunkData = []
        for(let i = 0; i < contextChunkIds.length ; i++) {
            let c_data = {
                "chunkId":contextChunkIds[i],
                "result": result
            }
            chunkData.push(c_data);
        }
        var params = {
            "category": window.category,
            'chunkData' : chunkData,
        }
        $.ajax({
            type: "POST",
            contentType: "application/json;charset=utf-8",
            url: "pdf/cropRect",
            data: JSON.stringify(params),
            success: function (res) {
                jqObj.children('.context-box').children('.loading-box').remove();
                let context_jq =  jqObj.children('.context-box');
                if(res.code !== 0) {
                    console.log(res);
                    context_jq.apppend(`<div style="float: left; color:red; display:iniline-block;">未获取到文件截图</div><br>`);
                    return;
                } 
                let data = res.data;
                if(!(data instanceof Array)) {
                    context_jq.apppend(`<div style="float: left; display:iniline-block;">未获取到截图</div><br>`);
                    console.log(data);
                    return ;
                }
                if(data.length == 0) {
                    context_jq.apppend(`<div style="float: left; display:iniline-block;">未获取到截图</div><br>`);
                    return ;
                }
                let html = `<div class="context-link" style="float: left;"><span>内容定位:</span>${(function(){
                    let h = '';
                    for(let i = 0; i < data.length; i++) {
                        let cropData =  data[i];
                        let pages = []
                        let rects = []
                        let pageRect =  cropData.rects;
                        for(let j = 0; j < pageRect.length; j++) {
                            let pr = pageRect[j];
                            pages.push(pr.page);
                            rects.push(pr.rect);
                        }
                        pages = JSON.stringify(pages);
                        rects = JSON.stringify(rects);
                        let filename =  cropData.filename;
                        let filePath =  cropData.filePath;
                        h += `<a style="cursor: pointer; margin-left:12px; color:cornflowerblue" data-name="${filename}"  data-pages="${pages}"  data-rects="${rects}" data-path="${filePath}" data-result="${result}"  data-url="" onclick=cropByRects(this) >${i+1}</a>`;
                    }
                    return h;
                })()}</div><br>`;
                context_jq.append(html);
            }
        });
    });
}

function cropByRects(dom) {
    let jqObj = $(dom);
    let url = jqObj.data('urls');
    if(url) {
        showImageMask(url);
        return ;
    }
    // updata url 
    let filename = jqObj.data('name');
    let filePath = jqObj.data('path');
    let result = jqObj.data('result');
    let pages = jqObj.data('pages')
    let rects = jqObj.data('rects');
    let pageRects = [];
    for(let i = 0; i < pages.length; i++) {
        pageRects.push({
            "page":pages[i],
            "rect":rects[i]
        });
    }
    let chunkData = [];
    let c_data = {
        "filename": filename,
        "filePath": filePath,
        "result": result,
        "rects": pageRects,
    }
    chunkData.push(c_data);
    let param = {
        "category": window.category,
        'chunkData' : chunkData
    }
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "pdf/cropByRect",
        data: JSON.stringify(param),
        success: function (res) {
            if(res.code !== 0) {
                console.log(res);
                return;
            }
            let data = res.data;
            if(data.length && data.length > 0) {
                let urls = data.join();
                jqObj.data("urls", data.join());
                showImageMask(urls);
            }
        }
    });
}

function cropFromFile(dom) {
    let jqObj = $(dom);
    let url = jqObj.data('urls');
    if(url) {
        showImageMask(url);
        return ;
    }
    // updata url 
    let chunk = jqObj.data('chunk');
    let filename = jqObj.data('name');
    let filePath = jqObj.data('path');
    let result = jqObj.data('result');
    let param = {
        'filename' : filename,
        'filePath' : filePath,
        'chunk' : chunk,
        'result': result
    }
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "pdf/crop",
        data: JSON.stringify(param),
        success: function (res) {
            if(res.code !== 0) {
                console.log(res);
                return;
            } 
            let data = res.data;
            if(data.length && data.length > 0) {
                let urls = data.join();
                jqObj.data("urls", data.join());
                showImageMask(urls);
            }
        }
    });
}


$('#pdfMask').mouseup(
    function() {
        $('#pdfMask').hide();
    }
);

function showImageMask(url) {
    $($('#pdfMask img')[0]).attr('src', url.split(",")[0]);
    $('#pdfMask').show();
}

function retry(index) {
    console.log(CONVERSATION_CONTEXT)
    let preArr = CONVERSATION_CONTEXT.slice(0, index);
    var paras = {
        "rag": false,
        "category": window.category,
        "messages": preArr,
        "temperature": 0.8,
        "max_tokens": 1024,
        "stream": true
    };
    let question = preArr[preArr.length-1]['content']
    addUserDialog(question);
    let robootAnswerJq =  addRobotDialog('');
    if (paras["stream"]) {
        streamOutput(paras, question, robootAnswerJq);
    } else {
        generalOutput(paras, question, robootAnswerJq);
    }
}


function syntaxHighlight(json) {
    json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function (match) {
        var cls = 'number';
        if (/^"/.test(match)) {
            if (/:$/.test(match)) {
                cls = 'key';
            } else {
                cls = 'string';
            }
        } else if (/true|false/.test(match)) {
            cls = 'boolean';
        } else if (/null/.test(match)) {
            cls = 'null';
        }
        return '<span class="' + cls + '">' + match + '</span>';
    });
}
