package com.waterwheel.chaintransmission.chain_simulator.service;

import com.waterwheel.chaintransmission.chain_simulator.config.ChainDynamicsProperties;
import com.waterwheel.chaintransmission.chain_simulator.config.ChainLinkDefaultProperties;
import com.waterwheel.chaintransmission.config.ChainDynamicsThreadPoolConfig;
import com.waterwheel.chaintransmission.dto.ChainDynamicsResultDTO;
import com.waterwheel.chaintransmission.entity.ChainDynamicsSimulation;
import com.waterwheel.chaintransmission.entity.ChainLinkParams;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.events.ChainSimulationCompletedEvent;
import com.waterwheel.chaintransmission.events.SensorDataReceivedEvent;
import com.waterwheel.chaintransmission.repository.ChainDynamicsSimulationRepository;
import com.waterwheel.chaintransmission.repository.ChainLinkParamsRepository;
import com.waterwheel.chaintransmission.simulation.ChainDynamicsSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class ChainSimulatorService {

    @Autowired
    private ChainDynamicsSimulator chainDynamicsSimulator;

    @Autowired
    private ChainDynamicsSimulationRepository simulationRepository;

    @Autowired
    private ChainLinkParamsRepository chainLinkParamsRepository;

    @Autowired
    private ChainLinkDefaultProperties defaultChainProps;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    @Qualifier("chainDynamicsExecutor")
    private Executor chainDynamicsExecutor;

    @Autowired
    private ChainDynamicsThreadPoolConfig.ChainSimStats chainSimStats;

    private final Map<Integer, Long> lastSimulationTime = new ConcurrentHashMap<>();
    private static final long MIN_SIMULATION_INTERVAL_MS = 10_000;

    public ChainDynamicsResultDTO runSimulation(WaterwheelDevice device, double inputSpeedRPM, double inputTorque) {
        long start = System.currentTimeMillis();
        chainSimStats.recordSubmission();

        ChainLinkParams params = chainLinkParamsRepository.findByDeviceId(device.getDeviceId())
                .orElseGet(() -> getDefaultParams(device.getDeviceId()));

        ChainDynamicsResultDTO result = chainDynamicsSimulator.simulate(device, params, inputSpeedRPM, inputTorque);

        persistResult(device.getDeviceId(), result, inputSpeedRPM, inputTorque);

        eventPublisher.publishEvent(new ChainSimulationCompletedEvent(this, device.getDeviceId(), result));
        long millis = System.currentTimeMillis() - start;
        chainSimStats.recordCompletion(millis);
        log.info("[ChainDynamics] 同步仿真完成 deviceId={}, 耗时={}ms, 状态={}",
                device.getDeviceId(), millis, result.getResonanceRisk());
        return result;
    }

    @Async("chainDynamicsExecutor")
    public CompletableFuture<ChainDynamicsResultDTO> runSimulationAsync(
            WaterwheelDevice device, double inputSpeedRPM, double inputTorque) {
        log.info("[ChainDynamics] 异步仿真提交 deviceId={}, speed={}rpm, 线程={}",
                device.getDeviceId(), inputSpeedRPM,
                Thread.currentThread().getName());
        try {
            ChainDynamicsResultDTO result = runSimulation(device, inputSpeedRPM, inputTorque);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("[ChainDynamics] 异步仿真失败 deviceId={}", device.getDeviceId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<ChainDynamicsResultDTO> submitSimulation(
            WaterwheelDevice device, double inputSpeedRPM, double inputTorque) {
        chainSimStats.recordSubmission();
        long start = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            log.info("[ChainDynamics] 线程池仿真开始 deviceId={}, speed={}rpm, 线程={}",
                    device.getDeviceId(), inputSpeedRPM,
                    Thread.currentThread().getName());

            ChainLinkParams params = chainLinkParamsRepository.findByDeviceId(device.getDeviceId())
                    .orElseGet(() -> getDefaultParams(device.getDeviceId()));

            ChainDynamicsResultDTO result = chainDynamicsSimulator.simulate(device, params, inputSpeedRPM, inputTorque);
            persistResult(device.getDeviceId(), result, inputSpeedRPM, inputTorque);
            eventPublisher.publishEvent(new ChainSimulationCompletedEvent(this, device.getDeviceId(), result));

            long millis = System.currentTimeMillis() - start;
            chainSimStats.recordCompletion(millis);
            log.info("[ChainDynamics] 线程池仿真完成 deviceId={}, 耗时={}ms",
                    device.getDeviceId(), millis);
            return result;
        }, chainDynamicsExecutor);
    }

    public Map<String, Object> getSimulatorStats() {
        return chainSimStats.snapshot();
    }

    @EventListener
    public void onSensorDataReceived(SensorDataReceivedEvent event) {
        Long lastRun = lastSimulationTime.get(event.getDeviceId());
        long now = System.currentTimeMillis();
        if (lastRun != null && (now - lastRun) < MIN_SIMULATION_INTERVAL_MS) {
            return;
        }
        lastSimulationTime.put(event.getDeviceId(), now);
        log.debug("收到传感器数据事件, deviceId={}, 但自动模拟触发暂未启用（需人工调用）", event.getDeviceId());
    }

    private void persistResult(Integer deviceId, ChainDynamicsResultDTO result, double inputSpeedRPM, double inputTorque) {
        ChainDynamicsSimulation entity = new ChainDynamicsSimulation();
        entity.setDeviceId(deviceId);
        entity.setSimulationTime(OffsetDateTime.now());
        entity.setInputSpeed(result.getInputSpeed());
        entity.setInputTorque(result.getInputTorque());
        entity.setLinkCount(result.getLinkCount());
        entity.setTensionDistribution(Map.of("data", result.getTensionDistribution()));
        entity.setVibrationFrequencies(Map.of("data", result.getVibrationFrequencies()));
        entity.setCollisionForces(Map.of("data", result.getCollisionForces()));
        entity.setMaxTension(result.getMaxTension());
        entity.setMinTension(result.getMinTension());
        entity.setAvgTension(result.getAvgTension());
        entity.setResonanceRisk(result.getResonanceRisk());
        entity.setSimulationDurationMs(result.getSimulationDurationMs());
        simulationRepository.save(entity);
    }

    private ChainLinkParams getDefaultParams(Integer deviceId) {
        ChainLinkParams params = new ChainLinkParams();
        params.setDeviceId(deviceId);
        params.setLinkMass(BigDecimal.valueOf(defaultChainProps.getLinkMass()));
        params.setLinkLength(BigDecimal.valueOf(defaultChainProps.getLinkLength()));
        params.setLinkStiffness(BigDecimal.valueOf(defaultChainProps.getStiffness()));
        params.setLinkDamping(BigDecimal.valueOf(defaultChainProps.getDamping()));
        params.setFrictionCoefficient(BigDecimal.valueOf(defaultChainProps.getFriction()));
        params.setAllowableTension(BigDecimal.valueOf(defaultChainProps.getAllowableTension()));
        params.setMaterial("锻铁");
        return chainLinkParamsRepository.save(params);
    }
}
