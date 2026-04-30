const tTextInteraction = window.tText || ((text) => text);
const tHtmlInteraction = window.tHtml || ((html) => html);
const SOCIAL_CHANNEL_API_BASE = '/socialChannel';

const interactionState = {
    userId: '',
    username: '',
    recommendedChannels: [],
    publishChannels: [],
    initPromise: null,
    subscribeLoading: false,
    publishLoading: false
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

function interactionActionNotice(action, channelName) {
    return `${tTextInteraction(action)} ${channelName}`;
}

function interactionCreateSuccessNotice(channelName) {
    const currentLang = typeof window.getCurrentLang === 'function' ? window.getCurrentLang() : '';
    if (currentLang === 'en-US') {
        return `Channel ${channelName} created successfully`;
    }
    return `频道 ${channelName} 创建成功`;
}

function getInteractionUserId() {
    const fromCookie = typeof getCookie === 'function' ? (getCookie('userId') || '') : '';
    if (fromCookie) {
        return fromCookie;
    }
    try {
        return localStorage.getItem('userId') || '';
    } catch (error) {
        return '';
    }
}

function getInteractionUsername() {
    const fromDom = $('#user_box').text() || '';
    const normalized = String(fromDom).trim();
    if (normalized) {
        return normalized;
    }
    return interactionState.userId || tTextInteraction('LinkMind 用户');
}

function interactionAjax(options) {
    return new Promise(function (resolve, reject) {
        $.ajax({
            type: options.type || 'GET',
            contentType: options.contentType || 'application/json;charset=utf-8',
            url: options.url,
            data: options.data,
            success: function (res) {
                if (res && res.status === 'success') {
                    resolve(res);
                    return;
                }
                reject(new Error((res && res.msg) || 'request failed'));
            },
            error: function (xhr) {
                const responseMsg = xhr && xhr.responseJSON && xhr.responseJSON.msg;
                reject(new Error(responseMsg || 'network error'));
            }
        });
    });
}

function interactionGet(path, params) {
    return interactionAjax({
        type: 'GET',
        contentType: 'application/x-www-form-urlencoded; charset=UTF-8',
        url: `${SOCIAL_CHANNEL_API_BASE}/${path}`,
        data: params || {}
    });
}

function interactionPost(path, body) {
    return interactionAjax({
        type: 'POST',
        url: `${SOCIAL_CHANNEL_API_BASE}/${path}`,
        data: JSON.stringify(body || {})
    });
}

async function ensureInteractionUserReady() {
    if (!interactionState.userId) {
        interactionState.userId = getInteractionUserId();
    }
    if (!interactionState.userId) {
        throw new Error(tTextInteraction('请先登录后再使用频道功能'));
    }
    if (!interactionState.username) {
        interactionState.username = getInteractionUsername();
    }
    await interactionPost('registerUser', {
        userId: interactionState.userId,
        username: interactionState.username
    });
    try {
        await interactionPost('saveLastLoginUser', {
            userId: interactionState.userId,
            username: interactionState.username
        });
    } catch (error) {
        // Ignore temp-save failure because user registration has succeeded.
    }
}

function toRecommendedChannel(channel, joinedMap) {
    const channelId = channel && channel.id != null ? String(channel.id) : '';
    const joined = !!joinedMap[channelId];
    const rawName = channel && channel.name ? channel.name : '';
    const normalizedName = rawName.indexOf('#') === 0 ? rawName.substring(1) : rawName;
    const latestText = channel && channel.description ? channel.description : tTextInteraction('暂无频道介绍');
    const defaultChannelName = tTextInteraction('未命名频道');
    return {
        id: channelId,
        tag: `#${normalizedName || defaultChannelName}`,
        description: latestText,
        followers: channel && channel.isPublic ? tTextInteraction('公开频道') : tTextInteraction('私有频道'),
        joined: joined,
        joinedInfo: {
            id: channelId,
            name: normalizedName || defaultChannelName,
            latest: latestText
        }
    };
}

async function loadInteractionSubscribeData() {
    await ensureInteractionUserReady();
    const responses = await Promise.all([
        interactionGet('listPublicChannels', { limit: 100 }),
        interactionGet('listMyChannels', { userId: interactionState.userId })
    ]);
    const publicChannels = (responses[0] && responses[0].data) || [];
    const myChannels = (responses[1] && responses[1].data) || [];
    const joinedMap = {};
    myChannels.forEach(function (channel) {
        if (channel && channel.id != null) {
            joinedMap[String(channel.id)] = channel;
        }
    });
    interactionState.recommendedChannels = publicChannels.map(function (channel) {
        return toRecommendedChannel(channel, joinedMap);
    });
}

async function loadInteractionPublishData() {
    await ensureInteractionUserReady();
    const res = await interactionGet('listOwnedChannels', { userId: interactionState.userId });
    const channels = (res && res.data) || [];
    interactionState.publishChannels = channels.map(function (channel) {
        const rawName = channel && channel.name ? channel.name : '';
        const normalizedName = rawName.indexOf('#') === 0 ? rawName : `#${rawName}`;
        return {
            id: channel && channel.id != null ? String(channel.id) : '',
            name: normalizedName,
            status: channel && channel.enabled ? tTextInteraction('已启用') : tTextInteraction('已停用'),
            owner: tTextInteraction('我创建的频道'),
            enabled: !!(channel && channel.enabled)
        };
    });
}

function prepareInteractionPage() {
    $('#conTab').show();
    $('#mytab').hide();
    $('#queryBox').hide();
    $('#footer-info').hide();
    $('#not-content').hide();
    $('#introduces').hide();
    $('#topTitle').hide();
    $('#model-selects').empty();
    $('#model-prefences').hide();
    $('#item-content').show();
    $('#item-content').css('height', 'calc(100vh - 60px)');
    $('#item-content').css('overflow-y', 'auto');
    document.body.classList.remove('home-mode');
    document.body.classList.add('interaction-mode');
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
    notice.text(tTextInteraction(message));
    notice.addClass('is-visible');
    interactionNoticeTimer = setTimeout(function () {
        notice.removeClass('is-visible');
    }, 1800);
}

function getFilteredJoinedChannels() {
    return interactionState.recommendedChannels
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
        return `
            <article class="interaction-card">
                <div class="interaction-card__tag">${escapeInteractionHtml(channel.tag)}</div>
                <p class="interaction-card__desc">${escapeInteractionHtml(channel.description)}</p>
                <div class="interaction-card__meta">
                    <span>${escapeInteractionHtml(channel.followers)}</span>
                    <button type="button" class="${actionClass} interaction-join-btn" data-channel-id="${escapeInteractionHtml(channel.id)}">${actionLabel}</button>
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
    const filteredRecommended = interactionState.recommendedChannels;
    const filteredJoined = getFilteredJoinedChannels();
    $('#interactionRecommendedGrid').html(buildRecommendedChannelsHtml(filteredRecommended));
    $('#interactionJoinedTableBody').html(buildJoinedChannelsRowsHtml(filteredJoined));

    $('.interaction-join-btn').off('click').on('click', async function () {
        const channelId = String($(this).data('channel-id') || '');
        const targetChannel = interactionState.recommendedChannels.find(function (channel) {
            return channel.id === channelId;
        });
        if (!targetChannel || interactionState.subscribeLoading) {
            return;
        }
        interactionState.subscribeLoading = true;
        try {
            if (targetChannel.joined) {
                await interactionPost('unsubscribe', {
                    userId: interactionState.userId,
                    channelId: Number(channelId)
                });
            } else {
                await interactionPost('subscribe', {
                    userId: interactionState.userId,
                    channelId: Number(channelId)
                });
            }
            await loadInteractionSubscribeData();
            updateInteractionSubscribeView();
            showInteractionNotice(targetChannel.joined
                ? interactionActionNotice('已退出', targetChannel.joinedInfo.name)
                : interactionActionNotice('已加入', targetChannel.joinedInfo.name));
        } catch (error) {
            showInteractionNotice(error.message || tTextInteraction('频道操作失败'));
        } finally {
            interactionState.subscribeLoading = false;
        }
    });

    $('.interaction-leave-btn').off('click').on('click', async function () {
        const channelName = String($(this).data('channel-name') || '');
        const targetChannel = interactionState.recommendedChannels.find(function (channel) {
            return channel.joinedInfo && channel.joinedInfo.name === channelName;
        });
        if (!targetChannel || interactionState.subscribeLoading) {
            return;
        }
        interactionState.subscribeLoading = true;
        try {
            await interactionPost('unsubscribe', {
                userId: interactionState.userId,
                channelId: Number(targetChannel.id)
            });
            await loadInteractionSubscribeData();
            updateInteractionSubscribeView();
            showInteractionNotice(interactionActionNotice('已退出', channelName));
        } catch (error) {
            showInteractionNotice(error.message || tTextInteraction('退出频道失败'));
        } finally {
            interactionState.subscribeLoading = false;
        }
    });
}

async function renderInteractionSubscribePage() {
    prepareInteractionPage();

    const html = `
        <div id="interactionPage" class="interaction-page">
            <div id="interactionPageNotice" class="interaction-page-notice"></div>

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
    try {
        await loadInteractionSubscribeData();
    } catch (error) {
        showInteractionNotice(error.message || tTextInteraction('加载频道失败'));
    }
    updateInteractionSubscribeView();
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
                    <button type="button" class="interaction-btn interaction-btn-secondary interaction-disable-btn" data-channel-id="${escapeInteractionHtml(channel.id)}" data-channel-name="${escapeInteractionHtml(channel.name)}">${channel.enabled ? tTextInteraction('停用') : tTextInteraction('启用')}</button>
                    <button type="button" class="interaction-btn interaction-btn-secondary interaction-delete-btn" data-channel-id="${escapeInteractionHtml(channel.id)}" data-channel-name="${escapeInteractionHtml(channel.name)}">${tTextInteraction('删除')}</button>
                </div>
            </div>
        `;
    }).join('');
}

function updateInteractionPublishView() {
    $('#interactionPublishList').html(buildPublishChannelsHtml(interactionState.publishChannels));
    $('.interaction-disable-btn').off('click').on('click', async function () {
        if (interactionState.publishLoading) {
            return;
        }
        const channelId = String($(this).data('channel-id') || '');
        const channelName = String($(this).data('channel-name') || '');
        if (!channelId) {
            return;
        }
        const target = interactionState.publishChannels.find(function (channel) {
            return channel.id === channelId;
        });
        if (!target) {
            return;
        }
        interactionState.publishLoading = true;
        try {
            await interactionPost('toggleChannel', {
                userId: interactionState.userId,
                channelId: Number(channelId),
                enabled: !target.enabled
            });
            await Promise.all([loadInteractionPublishData(), loadInteractionSubscribeData()]);
            updateInteractionPublishView();
            showInteractionNotice(target.enabled
                ? interactionActionNotice('已停用', channelName)
                : interactionActionNotice('已启用', channelName));
        } catch (error) {
            showInteractionNotice(error.message || tTextInteraction('状态切换失败'));
        } finally {
            interactionState.publishLoading = false;
        }
    });
    $('.interaction-delete-btn').off('click').on('click', async function () {
        if (interactionState.publishLoading) {
            return;
        }
        const channelId = String($(this).data('channel-id') || '');
        const channelName = String($(this).data('channel-name') || '');
        if (!channelId) {
            return;
        }
        interactionState.publishLoading = true;
        try {
            await interactionPost('deleteChannel', {
                userId: interactionState.userId,
                channelId: Number(channelId)
            });
            await Promise.all([loadInteractionPublishData(), loadInteractionSubscribeData()]);
            updateInteractionPublishView();
            showInteractionNotice(interactionActionNotice('已删除', channelName));
        } catch (error) {
            showInteractionNotice(error.message || tTextInteraction('删除频道失败'));
        } finally {
            interactionState.publishLoading = false;
        }
    });
}

async function renderInteractionPublishPage() {
    prepareInteractionPage();

    const html = `
        <div id="interactionPage" class="interaction-page">
            <div id="interactionPageNotice" class="interaction-page-notice"></div>

            <section class="interaction-section">
                <div class="interaction-section__head">
                    <div>
                        <h2>${tTextInteraction('频道管理')}</h2>
                        <p>${tTextInteraction('创建你自己的频道')}</p>
                    </div>
                </div>
                <div class="interaction-action-grid">
                    <button type="button" class="interaction-action-card interaction-action-card--fixed" id="interactionCreateChannel">
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
        <div id="interactionCreateChannelMask" style="display:none;position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,0.32);z-index:1200;align-items:center;justify-content:center;">
            <div style="width:min(560px,92vw);background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:16px;box-shadow:0 10px 30px rgba(15,23,42,.18);">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                    <div style="font-size:20px;font-weight:600;">${tTextInteraction('创建频道')}</div>
                    <button type="button" id="interactionCreateChannelCloseBtn" style="border:none;background:transparent;font-size:20px;cursor:pointer;color:#6b7280;">×</button>
                </div>
                <div style="display:grid;gap:10px;">
                    <label style="font-size:13px;color:#374151;">
                        ${tTextInteraction('频道名称')}<span style="color:#dc2626;margin-left:2px;">*</span>
                        <input id="interactionCreateChannelNameInput" type="text" placeholder="${tTextInteraction('请输入频道名称')}" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" />
                    </label>
                    <label style="font-size:13px;color:#374151;">
                        ${tTextInteraction('频道介绍')}（${tTextInteraction('可选')}）
                        <textarea id="interactionCreateChannelDescInput" placeholder="${tTextInteraction('请输入频道介绍（可选）')}" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;min-height:88px;resize:vertical;"></textarea>
                    </label>
                </div>
                <button type="button" id="interactionCreateChannelConfirmBtn" style="margin-top:14px;width:100%;padding:10px;border:none;border-radius:8px;background:#6366f1;color:#fff;cursor:pointer;">${tTextInteraction('创建')}</button>
            </div>
        </div>
    `;

    $('#item-content').html(tHtmlInteraction(html));
    $('#interactionCreateChannel').on('click', function () {
        if (interactionState.publishLoading) {
            return;
        }
        $('#interactionCreateChannelNameInput').val('');
        $('#interactionCreateChannelDescInput').val('');
        $('#interactionCreateChannelMask').css('display', 'flex');
    });
    $('#interactionCreateChannelCloseBtn').on('click', function () {
        $('#interactionCreateChannelMask').hide();
    });
    $('#interactionCreateChannelMask').on('click', function (e) {
        if (e.target && e.target.id === 'interactionCreateChannelMask') {
            $('#interactionCreateChannelMask').hide();
        }
    });
    $('#interactionCreateChannelConfirmBtn').on('click', async function () {
        if (interactionState.publishLoading) {
            return;
        }
        const channelName = String($('#interactionCreateChannelNameInput').val() || '').trim();
        if (!channelName) {
            showInteractionNotice(tTextInteraction('请输入频道名称'));
            return;
        }
        const channelDesc = String($('#interactionCreateChannelDescInput').val() || '').trim();
        interactionState.publishLoading = true;
        try {
            await interactionPost('createChannel', {
                userId: interactionState.userId,
                name: channelName,
                description: channelDesc,
                isPublic: true
            });
            $('#interactionCreateChannelMask').hide();
            await Promise.all([loadInteractionPublishData(), loadInteractionSubscribeData()]);
            updateInteractionPublishView();
            showInteractionNotice(interactionCreateSuccessNotice(channelName));
        } catch (error) {
            showInteractionNotice(error.message || tTextInteraction('创建频道失败'));
        } finally {
            interactionState.publishLoading = false;
        }
    });
    try {
        await loadInteractionPublishData();
    } catch (error) {
        showInteractionNotice(error.message || tTextInteraction('加载管理频道失败'));
        interactionState.publishChannels = [];
    }
    if (interactionState.recommendedChannels.length === 0) {
        try {
            await loadInteractionSubscribeData();
        } catch (error) {
            // Ignore follow-up load failure here.
        }
    }
    updateInteractionPublishView();
}

async function initInteractionUser() {
    if (interactionState.initPromise) {
        return interactionState.initPromise;
    }
    interactionState.initPromise = ensureInteractionUserReady().catch(function (error) {
        interactionState.initPromise = null;
        throw error;
    });
    return interactionState.initPromise;
}

window.openInteractionPage = async function openInteractionPage(navId, subNavId) {
    const subNav = typeof getSubNav === 'function' ? getSubNav(navId, subNavId) : null;
    if (!subNav || subNav.disabled) {
        return;
    }
    if (typeof setLeafNavActiveByNavId === 'function') {
        setLeafNavActiveByNavId(subNavId);
    }
    try {
        await initInteractionUser();
        if (subNav.key === 'interactionPublish') {
            await renderInteractionPublishPage();
            return;
        }
        await renderInteractionSubscribePage();
    } catch (error) {
        prepareInteractionPage();
        $('#item-content').html(tHtmlInteraction(`
            <div class="interaction-page">
                <div class="interaction-empty-state">${escapeInteractionHtml(error.message || '频道模块初始化失败')}</div>
            </div>
        `));
    }
};
