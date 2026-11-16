(function() {
    const tTextOpenRouter = window.tText || ((s) => s);
    const tHtmlOpenRouter = window.tHtml || ((s) => s);

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

    function formatCreditsMoney(v) {
        return Number(v || 0).toFixed(2);
    }

    function getCreditsUserId() {
        return (typeof getCookie === "function" ? getCookie("userId") : "") || "";
    }

    function isCreditsUserLoggedIn() {
        return !!getCreditsUserId();
    }

    /** Keeps at most two digits after the decimal point while typing. */
    function sanitizeCreditsAmountInput(str) {
        if (str === "" || str === ".") {
            return str;
        }
        var s = String(str).replace(/[^\d.]/g, "");
        var firstDot = s.indexOf(".");
        if (firstDot === -1) {
            return s;
        }
        var intPart = s.slice(0, firstDot);
        var rest = s.slice(firstDot + 1).replace(/\./g, "");
        var frac = rest.replace(/\D/g, "").substring(0, 2);
        if (firstDot === 0) {
            intPart = "0";
        }
        return intPart + "." + frac;
    }

    function isMobileDevice() {
        return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent || "");
    }

    var creditsPrepayPollTimer = null;

    function clearCreditsPrepayPoll() {
        if (creditsPrepayPollTimer) {
            clearInterval(creditsPrepayPollTimer);
            creditsPrepayPollTimer = null;
        }
    }

    function resetCreditsPrepayUi() {
        clearCreditsPrepayPoll();
        $("#creditsWechatQrWrap").hide();
        $("#creditsQrCode").attr("src", "").show();
        $("#creditsPayAmount").text("");
        $("#creditsH5PrepayBtn").hide().attr("href", "#");
        $("#creditsAmountInput").prop("disabled", false);
        $("#creditsPurchaseConfirm").prop("disabled", false);
    }

    function pollCreditsChargeDetail(seq) {
        clearCreditsPrepayPoll();
        var checkPayment = function () {
            $.ajax({
                type: "GET",
                contentType: "application/json;charset=utf-8",
                url: "/credit/getChargeDetailBySeq",
                data: { seq: seq },
                success: function (res) {
                    if (!res || res.status !== "success" || !res.data) {
                        return;
                    }
                    var detail = res.data;
                    if (detail && (detail.status === 1 || detail.status === "1" || Number(detail.status) === 1)) {
                        clearCreditsPrepayPoll();
                        $("#creditsPurchaseMask").hide();
                        resetCreditsPrepayUi();
                        loadCreditUserBalance(false);
                        loadChargeDetailsByUserId();
                    }
                }
            });
        };
        // The SaaS endpoint reconciles a pending order against WeChat. Polling
        // more often only repeats an external order query without making the
        // payment visible sooner.
        checkPayment();
        creditsPrepayPollTimer = setInterval(checkPayment, 3000);
    }

    function requestCreditsPrepay() {
        var userId = getCreditsUserId();
        if (!userId) {
            alert(tTextOpenRouter("请先登录"));
            return;
        }
        const st = window.__creditsPageState;
        const amount = Number(st.purchaseAmount || 0);
        if (amount < 0.01) return;
        var feeStr = formatCreditsMoney(amount);
        var $btn = $("#creditsPurchaseConfirm");
        $btn.prop("disabled", true);
        $.ajax({
            type: "POST",
            url: "/credit/prepay",
            contentType: "application/json;charset=utf-8",
            data: JSON.stringify({
                lagiUserId: userId,
                fee: feeStr
            }),
            success: function (res) {
                var ok = res && (String(res.result) === "1" || res.result === 1);
                if (ok) {
                    var paySeq = res.outTradeNo;
                    if (!paySeq) {
                        alert(tTextOpenRouter("获取收款码失败"));
                        $btn.prop("disabled", false);
                        return;
                    }
                    if (res.qrCode) {
                        $("#creditsQrCode").attr("src", "data:image/png;base64," + res.qrCode).show();
                    }
                    $("#creditsPayAmount").text(res.totalFee != null ? res.totalFee : feeStr);
                    $("#creditsWechatQrWrap").css({ display: "flex", flexDirection: "column", alignItems: "center" });
                    if (isMobileDevice() && res.mWebUrl) {
                        $("#creditsH5PrepayBtn").attr("href", res.mWebUrl).show();
                    } else {
                        $("#creditsH5PrepayBtn").hide();
                    }
                    $("#creditsAmountInput").prop("disabled", true);
                    pollCreditsChargeDetail(paySeq);
                } else {
                    alert(tTextOpenRouter("获取收款码失败"));
                    $btn.prop("disabled", false);
                }
            },
            error: function () {
                alert(tTextOpenRouter("获取收款码失败"));
                $btn.prop("disabled", false);
            }
        });
    }

    function formatChargeTime(v) {
        if (!v) return "—";
        var dt = new Date(v);
        if (isNaN(dt.getTime())) return String(v);
        return dt.toLocaleString();
    }

    function mapChargeStatus(v) {
        if (v === 1 || v === "1") return "已支付";
        if (v === 0 || v === "0") return "未支付";
        return "—";
    }

    function updateCreditsTxnTable(transactions) {
        var rows = transactions || [];
        $("#creditsTxnCount").text("共 " + rows.length + " 条");
        $("#creditsTxnTbody").html(renderCreditsTxnRows(rows));
    }

    function loadCreditUserBalance(skipServerSync) {
        if (skipServerSync) {
            return;
        }
        var userId = getCreditsUserId();
        if (!userId) {
            if (window.__creditsPageState) {
                window.__creditsPageState.balance = 0;
            }
            $("#creditsBalanceDisplay").text(tTextOpenRouter("登录后显示余额"));
            return;
        }
        $("#creditsBalanceDisplay").text("¥ —");
        $.ajax({
            type: "GET",
            contentType: "application/json;charset=utf-8",
            url: "/credit/getCreditUserBalance",
            data: { userId: userId },
            success: function (res) {
                var bal = 0;
                if (res && res.status === "success" && res.data && res.data.balance != null) {
                    bal = Number(res.data.balance);
                }
                if (window.__creditsPageState) {
                    window.__creditsPageState.balance = bal;
                }
                $("#creditsBalanceDisplay").text("¥ " + formatCreditsMoney(bal));
            },
            error: function () {
                if (window.__creditsPageState) {
                    window.__creditsPageState.balance = 0;
                }
                $("#creditsBalanceDisplay").text("¥ " + formatCreditsMoney(0));
            }
        });
    }

    function loadChargeDetailsByUserId() {
        var userId = getCreditsUserId();
        if (!userId) {
            $("#creditsTxnCount").text("—");
            $("#creditsTxnTbody").html(
                '<tr><td colspan="4" style="padding:24px;text-align:center;color:#6b7280;font-size:14px;line-height:1.6;">' +
                tTextOpenRouter("登录后可查看交易记录") +
                "</td></tr>"
            );
            return;
        }
        $("#creditsTxnTbody").html('<tr><td colspan="4" style="padding:24px;text-align:center;color:#9ca3af;">加载中...</td></tr>');
        $.ajax({
            type: "GET",
            contentType: "application/json;charset=utf-8",
            url: "/credit/getChargeDetailByUserId",
            data: { userId: userId },
            success: function (res) {
                if (!res || res.status !== "success" || !Array.isArray(res.data)) {
                    updateCreditsTxnTable([]);
                    return;
                }
                var rows = res.data.map(function (item) {
                    return {
                        seq: item.seq || "",
                        date: formatChargeTime(item.time),
                        amount: Number(item.amount || 0),
                        status: mapChargeStatus(item.status)
                    };
                });
                updateCreditsTxnTable(rows);
            },
            error: function () {
                updateCreditsTxnTable([]);
            }
        });
    }

    function ensureCreditsState() {
        if (!window.__creditsPageState) {
            window.__creditsPageState = {
                account: "个人账户: 未登录",
                balance: 0,
                purchaseAmount: 5,
                transactions: []
            };
        }
    }

    function renderCreditsTxnRows(transactions) {
        if (!transactions || !transactions.length) {
            return `<tr><td colspan="4" style="padding:24px;text-align:center;color:#9ca3af;">暂无交易记录</td></tr>`;
        }
        return transactions.map(function(tx) {
            var seq = tx.seq || tx.id || "—";
            return `
                <tr>
                    <td style="${openRouterTdStyle()}">${seq}</td>
                    <td style="${openRouterTdStyle()}">${tx.date || "—"}</td>
                    <td style="${openRouterTdStyle()}">¥ ${formatCreditsMoney(Number(tx.amount || 0))}</td>
                    <td style="${openRouterTdStyle()}">${tx.status || "—"}</td>
                </tr>
            `;
        }).join("");
    }

    function bindCreditsEvents() {
        $("#creditsAddBtn").on("click", function() {
            resetCreditsPrepayUi();
            $("#creditsPurchaseMask").css("display", "flex");
        });
        $("#creditsClosePurchase").on("click", function() {
            resetCreditsPrepayUi();
            $("#creditsPurchaseMask").hide();
        });
        $("#creditsPurchaseMask").on("click", function(e) {
            if (e.target && e.target.id === "creditsPurchaseMask") {
                resetCreditsPrepayUi();
                $("#creditsPurchaseMask").hide();
            }
        });
        $("#creditsAmountInput").on("input", function() {
            const st = window.__creditsPageState;
            var raw = $(this).val();
            var fixed = sanitizeCreditsAmountInput(raw);
            if (fixed !== raw) {
                $(this).val(fixed);
            }
            var amount = Number(fixed === "" || fixed === "." ? 0 : fixed);
            st.purchaseAmount = amount;
            $("#creditsTotalDue").text("¥ " + formatCreditsMoney(st.purchaseAmount));
        });
        $("#creditsPurchaseConfirm").on("click", function() {
            requestCreditsPrepay();
        });
    }

    window.loadCreditsPage = function loadCreditsPage(skipServerBalanceSync) {
        hideChatPartsForStandalonePage();
        ensureCreditsState();
        const st = window.__creditsPageState;
        st.account = isCreditsUserLoggedIn() ? "个人账户" : "个人账户: 未登录";
        var userIdForBalance = getCreditsUserId();
        var loggedIn = !!userIdForBalance;
        var initialBalanceDisplay;
        var balanceDisplayStyle = "font-size:40px;font-weight:700;letter-spacing:0.5px;";
        if (!loggedIn) {
            initialBalanceDisplay = tTextOpenRouter("登录后显示余额");
            balanceDisplayStyle = "font-size:16px;font-weight:600;color:#6b7280;line-height:1.5;max-width:280px;";
        } else if (!skipServerBalanceSync) {
            initialBalanceDisplay = "¥ —";
        } else {
            initialBalanceDisplay = "¥ " + formatCreditsMoney(st.balance);
        }
        var txnTbodyHtml = loggedIn
            ? renderCreditsTxnRows(st.transactions)
            : '<tr><td colspan="4" style="padding:24px;text-align:center;color:#6b7280;font-size:14px;line-height:1.6;">' +
              tTextOpenRouter("登录后可查看交易记录") +
              "</td></tr>";
        var txnCountText = loggedIn ? "共 0 条" : "—";
        var addBtnStyle = loggedIn
            ? "padding:10px 20px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;white-space:nowrap;"
            : "display:none;";
        const html = `
            <div id="credits-container" style="padding:20px;min-height:100%;background:#fff;">
                <div style="margin-bottom:14px;">
                    <h2 style="margin:0;font-size:28px;font-weight:700;">额度中心</h2>
                    <div style="margin-top:6px;color:#6b7280;font-size:13px;">${st.account}</div>
                </div>
                <div style="display:inline-flex;align-items:center;gap:12px;margin-bottom:14px;">
                    <div style="background:#f3f4f6;border:1px solid #e5e7eb;border-radius:10px;padding:14px;">
                        <div id="creditsBalanceDisplay" style="${balanceDisplayStyle}">${initialBalanceDisplay}</div>
                    </div>
                    <button type="button" id="creditsAddBtn" style="${addBtnStyle}">立即充值</button>
                </div>
                <div style="border:1px solid #e5e7eb;border-radius:10px;padding:14px;background:#fff;">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                        <div style="font-size:18px;font-weight:600;">最近交易</div>
                        <div id="creditsTxnCount" style="font-size:12px;color:#6b7280;">${txnCountText}</div>
                    </div>
                    <div style="overflow-x:auto;">
                        <table style="width:100%;border-collapse:collapse;font-size:13px;">
                            <thead><tr style="background:#fff;"><th style="${openRouterThStyle()}">交易序列号</th><th style="${openRouterThStyle()}">日期</th><th style="${openRouterThStyle()}">金额</th><th style="${openRouterThStyle()}">状态</th></tr></thead>
                            <tbody id="creditsTxnTbody">${txnTbodyHtml}</tbody>
                        </table>
                    </div>
                </div>
            </div>
            <div id="creditsPurchaseMask" style="display:none;position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,0.35);z-index:1200;align-items:center;justify-content:center;">
                <div style="width:min(520px,92vw);background:#fff;border-radius:12px;border:1px solid #e5e7eb;box-shadow:0 10px 30px rgba(15,23,42,.18);padding:16px;">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
                        <div style="font-size:20px;font-weight:600;">充值额度</div>
                        <button type="button" id="creditsClosePurchase" style="border:none;background:transparent;font-size:20px;cursor:pointer;color:#6b7280;">×</button>
                    </div>
                    <div style="display:grid;grid-template-columns:1fr auto;align-items:center;gap:10px;margin-bottom:10px;">
                        <label style="font-size:14px;color:#374151;">充值金额</label>
                        <input id="creditsAmountInput" type="number" min="1" step="1" value="${st.purchaseAmount}" style="width:120px;padding:8px;border:1px solid #d1d5db;border-radius:6px;text-align:right;" />
                    </div>
                    <div style="border-top:1px solid #e5e7eb;padding-top:10px;margin-top:10px;font-size:14px;line-height:1.8;">
                        <div style="display:flex;justify-content:space-between;font-weight:700;"><span>应付总额</span><span id="creditsTotalDue">¥ ${formatCreditsMoney(st.purchaseAmount)}</span></div>
                    </div>
                    <div id="creditsWechatQrWrap" style="display:none;flex-direction:column;align-items:center;margin-top:12px;padding-top:12px;border-top:1px solid #e5e7eb;text-align:center;">
                        <div style="font-size:13px;color:#374151;margin-bottom:8px;">${tTextOpenRouter("微信扫码支付")}</div>
                        <img id="creditsQrCode" alt="" style="display:block;width:200px;height:200px;margin:0 auto;border:1px solid #e5e7eb;border-radius:8px;object-fit:contain;background:#fff;" />
                        <div style="margin-top:8px;font-size:14px;color:#374151;">${tTextOpenRouter("支付金额")} <span id="creditsPayAmount" style="font-weight:600;"></span></div>
                        <a id="creditsH5PrepayBtn" href="#" target="_blank" rel="noopener noreferrer" style="display:none;margin-top:10px;font-size:14px;color:#6366f1;">${tTextOpenRouter("打开微信支付")}</a>
                    </div>
                    <button type="button" id="creditsPurchaseConfirm" style="margin-top:12px;width:100%;padding:10px;border:none;border-radius:8px;background:#6366f1;color:#fff;font-size:14px;cursor:pointer;">确认充值</button>
                    <div style="margin-top:8px;color:#6b7280;font-size:12px;">充值成功后将新增交易记录，并更新当前余额。</div>
                </div>
            </div>
        `;
        $("#item-content").html(tHtmlOpenRouter(html));
        bindCreditsEvents();
        loadCreditUserBalance(skipServerBalanceSync);
        loadChargeDetailsByUserId();
    };
})();
