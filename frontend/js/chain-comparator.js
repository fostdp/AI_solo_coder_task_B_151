const ChainComparator = (function () {
    const API_PREFIX = '/api/v1';
    let containerEl = null;
    let deviceId = 1;
    const PREFIX_ID = 'cc-';

    function buildChainTypePanel() {
        return `
            <div class="feature-panel active" id="${PREFIX_ID}panel">
                <div class="feature-section-title">链型对比参数</div>
                <div class="feature-row">
                    <label>转速(RPM)</label>
                    <input type="number" id="${PREFIX_ID}speed" value="15" step="1" min="1" max="50">
                </div>
                <div class="feature-row">
                    <label>扭矩(N·m)</label>
                    <input type="number" id="${PREFIX_ID}torque" value="80" step="5" min="10">
                </div>
                <div class="feature-row">
                    <label>刮板深度(m)</label>
                    <input type="number" id="${PREFIX_ID}depth" value="0.12" step="0.01" min="0.05" max="0.20">
                </div>
                <div class="feature-row">
                    <label>刮板宽度(m)</label>
                    <input type="number" id="${PREFIX_ID}width" value="0.25" step="0.01" min="0.10" max="0.40">
                </div>
                <div class="feature-row">
                    <label>安装角度(°)</label>
                    <input type="number" id="${PREFIX_ID}angle" value="40" step="1" min="15" max="60">
                </div>
                <button class="feature-btn blue" id="${PREFIX_ID}btn-compare">
                    🔗 执行三种链型对比
                </button>
                <div id="${PREFIX_ID}result"></div>
            </div>
        `;
    }

    function bindEvents() {
        containerEl.querySelector(`#${PREFIX_ID}btn-compare`)
            .addEventListener('click', doCompareChainTypes);
    }

    function doCompareChainTypes() {
        const params = new URLSearchParams({
            inputSpeedRPM: parseFloat(containerEl.querySelector(`#${PREFIX_ID}speed`).value),
            inputTorque: parseFloat(containerEl.querySelector(`#${PREFIX_ID}torque`).value),
            scraperDepth: parseFloat(containerEl.querySelector(`#${PREFIX_ID}depth`).value),
            scraperWidth: parseFloat(containerEl.querySelector(`#${PREFIX_ID}width`).value),
            scraperAngle: parseFloat(containerEl.querySelector(`#${PREFIX_ID}angle`).value)
        });
        const btn = containerEl.querySelector(`#${PREFIX_ID}btn-compare`);
        btn.disabled = true; btn.textContent = '⏳ 计算中...';

        fetch(`${API_PREFIX}/comparison/chain-types/${deviceId}?${params}`, {method: 'POST'})
            .then(r => r.json())
            .then(data => renderChainComparisonResult(data))
            .catch(err => {
                containerEl.querySelector(`#${PREFIX_ID}result`).innerHTML =
                    `<div class="feature-result" style="color:#ef5350;">请求失败：${err.message}</div>`;
            })
            .finally(() => { btn.disabled = false; btn.textContent = '🔗 执行三种链型对比'; });
    }

    function renderChainComparisonResult(data) {
        const resultDiv = containerEl.querySelector(`#${PREFIX_ID}result`);
        const html = [];
        html.push(`<div class="feature-result">`);
        html.push(`<div style="font-size:11px;color:#8cb4dd;margin-bottom:6px;">
            设备：${data.deviceName} &nbsp;|&nbsp; ${data.inputSpeedRPM} RPM &nbsp;|&nbsp; ${data.scraperDepth}m × ${data.scraperWidth}m @ ${data.scraperAngle}°
        </div>`);

        const classes = { plate: '', round: 'ring', hook: 'hook' };
        const rankLabels = ['🥇 最优', '🥈 次优', '🥉 第三'];

        data.results.forEach((r, idx) => {
            html.push(`
                <div class="chain-compare-card ${classes[r.chainTypeCode]}">
                    <div class="chain-compare-title">
                        <span>${r.chainTypeName} · ${r.chainTypeCode.toUpperCase()}</span>
                        <span class="chain-compare-badge">${rankLabels[idx]}</span>
                    </div>
                    <div style="font-size:10px;color:#98a5b3;margin-bottom:4px;">${r.description}</div>
                    <div class="chain-compare-grid">
                        <span>提水量</span><span>${parseFloat(r.actualWaterFlowLh).toFixed(1)} L/h</span>
                        <span>传动效率</span><span>${(parseFloat(r.transmissionEfficiency)*100).toFixed(1)}%</span>
                        <span>链条张力</span><span>${parseFloat(r.chainTensionN).toFixed(0)} N</span>
                        <span>功率消耗</span><span>${parseFloat(r.powerConsumptionW).toFixed(1)} W</span>
                        <span>摩擦损耗</span><span>${parseFloat(r.frictionLossW).toFixed(1)} W</span>
                        <span>预计寿命</span><span>${parseFloat(r.expectedLifespanHours).toFixed(0)} h</span>
                        <span>最大允许转速</span><span>${parseFloat(r.maxAllowableSpeed).toFixed(1)} RPM</span>
                        <span>共振风险</span><span style="color:${r.resonanceRisk?'#ef5350':'#43a047'};">
                            ${r.resonanceRisk?'⚠️ 有':'✅ 无'}
                        </span>
                    </div>
                </div>
            `);
        });
        html.push(`</div>`);
        resultDiv.innerHTML = html.join('');
    }

    function init(el, devId) {
        if (!el) return;
        containerEl = el;
        deviceId = devId || 1;
        containerEl.innerHTML = buildChainTypePanel();
        bindEvents();
    }

    function render(data) {
        if (data) renderChainComparisonResult(data);
    }

    function setDevice(id) { deviceId = id; }

    return {
        init: init,
        render: render,
        setDevice: setDevice
    };
})();

window.ChainComparator = ChainComparator;
