// import {isBlank} from './common'

let curConversations = -1;
let conversatonsList = [{ title: "你好", dateTime: 500 }, { title: "还行", dateTime: 100 }, { title: "写诗", dateTime: 1500 }]


const MUTE_ICON = '<svg t="1752981792710" class="icon" viewBox="0 0 1150 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="9827" width="20" height="20"><path d="M604.032508 0.11553A40.5789 40.5789 0 0 0 571.298588 12.902498L282.259303 267.967868H79.068805A82.094797 82.094797 0 0 0 0 349.252667v348.925138a82.094797 82.094797 0 0 0 79.068805 81.284799h206.004491l284.987296 231.239429a44.243891 44.243891 0 0 0 32.735919 12.786968 54.814865 54.814865 0 0 0 54.559865-53.238868V53.822397A52.76987 52.76987 0 0 0 604.032508 0.11553z m205.877491 246.62539a39.256903 39.256903 0 0 0-54.559865 12.786969 41.686897 41.686897 0 0 0 12.275969 56.051861 223.992447 223.992447 0 0 1 96.799761 187.761536 218.877459 218.877459 0 0 1-91.343774 180.769554 41.047899 41.047899 0 0 0-9.547976 56.051861 38.361905 38.361905 0 0 0 32.735919 15.429962 77.789808 77.789808 0 0 0 24.550939-5.625986 309.199236 309.199236 0 0 0-10.910973-502.969757z" fill="#2c2c2c" p-id="9828"></path><path d="M977.594585 99.601284a37.935906 37.935906 0 0 0-54.516866 2.812993 40.5359 40.5359 0 0 0 2.727994 56.050861 465.886849 465.886849 0 0 1 148.631632 340.528159 457.830869 457.830869 0 0 1-139.083656 330.724183 43.647892 43.647892 0 0 0-2.727993 56.050862 35.804912 35.804912 0 0 0 54.559865 0 532.807684 532.807684 0 0 0 163.635596-389.589038 522.23671 522.23671 0 0 0-173.226572-396.57902z" fill="#2c2c2c" p-id="9829"></path><path d="M1090.140307 59.506383q30.139926 30.139926 0 60.279851L216.078466 993.852074q-30.139926 30.139926-60.279851 0t0-60.279851l874.062841-874.06484q30.139926-30.139926 60.279851 0z" fill="#2c2c2c" p-id="9830"></path></svg>';
const AUDIO_PLAY_ICON = '<svg t="1753002308803" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="4529" width="20" height="20"><path d="M448 282.4v459.2L301.6 594.4 282.4 576H192V448h90.4l18.4-18.4L448 282.4M512 128L256 384H128v256h128l256 256V128z m64 5.6v64.8c145.6 29.6 256 159.2 256 313.6s-110.4 284-256 313.6v64.8c181.6-30.4 320-188 320-378.4S757.6 164 576 133.6z m0 188.8v65.6c55.2 14.4 96 64 96 124s-40.8 109.6-96 124v65.6C666.4 686.4 736 607.2 736 512s-69.6-174.4-160-189.6z" p-id="4530"></path></svg>';
const AUDIO_PLAYING_ICON ='<svg t="1752981609407" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="8621" width="20" height="20"><path d="M64 384h64v256H64zM480 192h64v640h-64zM896 384h64v256h-64zM756.8 256h64v512h-64zM619.2 320h64v384h-64zM203.2 256h64v512h-64zM340.8 320h64v384h-64z" fill="#2c2c2c" p-id="8622"></path></svg>';

function getCurConvId() {
    return curConversations;
}

function setCurConvId(convId) {
    curConversations = convId;
}




async function newConversation(conv, questionEnable = true, answerEnable = true) {
    const converation_index =  CONVERSATION_CONTEXT.length;
    let questionDiv = `
        <div class="w-full" >
            <div class="text-base gap-4 md:gap-6 m-auto md:max-w-2xl lg:max-w-2xl xl:max-w-3xl p-4 md:py-6 flex lg:px-0" >
                <div  class=" flex ml-auto" style="background-color: #f5f5f5; border-radius: 10px; padding: 12px; text-align: right; max-width: 65%;" >
                    <div class="chat-div min-h-[20px]" style="text-align: left;">${conv.user.question}</div>
                </div>
            </div>
        </div>
        `;
    let robot = `
<div class="robot-return w-full  group " data-index="${converation_index + 1}">
<div class="text-area  text-base gap-4 md:gap-6 m-auto md:max-w-2xl lg:max-w-2xl xl:max-w-3xl p-4 md:py-6 flex lg:px-0">
    <div class="intent-prompt">
        <span class="intent-prompt-text">正在为您匹配最佳的智能体或大模型</span>
    </div>
    <div class="relative flex w-[calc(100%-50px)] flex-col gap-1 md:gap-3 lg:w-[calc(100%-115px)]">
        <div class="">
            <div class="chat-div">
                <div class="markdown  w-full break-words  light result-streaming">
                    ${conv.robot.answer === '' ? '<p></p>' : conv.robot.answer} 
                </div>
                <div class="better-result w-full break-words light result-streaming">
                </div>
            </div>
        </div>
        <div class="conv-attached idx " style=" width: 50%; height: 24px;">
            
            <div class="appendVoice" style="">
                <audio class="myAudio1" controls="" preload="metadata"  style="display: none;"></audio>
                <button onclick="playAudio(this)" disabled class="audioplay p-1 rounded-md hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200 disabled:dark:hover:text-gray-400">
                ${MUTE_ICON}
                </button>
                <button onclick="showSounds(this, event)" class="sound  relativate p-1 rounded-md hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200 disabled:dark:hover:text-gray-400">
                <svg t="1752982108403" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="12130" width="20" height="20"><path d="M158.072096 279.909205a38.39808 38.39808 0 0 1 27.710615 71.676416 171.319434 171.319434 0 0 0-97.275136 222.260887c5.183741 12.79936 3.135843 27.134643-5.247738 38.39808a38.910054 38.910054 0 0 1-36.094195 14.335283 38.270086 38.270086 0 0 1-29.822509-24.574771A248.307585 248.307585 0 0 1 158.072096 279.909205z m93.563322 103.418829a38.526074 38.526074 0 0 1 49.661517 21.502924 37.886106 37.886106 0 0 1-0.511974 29.182541 40.445978 40.445978 0 0 1-20.926954 20.478976 91.00345 91.00345 0 0 0-50.685466 49.149543 89.59552 89.59552 0 0 0-1.023948 70.652467 37.630118 37.630118 0 0 1-0.575972 29.182541 40.381981 40.381981 0 0 1-20.862956 20.478976h-0.639968a38.39808 38.39808 0 0 1-49.085546-22.014899 168.631568 168.631568 0 0 1 2.047897-129.529524 164.919754 164.919754 0 0 1 92.60337-89.083545zM833.686316 5.426929a45.245738 45.245738 0 0 1 35.582221-3.071847 46.077696 46.077696 0 0 1 7.807609 84.987751l-164.727763 87.035648v446.057697a199.670016 199.670016 0 0 1-199.222039 199.158042 199.350032 199.350032 0 0 1-199.286036-199.158042A199.670016 199.670016 0 0 1 513.126344 421.150142c37.630118 0 74.748263 10.751462 106.170691 30.718465V146.79586a46.717664 46.717664 0 0 1 24.574771-40.957952l189.81451-100.474976z m-13.567322 663.646817a38.59007 38.59007 0 0 1 48.637568 23.038848 169.143543 169.143543 0 0 1-104.634768 215.029249 38.014099 38.014099 0 0 1-48.061597-23.9988 38.39808 38.39808 0 0 1 23.038848-48.637568 92.79536 92.79536 0 0 0 56.957152-117.818109 38.39808 38.39808 0 0 1 24.062797-47.61362z m141.176941 13.823309a38.654067 38.654067 0 0 1 29.310535 1.535923 36.926154 36.926154 0 0 1 19.327033 22.0149 245.875706 245.875706 0 0 1-10.943453 189.430528 246.579671 246.579671 0 0 1-141.752912 126.0097h-0.511974a37.886106 37.886106 0 0 1-48.125594-24.062797 38.20609 38.20609 0 0 1 23.486826-48.637568 171.767412 171.767412 0 0 0 98.363082-87.035649 172.79136 172.79136 0 0 0 7.871606-131.129443 40.189991 40.189991 0 0 1 1.535923-29.182541 40.573971 40.573971 0 0 1 21.438928-18.943053z" fill="#2c2c2c" p-id="12131"></path></svg>
                ${SOUNDS_HTML}
                </button>
                <button style="display: none;" data-index="${converation_index}"  onclick="showRecommend(this, event)"  class="recommend p-1 rounded-md hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200 disabled:dark:hover:text-gray-400">
                <svg t="1752981885416" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="11087" width="20" height="20"><path d="M505.284 601.305c-103.659 0-187.992-84.333-187.992-187.992 0-103.659 84.333-187.992 187.992-187.992s187.992 84.333 187.992 187.992c0 103.659-84.333 187.992-187.992 187.992z m0-311.984c-68.37 0-123.992 55.623-123.992 123.992s55.623 123.992 123.992 123.992 123.992-55.623 123.992-123.992c0-68.37-55.623-123.992-123.992-123.992zM317.282 930.421l-82.075-142.858-143.301-21.24 131.835-197.712 53.248 35.506-75.816 113.702 74.084 10.98 44.231 76.989 60.713-97.515 54.33 33.827z" fill="" p-id="11088"></path><path d="M505.284 752.984c-45.841 0-90.328-8.985-132.225-26.706-40.452-17.109-76.774-41.597-107.959-72.781s-55.671-67.507-72.781-107.959c-17.721-41.897-26.706-86.384-26.706-132.225s8.985-90.328 26.706-132.226c17.11-40.452 41.597-76.774 72.781-107.958s67.507-55.672 107.958-72.781c41.897-17.721 86.384-26.707 132.226-26.707s90.328 8.985 132.226 26.707c40.452 17.109 76.774 41.597 107.959 72.781s55.672 67.507 72.781 107.958c17.721 41.897 26.706 86.384 26.706 132.226s-8.985 90.328-26.706 132.225c-17.109 40.452-41.597 76.774-72.781 107.959s-67.508 55.672-107.959 72.781c-41.898 17.721-86.384 26.706-132.226 26.706z m0-615.343c-152.006 0-275.671 123.666-275.671 275.672 0 152.005 123.666 275.671 275.671 275.671s275.672-123.666 275.672-275.671c0-152.006-123.666-275.672-275.672-275.672z" fill="" p-id="11089"></path><path d="M705.624 928.421L588.375 740.1l54.33-33.827 60.712 97.515 44.231-76.989 74.085-10.98-75.816-113.702 53.248-35.506 131.834 197.712-143.301 21.24z" fill="" p-id="11090"></path></svg>
                ${RECOMMEND_HTML}
                </button>
                <button class="praise p-1 rounded-md hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200 disabled:dark:hover:text-gray-400">
                <svg t="1752982601404" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="13250" width="20" height="20"><path d="M170.666667 938.666667a85.333333 85.333333 0 0 1-85.333334-85.333334v-341.333333a85.333333 85.333333 0 0 1 85.333334-85.333333h85.333333l128-341.333334h42.666667a170.666667 170.666667 0 0 1 170.666666 170.666667v170.666667h232.021334a85.333333 85.333333 0 0 1 82.773333 106.026666l-69.12 276.693334A170.666667 170.666667 0 0 1 677.376 938.666667H170.666667z m85.333333-426.666667H170.666667v341.333333h85.333333v-341.333333z m187.136-341.333333L341.333333 442.154667V853.333333h336.085334a85.333333 85.333333 0 0 0 82.773333-64.64L829.354667 512H512V239.530667c0-35.925333-27.52-65.408-62.592-68.565334L443.136 170.666667z" fill="#000000" p-id="13251"></path></svg>
                </button>
                <button class="criticism p-1 rounded-md hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200 disabled:dark:hover:text-gray-400">
                <svg t="1752982690269" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="14271" width="16" height="16"><path d="M300 608.864V88h477.916c25.815 0 41.979 5.525 51.808 14.617 6.238 6.125 9.602 13.574 10.735 20.38l0.438 2.633 92.314 402.165 0.176 0.712c5.816 23.53 1.843 43.53-10.447 59.143-9.517 11.702-32.017 21.182-59.61 21.182H546.349l72.213 130.586c7.856 14.206 15.912 31.605 23.947 53.053 10.618 28.344 20.148 61.09 28.115 98.645 0.036 0.32-0.053 0.518-0.461 1.612-1.324 3.544-4.218 8.523-9.47 15.814C644.654 926.839 623.467 936 594.813 936c-18.135 0-28.537-4.288-37.618-12.874-8.405-7.946-14.718-17.855-25.561-39.254l-5.634-11.118-5.344-5.732c-0.433-0.72-0.918-1.551-1.444-2.474-1.787-3.135-7.986-14.904-10.1-18.652l0.01-0.006c-25.204-43.028-36.934-62.463-52.366-85.841-21.447-32.49-42.12-59.384-64.482-82.682-28.251-29.434-58.872-52.508-92.273-68.503z m-88-24.668a289.824 289.824 0 0 0-29.43-1.476H97.667c-6.617 0-8.667-2.052-8.667-8.768V96.256C89 90.049 91.054 88 97.667 88H212v496.196z m483.57 112.636h167.76c53.193 0 101.27-20.48 128.379-54.272 29.665-37.376 39.382-85.504 27.107-135.168l-91.552-398.848c-2.557-15.36-10.74-44.544-36.826-69.632C863.331 13.312 825.482 0 777.916 0H97.667C42.429 0 1 41.472 1 96.256v477.696c0 55.296 41.429 96.768 96.667 96.768h84.903c121.729 0 184.64 107.008 250.618 219.648 1.535 2.56 12.787 25.6 19.947 33.28C471.037 958.976 504.282 1024 594.811 1024c55.239 0 101.782-20.992 135.027-60.928 17.39-23.552 34.268-52.224 27.108-89.088-7.304-34.634-15.547-64.206-23.833-89.152l-37.543-88z" fill="#000000" p-id="14272"></path></svg>
                </button>
            </div>
        </div>
    </div>
    </div>
</div>
`;  
    // let answerDiv = part1 + part2 + part3;
    let answerDiv = robot ;
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
    let markdown =  $($(' .markdown')[$('.markdown').length - 1]);
    const soundSelectors = document.querySelectorAll('.sound');
    const recommendSelectors = document.querySelectorAll('.recommend');
    const soundSelector = soundSelectors[soundSelectors.length-1];
    const recommendSelector = recommendSelectors[recommendSelectors.length-1];
    soundSelector.addEventListener('mouseleave', function() {
        $('.sounds-selector').hide();
    });
    recommendSelector.addEventListener('mouseleave', function() {
        $('.recommend-selector').hide();
    });
    const audioSelectors = document.querySelectorAll('.myAudio1');
    const audioSelector = audioSelectors[audioSelectors.length-1];
    audioSelector.addEventListener('ended', function() {
        console.log("audioSelector ended", this);
        $(this).closest('.appendVoice').find('.audioplay').html(AUDIO_PLAY_ICON);
    });
    return markdown;
}


const SOUNDS_HTML = `
<div  class="sounds-selector absolute z-50 selector" style="display:none; bottom:24px !important; left:10px; width: 80px;" style="">
<ul  class="">
    <li class=" not-available " data-value="neutral" onclick="selectSound(this, event);">默认</li>
    <li class=" not-available " data-value="happy" onclick="selectSound(this, event);">快乐</li>
    <li class=" not-available " data-value="angry" onclick="selectSound(this, event);">生气</li>
    <li class=" not-available " data-value="sad" onclick="selectSound(this, event);">伤心</li>
    <li class=" not-available " data-value="fear" onclick="selectSound(this, event);">害怕</li>
    <li class=" not-available " data-value="hate" onclick="selectSound(this, event);">憎恨</li>
    <li class=" not-available " data-value="surprise" onclick="selectSound(this, event);">惊讶</li>
</ul>
</div>
`;

const RECOMMEND_HTML = `
<div  class="recommend-selector absolute z-50 selector" style="display:none; bottom:24px !important; left:40px; width: 80px;" style="">
<ul  class="">
    
</ul>
</div>
`;


function showSounds(that, event) {
    event.stopPropagation();
    const curSelector = $(that).children('.selector');
    curSelector.show();
}

function showRecommend(that, event) {
    event.stopPropagation();
    const curSelector = $(that).children('.selector');
    curSelector.show();
}

function selectSound(that, event) {
    event.stopPropagation();
    $(that).closest('.selector').hide();
    textToVoice(that);
}

function selectRecommend(that, event) {
    event.stopPropagation();
    $(that).closest('.selector').hide();
    const conversation_index = $(that).closest('.recommend').data("index");
    const user =  selectRecommend[conversation_index];
    if(user) {
        handleSelect(that, user.content);
    }
}


// document.addEventListener('DOMContentLoaded', function() {
//     document.addEventListener('click', function(e) {
//       const target = e.target;
//       if (!target.classList.contains('selector')) {
//         document.querySelectorAll('.selector').forEach(el => {
//             el.style.display = 'none';
//         });
//       }
//     });
// });




// 请求接口并生成HTML字符串
async function generateSelect(request, markdown) {
    let customSelect =  $(markdown.closest('.robot-return').find('.recommend-selector ul')[0]);
    const url = "/skill/relatedAgents";
    fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(request)
    }).then(response => {
        if (!response.ok) {
            throw new Error(`上传失败，状态码: ${response.status}`);
        }
        return response.json();
    }).then(result => {
        if (result.code === 0 && result.data && Array.isArray(result.data) && result.data.length > 0) {
            const agents = result.data;
            agents.forEach(agent => {
                // let html = `<option value="${agent.id}" data-priceperreq="${agent.pricePerReq}" title="使用这些智能体来获取更准确的回答！">${agent.name}</option>`;
                let html = `<li class=" not-available " value="${agent.id}" data-priceperreq="${agent.pricePerReq}" onclick="selectRecommend(this, event);">${agent.name}</li>`;
                customSelect.append(html);
            });
        }
    }).catch(error => {
        console.error("relatedAgents 请求失败:", error);
    }).finally(() => {
    })
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
    <div class="robot-return w-full  group ">
<div class="text-area  text-base gap-4 md:gap-6 m-auto md:max-w-2xl lg:max-w-2xl xl:max-w-3xl p-4 md:py-6 flex lg:px-0">
    
    <div class="relative flex w-[calc(100%-50px)] flex-col gap-1 md:gap-3 lg:w-[calc(100%-115px)]">
        <div class="">
            <div class="chat-div ">
                <div class="markdown  w-full break-words  light result-streaming">
                    ${robotAnswer === '' ? '<p></p>' : robotAnswer} 
                </div>
                <div class="better-result w-full break-words light result-streaming">
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
        // hideHelloContent();
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


function playAudio(that) {
    const audio = $(that).parent().find('.myAudio1')[0];
    if(audio.paused) {
        audio.play();
        $(that).html(AUDIO_PLAYING_ICON)
    } else {
        audio.pause();
        $(that).html(AUDIO_PLAY_ICON)
    }
}

function hideIntentPrompt() {
    // Get the last conversation element and find its intent-prompt
    const conversationElements = document.querySelectorAll('.robot-return');
    if (conversationElements.length > 0) {
        const lastConversation = conversationElements[conversationElements.length - 1];
        const intentPromptElement = lastConversation.querySelector('.intent-prompt');
        if (intentPromptElement) {
            intentPromptElement.style.display = 'none';
        }
    }
}

function showIntentPrompt() {
    // Get the last conversation element and find its intent-prompt
    const conversationElements = document.querySelectorAll('.robot-return');
    if (conversationElements.length > 0) {
        const lastConversation = conversationElements[conversationElements.length - 1];
        const intentPromptElement = lastConversation.querySelector('.intent-prompt');
        if (intentPromptElement) {
            intentPromptElement.style.display = 'block';
        }
    }
}
