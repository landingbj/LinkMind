(function() {
    const tTextOpenRouter = window.tText || ((s) => s);
    const tHtmlOpenRouter = window.tHtml || ((s) => s);
    const OPENAI_COMPATIBLE = "OpenAICompatible";
    const LANDING = "Landing";

    function hideChatPartsForStandalonePage() {
        $("#queryBox").hide();
        $("#footer-info").hide();
        $("#introduces").hide();
        $("#topTitle").hide();
        $("#item-content").show();
        $("#item-content").css("height", "calc(100vh - 60px)");
        $("#item-content").css("overflow-y", "auto");
        if (typeof hideBallDiv === "function") {
            hideBallDiv();
        }
    }

    function openRouterThStyle() {
        return "padding:10px;border-bottom:1px solid #e5e7eb;text-align:left;color:#6b7280;font-weight:600;white-space:nowrap;";
    }

    function openRouterTdStyle() {
        return "padding:10px;border-bottom:1px solid #f3f4f6;white-space:nowrap;";
    }

    function ensureApiKeysState() {
        if (!window.__apiKeysPageState) {
            window.__apiKeysPageState = {
                rows: [],
                loading: false,
                types: [],
                localApiKeyEditable: true,
                invalidApiKeys: {},
                apiKeysTableReady: false
            };
        }
    }

    /** Same rule as credits center: only cookie userId counts as logged in. */
    function isApiKeysUserLoggedIn() {
        return !!((typeof getCookie === "function" ? getCookie("userId") : "") || "");
    }

    function syncLocalApiKeyEditableFromResponse(res, st) {
        if (res && typeof res.localApiKeyEditable === "boolean") {
            st.localApiKeyEditable = res.localApiKeyEditable;
        }
    }

    function updateApiKeysModeHint(st) {
        var hint = $("#apiKeysModeHint");
        if (!hint.length) {
            return;
        }
        if (!st.localApiKeyEditable) {
            hint.text(tTextOpenRouter("仅管理云端 Landing 密钥（本地密钥已关闭）")).show();
        } else {
            hint.hide().text("");
        }
    }

    function maskApiKey(v) {
        var s = String(v || "");
        if (!s) return "—";
        if (s.length <= 8) return s;
        return s.slice(0, 4) + "..." + s.slice(-4);
    }

    function isInvalidApiKey(row) {
        const st = window.__apiKeysPageState || {};
        const apiKey = String((row && row.api_key) || "").trim();
        return !!(apiKey && st.invalidApiKeys && st.invalidApiKeys[apiKey]);
    }

    function openCreditsPage() {
        if (typeof loadCreditsPage === "function") {
            loadCreditsPage();
            if (typeof setLeafNavActiveByNavId === "function") {
                setLeafNavActiveByNavId(19);
            }
        }
    }

    function copyTextToClipboard(text, onDone) {
        var t = String(text || "");
        var done = typeof onDone === "function" ? onDone : function() {};
        if (!t) {
            done(false);
            return;
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(t).then(function() {
                done(true);
            }).catch(function() {
                done(false);
            });
            return;
        }
        try {
            var ta = document.createElement("textarea");
            ta.value = t;
            ta.setAttribute("readonly", "");
            ta.style.position = "fixed";
            ta.style.left = "-9999px";
            document.body.appendChild(ta);
            ta.select();
            var ok = document.execCommand("copy");
            document.body.removeChild(ta);
            done(ok);
        } catch (e) {
            done(false);
        }
    }

    function renderApiKeysRows(rows) {
        if (!rows || !rows.length) {
            return `<tr><td colspan="4" style="padding:24px;text-align:center;color:#9ca3af;">${tTextOpenRouter("暂无数据")}</td></tr>`;
        }
        const st = window.__apiKeysPageState || {};
        const showToggle = st.localApiKeyEditable !== false;
        return rows.map(function(row, rowIndex) {
            const isEnabled = Number(row.status || 0) === 1;
            const isInvalid = isInvalidApiKey(row);
            const rowStyle = isInvalid ? ' style="color:#dc2626;"' : "";
            const tdStyle = openRouterTdStyle() + (isInvalid ? "color:#dc2626;" : "");
            const apiKeyText = maskApiKey(row.api_key);
            const apiKeyHtml = isInvalid
                ? `<a href="javascript:void(0)" class="api-key-credit-link" style="color:#dc2626;font-weight:600;text-decoration:underline;">${apiKeyText}</a>`
                : apiKeyText;
            const toggleLink = showToggle
                ? `<a href="javascript:void(0)" class="api-key-toggle-link" data-id="${row.id || ""}" data-provider="${row.provider || ""}" data-enabled="${isEnabled ? "1" : "0"}" style="margin-right:12px;color:${isEnabled ? "#f59e0b" : "#10b981"};">${isEnabled ? tTextOpenRouter("禁用") : tTextOpenRouter("启用")}</a>`
                : "";
            const copyLink = `<a href="javascript:void(0)" class="api-key-copy-link" data-row-index="${rowIndex}" style="margin-right:12px;color:#6366f1;">${tTextOpenRouter("复制")}</a>`;
            return `
                <tr${rowStyle}>
                    <td style="${tdStyle}">${row.name || "—"}</td>
                    <td style="${tdStyle}">${row.provider || "—"}</td>
                    <td style="${tdStyle}">${apiKeyHtml}</td>
                    <td style="${tdStyle}">
                        ${toggleLink}${copyLink}<a href="javascript:void(0)" class="api-key-delete-link" data-id="${row.id || ""}" data-provider="${row.provider || ""}" data-row-index="${rowIndex}" style="color:#ef4444;">${tTextOpenRouter("删除")}</a>
                    </td>
                </tr>
            `;
        }).join("");
    }

    function setApiKeysLoading(loading) {
        const st = window.__apiKeysPageState;
        st.loading = !!loading;
        if (loading) {
            $("#apiKeysTbody").html('<tr><td colspan="4" style="padding:24px;text-align:center;color:#9ca3af;">加载中...</td></tr>');
        }
    }

    function renderProviderOptions(providers) {
        return (providers || []).map(function(provider) {
            return '<option value="' + provider + '">' + provider + "</option>";
        }).join("");
    }

    function loadProviders(done) {
        if (!isApiKeysUserLoggedIn()) {
            if (typeof done === "function") {
                done([]);
            }
            return;
        }
        $.ajax({
            type: "GET",
            contentType: "application/json;charset=utf-8",
            url: "/apiKey/providers",
            success: function(res) {
                const st = window.__apiKeysPageState;
                syncLocalApiKeyEditableFromResponse(res, st);
                if (res && res.status === "success" && Array.isArray(res.data)) {
                    st.types = res.data;
                } else {
                    st.types = [];
                }
                if (st.apiKeysTableReady && !st.loading && $("#apiKeysTbody").length) {
                    $("#apiKeysTbody").html(renderApiKeysRows(st.rows || []));
                    updateApiKeysModeHint(st);
                }
                if (typeof done === "function") {
                    done(st.types);
                }
            },
            error: function() {
                const st = window.__apiKeysPageState;
                st.types = [];
                if (typeof done === "function") {
                    done(st.types);
                }
            }
        });
    }

    function loadInvalidApiKeys(rows) {
        const st = window.__apiKeysPageState;
        const apiKeys = (rows || []).map(function(row) {
            return String((row && row.api_key) || "").trim();
        }).filter(function(apiKey) {
            return !!apiKey;
        });
        if (!apiKeys.length) {
            st.invalidApiKeys = {};
            $("#apiKeysTbody").html(renderApiKeysRows(st.rows));
            return;
        }
        $.ajax({
            type: "POST",
            url: "/apiKey/invalidList",
            contentType: "application/json;charset=utf-8",
            data: JSON.stringify({ apiKeys: apiKeys }),
            success: function(res) {
                const invalidMap = {};
                if (res && res.status === "success" && Array.isArray(res.data)) {
                    res.data.forEach(function(apiKey) {
                        const key = String(apiKey || "").trim();
                        if (key) {
                            invalidMap[key] = true;
                        }
                    });
                }
                st.invalidApiKeys = invalidMap;
                $("#apiKeysTbody").html(renderApiKeysRows(st.rows));
            },
            error: function() {
                st.invalidApiKeys = {};
                $("#apiKeysTbody").html(renderApiKeysRows(st.rows));
            }
        });
    }

    function toggleAddDialogFields() {
        const provider = ($("#apiKeyAddProviderSelect").val() || "").trim();
        const isOpenAICompatible = provider === OPENAI_COMPATIBLE;
        const isLanding = provider === LANDING;
        $("#apiKeyAddOpenAIFields").toggle(isOpenAICompatible);
        $("#apiKeyAddApiAddressWrap").toggle(isOpenAICompatible);
        $("#apiKeyAddStandardKeyBlock").toggle(!isLanding);
        $("#apiKeyLandingHint").toggle(isLanding);
    }

    function resetAddDialog() {
        $("#apiKeyAddProviderSelect").val("");
        $("#apiKeyAddApiKeyInput").val("");
        $("#apiKeyAddNameInput").val("");
        $("#apiKeyAddModelInput").val("");
        $("#apiKeyAddApiAddressInput").val("");
        toggleAddDialogFields();
    }

    function loadApiKeys() {
        if (!isApiKeysUserLoggedIn()) {
            return;
        }
        ensureApiKeysState();
        window.__apiKeysPageState.apiKeysTableReady = false;
        setApiKeysLoading(true);
        var userId = getApiKeysUserId();
        $.ajax({
            type: "GET",
            contentType: "application/json;charset=utf-8",
            url: "/apiKey/list",
            data: { userId: userId },
            success: function(res) {
                const st = window.__apiKeysPageState;
                st.loading = false;
                syncLocalApiKeyEditableFromResponse(res, st);
                if (!res || res.status !== "success" || !Array.isArray(res.data)) {
                    st.rows = [];
                } else {
                    st.rows = res.data.map(function(item) {
                        return {
                            id: item.id,
                            name: item.name || "",
                            provider: item.provider || "",
                            api_address: item.api_address || item.apiAddress || "",
                            api_key: item.api_key || item.apiKey || "",
                            status: item.status == null ? 0 : Number(item.status)
                        };
                    });
                }
                $("#apiKeysTbody").html(renderApiKeysRows(st.rows));
                st.apiKeysTableReady = true;
                updateApiKeysModeHint(st);
                loadInvalidApiKeys(st.rows);
            },
            error: function() {
                const st = window.__apiKeysPageState;
                st.loading = false;
                st.rows = [];
                $("#apiKeysTbody").html('<tr><td colspan="4" style="padding:24px;text-align:center;color:#9ca3af;">加载失败</td></tr>');
                st.apiKeysTableReady = true;
            }
        });
    }

    function bindApiKeysEvents() {
        $("#apiKeysCreateBtn").on("click", function() {
            resetAddDialog();
            $("#apiKeyAddArea").show();
            $("#apiKeyCreateConfirmBtn").show();
            $("#apiKeysCreateMask").css("display", "flex");
        });
        $("#apiKeysCloseBtn").on("click", function() {
            $("#apiKeysCreateMask").hide();
        });
        $("#apiKeysCreateMask").on("click", function(e) {
            if (e.target && e.target.id === "apiKeysCreateMask") {
                $("#apiKeysCreateMask").hide();
            }
        });
        $("#apiKeyAddProviderSelect").on("change", function() {
            toggleAddDialogFields();
        });
        $("#apiKeyCreateConfirmBtn").on("click", function() {
            addApiKey();
        });
        $("#api-keys-container").on("click", ".api-key-delete-link", function() {
            const id = Number($(this).data("id"));
            const provider = String($(this).data("provider") || "");
            const rowIndex = Number($(this).data("row-index"));
            const st = window.__apiKeysPageState;
            const raw = (st && st.rows && !isNaN(rowIndex)) ? st.rows[rowIndex] : null;
            const apiKey = raw ? String(raw.api_key || "").trim() : "";
            const row = { id: id, provider: provider, api_key: apiKey };
            deleteApiKey(row);
        });
        $("#api-keys-container").on("click", ".api-key-toggle-link", function() {
            const id = Number($(this).data("id"));
            const provider = String($(this).data("provider") || "");
            const enabled = String($(this).data("enabled") || "") === "1";
            toggleApiKey(id, provider, !enabled);
        });
        $("#api-keys-container").on("click", ".api-key-copy-link", function() {
            if (!isApiKeysUserLoggedIn()) {
                alert(tTextOpenRouter("请先登录"));
                return;
            }
            const st = window.__apiKeysPageState;
            const idx = Number($(this).data("row-index"));
            const row = (st && st.rows && !isNaN(idx)) ? st.rows[idx] : null;
            const key = row ? String(row.api_key || "").trim() : "";
            if (!key) {
                alert(tTextOpenRouter("暂无可复制的密钥"));
                return;
            }
            copyTextToClipboard(key, function(ok) {
                if (!ok) {
                    alert(tTextOpenRouter("复制失败"));
                }
            });
        });
        $("#api-keys-container").on("click", ".api-key-credit-link", function() {
            openCreditsPage();
        });
    }

    function getApiKeysUserId() {
        var fromCookie = (typeof getCookie === "function" ? getCookie("userId") : "") || "";
        if (fromCookie) return fromCookie;
        try {
            return localStorage.getItem("userId") || "";
        } catch (e) {
            return "";
        }
    }

    function addApiKey() {
        if (!isApiKeysUserLoggedIn()) {
            alert(tTextOpenRouter("请先登录"));
            return;
        }
        const provider = ($("#apiKeyAddProviderSelect").val() || "").trim();
        const apiKey = ($("#apiKeyAddApiKeyInput").val() || "").trim();
        if (!provider) {
            alert(tTextOpenRouter("请选择 provider"));
            return;
        }
        if (provider !== LANDING && !apiKey) {
            alert(tTextOpenRouter("请输入 API Key"));
            return;
        }
        const displayName = ($("#apiKeyAddNameInput").val() || "").trim();
        if (!displayName) {
            alert(tTextOpenRouter("名称为必填项"));
            return;
        }
        const payload = {
            provider: provider,
            apiKey: apiKey,
            name: displayName
        };
        if (provider === LANDING) {
            const userId = getApiKeysUserId();
            if (!userId) {
                alert(tTextOpenRouter("Landing 配置需要登录后获取用户 ID，请先登录"));
                return;
            }
            payload.userId = userId;
        }
        if (provider === OPENAI_COMPATIBLE) {
            const model = ($("#apiKeyAddModelInput").val() || "").trim();
            const apiAddress = ($("#apiKeyAddApiAddressInput").val() || "").trim();
            if (!model) {
                alert(tTextOpenRouter("OpenAICompatible 类型需要 model"));
                return;
            }
            payload.model = model;
            payload.apiAddress = apiAddress;
        }
        $.ajax({
            type: "POST",
            url: "/apiKey/add",
            contentType: "application/json;charset=utf-8",
            data: JSON.stringify(payload),
            success: function(res) {
                if (res && res.status === "success") {
                    $("#apiKeysCreateMask").hide();
                    loadApiKeys();
                } else {
                    alert((res && res.msg) || tTextOpenRouter("新增失败"));
                }
            },
            error: function() {
                alert(tTextOpenRouter("新增失败"));
            }
        });
    }

    function deleteApiKey(row) {
        if (!isApiKeysUserLoggedIn()) {
            alert(tTextOpenRouter("请先登录"));
            return;
        }
        if (!row || !row.id) return;
        confirm(tTextOpenRouter("确认删除该模型的 API Key 吗？")).then(function(ok) {
            if (!ok) return;
            $.ajax({
                type: "POST",
                url: "/apiKey/delete",
                contentType: "application/json;charset=utf-8",
                data: JSON.stringify({
                    id: row.id,
                    apiKey: row.api_key || "",
                    provider: row.provider,
                    userId: getApiKeysUserId()
                }),
                success: function(res) {
                    if (res && res.status === "success") {
                        loadApiKeys();
                    } else {
                        alert((res && res.msg) || tTextOpenRouter("删除失败"));
                    }
                },
                error: function() {
                    alert(tTextOpenRouter("删除失败"));
                }
            });
        });
    }

    function toggleApiKey(id, provider, enabled) {
        if (!isApiKeysUserLoggedIn()) {
            alert(tTextOpenRouter("请先登录"));
            return;
        }
        if (!id || !provider) return;
        $.ajax({
            type: "POST",
            url: "/apiKey/toggle",
            contentType: "application/json;charset=utf-8",
            data: JSON.stringify({
                id: id,
                provider: provider,
                enabled: !!enabled,
                userId: getApiKeysUserId()
            }),
            success: function(res) {
                if (res && res.status === "success") {
                    loadApiKeys();
                } else {
                    alert((res && res.msg) || tTextOpenRouter("保存失败"));
                }
            },
            error: function() {
                alert(tTextOpenRouter("保存失败"));
            }
        });
    }

    window.loadApiKeysPage = function loadApiKeysPage() {
        hideChatPartsForStandalonePage();
        ensureApiKeysState();
        const st = window.__apiKeysPageState;
        const loggedIn = isApiKeysUserLoggedIn();
        const subtitleText = loggedIn
            ? tTextOpenRouter("管理模型的 API Key")
            : tTextOpenRouter("登录后显示 API 密钥");
        const tbodyHtml = loggedIn
            ? renderApiKeysRows(st.rows)
            : '<tr><td colspan="4" style="padding:24px;text-align:center;color:#6b7280;font-size:14px;line-height:1.6;">' +
              tTextOpenRouter("登录后可查看 API 密钥") +
              "</td></tr>";
        const createBtnStyle = loggedIn
            ? "padding:10px 18px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;"
            : "display:none;";
        const html = `
            <div id="api-keys-container" style="padding:20px;min-height:100%;background:#fff;">
                <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:12px;">
                    <div>
                        <h2 style="margin:0;font-size:28px;font-weight:700;">API 密钥</h2>
                        <div style="margin-top:6px;color:#6b7280;font-size:13px;">${subtitleText}</div>
                        <div id="apiKeysModeHint" style="display:none;margin-top:6px;color:#6b7280;font-size:12px;"></div>
                    </div>
                    <button type="button" id="apiKeysCreateBtn" style="${createBtnStyle}">新增配置</button>
                </div>
                <div style="border:1px solid #e5e7eb;border-radius:10px;background:#fff;overflow:hidden;">
                    <div style="overflow-x:auto;">
                        <table style="width:100%;border-collapse:collapse;font-size:13px;">
                            <thead><tr style="background:#fff;"><th style="${openRouterThStyle()}">名称</th><th style="${openRouterThStyle()}">提供商</th><th style="${openRouterThStyle()}">API 密钥</th><th style="${openRouterThStyle()}">操作</th></tr></thead>
                            <tbody id="apiKeysTbody">${tbodyHtml}</tbody>
                        </table>
                    </div>
                </div>
            </div>
            <div id="apiKeysCreateMask" style="display:none;position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,0.32);z-index:1200;align-items:center;justify-content:center;">
                <div style="width:min(560px,92vw);background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:16px;box-shadow:0 10px 30px rgba(15,23,42,.18);">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                        <div style="font-size:20px;font-weight:600;">API Key 配置</div>
                        <button type="button" id="apiKeysCloseBtn" style="border:none;background:transparent;font-size:20px;cursor:pointer;color:#6b7280;">×</button>
                    </div>
                    <div id="apiKeyAddArea" style="display:grid;gap:10px;">
                        <label style="font-size:13px;color:#374151;">provider
                            <select id="apiKeyAddProviderSelect" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;">
                                <option value="">${tTextOpenRouter("请选择 provider")}</option>
                                ${renderProviderOptions(st.types)}
                            </select>
                        </label>
                        <label style="font-size:13px;color:#374151;">${tTextOpenRouter("名称")}<span style="color:#dc2626;margin-left:2px;">*</span><span style="color:#9ca3af;font-weight:400;">（${tTextOpenRouter("必填，仅列表展示")}）</span><input id="apiKeyAddNameInput" type="text" required placeholder="${tTextOpenRouter("例如：工作用 Key")}" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        <div id="apiKeyAddOpenAIFields" style="display:none;gap:10px;">
                            <label style="font-size:13px;color:#374151;">model<input id="apiKeyAddModelInput" type="text" placeholder="例如：gpt-4o-mini" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        </div>
                        <label id="apiKeyAddApiAddressWrap" style="display:none;font-size:13px;color:#374151;">api_address（可选）<input id="apiKeyAddApiAddressInput" type="text" placeholder="例如：https://xxx/v1/chat/completions" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        <div id="apiKeyLandingHint" style="display:none;padding:10px 12px;border-radius:8px;background:#f0f9ff;border:1px solid #bae6fd;color:#0369a1;font-size:13px;line-height:1.5;">${tTextOpenRouter("无需填写 API Key：密钥将由服务端自动生成，并写入配置文件 lagi.yml。")}</div>
                        <div id="apiKeyAddStandardKeyBlock">
                            <label style="font-size:13px;color:#374151;">API Key<input id="apiKeyAddApiKeyInput" type="text" placeholder="请输入 API Key" style="margin-top:6px;width:100%;padding:8px;border:1px solid #d1d5db;border-radius:6px;" /></label>
                        </div>
                    </div>
                    <button type="button" id="apiKeyCreateConfirmBtn" style="margin-top:14px;width:100%;padding:10px;border:none;border-radius:8px;background:#6366f1;color:#fff;cursor:pointer;">新增</button>
                </div>
            </div>
        `;
        $("#item-content").html(tHtmlOpenRouter(html));
        bindApiKeysEvents();
        if (loggedIn) {
            loadProviders(function(types) {
                const stAfter = window.__apiKeysPageState;
                const optionHtml = '<option value="">' + tTextOpenRouter("请选择 provider") + "</option>" + renderProviderOptions(types);
                $("#apiKeyAddProviderSelect").html(optionHtml);
                toggleAddDialogFields();
                updateApiKeysModeHint(stAfter);
            });
            loadApiKeys();
        }
    };

    /**
     * For chat completions: if user is logged in (cookie userId), returns a Landing API key from /apiKey/list.
     * Prefers enabled keys (status === 1). Resolves to null if not logged in or no Landing key.
     */
    window.fetchLandingApiKeyForChat = function() {
        return new Promise(function(resolve) {
            if (!isApiKeysUserLoggedIn()) {
                resolve(null);
                return;
            }
            var userId = getApiKeysUserId();
            if (!userId) {
                resolve(null);
                return;
            }
            $.ajax({
                type: "GET",
                contentType: "application/json;charset=utf-8",
                url: "/apiKey/list",
                data: { userId: userId },
                success: function(res) {
                    if (!res || res.status !== "success" || !Array.isArray(res.data)) {
                        resolve(null);
                        return;
                    }
                    var landing = [];
                    for (var i = 0; i < res.data.length; i++) {
                        var item = res.data[i];
                        if (item && String(item.provider || "").toLowerCase() === "landing") {
                            landing.push(item);
                        }
                    }
                    if (!landing.length) {
                        resolve(null);
                        return;
                    }
                    var enabled = landing.filter(function(x) {
                        return Number(x.status) === 1;
                    });
                    var pick = (enabled.length ? enabled : landing)[0];
                    var key = String(pick.api_key || pick.apiKey || "").trim();
                    resolve(key || null);
                },
                error: function() {
                    resolve(null);
                }
            });
        });
    };
})();
