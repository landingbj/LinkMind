(function () {
    var tTextLogs = window.tText || function (s) { return s; };
    var tHtmlLogs = window.tHtml || function (s) { return s; };
    var generationRows = [];
    var generationChartRows = [];
    var generationTotal = 0;
    var jobRows = [];
    var generationPage = 1;
    var pageSize = 20;
    var currentTab = "generations";
    var generationChart = null;
    var generationLoadSeq = 0;

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

    function toInputDateTimeValue(date) {
        var y = date.getFullYear();
        var m = String(date.getMonth() + 1).padStart(2, "0");
        var d = String(date.getDate()).padStart(2, "0");
        var h = String(date.getHours()).padStart(2, "0");
        var mm = String(date.getMinutes()).padStart(2, "0");
        return y + "-" + m + "-" + d + "T" + h + ":" + mm;
    }

    function getRangeMs() {
        var s = $("#logsDateFrom").val();
        var e = $("#logsDateTo").val();
        if (!s || !e) {
            return { min: 0, max: Date.now() };
        }
        var start = new Date(s).getTime();
        var end = new Date(e).getTime();
        if (!isFinite(start) || !isFinite(end)) {
            return { min: 0, max: Date.now() };
        }
        return { min: Math.min(start, end), max: Math.max(start, end) };
    }

    function inRange(ts) {
        var range = getRangeMs();
        return ts >= range.min && ts <= range.max;
    }

    function styleTh() {
        return "padding:10px;border-bottom:1px solid #ececec;text-align:left;color:#6b7280;font-weight:500;white-space:nowrap;font-size:13px;";
    }

    function styleTd() {
        return "padding:12px 10px;border-bottom:1px solid #f3f4f6;white-space:nowrap;font-size:13px;color:#111827;";
    }

    function tabBtnStyle(active) {
        return active
            ? "padding:10px 10px;border:none;border-bottom:2px solid #111827;background:#fff;color:#111827;font-size:14px;cursor:pointer;"
            : "padding:10px 10px;border:none;border-bottom:2px solid transparent;background:#fff;color:#6b7280;font-size:14px;cursor:pointer;";
    }

    function pickTs(row) {
        return Number(row.createdAt != null ? row.createdAt : row.created_at) || 0;
    }

    function resolveLogsApiRange() {
        var range = getRangeMs();
        var days = (range.max - range.min) / 86400000;
        if (days <= 1.5) {
            return "today";
        }
        if (days <= 7.5) {
            return "7d";
        }
        if (days <= 30.5) {
            return "30d";
        }
        return "all";
    }

    function loadGenerationPage(loadId, done) {
        var apiRange = resolveLogsApiRange();
        $.getJSON("/v1/token-statistics/details", {
            range: apiRange,
            page: generationPage,
            pageSize: pageSize
        })
            .done(function (data) {
                if (loadId !== generationLoadSeq) {
                    return;
                }
                generationRows = (data && data.records) ? data.records : [];
                generationTotal = Number(data && data.total != null ? data.total : generationRows.length);
                if (done) {
                    done();
                }
            })
            .fail(function () {
                if (loadId !== generationLoadSeq) {
                    return;
                }
                generationRows = [];
                generationTotal = 0;
                if (done) {
                    done();
                }
            });
    }

    function loadGenerationChartRows(loadId, done) {
        var apiRange = resolveLogsApiRange();
        $.getJSON("/v1/token-statistics/details", { range: apiRange, page: 1, pageSize: 200 })
            .done(function (data) {
                if (loadId !== generationLoadSeq) {
                    return;
                }
                generationChartRows = (data && data.records) ? data.records : [];
                if (done) {
                    done();
                }
            })
            .fail(function () {
                if (loadId !== generationLoadSeq) {
                    return;
                }
                generationChartRows = [];
                if (done) {
                    done();
                }
            });
    }

    function reloadGenerationData(done) {
        var loadId = ++generationLoadSeq;
        var pending = 2;
        function check() {
            pending--;
            if (pending === 0 && loadId === generationLoadSeq && done) {
                done();
            }
        }
        loadGenerationChartRows(loadId, check);
        loadGenerationPage(loadId, check);
    }

    function loadJobRows(done) {
        $.ajax({
            type: "GET",
            contentType: "application/json;charset=utf-8",
            url: "fence/list",
            timeout: 10000,
            success: function (response) {
                if (response && response.code === 0) {
                    done(response.data || []);
                    return;
                }
                done([]);
            },
            error: function () {
                done([]);
            }
        });
    }

    function filteredGenerationRows(rows) {
        var source = rows || [];
        return source.filter(function (r) {
            var ts = pickTs(r);
            return ts ? inRange(ts) : false;
        });
    }

    function filteredJobRows() {
        return jobRows.filter(function (r) {
            var ts = new Date(r.create_time || "").getTime();
            return ts ? inRange(ts) : true;
        });
    }

    function renderGenerationsChart(rows) {
        var dom = document.getElementById("logsGenerationChart");
        if (!dom || typeof echarts === "undefined") return;
        if (generationChart) {
            try { generationChart.dispose(); } catch (e) {}
            generationChart = null;
        }

        var dayMap = {};
        for (var i = 0; i < rows.length; i++) {
            var ts = pickTs(rows[i]);
            if (!ts) continue;
            var d = new Date(ts);
            var key = d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
            dayMap[key] = (dayMap[key] || 0) + 1;
        }
        var keys = Object.keys(dayMap).sort();
        if (!keys.length) {
            $("#logsGenerationChart").hide();
            $("#logsGenerationChartEmpty").css("display", "flex");
            return;
        }
        $("#logsGenerationChart").show();
        $("#logsGenerationChartEmpty").hide();
        generationChart = echarts.init(dom);
        generationChart.setOption({
            grid: { left: 26, right: 12, top: 8, bottom: 22 },
            xAxis: {
                type: "category",
                data: keys.map(function (k) { return k.replace("-", "/").slice(5) + " " + tTextLogs("上午") + "8:00"; }),
                axisLine: { lineStyle: { color: "#e5e7eb" } },
                axisLabel: { color: "#6b7280", fontSize: 11 }
            },
            yAxis: {
                type: "value",
                splitLine: { lineStyle: { color: "#f3f4f6" } },
                axisLine: { show: false },
                axisLabel: { color: "#6b7280", fontSize: 11 }
            },
            tooltip: { trigger: "axis" },
            series: [{
                type: "bar",
                data: keys.map(function (k) { return dayMap[k]; }),
                itemStyle: { color: "#1890ff" },
                barWidth: 38
            }]
        });
    }

    function renderGenerationsTable(rows) {
        var tbody = $("#logsGenerationsTbody");
        tbody.empty();
        if (!rows.length) {
            tbody.append('<tr><td colspan="7" style="padding:24px;text-align:center;color:#9ca3af;">' + tTextLogs("暂无记录") + '</td></tr>');
            $("#logsPageInfo").text("1");
            $("#logsPrevBtn,#logsNextBtn").prop("disabled", true);
            return;
        }

        var totalPages = Math.max(1, Math.ceil(generationTotal / pageSize));
        if (generationPage > totalPages) {
            generationPage = totalPages;
        }
        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            var ts = pickTs(row);
            var pt = Number(row.promptTokens || 0);
            var ct = Number(row.completionTokens || 0);
            var tt = Number(row.totalTokens != null ? row.totalTokens : row.total_tokens) || (pt + ct);
            var provider = row.provider || "Unknown";
            var model = row.model || row.modelName || "Unknown";
            var cachedTokens = Number(row.cachedTokens != null ? row.cachedTokens : row.cached_tokens);
            if (!isFinite(cachedTokens)) {
                cachedTokens = Number(row.savedTokens != null ? row.savedTokens : row.saved_tokens) || 0;
            }
            var speedRaw = row.outputSpeed != null ? row.outputSpeed
                : (row.output_speed != null ? row.output_speed
                : (row.speed != null ? row.speed : null));
            var speed = "";
            if (speedRaw == null || speedRaw === "") {
                speed = (ct > 0 ? (ct / 1.5).toFixed(1) : "0.0") + " tps";
            } else {
                speed = String(speedRaw).indexOf("tps") >= 0 ? String(speedRaw) : (String(speedRaw) + " tps");
            }
            var cost = "¥ " + ((tt / 1000) * 0.002).toFixed(4);
            var timeLabel = ts ? new Date(ts).toLocaleString() : "—";

            tbody.append(
                '<tr>' +
                '<td style="' + styleTd() + '">' + timeLabel + "</td>" +
                '<td style="' + styleTd() + '">' + provider + ' / <a style="color:#4f46e5;text-decoration:underline;cursor:pointer;">' + model + "</a></td>" +
                '<td style="' + styleTd() + '">' + pt + "</td>" +
                '<td style="' + styleTd() + '">' + ct + "</td>" +
                '<td style="' + styleTd() + '">' + cachedTokens + "</td>" +
                '<td style="' + styleTd() + '">' + cost + "</td>" +
                '<td style="' + styleTd() + '">' + speed + "</td>" +
                "</tr>"
            );
        }

        $("#logsPageInfo").text(String(generationPage));
        $("#logsPrevBtn").prop("disabled", generationPage <= 1);
        $("#logsNextBtn").prop("disabled", generationPage >= totalPages);
    }

    function renderJobsTab() {
        var rows = filteredJobRows();
        if (!rows.length) {
            $("#logsTabBody").html(tHtmlLogs(
                '<div style="padding:90px 0;text-align:center;color:#111827;">' +
                '<div style="font-size:32px;font-weight:500;margin-bottom:8px;">暂无进行中的任务</div>' +
                '<div style="font-size:24px;color:#6b7280;line-height:1.7;">任务页会显示当前正在执行的异步任务<br/>（例如由服务端返回的进行中任务）。</div>' +
                "</div>"
            ));
            return;
        }

        var html =
            '<div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:10px;">' +
            '<div style="overflow-x:auto;"><table style="width:100%;border-collapse:collapse;">' +
            "<thead><tr>" +
            '<th style="' + styleTh() + '">时间</th>' +
            '<th style="' + styleTh() + '">过滤器</th>' +
            '<th style="' + styleTh() + '">操作</th>' +
            '<th style="' + styleTh() + '">内容</th>' +
            "</tr></thead><tbody>";
        for (var i = 0; i < rows.length; i++) {
            html += "<tr>" +
                '<td style="' + styleTd() + '">' + (rows[i].create_time || "—") + "</td>" +
                '<td style="' + styleTd() + '">' + (rows[i].filter_name || "—") + "</td>" +
                '<td style="' + styleTd() + '">' + (rows[i].action_type || "—") + "</td>" +
                '<td style="' + styleTd() + '">' + (rows[i].content || "") + "</td>" +
                "</tr>";
        }
        html += "</tbody></table></div></div>";
        $("#logsTabBody").html(tHtmlLogs(html));
    }

    function renderGenerationsTab() {
        var body =
            '<div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:12px;">' +
            '<div id="logsGenerationChart" style="height:120px;"></div>' +
            '<div id="logsGenerationChartEmpty" style="display:none;height:120px;align-items:center;justify-content:center;color:#9ca3af;">暂无图表数据</div>' +
            "</div>" +
            '<div style="height:10px;"></div>' +
            '<div style="background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:10px;">' +
            '<div style="overflow-x:auto;"><table style="width:100%;border-collapse:collapse;">' +
            "<thead><tr>" +
            '<th style="' + styleTh() + '">时间</th>' +
            '<th style="' + styleTh() + '">Provider / Model</th>' +
            '<th style="' + styleTh() + '">输入 Tokens</th>' +
            '<th style="' + styleTh() + '">输出 Tokens</th>' +
            '<th style="' + styleTh() + '">Cached Tokens</th>' +
            '<th style="' + styleTh() + '">费用</th>' +
            '<th style="' + styleTh() + '">输出速度</th>' +
            "</tr></thead><tbody id='logsGenerationsTbody'></tbody></table></div>" +
            '<div style="display:flex;justify-content:center;align-items:center;gap:8px;margin-top:12px;">' +
            '<button id="logsPrevBtn" type="button" style="padding:4px 10px;border:1px solid #e5e7eb;border-radius:6px;background:#fff;cursor:pointer;">‹</button>' +
            '<span id="logsPageInfo" style="min-width:16px;text-align:center;color:#6b7280;">1</span>' +
            '<button id="logsNextBtn" type="button" style="padding:4px 10px;border:1px solid #e5e7eb;border-radius:6px;background:#fff;cursor:pointer;">›</button>' +
            "</div></div>";
        $("#logsTabBody").html(tHtmlLogs(body));
        $("#logsPrevBtn").off("click.logsPage").on("click.logsPage", function () {
            if (generationPage <= 1) {
                return;
            }
            generationPage--;
            var loadId = ++generationLoadSeq;
            loadGenerationPage(loadId, function () {
                renderGenerationsTable(filteredGenerationRows(generationRows));
            });
        });
        $("#logsNextBtn").off("click.logsPage").on("click.logsPage", function () {
            var totalPages = Math.max(1, Math.ceil(generationTotal / pageSize));
            if (generationPage >= totalPages) {
                return;
            }
            generationPage++;
            var loadId = ++generationLoadSeq;
            loadGenerationPage(loadId, function () {
                renderGenerationsTable(filteredGenerationRows(generationRows));
            });
        });
        renderGenerationsChart(filteredGenerationRows(generationChartRows));
        renderGenerationsTable(filteredGenerationRows(generationRows));
    }

    function renderTabBody() {
        if (currentTab === "jobs") {
            renderJobsTab();
            return;
        }
        renderGenerationsTab();
    }

    function bindToolbar() {
        $("#request-logs-container").off(".logs");
        $("#request-logs-container").on("click.logs", ".logs-tab-btn", function () {
            currentTab = String($(this).data("tab"));
            generationPage = 1;
            $(".logs-tab-btn").each(function () {
                var active = String($(this).data("tab")) === currentTab;
                $(this).attr("style", tabBtnStyle(active));
            });
            renderTabBody();
        });
        $("#request-logs-container").on("change.logs", "#logsDateFrom,#logsDateTo", function () {
            generationPage = 1;
            loadAllDataAndRender();
        });
        $("#request-logs-container").on("click.logs", "#logsRefreshBtn", function () {
            generationPage = 1;
            loadAllDataAndRender();
        });
    }

    function loadAllDataAndRender() {
        reloadGenerationData(function () {
            loadJobRows(function (jobs) {
                jobRows = jobs || [];
                renderTabBody();
            });
        });
    }

    window.loadRequestLogsPage = function loadRequestLogsPage() {
        hideChatPartsForStandalonePage();
        var end = new Date();
        var start = new Date(end.getTime() - 7 * 24 * 3600 * 1000);
        var html =
            '<div id="request-logs-container" style="padding:20px;min-height:100%;background:#fff;">' +
            '<div style="display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #efefef;margin-bottom:12px;">' +
            '<div style="display:flex;gap:6px;align-items:center;">' +
            '<button class="logs-tab-btn" data-tab="generations" style="' + tabBtnStyle(true) + '">调用日志</button>' +
            '<button class="logs-tab-btn" data-tab="jobs" style="' + tabBtnStyle(false) + '">过滤日志</button>' +
            "</div>" +
            '<button id="logsRefreshBtn" type="button" style="padding:4px 8px;border:none;background:#fff;color:#6b7280;cursor:pointer;">⟳</button>' +
            "</div>" +
            '<div style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:12px;">' +
            '<div style="display:flex;gap:6px;align-items:center;">' +
            '<span style="font-size:12px;color:#6b7280;">开始时间:</span>' +
            '<input id="logsDateFrom" type="datetime-local" value="' + toInputDateTimeValue(start) + '" style="padding:6px 8px;border:1px solid #e5e7eb;border-radius:6px;font-size:12px;" />' +
            '<span style="font-size:12px;color:#6b7280;">结束时间:</span>' +
            '<input id="logsDateTo" type="datetime-local" value="' + toInputDateTimeValue(end) + '" style="padding:6px 8px;border:1px solid #e5e7eb;border-radius:6px;font-size:12px;" />' +
            "</div>" +
            '<div style="display:flex;gap:8px;">' +
            
            "</div></div>" +
            '<div id="logsTabBody"></div></div>';

        $("#item-content").html(tHtmlLogs(html));
        currentTab = "generations";
        generationPage = 1;
        bindToolbar();
        loadAllDataAndRender();
    };
})();
