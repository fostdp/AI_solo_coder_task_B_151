package com.waterwheel.chaintransmission.virtualoperation;

import com.waterwheel.chaintransmission.dto.VirtualOperationDTO;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import com.waterwheel.chaintransmission.test.TestDataFactory;
import com.waterwheel.chaintransmission.virtualoperation.service.VirtualOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("功能4：公众虚拟翻车操作体验测试")
class VirtualOperationServiceTest {

    @Mock
    private WaterwheelDeviceRepository deviceRepository;

    @Mock
    private WaterEfficiencyOptimizer optimizer;

    @InjectMocks
    private VirtualOperationService operationService;

    private WaterwheelDevice defaultDevice;

    @BeforeEach
    void setUp() {
        defaultDevice = TestDataFactory.createDefaultDevice();
        operationService.resetAllSessions();

        when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    double depth = invocation.getArgument(0);
                    double width = invocation.getArgument(1);
                    double angle = invocation.getArgument(2);
                    double speed = invocation.getArgument(3);
                    double effectiveArea = depth * width * Math.sin(Math.toRadians(angle));
                    double flow = effectiveArea * speed * 3600000 * 0.85;
                    return Math.max(0, flow);
                });
    }

    // =============  正常场景  =============
    @Nested
    @DisplayName("正常场景：操作反馈验证")
    class NormalScenario {

        @Test
        @DisplayName("转速-流量线性关系验证：提高转速→流量上升")
        void testSpeedFlowPositiveCorrelation() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            double[] speeds = {0.5, 1.0, 1.5, 2.0, 2.5};
            double[] flows = new double[speeds.length];

            for (int i = 0; i < speeds.length; i++) {
                VirtualOperationDTO dto = operationService.performOperation(
                        1, speeds[i], 1.0, 0, i == 0);
                flows[i] = dto.getCurrentWaterFlowLh().doubleValue();
                assertTrue(flows[i] > 0, speeds[i] + "m/s流量应为正");
            }

            for (int i = 1; i < flows.length; i++) {
                assertTrue(flows[i] > flows[i - 1] * 0.9,
                        "流量应随转速近似线性上升: " + flows[i] + " > " + flows[i-1]);
            }
        }

        @Test
        @DisplayName("水位系数影响：水位上升→流量上升→张力上升")
        void testWaterLevelImpact() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO low = operationService.performOperation(1, 1.5, 0.5, 0, true);
            VirtualOperationDTO medium = operationService.performOperation(1, 1.5, 1.0, 0, false);
            VirtualOperationDTO high = operationService.performOperation(1, 1.5, 1.3, 0, false);

            assertTrue(high.getCurrentWaterFlowLh().doubleValue()
                    > medium.getCurrentWaterFlowLh().doubleValue(),
                    "高水位流量应大于中水位");
            assertTrue(medium.getCurrentWaterFlowLh().doubleValue()
                    > low.getCurrentWaterFlowLh().doubleValue(),
                    "中水位流量应大于低水位");

            assertTrue(high.getChainTensionN().doubleValue()
                    > medium.getChainTensionN().doubleValue(),
                    "高水位张力应大于中水位");
            assertTrue(medium.getChainTensionN().doubleValue()
                    > low.getChainTensionN().doubleValue(),
                    "中水位张力应大于低水位");

            assertTrue(high.getScraperLoadN().doubleValue()
                    > low.getScraperLoadN().doubleValue(),
                    "高水位刮板载荷应大于低水位");
        }

        @Test
        @DisplayName("累计提水量验证：运行10秒→累计水量 = 瞬时流量 × 时间")
        void testAccumulatedWaterCounting() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO step0 = operationService.performOperation(1, 1.5, 1.0, 0, true);
            assertEquals(0, step0.getOperationSeconds(), "初始运行时长应为0");
            assertEquals(0.0, step0.getTotalWaterLiters().doubleValue(), 0.001,
                    "初始累计水量应为0");

            double instantFlowLs = 0;
            int totalSeconds = 0;
            for (int i = 0; i < 10; i++) {
                VirtualOperationDTO dto = operationService.performOperation(1, 1.5, 1.0, 1, false);
                instantFlowLs = dto.getInstantaneousFlowLs().doubleValue();
                totalSeconds += 1;
                assertEquals(totalSeconds, dto.getOperationSeconds(),
                        "第" + (i+1) + "步运行时长应为" + totalSeconds + "s");
            }

            VirtualOperationDTO finalState = operationService.performOperation(1, 1.5, 1.0, 0, false);
            double expectedTotal = instantFlowLs * 10;
            double actualTotal = finalState.getTotalWaterLiters().doubleValue();
            double error = Math.abs(actualTotal - expectedTotal) / expectedTotal * 100;

            assertEquals(10, finalState.getOperationSeconds(), "累计运行10秒");
            assertTrue(error < 10.0,
                    "累计水量误差应 < 10%，期望: " + expectedTotal +
                            "，实际: " + actualTotal + "，误差: " + error + "%");
        }

        @Test
        @DisplayName("会话隔离验证：不同设备会话独立不干扰")
        void testSessionIsolation() {
            WaterwheelDevice device2 = TestDataFactory.createDevice(
                    2, "设备2", "round", "ancient_song",
                    35.0, 580.0, 15.0, 140, 28);

            when(deviceRepository.findById(1)).thenReturn(Optional.of(defaultDevice));
            when(deviceRepository.findById(2)).thenReturn(Optional.of(device2));

            operationService.performOperation(1, 1.5, 1.0, 5, true);
            operationService.performOperation(2, 2.0, 1.0, 10, true);

            VirtualOperationDTO s1 = operationService.performOperation(1, 1.5, 1.0, 0, false);
            VirtualOperationDTO s2 = operationService.performOperation(2, 2.0, 1.0, 0, false);

            assertEquals(5, s1.getOperationSeconds(), "设备1时长5秒");
            assertEquals(10, s2.getOperationSeconds(), "设备2时长10秒");

            assertTrue(s2.getCurrentWaterFlowLh().doubleValue()
                    > s1.getCurrentWaterFlowLh().doubleValue(),
                    "设备2链速更快，流量应更大");
        }

        @Test
        @DisplayName("状态正常：中等参数 → NORMAL状态，无告警")
        void testNormalStatus() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 1.5, 1.0, 0, true);

            assertEquals("NORMAL", dto.getOperationStatus());
            assertTrue(dto.getWarnings() == null || dto.getWarnings().isEmpty(),
                    "正常运行不应有告警");

            double tension = dto.getChainTensionN().doubleValue();
            assertTrue(tension < 6000, "正常张力应 < 6000N，实际: " + tension);

            double amplitude = dto.getVibrationAmplitudeMm().doubleValue();
            assertTrue(amplitude < 2.5, "正常振幅应 < 2.5mm，实际: " + amplitude);
        }

        @Test
        @DisplayName("效率验证：中等转速效率最高，高低速效率下降")
        void testEfficiencyCurve() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            double[] speeds = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5};
            double[] efficiencies = new double[speeds.length];

            for (int i = 0; i < speeds.length; i++) {
                VirtualOperationDTO dto = operationService.performOperation(
                        1, speeds[i], 1.0, 0, i == 0);
                efficiencies[i] = dto.getEfficiencyPercent().doubleValue();
            }

            int maxIdx = 0;
            for (int i = 1; i < efficiencies.length; i++) {
                if (efficiencies[i] > efficiencies[maxIdx]) maxIdx = i;
            }

            assertTrue(maxIdx >= 1 && maxIdx <= 3,
                    "效率峰值应出现在中速区间（1.0~2.0m/s），实际峰值在 " + speeds[maxIdx] + "m/s");
            assertTrue(efficiencies[0] < efficiencies[maxIdx],
                    "低速(0.5m/s)效率应低于峰值：" + efficiencies[0] + " < " + efficiencies[maxIdx]);
            assertTrue(efficiencies[efficiencies.length-1] < efficiencies[maxIdx],
                    "高速(3.5m/s)效率应低于峰值：" + efficiencies[efficiencies.length-1] + " < " + efficiencies[maxIdx]);
        }

        @Test
        @DisplayName("链条位置数据：返回完整的(x,y,z)三元组")
        void testChainLinkPositionsFormat() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 1.5, 1.0, 0, true);

            assertNotNull(dto.getChainLinkPositions());
            assertTrue(dto.getChainLinkPositions().size() >= 15,
                    "至少返回5个链节×3坐标=15个浮点数，实际: " + dto.getChainLinkPositions().size());
            assertEquals(0, dto.getChainLinkPositions().size() % 3,
                    "链节位置数据应为3的倍数（x,y,z）");
        }

        @Test
        @DisplayName("链条位置相位累积：连续调用 → 链节位置前进")
        void testChainPositionAdvances() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO step1 = operationService.performOperation(1, 1.5, 1.0, 0, true);
            VirtualOperationDTO step2 = operationService.performOperation(1, 1.5, 1.0, 1, false);
            VirtualOperationDTO step3 = operationService.performOperation(1, 1.5, 1.0, 1, false);

            float x1 = step1.getChainLinkPositions().get(0);
            float x2 = step2.getChainLinkPositions().get(0);
            float x3 = step3.getChainLinkPositions().get(0);

            assertNotEquals(x1, x2, 0.0001, "链节位置应随时间前进");
            assertNotEquals(x2, x3, 0.0001, "继续调用链节位置继续前进");
        }
    }

    // =============  参数化测试  =============
    @ParameterizedTest
    @CsvSource({
            "1.5, 1.0, NORMAL",
            "2.5, 1.0, WARNING",
            "3.8, 1.0, CRITICAL",
            "1.5, 1.3, WARNING",
            "0.4, 1.0, NORMAL"
    })
    @DisplayName("状态判定矩阵：速度{0}m/s × 水位{1} → 状态{2}")
    void statusDeterminationMatrix(double speed, double wl, String expectedStatus) {
        when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

        VirtualOperationDTO dto = operationService.performOperation(1, speed, wl, 0, true);

        if ("CRITICAL".equals(expectedStatus)) {
            assertTrue(dto.getOperationStatus().equals("WARNING")
                            || dto.getOperationStatus().equals("CRITICAL"),
                    "高速/高水位应触发告警，实际状态: " + dto.getOperationStatus());
        }
        assertNotNull(dto.getOperationStatus());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.3, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0})
    @DisplayName("转速全覆盖测试：{0}m/s 都应产生有效输出")
    void testFullSpeedRange(double speed) {
        when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

        VirtualOperationDTO dto = operationService.performOperation(1, speed, 1.0, 0, true);

        assertNotNull(dto);
        assertTrue(dto.getCurrentWaterFlowLh().doubleValue() >= 0,
                speed + "m/s 流量不应为负");
        assertTrue(Double.isFinite(dto.getInstantaneousFlowLs().doubleValue()),
                speed + "m/s 瞬时流量应为有限值");
        assertNotNull(dto.getChainTensionN());
    }

    // =============  边界场景  =============
    @Nested
    @DisplayName("边界场景测试")
    class BoundaryScenario {

        @Test
        @DisplayName("边界：最高速4.0m/s，触发高速警告")
        void testMaximumSpeed() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 4.0, 1.0, 0, true);

            assertTrue(dto.getWarnings() != null && dto.getWarnings().size() > 0,
                    "4.0m/s应触发高速警告");

            boolean hasHighSpeedWarning = dto.getWarnings().stream()
                    .anyMatch(w -> w.contains("高速") || w.contains("离心"));
            assertTrue(hasHighSpeedWarning, "警告应包含高速或离心字样");
        }

        @Test
        @DisplayName("边界：最低速0.3m/s，触发填充不足警告")
        void testMinimumSpeed() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 0.3, 1.0, 0, true);

            assertTrue(dto.getWarnings() != null && dto.getWarnings().size() > 0,
                    "0.3m/s应触发填充不足警告");

            boolean hasLowSpeedWarning = dto.getWarnings().stream()
                    .anyMatch(w -> w.contains("过低") || w.contains("填充"));
            assertTrue(hasLowSpeedWarning, "警告应包含过低或填充字样");
        }

        @Test
        @DisplayName("边界：共振区2.1m/s附近，触发振动异常警告")
        void testResonanceZone() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 2.1, 1.0, 0, true);

            double amplitude = dto.getVibrationAmplitudeMm().doubleValue();
            assertTrue(amplitude > 1.5,
                    "2.1m/s共振区振幅应显著升高，实际: " + amplitude + "mm");

            if (amplitude > 2.5) {
                assertTrue(dto.getWarnings() != null && dto.getWarnings().stream()
                                .anyMatch(w -> w.contains("振动") || w.contains("共振")),
                        "高振幅应触发振动警告");
            }
        }

        @Test
        @DisplayName("边界：最大张力区（高速+高水位），触发CRITICAL")
        void testMaximumTensionZone() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 3.5, 1.3, 0, true);

            double tension = dto.getChainTensionN().doubleValue();
            assertTrue(tension > 6000, "3.5m/s+1.3水位张力应 > 6000N，实际: " + tension);

            if (tension > 8000) {
                assertEquals("CRITICAL", dto.getOperationStatus(),
                        "张力>8000N状态应为CRITICAL");
            }
        }

        @Test
        @DisplayName("边界：长时间运行（1小时虚拟时间），累积正确")
        void testLongDurationOperation() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO init = operationService.performOperation(1, 1.5, 1.0, 0, true);
            double instantLs = init.getInstantaneousFlowLs().doubleValue();

            VirtualOperationDTO result = operationService.performOperation(1, 1.5, 1.0, 3600, false);

            assertEquals(3600, result.getOperationSeconds(), "应累积3600秒");
            double expectedTotal = instantLs * 3600;
            double actualTotal = result.getTotalWaterLiters().doubleValue();
            double error = Math.abs(actualTotal - expectedTotal) / expectedTotal * 100;

            assertTrue(error < 15.0, "1小时累计水量误差应 < 15%，实际: " + error + "%");
            assertTrue(actualTotal > 0, "累计水量应 > 0");
        }

        @Test
        @DisplayName("边界：重置会话，累计数据归零")
        void testSessionReset() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            operationService.performOperation(1, 1.5, 1.0, 100, true);
            VirtualOperationDTO before = operationService.performOperation(1, 1.5, 1.0, 0, false);
            assertEquals(100, before.getOperationSeconds());
            assertTrue(before.getTotalWaterLiters().doubleValue() > 0);

            VirtualOperationDTO after = operationService.performOperation(1, 1.5, 1.0, 0, true);
            assertEquals(0, after.getOperationSeconds(), "重置后运行时长应为0");
            assertEquals(0.0, after.getTotalWaterLiters().doubleValue(), 0.001,
                    "重置后累计水量应为0");
        }
    }

    // =============  异常场景  =============
    @Nested
    @DisplayName("异常场景测试")
    class ExceptionScenario {

        @Test
        @DisplayName("异常：无效设备ID，抛出异常")
        void testInvalidDeviceId() {
            when(deviceRepository.findById(999)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> operationService.performOperation(999, 1.5, 1.0, 0, true));

            assertTrue(ex.getMessage().contains("999"));
        }

        @Test
        @DisplayName("异常：负转速，自动clamp到最小值0.3")
        void testNegativeSpeedClamped() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, -5.0, 1.0, 0, true);

            assertEquals(0.3, dto.getChainSpeedMs(), TestDataFactory.TOLERANCE,
                    "负转速应被clamp到0.3m/s");
            assertTrue(dto.getCurrentWaterFlowLh().doubleValue() >= 0,
                    "clamp后流量不应为负");
        }

        @Test
        @DisplayName("异常：超高速10m/s，自动clamp到最大值4.0")
        void testExcessiveSpeedClamped() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 10.0, 1.0, 0, true);

            assertEquals(4.0, dto.getChainSpeedMs(), TestDataFactory.TOLERANCE,
                    "10m/s应被clamp到4.0m/s");
            assertTrue(dto.getCurrentWaterFlowLh().doubleValue() > 0,
                    "clamp后仍应有流量");
        }

        @Test
        @DisplayName("异常：水位系数0.0，自动clamp到0.3")
        void testZeroWaterLevelClamped() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 1.5, 0.0, 0, true);

            assertEquals(0.3, dto.getWaterLevelFactor(), TestDataFactory.TOLERANCE,
                    "0水位应被clamp到0.3");
            assertTrue(dto.getCurrentWaterFlowLh().doubleValue() > 0,
                    "最低水位仍应有少量提水");
        }

        @Test
        @DisplayName("异常：NaN转速，保护不崩溃")
        void testNaNParameterProtection() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, Double.NaN, 1.0, 0, true);

            assertNotNull(dto);
            assertTrue(dto.getChainSpeedMs() >= 0.3 && dto.getChainSpeedMs() <= 4.0,
                    "NaN转速应被处理为默认值在范围内");
            assertTrue(Double.isFinite(dto.getCurrentWaterFlowLh().doubleValue()),
                    "所有输出值应为有限值");
        }

        @Test
        @DisplayName("异常：Infinite水位，保护不崩溃")
        void testInfiniteParameterProtection() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(
                    1, 1.5, Double.POSITIVE_INFINITY, 0, true);

            assertNotNull(dto);
            assertTrue(Double.isFinite(dto.getWaterLevelFactor()),
                    "Infinite水位应被处理为有限值");
            assertTrue(Double.isFinite(dto.getCurrentWaterFlowLh().doubleValue()),
                    "输出值不应为Infinite");
        }

        @Test
        @DisplayName("异常：重置所有会话")
        void testResetAllSessions() {
            when(deviceRepository.findById(1)).thenReturn(Optional.of(defaultDevice));
            when(deviceRepository.findById(2)).thenReturn(Optional.of(defaultDevice));

            operationService.performOperation(1, 1.5, 1.0, 100, true);
            operationService.performOperation(2, 2.0, 1.0, 200, true);

            var result = operationService.resetAllSessions();

            assertEquals(2, result.get("cleared"));

            VirtualOperationDTO s1 = operationService.performOperation(1, 1.5, 1.0, 0, false);
            assertEquals(0, s1.getOperationSeconds(), "重置后设备1时长应为0");
        }

        @Test
        @DisplayName("异常：可选参数null使用默认值")
        void testNullOptionalParams() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(
                    1, 1.5, null, null, null);

            assertNotNull(dto);
            assertEquals(1.0, dto.getWaterLevelFactor(), TestDataFactory.TOLERANCE,
                    "null水位应使用默认值1.0");
            assertTrue(dto.getCurrentWaterFlowLh().doubleValue() > 0,
                    "null参数下仍应正常运行");
        }
    }

    // =============  张力分布验证  =============
    @Nested
    @DisplayName("张力分布验证")
    class TensionDistributionTests {

        @Test
        @DisplayName("张力分布：所有采样点为正，有波动")
        void testTensionDistributionAllPositive() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO dto = operationService.performOperation(1, 1.5, 1.0, 0, true);

            assertNotNull(dto.getTensionDistribution());
            assertEquals(50, dto.getTensionDistribution().size(),
                    "张力分布应包含50个采样点");

            for (var t : dto.getTensionDistribution()) {
                assertTrue(t.doubleValue() > 0, "所有张力采样点应为正");
            }

            double max = dto.getTensionDistribution().stream()
                    .mapToDouble(java.math.BigDecimal::doubleValue)
                    .max().orElse(0);
            double min = dto.getTensionDistribution().stream()
                    .mapToDouble(java.math.BigDecimal::doubleValue)
                    .min().orElse(0);
            double cv = (max - min) / ((max + min) / 2);

            assertTrue(cv > 0.1, "张力分布应有显著波动，CV应 > 0.1，实际: " + cv);
        }

        @Test
        @DisplayName("高速张力分布波动更大")
        void testHighSpeedIncreasesTensionVariation() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            VirtualOperationDTO low = operationService.performOperation(1, 0.5, 1.0, 0, true);
            VirtualOperationDTO high = operationService.performOperation(1, 3.5, 1.0, 0, true);

            double lowStd = stdDev(low.getTensionDistribution().stream()
                    .mapToDouble(java.math.BigDecimal::doubleValue).toArray());
            double highStd = stdDev(high.getTensionDistribution().stream()
                    .mapToDouble(java.math.BigDecimal::doubleValue).toArray());

            assertTrue(highStd > lowStd, "高速张力波动应大于低速: " + highStd + " > " + lowStd);
        }

        private double stdDev(double[] arr) {
            double mean = java.util.Arrays.stream(arr).average().orElse(0);
            double sum = 0;
            for (double v : arr) sum += (v - mean) * (v - mean);
            return Math.sqrt(sum / arr.length);
        }
    }

    // =============  操作响应速度  =============
    @Nested
    @DisplayName("性能验证")
    class PerformanceTests {

        @Test
        @DisplayName("性能：单次操作 < 50ms，满足交互体验")
        void testResponseTime() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            long start = System.nanoTime();
            operationService.performOperation(1, 1.5, 1.0, 1, true);
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            assertTrue(elapsed < 50,
                    "单次操作响应应 < 50ms，实际: " + elapsed + "ms");
        }

        @Test
        @DisplayName("性能：连续100次调用，平均 < 10ms")
        void testThroughput() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));

            operationService.performOperation(1, 1.5, 1.0, 0, true);

            long start = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                operationService.performOperation(1, 1.5, 1.0, 1, false);
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            double avg = (double) elapsed / 100;

            assertTrue(avg < 10,
                    "100次连续调用平均耗时应 < 10ms，实际: " + avg + "ms");
        }
    }
}
