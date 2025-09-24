// mobile debug console
// @ 调试插件
// var vConsole = new window.VConsole();
var lastFilePath = "";
let mediaRecorder = null;
let audioData = [];

// 播放录音
function playRecording() {
    if (audioData instanceof Blob) {
        const audio = new Audio(URL.createObjectURL(audioData));
        audio.play();
    }
}

// window.onload = function () {
const voiceButton = document.getElementById("voiceIcon");

// 按钮长按开始录音
voiceButton.addEventListener("mousedown", (e) => {
    timeOutEvent = setTimeout(() => {
        longPress()
    }, 300);
    e.preventDefault();
});

voiceButton.addEventListener("mousemove", (e) => {
    clearTimeout(timeOutEvent);
    timeOutEvent = 0;
});

voiceButton.addEventListener("mouseout", () => {
    clearTimeout(timeOutEvent);
    Recoder.stop();
});

// 松开按钮停止录音
voiceButton.addEventListener("mouseup", () => {
    clearTimeout(timeOutEvent);
    Recoder.stop();
});

var timeOutEvent = 0;
$(function () {
    $("#voiceIcon").on({
        touchstart: function (e) {
            longPress();
        },
        touchmove: function () {
            clearTimeout(timeOutEvent);
            timeOutEvent = 0;
        },
        touchend: function () {
            clearTimeout(timeOutEvent);
            if (timeOutEvent != 0) {
                alert("你这是点击，不是长按");
            }
            Recoder.stop();
            return false;
        }
    })
});

function longPress() {
    timeOutEvent = 0;
    Recoder.start();
}


//  转发功能的实现

const forwardButton = document.getElementById("forwardButton");

// 点击事件处理程序
forwardButton.addEventListener("click", () => {
    // 获取当前的IP地址和端口号，这里假设你已经有一个函数来获取它
    const ipAddressAndPort = getCurrentIpAddressAndPort();

    // 创建一个临时的textarea元素
    const tempTextarea = document.createElement("textarea");
    tempTextarea.value = ipAddressAndPort;

    // 将textarea元素添加到DOM中
    document.body.appendChild(tempTextarea);

    // 选中textarea中的文本
    tempTextarea.select();
    tempTextarea.setSelectionRange(0, 99999); // 选中所有文本，兼容不同浏览器

    // 复制选中的文本到剪贴板
    document.execCommand("copy");

    // 从DOM中移除临时的textarea元素
    document.body.removeChild(tempTextarea);

    // 提示用户复制成功
    alert("已复制到剪贴板：" + ipAddressAndPort);
});

// 获取当前的IP地址和端口号的示例函数
function getCurrentIpAddressAndPort() {
    const currentUrl = window.location.href;
    // 获取currentUrl的主机地址
    const currentHost = new URL(currentUrl).host;
    return currentUrl;
}

var voice_url = '';

// 播音功能的实现
function txtTovoice(txt, emotion) {
    // 获取最后一个进行播放。
    var len = $(".myAudio1").length;
// 检查是否至少存在一个匹配的元素
    if (len > 0) {
        // 要发送的 JSON 数据
        const postData = {
            "model": "default",
            "emotion": emotion,
            "text": txt,
            "category": window.category
        };

        // 创建 XMLHttpRequest 对象
        const xhr = new XMLHttpRequest();

        // 配置请求
        xhr.open('POST', '/audio/text2Voice', true);
        xhr.setRequestHeader('Content-Type', 'application/json');

        // 设置响应处理函数
        xhr.onload = function () {
            if (xhr.status === 200) {
                // 请求成功，处理响应数据
                const responseText = xhr.responseText;
                var json = JSON.parse(responseText);
                if (json.status == "success") {
                    console.log(json.data);

                    voice_url = json.data;
                    // const audioElement = document.getElementsByClassName('myAudio1')[len - 1];
                    $(".myAudio1")[len-  1].src = json.data
                    // const playButton = document.getElementsByClassName('playIcon1')[len - 1];
                    // const audioSource = document.getElementById("audioSource");
                    const audioplay =  document.getElementsByClassName('audioplay')[len-1];
                    audioplay.removeAttribute('disabled');
                    audioplay.innerHTML = AUDIO_PLAY_ICON
                }
                console.log('响应数据:', responseText);
            } else {
                // 请求失败，处理错误
                console.error('请求失败，状态码:', xhr.status);
            }
        };

        xhr.onerror = function () {
            // 发生网络错误
            console.error('网络错误');
        };

        // 发送 POST 请求并将 JSON 数据转换为字符串
        xhr.send(JSON.stringify(postData));

    } else {
        alert("当前无内容播放，请先输入对答");
    }

}

const WORD_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const TXT_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const PDF_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const EXCEL_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const PPT_FILE_SIZE_LIMIT = 5 * 1024 * 1024;
const IMAGE_FILE_SIZE_LIMIT = 10 * 1024 * 1024;
const AUDIO_FILE_SIZE_LIMIT = 2 * 1024 * 1024;
const VIDEO_FILE_SIZE_LIMIT = 50 * 1024 * 1024;

const FILE_SIZE_LIMIT = {
    "pdf": PDF_FILE_SIZE_LIMIT,
    "doc": WORD_FILE_SIZE_LIMIT,
    "docx": WORD_FILE_SIZE_LIMIT,
    "txt": TXT_FILE_SIZE_LIMIT,
    "csv": EXCEL_FILE_SIZE_LIMIT,
    "xlsx": EXCEL_FILE_SIZE_LIMIT,
    "xls": EXCEL_FILE_SIZE_LIMIT,
    "ppt": PPT_FILE_SIZE_LIMIT,
    "pptx": PPT_FILE_SIZE_LIMIT,
    "jpg": IMAGE_FILE_SIZE_LIMIT,
    "jpeg": IMAGE_FILE_SIZE_LIMIT,
    "png": IMAGE_FILE_SIZE_LIMIT,
    "heic": IMAGE_FILE_SIZE_LIMIT,
    "mp3": AUDIO_FILE_SIZE_LIMIT,
    "wav": AUDIO_FILE_SIZE_LIMIT,
    "pcm": AUDIO_FILE_SIZE_LIMIT,
    "mp4": VIDEO_FILE_SIZE_LIMIT,
    "avi": VIDEO_FILE_SIZE_LIMIT
};

function checkFileSizeLimit(selectedFile) {
    let fileType = selectedFile.name.split('.').pop();
    fileType = fileType.toLowerCase();
    var fileSize = selectedFile.size;
    for (var type in FILE_SIZE_LIMIT) {
        if (fileType === type && fileSize < FILE_SIZE_LIMIT[type]) {
            return true;
        }
    }
    return false;
}

let conversation1 = null;
let robootAnswerJq1 = null;


// 定义支持批量上传的文件类型
const batchSupportedTypes = ["pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv"];
var voiceResponse = "";

// 绑定文件上传按钮点击事件
const fileUploadButton = document.getElementById("addButton");
fileUploadButton.addEventListener("click", function () {
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    // 允许选择多个文件
    fileInput.multiple = true;
    fileInput.accept = ".pdf, .doc, .docx, .txt, .csv, .xlsx, .xls, .ppt, .pptx, .jpg, .jpeg, .png, .heic, .mp3, .wav, .avi , .mp4, .pcm";
    fileInput.style.display = "none";

    // 将文件输入元素添加到页面
    document.body.appendChild(fileInput);

    // 模拟点击文件输入元素
    fileInput.click();

    // 监听文件选择事件
    fileInput.addEventListener("change", function () {
        disableQueryBtn();

        const selectedFiles = fileInput.files;
        if (selectedFiles.length > 0) {
            const batchFormData = new FormData();
            let hasBatchFiles = false;
            let hasValidFiles = false;

            for (let i = 0; i < selectedFiles.length; i++) {
                const selectedFile = selectedFiles[i];
                if (selectedFile) {
                    // 获取文件类型
                    let fileType = selectedFile.name.split('.').pop();
                    fileType = fileType.toLowerCase();

                    if (!checkFileSizeLimit(selectedFile)) {
                        hideHelloContent();
                        let conversation = {
                            user: { question: "您所上传的文件 " + selectedFile.name },
                            robot: { answer: '鉴于当前资源有限，请适当缩减文件大小，敬请您的谅解！' }
                        };
                        newConversation(conversation);
                        addConv(conversation);
                        continue;
                    }

                    if (batchSupportedTypes.includes(fileType)) {
                        // 支持批量上传的文件添加到批量上传的 FormData 中
                        batchFormData.append("files", selectedFile);
                        hasBatchFiles = true;
                        hasValidFiles = true;
                    } else {
                        // 不支持批量上传的文件单独处理
                        singleFileUpload(selectedFile, fileType);
                        hasValidFiles = true;
                    }
                }
            }

            if (hasBatchFiles) {
                // 进行批量上传
                batchUpload(batchFormData);
            }

            if (!hasValidFiles) {
                // 如果没有有效的文件，不创建会话
                enableQueryBtn();
            }
        } else {
            console.log("没有选中文件");
            enableQueryBtn();
        }
        // 移除文件输入元素
        document.body.removeChild(fileInput);
    });
});

function singleFileUpload(selectedFile, fileType) {
    // 创建 FormData 对象
    const formData = new FormData();

    var question = "";
    // 设置服务器端点URL
    let serverEndpoint = "";
    var fileStatus = "";
    if (fileType === "pdf" || fileType === "doc" || fileType === "docx" || fileType === "txt" ||
        fileType === "xls" || fileType === "xlsx" || fileType === "ppt" || fileType === "pptx" ||
        fileType === "csv") {
        question = "您所上传的文档文件名称为：" + selectedFile.name;
        formData.append("file", selectedFile);
        serverEndpoint = "/uploadFile/asynchronousUpload?category=" + window.category  + "&knowledgeBaseId=" + currentKbId;
        fileStatus = "doc";
    } else if (fileType === "jpg" || fileType === "jpeg" || fileType === "png" || fileType === "heic") {
        question = "您所上传的图片是：" + selectedFile.name;
        serverEndpoint = "/uploadFile/uploadImageFile";
        formData.append("file", selectedFile);
        fileStatus = "pic";
    } else if (fileType === "mp3" || fileType === "wav" || fileType === "pcm") {
        question = "您所上传的音频文件名称为：" + selectedFile.name;
        formData.append("files", selectedFile);
        formData.append("category", "selectedFile");
        formData.append("total_epoch", 20);
        formData.append("batch_size", 4);
        serverEndpoint = "/audio/audioTrain";
        fileStatus = "voice";
    } else if (fileType === "avi" || fileType === "mp4") {
        formData.append("file", selectedFile);
        question = "您所上传的视频解析为：";
        serverEndpoint = "/uploadFile/uploadVideoFile";
        formData.append("file", selectedFile);
        fileStatus = "video";
    }

    if (fileStatus == "") {
        alert("请选择指定的文件类型");
        enableQueryBtn();
        return;
    }
    // hideHelloContent();

    let conversation = { user: { question: question }, robot: { answer: '' } };
    let robootAnswerJq = newConversation(conversation);

    // 发送 AJAX 请求
    const xhr = new XMLHttpRequest();
    xhr.open("POST", serverEndpoint, true);
    xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4) {
            if (xhr.status === 200) {
                // 请求成功，可以在这里处理服务器的响应
                responseText = xhr.responseText;
                if (responseText != "") {
                    var json = JSON.parse(responseText);
                    handleUploadResponse(json, fileStatus, selectedFile);
                }
            } else {
                // 请求失败，处理上传失败的情况
                textQuery1("文件上传结果", "上传文件失败", "doc");
                enableQueryBtn();
                return;
            }
        }
    };

    // 发送 FormData
    xhr.send(formData);
}



function batchUpload(batchFormData) {
    let serverEndpoint = "/uploadFile/asynchronousUpload?category=" + window.category;
     if(globalUserId) {
         serverEndpoint = `/uploadFile/asynchronousUpload?category=${window.category}&userId=${globalUserId}`;
     }

    let fileNames = [];
    // 遍历 FormData 中的所有文件，获取文件名
    for (let i = 0; i < batchFormData.getAll('files').length; i++) {
        fileNames.push(batchFormData.getAll('files')[i].name);
    }
    let question = "";
    // 拼接包含文件名的问题描述
    if (fileNames.length > 1){
        question = "你上传的文档包含：\n" + fileNames.map(file => '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;' + file).join('<br>');
    }else {
        question = "你上传的文档包含：" + fileNames.map(file => '&nbsp;&nbsp;' + file).join('<br>');
    }
    // let question = "上传文档：" + fileNames.join(', ');
    let fileStatus = "doc";

    // hideHelloContent();

    let conversation = { user: { question: question }, robot: { answer: '' } };
    let robootAnswerJq = newConversation(conversation);

    // 发送 AJAX 请求
    const xhr = new XMLHttpRequest();
    xhr.open("POST", serverEndpoint, true);
    xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4) {
            if (xhr.status === 200) {
                // 请求成功，可以在这里处理服务器的响应
                responseText = xhr.responseText;
                if (responseText != "") {
                    var json = JSON.parse(responseText);
                    handleUploadResponse(json, fileStatus);
                }
            } else {
                // 请求失败，处理上传失败的情况
                textQuery1("文件上传结果", "上传文件失败", "doc");
                enableQueryBtn();
                return;
            }
        }
    };

    // 发送 FormData
    xhr.send(batchFormData);
}

function handleUploadResponse(json, fileStatus, selectedFile) {
    if (fileStatus == 'pic') {
        if (json.status === "success") {
            var question = selectedFile ? "您所上传的图片名称为：" + selectedFile.name : "您批量上传的图片";
            var result = "已经收到您上传的图片。如果您想增强图片，请输入\"图像增强\"。如果您想使用AI描述图片，请输入\"看图说话\"。";
            lastFilePath = json.filePath;
            textQuery1(question, result, fileStatus);
        } else {
            alert("上传失败");
            enableQueryBtn();
            return;
        }
    } else if (fileStatus == "doc") {
        if (json.status == "success") {
            const task_id = json.task_id;
            const startTime = new Date().getTime();
            const getProgress = async () => {
                return new Promise((resolve, reject) => {
                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', `/training/getProgress?task_id=${task_id}`, true);
                    xhr.onload = function () {
                        if (xhr.status === 200) {
                            try {
                                const response = JSON.parse(xhr.responseText);
                                resolve(response);
                            } catch (e) {
                                reject(e);
                            }
                        } else {
                            reject(new Error('获取进度失败'));
                        }
                    };
                    xhr.onerror = function () {
                        reject(new Error('网络错误'));
                    };
                    xhr.send();
                });
            };

            const pollProgress = async () => {
                let progress = 0;
                let update = "上传中";
                while (progress < 100) {
                    const { msg, progress: currentProgress, status } = await getProgress();
                    if (status === 'success') {
                        progress = currentProgress;
                        const endTime = new Date().getTime();
                        const duration = endTime - startTime;
                        let res = "上传中";
                        if (progress === 100) {
                            res = `当前上传进度：${progress}%,上传耗时：${duration / 1000}秒`;
                            textQuery1("文件上传结果", res, "doc");
                            await sleep(3000);
                            addRobotDialog(`恭喜您，系统已接收到您的文档。</br>`);
                            await sleep(3000);
                            addRobotDialog(`正在分析您的文档，进行语料注入...</br>`);
                        } else {
                            res = `当前上传进度：${progress}%,上传状态：${update},上传耗时：${duration / 1000}秒`;
                            textQuery1("文件上传结果", res, "doc");
                        }
                        if (progress === 100) {
                            await sleep(3000);
                            addRobotDialog(`恭喜您，私有语料分析顺利完成已注入，您可以在新的会话中，询问与资料中内容相关问题。如果您想生成指令集，请输入\"生成指令集\"。</br>`);
                            enableQueryBtn();
                            break;
                        }
                    } else {
                        textQuery1("文件上传结果", "已经收到您的资料文档，您可以在新的会话中，询问与资料中内容相关的问题。如果您想生成指令集，请输入\"生成指令集\"。", "doc");
                        enableQueryBtn();
                        break;
                    }

                    // 每隔3秒请求一次
                    await new Promise(resolve => setTimeout(resolve, 3000));
                }
            };
            // 开始轮询获取进度
            pollProgress();

            lastFilePath = json.filePath;
            console.log("文件为" + lastFilePath);
        } else {
            alert("上传失败");
            enableQueryBtn();
            return;
        }
    } else if (fileStatus == "voice") {
        if (json.status == "success") {
            var question = selectedFile ? "您所上传的音频文件名称为：" + selectedFile.name : "您批量上传的音频文件";
            var voiceToTxtResult = voiceToTxt(selectedFile);
            var voiceResult = "";
            if (voiceToTxtResult != '') {
                var voiceToTxtJson = JSON.parse(voiceResponse);
                voiceResult = voiceToTxtJson.msg;
            }
            var result = `
                已将您的语音素材用于训练声音，稍后积累足够时间，可以模仿您所提供的口音发声。<br><br>
                你发送的音频文件的内容为： ${voiceResult}
            `;
            textQuery1(question, result, fileStatus);
        } else {
            alert("上传失败");
            enableQueryBtn();
            return;
        }
    } else if (fileStatus == "video") {
        if (json.status == "success") {
            var question = selectedFile ? "您所上传的视频文件名称为：" + selectedFile.name : "您批量上传的视频文件";
            var result = "已经收到您上传的视频。如果您想视频追踪，请输入\"视频追踪\"。" +
                "如果您想视频增强，请输入\"视频增强\"。";
            lastFilePath = json.filePath;
            textQuery1(question, result, fileStatus);
        } else {
            alert("视频解析失败");
            enableQueryBtn();
            return;
        }
    }
}


//
// //   上传文件功能的实现：
// var voiceResponse = "";
// const fileUploadButton = document.getElementById("addButton");
// fileUploadButton.addEventListener("click", function () {
//     const fileInput = document.createElement("input");
//     fileInput.type = "file";
//     fileInput.accept = ".pdf, .doc, .docx, .txt, .csv, .xlsx, .xls, .ppt, .pptx, .jpg, .jpeg, .png, .heic, .mp3, .wav, .avi , .mp4, .pcm";
//     fileInput.style.display = "none";
//
//     // 将文件输入元素添加到页面
//     document.body.appendChild(fileInput);
//
//     // 模拟点击文件输入元素
//     fileInput.click();
//
//     // 监听文件选择事件
//     fileInput.addEventListener("change", async function () {
//         disableQueryBtn();
//
//         const selectedFile = fileInput.files[0];
//         if (selectedFile) {
//             // 获取文件类型
//             let fileType = selectedFile.name.split('.').pop();
//             fileType = fileType.toLowerCase();
//
//             // 创建 FormData 对象
//             const formData = new FormData();
//
//             var question = "";
//             // 设置服务器端点URL
//             let serverEndpoint = "";
//             var fileStatus = "";
//             if (fileType === "pdf" || fileType === "doc" || fileType === "docx" || fileType === "txt" ||
//                 fileType === "xls" || fileType === "xlsx" || fileType === "ppt" || fileType === "pptx" || fileType === "csv") {
//                 question = "您所上传的文档文件名称为：" + selectedFile.name;
//                 formData.append("file", selectedFile); // 使用 "file" 作为文件字段的名称
//                 if(globalUserId) {
//                     serverEndpoint = `/uploadFile/asynchronousUpload?category=${window.category}&userId=${globalUserId}`;
//                 } else {
//                     serverEndpoint = "/uploadFile/asynchronousUpload?category=" + window.category;
//                 }
//                 fileStatus = "doc";
//             } else if (fileType === "jpg" || fileType === "jpeg" || fileType === "png" || fileType === "heic") {
//                 question = "您所上传的图片是：" + selectedFile.name;
//                 serverEndpoint = "/uploadFile/uploadImageFile";
//                 formData.append("file", selectedFile);
//                 fileStatus = "pic";
//             } else if (fileType === "mp3" || fileType === "wav" || fileType === "pcm") {
//                 question = "您所上传的音频文件名称为：" + selectedFile.name;
//                 formData.append("files", selectedFile);
//                 formData.append("category", "selectedFile");
//                 formData.append("total_epoch", 20);
//                 formData.append("batch_size", 4);
//                 serverEndpoint = "/audio/audioTrain";
//                 fileStatus = "voice";
//             } else if (fileType === "avi" || fileType === "mp4") {
//                 formData.append("file", selectedFile);
//                 question = "您所上传的视频解析为：";
//                 serverEndpoint = "/uploadFile/uploadVideoFile";
//                 formData.append("file", selectedFile);
//                 fileStatus = "video";
//                 // alert("暂不支持视频文件的上传");
//                 // return;
//             }
//
//             if (!checkFileSizeLimit(selectedFile)) {
//                 // hideHelloContent();
//                 let conversation1 = {
//                     user: {question: question},
//                     robot: {answer: '鉴于当前资源有限，请适当缩减文件大小，敬请您的谅解！'}
//                 }
//                 newConversation(conversation1);
//                 addConv(conversation1);
//                 return;
//             }
//
//             if (fileStatus == "") {
//                 alert("请选择指定的文件类型")
//                 return;
//             }
//             // hideHelloContent();
//
//             let conversation1 = {user: {question: question}, robot: {answer: ''}}
//             let robootAnswerJq1 = await newConversation(conversation1);
//
//
//             // 发送 AJAX 请求
//             const xhr = new XMLHttpRequest();
//             xhr.open("POST", serverEndpoint, true);
//             xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest"); // 标识为 AJAX 请求
//             xhr.onreadystatechange = function () {
//                 if (xhr.readyState === 4) {
//                     if (xhr.status === 200) {
//                         // 请求成功，可以在这里处理服务器的响应
//                         responseText = xhr.responseText;
//                         if (responseText != "") {
//                             var json = JSON.parse(responseText);
//                             if (fileStatus == 'pic') {
//                                 if (json.status === "success") {
//                                     var question = "您所上传的图片名称为：" + selectedFile.name;
//                                     var result = "已经收到您上传的图片。" +
//                                         "如果您想增强图片，请输入\"图像增强\"。如果您想使用AI描述图片，请输入\"看图说话\"。";
//                                     lastFilePath = json.filePath;
//                                     textQuery1(question, result, fileStatus);
//                                 } else {
//                                     alert("上传失败");
//                                     return;
//                                 }
//                             } else if (fileStatus == "doc") {
//                                 if (json.status == "success") {
//                                     const task_id = json.task_id; // 从响应中提取 task_id
//                                     const startTime = new Date().getTime();
//                                     const getProgress = async () => {
//                                         return new Promise((resolve, reject) => {
//                                             const xhr = new XMLHttpRequest();
//                                             xhr.open('GET', `/training/getProgress?task_id=${task_id}`, true); // 使用 task_id 构建 URL
//                                             xhr.onload = function () {
//                                                 if (xhr.status === 200) {
//                                                     try {
//                                                         const response = JSON.parse(xhr.responseText);
//                                                         resolve(response);
//                                                     } catch (e) {
//                                                         reject(e);
//                                                     }
//                                                 } else {
//                                                     reject(new Error('获取进度失败'));
//                                                 }
//                                             };
//                                             xhr.onerror = function () {
//                                                 reject(new Error('网络错误'));
//                                             };
//                                             xhr.send();
//                                         });
//                                     };
//
//                                     const pollProgress = async () => {
//                                         let progress = 0;
//                                         let update = "上传中";
//                                         while (progress < 100) {
//                                             const { msg, progress: currentProgress, status } = await getProgress();
//                                             if (status === 'success') {
//                                                 progress = currentProgress;
//                                                 const endTime = new Date().getTime();
//                                                 const duration = endTime - startTime;
//                                                 if (progress === 100) {
//                                                     res = `已经收到您的资料文档，上传总耗时：${duration/1000}秒，您可以在新的会话中，询问与资料中内容相关问题。如果您想生成指令集，请输入\"生成指令集\"。`;
//                                                 }else {
//                                                     res = `当前上传进度：${progress}%,上传状态：${update},上传耗时：${duration/1000}秒`;
//                                                 }
//                                                 textQuery1("文件上传结果", res, "doc");
//                                                 if (progress === 100) {
//                                                     break;
//                                                 }
//                                             } else {
//                                                 textQuery1("文件上传结果", "已经收到您的资料文档，您可以在新的会话中，询问与资料中内容相关的问题。如果您想生成指令集，请输入\"生成指令集\"。", "doc");
//                                                 break;
//                                             }
//
//                                             // 每隔3秒请求一次
//                                             await new Promise(resolve => setTimeout(resolve, 3000));
//                                         }
//                                     };
//                                     // 开始轮询获取进度
//                                     pollProgress();
//
//                                     // var result = "已经收到您的资料文档，您可以在新的会话中，询问与资料中内容相关的问题。如果您想生成指令集，请输入\"生成指令集\"。"
//                                     lastFilePath = json.filePath;
//                                     console.log("文件为" + lastFilePath)
//                                 } else {
//                                     alert("上传失败")
//                                     return;
//                                 }
//                             } else if (fileStatus == "voice") {
//                                 if (json.status == "success") {
//                                     var question = "您所上传的音频文件名称为：" + selectedFile.name;
//                                     var voiceToTxtResult = voiceToTxt(selectedFile);
//                                     var voiceResult = "";
//                                     if (voiceToTxtResult != '') {
//                                         var voiceToTxtJson = JSON.parse(voiceResponse);
//
//                                         voiceResult = voiceToTxtJson.msg;
//
//                                     }
//                                     var result = `
//                     已将您的语音素材用于训练声音，稍后积累足够时间，可以模仿您所提供的口音发声。<br><br>
//                     你发送的音频文件的内容为： ${voiceResult}
//                     `
//                                     textQuery1(question, result, fileStatus);
//                                 } else {
//                                     alert("上传失败");
//                                     return;
//                                 }
//                             } else if (fileStatus == "video") {
//                                 if (json.status == "success") {
//                                     var question = "您所上传的视频文件名称为：" + selectedFile.name;
//                                     var result = "已经收到您上传的视频。如果您想视频追踪，请输入\"视频追踪\"。" +
//                                         "如果您想视频增强，请输入\"视频增强\"。";
//                                     lastFilePath = json.filePath;
//                                     textQuery1(question, result, fileStatus);
//                                 } else {
//                                     alert("视频解析失败")
//                                     return;
//                                 }
//                             }
//                         }
//                     } else {
//                         // 请求失败，处理上传失败的情况
//                         textQuery1("文件上传结果", "上传文件失败", "doc");
//                         return;
//                     }
//                 }
//             };
//
//             // 发送 FormData
//             xhr.send(formData);
//         } else {
//             console.log("没有选中文件");
//         }
//         // 移除文件输入元素
//         document.body.removeChild(fileInput);
//     });
// });


function textQuery1(questionRel, answerRel, fileStatus) {
    if (queryLock) {
        alert("有对话正在进行请耐心等待");
        return;
    }
    queryLock = true;
    try{
        disableQueryBtn();
        let question = questionRel;
        if (isBlank(question)) {
            alert("请输入有效字符串！！！");
            $('#queryBox textarea').val('');
            return;
        }

        // 隐藏非对话内容
        // hideHelloContent();

        const markdownElements = document.querySelectorAll(".markdown");
        var len = markdownElements.length;
        conversation1 = {user: {question: question}, robot: {answer: answerRel}};
        $($(".markdown")[len - 1]).html(answerRel);
        answerRel.replace(/<think>[\s\S]*?<\/think>/g, '');
        answerRel = answerRel.replace(/<[^>]*>/g, "")
        answerRel = answerRel.replaceAll("分割后的图片：", "");
        // 增加不需要音频文件的判断
        if (fileStatus != 'video') {
            txtTovoice(answerRel, "default");
        }
        addConv(conversation1);
        $('#queryBox textarea').val('');
    } catch(error) {
        console.log("An error occurred: " + error);
    } finally {
        queryLock = false;

    }
}


async function voiceToTxt(selectedFile) {
    return new Promise((resolve, reject) => {
        const formData = new FormData();
        const audioDataPost = new Blob([selectedFile], {
            type: 'audio/*'
        });
        formData.append('audioFile', audioDataPost, selectedFile.name);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/search/uploadVoice', false); // 使用false表示同步请求
        xhr.setRequestHeader('Access-Control-Allow-Origin', 'localhost');
        xhr.setRequestHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        xhr.setRequestHeader('Access-Control-Allow-Headers', 'Content-Type');
        xhr.setRequestHeader('Access-Control-Allow-Credentials', 'true');

        xhr.onload = function () {
            if (xhr.status === 200) {
                const responseText = xhr.responseText;
                voiceResponse = responseText
                resolve(responseText); // 上传成功时解决Promise
            } else {
                voiceResponse = "";
                reject(new Error("上传失败")); // 上传失败时拒绝Promise
            }
        };

        xhr.send(formData);
    });
}

function textToVoice(emotionSelect) {
    let text = CONVERSATION_CONTEXT[ $(emotionSelect).closest('.robot-return').data('index')].content.replace(/<think>[\s\S]*?<\/think>/g, '').trim();
    let emotion = $(emotionSelect).attr('value') || $(emotionSelect).data('value');

    // const audioElement = $(emotionSelect).parent().find('.myAudio1')[0];
    // const playButton = $(emotionSelect).parent().find('.playIcon1')[0];

    const audioElement = $(emotionSelect).closest('.appendVoice').find('.myAudio1')[0];
    const audioplay = $(emotionSelect).closest('.appendVoice').find('.audioplay')[0];
    audioplay.innerHTML = MUTE_ICON;
    audioplay.disabled = true;
    // const audioSource = $(emotionSelect).parent().find("audioSource1")[0];

    $.ajax({
        type: "POST",
        url: "audio/text2Voice",
        contentType: "application/json;charset=utf-8",
        data: JSON.stringify({
            "model": "default",
            "emotion": emotion,
            "text": text,
            "category": window.category
        }),
        success: function (res) {
            if (res.status === "success") {
                audioElement.src = res.data;
            }
            audioElement.load();
            audioplay.disabled = false;
            audioplay.innerHTML = AUDIO_PLAY_ICON;
        },
        error: function (res) {
            audioElement.load();
            audioplay.disabled = false;
            audioplay.innerHTML = AUDIO_PLAY_ICON;
        }
    });
}

const record_save_type = 'audio/mp3';

$(document).on("change", ".emotionSelect", function () {
    textToVoice(this);
})

function remoteSolve(blob) {
    const formData = new FormData();
    // 将MP3音频文件添加到FormData对象
    formData.append('audioFile', blob, 'audiofile.wav');
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/search/uploadVoice', true);
    xhr.setRequestHeader('Access-Control-Allow-Origin', 'localhost');
    xhr.setRequestHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    xhr.setRequestHeader('Access-Control-Allow-Headers', 'Content-Type');
    xhr.setRequestHeader('Access-Control-Allow-Credentials', 'true');
    xhr.onload = function () {
        if (xhr.status === 200) {
            console.log('上传成功');
            const responseText = xhr.responseText;
            // 这是 返回的结果
            if (responseText != "") {
                try {
                    var json = JSON.parse(responseText);
                    if (json.code == 0) {
                        console.log(json.msg)
                        $('#queryBox textarea').val(json.msg);
                    }
                } catch {
                    alert("转换失败");
                }
            } else {
                alert("转换失败");
            }
            console.log(responseText);
        } else {

            alert("转换失败");
        }
    };
    xhr.send(formData);
    console.log(audioData);
}


// 新录音
var Recoder = {
    start() {
        navigator.permissions.query(
            {name: 'microphone'}
        ).then(function (permissionStatus) {
            if (permissionStatus.state !== 'prompt') {
                $("#voiceIcon").css("background", "rgb(200, 200, 200)");
                // 停止之前的录制内容
                mediaRecorder && mediaRecorder.stop();

                if (!window.AudioContext) {
                    window.AudioContext = window.AudioContext || window.webkitAudioContext || window.mozAudioContext || window.msAudioContext;
                }

                // 老版浏览器可能根本没有实现mediaDevices, 为其设置一个空对象
                if (!navigator.mediaDevices) {
                    navigator.mediaDevices = {};
                }
                if (!navigator.mediaDevices.getUserMedia) {
                    navigator.mediaDevices.getUserMedia = function (constraints) {
                        let getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia;
                        // 浏览器不支持
                        if (!getUserMedia) {
                            return Promise.reject(new Error('您的浏览器暂不支持 getUserMedia !'));
                        }
                        // 包装老版 getUserMedia
                        return new Promise((resolve, reject) => getUserMedia.call(navigator, constraints, resolve, reject));
                    }
                }
                // 参数
                // let constraints = {audio: true, video: true}; // 请求不带任何参数的音频和视频
                let constraints = {audio: true};  // 请求音频

                let mediaStream = navigator.mediaDevices.getUserMedia(constraints);

                mediaStream.then((stream) => {
                    console.info("打开成功!")

                    let audioContext = new AudioContext();

                    // 创建一个新的音视频对象
                    let destination = audioContext.createMediaStreamDestination();
                    // 创建音视频源
                    let mediaStreamSource = audioContext.createMediaStreamSource(stream);
                    // 将音视频源 链接 到新音视频对象 中
                    mediaStreamSource.connect(destination);
                    // 媒体录制接口
                    //   mimeType: "audio/webm"
                    let _mediaRecorder = new MediaRecorder(destination.stream, {audioBitsPerSecond: 16000});

                    let chunks = [];
                    // 有可用数据流时触发，e.data即需要的音视频数据
                    _mediaRecorder.ondataavailable = (e) => chunks.push(e.data);
                    // 间视频录制结束时触发
                    _mediaRecorder.onstop = () => {
                        // 通过Blob数据块, 合成完整的Blob块数据
                        let blob = new Blob(chunks, {'type': 'audio/wav'});
                        console.log(blob);

                        remoteSolve(blob);

                    };
                    // 將 mediaRecorder 对象扔到全局this中, 用于其他方法调用
                    mediaRecorder = _mediaRecorder;
                    // 录制开始
                    _mediaRecorder.start();
                }, () => {
                    $("#voiceIcon").css("background", "");
                    console.log("打开失败!");
                });
            } else {
                navigator.mediaDevices.getUserMedia = navigator.mediaDevices.getUserMedia || navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
                if (navigator.mediaDevices.getUserMedia) {
                    console.log("支持");
                    navigator.mediaDevices.getUserMedia({
                        audio: true
                    })
                        .then()
                        .catch((info) => {
                            $("#voiceIcon").css("background", "");
                            alert("无法获取麦克风权限！错误信息：" + info);
                        });
                } else {
                    alert("无法获取麦克风权限");
                }
            }
            permissionStatus.onchange = function () {
                console.log("Permission changed to " + this.state);
            }
        })
    },

    stop() {
        if (!mediaRecorder) {
            return;
        }
        // 返回录制的二进制数据, 调用这个方法后会生成一个新的Blob对象
        $("#voiceIcon").css("background", "");
        mediaRecorder.requestData();
        // 停止录制
        mediaRecorder.stop();
        mediaRecorder = null;
    }
}



const agentButton = document.getElementById("agentButton");
agentButton.addEventListener("click", function (e) {
    $('#agent-container').toggle();
    $(document).one("click", function(){
        $("#agent-container").hide();
    });
    e.stopPropagation();
});
