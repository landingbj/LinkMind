let filterConfigData = [];
let filteredConfigData = [];
const tTextFilter = window.tText || ((s) => s);
const tHtmlFilter = window.tHtml || ((s) => s);

const FILTER_TYPE_META = {
    sensitive_input: {
        label: '输入敏感词',
        summary: '拦截或处理用户问题，命中 block 时会直接阻止请求。',
        ruleHint: '在分组里配置规则和级别：1=block，2=mask，3=erase。多条规则支持英文逗号、中文逗号、顿号、分号或换行分隔。'
    },
    sensitive: {
        label: '输出敏感词',
        summary: '过滤模型回复内容，支持 block、mask、erase。',
        ruleHint: '在分组里配置规则和级别：1=block，2=mask，3=erase。多条规则支持英文逗号、中文逗号、顿号、分号或换行分隔。'
    },
    priority: {
        label: '优先词',
        summary: '影响候选答案或检索结果排序，不会拦截请求。',
        ruleHint: '在规则中填写优先匹配词，支持英文逗号、中文逗号、顿号、分号或换行分隔。'
    },
    stopping: {
        label: '停止词',
        summary: '用于判断新话题和会话边界，命中后不再强行续接上一轮上下文，不会拦截请求。',
        ruleHint: '在规则中填写表示话题结束或新话题开始的词，支持英文逗号、中文逗号、顿号、分号或换行分隔。'
    },
    continue: {
        label: '继续词',
        summary: '用于判断用户想继续上一轮话题，会影响上下文拼接和检索。',
        ruleHint: '在规则中填写继续追问类词，支持英文逗号、中文逗号、顿号、分号或换行分隔。'
    }
};

function getFilterTypeMeta(name) {
    return FILTER_TYPE_META[name] || {
        label: name || '',
        summary: '',
        ruleHint: ''
    };
}

function escapeHtmlFilter(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function escapeJsStringFilter(value) {
    return String(value == null ? '' : value)
        .replace(/\\/g, '\\\\')
        .replace(/'/g, "\\'")
        .replace(/\r/g, '\\r')
        .replace(/\n/g, '\\n');
}

function getFilterConfigErrorMessage(response, fallback) {
    if (!response) {
        return fallback || tTextFilter('未知错误');
    }
    return response.errorMsg || response.msg || (response.message && response.message !== 'failed' ? response.message : null) || fallback || tTextFilter('未知错误');
}

function loadFilterConfigPage() {
    $('#queryBox').hide();
    $('#footer-info').hide();
    $('#introduces').hide();
    $('#topTitle').hide();
    $('#item-content').show();
    $('#item-content').css('height', 'calc(100vh - 60px)');
    $('#item-content').css('overflow-y', 'auto');
    hideBallDiv();
    const html = `
        <div id="filter-config-container" style="padding: 20px; min-height: 100%; background: #fff; overflow-y: auto;">
            <div style="margin-bottom: 20px;">
                <h2 style="margin-bottom: 10px;">安全配置管理</h2>
                <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
                    <button onclick="showAddFilterDialog()" style="padding: 8px 16px; background: #1296db; color: white; border: none; border-radius: 4px; cursor: pointer;">新增过滤器</button>
                    <input type="text" id="searchInput" placeholder="搜索过滤器..." style="padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; flex: 1; min-width: 200px;" onkeyup="filterConfigList()" />
                    <select id="filterType" onchange="filterConfigList()" style="padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px;">
                        <option value="">全部类型</option>
                        <option value="sensitive_input">输入敏感词</option>
                        <option value="sensitive">输出敏感词</option>
                        <option value="priority">优先级</option>
                        <option value="stopping">停止词</option>
                        <option value="continue">继续词</option>
                    </select>
                </div>
            </div>
            <div id="filterConfigList" style="display: grid; gap: 16px;">
            </div>
        </div>
        <div id="filterModal" style="display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 1000; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 8px; padding: 24px; max-width: 600px; width: 90%; max-height: 80vh; overflow-y: auto;">
                <h3 id="modalTitle" style="margin-bottom: 20px;">新增过滤器</h3>
                <div style="margin-bottom: 16px;">
                    <label style="display: block; margin-bottom: 8px;">过滤器类型: <span style="color: red;">*</span></label>
                    <select id="filterName" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" onchange="onFilterTypeChange()">
                        <option value="">请选择过滤器类型</option>
                        <option value="sensitive_input">输入敏感词 (sensitive_input)</option>
                        <option value="sensitive">输出敏感词 (sensitive)</option>
                        <option value="priority">优先级 (priority)</option>
                        <option value="stopping">停止词 (stopping)</option>
                        <option value="continue">继续词 (continue)</option>
                    </select>
                    <div id="filterTypeSummary" style="font-size: 12px; color: #666; margin-top: 6px;">注意：只能选择以上系统支持的过滤器类型，自定义名称不会生效</div>
                </div>
                <div id="groupsContainer" style="margin-bottom: 16px;">
                </div>
                <div id="rulesContainer" style="margin-bottom: 16px;">
                    <label style="display: block; margin-bottom: 8px;">规则 (支持逗号、顿号、分号或换行分隔):</label>
                    <textarea id="filterRules" placeholder="例如: car,weather,社*保&#10;支持英文逗号、中文逗号、顿号、分号或换行分隔，支持正则表达式" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; min-height: 100px;"></textarea>
                    <div id="filterRuleHint" style="font-size: 12px; color: #666; margin-top: 4px;">提示：支持英文逗号、中文逗号、顿号、分号或换行分隔，支持正则表达式。如果是敏感词过滤器，请在“分组”中配置级别和规则。</div>
                </div>
                <div style="display: flex; gap: 10px; justify-content: flex-end;">
                    <button onclick="hideFilterModal()" style="padding: 8px 16px; background: #ccc; color: white; border: none; border-radius: 4px; cursor: pointer;">取消</button>
                    <button onclick="saveFilterConfig()" style="padding: 8px 16px; background: #1296db; color: white; border: none; border-radius: 4px; cursor: pointer;">保存</button>
                </div>
            </div>
        </div>
        <div id="deleteConfirmModal" style="display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 1001; align-items: center; justify-content: center;" onclick="if(event.target === this) { $('#deleteConfirmModal').css('display', 'none'); deleteConfirmId = null; deleteConfirmName = null; }">
            <div style="background: white; border-radius: 8px; padding: 24px; max-width: 400px; width: 90%;" onclick="event.stopPropagation();">
                <h3 style="margin-bottom: 16px; color: black;">确认删除</h3>
                <p id="deleteConfirmMessage" style="margin-bottom: 24px; color: #666;">确定要删除这个过滤器吗？此操作不可恢复。</p>
                <div style="display: flex; gap: 10px; justify-content: flex-end;">
                    <button id="deleteConfirmCancel" style="padding: 8px 16px; background: #ccc; color: white; border: none; border-radius: 4px; cursor: pointer;">取消</button>
                    <button id="deleteConfirmOk" style="padding: 8px 16px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">确认删除</button>
                </div>
            </div>
        </div>
    `;
    $('#item-content').html(tHtmlFilter(html));
    loadFilterConfigs();
}

function loadFilterConfigs() {
    $.ajax({
        type: "GET",
        contentType: "application/json;charset=utf-8",
        url: "filterConfig/list",
        success: function(response) {
            if (response.code === 0) {
                filterConfigData = response.data || [];
                filteredConfigData = filterConfigData;
                renderFilterConfigList();
            }
        },
        error: function() {
            console.error("获取配置失败");
        }
    });
}

function filterConfigList() {
    const searchText = $('#searchInput').val().toLowerCase();
    const filterType = $('#filterType').val();

    filteredConfigData = filterConfigData.filter(filter => {
        const matchSearch = !searchText || (filter.name && filter.name.toLowerCase().includes(searchText));
        const matchType = !filterType || filter.name === filterType;
        return matchSearch && matchType;
    });

    renderFilterConfigList();
}

function renderFilterConfigList() {
    const container = $('#filterConfigList');
    container.empty();

    if (filteredConfigData.length === 0 && filterConfigData.length > 0) {
        container.html(`<div style="text-align: center; padding: 40px; color: #999;">${tTextFilter('未找到匹配的过滤器')}</div>`);
        return;
    }

    if (filterConfigData.length === 0) {
        container.html(`<div style="text-align: center; padding: 40px; color: #999;">${tTextFilter('暂无过滤器配置，请点击“新增过滤器”添加')}</div>`);
        return;
    }

    const dataToRender = filteredConfigData.length > 0 ? filteredConfigData : filterConfigData;

    dataToRender.forEach((filter, index) => {
        const actualIndex = filterConfigData.findIndex(f => String(f.id || '') === String(filter.id || ''));
        const filterName = filter.name || '';
        const typeMeta = getFilterTypeMeta(filterName);
        const typeLabel = typeMeta.label ? `${tTextFilter(typeMeta.label)} (${filterName})` : filterName;
        const typeSummaryHtml = typeMeta.summary
            ? `<div style="margin-top: 6px; color: #666; font-size: 13px; line-height: 1.5;">${escapeHtmlFilter(tTextFilter(typeMeta.summary))}</div>`
            : '';
        const safeFilterId = escapeJsStringFilter(filter.id || '');
        const safeDeleteName = escapeJsStringFilter(filterName);
        const idBadge = filter.id ? `<span style="font-size: 12px; color: #777; font-weight: normal; margin-left: 8px;">#${escapeHtmlFilter(filter.id)}</span>` : '';
        const groupsHtml = filter.groups ? renderGroups(filter.groups) : '';
        const rulesHtml = filter.rules ? `<div style="margin-top: 12px;"><strong>规则:</strong> <div style="margin-top: 8px; padding: 8px; background: #fff; border: 1px solid #eee; border-radius: 4px; white-space: pre-wrap;">${escapeHtmlFilter(filter.rules)}</div></div>` : '';
        const card = `
            <div style="background: white; border-radius: 8px; padding: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                    <div>
                        <h3 style="margin: 0;">${escapeHtmlFilter(typeLabel)}${idBadge}</h3>
                        ${typeSummaryHtml}
                    </div>
                    <div>
                        <button onclick="editFilterConfig(${actualIndex >= 0 ? actualIndex : index})" style="padding: 6px 12px; background: #1296db; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 8px;">编辑</button>
                        <button onclick="deleteFilterConfig('${safeFilterId}', '${safeDeleteName}')" style="padding: 6px 12px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">删除</button>
                    </div>
                </div>
                ${groupsHtml}
                ${rulesHtml}
            </div>
        `;
        container.append(tHtmlFilter(card));
    });
}

function renderGroups(groups) {
    if (!groups || groups.length === 0) return '';
    let html = '<div style="margin-top: 12px;"><strong>分组:</strong><div style="margin-top: 8px;">';
    groups.forEach(group => {
        html += `<div style="padding: 8px; background: #fff; border: 1px solid #eee; border-radius: 4px; margin-bottom: 8px;">
            <div><strong>级别:</strong> ${escapeHtmlFilter(group.level || '')}</div>
            <div style="margin-top: 4px;"><strong>规则:</strong> <div style="margin-top: 4px; white-space: pre-wrap;">${escapeHtmlFilter(group.rules || '')}</div></div>
        </div>`;
    });
    html += '</div></div>';
    return tHtmlFilter(html);
}

let currentEditIndex = -1;
let currentEditId = null;

function showAddFilterDialog() {
    currentEditIndex = -1;
    currentEditId = null;
    $('#modalTitle').text(tTextFilter('新增过滤器'));
    $('#filterName').val('');
    $('#filterName').prop('disabled', false);
    $('#filterRules').val('');
    $('#groupsContainer').empty();
    onFilterTypeChange();
    $('#filterModal').css('display', 'flex');
}

function onFilterTypeChange() {
    const filterType = $('#filterName').val();
    const groupsContainer = $('#groupsContainer');
    const rulesContainer = $('#rulesContainer');
    const typeMeta = getFilterTypeMeta(filterType);

    $('#filterTypeSummary').text(typeMeta.summary ? tTextFilter(typeMeta.summary) : tTextFilter('注意：只能选择以上系统支持的过滤器类型，自定义名称不会生效'));
    $('#filterRuleHint').text(typeMeta.ruleHint ? tTextFilter(typeMeta.ruleHint) : tTextFilter('提示：支持英文逗号、中文逗号、顿号、分号或换行分隔，支持正则表达式。如果是敏感词过滤器，请在“分组”中配置级别和规则。'));

    if (currentEditIndex < 0 && isSensitiveFilter(filterType)) {
        if (groupsContainer.find('.group-container').length === 0) {
            groupsContainer.html(tHtmlFilter(`
                <label style="display: block; margin-bottom: 8px;">分组配置 (敏感词需要配置级别和规则):</label>
                <div class="group-container" style="border: 1px solid #ddd; border-radius: 4px; padding: 12px; margin-bottom: 12px;">
                    <div style="margin-bottom: 8px;">
                        <label style="display: block; margin-bottom: 4px;">级别 (1=删除, 2=掩码, 3=擦除):</label>
                        <input type="number" class="group-level" min="1" max="3" value="2" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px;" placeholder="1, 2, 或 3" />
                    </div>
                    <div>
                        <label style="display: block; margin-bottom: 4px;">规则 (支持逗号、顿号、分号或换行分隔):</label>
                        <textarea class="group-rules" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px; min-height: 60px;" placeholder="例如: 维尼熊,敏感词,规则*"></textarea>
                    </div>
                </div>
                <button type="button" onclick="addGroup()" style="padding: 6px 12px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-bottom: 8px;">添加分组</button>
            `));
        }
        rulesContainer.hide();
    } else if (isSensitiveFilter(filterType)) {
        rulesContainer.hide();
    } else {
        groupsContainer.empty();
        rulesContainer.show();
    }
}

function addGroup() {
    const groupsContainer = $('#groupsContainer');
    const newGroup = $(tHtmlFilter(`
        <div class="group-container" style="border: 1px solid #ddd; border-radius: 4px; padding: 12px; margin-bottom: 12px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                <label style="display: block; margin-bottom: 4px;">级别 (1=删除, 2=掩码, 3=擦除):</label>
                <button type="button" onclick="$(this).closest('.group-container').remove()" style="padding: 4px 8px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">删除</button>
            </div>
            <div style="margin-bottom: 8px;">
                <input type="number" class="group-level" min="1" max="3" value="2" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px;" placeholder="1, 2, 或 3" />
            </div>
            <div>
                <label style="display: block; margin-bottom: 4px;">规则 (支持逗号、顿号、分号或换行分隔):</label>
                <textarea class="group-rules" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px; min-height: 60px;" placeholder="例如: 维尼熊,敏感词,规则*"></textarea>
            </div>
        </div>
    `));
    groupsContainer.find('button[onclick="addGroup()"]').before(newGroup);
}

function editFilterConfig(index) {
    if (index < 0 || index >= filterConfigData.length) {
        alert(tTextFilter('过滤器不存在'));
        return;
    }
    currentEditIndex = index;
    const filter = filterConfigData[index];
    currentEditId = filter.id || null;
    $('#modalTitle').text(tTextFilter('编辑过滤器'));
    $('#filterName').val(filter.name || '');
    $('#filterName').prop('disabled', true);
    $('#filterRules').val(filter.rules || '');

    const groupsContainer = $('#groupsContainer');
    groupsContainer.empty();
    if (filter.groups && filter.groups.length > 0) {
        groupsContainer.append(tHtmlFilter('<label style="display: block; margin-bottom: 8px;">分组配置:</label>'));
        filter.groups.forEach(group => {
            const groupDiv = `
                <div class="group-container" style="border: 1px solid #ddd; border-radius: 4px; padding: 12px; margin-bottom: 12px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                        <label style="display: block; margin-bottom: 4px;">级别 (1=删除, 2=掩码, 3=擦除):</label>
                        <button type="button" onclick="$(this).closest('.group-container').remove()" style="padding: 4px 8px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">删除</button>
                    </div>
                    <div style="margin-bottom: 8px;">
                        <input type="number" class="group-level" min="1" max="3" value="${escapeHtmlFilter(group.level || '2')}" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px;" />
                    </div>
                    <div>
                        <label style="display: block; margin-bottom: 4px;">规则 (支持逗号、顿号、分号或换行分隔):</label>
                        <textarea class="group-rules" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px; min-height: 60px;">${escapeHtmlFilter(group.rules || '')}</textarea>
                    </div>
                </div>
            `;
            groupsContainer.append(tHtmlFilter(groupDiv));
        });
        if (isSensitiveFilter(filter.name)) {
            groupsContainer.append(tHtmlFilter('<button type="button" onclick="addGroup()" style="padding: 6px 12px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-bottom: 8px;">添加分组</button>'));
        }
    } else if (isSensitiveFilter(filter.name)) {
        groupsContainer.append(tHtmlFilter('<label style="display: block; margin-bottom: 8px;">分组配置:</label>'));
        const groupDiv = `
            <div class="group-container" style="border: 1px solid #ddd; border-radius: 4px; padding: 12px; margin-bottom: 12px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                    <label style="display: block; margin-bottom: 4px;">级别 (1=删除, 2=掩码, 3=擦除):</label>
                    <button type="button" onclick="$(this).closest('.group-container').remove()" style="padding: 4px 8px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">删除</button>
                </div>
                <div style="margin-bottom: 8px;">
                    <input type="number" class="group-level" min="1" max="3" value="2" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px;" />
                </div>
                <div>
                    <label style="display: block; margin-bottom: 4px;">规则 (支持逗号、顿号、分号或换行分隔):</label>
                    <textarea class="group-rules" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 4px; min-height: 60px;" placeholder="例如: 维尼熊,敏感词,规则*"></textarea>
                </div>
            </div>
        `;
        groupsContainer.append(tHtmlFilter(groupDiv));
        groupsContainer.append(tHtmlFilter('<button type="button" onclick="addGroup()" style="padding: 6px 12px; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-bottom: 8px;">添加分组</button>'));
    }

    if (isSensitiveFilter(filter.name)) {
        $('#rulesContainer').hide();
    } else {
        $('#rulesContainer').show();
    }

    onFilterTypeChange();
    $('#filterModal').css('display', 'flex');
}

function hideFilterModal() {
    $('#filterModal').css('display', 'none');
}

function isSensitiveFilter(name) {
    return name === 'sensitive' || name === 'sensitive_input';
}

function saveFilterConfig() {
    const name = $('#filterName').val().trim();
    const rules = $('#filterRules').val().trim();

    if (!name) {
        alert(tTextFilter('请选择过滤器类型'));
        return;
    }

    const validTypes = ['sensitive', 'sensitive_input', 'priority', 'stopping', 'continue'];
    if (!validTypes.includes(name)) {
        alert(tTextFilter('过滤器类型只能是: sensitive_input(输入敏感词)、sensitive(输出敏感词)、priority(优先级)、stopping(停止词)、continue(继续词)'));
        return;
    }

    const filter = {
        name: name,
        rules: rules || null
    };
    if (currentEditId) {
        filter.id = currentEditId;
    }

    const groups = [];
    $('.group-level').each(function() {
        const level = $(this).val().trim();
        const rulesText = $(this).closest('.group-container').find('.group-rules').val().trim();
        if (level && rulesText) {
            groups.push({
                level: level,
                rules: rulesText
            });
        }
    });

    if (groups.length > 0) {
        filter.groups = groups;
    }

    const url = currentEditIndex >= 0 ? 'filterConfig/update' : 'filterConfig/add';
    const method = 'POST';
    const isEdit = currentEditIndex >= 0;

    $.ajax({
        type: method,
        contentType: "application/json;charset=utf-8",
        url: url,
        data: JSON.stringify(filter),
        success: function(response) {
            if (response && response.code === 0) {
                hideFilterModal();
                loadFilterConfigs();
                alert(isEdit ? tTextFilter('编辑成功') : tTextFilter('保存成功'));
            } else {
                alert(tTextFilter('保存失败') + ': ' + getFilterConfigErrorMessage(response));
            }
        },
        error: function(xhr, status, error) {
            let errorMsg = tTextFilter('保存失败');
            if (xhr.responseJSON) {
                errorMsg += ': ' + getFilterConfigErrorMessage(xhr.responseJSON);
            } else if (xhr.responseText) {
                try {
                    const errorJson = JSON.parse(xhr.responseText);
                    errorMsg += ': ' + getFilterConfigErrorMessage(errorJson);
                } catch (e) {
                    errorMsg += ': ' + xhr.responseText.substring(0, 200);
                }
            }
            alert(errorMsg);
            console.error('保存过滤器配置失败:', xhr, status, error);
        }
    });
}

let deleteConfirmId = null;
let deleteConfirmName = null;

function deleteFilterConfig(id, name) {
    if (!id) {
        alert(tTextFilter('过滤器不存在'));
        return;
    }
    deleteConfirmId = id;
    deleteConfirmName = name;
    $('#deleteConfirmMessage').text(tTextFilter('确定要删除过滤器 "') + name + tTextFilter('" 吗？此操作不可恢复。'));
    $('#deleteConfirmModal').css('display', 'flex');

    $('#deleteConfirmCancel').off('click');
    $('#deleteConfirmOk').off('click');

    $('#deleteConfirmCancel').on('click', function() {
        $('#deleteConfirmModal').css('display', 'none');
        deleteConfirmId = null;
        deleteConfirmName = null;
    });

    $('#deleteConfirmOk').on('click', function() {
        const idToDelete = deleteConfirmId;
        $('#deleteConfirmModal').css('display', 'none');
        deleteConfirmId = null;
        deleteConfirmName = null;

        if (idToDelete) {
            performDelete(idToDelete);
        }
    });
}

function performDelete(id) {
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "filterConfig/delete",
        data: JSON.stringify({id: id}),
        success: function(response) {
            if (response.code === 0) {
                loadFilterConfigs();
                alert(tTextFilter('删除成功'));
            } else {
                alert(tTextFilter('删除失败') + ': ' + getFilterConfigErrorMessage(response));
            }
        },
        error: function(xhr, status, error) {
            let errorMsg = tTextFilter('删除失败');
            if (xhr.responseJSON) {
                errorMsg += ': ' + getFilterConfigErrorMessage(xhr.responseJSON);
            } else if (xhr.responseText) {
                try {
                    const errorJson = JSON.parse(xhr.responseText);
                    errorMsg += ': ' + getFilterConfigErrorMessage(errorJson);
                } catch (e) {
                    errorMsg += ': ' + xhr.responseText.substring(0, 200);
                }
            }
            alert(errorMsg);
            console.error('删除过滤器配置失败:', xhr, status, error);
        }
    });
}
