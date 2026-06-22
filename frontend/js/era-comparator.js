const EraComparator = (function () {
    const API_PREFIX = '/api/v1';
    let containerEl = null;
    let deviceId = 1;
    const PREFIX_ID = 'ec-';

    function buildEraPanel() {
        return `
            <div class="feature-panel" id="${PREFIX_ID}panel">
                <div class="feature-section-title">跨时代对比参数</div>
                <div class="feature-row">
                    <label>链速缩放</label>
                    <input type="number" id="${PREFIX_ID}speed-ratio" value="1.0" step="0.1" min="0.5" max="2.0">
                </div>
                <div class="feature-row">
                    <label>刮板尺寸缩放</label>
                    <input type="number" id="${PREFIX_ID}size-scale" value="1.0" step="0.1" min="0.8" max="1.5">
                </div>
                <button class="feature-btn orange" id="${PREFIX_ID}btn-compare">
                    ⏳ 宋代 vs 现代 跨时代对比
                </button>
                <div id="${PREFIX_ID}result"></div>
            </div>
        `;
    }

    function bindEvents() {
        containerEl.querySelector(`#${PREFIX_ID}btn-compare`)
            .addEventListener('click', doCompareEras);
    }

    function doCompareEras() {
        const params = new URLSearchParams({
            chainSpeedRatio: parseFloat(containerEl.querySelector(`#${PREFIX_ID}speed-ratio`).value),
            scraperSizeScale: parseFloat(containerEl.querySelector(`#${PREFIX_ID}size-scale`).value)
        });
        const btn = containerEl.querySelector(`#${PREFIX_ID}btn-compare`);
        btn.disabled = true; btn.textContent = '⏳ 计算中...';

        fetch(`${API_PREFIX}/comparison/eras/${deviceId}?${params}`, {method: 'POST'})
            .then(r => r.json())
            .then(data => renderEraComparisonResult(data))
            .catch(err => {
                containerEl.querySelector(`#${PREFIX_ID}result`).innerHTML =
                    `<div class="feature-result" style="color:#ef5350;">请求失败：${err.message}</div>`;
            })
            .finally(() => { btn.disabled = false; btn.textContent = '⏳ 宋代 vs 现代 跨时代对比'; });
    }

    function renderEraComparisonResult(data) {
        const resultDiv = containerEl.querySelector(`#${PREFIX_ID}result`);
        const html = [];
        html.push('<div class="feature-result">');

        if (data.keyMetrics && data.keyMetrics.length) {
            html.push('<div class="feature-section-title">📊 核心指标对比</div>');
            data.keyMetrics.forEach(m => {
                const delta = (parseFloat(m.improvementRatio) - 1) * 100;
                const color = delta > 0 ? '#43a047' : (delta < -20 ? '#ef5350' : '#fb8c00');
                html.push(`
                    <div style="padding:6px 4px;border-bottom:1px solid rgba(255,255,255,0.06);">
                        <div style="display:flex;justify-content:space-between;font-size:11px;">
                            <span style="color:#e3eaf1;font-weight:600;">${m.metricName} (${m.unit})</span>
                            <span style="color:${color};">${delta > 0 ? '↑' : delta < 0 ? '↓' : '→'}
                                ${Math.abs(delta).toFixed(1)}%</span>
                        </div>
                        <div style="display:flex;gap:8px;margin-top:2px;font-size:10.5px;font-family:monospace;">
                            <span style="flex:1;color:#fb8c00;">宋 ${parseFloat(m.ancientValue).toFixed(2)}</span>
                            <span style="flex:1;color:#43a047;text-align:right;">今 ${parseFloat(m.modernValue).toFixed(2)}</span>
                        </div>
                        <div style="font-size:10px;color:#98a5b3;margin-top:2px;">${m.improvementDescription}</div>
                    </div>
                `);
            });
        }

        data.results.forEach(r => {
            const isAncient = r.eraCode === 'ancient_song';
            html.push(`<div class="chain-compare-card ${isAncient ? 'hook' : ''}" style="border-left-color:${isAncient ? '#fb8c00':'#1e88e5'};">
                <div class="chain-compare-title">
                    <span>${isAncient ? '🏯' : '🏭'} ${r.eraName}</span>
                    <span class="chain-compare-badge" style="background:${isAncient ? '#fb8c00':'#1e88e5'};">
                        总效率 ${(parseFloat(r.totalEfficiency)*100).toFixed(1)}%
                    </span>
                </div>
                <div style="font-size:10px;color:#98a5b3;margin-bottom:4px;">${r.description}</div>
                <div class="chain-compare-grid">
                    <span>车架材质</span><span>${r.frameMaterial}</span>
                    <span>链条材质</span><span>${r.chainMaterial}</span>
                    <span>驱动方式</span><span>${r.driveType}</span>
                    <span>动力来源</span><span>${r.powerSource}</span>
                    <span>提水量</span><span>${parseFloat(r.waterFlowLh).toFixed(0)} L/h</span>
                    <span>输入功率</span><span>${parseFloat(r.powerInputKW).toFixed(2)} kW</span>
                    <span>链条张力</span><span>${parseFloat(r.chainTensionN).toFixed(0)} N</span>
                    <span>能量成本</span><span>${parseFloat(r.energyCostPerCubicYuan).toFixed(3)} 元/m³</span>
                    <span>年维护工时</span><span>${parseFloat(r.maintenanceHoursPerYear).toFixed(0)} h</span>
                    <span>设备寿命</span><span>${parseFloat(r.lifespanYears).toFixed(1)} 年</span>
                    <span>噪音水平</span><span>${parseFloat(r.noiseLevelDB).toFixed(0)} dB</span>
                    <span>成本系数</span><span>${parseFloat(r.costFactor).toFixed(1)}×</span>
                </div>
                ${r.historicalContext ? `
                    <div style="margin-top:6px;padding-top:6px;border-top:1px dashed rgba(255,255,255,0.1);font-size:10px;color:#8cb4dd;">
                        ${Object.entries(r.historicalContext).map(([k,v]) =>
                            `<div><strong>${k}：</strong>${typeof v === 'number' ? v.toLocaleString() : v}</div>`
                        ).join('')}
                    </div>
                ` : ''}
            </div>`);
        });

        html.push('</div>');
        resultDiv.innerHTML = html.join('');
    }

    function init(el, devId) {
        if (!el) return;
        containerEl = el;
        deviceId = devId || 1;
        containerEl.innerHTML = buildEraPanel();
        bindEvents();
    }

    function render(data) {
        if (data) renderEraComparisonResult(data);
    }

    function setDevice(id) { deviceId = id; }

    return {
        init: init,
        render: render,
        setDevice: setDevice
    };
})();

window.EraComparator = EraComparator;
