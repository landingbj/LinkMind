let queryLock = false;
var PromptDialog = 0;
const tTextQuery = window.tText || ((s) => s);

// Renders the chain-of-thought reasoning content (reasoning_content) as a
// collapsible block above the final answer. Returns empty string if no
// reasoning text is present.
function ensureReasoningBlockStyles() {
    if (document.getElementById('reasoning-block-styles')) {
        return;
    }
    var style = document.createElement('style');
    style.id = 'reasoning-block-styles';
    style.textContent = ''
        + '.reasoning-block > summary { list-style: none; }'
        + '.reasoning-block > summary::-webkit-details-marker { display: none; }'
        + '.reasoning-block > summary::marker { content: ""; }'
        + '.reasoning-block .reasoning-arrow { display:inline-block; width:0; height:0; margin-right:6px; vertical-align:middle;'
        + ' border-left:5px solid currentColor; border-top:4px solid transparent; border-bottom:4px solid transparent;'
        + ' transition: transform 0.18s ease; }'
        + '.reasoning-block[open] > summary .reasoning-arrow { transform: rotate(90deg); }';
    document.head.appendChild(style);
}

function restoreEscapedMarkdownNewlines(text) {
    var value = String(text || '');
    if (value.indexOf('\n') === -1 && /\\n/.test(value)) {
        return value.replace(/\\r\\n|\\n/g, '\n');
    }
    return value;
}

function normalizeCompactMarkdownTables(text) {
    var value = String(text || '');
    value = value.replace(/(^|\n)(#{1,6}[^\n|]*?)\s+(\|[^|\n]+(?:\|[^|\n]+){2,}\|)/g, '$1$2\n\n$3');
    value = value.replace(/([^\n|])\s+(\|[^|\n]+(?:\|[^|\n]+){2,}\|)/g, '$1\n\n$2');
    for (var i = 0; i < 4; i++) {
        value = value
            .replace(/\|\s+(?=\|[ \t]*:?-{3,}:?[ \t]*(?:\|[ \t]*:?-{3,}:?[ \t]*)+\|)/g, '|\n')
            .replace(/\|\s+(?=\|[^|\n]*\|[^|\n]*\|)/g, '|\n')
            .replace(/(\|[^|\n]+(?:\|[^|\n]+){2,}\|)\s+(?=(?:#{1,6}\s+|>{1,3}\s+|(?:[-*+]|\d{1,2}[.)])\s+))/g, '$1\n\n');
    }
    return value;
}

function normalizeAssistantMarkdown(text) {
    var value = restoreEscapedMarkdownNewlines(text)
        .replace(/\r\n?/g, '\n')
        .replace(/([^\n])\s+(#{1,6}\s+)/g, '$1\n\n$2')
        .replace(/([^\n])\s+(-{3,}\s*)/g, '$1\n$2');
    value = normalizeCompactMarkdownTables(value)
        .replace(/([^\n])\s+((?:[-*+]|\d{1,2}[.)])\s+)/g, '$1\n$2');
    return normalizeCompactMarkdownTables(value).trim();
}

function escapeAssistantHtml(text) {
    return String(text || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function isSafeAssistantUrl(url, allowImageData) {
    var value = String(url || '').trim();
    if (!value) {
        return false;
    }
    if (value[0] === '#' || value[0] === '/' || value.indexOf('./') === 0 || value.indexOf('../') === 0) {
        return true;
    }
    if (allowImageData && /^data:image\/(png|jpe?g|gif|webp);base64,/i.test(value)) {
        return true;
    }
    try {
        var parsed = new URL(value, window.location.href);
        return parsed.protocol === 'http:' || parsed.protocol === 'https:' || parsed.protocol === 'mailto:';
    } catch (e) {
        return false;
    }
}

function sanitizeAssistantMarkdownHtml(html) {
    var template = document.createElement('template');
    template.innerHTML = String(html || '');
    var allowedTags = new Set([
        'P', 'BR', 'STRONG', 'B', 'EM', 'I', 'DEL', 'S', 'CODE', 'PRE',
        'BLOCKQUOTE', 'UL', 'OL', 'LI', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
        'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD', 'A', 'IMG', 'HR'
    ]);
    var dangerousTags = new Set(['SCRIPT', 'STYLE', 'IFRAME', 'OBJECT', 'EMBED', 'LINK', 'META']);

    function cleanNode(node) {
        Array.from(node.childNodes).forEach(function (child) {
            if (child.nodeType === Node.COMMENT_NODE) {
                child.remove();
                return;
            }
            if (child.nodeType !== Node.ELEMENT_NODE) {
                return;
            }

            var tagName = child.tagName;
            if (dangerousTags.has(tagName)) {
                child.remove();
                return;
            }
            if (!allowedTags.has(tagName)) {
                var fragment = document.createDocumentFragment();
                while (child.firstChild) {
                    fragment.appendChild(child.firstChild);
                }
                child.replaceWith(fragment);
                cleanNode(node);
                return;
            }

            Array.from(child.attributes).forEach(function (attr) {
                var name = attr.name.toLowerCase();
                var value = attr.value;
                var keep = false;
                if (tagName === 'A' && (name === 'href' || name === 'title')) {
                    keep = name === 'title' || isSafeAssistantUrl(value, false);
                } else if (tagName === 'IMG' && (name === 'src' || name === 'alt' || name === 'title')) {
                    keep = name !== 'src' || isSafeAssistantUrl(value, true);
                } else if (tagName === 'CODE' && name === 'class') {
                    keep = /^language-[\w-]+$/.test(value);
                } else if ((tagName === 'TH' || tagName === 'TD') && (name === 'align' || name === 'colspan' || name === 'rowspan')) {
                    keep = true;
                }
                if (!keep) {
                    child.removeAttribute(attr.name);
                }
            });
            if (tagName === 'A' && child.getAttribute('href')) {
                child.setAttribute('target', '_blank');
                child.setAttribute('rel', 'noopener noreferrer');
            }
            cleanNode(child);
        });
    }

    cleanNode(template.content);
    return template.innerHTML;
}

function renderAssistantMarkdown(text) {
    var markdownText = normalizeAssistantMarkdown(text);
    if (!markdownText) {
        return '<p></p>';
    }
    try {
        if (typeof marked !== 'undefined' && marked.parse) {
            var html = marked.parse(markdownText, {
                gfm: true,
                breaks: true
            });
            return sanitizeAssistantMarkdownHtml(html);
        }
    } catch (e) {
        console.warn('markdown render failed', e);
    }
    return escapeAssistantHtml(markdownText).replace(/\n/g, '<br>');
}

function scrollChatToBottom() {
    var itemContent = document.getElementById('item-content');
    if (itemContent) {
        itemContent.scrollTop = itemContent.scrollHeight;
    }
}

function finishAssistantStreaming(jqObj) {
    if (!jqObj || !jqObj.length) {
        return;
    }
    jqObj.removeClass('result-streaming');
    jqObj.parent().children('.better-result').removeClass('result-streaming');
}

function stripTrailingDecodeArtifacts(text) {
    return String(text || '').replace(/\uFFFD+$/g, '');
}

function buildReasoningBlockHtml(reasoningText, openByDefault) {
    if (!reasoningText) {
        return '';
    }
    ensureReasoningBlockStyles();
    var rendered = renderAssistantMarkdown(reasoningText);
    var openAttr = openByDefault ? ' open' : '';
    var title = tTextQuery('思考过程');
    return `<details class="reasoning-block"${openAttr} style="margin:0 0 10px 0;padding:8px 12px;background:#f5f7fb;border-left:3px solid #c7d2fe;border-radius:6px;color:#4b5563;font-size:13px;">`
        + `<summary style="cursor:pointer;color:#6366f1;font-weight:600;outline:none;user-select:none;display:flex;align-items:center;"><span class="reasoning-arrow"></span><span>${title}</span></summary>`
        + `<div class="reasoning-block__content" style="margin-top:6px;line-height:1.6;white-space:normal;word-break:break-word;">${rendered}</div>`
        + `</details>`;
}

function getSecurityFilterMessage(content) {
    var text = (content && String(content).trim())
        ? String(content).trim()
        : tTextQuery('该问题触发安全过滤规则，已停止生成回答。请调整提问内容后重试。');
    if (text === 'Your request was blocked by the security filter.') {
        return tTextQuery('该问题触发安全过滤规则，已停止生成回答。请调整提问内容后重试。');
    }
    return text;
}

function renderSecurityFilterNotice(jqObj, content) {
    var message = getSecurityFilterMessage(content)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/\n/g, '<br>');
    finishAssistantStreaming(jqObj);
    jqObj.html(
        '<div style="display:flex;gap:10px;align-items:flex-start;max-width:680px;padding:12px 14px;border:1px solid #f2c9c9;border-radius:8px;background:#fff7f7;color:#7f1d1d;">'
        + '<div aria-hidden="true" style="width:22px;height:22px;line-height:22px;text-align:center;border-radius:50%;background:#dc2626;color:#fff;font-weight:700;flex:0 0 auto;">!</div>'
        + '<div style="min-width:0;">'
        + '<div style="font-weight:600;margin-bottom:4px;">' + tTextQuery('安全过滤已拦截') + '</div>'
        + '<div style="line-height:1.6;color:#991b1b;word-break:break-word;">' + message + '</div>'
        + '</div>'
        + '</div>'
    );
    jqObj.parent().children('.better-result').hide();
}

const words = [
    "股票", "天气", "油价", "新闻", "财经", "健康", "医疗",
    "教育", "游戏", "购物", "电影推荐", "美食", "食谱",
    "旅行", "翻译", "心理咨询", "投资", "区块链", "AI绘画",
    "编程助手", "数据分析", "社交媒体", "聊天", "运动健身", "租车",
    "交通", "智能家居", "宠物护理", "时尚", "工作助手", "营销",
    "SEO优化", "招聘", "天气预报", "空气质量", "旅行规划", "导航",
    "语音助手", "虚拟助手", "记账", "理财", "房产估值", "租房助手",
    "日程管理", "音乐推荐", "图书推荐", "家装设计", "电商", "促销分析",
    "心理健康", "疾病诊断", "运动分析", "天气提醒", "历史知识",
    "科学探索", "编程教学", "语言学习", "语法检查", "面试准备", "写作助手",
    "论文查重", "考试复习", "定制化学习", "儿童教育", "旅游翻译",
    "语音翻译", "多语言沟通", "实时翻译", "新闻追踪", "事件提醒",
    "个人助理", "学习路径", "职业规划", "求职简历", "招聘筛选",
    "游戏攻略", "竞技分析", "运动战术", "健身计划", "减脂",
    "心率监控", "血压监控", "睡眠分析", "营养摄入", "减压助手",
    "会议记录", "在线课堂", "绘画教学", "智能合同助手", "法律顾问",
    "税务助手", "智能财务", "危机预测", "客户服务", "自然灾害预警",
    "环保数据", "气候变化", "星座运势", "心理测试", "名人信息"
];

// 绑定页面回车事件
$('#queryContent').keydown(function (event) {
    if (event.keyCode === 13) {
        event.preventDefault();
        if (!$('#queryBtn').prop("disabled")) {
            textQuery();
        }
    }
});


function showBallDiv() {
    const ballDiv = document.getElementById("ball-div");
    if (ballDiv) {
        ballDiv.style.display = "block";
    }
}

function hideBallDiv() {
    const ballDiv = document.getElementById("ball-div");
    if (ballDiv) {
        ballDiv.style.display = "none";
    }
}

function matchingAgents(word) {
    $('#item-content').hide();
    showBallDiv();
    highlightWord(word);
    setTimeout(() => {
        hideBallDiv();
        resetBallState();
        $('#item-content').show();
    }, 3000);
}

async function textQuery() {
    if (queryLock) {
        alert(tTextQuery("有对话正在进行请耐心等待"));
        return;
    }
    if (typeof ensureChatBottomBarVisible === 'function') {
        ensureChatBottomBarVisible();
    }
    queryLock = true;
    disableQueryBtn();
    let question = $('#queryBox textarea').val();
    if (isBlank(question)) {
        alert(tTextQuery("请输入有效字符串！！！"));
        $('#queryBox textarea').val('');
        enableQueryBtn();
        querying = false;
        queryLock = false;
        return;
    }

    let agentId = currentAppId;

    // 隐藏非对话内容
    // hideHelloContent();

    $('#queryBox textarea').val('');
    let conversation = {user: {question: question}, robot: {answer: ''}}

    await sleep(200);

    if (currentPromptDialog !== undefined && currentPromptDialog.key === SOCIAL_NAV_KEY) {
        let robotAnswerJq = await socialAgentsConversation(question);
    } else {
        let robotAnswerJq = await newConversation(conversation);
        getTextResult(question.trim(), robotAnswerJq, conversation, agentId);
    }
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
        addRobotDialog(tTextQuery('请问您想发什么消息？</br>'));
        setSocialPromptStepDone(action);
        unlockInput();
        return;
    } else if (action === TIMER_WHAT) {
        TIMER_DATA["message"] = prompt;
        addRobotDialog(tTextQuery('现在吗？还是之后具体什么时间？</br>'));
        setSocialPromptStepDone(action);
        unlockInput();
        return;
    } else if (action === TIMER_WHEN) {
        getStandardTime(action, prompt);
        return;
    } else if (action === ROBOT_ENABLE) {
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
                    SOCIAL_CHANEL["appIdList"] = JSON.parse(JSON.stringify(appIdList));
                    ;
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
                TIMER_DATA["channelId"] = SOCIAL_CHANEL["channelId"];
                addRobotDialog(tTextQuery('已收到您的指令，请等待好消息。</br>'));
                setSocialPromptStepDone(action);
                addTimerTask();
            } else {
                addRobotDialog(tTextQuery('现在吗？还是之后具体什么时间？</br>'));
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
                addRobotDialog(tTextQuery('好的，协助您默认打理半个小时。</br>'));
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
            addRobotDialog(tTextQuery('您需要将后续会话，委托给助理自动答复吗？</br>'));
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
                let html = '<div>' + tTextQuery('请扫描以下') + appName + tTextQuery('的二维码授权：') + '</div></br><img src="' + qrCodeUrl + '" alt="QR code" />';
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
    addRobotDialog(tTextQuery('调用失败!</br>'));
    unlockInput();
}

function unlockInput() {
    $('#queryBox textarea').val('');
    queryLock = false;
    if (typeof ensureChatBottomBarVisible === 'function') {
        ensureChatBottomBarVisible();
    }
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
                addRobotDialog(tTextQuery('请问您想给谁发消息(需要您存在的通讯录中的人名或群名)。</br>'));
            }
            console.log(res);
        },
        error: function () {
            returnFailedResponse();
        }
    });
}

const CONVERSATION_CONTEXT = [];

/**
 * Authorization for v1/chat/completions (Bearer Landing key) when logged in. See apikeys.js fetchLandingApiKeyForChat.
 */
async function buildChatCompletionsAuthHeader() {
    if (typeof window.fetchLandingApiKeyForChat !== "function") {
        return null;
    }
    var key = await window.fetchLandingApiKeyForChat();
    return key ? ("Bearer " + key) : null;
}

function getSelectedChatModel() {
    return $('#model-select').val() || $('.model-select:visible').first().val() || $('.model-select').first().val();
}

function applySelectedChatModel(paras) {
    var selectedModel = getSelectedChatModel();
    if (selectedModel) {
        paras.model = selectedModel;
    }
}

function getTextResult(question, robootAnswerJq, conversation, agentId) {
    var result = '';
    var paras = {
        "category": window.category,
        "messages": CONVERSATION_CONTEXT.concat([
            {"role": "user", "content": question}
        ]),
        "temperature": 0.8,
        "max_tokens": 4096,
        // "stream": true,
        "stream": true
    };
    if (agentId) {
        paras["worker"] = "appointedWorker";
        paras["agentId"] = agentId;
        paras["stream"] = true;
    }

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
                    finishAssistantStreaming(robootAnswerJq);
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
                    finishAssistantStreaming(robootAnswerJq);
                    answer = result;
                }
                // 判断图生文
                else if (res.samUrl != null) {
                    result = tTextQuery("您所上传的图片的意思是：") + "<br><b>" + tTextQuery("类别") + "</b>：" + res.classification + "<br><b>" + tTextQuery("描述") + "</b>：" + res.caption + "<br>" +
                        "<b>" + tTextQuery("分割后的图片") + "</b>：  <img src='" + res.samUrl + "' alt='Image'><br>";
                    robootAnswerJq.html(result);
                    finishAssistantStreaming(robootAnswerJq);
                    let p = robootAnswerJq.parent().parent().parent();
                    p.children('.idx').children('.appendVoice').children('audio').hide();
                    p.children('.idx').children('.appendVoice').children('select').hide();
                    answer = result;
                } else if (res.enhanceImageUrl != null) {
                    result = tTextQuery("加强后的图片如下：") + "<br><img src='" + res.enhanceImageUrl + "' alt='Image'><br>";
                    robootAnswerJq.html(result);
                    finishAssistantStreaming(robootAnswerJq);
                    answer = result;
                } else if (res.svdVideoUrl != null) {
                    result = "<video id='media' src='" + res.svdVideoUrl + "' controls width='400px' height='400px'></video>";
                    robootAnswerJq.html(result);
                    finishAssistantStreaming(robootAnswerJq);
                    answer = result;
                } else if (res.type != null && res.type === 'mot') {
                    result = "<video id='media' src='" + res.data + "' controls width='400px' height='400px'></video>";
                    robootAnswerJq.html(result);
                    finishAssistantStreaming(robootAnswerJq);
                    answer = result;
                } else if (res.type != null && res.type === 'mmediting') {
                    result = "<video id='media' src='" + res.data + "' controls width='400px' height='400px'></video>";
                    robootAnswerJq.html(result);
                    finishAssistantStreaming(robootAnswerJq);
                    answer = result;
                } else {
                    if (paras["stream"]) {
                        streamOutput(paras, question, robootAnswerJq);
                    } else {
                        generalOutput(paras, question, robootAnswerJq);
                    }
                }
            } else {
                robootAnswerJq.html(tTextQuery("调用失败！"));
                finishAssistantStreaming(robootAnswerJq);
                answer = tTextQuery('调用失败! ');
            }
            $('#queryBox textarea').val('');
            queryLock = false;
            enableQueryBtn();
            querying = false;
            conversation.robot.answer = answer;
            addConv(conversation);
            if (typeof ensureChatBottomBarVisible === 'function') {
                ensureChatBottomBarVisible();
            }
        },
        error: function () {
            $('#queryBox textarea').val('');
            enableQueryBtn();
            querying = false;
            queryLock = false;
            robootAnswerJq.html(tTextQuery("调用失败！"));
            finishAssistantStreaming(robootAnswerJq);
            conversation.robot.answer = tTextQuery("调用失败！");
            addConv(conversation);
            if (typeof ensureChatBottomBarVisible === 'function') {
                ensureChatBottomBarVisible();
            }
        }

    });
    return result;
}

async function generalOutput(paras, question, robootAnswerJq) {
    let url = paras.agentId ? 'chat/go' : 'v1/chat/completions';
    applySelectedChatModel(paras);
    var ajaxOpts = {
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: url,
        // url: "v1/worker/completions",
        data: JSON.stringify(paras),
        success: function (res) {
            if (res.choices === undefined) {
                queryLock = false;
                finishAssistantStreaming(robootAnswerJq);
                robootAnswerJq.html(tTextQuery("调用失败！"));
                if (typeof ensureChatBottomBarVisible === 'function') {
                    ensureChatBottomBarVisible();
                }
                return;
            }
            if (res.choices.length === 0) {
                finishAssistantStreaming(robootAnswerJq);
                if (typeof ensureChatBottomBarVisible === 'function') {
                    ensureChatBottomBarVisible();
                }
                return;
            }
            var choice = res.choices[0] || {};
            var chatMessage = choice.message || {};
            if (choice.finish_reason === 'content_filter') {
                renderSecurityFilterNotice(robootAnswerJq, chatMessage.content);
                enableQueryBtn();
                querying = false;
                queryLock = false;
                if (typeof ensureChatBottomBarVisible === 'function') {
                    ensureChatBottomBarVisible();
                }
                return;
            }
            if (chatMessage.filename !== undefined) {
                var a = '';
                let isFirst = true;
                for (let i = 0; i < chatMessage.filename.length; i++) {
                    let marginLeft = isFirst ? '0' : '50px';
                    a += `<a class="filename" style="list-style:none;color: #666;text-decoration: none;display: inline-block; " href="uploadFile/downloadFile?filePath=${chatMessage.filepath[i]}&fileName=${chatMessage.filename[i]}">${chatMessage.filename[i]}</a></br>`;
                    isFirst = false;
                }
            }
            var reasoningText = chatMessage.reasoning_content;
            var reasoningHtml = buildReasoningBlockHtml(reasoningText, false);
            if (chatMessage.content === undefined) {
                if (reasoningHtml) {
                    robootAnswerJq.html(reasoningHtml);
                }
                finishAssistantStreaming(robootAnswerJq);
                if (typeof ensureChatBottomBarVisible === 'function') {
                    ensureChatBottomBarVisible();
                }
                return;
            }
            var fullText = renderAssistantMarkdown(chatMessage.content);
            result = `
                        ${reasoningHtml}
                        ${fullText}
                        ${chatMessage.imageList && chatMessage.imageList.length > 0 ? chatMessage.imageList.map(image => `<img src='${image}' alt='Image' style="max-width:100%; height:auto; margin-bottom:10px;">`).join('') : "" }                        
                        ${chatMessage.filename !== undefined ? `<div style="display: flex;"><div style="width:50px;flex:1">${tTextQuery('附件:')}</div><div style="width:600px;flex:17 padding-left:5px">${a}</div></div><br>` : ""}
                        ${res.source !== undefined ? `<div style="display: flex;"><div style="width:300px;flex:1"><small>${tTextQuery('来源:')}${res.source}</small></div></div><br>` : ""}
                        `
            robootAnswerJq.html(result);
            finishAssistantStreaming(robootAnswerJq);
            enableQueryBtn();
            querying = false;
            if (typeof ensureChatBottomBarVisible === 'function') {
                ensureChatBottomBarVisible();
            }
        },
        error: function () {
            queryLock = false;
            if (typeof ensureChatBottomBarVisible === 'function') {
                ensureChatBottomBarVisible();
            }
            enableQueryBtn();
            querying = false;
            finishAssistantStreaming(robootAnswerJq);
            robootAnswerJq.html(tTextQuery("调用失败！"));
        }
    };
    if (!paras.agentId) {
        var authHeader = await buildChatCompletionsAuthHeader();
        if (authHeader) {
            ajaxOpts.headers = { Authorization: authHeader };
        }
    }
    $.ajax(ajaxOpts);
}

// Prepend a system message carrying an empty <available_skills> block so the
// server-side hook can merge configured skills into the system prompt.
const SKILLS_SYSTEM_PROMPT = ''
    + 'You are LinkMind, an open-source AI middleware.\n\nThe following skills provide specialized instructions for specific tasks.\n'
    + "Use the read tool to load a skill's file when the task matches its description.\n"
    + 'When a skill file references a relative path, resolve it against the skill directory (parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n\n'
    + '<available_skills>\n'
    + '</available_skills>\n';

const SKILLS_TOOLS = [
    {
        type: 'function',
        function: {
            name: 'read',
            description: 'Read the contents of a file. Supports text files and images (jpg, png, gif, webp). Images are sent as attachments. For text files, output is truncated to 2000 lines or 50KB (whichever is hit first). Use offset/limit for large files. When you need the full file, continue with offset until complete.',
            strict: true,
            parameters: {
                type: 'object',
                properties: {
                    path: {
                        type: 'string',
                        description: 'Path to the file to read (relative or absolute)',
                        schemaExtensions: {}
                    },
                    offset: {
                        type: 'number',
                        description: 'Line number to start reading from (1-indexed)',
                        schemaExtensions: {}
                    },
                    limit: {
                        type: 'number',
                        description: 'Maximum number of lines to read',
                        schemaExtensions: {}
                    },
                    file_path: {
                        type: 'string',
                        description: 'Path to the file to read (relative or absolute)',
                        schemaExtensions: {}
                    },
                    filePath: {
                        type: 'string',
                        description: 'Path to the file to read (relative or absolute)',
                        schemaExtensions: {}
                    },
                    file: {
                        type: 'string',
                        description: 'Path to the file to read (relative or absolute)',
                        schemaExtensions: {}
                    }
                },
                required: [],
                additionalProperties: false,
                schemaExtensions: {}
            }
        }
    },
    {
        type: 'function',
        function: {
            name: 'exec',
            description: 'Execute shell commands with background continuation. Use yieldMs/background to continue later via process tool. Use pty=true for TTY-required commands (terminal UIs, coding agents).',
            strict: true,
            parameters: {
                type: 'object',
                properties: {
                    command: {
                        type: 'string',
                        description: 'Shell command to execute',
                        schemaExtensions: {}
                    },
                    workdir: {
                        type: 'string',
                        description: 'Working directory (defaults to cwd)',
                        schemaExtensions: {}
                    },
                    env: {
                        type: 'object',
                        schemaExtensions: {
                            patternProperties: {
                                '^(.*)$': { type: 'string' }
                            }
                        }
                    },
                    yieldMs: {
                        type: 'number',
                        description: 'Milliseconds to wait before backgrounding (default 10000)',
                        schemaExtensions: {}
                    },
                    background: {
                        type: 'boolean',
                        description: 'Run in background immediately',
                        schemaExtensions: {}
                    },
                    timeout: {
                        type: 'number',
                        description: 'Timeout in seconds (optional, kills process on expiry)',
                        schemaExtensions: {}
                    },
                    pty: {
                        type: 'boolean',
                        description: 'Run in a pseudo-terminal (PTY) when available (TTY-required CLIs, coding agents)',
                        schemaExtensions: {}
                    },
                    elevated: {
                        type: 'boolean',
                        description: 'Run on the host with elevated permissions (if allowed)',
                        schemaExtensions: {}
                    },
                    host: {
                        type: 'string',
                        description: 'Exec host (sandbox|gateway|node).',
                        schemaExtensions: {}
                    },
                    security: {
                        type: 'string',
                        description: 'Exec security mode (deny|allowlist|full).',
                        schemaExtensions: {}
                    },
                    ask: {
                        type: 'string',
                        description: 'Exec ask mode (off|on-miss|always).',
                        schemaExtensions: {}
                    },
                    node: {
                        type: 'string',
                        description: 'Node id/name for host=node.',
                        schemaExtensions: {}
                    }
                },
                required: ['command'],
                additionalProperties: false,
                schemaExtensions: {}
            }
        }
    }
];

function getCurrentUserId() {
    var fromCookie = (typeof getCookie === 'function' ? getCookie('userId') : '') || '';
    if (fromCookie) {
        return fromCookie;
    }
    try {
        return localStorage.getItem('userId') || '';
    } catch (e) {
        return '';
    }
}

function ensureSystemSkillsPrompt(paras) {
    if (!paras || !Array.isArray(paras.messages)) {
        return;
    }
    var userId = getCurrentUserId();
    if (userId) {
        paras.extraBody = Object.assign({}, paras.extraBody || {}, { userId: String(userId) });
    }
    if (!Array.isArray(paras.tools) || paras.tools.length === 0) {
        paras.tools = SKILLS_TOOLS;
    }
    var hasSystem = paras.messages.some(function (m) {
        return m && m.role === 'system';
    });
    if (hasSystem) {
        return;
    }
    paras.messages = [{
        role: 'system',
        content: SKILLS_SYSTEM_PROMPT
    }].concat(paras.messages);
}

function streamOutput(paras, question, robootAnswerJq) {
    ensureSystemSkillsPrompt(paras);

    function isJsonString(str) {
        try {
            JSON.parse(str);
            return true;
        } catch (e) {
            return false;
        }
    }

    async function generateStream(paras) {
        let url = paras.agentId ? 'chat/go' : 'v1/chat/completions';
        applySelectedChatModel(paras);
        var streamHeaders = {
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        };
        if (!paras.agentId) {
            var authH = await buildChatCompletionsAuthHeader();
            if (authH) {
                streamHeaders["Authorization"] = authH;
            }
        }
        const response = await fetch(url, {
            method: "POST",
            cache: "no-cache",
            keepalive: true,
            headers: streamHeaders,
            body: JSON.stringify(paras),
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();

        let fullText = '';
        let flag = true;
        let buffer = '';
        let sourceContent = '';
        let pageContent = '';
        let reasoningContent = '';
        let securityFiltered = false;
        let lastStreamMessage = {};
        let lastStreamJson = {};
        robootAnswerJq.html('<p></p>');

        function renderStreamAnswer(chatMessage, json, attachmentHtml) {
            if (chatMessage !== undefined) {
                lastStreamMessage = Object.assign({}, chatMessage || {});
                if (attachmentHtml !== undefined) {
                    lastStreamMessage.attachmentHtml = attachmentHtml;
                }
            }
            if (json !== undefined) {
                lastStreamJson = json || {};
            }
            chatMessage = lastStreamMessage || {};
            json = lastStreamJson || {};
            fullText = renderAssistantMarkdown(pageContent);
            let reasoningHtmlStream = buildReasoningBlockHtml(reasoningContent, false);
            result = `
                        ${reasoningHtmlStream}
                        ${fullText}
                        ${chatMessage.imageList && chatMessage.imageList.length > 0 ? chatMessage.imageList.map(image => `<img src='${image}' alt='Image' style="max-width:100%; height:auto; margin-bottom:10px;">`).join('') : ""}
                        ${chatMessage.filename !== undefined ? `<div style="display: flex;"><div style="width:50px;flex:1">${tTextQuery('附件:')}</div><div style="width:600px;flex:17 padding-left:5px">${chatMessage.attachmentHtml || ''}</div></div>` : ""}
                        ${chatMessage.context || chatMessage.contextChunkIds ? `<div class="context-box"><div class="loading-box">${tTextQuery('正在索引文档')}&nbsp;&nbsp;<span></span></div><a style="float: right; cursor: pointer; color:cornflowerblue" onClick="retry(${CONVERSATION_CONTEXT.length + 1})">${tTextQuery('更多通用回答')}</a></div>` : ""}
                        ${json.source !== undefined ? `<div style="display: flex;"><div style="width:300px;flex:1"><small>${tTextQuery('来源:')}${json.source}</small></div></div><br>` : ""}`
            robootAnswerJq.html(result);
            scrollChatToBottom();
        }

        function finalizeStreamAnswer() {
            var cleanPageContent = stripTrailingDecodeArtifacts(pageContent);
            var cleanSourceContent = stripTrailingDecodeArtifacts(sourceContent);
            if (cleanPageContent !== pageContent || cleanSourceContent !== sourceContent) {
                pageContent = cleanPageContent;
                sourceContent = cleanSourceContent;
                renderStreamAnswer();
            }
        }

        function processStreamText(res) {
            if (!res) {
                return;
            }
            if (res.startsWith("error:")) {
                finishAssistantStreaming(robootAnswerJq);
                robootAnswerJq.html(res.replaceAll('error:', ''));
                flag = false;
                return;
            }
            buffer += res;
            const chunkArray = buffer.split("\n\n");

            for (let chunk of chunkArray) {
                chunk = chunk.replaceAll('data: ', '').trim();
                if (chunk === "[DONE]") {
                    finalizeStreamAnswer();
                    CONVERSATION_CONTEXT.push({"role": "user", "content": question});
                    CONVERSATION_CONTEXT.push({"role": "assistant", "content": sourceContent || pageContent});
                    flag = false;
                    break;
                }
                if (chunk.length === 0 || !isJsonString(chunk)) {
                    buffer = chunk;
                    break;
                } else {
                    buffer = '';
                }
                let json = JSON.parse(chunk);
                if (json.choices === undefined) {
                    queryLock = false;
                    finishAssistantStreaming(robootAnswerJq);
                    robootAnswerJq.html(tTextQuery("调用失败！"));
                    flag = false;
                    break
                }
                if (json.choices.length === 0) {
                    continue;
                }
                let choice = json.choices[0] || {};
                let chatMessage = choice.delta || choice.message || {};
                if (choice.finish_reason === 'content_filter') {
                    securityFiltered = true;
                    sourceContent = getSecurityFilterMessage(pageContent || chatMessage.content);
                    renderSecurityFilterNotice(robootAnswerJq, sourceContent);
                    continue;
                }
                let a = '';
                if (chatMessage.filename) {
                    for (let i = 0; i < chatMessage.filename.length; i++) {
                        a += `<a class="filename" style="list-style:none;color: #666;text-decoration: none;display: inline-block; " href="uploadFile/downloadFile?filePath=${chatMessage.filepath[i]}&fileName=${chatMessage.filename[i]}">${chatMessage.filename[i]}</a></br>`;
                    }
                }
                if (chatMessage.reasoning_content) {
                    reasoningContent += chatMessage.reasoning_content;
                    // Only render the reasoning-only block while the final answer
                    // hasn't started streaming yet. Otherwise we would overwrite
                    // already-rendered content with just the reasoning block.
                    if (!pageContent) {
                        let reasoningOnlyHtml = buildReasoningBlockHtml(reasoningContent, true);
                        robootAnswerJq.html(reasoningOnlyHtml + '<p></p>');
                    }
                }
                if (chatMessage.content === undefined || chatMessage.content === null || chatMessage.content === '') {
                    continue;
                }
                // console.log("content:", chatMessage);
                if(json.source) {
                    sourceContent  +=  chatMessage.content;
                }
                pageContent += chatMessage.content;
                renderStreamAnswer(chatMessage, json, a);
                if (chatMessage.contextChunkIds) {
                    if (chatMessage.contextChunkIds instanceof Array) {
                        getCropRect(chatMessage.contextChunkIds, fullText, robootAnswerJq);
                    }
                }
            }
        }

        while (flag) {
            const {value, done} = await reader.read();
            if (done) {
                processStreamText(decoder.decode());
                if (flag) {
                    finalizeStreamAnswer();
                    if (pageContent) {
                        CONVERSATION_CONTEXT.push({"role": "user", "content": question});
                        CONVERSATION_CONTEXT.push({"role": "assistant", "content": sourceContent || pageContent});
                    }
                    flag = false;
                }
                break;
            }
            processStreamText(decoder.decode(value, {stream: true}));
        }
        return {securityFiltered: securityFiltered};
    }

    generateStream(paras).then(r => {
        finishAssistantStreaming(robootAnswerJq);
        if (typeof ensureChatBottomBarVisible === 'function') {
            ensureChatBottomBarVisible();
        }
        var securityFiltered = r && r.securityFiltered;
        if (!securityFiltered && CONVERSATION_CONTEXT.length > 0) {
            const last = CONVERSATION_CONTEXT[CONVERSATION_CONTEXT.length - 1];
            if (last && last.role === 'assistant' && last.content) {
                txtTovoice(last.content, "default");
            }
        }
        enableQueryBtn();
        querying = false;
        queryLock = false;
        let betterResult = robootAnswerJq.parent().children('.better-result')
        if (securityFiltered) {
            betterResult.hide();
        } else {
            betterResult.show();
        }
    }).catch((err) => {
        console.error(err);
        finishAssistantStreaming(robootAnswerJq);
        if (typeof ensureChatBottomBarVisible === 'function') {
            ensureChatBottomBarVisible();
        }
        enableQueryBtn();
        querying = false;
        queryLock = false;
        robootAnswerJq.html(tTextQuery("系统繁忙，请稍后再试！"));
    });
}

async function filterChunk(filenames, filePaths, contextChunkIds, result, jqObj) {
    return new Promise((resolve, reject) => {
        console.log("chunks : " + contextChunkIds);

        var params = {
            "category": window.category,
            'chunkIds': contextChunkIds,
            'result': result
        }
        $.ajax({
            type: "POST",
            contentType: "application/json;charset=utf-8",
            url: "pdf/filterChunk",
            data: JSON.stringify(params),
            success: function (res) {
                jqObj.children('.loading-box').remove();
                if (res.code !== 0) {
                    console.log(res);
                    jqObj.apppend(`<div style="float: left; color:red;">${tTextQuery('未获取到文件截图')}</div>`);
                    return;
                }
                let data = res.data;
                if (!(data instanceof Array)) {
                    console.log(data);
                    jqObj.apppend(`<div style="float: left;">${tTextQuery('未获取到截图')}</div>`);
                    return;
                }
                let html = `<div  style="float: left;">${(function () {
                    let h = '';
                    for (let i = 0; i < data.length; i++) {
                        let cropData = data[i];
                        let chunk = cropData.chunk;
                        let filename = cropData.filename;
                        let filePath = cropData.filePath;
                        h += `<a style="cursor: pointer; margin-left:12px" data-name="${filename}" data-chunk="${chunk}" data-path="${filePath}" data-result="${result}"  data-url="" onclick=cropFromFile(this) >${i + 1}</a>`;
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
        if (contextChunkIds.length === 0) {
            return;
        }
        let chunkData = []
        for (let i = 0; i < contextChunkIds.length; i++) {
            let c_data = {
                "chunkId": contextChunkIds[i],
                "result": result
            }
            chunkData.push(c_data);
        }
        var params = {
            "category": window.category,
            'chunkData': chunkData,
        }
        $.ajax({
            type: "POST",
            contentType: "application/json;charset=utf-8",
            url: "pdf/cropRect",
            data: JSON.stringify(params),
            success: function (res) {
                jqObj.children('.context-box').children('.loading-box').remove();
                let context_jq = jqObj.children('.context-box');
                if (res.code !== 0) {
                    console.log(res);
                    context_jq.append(`<div style="float: left; color:red; display:iniline-block;">${tTextQuery('未获取到文件截图')}</div><br>`);
                    return;
                }
                if (!context_jq) {
                    return;
                }
                let data = res.data;
                if (!(data instanceof Array)) {
                    context_jq.append(`<div style="float: left; display:iniline-block;">${tTextQuery('未获取到截图')}</div><br>`);
                    console.log(data);
                    return;
                }
                if (data.length == 0) {
                    context_jq.append(`<div style="float: left; display:iniline-block;">${tTextQuery('未获取到截图')}</div><br>`);
                    return;
                }
                let html = `<div class="context-link" style="float: left;"><span>${tTextQuery('内容定位:')}</span>${(function () {
                    let h = '';
                    for (let i = 0; i < data.length; i++) {
                        let cropData = data[i];
                        let pages = []
                        let rects = []
                        let pageRect = cropData.rects;
                        for (let j = 0; j < pageRect.length; j++) {
                            let pr = pageRect[j];
                            pages.push(pr.page);
                            rects.push(pr.rect);
                        }
                        pages = JSON.stringify(pages);
                        rects = JSON.stringify(rects);
                        let filename = cropData.filename;
                        let filePath = cropData.filePath;
                        h += `<a style="cursor: pointer; margin-left:12px; color:cornflowerblue" data-name="${filename}"  data-pages="${pages}"  data-rects="${rects}" data-path="${filePath}" data-result="${result}"  data-url="" onclick=cropByRects(this) >${i + 1}</a>`;
                    }
                    return h;
                })()}</div><br>`;
                context_jq.append(html);
            },
            error: function () {
                jqObj.children('.context-box').children('.loading-box').remove();
            }
        });
    });
}

function cropByRects(dom) {
    let jqObj = $(dom);
    let url = jqObj.data('urls');
    if (url) {
        showImageMask(url);
        return;
    }
    // updata url
    let filename = jqObj.data('name');
    let filePath = jqObj.data('path');
    let result = jqObj.data('result');
    let pages = jqObj.data('pages')
    let rects = jqObj.data('rects');
    let pageRects = [];
    for (let i = 0; i < pages.length; i++) {
        pageRects.push({
            "page": pages[i],
            "rect": rects[i]
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
        'chunkData': chunkData
    }
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "pdf/cropByRect",
        data: JSON.stringify(param),
        success: function (res) {
            if (res.code !== 0) {
                console.log(res);
                return;
            }
            let data = res.data;
            if (data.length && data.length > 0) {
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
    if (url) {
        showImageMask(url);
        return;
    }
    // updata url
    let chunk = jqObj.data('chunk');
    let filename = jqObj.data('name');
    let filePath = jqObj.data('path');
    let result = jqObj.data('result');
    let param = {
        'filename': filename,
        'filePath': filePath,
        'chunk': chunk,
        'result': result
    }
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "pdf/crop",
        data: JSON.stringify(param),
        success: function (res) {
            if (res.code !== 0) {
                console.log(res);
                return;
            }
            let data = res.data;
            if (data.length && data.length > 0) {
                let urls = data.join();
                jqObj.data("urls", data.join());
                showImageMask(urls);
            }
        }
    });
}


$('#pdfMask').mouseup(
    function () {
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
        "max_tokens": 4096,
        "stream": true
    };
    let question = preArr[preArr.length - 1]['content']
    addUserDialog(question);
    let robootAnswerJq = addRobotDialog('');
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
