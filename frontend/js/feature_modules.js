// ============================================================
// 新功能模块集成：链型对比 / 跨时代对比 / 并联优化 / 虚拟操作
// ============================================================

const FeatureModules = (function () {
    const API_PREFIX = '/api/v1';
    let currentDeviceId = 1;
    let featureTab = 'chain-type';
    let virtualOperationTimer = null;

    // ========== 初始化 ==========
    function init(deviceId) {
        currentDeviceId = deviceId || 1;
        buildFeatureUI();
        bindEvents();
        showTab('chain-type');
    }

    // ========== 构造新的Tab容器 ==========
    function buildFeatureUI() {
        if (document.getElementById('feature-modules-root')) return;

        const leftPanel = document.querySelector('.left-panel');
        if (!leftPanel) return;

        const div = document.createElement('div');
        div.id = 'feature-modules-root';
        div.className = 'feature-root';
        div.innerHTML = `
            <div class="feature-header">
                <div class="feature-tabs">
                    <div class="feature-tab active" data-tab="chain-type">链型对比</div>
                    <div class="feature-tab" data-tab="era">跨时代对比</div>
                    <div class="feature-tab" data-tab="parallel">并联优化</div>
                    <div class="feature-tab" data-tab="virtual">虚拟操作</div>
                </div>
            </div>
            <div class="feature-body">
                ${buildChainTypePanel()}
                ${buildEraPanel()}
                ${buildParallelPanel()}
                ${buildVirtualPanel()}
            </div>
            <style>
                .feature-root {
                    border-top: 2px solid var(--border-color);
                    margin-top: 16px;
                    padding-top: 14px;
                }
                .feature-tabs {
                    display: flex;
                    gap: 4px;
                    margin-bottom: 12px;
                    flex-wrap: wrap;
                }
                .feature-tab {
                    flex: 1;
                    min-width: 52px;
                    text-align: center;
                    padding: 6px 4px;
                    font-size: 11px;
                    border-radius: 4px;
                    cursor: pointer;
                    background: rgba(255,255,255,0.05);
                    color: #b8c4d1;
                    border: 1px solid transparent;
                    transition: all 0.15s;
                }
                .feature-tab:hover { background: rgba(255,255,255,0.1); }
                .feature-tab.active {
                    background: linear-gradient(135deg,#1e88e5,#0d47a1);
                    color: #fff;
                    border-color: #1976d2;
                    box-shadow: 0 2px 6px rgba(30,136,229,0.3);
                }
                .feature-body { position: relative; }
                .feature-panel {
                    display: none;
                    animation: fadeIn 0.2s ease;
                }
                .feature-panel.active { display: block; }
                @keyframes fadeIn { from {opacity:0; transform: translateY(4px);} to {opacity:1;} }

                .feature-section-title {
                    font-size: 11px;
                    font-weight: 600;
                    color: #8cb4dd;
                    margin: 10px 0 6px;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                }
                .feature-row {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    margin: 6px 0;
                }
                .feature-row label {
                    font-size: 11px;
                    color: #98a5b3;
                    flex: 0 0 80px;
                }
                .feature-row input, .feature-row select {
                    flex: 1;
                    background: rgba(0,0,0,0.3);
                    color: #e3eaf1;
                    border: 1px solid var(--border-color);
                    padding: 4px 6px;
                    border-radius: 3px;
                    font-size: 11px;
                    outline: none;
                }
                .feature-row input:focus { border-color: #1e88e5; }

                .feature-btn {
                    width: 100%;
                    padding: 8px;
                    margin: 10px 0;
                    background: linear-gradient(135deg,#43a047,#1b5e20);
                    color: #fff;
                    border: none;
                    border-radius: 4px;
                    font-size: 12px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.15s;
                }
                .feature-btn:hover { box-shadow: 0 2px 8px rgba(67,160,71,0.4); }
                .feature-btn.blue { background: linear-gradient(135deg,#1e88e5,#0d47a1); }
                .feature-btn.orange { background: linear-gradient(135deg,#fb8c00,#e65100); }
                .feature-btn.red { background: linear-gradient(135deg,#e53935,#b71c1c); }
                .feature-btn:disabled {
                    opacity: 0.5;
                    cursor: not-allowed;
                }

                .feature-result {
                    margin-top: 10px;
                    padding: 10px;
                    background: rgba(0,0,0,0.25);
                    border-radius: 4px;
                    border: 1px solid var(--border-color);
                    font-size: 11px;
                }
                .chain-compare-card {
                    background: rgba(30,40,60,0.6);
                    padding: 8px;
                    margin: 6px 0;
                    border-radius: 4px;
                    border-left: 3px solid #1e88e5;
                }
                .chain-compare-card.ring { border-left-color: #43a047; }
                .chain-compare-card.hook { border-left-color: #fb8c00; }
                .chain-compare-title {
                    font-weight: 600;
                    color: #e3eaf1;
                    margin-bottom: 4px;
                    display: flex;
                    justify-content: space-between;
                }
                .chain-compare-badge {
                    font-size: 10px;
                    padding: 2px 6px;
                    border-radius: 8px;
                    background: #1e88e5;
                }
                .chain-compare-grid {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 3px 10px;
                    font-size: 10.5px;
                    color: #98a5b3;
                }
                .chain-compare-grid span:last-child {
                    text-align: right;
                    color: #e3eaf1;
                    font-family: monospace;
                }

                .slider-track {
                    position: relative;
                    background: linear-gradient(to right,#43a047,#fbc02d,#e53935);
                    height: 6px;
                    border-radius: 3px;
                    margin: 6px 0;
                }
                .slider-value {
                    text-align: center;
                    font-family: monospace;
                    font-size: 13px;
                    color: #1e88e5;
                    margin: 4px 0;
                }
                input[type="range"].big {
                    width: 100%;
                    height: 32px;
                    cursor: grab;
                }
                input[type="range"].big:active { cursor: grabbing; }
                .virtual-stats {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 6px;
                }
                .virtual-stat {
                    background: rgba(30,40,60,0.5);
                    padding: 6px;
                    border-radius: 3px;
                }
                .virtual-stat-label { font-size: 10px; color: #8cb4dd; }
                .virtual-stat-value {
                    font-size: 13px;
                    font-weight: 600;
                    font-family: monospace;
                    color: #e3eaf1;
                }
                .virtual-status {
                    padding: 6px;
                    border-radius: 4px;
                    margin: 6px 0;
                    text-align: center;
                    font-weight: 600;
                }
                .virtual-status.NORMAL { background: rgba(67,160,71,0.2); color: #81c784; border: 1px solid #43a047; }
                .virtual-status.WARNING { background: rgba(251,140,0,0.2); color: #ffb74d; border: 1px solid #fb8c00; }
                .virtual-status.CRITICAL { background: rgba(229,57,53,0.2); color: #ef5350; border: 1px solid #e53935; }
                .warnings-list {
                    font-size: 10.5px;
                    color: #ffb74d;
                    list-style: none;
                    padding: 0;
                    margin: 6px 0;
                }
                .warnings-list li {
                    padding: 3px 6px;
                    margin: 2px 0;
                    background: rgba(251,140,0,0.1);
                    border-left: 2px solid #fb8c00;
                    border-radius: 2px;
                }
                .era-metric-row {
                    display: flex;
                    justify-content: space-between;
                    padding: 4px 0;
                    border-bottom: 1px dashed rgba(255,255,255,0.08);
                    font-size: 10.5px;
                }
                .era-metric-row:last-child { border: none; }
                .era-metric-name { color: #98a5b3; }
                .era-metric-val { color: #e3eaf1; font-family: monospace; }
                .parallel-device-list {
                    max-height: 120px;
                    overflow-y: auto;
                    margin: 6px 0;
                }
                .parallel-device-item {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 4px 6px;
                    margin: 2px 0;
                    background: rgba(30,40,60,0.5);
                    border-radius: 3px;
                    font-size: 11px;
                    cursor: pointer;
                }
                .parallel-device-item.selected {
                    background: rgba(30,136,229,0.25);
                    border: 1px solid #1e88e5;
                }
                .parallel-assignment {
                    padding: 6px;
                    margin: 4px 0;
                    background: rgba(30,40,60,0.5);
                    border-radius: 3px;
                    border-left: 3px solid #43a047;
                }
                .parallel-assignment-title {
                    font-weight: 600;
                    font-size: 11px;
                    margin-bottom: 4px;
                    color: #e3eaf1;
                }
                .parallel-summary {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 4px;
                    padding: 8px;
                    background: rgba(30,136,229,0.1);
                    border-radius: 4px;
                    margin: 8px 0;
                    border: 1px solid #1e88e5;
                }
                .parallel-summary .label { font-size: 10px; color: #8cb4dd; }
                .parallel-summary .value {
                    font-size: 13px;
                    font-family: monospace;
                    color: #e3eaf1;
                    font-weight: 600;
                }
            </style>
        `;
        leftPanel.appendChild(div);
    }

    // ========== 面板HTML ==========
    function buildChainTypePanel() {
        return `
            <div id="panel-chain-type" class="feature-panel active">
                <div class="feature-section-title">链型对比参数</div>
                <div class="feature-row">
                    <label>转速(RPM)</label>
                    <input type="number" id="ct-speed" value="15" step="1" min="1" max="50">
                </div>
                <div class="feature-row">
                    <label>扭矩(N·m)</label>
                    <input type="number" id="ct-torque" value="80" step="5" min="10">
                </div>
                <div class="feature-row">
                    <label>刮板深度(m)</label>
                    <input type="number" id="ct-depth" value="0.12" step="0.01" min="0.05" max="0.20">
                </div>
                <div class="feature-row">
                    <label>刮板宽度(m)</label>
                    <input type="number" id="ct-width" value="0.25" step="0.01" min="0.10" max="0.40">
                </div>
                <div class="feature-row">
                    <label>安装角度(°)</label>
                    <input type="number" id="ct-angle" value="40" step="1" min="15" max="60">
                </div>
                <button class="feature-btn blue" id="btn-compare-chain">
                    🔗 执行三种链型对比
                </button>
                <div id="ct-result"></div>
            </div>
        `;
    }

    function buildEraPanel() {
        return `
            <div id="panel-era" class="feature-panel">
                <div class="feature-section-title">跨时代对比参数</div>
                <div class="feature-row">
                    <label>链速缩放</label>
                    <input type="number" id="era-speed-ratio" value="1.0" step="0.1" min="0.5" max="2.0">
                </div>
                <div class="feature-row">
                    <label>刮板尺寸缩放</label>
                    <input type="number" id="era-size-scale" value="1.0" step="0.1" min="0.8" max="1.5">
                </div>
                <button class="feature-btn orange" id="btn-compare-era">
                    ⏳ 宋代 vs 现代 跨时代对比
                </button>
                <div id="era-result"></div>
            </div>
        `;
    }

    function buildParallelPanel() {
        return `
            <div id="panel-parallel" class="feature-panel">
                <div class="feature-section-title">并联优化</div>
                <div class="feature-row">
                    <label>目标流量(L/h)</label>
                    <input type="number" id="pl-target-flow" value="80000" step="1000" min="10000">
                </div>
                <div class="feature-row">
                    <label>功率上限(kW)</label>
                    <input type="number" id="pl-max-power" value="15" step="1" min="1">
                </div>
                <div class="feature-row">
                    <label>优化目标</label>
                    <select id="pl-goal">
                        <option value="BALANCED">综合平衡</option>
                        <option value="MAX_FLOW">流量最大化</option>
                        <option value="MIN_POWER">能耗最小化</option>
                    </select>
                </div>
                <div class="feature-section-title">选择并联设备</div>
                <div class="parallel-device-list" id="pl-device-list"></div>
                <button class="feature-btn" id="btn-run-parallel">
                    ⚙️ 运行并联协同优化
                </button>
                <div id="pl-result"></div>
            </div>
        `;
    }

    function buildVirtualPanel() {
        return `
            <div id="panel-virtual" class="feature-panel">
                <div class="feature-section-title">虚拟操作翻车</div>
                <div style="text-align:center;">
                    <div class="slider-value">
                        链条运行速度：<span id="vo-speed-val">1.50</span> m/s
                    </div>
                    <input type="range" class="big" id="vo-speed-slider"
                           min="0.3" max="4.0" step="0.05" value="1.5">
                    <div class="slider-track"></div>
                </div>
                <div class="feature-row">
                    <label>水位系数</label>
                    <input type="range" id="vo-water-level" min="0.3" max="1.3" step="0.05" value="1.0" style="flex:1;">
                    <span id="vo-wl-val" style="font-size:11px;font-family:monospace;color:#1e88e5;">1.00</span>
                </div>
                <div class="feature-row" style="gap:4px;">
                    <button class="feature-btn orange" id="vo-start" style="flex:1;margin:0;">▶️ 启动</button>
                    <button class="feature-btn red" id="vo-stop" style="flex:1;margin:0;" disabled>⏸️ 暂停</button>
                </div>
                <button class="feature-btn blue" id="vo-reset" style="padding:6px;margin-top:6px;">
                    🔄 重置累计数据
                </button>
                <div class="feature-section-title">实时运行数据</div>
                <div id="vo-status" class="virtual-status NORMAL">● 系统正常</div>
                <div class="virtual-stats">
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">提水量(L/h)</div>
                        <div class="virtual-stat-value" id="vo-flow">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">瞬时流量(L/s)</div>
                        <div class="virtual-stat-value" id="vo-inst-flow">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">链条张力(N)</div>
                        <div class="virtual-stat-value" id="vo-tension">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">链轮转速(RPM)</div>
                        <div class="virtual-stat-value" id="vo-rpm">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">驱动功率(W)</div>
                        <div class="virtual-stat-value" id="vo-power">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">效率(%)</div>
                        <div class="virtual-stat-value" id="vo-eff">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">累计提水(L)</div>
                        <div class="virtual-stat-value" id="vo-total" style="color:#43a047;">0</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">运行时长(s)</div>
                        <div class="virtual-stat-value" id="vo-seconds" style="color:#43a047;">0</div>
                    </div>
                </div>
                <ul class="warnings-list" id="vo-warnings"></ul>
            </div>
        `;
    }

    // ========== 事件绑定 ==========
    function bindEvents() {
        document.querySelectorAll('.feature-tab').forEach(tab => {
            tab.addEventListener('click', () => showTab(tab.dataset.tab));
        });

        document.getElementById('btn-compare-chain').addEventListener('click', runChainTypeCompare);
        document.getElementById('btn-compare-era').addEventListener('click', runEraCompare);
        document.getElementById('btn-run-parallel').addEventListener('click', runParallelOptimization);

        const speedSlider = document.getElementById('vo-speed-slider');
        speedSlider.addEventListener('input', () => {
            document.getElementById('vo-speed-val').textContent = parseFloat(speedSlider.value).toFixed(2);
        });

        const wlSlider = document.getElementById('vo-water-level');
        wlSlider.addEventListener('input', () => {
            document.getElementById('vo-wl-val').textContent = parseFloat(wlSlider.value).toFixed(2);
        });

        document.getElementById('vo-start').addEventListener('click', startVirtualOperation);
        document.getElementById('vo-stop').addEventListener('click', stopVirtualOperation);
        document.getElementById('vo-reset').addEventListener('click', resetVirtualOperation);

        buildDeviceSelector();
    }

    function buildDeviceSelector() {
        fetch(`${API_PREFIX}/devices`)
            .then(r => r.json())
            .then(devices => {
                const list = document.getElementById('pl-device-list');
                if (!list) return;
                list.innerHTML = '';
                devices.forEach(d => {
                    const div = document.createElement('div');
                    div.className = 'parallel-device-item' + (d.device_id <= 3 ? ' selected' : '');
                    div.dataset.id = d.device_id;
                    div.innerHTML = `<span>#${d.device_id} ${d.device_name}</span><span>${d.nominal_sprocket_speed_rpm} RPM</span>`;
                    div.addEventListener('click', () => div.classList.toggle('selected'));
                    list.appendChild(div);
                });
            })
            .catch(() => {});
    }

    function showTab(name) {
        featureTab = name;
        document.querySelectorAll('.feature-tab').forEach(t => {
            t.classList.toggle('active', t.dataset.tab === name);
        });
        document.querySelectorAll('.feature-panel').forEach(p => {
            p.classList.toggle('active', p.id === `panel-${name}`);
        });
    }

    // ========== 链型对比 ==========
    function runChainTypeCompare() {
        const params = new URLSearchParams({
            inputSpeedRPM: parseFloat(document.getElementById('ct-speed').value),
            inputTorque: parseFloat(document.getElementById('ct-torque').value),
            scraperDepth: parseFloat(document.getElementById('ct-depth').value),
            scraperWidth: parseFloat(document.getElementById('ct-width').value),
            scraperAngle: parseFloat(document.getElementById('ct-angle').value)
        });
        const btn = document.getElementById('btn-compare-chain');
        btn.disabled = true; btn.textContent = '⏳ 计算中...';

        fetch(`${API_PREFIX}/comparison/chain-types/${currentDeviceId}?${params}`, {method: 'POST'})
            .then(r => r.json())
            .then(data => renderChainTypeResult(data))
            .catch(err => {
                document.getElementById('ct-result').innerHTML =
                    `<div class="feature-result" style="color:#ef5350;">请求失败：${err.message}</div>`;
            })
            .finally(() => { btn.disabled = false; btn.textContent = '🔗 执行三种链型对比'; });
    }

    function renderChainTypeResult(data) {
        const resultDiv = document.getElementById('ct-result');
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

    // ========== 跨时代对比 ==========
    function runEraCompare() {
        const params = new URLSearchParams({
            chainSpeedRatio: parseFloat(document.getElementById('era-speed-ratio').value),
            scraperSizeScale: parseFloat(document.getElementById('era-size-scale').value)
        });
        const btn = document.getElementById('btn-compare-era');
        btn.disabled = true; btn.textContent = '⏳ 计算中...';

        fetch(`${API_PREFIX}/comparison/eras/${currentDeviceId}?${params}`, {method: 'POST'})
            .then(r => r.json())
            .then(data => renderEraResult(data))
            .catch(err => {
                document.getElementById('era-result').innerHTML =
                    `<div class="feature-result" style="color:#ef5350;">请求失败：${err.message}</div>`;
            })
            .finally(() => { btn.disabled = false; btn.textContent = '⏳ 宋代 vs 现代 跨时代对比'; });
    }

    function renderEraResult(data) {
        const resultDiv = document.getElementById('era-result');
        const html = [];
        html.push('<div class="feature-result">');

        // Key metrics
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

        // Each era
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

    // ========== 并联优化 ==========
    function runParallelOptimization() {
        const ids = Array.from(document.querySelectorAll('#pl-device-list .parallel-device-item.selected'))
            .map(el => parseInt(el.dataset.id));

        if (ids.length < 1) {
            alert('请至少选择一台设备');
            return;
        }

        const params = new URLSearchParams();
        ids.forEach(id => params.append('deviceIds', id));
        params.append('targetTotalFlowLh', parseFloat(document.getElementById('pl-target-flow').value));
        params.append('maxTotalPowerKW', parseFloat(document.getElementById('pl-max-power').value));
        params.append('optimizationGoal', document.getElementById('pl-goal').value);

        const btn = document.getElementById('btn-run-parallel');
        btn.disabled = true; btn.textContent = '⏳ 协同优化中...';

        fetch(`${API_PREFIX}/optimization/parallel?${params.toString()}`, {method: 'POST'})
            .then(r => r.json())
            .then(data => renderParallelResult(data, ids))
            .catch(err => {
                document.getElementById('pl-result').innerHTML =
                    `<div class="feature-result" style="color:#ef5350;">请求失败：${err.message}</div>`;
            })
            .finally(() => { btn.disabled = false; btn.textContent = '⚙️ 运行并联协同优化'; });
    }

    function renderParallelResult(data, ids) {
        const resultDiv = document.getElementById('pl-result');
        const html = [];
        html.push('<div class="feature-result">');

        // 汇总
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

        // 各设备分配
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

        // Trace
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

    // ========== 虚拟操作 ==========
    let lastVirtualReset = true;

    function startVirtualOperation() {
        if (virtualOperationTimer) return;
        document.getElementById('vo-start').disabled = true;
        document.getElementById('vo-stop').disabled = false;

        const step = () => {
            const speed = parseFloat(document.getElementById('vo-speed-slider').value);
            const wl = parseFloat(document.getElementById('vo-water-level').value);
            const params = new URLSearchParams({
                chainSpeedMs: speed,
                waterLevelFactor: wl,
                operationSeconds: 1,
                resetSession: lastVirtualReset
            });
            lastVirtualReset = false;

            fetch(`${API_PREFIX}/virtual-operation/${currentDeviceId}/step?${params}`, {method: 'POST'})
                .then(r => r.json())
                .then(d => renderVirtualStep(d))
                .catch(() => {});
        };

        step();
        virtualOperationTimer = setInterval(step, 1000);
    }

    function stopVirtualOperation() {
        if (virtualOperationTimer) {
            clearInterval(virtualOperationTimer);
            virtualOperationTimer = null;
        }
        document.getElementById('vo-start').disabled = false;
        document.getElementById('vo-stop').disabled = true;
    }

    function resetVirtualOperation() {
        stopVirtualOperation();
        lastVirtualReset = true;
        document.getElementById('vo-total').textContent = '0';
        document.getElementById('vo-seconds').textContent = '0';
        document.getElementById('vo-warnings').innerHTML = '';
        document.getElementById('vo-status').className = 'virtual-status NORMAL';
        document.getElementById('vo-status').textContent = '● 数据已重置';
        ['vo-flow','vo-inst-flow','vo-tension','vo-rpm','vo-power','vo-eff'].forEach(id => {
            document.getElementById(id).textContent = '--';
        });
    }

    function renderVirtualStep(d) {
        document.getElementById('vo-flow').textContent = parseFloat(d.currentWaterFlowLh).toFixed(1);
        document.getElementById('vo-inst-flow').textContent = parseFloat(d.instantaneousFlowLs).toFixed(3);
        document.getElementById('vo-tension').textContent = parseFloat(d.chainTensionN).toFixed(0);
        document.getElementById('vo-rpm').textContent = parseFloat(d.sprocketRPM).toFixed(1);
        document.getElementById('vo-power').textContent = parseFloat(d.powerConsumptionW).toFixed(0);
        document.getElementById('vo-eff').textContent = parseFloat(d.efficiencyPercent).toFixed(1);
        document.getElementById('vo-total').textContent = parseFloat(d.totalWaterLiters).toFixed(1);
        document.getElementById('vo-seconds').textContent = d.operationSeconds;

        const statusEl = document.getElementById('vo-status');
        statusEl.className = `virtual-status ${d.operationStatus}`;
        const icon = d.operationStatus === 'NORMAL' ? '●' :
                     d.operationStatus === 'WARNING' ? '⚠️' : '🚨';
        statusEl.textContent = `${icon} ${
            {NORMAL:'系统正常运行', WARNING:'注意运行参数', CRITICAL:'危险！请立即调整'}[d.operationStatus]
        }`;

        const wl = document.getElementById('vo-warnings');
        if (d.warnings && d.warnings.length) {
            wl.innerHTML = d.warnings.map(w => `<li>${w}</li>`).join('');
        } else {
            wl.innerHTML = '';
        }

        if (d.chainLinkPositions && d.chainLinkPositions.length >= 15) {
            try {
                if (typeof window.ChainPump3D !== 'undefined'
                    && typeof window.ChainPump3D.updateChainLinkPositions === 'function') {
                    window.ChainPump3D.updateChainLinkPositions(d.chainLinkPositions);
                }
            } catch(e) {}
        }

        if (typeof window.EfficiencyPanel !== 'undefined'
            && typeof window.EfficiencyPanel.addRealtimeFlowPoint === 'function') {
            window.EfficiencyPanel.addRealtimeFlowPoint(parseFloat(d.currentWaterFlowLh));
        }
    }

    // ========== 公开API ==========
    return {
        init: init,
        setDevice: function(id) { currentDeviceId = id; },
        stopAll: function() { stopVirtualOperation(); }
    };
})();

window.FeatureModules = FeatureModules;
