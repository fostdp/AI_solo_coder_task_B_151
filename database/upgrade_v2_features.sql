-- ============================================================
-- V2 功能升级：链型对比 / 跨时代对比 / 并联优化 / 虚拟操作
-- 向后兼容，不破坏现有表结构
-- ============================================================

-- 1. 扩展 waterwheel_device 表，新增链型和时代字段
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='waterwheel_device' AND column_name='chain_type_code') THEN
        ALTER TABLE waterwheel_device ADD COLUMN chain_type_code VARCHAR(20) DEFAULT 'plate';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='waterwheel_device' AND column_name='era_code') THEN
        ALTER TABLE waterwheel_device ADD COLUMN era_code VARCHAR(30) DEFAULT 'ancient_song';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='waterwheel_device' AND column_name='parallel_group_id') THEN
        ALTER TABLE waterwheel_device ADD COLUMN parallel_group_id INTEGER DEFAULT 1;
    END IF;
END $$;

COMMENT ON COLUMN waterwheel_device.chain_type_code IS '链传动类型：plate板链/round环链/hook钩链';
COMMENT ON COLUMN waterwheel_device.era_code IS '时代类型：ancient_song宋代/modern_electric现代';
COMMENT ON COLUMN waterwheel_device.parallel_group_id IS '并联分组ID，同组设备可协同优化';


-- 2. 三种链传动形式参数表（参考chain_link_params扩展）
CREATE TABLE IF NOT EXISTS chain_type_spec (
    chain_type_code    VARCHAR(20) PRIMARY KEY,
    display_name       VARCHAR(50) NOT NULL,
    transmission_efficiency NUMERIC(6,4) NOT NULL,
    friction_coefficient    NUMERIC(6,4) NOT NULL,
    tension_coefficient     NUMERIC(6,4) NOT NULL,
    water_retention_rate    NUMERIC(6,4) NOT NULL,
    wear_coefficient        NUMERIC(6,4) NOT NULL,
    max_allowable_speed_rpm NUMERIC(8,2) NOT NULL,
    material_spec          VARCHAR(100),
    historical_era         VARCHAR(30),
    description            TEXT,
    created_at             TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO chain_type_spec (chain_type_code, display_name,
    transmission_efficiency, friction_coefficient, tension_coefficient,
    water_retention_rate, wear_coefficient, max_allowable_speed_rpm,
    material_spec, historical_era, description)
VALUES
('plate', '板链（宋代主流制式）',
    0.8800, 0.1800, 1.1500, 0.9000, 1.0000, 12.50,
    '锻铁铆接，板厚3-5mm', 'ancient_song',
    '《农书》《天工开物》记载的标准形制，多块铁板铆接成链节，承载能力强'),
('round', '环链（精密加工型）',
    0.9200, 0.1200, 0.9500, 0.8500, 0.8500, 18.00,
    '熟铁锻打圆环，环径均匀', 'ancient_song',
    '圆环相扣结构，啮合平滑摩擦小，对锻造工艺要求高，多为官府作坊制造'),
('hook', '钩链（便捷拆装型）',
    0.7800, 0.2500, 1.3000, 0.9500, 1.2500, 8.00,
    '铸铁浇铸钩头，配合销钉', 'ancient_song',
    '钩状连接件，无需工具即可拆装维护，适合小型临时灌溉场景');

-- 3. 时代参数表
CREATE TABLE IF NOT EXISTS era_spec (
    era_code            VARCHAR(30) PRIMARY KEY,
    display_name        VARCHAR(100) NOT NULL,
    frame_material      VARCHAR(30) NOT NULL,
    chain_material      VARCHAR(30) NOT NULL,
    drive_type          VARCHAR(30) NOT NULL,
    power_source        VARCHAR(20) NOT NULL,
    mechanical_efficiency NUMERIC(6,4) NOT NULL,
    transmission_efficiency NUMERIC(6,4) NOT NULL,
    control_efficiency  NUMERIC(6,4) DEFAULT 0,
    typical_speed_rpm   NUMERIC(8,2) NOT NULL,
    typical_power_kw    NUMERIC(8,2) NOT NULL,
    typical_noise_db    NUMERIC(6,2),
    maintenance_hours_per_year NUMERIC(8,1),
    lifespan_years      NUMERIC(5,1),
    cost_factor         NUMERIC(6,2),
    historical_context  JSONB,
    description         TEXT,
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO era_spec (era_code, display_name,
    frame_material, chain_material, drive_type, power_source,
    mechanical_efficiency, transmission_efficiency, control_efficiency,
    typical_speed_rpm, typical_power_kw, typical_noise_db,
    maintenance_hours_per_year, lifespan_years, cost_factor,
    historical_context, description)
VALUES
('ancient_song', '宋代水转翻车（960-1279）',
    'wood_frame', 'wrought_iron', 'water_wheel', '水力',
    0.6500, 0.5500, 0.0000,
    3.50, 15.00, 68.0,
    120.0, 8.0, 1.00,
    '{"dynasty":"北宋","inventor":"王祯《农书》","typical_irrigation_area_mu":50,"labor":"2-3人值守"}'::jsonb,
    '宋代水利技术巅峰，木质车架、锻铁链条、水轮驱动，代表古代农业灌溉最高水平'),
('modern_electric', '现代电动链式泵（21世纪）',
    'steel_frame', 'alloy_steel', 'electric_motor', '电力',
    0.9500, 0.8800, 0.8200,
    22.00, 380.00, 82.0,
    40.0, 15.0, 3.50,
    '{"standard":"GB/T 13006-2013","control":"PLC+HMI+远程","typical_irrigation_area_mu":500}'::jsonb,
    '现代工业链式水泵：合金钢链条、变频电机驱动、PLC自动控制，效率与可靠性全面提升');


-- 4. 并联优化结果存储表
CREATE TABLE IF NOT EXISTS parallel_optimization_result (
    result_id           BIGSERIAL PRIMARY KEY,
    optimization_time   TIMESTAMPTZ DEFAULT NOW(),
    optimization_goal   VARCHAR(20) NOT NULL,
    target_total_flow_lh  NUMERIC(16,4),
    max_total_power_kw    NUMERIC(10,4),
    device_count        INTEGER NOT NULL,
    device_ids          INTEGER[] NOT NULL,
    converged           BOOLEAN,
    iterations          INTEGER,
    computation_time_ms BIGINT,
    predicted_total_flow NUMERIC(16,4),
    actual_power_kw     NUMERIC(10,4),
    average_efficiency  NUMERIC(6,4),
    load_balance_std    NUMERIC(8,4),
    coordination_gain   NUMERIC(6,2),
    assignments_json    JSONB,
    trace_json          JSONB,
    created_by          VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_parallel_opt_time
    ON parallel_optimization_result (optimization_time);

COMMENT ON TABLE parallel_optimization_result IS '多台翻车并联协同优化结果';


-- 5. 虚拟操作日志表（供统计公众体验数据）
CREATE TABLE IF NOT EXISTS virtual_operation_log (
    log_id              BIGSERIAL PRIMARY KEY,
    log_time            TIMESTAMPTZ DEFAULT NOW(),
    device_id           INTEGER NOT NULL,
    session_id          VARCHAR(64),
    chain_speed_ms      NUMERIC(8,4),
    water_level_factor  NUMERIC(6,4),
    operation_seconds   INTEGER,
    total_water_liters  NUMERIC(16,4),
    peak_tension_n      NUMERIC(12,4),
    peak_efficiency     NUMERIC(6,4),
    warnings_count      INTEGER DEFAULT 0,
    operation_status    VARCHAR(10),
    user_agent          TEXT
);
SELECT create_hypertable('virtual_operation_log', 'log_time',
    if_not_exists => TRUE);
CREATE INDEX IF NOT EXISTS idx_virtual_op_device
    ON virtual_operation_log (device_id, log_time DESC);

COMMENT ON TABLE virtual_operation_log IS '公众虚拟翻车操作日志（超表分区）';


-- 6. 跨时代对比和链型对比结果缓存视图
CREATE OR REPLACE VIEW v_chain_type_performance_summary AS
SELECT
    wd.device_id,
    wd.device_name,
    ct.chain_type_code,
    ct.display_name as chain_type_name,
    ct.transmission_efficiency,
    ct.friction_coefficient,
    ct.water_retention_rate,
    ct.max_allowable_speed_rpm,
    wd.nominal_sprocket_speed_rpm,
    ROUND(
        CASE WHEN wd.nominal_sprocket_speed_rpm <= ct.max_allowable_speed_rpm
             THEN wd.nominal_sprocket_speed_rpm::numeric * ct.transmission_efficiency * 1200
             ELSE ct.max_allowable_speed_rpm::numeric * ct.transmission_efficiency * 1200
        END, 2
    ) as estimated_flow_lh
FROM waterwheel_device wd
CROSS JOIN chain_type_spec ct
ORDER BY wd.device_id, ct.transmission_efficiency DESC;

COMMENT ON VIEW v_chain_type_performance_summary IS '链型效率快速对比视图';


-- 7. 更新现有设备示例数据
UPDATE waterwheel_device
SET chain_type_code = CASE device_id
    WHEN 1 THEN 'plate'
    WHEN 2 THEN 'round'
    WHEN 3 THEN 'hook'
    ELSE 'plate'
END,
era_code = 'ancient_song',
parallel_group_id = 1
WHERE chain_type_code IS NULL OR era_code IS NULL;

-- 可选：插入一台现代链式泵示例设备（便于跨时代对比）
INSERT INTO waterwheel_device (
    device_name, location_code, nominal_sprocket_speed_rpm,
    sprocket_radius_cm, chain_length_cm, scraper_count,
    chain_type_code, era_code, parallel_group_id,
    description, installation_date, status
)
SELECT
    '现代电动链式泵对照机', 'MOD-REF-001', 22.0,
    35.0, 650.0, 24,
    'round', 'modern_electric', 2,
    '21世纪工业链式水泵对照基准：合金钢链+变频电机+PLC自动控制',
    NOW(), 1
WHERE NOT EXISTS (SELECT 1 FROM waterwheel_device WHERE location_code='MOD-REF-001');


-- 8. PL/pgSQL：批量估算多设备并联理论最大流量
CREATE OR REPLACE FUNCTION estimate_parallel_max_flow(
    device_array INTEGER[],
    safety_factor NUMERIC DEFAULT 0.9
) RETURNS NUMERIC AS $$
DECLARE
    total NUMERIC := 0;
    rec RECORD;
BEGIN
    FOR rec IN
        SELECT
            wd.device_id,
            wd.nominal_sprocket_speed_rpm,
            ct.transmission_efficiency,
            ct.max_allowable_speed_rpm
        FROM waterwheel_device wd
        JOIN chain_type_spec ct ON wd.chain_type_code = ct.chain_type_code
        WHERE wd.device_id = ANY(device_array)
    LOOP
        total := total + LEAST(rec.nominal_sprocket_speed_rpm, rec.max_allowable_speed_rpm)
                 * rec.transmission_efficiency * 1200;
    END LOOP;
    RETURN ROUND(total * safety_factor, 2);
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION estimate_parallel_max_flow IS '估算多台并联设备理论最大提水量(L/h)';
