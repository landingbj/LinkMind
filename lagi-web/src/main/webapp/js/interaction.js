const isMateMode = true; // TODO: 预留给后续后端接口判断
window.isMateMode = isMateMode;

const tTextInteraction = window.tText || ((text) => text);
const tHtmlInteraction = window.tHtml || ((html) => html);

const interactionState = {
    subscribeSearch: '',
    publishSearch: '',
    recommendedChannels: [
        {
            id: 'job',
            tag: '#上海招聘',
            description: '聚合本地岗位、内推消息和面试反馈。',
            followers: '2.4k 人关注',
            joined: true,
            joinedInfo: {
                name: '上海招聘',
                latest: '发布信息：浦东产品经理岗位已更新'
            }
        },
        {
            id: 'study',
            tag: '#留学情报',
            description: '查看申请节奏、文书经验和院校资讯。',
            followers: '1.8k 人关注',
            joined: false,
            joinedInfo: {
                name: '留学情报',
                latest: '发布信息：英国秋季申请时间线整理'
            }
        },
        {
            id: 'dating',
            tag: '#相亲同城',
            description: '浏览同城活动、破冰话题和线下见面信息。',
            followers: '1.2k 人关注',
            joined: false,
            joinedInfo: {
                name: '相亲同城',
                latest: '发布信息：本周六静安下午茶活动'
            }
        },
        {
            id: 'rental',
            tag: '#租房互助',
            description: '获取转租、合租和租房提醒信息。',
            followers: '920 人关注',
            joined: true,
            joinedInfo: {
                name: '租房互助',
                latest: '发布信息：徐汇单间转租今晚更新'
            }
        }
    ],
    publishChannels: [
        { id: 'channel-job', name: '#上海招聘', status: '已上线', owner: '频道管理员 A' },
        { id: 'channel-study', name: '#留学情报', status: '待完善', owner: '频道管理员 B' },
        { id: 'channel-dating', name: '#相亲同城', status: '草稿中', owner: '频道管理员 C' }
    ]
};

let interactionNoticeTimer = 0;

function escapeInteractionHtml(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function normalizeInteractionText(value) {
    return String(value == null ? '' : value).trim().toLowerCase();
}

function prepareInteractionPage() {
    $('#conTab').show();
    $('#mytab').hide();
    $('#queryBox').hide();
    $('#footer-info').hide();
    $('#introduces').hide();
    $('#topTitle').hide();
    $('#model-selects').empty();
    $('#model-prefences').hide();
    $('#item-content').show();
    $('#item-content').css('height', 'calc(100vh - 60px)');
    $('#item-content').css('overflow-y', 'auto');
    document.body.classList.remove('home-mode');
    if (typeof hideBallDiv === 'function') {
        hideBallDiv();
    }
}

function showInteractionNotice(message) {
    const notice = $('#interactionPageNotice');
    if (!notice.length) {
        return;
    }
    clearTimeout(interactionNoticeTimer);
    notice.text(message);
    notice.addClass('is-visible');
    interactionNoticeTimer = setTimeout(function () {
        notice.removeClass('is-visible');
    }, 1800);
}

function getFilteredRecommendedChannels() {
    const keyword = normalizeInteractionText(interactionState.subscribeSearch);
    if (!keyword) {
        return interactionState.recommendedChannels;
    }
    return interactionState.recommendedChannels.filter(function (channel) {
        const matchText = [
            channel.tag,
            channel.description,
            channel.followers,
            channel.joinedInfo.name,
            channel.joinedInfo.latest
        ].join(' ');
        return normalizeInteractionText(matchText).indexOf(keyword) !== -1;
    });
}

function getFilteredJoinedChannels() {
    return getFilteredRecommendedChannels()
        .filter(function (channel) {
            return channel.joined;
        })
        .map(function (channel) {
            return channel.joinedInfo;
        });
}

function buildRecommendedChannelsHtml(channels) {
    if (!channels.length) {
        return `<div class="interaction-empty-state">${tTextInteraction('没有匹配到推荐频道')}</div>`;
    }

    return channels.map(function (channel) {
        const actionClass = channel.joined ? 'interaction-btn interaction-btn-secondary' : 'interaction-btn interaction-btn-primary';
        const actionLabel = channel.joined ? tTextInteraction('已加入') : tTextInteraction('加入');
        const disabledAttr = channel.joined ? 'disabled="disabled"' : '';
        return `
            <article class="interaction-card">
                <div class="interaction-card__tag">${escapeInteractionHtml(channel.tag)}</div>
                <p class="interaction-card__desc">${escapeInteractionHtml(channel.description)}</p>
                <div class="interaction-card__meta">
                    <span>${escapeInteractionHtml(channel.followers)}</span>
                    <button type="button" class="${actionClass} interaction-join-btn" data-channel-id="${escapeInteractionHtml(channel.id)}" ${disabledAttr}>${actionLabel}</button>
                </div>
            </article>
        `;
    }).join('');
}

function buildJoinedChannelsRowsHtml(channels) {
    if (!channels.length) {
        return `
            <tr>
                <td colspan="3" class="interaction-table__empty">${tTextInteraction('当前没有匹配到已加入频道')}</td>
            </tr>
        `;
    }

    return channels.map(function (channel) {
        return `
            <tr>
                <td>${escapeInteractionHtml(channel.name)}</td>
                <td>${escapeInteractionHtml(channel.latest)}</td>
                <td>
                    <button type="button" class="interaction-btn interaction-btn-secondary interaction-leave-btn" data-channel-name="${escapeInteractionHtml(channel.name)}">${tTextInteraction('退出')}</button>
                </td>
            </tr>
        `;
    }).join('');
}

function updateInteractionSubscribeView() {
    const filteredRecommended = getFilteredRecommendedChannels();
    const filteredJoined = getFilteredJoinedChannels();
    $('#interactionRecommendedGrid').html(buildRecommendedChannelsHtml(filteredRecommended));
    $('#interactionJoinedTableBody').html(buildJoinedChannelsRowsHtml(filteredJoined));

    $('.interaction-join-btn').off('click').on('click', function () {
        const channelId = String($(this).data('channel-id') || '');
        const targetChannel = interactionState.recommendedChannels.find(function (channel) {
            return channel.id === channelId;
        });
        if (!targetChannel || targetChannel.joined) {
            return;
        }
        targetChannel.joined = true;
        updateInteractionSubscribeView();
        showInteractionNotice(`已加入 ${targetChannel.joinedInfo.name}`);
    });

    $('.interaction-leave-btn').off('click').on('click', function () {
        const channelName = String($(this).data('channel-name') || '');
        const targetChannel = interactionState.recommendedChannels.find(function (channel) {
            return channel.joinedInfo && channel.joinedInfo.name === channelName;
        });
        if (!targetChannel) {
            return;
        }
        targetChannel.joined = false;
        updateInteractionSubscribeView();
        showInteractionNotice(`已退出 ${channelName}`);
    });
}

function renderInteractionSubscribePage() {
    prepareInteractionPage();

    const html = `
        <div id="interactionPage" class="interaction-page">
            <div id="interactionPageNotice" class="interaction-page-notice"></div>
            <div class="interaction-toolbar">
                <input
                    id="interactionSubscribeSearch"
                    class="interaction-search-input"
                    type="search"
                    value="${escapeInteractionHtml(interactionState.subscribeSearch)}"
                    placeholder="${escapeInteractionHtml(tTextInteraction('搜索频道、关键词或最新消息'))}"
                />
            </div>

            <section class="interaction-section">
                <div class="interaction-section__head">
                    <div>
                        <h2>${tTextInteraction('推荐频道')}</h2>
                        <p>${tTextInteraction('发现你可能感兴趣的频道')}</p>
                    </div>
                </div>
                <div id="interactionRecommendedGrid" class="interaction-card-grid"></div>
            </section>

            <section class="interaction-section">
                <div class="interaction-section__head">
                    <div>
                        <h2>${tTextInteraction('我加入的频道')}</h2>
                        <p>${tTextInteraction('查看已加入频道的最新动态')}</p>
                    </div>
                </div>
                <div class="interaction-table-wrap">
                    <table class="interaction-table">
                        <thead>
                            <tr>
                                <th>${tTextInteraction('名称')}</th>
                                <th>${tTextInteraction('最新信息')}</th>
                                <th>${tTextInteraction('操作')}</th>
                            </tr>
                        </thead>
                        <tbody id="interactionJoinedTableBody"></tbody>
                    </table>
                </div>
            </section>
        </div>
    `;

    $('#item-content').html(tHtmlInteraction(html));
    $('#interactionSubscribeSearch').on('input', function () {
        interactionState.subscribeSearch = $(this).val() || '';
        updateInteractionSubscribeView();
    });
    updateInteractionSubscribeView();
}

function getFilteredPublishChannels() {
    const keyword = normalizeInteractionText(interactionState.publishSearch);
    if (!keyword) {
        return interactionState.publishChannels;
    }
    return interactionState.publishChannels.filter(function (channel) {
        const matchText = [channel.name, channel.status, channel.owner].join(' ');
        return normalizeInteractionText(matchText).indexOf(keyword) !== -1;
    });
}

function buildPublishChannelsHtml(channels) {
    if (!channels.length) {
        return `<div class="interaction-empty-state">${tTextInteraction('没有匹配到可管理频道')}</div>`;
    }

    return channels.map(function (channel) {
        return `
            <div class="interaction-manage-row">
                <div>
                    <div class="interaction-manage-row__title">${escapeInteractionHtml(channel.name)}</div>
                    <div class="interaction-manage-row__meta">${escapeInteractionHtml(channel.owner)} · ${escapeInteractionHtml(channel.status)}</div>
                </div>
                <div class="interaction-manage-actions">
                    <button type="button" class="interaction-btn interaction-btn-secondary interaction-disable-btn" data-channel-name="${escapeInteractionHtml(channel.name)}">${tTextInteraction('停用')}</button>
                    <button type="button" class="interaction-btn interaction-btn-secondary interaction-delete-btn" data-channel-name="${escapeInteractionHtml(channel.name)}">${tTextInteraction('删除')}</button>
                </div>
            </div>
        `;
    }).join('');
}

function updateInteractionPublishView() {
    $('#interactionPublishList').html(buildPublishChannelsHtml(getFilteredPublishChannels()));
    $('.interaction-disable-btn').off('click').on('click', function () {
        const channelName = String($(this).data('channel-name') || '');
        showInteractionNotice(`${channelName} 暂不支持停用`);
    });
    $('.interaction-delete-btn').off('click').on('click', function () {
        const channelName = String($(this).data('channel-name') || '');
        showInteractionNotice(`${channelName} 暂不支持删除`);
    });
}

function renderInteractionPublishPage() {
    prepareInteractionPage();

    const html = `
        <div id="interactionPage" class="interaction-page">
            <div id="interactionPageNotice" class="interaction-page-notice"></div>
            <div class="interaction-toolbar">
                <input
                    id="interactionPublishSearch"
                    class="interaction-search-input"
                    type="search"
                    value="${escapeInteractionHtml(interactionState.publishSearch)}"
                    placeholder="${escapeInteractionHtml(tTextInteraction('搜索频道名称'))}"
                />
            </div>

            <section class="interaction-section">
                <div class="interaction-section__head">
                    <div>
                        <h2>${tTextInteraction('频道管理')}</h2>
                        <p>${tTextInteraction('创建你自己的频道')}</p>
                    </div>
                </div>
                <div class="interaction-action-grid">
                    <button type="button" class="interaction-action-card" id="interactionCreateChannel">
                        <span class="interaction-action-card__label">#${tTextInteraction('创建')}</span>
                        <strong>${tTextInteraction('创建频道')}</strong>
                        <p>${tTextInteraction('填写频道信息后即可发起创建')}</p>
                    </button>
                </div>
            </section>

            <section class="interaction-section">
                <div class="interaction-section__head">
                    <div>
                        <h2>${tTextInteraction('管理列表')}</h2>
                        <p>${tTextInteraction('可对现有频道进行停用或删除')}</p>
                    </div>
                </div>
                <div id="interactionPublishList" class="interaction-manage-list"></div>
            </section>
        </div>
    `;

    $('#item-content').html(tHtmlInteraction(html));
    $('#interactionPublishSearch').on('input', function () {
        interactionState.publishSearch = $(this).val() || '';
        updateInteractionPublishView();
    });
    $('#interactionCreateChannel').on('click', function () {
        showInteractionNotice('暂不支持创建频道');
    });
    updateInteractionPublishView();
}

window.openInteractionPage = function openInteractionPage(navId, subNavId) {
    const subNav = typeof getSubNav === 'function' ? getSubNav(navId, subNavId) : null;
    if (!subNav || subNav.disabled) {
        return;
    }
    if (typeof setLeafNavActiveByNavId === 'function') {
        setLeafNavActiveByNavId(subNavId);
    }
    if (subNav.key === 'interactionPublish') {
        renderInteractionPublishPage();
        return;
    }
    renderInteractionSubscribePage();
};
