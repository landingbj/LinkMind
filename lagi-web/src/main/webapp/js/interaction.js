const tTextInteraction = window.tText || ((text) => text);
const tHtmlInteraction = window.tHtml || ((html) => html);
const SOCIAL_CHANNEL_API_BASE = '/socialChannel';

const interactionState = {
    userId: '',
    username: '',
    recommendedChannels: [],
    publishChannels: [],
    cascadeServerAddress: '',
    initPromise: null,
    subscribeLoading: false,
    publishLoading: false,
    cascadeLoading: false,
    monitor: {
        channelId: '',
        channelName: '',
        timer: 0,
        intervalSeconds: 5,
        lastMaxId: 0,
        loading: false
    }
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
}

// Triggered once right after the user's login session is confirmed
// (cookie-based auth or successful login). Registers the user with the
// interaction backend and best-effort saves the last-login record.
// Always overwrites userId/username so account switching cannot leak the
// previous session's values into the registration call.
async function registerInteractionUserOnLogin() {
    const userId = getInteractionUserId();
    if (!userId) {
        return;
    }
    interactionState.userId = userId;
    interactionState.username = getInteractionUsername();
    try {
        await interactionPost('registerUser', {
            userId: interactionState.userId,
            username: interactionState.username
        });
    } catch (error) {
        return;
    }
    try {
        await interactionPost('saveLastLoginUser', {
            userId: interactionState.userId,
            username: interactionState.username
        });
    } catch (error) {
        // Ignore temp-save failure because user registration has succeeded.
    }
}

window.registerInteractionUserOnLogin = registerInteractionUserOnLogin;

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

function getInteractionPreferredLang() {
    if (typeof window.getCurrentLang === 'function') {
        const lang = window.getCurrentLang();
        if (lang) {
            return lang;
        }
    }
    if (navigator && navigator.language) {
        const navLang = String(navigator.language);
        if (navLang.toLowerCase().indexOf('zh') === 0) {
            return 'zh-CN';
        }
        return 'en-US';
    }
    return 'zh-CN';
}

function detectInteractionLang(text) {
    const value = String(text == null ? '' : text);
    for (let i = 0; i < value.length; i++) {
        const code = value.charCodeAt(i);
        if (code >= 0x4e00 && code <= 0x9fff) {
            return 'zh-CN';
        }
    }
    return 'en-US';
}

async function loadInteractionSubscribeData() {
    await ensureInteractionUserReady();
    const preferredLang = getInteractionPreferredLang();
    const responses = await Promise.all([
        interactionGet('listPublicChannels', { limit: 100, lang: preferredLang }),
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
    if (typeof stopInteractionMonitorTimer === 'function') {
        stopInteractionMonitorTimer();
    }
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
                <td style="text-align:center;white-space:nowrap;">
                    <button type="button" class="interaction-btn interaction-btn-secondary interaction-monitor-btn" data-channel-id="${escapeInteractionHtml(channel.id)}" data-channel-name="${escapeInteractionHtml(channel.name)}" style="margin-right:6px;">${tTextInteraction('监控')}</button>
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

    $('.interaction-monitor-btn').off('click').on('click', function () {
        const channelId = String($(this).data('channel-id') || '');
        const channelName = String($(this).data('channel-name') || '');
        if (!channelId) {
            return;
        }
        openInteractionMonitor(channelId, channelName);
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
                                <th>${tTextInteraction('频道介绍')}</th>
                                <th style="text-align:center;">${tTextInteraction('操作')}</th>
                            </tr>
                        </thead>
                        <tbody id="interactionJoinedTableBody"></tbody>
                    </table>
                </div>
            </section>
        </div>
        ${buildInteractionMonitorModalHtml()}
    `;

    $('#item-content').html(tHtmlInteraction(html));
    bindInteractionMonitorModalEvents();
    try {
        await loadInteractionSubscribeData();
    } catch (error) {
        showInteractionNotice(error.message || tTextInteraction('加载频道失败'));
    }
    updateInteractionSubscribeView();
    // After the initial render, asynchronously translate any channel whose
    // name or description doesn't match the user's preferred language.
    translateMismatchedChannelsAsync();
}

function buildInteractionMonitorModalHtml() {
    return `
        <div id="interactionMonitorMask" style="display:none;position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(15,23,42,0.45);z-index:1300;align-items:center;justify-content:center;">
            <div style="width:min(720px,94vw);height:min(78vh,720px);background:#f5f7fb;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 18px 40px rgba(15,23,42,.22);display:flex;flex-direction:column;overflow:hidden;">
                <div style="display:flex;align-items:center;justify-content:space-between;padding:12px 18px;background:#ffffff;border-bottom:1px solid #e5e7eb;">
                    <div style="display:flex;align-items:center;gap:10px;min-width:0;">
                        <div style="width:36px;height:36px;border-radius:50%;background:#6366f1;color:#fff;display:flex;align-items:center;justify-content:center;font-weight:600;flex:none;">#</div>
                        <div style="min-width:0;">
                            <div id="interactionMonitorTitle" style="font-size:15px;font-weight:600;color:#111827;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;"></div>
                            <div id="interactionMonitorSubTitle" style="font-size:12px;color:#6b7280;"></div>
                        </div>
                    </div>
                    <button type="button" id="interactionMonitorCloseBtn" style="border:none;background:transparent;font-size:22px;cursor:pointer;color:#6b7280;line-height:1;padding:4px 6px;">×</button>
                </div>
                <div style="display:flex;align-items:center;gap:8px;padding:8px 18px;background:#ffffff;border-bottom:1px solid #eef0f4;font-size:12px;color:#4b5563;">
                    <span>${tTextInteraction('刷新间隔')}</span>
                    <input id="interactionMonitorIntervalInput" type="number" min="2" max="600" step="1" style="width:74px;padding:4px 6px;border:1px solid #d1d5db;border-radius:6px;font-size:12px;" />
                    <span>${tTextInteraction('秒')}</span>
                    <button type="button" id="interactionMonitorRefreshBtn" class="interaction-btn interaction-btn-secondary" style="padding:4px 10px;font-size:12px;">${tTextInteraction('立即刷新')}</button>
                    <span id="interactionMonitorStatus" style="margin-left:auto;color:#9ca3af;"></span>
                </div>
                <div id="interactionMonitorMessages" style="flex:1;overflow-y:auto;padding:14px 18px;background:#f5f7fb;display:flex;flex-direction:column;gap:10px;"></div>
            </div>
        </div>
    `;
}

function bindInteractionMonitorModalEvents() {
    $('#interactionMonitorCloseBtn').off('click').on('click', closeInteractionMonitor);
    $('#interactionMonitorMask').off('click').on('click', function (e) {
        if (e.target && e.target.id === 'interactionMonitorMask') {
            closeInteractionMonitor();
        }
    });
    $('#interactionMonitorIntervalInput').off('keydown change blur').on('keydown', function (event) {
        if (event.key === 'Enter') {
            event.preventDefault();
            applyInteractionMonitorInterval();
        }
    }).on('change blur', function () {
        applyInteractionMonitorInterval();
    });
    $('#interactionMonitorRefreshBtn').off('click').on('click', function () {
        refreshInteractionMonitorMessages(true);
    });
}

function openInteractionMonitor(channelId, channelName) {
    const monitor = interactionState.monitor;
    monitor.channelId = String(channelId);
    monitor.channelName = String(channelName || '');
    monitor.lastMaxId = 0;
    $('#interactionMonitorTitle').text('#' + monitor.channelName);
    $('#interactionMonitorSubTitle').text(tTextInteraction('频道实时消息'));
    $('#interactionMonitorIntervalInput').val(monitor.intervalSeconds);
    $('#interactionMonitorMessages').html(`<div style="text-align:center;color:#9ca3af;font-size:12px;padding:24px 0;">${tTextInteraction('正在加载...')}</div>`);
    $('#interactionMonitorStatus').text('');
    $('#interactionMonitorMask').css('display', 'flex');
    refreshInteractionMonitorMessages(true);
    startInteractionMonitorTimer();
}

function closeInteractionMonitor() {
    stopInteractionMonitorTimer();
    $('#interactionMonitorMask').hide();
    interactionState.monitor.channelId = '';
}

function startInteractionMonitorTimer() {
    stopInteractionMonitorTimer();
    const seconds = Math.max(2, Number(interactionState.monitor.intervalSeconds) || 5);
    interactionState.monitor.timer = setInterval(function () {
        refreshInteractionMonitorMessages(false);
    }, seconds * 1000);
}

function stopInteractionMonitorTimer() {
    if (interactionState.monitor.timer) {
        clearInterval(interactionState.monitor.timer);
        interactionState.monitor.timer = 0;
    }
}

function applyInteractionMonitorInterval() {
    const raw = Number($('#interactionMonitorIntervalInput').val());
    let seconds = isFinite(raw) ? Math.floor(raw) : 5;
    if (seconds < 2) {
        seconds = 2;
    }
    if (seconds > 600) {
        seconds = 600;
    }
    interactionState.monitor.intervalSeconds = seconds;
    $('#interactionMonitorIntervalInput').val(seconds);
    startInteractionMonitorTimer();
    $('#interactionMonitorStatus').text(tTextInteraction('已更新刷新间隔'));
}

function formatInteractionMonitorTime(value) {
    if (!value) {
        return '';
    }
    const date = value instanceof Date ? value : new Date(value);
    if (isNaN(date.getTime())) {
        return '';
    }
    const pad = function (n) { return n < 10 ? '0' + n : String(n); };
    return pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
}

function buildInteractionMonitorMessageHtml(message, currentUserId) {
    const userName = escapeInteractionHtml(message.userName || message.userId || tTextInteraction('未知用户'));
    const content = escapeInteractionHtml(message.content || '').replace(/\n/g, '<br/>');
    const timeText = escapeInteractionHtml(formatInteractionMonitorTime(message.createdAt));
    const isSelf = currentUserId && String(message.userId) === String(currentUserId);
    const isAgent = !!message.agentAutoSent;
    const alignStyle = isSelf ? 'align-items:flex-end;' : 'align-items:flex-start;';
    const bubbleBg = isSelf ? 'background:#6366f1;color:#fff;' : (isAgent ? 'background:#fef3c7;color:#92400e;' : 'background:#ffffff;color:#111827;');
    const avatarBg = isSelf ? '#6366f1' : (isAgent ? '#f59e0b' : '#9ca3af');
    const initial = (message.userName || message.userId || '?').charAt(0).toUpperCase();
    const agentBadge = isAgent ? `<span style="margin-left:6px;padding:1px 6px;border-radius:8px;background:#fde68a;color:#92400e;font-size:10px;">${tTextInteraction('Agent')}</span>` : '';
    const headerRow = isSelf
        ? `<div style="font-size:12px;color:#6b7280;margin-bottom:2px;">${timeText} · ${userName}${agentBadge}</div>`
        : `<div style="font-size:12px;color:#6b7280;margin-bottom:2px;">${userName}${agentBadge} · ${timeText}</div>`;
    const avatar = `<div style="width:32px;height:32px;border-radius:50%;background:${avatarBg};color:#fff;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:600;flex:none;">${escapeInteractionHtml(initial)}</div>`;
    const bubble = `
        <div style="display:flex;flex-direction:column;${alignStyle}max-width:72%;">
            ${headerRow}
            <div style="${bubbleBg}padding:8px 12px;border-radius:12px;box-shadow:0 1px 2px rgba(15,23,42,0.06);word-break:break-word;white-space:normal;line-height:1.5;font-size:14px;">${content}</div>
        </div>
    `;
    const rowStyle = isSelf
        ? 'display:flex;gap:8px;justify-content:flex-end;align-items:flex-start;'
        : 'display:flex;gap:8px;justify-content:flex-start;align-items:flex-start;';
    return `<div style="${rowStyle}">${isSelf ? bubble + avatar : avatar + bubble}</div>`;
}

async function refreshInteractionMonitorMessages(forceScroll) {
    const monitor = interactionState.monitor;
    if (!monitor.channelId || monitor.loading) {
        return;
    }
    monitor.loading = true;
    try {
        const res = await interactionGet('listMessages', {
            userId: interactionState.userId,
            channelId: monitor.channelId,
            limit: 100
        });
        const messages = ((res && res.data) || []).slice().reverse();
        const container = $('#interactionMonitorMessages');
        if (!messages.length) {
            container.html(`<div style="text-align:center;color:#9ca3af;font-size:12px;padding:24px 0;">${tTextInteraction('暂无消息')}</div>`);
        } else {
            const html = messages.map(function (msg) {
                return buildInteractionMonitorMessageHtml(msg, interactionState.userId);
            }).join('');
            container.html(html);
        }
        const newestId = messages.length ? Number(messages[messages.length - 1].id || 0) : 0;
        const containerEl = container.get(0);
        if (containerEl) {
            const nearBottom = containerEl.scrollHeight - containerEl.scrollTop - containerEl.clientHeight < 80;
            if (forceScroll || nearBottom || newestId > monitor.lastMaxId) {
                containerEl.scrollTop = containerEl.scrollHeight;
            }
        }
        monitor.lastMaxId = newestId;
        const now = new Date();
        const pad = function (n) { return n < 10 ? '0' + n : String(n); };
        const stamp = pad(now.getHours()) + ':' + pad(now.getMinutes()) + ':' + pad(now.getSeconds());
        $('#interactionMonitorStatus').text(tTextInteraction('已更新') + ' ' + stamp);
    } catch (error) {
        $('#interactionMonitorStatus').text(error.message || tTextInteraction('加载消息失败'));
    } finally {
        monitor.loading = false;
    }
}

function translateMismatchedChannelsAsync() {
    const preferredLang = getInteractionPreferredLang();
    const channels = (interactionState.recommendedChannels || []).slice();
    channels.forEach(function (channel) {
        if (!channel || !channel.id) {
            return;
        }
        const rawName = channel.joinedInfo && channel.joinedInfo.name ? channel.joinedInfo.name : '';
        const description = channel.description || '';
        const nameLang = detectInteractionLang(rawName);
        const descLang = detectInteractionLang(description);
        if (nameLang === preferredLang && descLang === preferredLang) {
            return;
        }
        interactionPost('translateChannel', {
            channelId: Number(channel.id),
            lang: preferredLang
        }).then(function (res) {
            const data = (res && res.data) || {};
            const newName = String(data.name == null ? '' : data.name).trim();
            const newDesc = String(data.description == null ? '' : data.description).trim();
            const target = interactionState.recommendedChannels.find(function (item) {
                return item.id === channel.id;
            });
            if (!target) {
                return;
            }
            if (newName) {
                const normalized = newName.indexOf('#') === 0 ? newName.substring(1) : newName;
                target.tag = '#' + normalized;
                if (target.joinedInfo) {
                    target.joinedInfo.name = normalized;
                }
            }
            if (newDesc) {
                target.description = newDesc;
                if (target.joinedInfo) {
                    target.joinedInfo.latest = newDesc;
                }
            }
            updateInteractionSubscribeView();
        }).catch(function () {
            // Ignore translation errors; keep showing the original content.
        });
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

function formatInteractionLabelValue(label, value) {
    const currentLang = typeof window.getCurrentLang === 'function' ? window.getCurrentLang() : '';
    return currentLang === 'en-US' ? `${label}: ${value}` : `${label}：${value}`;
}

function updateInteractionCascadeView(data) {
    const address = data && data.serverAddress ? String(data.serverAddress) : '';
    interactionState.cascadeServerAddress = address;
    $('#interactionCascadeServerInput').val(address);
    $('#interactionCascadeStatus').text(address
        ? formatInteractionLabelValue(tTextInteraction('当前服务器'), address)
        : tTextInteraction('当前未配置服务器地址'));
}

function getInteractionCascadeErrorMessage(error, fallback) {
    const message = error && error.message ? String(error.message) : '';
    if (message.indexOf('serverAddress') >= 0 || message.indexOf('http(s) URL') >= 0) {
        return tTextInteraction('服务器地址格式不正确');
    }
    return message || fallback;
}

async function renderInteractionCascadePage() {
    prepareInteractionPage();

    const html = `
        <div id="interactionPage" class="interaction-page">
            <div id="interactionPageNotice" class="interaction-page-notice"></div>

            <section class="interaction-section interaction-settings-section">
                <div class="interaction-section__head">
                    <div>
                        <h2>${tTextInteraction('服务器设置')}</h2>
                        <p>${tTextInteraction('设置互动级联服务器地址')}</p>
                    </div>
                </div>
                <div class="interaction-setting-form">
                    <label class="interaction-setting-label" for="interactionCascadeServerInput">${tTextInteraction('服务器地址')}</label>
                    <div class="interaction-setting-row">
                        <input id="interactionCascadeServerInput" class="interaction-setting-input" type="text" placeholder="https://server.example.com" />
                        <button type="button" id="interactionCascadeSaveBtn" class="interaction-btn interaction-btn-primary interaction-setting-save-btn">${tTextInteraction('保存设置')}</button>
                    </div>
                    <div id="interactionCascadeStatus" class="interaction-setting-status">${tTextInteraction('正在加载...')}</div>
                </div>
            </section>
        </div>
    `;

    $('#item-content').html(tHtmlInteraction(html));
    $('#interactionCascadeSaveBtn').on('click', async function () {
        if (interactionState.cascadeLoading) {
            return;
        }
        const serverAddress = String($('#interactionCascadeServerInput').val() || '').trim();
        interactionState.cascadeLoading = true;
        $('#interactionCascadeSaveBtn').prop('disabled', true);
        try {
            const res = await interactionPost('cascadeConfig', { serverAddress: serverAddress });
            updateInteractionCascadeView((res && res.data) || {});
            showInteractionNotice(tTextInteraction('服务器设置已保存'));
        } catch (error) {
            showInteractionNotice(getInteractionCascadeErrorMessage(error, tTextInteraction('保存服务器设置失败')));
        } finally {
            interactionState.cascadeLoading = false;
            $('#interactionCascadeSaveBtn').prop('disabled', false);
        }
    });
    $('#interactionCascadeServerInput').on('keydown', function (event) {
        if (event.key === 'Enter') {
            event.preventDefault();
            $('#interactionCascadeSaveBtn').trigger('click');
        }
    });

    try {
        const res = await interactionGet('cascadeConfig');
        updateInteractionCascadeView((res && res.data) || {});
    } catch (error) {
        $('#interactionCascadeStatus').text(tTextInteraction('当前未配置服务器地址'));
        showInteractionNotice(error.message || tTextInteraction('加载服务器设置失败'));
    }
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
        if (subNav.key === 'interactionCascade') {
            await renderInteractionCascadePage();
            return;
        }
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
