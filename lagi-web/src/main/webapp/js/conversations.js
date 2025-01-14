// import {isBlank} from './common'

let curConversations = -1;
let conversatonsList = [{ title: "你好", dateTime: 500 }, { title: "还行", dateTime: 100 }, { title: "写诗", dateTime: 1500 }]



function getCurConvId() {
    return curConversations;
}

function setCurConvId(convId) {
    curConversations = convId;
}


async function newConversation(conv, questionEnable = true, answerEnable = true) {
    let questionDiv = `
        <div class="w-full border-b border-black/10 dark:border-gray-900/50 text-gray-800 dark:text-gray-100 group dark:bg-gray-800">
            <div class="text-base gap-4 md:gap-6 m-auto md:max-w-2xl lg:max-w-2xl xl:max-w-3xl p-4 md:py-6 flex lg:px-0">
                <div class="w-[30px] flex flex-col relative items-end">
                    <div class="relative flex"><span style="box-sizing: border-box; display: inline-block; overflow: hidden; width: initial; height: initial; background: none; opacity: 1; border: 0px; margin: 0px; padding: 0px; position: relative; max-width: 100%;"><span style="box-sizing: border-box; display: block; width: initial; height: initial; background: none; opacity: 1; border: 0px; margin: 0px; padding: 0px; max-width: 100%;"><img aria-hidden="true" src="images/yhtx.png" alt="huamn" style="display: block; max-width: 100%; width: 30px; height: 30px; background: none; opacity: 1; border: 0px; margin: 0px; padding: 0px;"></span></span>
                    </div>
                </div>
                <div  class="relative flex w-[calc(100%-50px)] flex-col gap-1 md:gap-3 lg:w-[calc(100%-115px)]">
                    <div class="flex flex-grow flex-col gap-3">
                        <div class="min-h-[20px] flex flex-col items-start gap-4 whitespace-pre-wrap">${conv.user.question} </div>
                    </div>
                    <div class="flex justify-between"></div>
                </div>
            </div>
        </div>
        `;
    let part1 = `
<div class="robot-return w-full border-b border-black/10 dark:border-gray-900/50 text-gray-800 dark:text-gray-100 group bg-gray-50 dark:bg-[#444654]">
    <div class="text-area  text-base gap-4 md:gap-6 m-auto md:max-w-2xl lg:max-w-2xl xl:max-w-3xl p-4 md:py-6 flex lg:px-0">
        <div class="w-[34px] flex flex-col relative items-end">
            <div class="relative h-[34px] w-[34px] p-1 rounded-sm text-white flex items-center justify-center" style="">
                <img src ="images/Small_logo.png" style = "width:100%;height:100% !important;object-fit: cover;" alt = "logo"/>
            </div>
        </div>
        <div class="relative flex w-[calc(100%-50px)] flex-col gap-1 md:gap-3 lg:w-[calc(100%-115px)]">
            <div class="flex flex-grow flex-col gap-3">
                <div class="min-h-[20px] flex flex-col items-start gap-4">
                    <div class="markdown prose-r w-full break-words dark:prose-invert light result-streaming">
                        ${conv.robot.answer === '' ? '<p></p>' : conv.robot.answer} 
                    </div>
                </div>
            </div>
            <div class="conv-attached flex justify-between idx">
                <div class=" appendVoice text-gray-400 flex self-end lg:self-center justify-center mt-2 gap-3 md:gap-4 lg:gap-1 lg:absolute lg:top-0 lg:translate-x-full lg:right-0 lg:mt-0 lg:pl-2 visible">
                    <button class="p-1 rounded-md hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200 disabled:dark:hover:text-gray-400"><svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg" class="h-4 w-4">
                         <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3">
                            </path></svg></button>
                    <button class="p-1 rounded-md hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200 disabled:dark:hover:text-gray-400"><svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg" class="h-4 w-4">
                            <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm7-13h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17">
                            </path>
                        </svg></button>
`;
    let part2 = await generateSelect(conv.user.question);
    // let part2 = `<select class="custom-select" style="color:black; border-radius: 10px;" id="customSelect" onchange="handleSelect(this)">
    //                     <option value="default" data-priceperreq="0">智能推荐</option>
    //                     <option value="1" data-priceperreq="0.01">收费智能体1</option>
    //                     <option value="2" data-priceperreq="0.02">收费智能体2</option>
    //                     <option value="3" data-priceperreq="0.03">收费智能体3</option>
    //                     <option value="4" data-priceperreq="0.04">收费智能体4</option>
    //                     <option value="5" data-priceperreq="0.05">收费智能体5</option>
    //                 </select> `;

    let part3 = `
                    <audio class="myAudio1" controls="" preload="metadata" style="width:100px">
                    <source class="audioSource1" src="">
                    </audio>
                    <button class="playIcon1"  style="display:none">
                        <svg  class="playSvg icon"  style="width: 24px;height: 24px;"  t="1695352126205"  viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="5751" width="200" height="200"><path d="M128 138.666667c0-47.232 33.322667-66.666667 74.176-43.562667l663.146667 374.954667c40.96 23.168 40.853333 60.8 0 83.882666L202.176 928.896C161.216 952.064 128 932.565333 128 885.333333v-746.666666z" fill="#3D3D3D" p-id="5752"></path></svg>
                    </button>
                    <select class="emotionSelect audio-select" style="color:black" id="emotionSelectId">
                        <option value="neutral">默认</option>
                        <option  value="happy">快乐</option>
                        <option value="angry">生气</option>
                        <option value="sad">伤心</option>
                        <option value="fear">害怕</option>
                        <option value="hate">憎恨</option>
                        <option value="surprise">惊讶</option>
                    </select>
            </div>
            </div>
            </div>
        </div>
    </div>
</div>
`;
    let answerDiv = part1 + part2 + part3;
    if (!questionEnable) {
        questionDiv = '';
    }
    if (!answerEnable) {
        answerDiv = '';
    }
    let chatHtml = questionDiv + answerDiv;
    $('#item-content').append(chatHtml);
    replaceConversationAttached();
    $('#item-content').scrollTop($('#item-content').prop('scrollHeight'));
    return $($(' .markdown')[$('.markdown').length - 1]);
}

// 请求接口并生成HTML字符串
async function generateSelect(userQuestion) {
    const url = "/skill/relatedAgents";
    const requestData = {
        stream: false,
        temperature: 0.8,
        max_tokens: 1024,
        category: "default",
        messages: [
            {
                role: "user",
                content: userQuestion
            }
        ]
    };
    try {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(requestData)
        });
        const result = await response.json();
        if (result.code === 0 && result.data && Array.isArray(result.data) && result.data.length > 0) {
            const agents = result.data;
            let part2 = `<select class="custom-select" style="color:black; border-radius: 10px;" id="customSelect" onchange="handleSelect(this,'${userQuestion}')"><option value="default" data-priceperreq="0">智能推荐</option>`;
            agents.forEach(agent => {
            part2 += `<option value="${agent.id}" data-priceperreq="${agent.pricePerReq}">${agent.name}</option>`;
            });
            part2 += `</select>`;
            return part2;
        } else {
            return '';
        }
    } catch (error) {
        return '';
    }
}


function newRobotStartDialog(robotAnswer) {
    // $('#item-content').empty();
    return addRobotDialog(robotAnswer)
}

async function addUserDialog(userQuestion) {
    let conversation = { user: { question: userQuestion }, robot: { answer: '' } }
    let robot = await newConversation(conversation, true, false);
    return robot;
}

function addRobotDialog(robotAnswer) {
    let chatHtml = `
    <div class="robot-return w-full border-b border-black/10 dark:border-gray-900/50 text-gray-800 dark:text-gray-100 group bg-gray-50 dark:bg-[#444654]">
        <div class="text-area text-base gap-4 md:gap-6 m-auto md:max-w-2xl lg:max-w-2xl xl:max-w-3xl p-4 md:py-6 flex lg:px-0">
            <div class="w-[34px] flex flex-col relative items-end">
                <div class="relative h-[34px] w-[34px] p-1 rounded-sm text-white flex items-center justify-center" style="">
                    <img src ="images/Small_logo.png" style = "width:100%;height:100% !important;object-fit: cover;" alt = "logo"/>
                </div>
            </div>
            <div class="relative flex w-[calc(100%-50px)] flex-col gap-1 md:gap-3 lg:w-[calc(100%-115px)]">
                <div class="flex flex-grow flex-col gap-3">
                    <div class="min-h-[20px] flex flex-col items-start gap-4">
                        <div class="markdown prose-r w-full break-words dark:prose-invert light result-streaming">
                            ${robotAnswer == '' ? '<p></p>' : robotAnswer} 
                        </div>
                    </div>
                </div>
                </div>
                </div>
            </div>
        </div>
    </div>
    </div>
    `;
    $('#item-content').append(chatHtml);
    $('#item-content').scrollTop($('#item-content').prop('scrollHeight'));
    replaceConversationAttached();
    return $($(' .markdown')[$('.markdown').length - 1]);
}


function showConversationsNav(convsList) {
    let WConvsList = convertByDate(convsList);
    let html = '';
    for (let i = 0; i < WConvsList.length; i++) {
        const wconvs = WConvsList[i];
        if (!wconvs) {
            continue;
        }
        html += `
        <div class="relative" data-projection-id="5" style="height: auto; opacity: 1;">
            <div class="sticky top-0 z-[16]" data-projection-id="6" style="opacity: 1;">
                <h3 class="h-9 pb-2 pt-3 px-3 text-xs text-gray-500 font-medium text-ellipsis overflow-hidden break-all bg-default-50 dark:bg-default-900">${wconvs.date}</h3>
            </div>
            <ol>
                ${showConv(wconvs.convs)}
            </ol>
        </div>
        `;
    }
    $('#conversationsNav').empty();
    $('#conversationsNav').append(html);
}


function loadConversationNav() {
    conversatonsList = loadConvs();
    showConversationsNav(conversatonsList);
}


function loadConversation(convId) {
    conversatonsList = loadConvs();
    const convs = conversatonsList[convId].convs;
    // $('#item-content').empty();
    if (convs.length == 0) {
        showHelloContent();
    } else {
        hideHelloContent();
    }
    for (let index = 0; index < convs.length; index++) {
        const conv = convs[index];
        newConversation(conv);
    }
}


function addConv(conv) {
    // let convId = getCurConvId();
    // if(convId == -1) {
    //     newConversationWindow(null, null, [conv]);
    //     convId = getCurConvId();
    // }
    // conversatonsList = loadConvs();
    // saveOrUpdateConv(convId, null, conv);
}


// 对话列表
// conversatonsList[
//     convs{ title, dateTime, convs[]}
// ]


function saveOrUpdateConv(convId, title, conv) {
    let convs = loadConvs();
    if (!convs) {
        convs = [];
    }
    // 大于插入否则更新
    if (convId == undefined || convId == null || convId >= convs.length) {
        convs.push({ title, dateTime: new Date().getTime(), convs: [] });
        convId = convs.length - 1;
    }
    if (!isBlank(title)) {
        convs[convId]["title"] = title;
    }
    if (conv != null || conv != undefined) {
        convs[convId].convs.push(conv);
    }
    localStorage.setItem("conversations", JSON.stringify(convs));
    return convId;
}


function loadConvs() {
    let convs = localStorage.getItem("conversations");
    return convs ? JSON.parse(convs) : [];
}

function clearConvs() {
    localStorage.removeItem("conversations");
    loadConversationNav();
}



function convertByDate(convsList) {
    let res = [];
    // 当天 
    let days = new Date().getDate();
    // 获取当月
    let month = new Date().getMonth() + 1;
    // 获取当年
    let year = new Date().getFullYear();
    for (let i = 0; i < convsList.length; i++) {
        const convs = convsList[i];
        let tD = new Date(convs.dateTime);
        let td = tD.getDate();
        let tm = tD.getMonth() + 1;
        let ty = tD.getFullYear();
        if (td == days) {
            if (res[0] == undefined) {
                res[0] = { date: '今天', convs: [convs] };
            } else {
                res[0].convs.push(convs);
            }
        } else if (tm == month) {
            if (res[1] == undefined) {
                res[1] = { date: '本月', convs: [convs] };
            } else {
                res[1].convs.push(convs);
            }
        }
        else if (ty == year) {
            if (res[2] == undefined) {
                res[2] = { date: '今年', convs: [convs] };
            } else {
                res[2].convs.push(convs);
            }
        } else {
            if (res[3] == undefined) {
                res[3] = { date: '更早', convs: [convs] };
            } else {
                res[3].convs.push(convs);
            }
        }
    }
    return res;
}

function showConv(convsList) {
    // console.log(convsList);
    let html = '';
    for (let index = 0; index < convsList.length; index++) {
        const convs = convsList[index];
        html += `
        <li class="relative z-[15]" data-projection-id="7" style="opacity: 1; height: auto;">
            <a  onclick=changeConversation(${index}) class="flex py-3 px-3 items-center gap-3 relative rounded-md hover:bg-default-100 dark:hover:bg-[#2A2B32] cursor-pointer break-all bg-default-50 hover:pr-4 dark:bg-default-900 group">
                <svg stroke="currentColor" fill="none" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round" class="icon-sm" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z">
                    </path>
                </svg>
                <div class="flex-1 text-ellipsis max-h-5 overflow-hidden break-all relative">
                    ${convs.title}<div class="absolute inset-y-0 right-0 w-8 z-10 bg-gradient-to-l dark:from-default-900 from-gray-50 group-hover:from-gray-100 dark:group-hover:from-[#2A2B32]">
                    </div>
                </div>
            </a>
        </li>
        `;
    }
    // console.log(html);
    return html;
}


function changeConversation(convId) {
    setCurConvId(convId);
    loadConversation(convId);
}

function newConversationWindow(convsId, title, convs) {
    if (convsId == null && isBlank(title) && convs != null && convs.length != 0) {
        convsId = saveOrUpdateConv(null, convs[0].user.question, null);
    } else {
        convsId = saveOrUpdateConv(null, '新的对话', null);
    }
    setCurConvId(convsId);
    loadConversationNav();
}




class Chart {

    static charts = [];
    static is_listening = false;

    constructor(doc, option) {
        this.doc = doc;
        this.option = option;
        this.chart = echarts.init(doc);
        this.chart.setOption(option);
        Chart.charts.push(this.chart);
    }

    static listen_all_resize() {
        if (Chart.is_listening) {
            return;
        }
        Chart.is_listening = true;
        window.addEventListener('resize', function () {
            for (let index = 0; index < Chart.charts.length; index++) {
                const chart = Chart.charts[index];
                if (chart) {
                    chart.resize();
                }
            }
        });
    }

    fresh() {
        this.chart.clear();
        this.chart.setOption(this.option);
    }

    setChart(option) {
        this.option = option;
        fresh();
    }

    getChart() {
        return this.chart;
    }
}


// 不同数据格式重写
function transformToCoordinateData(data, x_field, y_field) {
    if (!(data instanceof Array)) {
        // 数据不是数组
        return { "x_data": [], "y_data": [] };
    }
    let t_data = data;
    let x_data = [], y_data = [];
    for (let index = 0; index < t_data.length; index++) {
        const t_tr = t_data[index];
        x_data.push(t_tr[x_field]);
        y_data.push(t_tr[y_field]);
    }
    return { "x_data": x_data, "y_data": y_data };
}

function transformToPieData(data, name_field, value_filed) {
    if (!(data instanceof Array)) {
        // 数据不是数组
        return [];
    }
    let t_data = [];
    for (let index = 0; index < data.length; index++) {
        const row = data[index];
        t_data.push({ "name": row[name_field], "value": row[value_filed] });
    }
    return t_data;
}


function genCoordinateOption(data, y_name, type) {
    let { x_data, y_data } = data;
    let option = {
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' }
        },
        legend: {},
        xAxis: [
            {
                type: 'category',
                axisTick: {
                    alignWithLabel: true
                },
                data: x_data,
            }
        ],
        yAxis: [
            {
                type: 'value',
                name: y_name,
                min: (value) => { // 百位起最小值向下取整
                    return Math.floor(value.min / 100) * 100;
                },
                max: (value) => {  // 百位起最大值向上取整
                    return Math.ceil(value.max / 100) * 100;
                },
                scale: true, //自适应
                position: 'left',
            }
        ],
        series: [
            {
                name: y_name,
                type: type,
                smooth: true,
                yAxisIndex: 0,
                data: y_data,
            }
        ]
    };
    return option;
}


function genPieOption(t_data, type, name) {
    let option = {
        series: [
            {
                type: 'pie',
                data: t_data,

            }
        ]
    };
    if (type == 'circle') {
        option.title = {
            text: name,
            left: 'center',
            top: 'center'
        };
        option.series[0].radius = ['40%', '70%'];
    }
    return option;
}


function addChart(markdown_doc, data, name_field = 'statis_month', value_filed = 'zcj', t_name = 'zcj', type = 'line') {
    let option;
    if (type == 'line' || type == 'bar') {
        let t_data = transformToCoordinateData(data, name_field, value_filed);
        option = genCoordinateOption(t_data, t_name, type);
    } else if (type == 'pie' || type == 'circle') {
        let t_data = transformToPieData(data, name_field, value_filed);
        option = genPieOption(t_data, type, t_name);
    } else {
        let t_data = transformToCoordinateData(data, name_field, value_filed);
        option = genCoordinateOption(t_data, t_name, 'line');
    }
    // 元素上画图
    let markdown_el = $(markdown_doc);
    markdown_el.html(markdown_el.html() + '<br/><div class = "chart" style=" width: 100%; height: 480px;" >');
    let chart_doc = markdown_el.children('.chart')[0];
    new Chart(chart_doc, option);
    $('#item-content').scrollTop($('#item-content').prop('scrollHeight'));
}



Chart.listen_all_resize();