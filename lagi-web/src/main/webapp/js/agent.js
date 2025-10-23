// 页面加载时获取并渲染智能体列表
document.addEventListener('DOMContentLoaded', () => {
    // loadAgentMenu(currentPage); // 初始加载第一页的智能体
    const agentToolsElement = document.getElementById('agent-tools');
    // agentToolsElement.addEventListener('scroll', handleScroll); // 监听滚动事件
});


// 获取用户ID
let globalUserId = getCookie('userId');

let currentAgentId = null; // 设定一个全局变量来标记当前的 agentId

let selectedAgentId = null;  // 全局变量，用来存储选择的 agentId

// 创建 driver 映射关系
const driverMap = {
    "ai.agent.chat.qianfan.XiaoxinAgent": "千帆",
    "ai.agent.customer.GeneralAgent": "联动北方",
    "ai.agent.chat.coze.CozeAgent": "扣子",
    "ai.agent.chat.tencent.YuanQiAgent": "元器",
    "ai.agent.chat.zhipu.ZhipuAgent": "智谱"
};


// 显示或隐藏收费输入框
function handleFeeRequirementChange() {
    const isFeeRequiredYes = document.getElementById("isFeeRequiredYes").checked;
    const pricePerReqContainer = document.getElementById("pricePerReqContainer");

    if (isFeeRequiredYes) {
        pricePerReqContainer.style.display = "block";  // 显示收费金额输入框
    } else {
        pricePerReqContainer.style.display = "none";   // 隐藏收费金额输入框
    }
}

// 初始化事件监听
document.getElementById("isFeeRequiredYes").addEventListener("change", handleFeeRequirementChange);
document.getElementById("isFeeRequiredNo").addEventListener("change", handleFeeRequirementChange);
handleFeeRequirementChange();

// 清空弹框中的表单内容
function resetAgentForm() {
    var form = document.getElementById('agent-form');
    form.reset();
    document.getElementById('pricePerReqContainer').style.display = 'none';
    document.getElementById('isFeeRequiredNo').checked = true;
    document.getElementById('pricePerReq').value = '';
}

// 打开弹框的方法
function openAgentModal(e) {
    if (!getCookie('userId')) {
        openModal(e);
        return;
    }
    var modal = document.getElementById('agent-form-modal');
    modal.style.display = 'block';
    document.getElementById("agent-list-container").classList.add("blurred");
    document.body.classList.add("no-select");
}

// 关闭弹框并清空表单内容
function closeAgentModal() {
    var modal = document.getElementById('agent-form-modal');
    modal.style.display = 'none';
    document.getElementById("agent-list-container").classList.remove("blurred");
    document.body.classList.remove("no-select");
    resetAgentForm();
}

// 打开智能体列表
function openAgentList() {
    document.getElementById("agent-list-container").style.display = 'block';
}

// 关闭智能体列表
function closeAgentList() {
    document.getElementById("agent-list-container").style.display = 'none';
}

// 打开智能体列表
function openPaidAgentList() {
    document.getElementById("paid-agent-list-container").style.display = 'block';
}

// 关闭智能体列表
function closePaidAgentList() {
    document.getElementById("paid-agent-list-container").style.display = 'none';
}

// 保存智能体信息
function saveAgent() {
    let agentConfig = {
        id: currentAgentId,
        name: document.getElementById("agent-name").value,
        driver: document.getElementById("platform").value,
        token: document.getElementById("token").value,
        appId: document.getElementById("app-id").value,
        isFeeRequired: document.querySelector('input[name="isFeeRequired"]:checked').value === 'true',
        pricePerReq: document.getElementById("pricePerReq").value.trim() === "" ? 0 : parseFloat(document.getElementById("pricePerReq").value.trim()),
        lagiUserId: globalUserId,
        publishStatus: true
    };

    let url = currentAgentId ? '/updateLagiAgent' : '/addLagiAgent';
    fetch('/agent' + url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(agentConfig)
    }).then(response => response.json())
        .then(data => {
            closeAgentModal();
            loadAgentList(1);  // 刷新列表

            // 在保存成功后弹出提示框
            alert('发布成功！');
        })
        .catch(error => {
            // 处理可能的错误情况
            alert('发布失败，请重试！');
        });
}

// 加载智能体列表
function loadAgentList(pageNumber) {
    fetch(`/agent/getLagiAgentList?pageNumber=${pageNumber}&pageSize=10&lagiUserId=${globalUserId}`)
        .then(response => response.json())
        .then(data => {
            let tbody = document.querySelector("#agent-list tbody");
            tbody.innerHTML = ''; // 清空表格内容

            data.data.forEach(agent => {
                // 获取 driver 的翻译
                const driverName = driverMap[agent.driver] || agent.driver; // 如果没有找到映射，则显示原值

                let row = document.createElement('tr');

                // 根据 publishStatus 决定按钮
                let publishButton = agent.publishStatus
                    ? `<button class="operation-button unpublish" onclick="togglePublishStatus(${agent.id}, false)"><svg t="1740998296700" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="7299" width="200" height="200"><path d="M186.636724 430.268534c4.909823 0 9.82374-1.855254 13.596669-5.573949l306.881342-302.44838 0 648.318712c0 10.698666 8.67252 19.371186 19.371186 19.371186s19.371186-8.67252 19.371186-19.371186l0-647.568628 302.538431 298.121841c3.773953 3.718695 8.683776 5.572926 13.594623 5.572926 5.004991 0 10.007935-1.927909 13.798261-5.774517 7.509021-7.620561 7.41897-19.884886-0.201591-27.393907L540.528754 63.354933c-3.354398-3.306302-7.608282-5.134951-11.961426-5.501294-0.684592-0.073678-1.378393-0.115634-2.081405-0.115634-0.328481 0-0.648776 0.032746-0.974188 0.049119-4.989641-0.061398-10.001795 1.788739-13.837146 5.568832L173.039032 397.101133c-7.619538 7.509021-7.708566 19.774369-0.199545 27.393907C176.629813 428.341648 181.631734 430.268534 186.636724 430.268534z" fill="#363636" p-id="7300"></path><path d="M876.833219 705.913455c-10.698666 0-19.371186 8.67252-19.371186 19.371186l0 165.41847c0 13.05534-10.620895 23.676236-23.676236 23.676236l-618.946754 0c-13.05534 0-23.676236-10.620895-23.676236-23.676236L191.162808 725.284641c0-10.698666-8.67252-19.371186-19.371186-19.371186s-19.371186 8.67252-19.371186 19.371186l0 165.41847c0 34.417881 28.000728 62.417585 62.417585 62.417585l618.946754 0c34.417881 0 62.417585-27.999704 62.417585-62.417585L896.202359 725.284641C896.204405 714.585975 887.531885 705.913455 876.833219 705.913455z" fill="#363636" p-id="7301"></path></svg>取消发布</button>`
                    : `<button class="operation-button publish" onclick="togglePublishStatus(${agent.id}, true)"><svg t="1740998296700" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="7299" width="200" height="200"><path d="M186.636724 430.268534c4.909823 0 9.82374-1.855254 13.596669-5.573949l306.881342-302.44838 0 648.318712c0 10.698666 8.67252 19.371186 19.371186 19.371186s19.371186-8.67252 19.371186-19.371186l0-647.568628 302.538431 298.121841c3.773953 3.718695 8.683776 5.572926 13.594623 5.572926 5.004991 0 10.007935-1.927909 13.798261-5.774517 7.509021-7.620561 7.41897-19.884886-0.201591-27.393907L540.528754 63.354933c-3.354398-3.306302-7.608282-5.134951-11.961426-5.501294-0.684592-0.073678-1.378393-0.115634-2.081405-0.115634-0.328481 0-0.648776 0.032746-0.974188 0.049119-4.989641-0.061398-10.001795 1.788739-13.837146 5.568832L173.039032 397.101133c-7.619538 7.509021-7.708566 19.774369-0.199545 27.393907C176.629813 428.341648 181.631734 430.268534 186.636724 430.268534z" fill="#3bc05c" p-id="7300"></path><path d="M876.833219 705.913455c-10.698666 0-19.371186 8.67252-19.371186 19.371186l0 165.41847c0 13.05534-10.620895 23.676236-23.676236 23.676236l-618.946754 0c-13.05534 0-23.676236-10.620895-23.676236-23.676236L191.162808 725.284641c0-10.698666-8.67252-19.371186-19.371186-19.371186s-19.371186 8.67252-19.371186 19.371186l0 165.41847c0 34.417881 28.000728 62.417585 62.417585 62.417585l618.946754 0c34.417881 0 62.417585-27.999704 62.417585-62.417585L896.202359 725.284641C896.204405 714.585975 887.531885 705.913455 876.833219 705.913455z" fill="#3bc05c" p-id="7301"></path></svg>发布</button>`;

                // 判断是否显示编排按钮
                let orchestrateButton = agent.driver === 'ai.agent.customer.GeneralAgent'
                    ? `<button class="operation-button orchestrate" onclick="orchestrationAgent(${agent.id})"><svg t="1740998030306" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="3893" width="200" height="200"><path d="M992 1024 31.296 1024C14.016 1024 0 1009.984 0 992.704l0-333.056c0-17.28 14.016-31.296 31.296-31.296L992 628.352c17.28 0 31.296 14.016 31.296 31.296l0 333.056C1023.36 1009.984 1009.344 1024 992 1024zM62.656 961.344l898.048 0 0-270.4L62.656 690.944 62.656 961.344z" fill="#666666" p-id="3894"></path><path d="M992.32 603.392 551.296 603.392c-17.28 0-31.296-14.016-31.296-31.296l0-540.8C519.936 14.016 533.952 0 551.296 0l441.088 0c17.28 0 31.296 14.016 31.296 31.296l0 199.552c0 17.28-14.016 31.296-31.296 31.296s-31.296-14.016-31.296-31.296L961.088 62.656 582.592 62.656 582.592 540.8l378.432 0L961.024 478.208c0-17.28 14.016-31.296 31.296-31.296s31.296 14.016 31.296 31.296l0 93.888C1023.68 589.376 1009.664 603.392 992.32 603.392z" fill="#666666" p-id="3895"></path><path d="M469.504 605.376 31.296 605.376C14.016 605.376 0 591.36 0 574.016l0-230.72c0-17.28 14.016-31.296 31.296-31.296l438.208 0c17.28 0 31.296 14.016 31.296 31.296l0 230.72C500.8 591.36 486.784 605.376 469.504 605.376zM62.656 542.72l375.552 0L438.208 374.656 62.656 374.656 62.656 542.72z" fill="#666666" p-id="3896"></path><path d="M469.824 293.376 31.68 293.376c-17.28 0-31.296-14.016-31.296-31.296l0-230.72C0.32 14.016 14.336 0 31.68 0l438.208 0c17.28 0 31.296 14.016 31.296 31.296l0 230.72C501.184 279.36 487.168 293.376 469.824 293.376zM62.976 230.72l375.552 0L438.528 62.656 62.976 62.656 62.976 230.72z" fill="#666666" p-id="3897"></path></svg>编排</button>`
                    : '';

                row.innerHTML = `
            <td>${agent.name}</td>
            <td>${driverName}</td>
            <td>${agent.token}</td>
            <td>${agent.appId}</td>
            <td>${agent.isFeeRequired ? '是' : '否'}</td>
            <td>${agent.isFeeRequired ? agent.pricePerReq : '0'}</td>
            <td>${agent.publishStatus ? '已发布' : '未发布'}</td>
            <td>${agent.income.toFixed(2)}</td> 
            <td>${agent.reqNum}</td> 
            <td>${agent.subscriberNum}</td> 
            <td>
              <button class="operation-button edit" onclick="editAgent(${agent.id})"><svg t="1740997971411" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="2849" width="200" height="200"><path d="M908.785955 492.415966c-11.04966 0-20.008706 8.958022-20.008706 20.008706l0 338.328565c0 38.614459-31.416524 70.030983-70.030983 70.030983L202.707748 920.78422c-38.614459 0-70.030983-31.416524-70.030983-70.030983l0-676.303067c0-38.614459 31.416524-70.030983 70.030983-70.030983l362.378308 0c11.04966 0 20.008706-8.958022 20.008706-20.008706 0-11.050684-8.959046-20.008706-20.008706-20.008706L202.707748 64.401776c-60.681034 0-110.049418 49.367361-110.049418 110.049418l0 676.303067c0 60.681034 49.367361 110.049418 110.049418 110.049418l616.038518 0c60.681034 0 110.049418-49.367361 110.049418-110.049418L928.795685 512.425695C928.795685 501.373988 919.836639 492.415966 908.785955 492.415966z" fill="#1364ff" p-id="2850"></path><path d="M942.576549 117.985158 907.20597 82.614579c-23.405059-23.405059-61.486376-23.400966-84.891436 0l-422.369633 422.36861c-1.295506 1.296529-2.407839 2.76395-3.304256 4.363378L269.318842 736.416166c-4.457522 7.951089-2.977821 17.909905 3.598968 24.221658 3.830235 3.675716 8.823969 5.572926 13.859659 5.572926 3.613294 0 7.247054-0.977257 10.488889-2.973728l219.285309-135.104047c1.327228-0.816598 2.552126-1.784646 3.654226-2.885723l422.369633-422.36861C965.979562 179.471534 965.979562 141.390217 942.576549 117.985158zM418.556794 552.080857l55.42124 55.279001-132.025942 81.343633L418.556794 552.080857zM914.279063 174.579107 505.926678 582.930469l-63.748906-63.586201L850.612021 110.912066c7.798617-7.800663 20.493753-7.804756 28.29544 0l35.371602 35.371602C922.081773 154.084331 922.081773 166.777421 914.279063 174.579107z" fill="#1364ff" p-id="2851"></path></svg>编辑</button>
              ${orchestrateButton}
              <button class="operation-button delete" onclick="deleteAgent(${agent.id})"><svg t="1740998071733" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="5089" id="mx_n_1740998071733" width="200" height="200"><path d="M398.753773 834.390571c-10.326183 0-18.696828-8.370645-18.696828-18.696828L380.056945 328.657298c0-10.326183 8.370645-18.696828 18.696828-18.696828s18.696828 8.370645 18.696828 18.696828l0 487.036445C417.4506 826.019926 409.079956 834.390571 398.753773 834.390571z" fill="#f52525" p-id="5090"></path><path d="M633.890095 834.390571c-10.326183 0-18.696828-8.370645-18.696828-18.696828L615.193267 328.657298c0-10.326183 8.370645-18.696828 18.696828-18.696828s18.696828 8.370645 18.696828 18.696828l0 487.036445C652.586922 826.019926 644.216277 834.390571 633.890095 834.390571z" fill="#f52525" p-id="5091"></path><path d="M710.574614 985.684346c-4.027733 0-8.083096-1.295506-11.500938-3.967358-26.988678-21.096481-80.083943-25.731034-98.264001-25.735128l-27.973098 0c-0.036839 0-0.073678 0-0.109494 0l-39.437198-0.233314L233.780441 955.748546c-0.335644 0-0.670266-0.00921-1.00591-0.026606-22.538319-1.214664-35.687804-11.190877-42.74964-19.345604-15.238053-17.596773-14.937201-40.310078-14.603603-45.898353L175.421288 248.768807c0-10.326183 8.370645-18.696828 18.696828-18.696828s18.696828 8.370645 18.696828 18.696828l0 642.355907c0 0.660032 0 0.968048-0.066515 1.597381-0.212848 3.461845 0.393973 13.353123 5.653767 19.300578 3.352351 3.791349 8.56405 5.863545 15.921621 6.332219l299.029514 0c0.036839 0 0.073678 0 0.109494 0l39.437198 0.233314 27.913746 0c3.199878 0.001023 78.735226 0.404206 121.287367 33.667797 8.135284 6.359848 9.575076 18.10945 3.216251 26.244734C721.630415 983.217155 716.130144 985.684346 710.574614 985.684346z" fill="#f52525" p-id="5092"></path><path d="M798.810215 955.980837l-24.01495 0c-10.326183 0-18.696828-8.370645-18.696828-18.696828s8.370645-18.696828 18.696828-18.696828l21.841447 0c0.64059-0.102331 1.287319-0.170892 1.937119-0.205685 7.676843-0.413416 13.07069-2.534729 16.490579-6.483668 6.093788-7.039323 5.560646-18.983353 5.550413-19.097964-0.050142-0.556679-0.074701-1.116427-0.074701-1.676176L820.540122 248.768807c0-10.326183 8.370645-18.696828 18.696828-18.696828l49.27117 0c0.075725 0 0.12075 0 0.172939-0.00307 0.047072-0.00307-0.020466-0.007163 0.063445-0.002047 6.862291-0.254803 6.854105-4.354168 6.847965-7.356548l0-92.054579c0-3.504824-2.849908-6.356778-6.352685-6.356778l-214.925001 0c-5.02034 0.021489-42.351574-0.751107-71.783907-32.649608-0.141216-0.152473-0.278339-0.306992-0.413416-0.463558-9.723455-11.266601-23.842011-17.33276-41.968857-18.031679l-86.940095 0c-18.124799 0.698918-32.240285 6.764054-41.961694 18.030655-0.1361 0.157589-0.274246 0.312108-0.415462 0.464581-29.434379 31.898501-66.768683 32.68133-71.785953 32.649608L144.119375 124.298957c-3.504824 0-6.355755 2.851954-6.355755 6.356778l0 92.015693c-0.00614 3.038196-0.013303 7.140631 6.852058 7.394411 0.086981-0.00614 0.017396-0.001023 0.062422 0.002047 0.052189 0.002047 0.095167 0.002047 0.172939 0.00307l49.2681 0c10.326183 0 18.696828 8.370645 18.696828 18.696828s-8.370645 18.696828-18.696828 18.696828l-49.134047 0c-3.726881 0.057305-19.807115-0.533143-32.124651-12.625552-5.714142-5.610788-12.521175-15.752776-12.489452-32.202423l0-91.980901c0-24.12342 19.62599-43.750433 43.74941-43.750433l214.997656 0c0.289596 0 0.270153-0.005117 0.499373 0.005117 1.973958 0 24.966625-0.446162 43.546795-20.420075 11.642155-13.380752 33.026184-29.455869 69.042469-30.719652 0.218988-0.008186 0.436952-0.01228 0.655939-0.01228l87.62878 0c0.218988 0 0.436952 0.004093 0.655939 0.01228 36.020378 1.263783 57.405431 17.337877 69.049632 30.718629 18.577101 19.971867 41.568744 20.420075 43.545772 20.420075 0.230244-0.011256 0.210801-0.005117 0.499373-0.005117l214.998679 0c24.121374 0 43.745317 19.627013 43.745317 43.750433l0 92.015693c0.032746 16.412808-6.771217 26.554796-12.486382 32.166607-12.317537 12.094456-28.389584 12.67774-32.124651 12.626575l-30.441313 0 0 623.010303c0.334621 5.586229 0.638543 28.300556-14.59951 45.899376-6.843872 7.904017-19.404955 17.519002-40.690748 19.208481C801.38383 955.847807 800.09958 955.980837 798.810215 955.980837z" fill="#f52525" p-id="5093"></path></svg>删除</button>
              <button class="operation-button test" onclick="testAgent(${agent.id})"><svg t="1761032778852" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="4613" width="32" height="32"><path d="M191.41 804.42h666.84v53.47H191.41zM915.23 713h-781a27.93 27.93 0 0 1-27.9-27.9V181.19a27.93 27.93 0 0 1 27.9-27.9h781a27.93 27.93 0 0 1 27.9 27.9v503.93a27.93 27.93 0 0 1-27.9 27.88z m-755.4-53.47h729.84V206.76H159.83z" p-id="4614" fill="#1296db"></path><path d="M438.86 532.76l-101.42-99.58 101.23-107.27 19.44 18.34-83.23 88.22 82.71 81.21-18.73 19.08zM610.66 532.39l-19.44-18.35 83.24-88.21-82.72-81.21 18.73-19.08 101.42 99.58-101.23 107.27zM485.71 533.518l51.957-208.854 25.94 6.453-51.958 208.854z" p-id="4615" fill="#1296db"></path></svg>测试</button>
              ${publishButton}
            </td>
          `;
                tbody.appendChild(row);
            });

            renderPagination(data.totalPage, pageNumber);  // 渲染分页
        });
}


// 渲染分页
function renderPagination(totalPages, currentPage) {
    let paginationContainer = document.getElementById('pagination');
    paginationContainer.innerHTML = '';

    // Style for the pagination container (using inline style, no background color)
    paginationContainer.style.display = 'flex';
    paginationContainer.style.gap = '5px';
    paginationContainer.style.justifyContent = 'center';
    paginationContainer.style.padding = '20px';
    paginationContainer.style.alignItems = 'center'; // Ensure vertical centering of all elements

    // 添加上一页按钮 (using SVG for left arrow, removed problematic flex on button)
    let prevButton = document.createElement('button');
    prevButton.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>';
    prevButton.onclick = () => {
        if (currentPage > 1) loadAgentList(currentPage - 1);
    };
    // Inline styles for prev/next buttons
    prevButton.style.padding = '8px 12px';
    prevButton.style.border = 'none';
    prevButton.style.borderRadius = '4px';
    prevButton.style.backgroundColor = '#007bff';
    prevButton.style.color = 'white';
    prevButton.style.cursor = 'pointer';
    prevButton.style.fontSize = '14px';
    prevButton.style.transition = 'background-color 0.3s';
    prevButton.style.outline = 'none';
    // Remove display: 'flex' from button, apply it to SVG container if needed
    prevButton.querySelector('svg').style.display = 'block'; // Ensure SVG is visible and centered
    prevButton.querySelector('svg').style.margin = 'auto'; // Center the SVG within the button
    prevButton.onmouseover = () => prevButton.style.backgroundColor = '#0056b3';
    prevButton.onmouseout = () => prevButton.style.backgroundColor = '#007bff';
    paginationContainer.appendChild(prevButton);

    for (let i = 1; i <= totalPages; i++) {
        let button = document.createElement('button');
        button.textContent = i;
        button.onclick = () => loadAgentList(i);
        // Inline styles for page number buttons, ensuring centering
        button.style.padding = '8px 12px';
        button.style.border = 'none';
        button.style.borderRadius = '4px';
        button.style.backgroundColor = i === currentPage ? '#007bff' : 'transparent';
        button.style.color = i === currentPage ? 'white' : '#007bff';
        button.style.cursor = 'pointer';
        button.style.fontSize = '14px';
        button.style.transition = 'background-color 0.3s, color 0.3s';
        button.style.outline = 'none';
        button.style.border = '1px solid #007bff';
        button.style.display = 'flex'; // Use flex on the button to center the text
        button.style.alignItems = 'center'; // Vertically center the text
        button.style.justifyContent = 'center'; // Horizontally center the text
        button.onmouseover = () => {
            if (i !== currentPage) {
                button.style.backgroundColor = '#e9ecef';
                button.style.color = '#0056b3';
            }
        };
        button.onmouseout = () => {
            if (i !== currentPage) {
                button.style.backgroundColor = 'transparent';
                button.style.color = '#007bff';
            }
        };
        paginationContainer.appendChild(button);
    }

    // 添加下一页按钮 (using SVG for right arrow, same fix as prevButton)
    let nextButton = document.createElement('button');
    nextButton.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6"/></svg>';
    nextButton.onclick = () => {
        if (currentPage < totalPages) loadAgentList(currentPage + 1);
    };
    // Inline styles for prev/next buttons (same as prevButton)
    nextButton.style.padding = '8px 12px';
    nextButton.style.border = 'none';
    nextButton.style.borderRadius = '4px';
    nextButton.style.backgroundColor = '#007bff';
    nextButton.style.color = 'white';
    nextButton.style.cursor = 'pointer';
    nextButton.style.fontSize = '14px';
    nextButton.style.transition = 'background-color 0.3s';
    nextButton.style.outline = 'none';
    // Remove display: 'flex' from button, apply it to SVG container if needed
    nextButton.querySelector('svg').style.display = 'block'; // Ensure SVG is visible and centered
    nextButton.querySelector('svg').style.margin = 'auto'; // Center the SVG within the button
    nextButton.onmouseover = () => nextButton.style.backgroundColor = '#0056b3';
    nextButton.onmouseout = () => nextButton.style.backgroundColor = '#007bff';
    paginationContainer.appendChild(nextButton);
}

// 编辑智能体
function editAgent(agentId) {
    currentAgentId = agentId;

    // 构造请求 URL，直接将参数附加到 URL 中
    const url = `/agent/getLagiAgent?agentId=${agentId}&lagiUserId=${globalUserId}`;

    fetch(url)
        .then(response => response.json())
        .then(data => {
            // 填充数据到表单
            document.getElementById("agent-name").value = data.data.name;
            document.getElementById("platform").value = data.data.driver;
            document.getElementById("token").value = data.data.token;
            document.getElementById("app-id").value = data.data.appId;
            document.getElementById("isFeeRequiredYes").checked = data.data.isFeeRequired;
            document.getElementById("isFeeRequiredNo").checked = !data.data.isFeeRequired;
            document.getElementById("pricePerReq").value = data.data.pricePerReq;

            // 根据是否收费，显示/隐藏收费金额输入框
            const pricePerReqContainer = document.getElementById("pricePerReqContainer");
            if (data.data.isFeeRequired) {
                pricePerReqContainer.style.display = "block";  // 显示收费金额输入框
            } else {
                pricePerReqContainer.style.display = "none";  // 隐藏收费金额输入框
            }

            // 打开模态框
            openAgentModal();
        })
        .catch(error => {
            console.error('Error:', error);  // 捕获并处理错误
        });
}

// 切换发布状态（POST 请求）
async function togglePublishStatus(agentId, currentPublishStatus) {
    // debugger
    // 获取确认信息
    const action = currentPublishStatus ? "发布" : "取消发布";
    const confirmMessage = `确定要${action}该智能体吗？`;
    const flag = await confirm(confirmMessage);
    // 弹出二次确认框
    if (flag) {
        // 构造请求数据
        const requestData = {
            id: agentId,
            publishStatus: currentPublishStatus  // 切换发布状态
        };

        // 发送 POST 请求到 /agent/updateLagiAgent 接口
        fetch('/agent/updateLagiAgent', {
            method: 'POST', // 使用 POST 请求
            headers: {
                'Content-Type': 'application/json' // 请求头设置为 JSON 格式
            },
            body: JSON.stringify(requestData) // 将请求数据转换为 JSON 字符串
        })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    // 如果更新成功，刷新智能体列表
                    loadAgentList(1);  // 重新加载列表，传入当前页码 1 或者你想要的页码
                } else {
                    alert('发布状态更新失败');
                }
            })
            .catch(error => {
                console.error('发布状态更新失败', error);
            });
    } else {
    }
}


// 删除智能体
async function deleteAgent(agentId) {
    const flag = await confirm('确定要删除这个智能体吗？');
    if (flag) {
        fetch(`/agent/deleteLagiAgentById`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify([agentId])
        }).then(response => response.json())
            .then(data => {
                loadAgentList(1); // 刷新列表
            });
    }
}

async function testAgent(agentId) {
    currentAppId = agentId;
    alert("测试agent设置成功, 请回到对话界面");
}

// 加载订阅智能体列表
function loadPaidAgentList(pageNumber) {
    fetch(`/agent/getPaidAgentByUser?lagiUserId=${globalUserId}&pageNumber=${pageNumber}&pageSize=10`)
        // fetch(`/agent/getPaidAgentByUser?lagiUserId=${60}&pageNumber=${pageNumber}&pageSize=10`)
        .then(response => response.json())
        .then(data => {
            let tbody = document.querySelector("#paid-agent-list tbody");
            tbody.innerHTML = ''; // 清空表格内容

            data.data.forEach(agent => {
                // 获取 driver 的翻译
                const driverName = driverMap[agent.driver] || agent.driver; // 如果没有找到映射，则显示原值

                let row = document.createElement('tr');

                row.innerHTML = `
                    <td>${agent.name}</td>
                    <td>${driverName}</td>
                    <td>${agent.isFeeRequired ? agent.pricePerReq : '0'}</td>
                    <td>${agent.balance}</td>
                `;
                tbody.appendChild(row);
            });

            renderPaidPagination(data.totalPage, pageNumber);  // 渲染分页
        });
}

// 渲染分页按钮
function renderPaidPagination(totalPage, currentPage) {
    let paginationContainer = document.getElementById("paid-agent-pagination");
    paginationContainer.innerHTML = '';  // 清空分页容器

    // Style for the pagination container (using inline style, no background color)
    paginationContainer.style.display = 'flex';
    paginationContainer.style.gap = '5px';
    paginationContainer.style.justifyContent = 'center';
    paginationContainer.style.padding = '20px';
    paginationContainer.style.alignItems = 'center'; // Ensure vertical centering of all elements

    // 添加上一页按钮 (using SVG for left arrow)
    let prevButton = document.createElement('button');
    prevButton.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>';
    prevButton.onclick = () => {
        if (currentPage > 1) loadPaidAgentList(currentPage - 1);
    };
    // Inline styles for prev/next buttons
    prevButton.style.padding = '8px 12px';
    prevButton.style.border = 'none';
    prevButton.style.borderRadius = '4px';
    prevButton.style.backgroundColor = '#007bff';
    prevButton.style.color = 'white';
    prevButton.style.cursor = 'pointer';
    prevButton.style.fontSize = '14px';
    prevButton.style.transition = 'background-color 0.3s';
    prevButton.style.outline = 'none';
    // Ensure SVG is visible and centered
    prevButton.querySelector('svg').style.display = 'block';
    prevButton.querySelector('svg').style.margin = 'auto';
    prevButton.onmouseover = () => prevButton.style.backgroundColor = '#0056b3';
    prevButton.onmouseout = () => prevButton.style.backgroundColor = '#007bff';
    paginationContainer.appendChild(prevButton);

    // 添加分页按钮
    for (let i = 1; i <= totalPage; i++) {
        let button = document.createElement('button');
        button.textContent = i;
        button.onclick = () => loadPaidAgentList(i);
        // Inline styles for page number buttons, ensuring centering
        button.style.padding = '8px 12px';
        button.style.border = 'none';
        button.style.borderRadius = '4px';
        button.style.backgroundColor = i === currentPage ? '#007bff' : 'transparent';
        button.style.color = i === currentPage ? 'white' : '#007bff';
        button.style.cursor = 'pointer';
        button.style.fontSize = '14px';
        button.style.transition = 'background-color 0.3s, color 0.3s';
        button.style.outline = 'none';
        button.style.border = '1px solid #007bff';
        button.style.display = 'flex'; // Use flex to center the text
        button.style.alignItems = 'center'; // Vertically center the text
        button.style.justifyContent = 'center'; // Horizontally center the text
        button.onmouseover = () => {
            if (i !== currentPage) {
                button.style.backgroundColor = '#e9ecef';
                button.style.color = '#0056b3';
            }
        };
        button.onmouseout = () => {
            if (i !== currentPage) {
                button.style.backgroundColor = 'transparent';
                button.style.color = '#007bff';
            }
        };
        paginationContainer.appendChild(button);
    }

    // 添加下一页按钮 (using SVG for right arrow)
    let nextButton = document.createElement('button');
    nextButton.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6"/></svg>';
    nextButton.onclick = () => {
        if (currentPage < totalPage) loadPaidAgentList(currentPage + 1);
    };
    // Inline styles for prev/next buttons (same as prevButton)
    nextButton.style.padding = '8px 12px';
    nextButton.style.border = 'none';
    nextButton.style.borderRadius = '4px';
    nextButton.style.backgroundColor = '#007bff';
    nextButton.style.color = 'white';
    nextButton.style.cursor = 'pointer';
    nextButton.style.fontSize = '14px';
    nextButton.style.transition = 'background-color 0.3s';
    nextButton.style.outline = 'none';
    // Ensure SVG is visible and centered
    nextButton.querySelector('svg').style.display = 'block';
    nextButton.querySelector('svg').style.margin = 'auto';
    nextButton.onmouseover = () => nextButton.style.backgroundColor = '#0056b3';
    nextButton.onmouseout = () => nextButton.style.backgroundColor = '#007bff';
    paginationContainer.appendChild(nextButton);
}

// 显示 div 的函数
function showHelpButton() {
    document.getElementById("help-button").style.display = "flex";
}

// 隐藏 div 的函数
function hideHelpButton() {
    document.getElementById("help-button").style.display = "none";
}

// 显示 div 的函数
function showFooterInfo() {
    document.getElementById("footer-info").style.display = "block";
}

// 隐藏 div 的函数
function hideFooterInfo() {
    document.getElementById("footer-info").style.display = "none";
}

//========= 查看收费智能体js=============

async function handleSelect(selectedItem, userQuestion) {
    if (!getCookie('userId')) {
        openModal();
        return;
    }
    const selectedValue = selectedItem.value;
    // const selectedOption = selectedItem.options[selectedItem.selectedIndex];
    const pricePerReq = selectedItem.getAttribute('data-priceperreq');
    const agentName = selectedItem.textContent.trim();
    if (selectedValue === 'default') {
        return;
    }
    selectedAgentId = selectedValue;
    // 判断是否是用户自己发布的智能体
    try {
        const isUserAgent = await isAgentBelongsToUser(globalUserId, selectedAgentId);
        if (isUserAgent) {
            // 如果是用户发布的智能体，可以跳过订阅检查，直接进行下一步
            await appointTextQuery(userQuestion, selectedAgentId);
        } else {
            // 如果不是用户发布的智能体，继续判断是否订阅
            const agentData = await checkSubscription(selectedAgentId);
            if (agentData) {
                // 如果余额小于零，弹出提示并打开收费框
                if (agentData.balance <= 0) {
                    let flag = await confirm('余额不足，请充值！');
                    if (flag) {
                        openRechargeModal(agentName, pricePerReq);
                    }
                } else {
                    // 使用 await 等待 appointTextQuery 异步调用完成
                    await appointTextQuery(userQuestion, selectedAgentId);
                }
            } else {
                // 如果没有订阅该智能体，弹出提示
                let flag = await confirm(`该智能体 (${agentName}) 需要订阅才能使用！`);
                if (flag) {
                    openRechargeModal(agentName, pricePerReq);
                }
            }
        }
    } catch (error) {
        console.error("操作失败:", error);
        alert("操作失败，请稍后再试！");
    }
}




function openRechargeModal(agentName, pricePerReq) {
    // 获取弹框元素
    const overlay = document.getElementById('recharge-modal');
    const agentNameElement = document.querySelector('.recharge-agent-name');
    const priceElement = document.querySelector('.recharge-price');

    // 更新智能体名称和单次调用价格
    agentNameElement.textContent = `智能体名称：${agentName}`;
    priceElement.textContent = `¥ ${pricePerReq}`;

    // 更新底部的总金额
    const totalAmountFooterElement = document.getElementById('recharge-total-amount-footer');
    totalAmountFooterElement.textContent = `¥ ${pricePerReq}`;

    // 显示弹框
    if (overlay) {
        overlay.style.display = 'flex';
    }


    // 更新单次调用价格
    const callCountInput = document.getElementById('recharge-call-count');
    callCountInput.addEventListener('input', function () {
        updateTotalAmount(pricePerReq);
    });

    // 初始化金额
    updateTotalAmount(pricePerReq);
}

// 计算充值金额的函数
function updateTotalAmount(pricePerReq) {
    const callCountInput = document.getElementById('recharge-call-count');
    const totalAmountFooterElement = document.getElementById('recharge-total-amount-footer');
    const callCount = parseInt(callCountInput.value, 10);
    // 如果输入无效，设置默认值为 1
    if (isNaN(callCount) || callCount < 1) {
        callCountInput.value = 1;
        return;
    }
    // 计算总金额
    const totalAmount = pricePerReq * callCount;
    // 使用 toFixed() 保留两位小数
    const formattedTotalAmount = totalAmount.toFixed(2);
    // 更新总金额显示
    totalAmountFooterElement.textContent = `¥ ${formattedTotalAmount}`;
}



// 关闭弹框
function closeRechargeModal() {
    const overlay = document.getElementById('recharge-modal');
    const callCountInput = document.getElementById('recharge-call-count');
    callCountInput.value = 1;
    overlay.style.display = 'none';
}

// 订阅按钮点击事件
function subscription() {
    const totalAmountFooterElement = document.getElementById('recharge-total-amount-footer');
    const totalAmount = totalAmountFooterElement.textContent.trim().replace('¥', '').trim();
    // 检查 agentId 是否存在
    if (!selectedAgentId) {
        alert('请选择一个智能体');
        return;
    }
    // 调用 showQrCode 函数
    showQrCode(globalUserId, selectedAgentId, totalAmount);
    closeRechargeModal();
}

function showQrCode(lagiUserId, agentId, fee) {
    if (isMobileDevice()) {
        $('#h5PrepayBtn').show();
    } else {
        $('#h5PrepayBtn').hide();
    }
    $.ajax({
        url: "/agent/prepay",
        contentType: "application/json;charset=utf-8",
        type: "post",
        data: JSON.stringify({
            "lagiUserId": lagiUserId,
            "agentId": agentId,
            "fee": fee,
        }),
        success: function (res) {
            if (res.result === '1') {
                document.getElementById("qrCode").src = "data:image/png;base64," + res.qrCode;
                $('#payAmount').text(res.totalFee);
                $('#wechat_pay_qr').show();
                interval = setInterval(function () {
                    getAgentChargeDetail(res.outTradeNo);
                }, 1000);
            } else {
                alert("获取收款码失败");
            }
        }
    });
}

// 查询支付状态
function getAgentChargeDetail(outTradeNo) {
    $.ajax({
        url: "/agent/getAgentChargeDetail",
        contentType: "application/json;charset=utf-8",
        type: "get",
        data: { 'outTradeNo': outTradeNo },
        success: function (res) {
            if (res.status === 1) {
                clearInterval(interval);
                alert("支付成功");
                $('#wechat_pay_qr').hide();
            }
        }
    });
}

function cancelPayment() {
    // 隐藏支付弹框
    $('#wechat_pay_qr').hide();
    // 清除二维码和支付信息
    document.getElementById("qrCode").src = "";
    $('#payAmount').text('');
    // 清除支付查询的定时器
    clearInterval(interval);
}

function isMobileDevice() {
    const userAgent = navigator.userAgent || navigator.vendor || window.opera;
    const mobileRegex = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i;
    return mobileRegex.test(userAgent);
}

function h5Prepay() {
    selectedAgentId = 27;
    const totalAmountFooterElement = document.getElementById('recharge-total-amount-footer');
    const totalAmount = totalAmountFooterElement.textContent.trim().replace('¥', '').trim();
    if (!selectedAgentId) {
        alert('请选择一个智能体');
        return;
    }
    $.ajax({
        url: "/agent/h5Prepay",
        contentType: "application/json;charset=utf-8",
        type: "post",
        data: JSON.stringify({
            "lagiUserId": globalUserId,
            "agentId": selectedAgentId,
            "fee": totalAmount,
        }),
        success: function (res) {
            if (res.result === '1') {
                window.location.href = res.mWebUrl;
                $('#h5PrepayBtn').text('支付中...').css('disabled', true);
            } else {
                alert(res.error);
            }
        }
    });
}

function checkSubscription(agentId) {
    return new Promise((resolve, reject) => {
        fetch(`/agent/getPaidAgentByUser?lagiUserId=${globalUserId}&pageNumber=1&pageSize=1000`)
            .then(response => response.json())
            .then(data => {
                if (data.status === "success") {
                    const agent = data.data.find(agent => agent.id === parseInt(agentId));
                    resolve(agent || null);  // 如果找到该ID的智能体，返回数据，否则返回null
                } else {
                    reject(new Error("接口返回状态异常"));
                }
            })
            .catch(err => {
                reject(err);
            });
    });
}

// 判断智能体是否是用户发布的
async function isAgentBelongsToUser(lagiUserId, agentId) {
    try {
        const response = await fetch(`/agent/getLagiAgent?agentId=${agentId}`);
        if (!response.ok) {
            throw new Error('请求失败，无法获取数据');
        }
        const data = await response.json();
        if (data.status === 'success' && data.data) {
            const agent = data.data;

            if (agent.lagiUserId === lagiUserId) {
                return true;
            }
        }
        return false;
    } catch (error) {
        console.error('请求数据时发生错误:', error);
        return false;
    }
}


// ========= 创建智能体js=============

function openCreateAgent() {
    if (!getCookie('userId')) {
        openModal();
        return;
    }
    document.getElementById("createLagiAgent").style.visibility = 'visible';

    document.getElementById("LagiAgentName").value = '';
    document.getElementById("LagiAgentDescribe").value = '';

    document.getElementById("createLagiAgentButtons").disabled = true;
}

function closeCreateAgent() {
    document.getElementById("createLagiAgent").style.visibility = 'hidden';

    document.getElementById("LagiAgentName").value = '';
    document.getElementById("LagiAgentDescribe").value = '';

    document.getElementById("createLagiAgentButtons").disabled = true;
}


function validateForm() {
    const lagiagentName = document.getElementById("LagiAgentName").value.trim();
    const describeValue = document.getElementById("LagiAgentDescribe").value.trim();
    const createButton = document.getElementById("createLagiAgentButtons");

    if (describeValue === "" || lagiagentName === "") {
        createButton.disabled = true;
    } else {
        createButton.disabled = false;
    }
}

function createLagiAgent() {
    let agentConfig = {
        id: currentAgentId,
        name: document.getElementById("LagiAgentName").value,
        describe: document.getElementById("LagiAgentDescribe").value,
        lagiUserId: globalUserId,
        isFeeRequired: false,
        pricePerReq: 0,
        publishStatus: true
    };
    document.getElementById("createLagiAgentButtons").disabled = true;
    fetch('/agent/createLagiAgent', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(agentConfig),
    })
        .then(response => response.json())
        .then(data => {
            closeCreateAgent();
            alert('智能体创建成功！');
            document.getElementById("createLagiAgentButtons").disabled = false;
        })
        .catch(error => {
            closeCreateAgent();
            alert('创建失败，请重试！');
            document.getElementById("createLagiAgentButtons").disabled = false;
        });
}

document.getElementById("LagiAgentDescribe").addEventListener("input", validateForm);
document.getElementById("LagiAgentName").addEventListener("input", validateForm);

// ========= 编排智能体js开始=============
let canvas = document.getElementById("orchestration-canvas");
let xmlns = "http://www.w3.org/2000/svg";
let scaleFactor = 1;
let offsetX = 0, offsetY = 0;

// Expanded Node Types
const NODE_TYPES = {
    START: 'start',
    END: 'end',
    PROCESS: 'process',
    DECISION: 'decision',
    DOCUMENT: 'document',
    SUBPROCESS: 'subprocess',
    DATABASE: 'database',
    NOTE: 'note',
    ONPAGE_REF: 'onpage_ref'
};

// Structure Types
const STRUCTURE_TYPES = {
    SEQUENCE: 'sequence',
    BRANCH: 'branch',
    LOOP_WHILE: 'loop_while',
    LOOP_UNTIL: 'loop_until'
};

// Node Data Model
let nodes = [];
let connections = [];

// Open Orchestration Editor
function orchestrationAgent(agentId) {
    // document.getElementById("orchestration-modal-container").style.display = "flex";
    // document.getElementById("orchestration-modal").setAttribute("data-agent-id", agentId);
    // nodes = [];
    // connections = [];
    // updateCanvas();
    // window.open(`http://localhost:3000/?agentId=${agentId}`, '_blank');
    window.open(`/ui/workflow/index.html?agentId=${agentId}`, '_blank');
}

// Close Orchestration Editor
function closeOrchestrationAgent() {
    document.getElementById("orchestration-modal-container").style.display = "none";
    let tbody = document.querySelector("#orchestration-table tbody");
    tbody.innerHTML = "";
    updateCanvas();
}

// Add New Row
function addRow() {
    let tbody = document.querySelector("#orchestration-table tbody");
    let row = document.createElement("tr");
    row.innerHTML = `
        <td class="orchestration-task">
            <input type="text" placeholder="任务名称" oninput="updateCanvas()">
        </td>
        <td class="orchestration-logic">
            <input type="text" placeholder="执行逻辑" oninput="updateCanvas()">
        </td>
        <td class="orchestration-node-type">
            <select onchange="updateCanvas()">
                <option value="${NODE_TYPES.START}">开始</option>
                <option value="${NODE_TYPES.END}">结束</option>
                <option value="${NODE_TYPES.PROCESS}">流程</option>
                <option value="${NODE_TYPES.DECISION}">判定</option>
                <option value="${NODE_TYPES.DOCUMENT}">文档</option>
                <option value="${NODE_TYPES.SUBPROCESS}">子流程</option>
                <option value="${NODE_TYPES.DATABASE}">数据库</option>
                <option value="${NODE_TYPES.NOTE}">注释</option>
                <option value="${NODE_TYPES.ONPAGE_REF}">页面内引用</option>
            </select>
        </td>
        <td>
            <select onchange="updateCanvas()" class="structure-type">
                <option value="${STRUCTURE_TYPES.SEQUENCE}">顺序</option>
                <option value="${STRUCTURE_TYPES.BRANCH}">分支</option>
                <option value="${STRUCTURE_TYPES.LOOP_WHILE}">循环（条件为真时）</option>
                <option value="${STRUCTURE_TYPES.LOOP_UNTIL}">循环（直到条件为真）</option>
            </select>
        </td>
        <td>
            <button class="orchestration-delete-btn" onclick="deleteRow(this)">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#ef4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6" />
                </svg>
            </button>
        </td>
    `;
    tbody.appendChild(row);
    updateCanvas();
}


// Delete Row and Update Canvas
function deleteRow(button) {
    let row = button.parentElement.parentElement;
    row.remove();
    updateCanvas();
}

// Update Canvas
function updateCanvas() {
    canvas.innerHTML = "";
    nodes = [];
    connections = [];

    // Parse Table Data
    const rows = Array.from(document.querySelectorAll("#orchestration-table tbody tr"));
    let prevNode = null;
    let branchStack = []; // Track active branches

    rows.forEach((row, index) => {
        const task = row.querySelector(".orchestration-task input").value || `步骤${index + 1}`;
        const logic = row.querySelector(".orchestration-logic input").value;
        const nodeType = row.querySelector(".orchestration-node-type select").value;
        const structureType = row.querySelector(".structure-type").value;

        // Create Node
        const node = {
            id: `node_${index}`,
            type: nodeType,
            structure: structureType,
            text: task,
            logic: logic,
            x: 0,
            y: 0,
            children: [],
            parents: []
        };
        nodes.push(node);

        // Handle Connections
        if (prevNode && !branchStack.length) {
            connections.push({ from: prevNode.id, to: node.id, type: 'normal' });
            prevNode.children.push(node.id);
            node.parents.push(prevNode.id);
        }

        // Handle Branch Structure
        if (structureType === STRUCTURE_TYPES.BRANCH && nodeType === NODE_TYPES.DECISION) {
            const trueNode = {
                id: `node_${index}_true`,
                type: NODE_TYPES.PROCESS,
                structure: STRUCTURE_TYPES.SEQUENCE,
                text: "是路径",
                logic: "",
                x: 0,
                y: 0,
                children: [],
                parents: [node.id]
            };
            const falseNode = {
                id: `node_${index}_false`,
                type: NODE_TYPES.PROCESS,
                structure: STRUCTURE_TYPES.SEQUENCE,
                text: "否路径",
                logic: "",
                x: 0,
                y: 0,
                children: [],
                parents: [node.id]
            };
            nodes.push(trueNode, falseNode);
            connections.push({ from: node.id, to: trueNode.id, type: 'condition', label: '是' });
            connections.push({ from: node.id, to: falseNode.id, type: 'condition', label: '否' });
            node.children.push(trueNode.id, falseNode.id);
            branchStack.push({ trueNode, falseNode, decision: node });
            prevNode = null; // Pause main flow until branch merges
        } else if (branchStack.length > 0) {
            // Connect within branch
            const lastBranch = branchStack[branchStack.length - 1];
            if (!lastBranch.trueConnected) {
                connections.push({ from: lastBranch.trueNode.id, to: node.id, type: 'normal' });
                lastBranch.trueNode.children.push(node.id);
                node.parents.push(lastBranch.trueNode.id);
                lastBranch.trueConnected = true;
            } else if (!lastBranch.falseConnected) {
                connections.push({ from: lastBranch.falseNode.id, to: node.id, type: 'normal' });
                lastBranch.falseNode.children.push(node.id);
                node.parents.push(lastBranch.falseNode.id);
                lastBranch.falseConnected = true;
                branchStack.pop(); // Merge branches here
                prevNode = node; // Resume main flow
            }
        } else {
            prevNode = node;
        }
    });

    // Auto Layout
    autoLayout(nodes, connections);

    // Render
    renderNodes(nodes);
    renderConnections(connections);
}

// Improved Auto Layout Algorithm
function autoLayout(nodes, connections) {
    const BASE_X = 400;
    const BASE_Y = 100;
    const H_SPACING = 300; // Increased for horizontal branching
    const V_SPACING = 150;
    const BRANCH_OFFSET = 0; // Align branches horizontally

    const nodeMap = new Map();
    nodes.forEach(node => nodeMap.set(node.id, node));

    // Build Relationships
    nodes.forEach(node => {
        node.children = [];
        node.parents = [];
    });
    connections.forEach(conn => {
        const parent = nodeMap.get(conn.from);
        const child = nodeMap.get(conn.to);
        if (parent && child) {
            parent.children.push(child);
            child.parents.push(parent);
        }
    });

    // Recursive Layout
    function layoutNode(node, x, y, visited = new Set(), branchLevel = 0) {
        if (visited.has(node.id)) return;
        visited.add(node.id);

        node.x = x + (branchLevel * H_SPACING);
        node.y = y;

        if (node.structure === STRUCTURE_TYPES.BRANCH && node.type === NODE_TYPES.DECISION) {
            const trueChild = node.children.find(c => c.id.includes('true'));
            const falseChild = node.children.find(c => c.id.includes('false'));
            if (trueChild) layoutNode(trueChild, x + H_SPACING / 2, y + V_SPACING, visited, 0);
            if (falseChild) layoutNode(falseChild, x - H_SPACING / 2, y + V_SPACING, visited, 0);
        } else if (node.structure.startsWith('loop')) {
            node.children.forEach((child, idx) => {
                layoutNode(child, x, y + V_SPACING * (idx + 1), visited, branchLevel);
                connections.push({ from: child.id, to: node.id, type: 'loop' });
            });
        } else {
            node.children.forEach((child, idx) => {
                layoutNode(child, x, y + V_SPACING * (idx + 1), visited, branchLevel);
            });
        }
    }

    const rootNode = nodes.find(n => n.parents.length === 0);
    if (rootNode) layoutNode(rootNode, BASE_X, BASE_Y);
}

// Render Nodes
function renderNodes(nodes) {
    const defs = document.createElementNS(xmlns, "defs");
    const marker = document.createElementNS(xmlns, "marker");
    marker.id = "arrowhead";
    marker.setAttribute("viewBox", "0 0 10 10");
    marker.setAttribute("refX", "9");
    marker.setAttribute("refY", "3");
    marker.setAttribute("markerWidth", "6");
    marker.setAttribute("markerHeight", "6");
    marker.setAttribute("orient", "auto");
    const arrow = document.createElementNS(xmlns, "path");
    arrow.setAttribute("d", "M0,0 L0,6 L9,3 z");
    arrow.setAttribute("fill", "#666");
    marker.appendChild(arrow);
    defs.appendChild(marker);
    canvas.appendChild(defs);

    nodes.forEach(node => {
        let shape;
        const width = 120;
        const height = 60;
        const radius = 30;

        switch (node.type) {
            case NODE_TYPES.START:
            case NODE_TYPES.END:
                shape = document.createElementNS(xmlns, "circle");
                shape.setAttribute("cx", node.x);
                shape.setAttribute("cy", node.y);
                shape.setAttribute("r", radius);
                shape.setAttribute("fill", node.type === NODE_TYPES.START ? "#4CAF50" : "#F44336");
                shape.setAttribute("stroke-width", node.type === NODE_TYPES.END ? "4" : "2");
                break;

            case NODE_TYPES.PROCESS:
                shape = document.createElementNS(xmlns, "rect");
                shape.setAttribute("x", node.x - width / 2);
                shape.setAttribute("y", node.y - height / 2);
                shape.setAttribute("width", width);
                shape.setAttribute("height", height);
                shape.setAttribute("rx", 5);
                shape.setAttribute("fill", "#4CAF50"); // Green for process nodes
                break;

            case NODE_TYPES.DECISION:
                shape = document.createElementNS(xmlns, "path");
                const d = `M ${node.x} ${node.y - height / 2} L ${node.x + width / 2} ${node.y} L ${node.x} ${node.y + height / 2} L ${node.x - width / 2} ${node.y} Z`;
                shape.setAttribute("d", d);
                shape.setAttribute("fill", "#FFCA28"); // Yellow for decision
                break;

            case NODE_TYPES.DOCUMENT:
                shape = document.createElementNS(xmlns, "path");
                const docPath = `M ${node.x - width / 2} ${node.y - height / 2} H ${node.x + width / 2} V ${node.y + height / 2 - 10} Q ${node.x + width / 4} ${node.y + height / 2 + 10}, ${node.x - width / 2} ${node.y + height / 2 - 10} Z`;
                shape.setAttribute("d", docPath);
                shape.setAttribute("fill", "#9C27B0");
                break;

            case NODE_TYPES.SUBPROCESS:
                shape = document.createElementNS(xmlns, "rect");
                shape.setAttribute("x", node.x - width / 2);
                shape.setAttribute("y", node.y - height / 2);
                shape.setAttribute("width", width);
                shape.setAttribute("height", height);
                shape.setAttribute("rx", 5);
                shape.setAttribute("fill", "#673AB7");
                const innerRect = document.createElementNS(xmlns, "rect");
                innerRect.setAttribute("x", node.x - width / 2 + 5);
                innerRect.setAttribute("y", node.y - height / 2 + 5);
                innerRect.setAttribute("width", width - 10);
                innerRect.setAttribute("height", height - 10);
                innerRect.setAttribute("fill", "none");
                innerRect.setAttribute("stroke", "#fff");
                innerRect.setAttribute("stroke-width", "2");
                canvas.appendChild(innerRect);
                break;

            case NODE_TYPES.DATABASE:
                shape = document.createElementNS(xmlns, "path");
                const dbPath = `M ${node.x - width / 2} ${node.y - height / 2} A ${width / 2} 10 0 0 1 ${node.x + width / 2} ${node.y - height / 2} V ${node.y + height / 2} A ${width / 2} 10 0 0 1 ${node.x - width / 2} ${node.y + height / 2} Z`;
                shape.setAttribute("d", dbPath);
                shape.setAttribute("fill", "#FF5722");
                break;

            case NODE_TYPES.NOTE:
                shape = document.createElementNS(xmlns, "path");
                const notePath = `M ${node.x - width / 2} ${node.y - height / 2} H ${node.x + width / 2 - 10} L ${node.x + width / 2} ${node.y - height / 2 + 10} V ${node.y + height / 2} H ${node.x - width / 2} Z`;
                shape.setAttribute("d", notePath);
                shape.setAttribute("fill", "#8BC34A");
                break;

            case NODE_TYPES.ONPAGE_REF:
                shape = document.createElementNS(xmlns, "circle");
                shape.setAttribute("cx", node.x);
                shape.setAttribute("cy", node.y);
                shape.setAttribute("r", radius);
                shape.setAttribute("fill", "#607D8B");
                shape.setAttribute("stroke-dasharray", "5,5");
                break;
        }

        shape.setAttribute("stroke", "#333");
        shape.setAttribute("stroke-width", "2");
        canvas.appendChild(shape);

        // Add Text
        addText(node.x, node.y - 5, node.text, 12, "bold");
        if (node.logic) addText(node.x, node.y + 15, node.logic, 10);
    });
}

// Render Connections
function renderConnections(connections) {
    connections.forEach(conn => {
        const fromNode = nodes.find(n => n.id === conn.from);
        const toNode = nodes.find(n => n.id === conn.to);
        if (!fromNode || !toNode) return;

        let path;
        if (conn.type === 'loop') {
            path = document.createElementNS(xmlns, "path");
            const controlX = fromNode.x - 150;
            const controlY = (fromNode.y + toNode.y) / 2;
            path.setAttribute("d", `M ${fromNode.x} ${fromNode.y} C ${controlX} ${fromNode.y}, ${controlX} ${toNode.y}, ${toNode.x} ${toNode.y}`);
            path.setAttribute("stroke-dasharray", "5,5");
        } else if (fromNode.type === NODE_TYPES.DECISION) {
            path = document.createElementNS(xmlns, "line");
            path.setAttribute("x1", fromNode.x);
            path.setAttribute("y1", fromNode.y);
            path.setAttribute("x2", toNode.x);
            path.setAttribute("y2", toNode.y);
        } else {
            path = document.createElementNS(xmlns, "line");
            path.setAttribute("x1", fromNode.x);
            path.setAttribute("y1", fromNode.y);
            path.setAttribute("x2", toNode.x);
            path.setAttribute("y2", toNode.y);
        }

        path.setAttribute("stroke", "#333");
        path.setAttribute("fill", "none");
        path.setAttribute("stroke-width", "2");
        path.setAttribute("marker-end", "url(#arrowhead)");
        canvas.appendChild(path);

        if (conn.label) {
            const labelX = (fromNode.x + toNode.x) / 2;
            const labelY = (fromNode.y + toNode.y) / 2 - 20; // Position above the line
            addText(labelX, labelY, conn.label, 12, "bold");
        }
    });
}

// Helper Function: Add Text
function addText(x, y, text, size, weight = "normal") {
    const textElem = document.createElementNS(xmlns, "text");
    textElem.setAttribute("x", x);
    textElem.setAttribute("y", y);
    textElem.setAttribute("text-anchor", "middle");
    textElem.setAttribute("font-size", size);
    textElem.setAttribute("font-weight", weight);
    textElem.setAttribute("fill", "#000"); // Black text for visibility
    textElem.textContent = text;
    canvas.appendChild(textElem);
}

// Enable Canvas Drag
function enableCanvasDrag() {
    let canvasContainer = document.getElementById("orchestration-canvas-container");
    let isDragging = false;
    let startX, startY;
    if (canvasContainer) {
        canvasContainer.addEventListener("mousedown", (e) => {
            isDragging = true;
            startX = e.clientX;
            startY = e.clientY;
        });

        canvasContainer.addEventListener("mousemove", (e) => {
            if (isDragging) {
                let offsetX = e.clientX - startX;
                let offsetY = e.clientY - startY;
                canvasContainer.scrollLeft -= offsetX;
                canvasContainer.scrollTop -= offsetY;
                startX = e.clientX;
                startY = e.clientY;
            }
        });

        canvasContainer.addEventListener("mouseup", () => {
            isDragging = false;
        });
    }
}

// Enable Canvas Zoom
function enableCanvasZoom() {
    let canvasContainer = document.getElementById("orchestration-canvas-container");
    if (canvasContainer) {
        canvasContainer.addEventListener("wheel", (e) => {
            e.preventDefault();
            let zoom = e.deltaY > 0 ? 0.9 : 1.1;
            scaleFactor *= zoom;
            scaleFactor = Math.max(0.5, Math.min(scaleFactor, 3));
            canvas.style.transform = `scale(${scaleFactor})`;
            const rect = canvasContainer.getBoundingClientRect();
            const offsetX = e.clientX - rect.left;
            const offsetY = e.clientY - rect.top;
            canvasContainer.scrollLeft = (canvasContainer.scrollLeft + offsetX) * zoom - offsetX;
            canvasContainer.scrollTop = (canvasContainer.scrollTop + offsetY) * zoom - offsetY;
        });
    }
}

enableCanvasDrag();
enableCanvasZoom();

// Placeholder Save Function
function saveOrchestration() {
    let agentId = document.getElementById("orchestration-modal").getAttribute("data-agent-id");
    let rows = document.querySelectorAll("#orchestration-table tbody tr");
    let orchestrationData = [];

    rows.forEach(row => {
        let task = row.children[0].children[0].value.trim();
        let logic = row.children[1].children[0].value.trim();
        if (task && logic) {
            orchestrationData.push({ task, logic });
        }
    });

    fetch('/agent/orchestrationAgent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            lagiUserId: globalUserId,
            agentId: agentId,
            orchestrationData: orchestrationData
        })
    })
        .then(response => response.json())  // 解析后端返回的 JSON
        .then(data => {
            // 根据后端返回的 status 字段判断
            if (data.status === "success") {
                alert("修改成功");
                closeOrchestrationAgent();
            } else {
                alert("修改失败");
            }
        })
        .catch(error => {
            console.error('Error:', error);  // 捕获网络错误或解析错误
            alert("请求失败，请稍后重试");
        });
}
// ========= 编排智能体js结束=============