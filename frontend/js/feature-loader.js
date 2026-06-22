const FeatureModules = (function () {
    let currentDeviceId = 1;
    let featureTab = 'chain-type';
    let cssLoaded = false;
    let componentsReady = false;
    const MODULE_SCRIPTS = [
        'js/chain-comparator.js',
        'js/era-comparator.js',
        'js/parallel-optimizer.js',
        'js/vr-chain-pump.js'
    ];
    const CSS_PATH = 'css/common-feature.css';

    function loadCSS() {
        if (cssLoaded) return Promise.resolve();
        return new Promise(resolve => {
            if (document.querySelector(`link[href="${CSS_PATH}"]`)) {
                cssLoaded = true;
                resolve();
                return;
            }
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = CSS_PATH;
            link.onload = () => { cssLoaded = true; resolve(); };
            link.onerror = () => { cssLoaded = true; resolve(); };
            document.head.appendChild(link);
        });
    }

    function loadScript(src) {
        return new Promise((resolve, reject) => {
            if (document.querySelector(`script[src="${src}"]`)) {
                resolve();
                return;
            }
            const s = document.createElement('script');
            s.src = src;
            s.onload = resolve;
            s.onerror = reject;
            document.head.appendChild(s);
        });
    }

    function loadAllComponents() {
        return loadCSS().then(() => {
            return MODULE_SCRIPTS.reduce((chain, src) => {
                return chain.then(() => loadScript(src));
            }, Promise.resolve());
        });
    }

    function waitForComponents() {
        const check = () => {
            return window.ChainComparator
                && window.EraComparator
                && window.ParallelOptimizer
                && window.VRChainPump;
        };
        if (check()) return Promise.resolve();
        return new Promise(resolve => {
            const timer = setInterval(() => {
                if (check()) {
                    clearInterval(timer);
                    resolve();
                }
            }, 50);
        });
    }

    function buildFeatureUI() {
        if (document.getElementById('feature-modules-root')) return;

        const leftPanel = document.querySelector('.left-panel') || document.querySelector('.sidebar-left');
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
                <div id="cc-container"></div>
                <div id="ec-container"></div>
                <div id="po-container"></div>
                <div id="vr-container"></div>
            </div>
        `;
        leftPanel.appendChild(div);

        initSubPanels();
        bindTabEvents();
        showTab('chain-type');
    }

    function initSubPanels() {
        const ccEl = document.getElementById('cc-container');
        const ecEl = document.getElementById('ec-container');
        const poEl = document.getElementById('po-container');
        const vrEl = document.getElementById('vr-container');

        if (window.ChainComparator) window.ChainComparator.init(ccEl, currentDeviceId);
        if (window.EraComparator) window.EraComparator.init(ecEl, currentDeviceId);
        if (window.ParallelOptimizer) window.ParallelOptimizer.init(poEl, []);
        if (window.VRChainPump) window.VRChainPump.init(vrEl, currentDeviceId, null);
    }

    function bindTabEvents() {
        document.querySelectorAll('#feature-modules-root .feature-tab').forEach(tab => {
            tab.addEventListener('click', () => showTab(tab.dataset.tab));
        });
    }

    function showTab(name) {
        featureTab = name;
        const root = document.getElementById('feature-modules-root');
        if (!root) return;

        root.querySelectorAll('.feature-tab').forEach(t => {
            t.classList.toggle('active', t.dataset.tab === name);
        });

        const map = {
            'chain-type': 'cc-container',
            'era': 'ec-container',
            'parallel': 'po-container',
            'virtual': 'vr-container'
        };
        const targetId = map[name];

        root.querySelectorAll('.feature-body > div').forEach(el => {
            el.style.display = (el.id === targetId) ? 'block' : 'none';
        });
    }

    function init(deviceId) {
        currentDeviceId = deviceId || 1;

        return loadAllComponents()
            .then(() => waitForComponents())
            .then(() => {
                componentsReady = true;
                buildFeatureUI();
            })
            .catch(err => {
                console.warn('Feature modules load failed:', err);
            });
    }

    function setDevice(id) {
        currentDeviceId = id;
        if (window.ChainComparator && typeof window.ChainComparator.setDevice === 'function') {
            window.ChainComparator.setDevice(id);
        }
        if (window.EraComparator && typeof window.EraComparator.setDevice === 'function') {
            window.EraComparator.setDevice(id);
        }
        if (window.VRChainPump && typeof window.VRChainPump.setDevice === 'function') {
            window.VRChainPump.setDevice(id);
        }
    }

    function stopAll() {
        if (window.VRChainPump && typeof window.VRChainPump.stopAuto === 'function') {
            window.VRChainPump.stopAuto();
        }
    }

    function getChainComparator() { return window.ChainComparator; }
    function getEraComparator() { return window.EraComparator; }
    function getParallelOptimizer() { return window.ParallelOptimizer; }
    function getVRChainPump() { return window.VRChainPump; }

    return {
        init: init,
        setDevice: setDevice,
        stopAll: stopAll,
        ChainComparator: getChainComparator,
        EraComparator: getEraComparator,
        ParallelOptimizer: getParallelOptimizer,
        VRChainPump: getVRChainPump
    };
})();

window.FeatureModules = FeatureModules;
