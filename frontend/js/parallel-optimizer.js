const ParallelOptimizer = (function () {
    const API_PREFIX = '/api/v1';
    let containerEl = null;
    let deviceIds = [];
    const PREFIX_ID = 'po-';

    function buildParallelPanel() {
        return `
            <div class="feature-panel" id="${PREFIX_ID}panel">
                <div class="feature-section-title">并联优化</div>
                <div class="feature-row">
                    <label>目标流量(L/h)</label>
                    <input type="number" id="${PREFIX_ID}target-flow" value="80000" step="1000" min="10000">
                </div>
                <div class="feature-row">
                    <label>功率上限(kW)</label>
                    <input type="number" id="${PREFIX_ID}max-power" value="15" step="1" min="1">
                </div>
                <div class="feature-row">
                    <label>优化目标</label>
                    <select id="${PREFIX_ID}goal">
                        <option value="BALANCED">综合平衡</option>
                        <option value="MAX_FLOW">流量最大化</option>
                        <option value="MIN_POWER">能耗最小化</option>
                    </select>
                </div>
                <div class="feature-section-title">选择并联设备</div>
                <div class="parallel-device-list" id="${PREFIX_ID}device-list"></div>
                <button class="feature-btn" id="${PREFIX_ID}btn-run">
                    ⚙️ 运行并联协同优化
                </button>
                <div id="${PREFIX_ID}result"></div>
            </div>
        `;
    }

    function bindEvents() {
        containerEl.querySelector(`#${PREFIX_ID}btn-run`)
            .addEventListener('click', doRunParallelOptimization);
        buildDeviceSelector();
    }

    function buildDeviceSelector() {
        fetch(`${API_PREFIX}/devices`)
            .then(r => r.json())
            .then(devices => {
                const list = containerEl.querySelector(`#${PREFIX_ID}device-list`);
                if (!list) return;
                list.innerHTML = '';
                devices.forEach(d => {
                    const div = document.createElement('div');
                    const isPreSelected = (deviceIds && deviceIds.length > 0)
                        ? deviceIds.includes(d.device_id)
                        : (d.device_id <= 3);
                    div.className = 'parallel-device-item' + (isPreSelected ? ' selected' : '');
                    div.dataset.id = d.device_id;
                    div.innerHTML = `<span>#${d.device_id} ${d.device_name}</span><span>${d.nominal_sprocket_speed_rpm} RPM</span>`;
                    div.addEventListener('click', () => div.classList.toggle('selected'));
                    list.appendChild(div);
                });
            })
            .catch(() => {});
    }

    function doRunParallelOptimization() {
        const ids = Array.from(containerEl.querySelectorAll(`#${PREFIX_ID}device-list .parallel-device-item.selected`))
            .map(el => parseInt(el.dataset.id));

        if (ids.length < 1) {
            alert('请至少选择一台设备');
            return;
        }

        const params = new URLSearchParams();
        ids.forEach(id => params.append('deviceIds', id));
        params.append('targetTotalFlowLh', parseFloat(containerEl.querySelector(`#${PREFIX_ID}target-flow`).value));
        params.append('maxTotalPowerKW', parseFloat(containerEl.querySelector(`#${PREFIX_ID}max-power`).value));
        params.append('optimizationGoal', containerEl.querySelector(`#${PREFIX_ID}goal`).value);

        const btn = containerEl.querySelector(`#${PREFIX_ID}btn-run`);
        btn.disabled = true; btn.textContent = '⏳ 协同优化中...';

        fetch(`${API_PREFIX}/optimization/parallel?${params.toString()}`, {method: 'POST'})
            .then(r => r.json())
            .then(data => renderParallelResult(data, ids))
            .catch(err => {
                containerEl.querySelector(`#${PREFIX_ID}result`).innerHTML =
                    `<div class="feature-result" style="color:#ef5350;">请求失败：${err.message}</div>`;
            })
            .finally(() => { btn.disabled = false; btn.textContent = '⚙️ 运行并联协同优化'; });
    }

    function renderParallelResult(data, ids) {
        const resultDiv = containerEl.querySelector(`#${PREFIX_ID}result`);
        const html = [];
        html.push('<div class="feature-result">');

        html.push(`<div class="parallel-summary">
            <div><div class="label">预测总流量</div><div class="value">${parseFloat(data.totalPredictedFlowLh).toFixed(0)} L/h</div></div>
            <div><div class="label">总功耗</div><div class="value">${parseFloat(data.totalPowerConsumptionKW).toFixed(2)} kW</div></div>
            <div><div class="label">平均效率</div><div class="value">${(parseFloat(data.averageEfficiency)*100).toFixed(1)}%</div></div>
            <div><div class="label">负载均衡</div><div class="value">±${(parseFloat(data.loadBalanceStdDev)*100).toFixed(1)}%</div></div>
            <div><div class="label">协同增益</div><div class="value" style="color:#43a047;">${parseFloat(data.coordinationGain).toFixed(1)}%</div></div>
            <div><div class="label">迭代收敛</div><div class="value" style="color:${data.converged?'#43a047':'#fb8c00'};">
                ${data.converged ? '✅ 是' : '⚠️ 否'} · ${data.iterations}步 · ${data.computationTimeMs}ms
            </div></div>
        </div>`);

        data.assignments.forEach(a => {
            const ratioPct = (parseFloat(a.assignedFlowRatio) * 100).toFixed(1);
            html.push(`<div class="parallel-assignment">
                <div class="parallel-assignment-title">
                    #${a.deviceId} ${a.deviceName} · 承担 ${ratioPct}% 流量
                </div>
                <div class="chain-compare-grid">
                    <span>分配提水量</span><span>${parseFloat(a.assignedFlowLh).toFixed(0)} L/h</span>
                    <span>最佳链速</span><span>${parseFloat(a.optimalChainSpeedMs).toFixed(2)} m/s</span>
                    <span>链轮转速</span><span>${parseFloat(a.optimalSprocketRPM).toFixed(1)} RPM</span>
                    <span>刮板深度</span><span>${parseFloat(a.scraperDepth).toFixed(3)} m</span>
                    <span>预测张力</span><span>${parseFloat(a.predictedTensionN).toFixed(0)} N</span>
                    <span>单机功耗</span><span>${parseFloat(a.powerConsumptionKW).toFixed(2)} kW</span>
                    <span>单机效率</span><span>${(parseFloat(a.individualEfficiency)*100).toFixed(1)}%</span>
                </div>
            </div>`);
        });

        if (data.trace && data.trace.length > 0) {
            const last = data.trace[data.trace.length - 1];
            const first = data.trace[0];
            const improve = (parseFloat(first.objectiveValue) - parseFloat(last.objectiveValue))
                / Math.max(parseFloat(first.objectiveValue), 1e-9) * 100;
            html.push(`<div style="margin-top:8px;font-size:10px;color:#8cb4dd;">
                迭代进度：初始目标值 ${parseFloat(first.objectiveValue).toFixed(2)}
                → 终值 ${parseFloat(last.objectiveValue).toFixed(2)}
                <span style="color:#43a047;">(改善 ${improve.toFixed(1)}%)</span>
            </div>`);
        }

        html.push('</div>');
        resultDiv.innerHTML = html.join('');
    }

    function init(el, devIds) {
        if (!el) return;
        containerEl = el;
        deviceIds = devIds || [];
        containerEl.innerHTML = buildParallelPanel();
        bindEvents();
    }

    function render(data) {
        if (data) renderParallelResult(data);
    }

    function setDeviceIds(ids) { deviceIds = ids; }

    return {
        init: init,
        render: render,
        setDeviceIds: setDeviceIds
    };
})();

window.ParallelOptimizer = ParallelOptimizer;
