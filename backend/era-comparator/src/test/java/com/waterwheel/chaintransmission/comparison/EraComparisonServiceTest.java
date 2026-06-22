package com.waterwheel.chaintransmission.comparison;

import com.waterwheel.chaintransmission.comparison.service.EraComparisonService;
import com.waterwheel.chaintransmission.dto.EraComparisonDTO;
import com.waterwheel.chaintransmission.entity.EraType;
import com.waterwheel.chaintransmission.entity.WaterwheelDevice;
import com.waterwheel.chaintransmission.optimization.WaterEfficiencyOptimizer;
import com.waterwheel.chaintransmission.repository.WaterwheelDeviceRepository;
import com.waterwheel.chaintransmission.test.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("功能2：跨时代对比测试（宋代 vs 现代）")
class EraComparisonServiceTest {

    @Mock
    private WaterwheelDeviceRepository deviceRepository;

    @Mock
    private WaterEfficiencyOptimizer optimizer;

    @InjectMocks
    private EraComparisonService eraService;

    private WaterwheelDevice defaultDevice;

    @BeforeEach
    void setUp() {
        defaultDevice = TestDataFactory.createDefaultDevice();
    }

    // =============  正常场景  =============
    @Nested
    @DisplayName("正常场景：技术进步验证")
    class NormalScenario {

        @Test
        @DisplayName("总效率验证：现代 > 古代，提升约1.9倍")
        void testTotalEfficiencyImprovement() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(15000.0);

            EraComparisonDTO result = eraService.compareEras(1, 1.0, 1.0);

            assertNotNull(result);
            assertEquals(2, result.getResults().size(), "应返回两个时代结果");

            EraComparisonDTO.EraResult ancient = result.getResults().get(0);
            EraComparisonDTO.EraResult modern = result.getResults().get(1);

            double ancientEff = ancient.getTotalEfficiency().doubleValue();
            double modernEff = modern.getTotalEfficiency().doubleValue();

            assertEquals(EraType.ANCIENT_SONG.getTotalEfficiency(), ancientEff, TestDataFactory.TOLERANCE,
                    "古代总效率应与枚举值一致");
            assertEquals(EraType.MODERN_ELECTRIC.getTotalEfficiency(), modernEff, TestDataFactory.TOLERANCE,
                    "现代总效率应与枚举值一致");

            double improvementRatio = modernEff / ancientEff;
            assertTrue(improvementRatio > 1.8 && improvementRatio < 2.0,
                    "现代总效率应为古代的1.8~2.0倍，实际: " + improvementRatio);
        }

        @Test
        @DisplayName("提水量验证：现代链式泵提水量应为古代的2.5~3.5倍")
        void testWaterFlowImprovement() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double speed = invocation.getArgument(3);
                        double depth = invocation.getArgument(0);
                        return depth * speed * 3600000 * 0.7;
                    });

            EraComparisonDTO result = eraService.compareEras(1, 1.0, 1.0);

            double ancientFlow = result.getResults().get(0).getWaterFlowLh().doubleValue();
            double modernFlow = result.getResults().get(1).getWaterFlowLh().doubleValue();

            double flowRatio = modernFlow / ancientFlow;
            assertTrue(flowRatio > 2.5 && flowRatio < 3.5,
                    "现代提水量应为古代的2.5~3.5倍，实际: " + flowRatio);

            assertTrue(ancientFlow > 0, "古代也应有正提水量");
        }

        @Test
        @DisplayName("核心指标对比：6项指标全部包含且现代领先")
        void testKeyMetricsAllPresentAndModernSuperior() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10000.0);

            EraComparisonDTO result = eraService.compareEras(1, 1.0, 1.0);

            assertEquals(6, result.getKeyMetrics().size(), "应包含6项核心对比指标");

            String[] expectedMetrics = {"提水量", "总效率", "能量成本", "典型转速", "年维护工时", "设备寿命"};
            for (int i = 0; i < expectedMetrics.length; i++) {
                assertEquals(expectedMetrics[i], result.getKeyMetrics().get(i).getMetricName(),
                        "第" + (i+1) + "项指标名不匹配");
            }

            for (EraComparisonDTO.ComparisonMetric m : result.getKeyMetrics()) {
                double ancientVal = m.getAncientValue().doubleValue();
                double modernVal = m.getModernValue().doubleValue();
                double ratio = m.getImprovementRatio().doubleValue();

                if ("能量成本".equals(m.getMetricName())) {
                    assertTrue(modernVal > ancientVal, "现代能量成本应更高（有电费）");
                } else if ("年维护工时".equals(m.getMetricName())) {
                    assertTrue(modernVal < ancientVal, "现代年维护工时应更少");
                    assertTrue(ratio < 1.0, "维护工时提升比应小于1");
                } else {
                    assertTrue(modernVal > ancientVal, m.getMetricName() + "现代应优于古代");
                    assertTrue(ratio > 1.0, m.getMetricName() + "提升比应大于1");
                }

                assertNotNull(m.getImprovementDescription(),
                        m.getMetricName() + "应有改进描述");
                assertTrue(m.getImprovementDescription().length() > 5,
                        m.getMetricName() + "改进描述过短");
            }
        }

        @Test
        @DisplayName("三级效率叠乘验证：总效率 = 机械 × 传动 × 控制")
        void testEfficiencyMultiplicativeLaw() {
            double mechanical = EraType.MODERN_ELECTRIC.getMechanicalEfficiency();
            double transmission = EraType.MODERN_ELECTRIC.getTransmissionEfficiency();
            double control = EraType.MODERN_ELECTRIC.getControlEfficiency();
            double expectedTotal = mechanical * transmission * control;
            double actualTotal = EraType.MODERN_ELECTRIC.getTotalEfficiency();

            assertEquals(expectedTotal, actualTotal, TestDataFactory.TOLERANCE,
                    "现代三级效率叠乘应等于总效率");

            double ancientMech = EraType.ANCIENT_SONG.getMechanicalEfficiency();
            double ancientTrans = EraType.ANCIENT_SONG.getTransmissionEfficiency();
            double ancientControl = EraType.ANCIENT_SONG.getControlEfficiency();
            double ancientExpected = ancientMech * ancientTrans * (ancientControl == 0 ? 1.0 : ancientControl);
            double ancientActual = EraType.ANCIENT_SONG.getTotalEfficiency();

            assertEquals(ancientExpected, ancientActual, TestDataFactory.TOLERANCE,
                    "古代两级效率叠乘（控制效率为0时视为1）应等于总效率");
        }

        @Test
        @DisplayName("历史语境验证：宋代和现代各自元数据完整")
        void testHistoricalContextPresent() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10000.0);

            EraComparisonDTO result = eraService.compareEras(1, 1.0, 1.0);

            EraComparisonDTO.EraResult ancient = result.getResults().get(0);
            EraComparisonDTO.EraResult modern = result.getResults().get(1);

            assertNotNull(ancient.getHistoricalContext());
            assertNotNull(modern.getHistoricalContext());

            assertTrue(ancient.getHistoricalContext().containsKey("dynasty"),
                    "古代语境应包含朝代");
            assertTrue(ancient.getHistoricalContext().containsKey("inventor"),
                    "古代语境应包含发明人");
            assertTrue(ancient.getHistoricalContext().get("dynasty").toString().contains("宋"),
                    "朝代应为宋代");

            assertTrue(modern.getHistoricalContext().containsKey("controlSystem"),
                    "现代语境应包含控制系统");
            assertTrue(modern.getHistoricalContext().containsKey("standardCompliance"),
                    "现代语境应包含合规标准");
        }

        @Test
        @DisplayName("链式泵转速验证：现代典型转速22RPM vs 宋代3.5RPM，约6.3倍")
        void testSpeedRatio() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10000.0);

            EraComparisonDTO result = eraService.compareEras(1, 1.0, 1.0);

            double ancientRPM = result.getResults().get(0).getTypicalSpeedRPM().doubleValue();
            double modernRPM = result.getResults().get(1).getTypicalSpeedRPM().doubleValue();

            double speedRatio = modernRPM / ancientRPM;
            assertEquals(6.3, speedRatio, 0.1, "现代/古代转速比应为约6.3倍");
        }
    }

    // =============  参数化测试  =============
    @Nested
    @DisplayName("参数化测试：多缩放比例验证")
    class ParametrizedTests {

        @ParameterizedTest
        @CsvSource({
                "0.5, 0.8",
                "1.0, 1.0",
                "1.5, 1.2",
                "2.0, 1.5"
        })
        @DisplayName("缩放比例影响：{0}倍链速 + {1}倍尺寸")
        void testScaleRatioImpact(double speedRatio, double sizeScale) {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double depth = invocation.getArgument(0);
                        double width = invocation.getArgument(1);
                        double speed = invocation.getArgument(3);
                        return depth * width * speed * 1e6;
                    });

            EraComparisonDTO result = eraService.compareEras(1, speedRatio, sizeScale);

            assertNotNull(result);
            assertEquals(speedRatio, result.getChainSpeedRatio(), TestDataFactory.TOLERANCE);
            assertEquals(sizeScale, result.getScraperSizeScale(), TestDataFactory.TOLERANCE);

            double flow = result.getResults().get(1).getWaterFlowLh().doubleValue();
            assertTrue(flow > 0, sizeScale + "倍尺寸也应产生正流量");

            if (speedRatio > 1.0 || sizeScale > 1.0) {
                verify(optimizer, atLeastOnce()).calculateWaterFlow(
                        anyDouble(), anyDouble(), anyDouble(), anyDouble());
            }
        }
    }

    // =============  边界场景  =============
    @Nested
    @DisplayName("边界场景测试")
    class BoundaryScenario {

        @Test
        @DisplayName("边界：最小缩放 0.5倍速 + 0.8倍尺寸，仍可对比")
        void testMinimumScaleRatios() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(5000.0);

            EraComparisonDTO result = eraService.compareEras(1, 0.5, 0.8);

            assertNotNull(result);
            assertEquals(2, result.getResults().size());

            double ancientFlow = result.getResults().get(0).getWaterFlowLh().doubleValue();
            double modernFlow = result.getResults().get(1).getWaterFlowLh().doubleValue();

            assertTrue(ancientFlow > 0, "最小缩放古代仍应提水");
            assertTrue(modernFlow > ancientFlow, "最小缩放现代仍更强");
        }

        @Test
        @DisplayName("边界：最大缩放 2.0倍速 + 1.5倍尺寸，验证极限性能")
        void testMaximumScaleRatios() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenAnswer(invocation -> {
                        double depth = invocation.getArgument(0);
                        double speed = invocation.getArgument(3);
                        return depth * speed * 1e6;
                    });

            EraComparisonDTO result = eraService.compareEras(1, 2.0, 1.5);

            EraComparisonDTO.EraResult modern = result.getResults().get(1);
            double flow = modern.getWaterFlowLh().doubleValue();
            double power = modern.getPowerInputKW().doubleValue();

            assertTrue(flow > 80000, "最大缩放现代提水量应超过80m³/h");
            assertTrue(power > 5, "最大缩放现代功率应超过5kW");
        }

        @Test
        @DisplayName("边界：宋代低水位场景，能量成本为0（不用交电费）")
        void testAncientZeroEnergyCost() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(8000.0);

            EraComparisonDTO result = eraService.compareEras(1, 1.0, 0.6);

            double ancientCost = result.getResults().get(0).getEnergyCostPerCubicYuan().doubleValue();
            double modernCost = result.getResults().get(1).getEnergyCostPerCubicYuan().doubleValue();

            assertEquals(0.0, ancientCost, TestDataFactory.TOLERANCE,
                    "宋代水力驱动能量成本应为0");
            assertTrue(modernCost > 0, "现代电力驱动能量成本应大于0");
        }
    }

    // =============  异常场景  =============
    @Nested
    @DisplayName("异常场景测试")
    class ExceptionScenario {

        @Test
        @DisplayName("异常：无效设备ID抛异常")
        void testInvalidDeviceId() {
            when(deviceRepository.findById(999)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> eraService.compareEras(999, 1.0, 1.0));

            assertTrue(ex.getMessage().contains("999"));
        }

        @Test
        @DisplayName("异常：缩放比例null使用默认值1.0")
        void testNullScaleRatiosUseDefaults() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(10000.0);

            EraComparisonDTO result = eraService.compareEras(1, null, null);

            assertEquals(1.0, result.getChainSpeedRatio(), TestDataFactory.TOLERANCE);
            assertEquals(1.0, result.getScraperSizeScale(), TestDataFactory.TOLERANCE);
        }

        @Test
        @DisplayName("异常：超大功率输入，现代功率封顶保护")
        void testExcessivePowerProtection() {
            WaterwheelDevice bigDevice = TestDataFactory.createDevice(
                    10, "大型测试泵", "round", "modern_electric",
                    50.0, 800.0, 30.0, 200, 36);

            when(deviceRepository.findById(10)).thenReturn(Optional.of(bigDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(200000.0);

            EraComparisonDTO result = eraService.compareEras(10, 2.0, 1.5);

            EraComparisonDTO.EraResult modern = result.getResults().get(1);
            double power = modern.getPowerInputKW().doubleValue();

            assertTrue(power > 0, "功率应为正");
        }

        @Test
        @DisplayName("异常：零流量输入，能量成本计算保护（不除以零）")
        void testZeroFlowProtection() {
            when(deviceRepository.findById(anyInt())).thenReturn(Optional.of(defaultDevice));
            when(optimizer.calculateWaterFlow(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(0.0);

            EraComparisonDTO result = eraService.compareEras(1, 1.0, 1.0);

            for (EraComparisonDTO.EraResult r : result.getResults()) {
                double cost = r.getEnergyCostPerCubicYuan().doubleValue();
                assertTrue(Double.isFinite(cost), "零流量时能量成本应为有限值而非NaN/Infinite");
            }
        }
    }

    // =============  元数据接口  =============
    @Nested
    @DisplayName("元数据接口测试")
    class MetaDataTests {

        @Test
        @DisplayName("获取所有时代元数据：两个时代完整信息")
        void testGetAllEraMeta() {
            var metaList = eraService.getAllEraMeta();

            assertEquals(2, metaList.size());
            metaList.forEach(meta -> {
                assertTrue(meta.containsKey("code"));
                assertTrue(meta.containsKey("name"));
                assertTrue(meta.containsKey("mechanicalEfficiency"));
                assertTrue(meta.containsKey("transmissionEfficiency"));
                assertTrue(meta.containsKey("controlEfficiency"));
                assertTrue(meta.containsKey("totalEfficiency"));
                assertTrue(meta.containsKey("typicalPowerKW"));
            });
        }
    }
}
