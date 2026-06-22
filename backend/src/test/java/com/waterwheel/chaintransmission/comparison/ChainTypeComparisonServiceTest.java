package com.waterwheel.chaintransmission.comparison;

import com.waterwheel.chaintransmission.comparison.service.ChainTypeComparisonService;
import com.waterwheel.chaintransmission.dto.ChainTypeComparisonDTO;
import com.waterwheel.chaintransmission.entity.ChainType;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import com.waterwheel.chaintransmission.test.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("功能1：链传动形式对比测试")
class ChainTypeComparisonServiceTest {

    @Mock
    private WaterwheelDeviceRepository deviceRepository;

    @Mock
    private WaterEfficiencyOptimizer optimizer;

    @InjectMocks
    private ChainTypeComparisonService comparisonService;

    private WaterwheelDevice defaultDevice;

    @BeforeEach
    void setUp() {
        defaultDevice = TestDataFactory.createDefaultDevice();
    }

    // =============  正常场景  =============
    @Nested
    @DisplayName("正常场景测试")
    class NormalScenario {

        @Test
        @DisplayName("三种链型效率排序验证：环链 > 板链 > 钩链")
        void testChainTypeEfficiencyOrder() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double speed = invocation.getArgument(3);
                        return 5000.0 * speed * 3.6;
                    });

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(1, 15.0, 80.0, 0.12, 0.25, 40.0);

            assertNotNull(result);
            assertEquals(3, result.getResults().size(), "应返回3种链型结果");

            double[] efficiencies = result.getResults().stream()
                    .mapToDouble(r -> r.getActualWaterFlowLh().doubleValue())
                    .toArray();

            assertEquals(ChainType.ROUND_LINK_CHAIN.getCode(), result.getResults().get(0).getChainTypeCode(),
                    "效率最高应为环链(ROUND)");
            assertEquals(ChainType.PLATE_CHAIN.getCode(), result.getResults().get(1).getChainTypeCode(),
                    "效率第二应为板链(PLATE)");
            assertEquals(ChainType.HOOK_CHAIN.getCode(), result.getResults().get(2).getChainTypeCode(),
                    "效率最低应为钩链(HOOK)");

            assertTrue(efficiencies[0] > efficiencies[1] && efficiencies[1] > efficiencies[2],
                    "提水量应严格递减：环链 > 板链 > 钩链");
        }

        @Test
        @DisplayName("传动效率参数正确性验证")
        void testTransmissionEfficiencyCorrectness() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10000.0);

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(1, 15.0, 80.0, 0.12, 0.25, 40.0);

            for (ChainTypeComparisonDTO.ChainTypeResult r : result.getResults()) {
                ChainType ct = ChainType.fromCode(r.getChainTypeCode());
                assertEquals(ct.getTransmissionEfficiency(),
                        r.getTransmissionEfficiency().doubleValue(),
                        TestDataFactory.TOLERANCE,
                        ct.getDisplayName() + "传动效率应与枚举值一致");
            }
        }

        @Test
        @DisplayName("链条张力验证：钩链 > 板链 > 环链")
        void testChainTensionOrder() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(8000.0);

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(1, 15.0, 80.0, 0.12, 0.25, 40.0);

            java.util.Map<String, Double> tensionMap = new java.util.HashMap<>();
            result.getResults().forEach(r -> tensionMap.put(r.getChainTypeCode(), r.getChainTensionN().doubleValue()));

            double hookTension = tensionMap.get("hook");
            double plateTension = tensionMap.get("plate");
            double roundTension = tensionMap.get("round");

            assertTrue(hookTension > plateTension && plateTension > roundTension,
                    "张力应随摩擦系数增加而增大：钩链 > 板链 > 环链");
        }

        @Test
        @DisplayName("获取链型元数据：返回3种链型完整信息")
        void testGetAllChainTypeMeta() {
            var metaList = comparisonService.getAllChainTypeMeta();

            assertEquals(3, metaList.size());
            metaList.forEach(meta -> {
                assertTrue(meta.containsKey("code"));
                assertTrue(meta.containsKey("name"));
                assertTrue(meta.containsKey("transmissionEfficiency"));
                assertTrue(meta.containsKey("frictionCoefficient"));
                assertTrue(meta.containsKey("maxSpeed_rpm"));
            });
        }
    }

    // =============  边界场景  =============
    @Nested
    @DisplayName("边界场景测试")
    class BoundaryScenario {

        @Test
        @DisplayName("边界输入：转速达到环链许用上限18 RPM")
        void testAtMaxAllowableSpeedForRoundChain() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double speed = invocation.getArgument(3);
                        return 10000.0 * speed * 3.6;
                    });

            double maxSpeedForRound = ChainType.ROUND_LINK_CHAIN.getMaxSpeed();
            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, maxSpeedForRound, 100.0, 0.12, 0.25, 40.0);

            ChainTypeComparisonDTO.ChainTypeResult roundResult = result.getResults().stream()
                    .filter(r -> "round".equals(r.getChainTypeCode()))
                    .findFirst().orElseThrow();

            assertFalse(roundResult.getResonanceRisk(),
                    "18RPM未超过环链许用速度，不应触发共振风险");
        }

        @Test
        @DisplayName("边界输入：转速超过钩链许用上限9 RPM（触发磨损警告）")
        void testExceedHookChainMaxSpeed() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(15000.0);

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, 12.0, 80.0, 0.12, 0.25, 40.0);

            ChainTypeComparisonDTO.ChainTypeResult hookResult = result.getResults().stream()
                    .filter(r -> "hook".equals(r.getChainTypeCode()))
                    .findFirst().orElseThrow();

            double speedRatio = 12.0 / ChainType.HOOK_CHAIN.getMaxSpeed();
            assertTrue(speedRatio > 1.0, "12RPM应超过钩链8RPM的许用值");

            double wearRate = hookResult.getWearRate().doubleValue();
            assertTrue(wearRate > 1.0, "超许用转速运行时磨损率应显著升高");

            BigDecimal expectedLife = hookResult.getExpectedLifespanHours();
            assertTrue(expectedLife.doubleValue() < 60000, "超速运行时预期寿命应低于正常水平");
        }

        @Test
        @DisplayName("边界输入：最小刮板深度 0.05m")
        void testMinimumScraperDepth() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double depth = invocation.getArgument(0);
                        double width = invocation.getArgument(1);
                        double angle = invocation.getArgument(2);
                        double speed = invocation.getArgument(3);
                        double area = depth * width * Math.sin(Math.toRadians(angle));
                        return area * speed * 3600000 * 0.85;
                    });

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, 15.0, 80.0, 0.05, 0.25, 40.0);

            double totalFlow = result.getResults().stream()
                    .mapToDouble(r -> r.getActualWaterFlowLh().doubleValue())
                    .average().orElse(0);

            assertTrue(totalFlow > 0, "最小深度也应产生正流量");
            assertTrue(totalFlow < 8000, "0.05m深度提水量应小于正常深度的提水量");
        }

        @Test
        @DisplayName("边界输入：最大刮板角度 60°")
        void testMaximumScraperAngle() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double angle = invocation.getArgument(2);
                        return 10000.0 * Math.sin(Math.toRadians(angle));
                    });

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, 15.0, 80.0, 0.12, 0.25, 60.0);

            assertNotNull(result);
            assertEquals(3, result.getResults().size());
            verify(optimizer, times(3)).calculateWaterFlow(
                    eq(0.12), eq(0.25), eq(60.0), anyDouble());
        }
    }

    // =============  异常场景  =============
    @Nested
    @DisplayName("异常场景测试")
    class ExceptionScenario {

        @Test
        @DisplayName("异常输入：无效设备ID，应抛出异常")
        void testInvalidDeviceId() {
            when(deviceRepository.findById(9999)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> comparisonService.compareChainTypes(9999, 15.0, 80.0, 0.12, 0.25, 40.0),
                    "不存在的设备ID应抛出异常");

            assertTrue(ex.getMessage().contains("9999"), "异常信息应包含设备ID");
            verify(deviceRepository, times(1)).findById(9999);
        }

        @Test
        @DisplayName("异常输入：负转速，产生负流量被clamp为0")
        void testNegativeSpeedInput() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double speed = invocation.getArgument(3);
                        return Math.max(0, 5000.0 * speed * 3.6);
                    });

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, -5.0, 80.0, 0.12, 0.25, 40.0);

            assertNotNull(result);
            for (ChainTypeComparisonDTO.ChainTypeResult r : result.getResults()) {
                assertTrue(r.getActualWaterFlowLh().doubleValue() >= 0,
                        r.getChainTypeName() + "提水量不应为负");
            }
        }

        @Test
        @DisplayName("异常输入：扭矩为0，张力计算保护")
        void testZeroTorqueInput() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(5000.0);

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, 15.0, 0.0, 0.12, 0.25, 40.0);

            assertNotNull(result);
            for (ChainTypeComparisonDTO.ChainTypeResult r : result.getResults()) {
                assertTrue(r.getChainTensionN().doubleValue() >= 0,
                        r.getChainTypeName() + "张力不应为负");
            }
        }

        @Test
        @DisplayName("异常输入：空的可选参数，使用设备默认值")
        void testNullOptionalParameters() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(5000.0);

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, null, null, null, null, null);

            assertNotNull(result);
            assertEquals(15.0, result.getInputSpeedRPM(), TestDataFactory.TOLERANCE,
                    "转速为空应使用设备默认值");
            assertEquals(0.12, result.getScraperDepth(), TestDataFactory.TOLERANCE,
                    "深度为空应使用默认值0.12m");
        }
    }

    // =============  数学一致性验证  =============
    @Nested
    @DisplayName("数学一致性验证")
    class MathematicalValidation {

        @Test
        @DisplayName("功率守恒验证：输入功率 × 效率 ≈ 有效功率")
        void testPowerConservation() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10000.0);

            double torque = 80.0;
            double speedRPM = 15.0;
            double angularVelocity = speedRPM * Math.PI / 30.0;
            double expectedPowerW = torque * angularVelocity;

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, speedRPM, torque, 0.12, 0.25, 40.0);

            for (ChainTypeComparisonDTO.ChainTypeResult r : result.getResults()) {
                double actualPower = r.getPowerConsumptionW().doubleValue();
                double frictionLoss = r.getFrictionLossW().doubleValue();
                ChainType ct = ChainType.fromCode(r.getChainTypeCode());

                assertEquals(expectedPowerW, actualPower, TestDataFactory.TOLERANCE,
                        ct.getDisplayName() + "功率输入应等于扭矩×角速度");

                double expectedFriction = expectedPowerW * ct.getFrictionCoefficient() * 0.3;
                assertEquals(expectedFriction, frictionLoss, 1.0,
                        ct.getDisplayName() + "摩擦损耗应与摩擦系数成正比");
            }
        }

        @Test
        @DisplayName("保水率验证：钩链保水率最高但提水量最低")
        void testWaterRetentionTradeoff() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10000.0);

            ChainTypeComparisonDTO result = comparisonService.compareChainTypes(
                    1, 10.0, 80.0, 0.12, 0.25, 40.0);

            double maxRetention = 0;
            double minFlow = Double.MAX_VALUE;
            String bestRetentionType = null;
            String worstFlowType = null;

            for (ChainTypeComparisonDTO.ChainTypeResult r : result.getResults()) {
                ChainType ct = ChainType.fromCode(r.getChainTypeCode());
                if (ct.getWaterRetentionRate() > maxRetention) {
                    maxRetention = ct.getWaterRetentionRate();
                    bestRetentionType = ct.getCode();
                }
                if (r.getActualWaterFlowLh().doubleValue() < minFlow) {
                    minFlow = r.getActualWaterFlowLh().doubleValue();
                    worstFlowType = ct.getCode();
                }
            }

            assertEquals("hook", bestRetentionType, "钩链保水率应最高(95%)");
            assertEquals("hook", worstFlowType,
                    "尽管保水率最高，但钩链传动效率低导致最终提水量仍最低");
        }
    }
}
