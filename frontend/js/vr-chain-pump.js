const VRChainPump = (function () {
    const API_PREFIX = '/api/v1';
    let containerEl = null;
    let deviceId = 1;
    let onSpeedChangeCb = null;
    let virtualOperationTimer = null;
    let lastVirtualReset = true;
    let currentSpeed = 1.5;
    const PREFIX_ID = 'vr-';

    function buildVirtualPanel() {
        return `
            <div class="feature-panel" id="${PREFIX_ID}panel">
                <div class="feature-section-title">虚拟操作翻车</div>
                <div style="text-align:center;">
                    <div class="slider-value">
                        链条运行速度：<span id="${PREFIX_ID}speed-val">1.50</span> m/s
                    </div>
                    <input type="range" class="big" id="${PREFIX_ID}speed-slider"
                           min="0.3" max="4.0" step="0.05" value="1.5">
                    <div class="slider-track"></div>
                </div>
                <div class="feature-row">
                    <label>水位系数</label>
                    <input type="range" id="${PREFIX_ID}water-level" min="0.3" max="1.3" step="0.05" value="1.0" style="flex:1;">
                    <span id="${PREFIX_ID}wl-val" style="font-size:11px;font-family:monospace;color:#1e88e5;">1.00</span>
                </div>
                <div class="feature-row" style="gap:4px;">
                    <button class="feature-btn orange" id="${PREFIX_ID}start" style="flex:1;margin:0;">▶️ 启动</button>
                    <button class="feature-btn red" id="${PREFIX_ID}stop" style="flex:1;margin:0;" disabled>⏸️ 暂停</button>
                </div>
                <button class="feature-btn blue" id="${PREFIX_ID}reset" style="padding:6px;margin-top:6px;">
                    🔄 重置累计数据
                </button>
                <div class="feature-section-title">实时运行数据</div>
                <div id="${PREFIX_ID}status" class="virtual-status NORMAL">● 系统正常</div>
                <div class="virtual-stats">
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">提水量(L/h)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}flow">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">瞬时流量(L/s)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}inst-flow">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">链条张力(N)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}tension">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">链轮转速(RPM)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}rpm">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">驱动功率(W)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}power">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">效率(%)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}eff">--</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">累计提水(L)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}total" style="color:#43a047;">0</div>
                    </div>
                    <div class="virtual-stat">
                        <div class="virtual-stat-label">运行时长(s)</div>
                        <div class="virtual-stat-value" id="${PREFIX_ID}seconds" style="color:#43a047;">0</div>
                    </div>
                </div>
                <ul class="warnings-list" id="${PREFIX_ID}warnings"></ul>
            </div>
        `;
    }

    function bindEvents() {
        const speedSlider = containerEl.querySelector(`#${PREFIX_ID}speed-slider`);
        speedSlider.addEventListener('input', () => {
            const v = parseFloat(speedSlider.value);
            containerEl.querySelector(`#${PREFIX_ID}speed-val`).textContent = v.toFixed(2);
            currentSpeed = v;
            if (typeof onSpeedChangeCb === 'function') {
                try { onSpeedChangeCb(v); } catch(e) {}
            }
        });

        const wlSlider = containerEl.querySelector(`#${PREFIX_ID}water-level`);
        wlSlider.addEventListener('input', () => {
            containerEl.querySelector(`#${PREFIX_ID}wl-val`).textContent = parseFloat(wlSlider.value).toFixed(2);
        });

        containerEl.querySelector(`#${PREFIX_ID}start`).addEventListener('click', startVirtualAuto);
        containerEl.querySelector(`#${PREFIX_ID}stop`).addEventListener('click', stopVirtualAuto);
        containerEl.querySelector(`#${PREFIX_ID}reset`).addEventListener('click', resetVirtualOperation);
    }

    function doVirtualStep() {
        const speed = parseFloat(containerEl.querySelector(`#${PREFIX_ID}speed-slider`).value);
        const wl = parseFloat(containerEl.querySelector(`#${PREFIX_ID}water-level`).value);
        const params = new URLSearchParams({
            chainSpeedMs: speed,
            waterLevelFactor: wl,
            operationSeconds: 1,
            resetSession: lastVirtualReset
        });
        lastVirtualReset = false;

        fetch(`${API_PREFIX}/virtual-operation/${deviceId}/step?${params}`, {method: 'POST'})
            .then(r => r.json())
            .then(d => renderVirtualState(d))
            .catch(() => {});
    }

    function renderVirtualState(d) {
        containerEl.querySelector(`#${PREFIX_ID}flow`).textContent = parseFloat(d.currentWaterFlowLh).toFixed(1);
        containerEl.querySelector(`#${PREFIX_ID}inst-flow`).textContent = parseFloat(d.instantaneousFlowLs).toFixed(3);
        containerEl.querySelector(`#${PREFIX_ID}tension`).textContent = parseFloat(d.chainTensionN).toFixed(0);
        containerEl.querySelector(`#${PREFIX_ID}rpm`).textContent = parseFloat(d.sprocketRPM).toFixed(1);
        containerEl.querySelector(`#${PREFIX_ID}power`).textContent = parseFloat(d.powerConsumptionW).toFixed(0);
        containerEl.querySelector(`#${PREFIX_ID}eff`).textContent = parseFloat(d.efficiencyPercent).toFixed(1);
        containerEl.querySelector(`#${PREFIX_ID}total`).textContent = parseFloat(d.totalWaterLiters).toFixed(1);
        containerEl.querySelector(`#${PREFIX_ID}seconds`).textContent = d.operationSeconds;

        const statusEl = containerEl.querySelector(`#${PREFIX_ID}status`);
        statusEl.className = `virtual-status ${d.operationStatus}`;
        const icon = d.operationStatus === 'NORMAL' ? '●' :
                     d.operationStatus === 'WARNING' ? '⚠️' : '🚨';
        statusEl.textContent = `${icon} ${
            {NORMAL:'系统正常运行', WARNING:'注意运行参数', CRITICAL:'危险！请立即调整'}[d.operationStatus]
        }`;

        const wl = containerEl.querySelector(`#${PREFIX_ID}warnings`);
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

    function startVirtualAuto() {
        if (virtualOperationTimer) return;
        containerEl.querySelector(`#${PREFIX_ID}start`).disabled = true;
        containerEl.querySelector(`#${PREFIX_ID}stop`).disabled = false;

        doVirtualStep();
        virtualOperationTimer = setInterval(doVirtualStep, 1000);
    }

    function stopVirtualAuto() {
        if (virtualOperationTimer) {
            clearInterval(virtualOperationTimer);
            virtualOperationTimer = null;
        }
        const startBtn = containerEl.querySelector(`#${PREFIX_ID}start`);
        const stopBtn = containerEl.querySelector(`#${PREFIX_ID}stop`);
        if (startBtn) startBtn.disabled = false;
        if (stopBtn) stopBtn.disabled = true;
    }

    function resetVirtualOperation() {
        stopVirtualAuto();
        lastVirtualReset = true;
        containerEl.querySelector(`#${PREFIX_ID}total`).textContent = '0';
        containerEl.querySelector(`#${PREFIX_ID}seconds`).textContent = '0';
        containerEl.querySelector(`#${PREFIX_ID}warnings`).innerHTML = '';
        const statusEl = containerEl.querySelector(`#${PREFIX_ID}status`);
        statusEl.className = 'virtual-status NORMAL';
        statusEl.textContent = '● 数据已重置';
        [`${PREFIX_ID}flow`, `${PREFIX_ID}inst-flow`, `${PREFIX_ID}tension`,
         `${PREFIX_ID}rpm`, `${PREFIX_ID}power`, `${PREFIX_ID}eff`].forEach(id => {
            containerEl.querySelector(`#${id}`).textContent = '--';
        });
    }

    function getCurrentSpeed() {
        return currentSpeed;
    }

    function init(el, devId, cb) {
        if (!el) return;
        containerEl = el;
        deviceId = devId || 1;
        onSpeedChangeCb = cb || null;
        containerEl.innerHTML = buildVirtualPanel();
        bindEvents();
    }

    function render(state) {
        if (state) renderVirtualState(state);
    }

    function setDevice(id) { deviceId = id; }

    return {
        init: init,
        render: render,
        startAuto: startVirtualAuto,
        stopAuto: stopVirtualAuto,
        getCurrentSpeed: getCurrentSpeed,
        setDevice: setDevice
    };
})();

window.VRChainPump = VRChainPump;
