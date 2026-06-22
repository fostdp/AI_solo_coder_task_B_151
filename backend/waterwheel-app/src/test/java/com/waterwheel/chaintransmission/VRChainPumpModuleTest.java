package com.waterwheel.chaintransmission;

import com.waterwheel.chaintransmission.virtualoperation.service.VirtualOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VRChainPump 模块独立测试")
class VRChainPumpModuleTest {

    @InjectMocks
    private VirtualOperationService operationService;

    @Mock
    private Object unusedRepository;

    @Mock
    private Object unusedOptimizer;

    private Method applySensitivityCurveMethod;
    private double MIN_SPEED;
    private double MAX_SPEED;

    @BeforeEach
    void setUp() throws Exception {
        applySensitivityCurveMethod = VirtualOperationService.class
                .getDeclaredMethod("applySensitivityCurve", double.class);
        applySensitivityCurveMethod.setAccessible(true);

        Field minSpeedField = VirtualOperationService.class.getDeclaredField("MIN_SPEED");
        minSpeedField.setAccessible(true);
        MIN_SPEED = (double) minSpeedField.get(null);

        Field maxSpeedField = VirtualOperationService.class.getDeclaredField("MAX_SPEED");
        maxSpeedField.setAccessible(true);
        MAX_SPEED = (double) maxSpeedField.get(null);
    }

    private double applySensitivityCurve(double speed) throws Exception {
        return (double) applySensitivityCurveMethod.invoke(operationService, speed);
    }

    @Test
    @DisplayName("testSensitivityCurveLowSpeedBoost - 低速放大：0.5→0.75（+50%）")
    void testSensitivityCurveLowSpeedBoost() throws Exception {
        double input = 0.5;
        double output = applySensitivityCurve(input);

        assertTrue(output > input,
                "低速应被放大: 输入=" + input + " 输出=" + output);

        double gainPercent = (output / input - 1.0) * 100.0;
        assertTrue(gainPercent >= 40.0 && gainPercent <= 70.0,
                "低速增益应约为+50%（40%~70%），实际: " + String.format("%.1f", gainPercent) + "%");

        double expectedLow = 0.75;
        double tolerance = Math.abs(expectedLow) * 0.15;
        assertEquals(expectedLow, output, tolerance,
                "0.5m/s 输入应输出约 0.75m/s (±15%)，实际: " + output);
    }

    @Test
    @DisplayName("testSensitivityCurveMidSpeedLinear - 中速近似线性：1.5≈1.65")
    void testSensitivityCurveMidSpeedLinear() throws Exception {
        double input = 1.5;
        double output = applySensitivityCurve(input);

        double expectedMid = 1.65;
        double tolerance = Math.abs(expectedMid) * 0.10;
        assertEquals(expectedMid, output, tolerance,
                "1.5m/s 输入应约为 1.65m/s (±10%)，实际: " + output);

        double gainPercent = (output / input - 1.0) * 100.0;
        assertTrue(gainPercent >= 0.0,
                "中速也应略有增益，实际增益: " + String.format("%.1f", gainPercent) + "%");
        assertTrue(gainPercent <= 25.0,
                "中速增益不应过高 (<25%)，实际增益: " + String.format("%.1f", gainPercent) + "%");
    }

    @Test
    @DisplayName("testSensitivityCurveHighSpeedCompress - 高速压缩：3.5 < 3.5（-3%到-4%）")
    void testSensitivityCurveHighSpeedCompress() throws Exception {
        double input = 3.5;
        double output = applySensitivityCurve(input);

        assertTrue(output < input,
                "高速应被压缩: 输入=" + input + " 输出=" + output);

        double compressPercent = (1.0 - output / input) * 100.0;
        assertTrue(compressPercent >= 1.0 && compressPercent <= 10.0,
                "高速压缩应在1%~10%范围，实际压缩: " + String.format("%.1f", compressPercent) + "%");
    }

    @Test
    @DisplayName("testSensitivityCurveMonotonicIncreasing - 输入递增：speed1<speed2 → curve(speed1)<curve(speed2)")
    void testSensitivityCurveMonotonicIncreasing() throws Exception {
        double[] testSpeeds = {0.3, 0.5, 0.8, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};

        double prevOutput = Double.NEGATIVE_INFINITY;
        for (double speed : testSpeeds) {
            double currentOutput = applySensitivityCurve(speed);
            assertTrue(currentOutput > prevOutput,
                    "曲线应严格单调递增: speed=" + speed
                            + " 前输出=" + prevOutput + " 当前输出=" + currentOutput);
            prevOutput = currentOutput;
        }

        for (double speed = MIN_SPEED; speed <= MAX_SPEED; speed += 0.05) {
            double output1 = applySensitivityCurve(speed);
            double output2 = applySensitivityCurve(speed + 0.001);
            assertTrue(output2 >= output1 - 1e-9,
                    "微小区间也应单调不减: speed=" + speed
                            + " output(speed)=" + output1
                            + " output(speed+ε)=" + output2);
        }
    }

    @Test
    @DisplayName("testSensitivityCurveBoundaries - 边界：min=MIN_SPEED、max=MAX_SPEED")
    void testSensitivityCurveBoundaries() throws Exception {
        assertEquals(0.3, MIN_SPEED, 1e-9, "MIN_SPEED 常量应为 0.3");
        assertEquals(4.0, MAX_SPEED, 1e-9, "MAX_SPEED 常量应为 4.0");

        double atMin = applySensitivityCurve(MIN_SPEED);
        assertEquals(MIN_SPEED, atMin, 1e-9,
                "MIN_SPEED 输入应映射到 MIN_SPEED 输出");

        double atMax = applySensitivityCurve(MAX_SPEED);
        assertEquals(MAX_SPEED, atMax, 1e-9,
                "MAX_SPEED 输入应映射到 MAX_SPEED 输出");

        double belowMin = applySensitivityCurve(-100.0);
        assertEquals(MIN_SPEED, belowMin, 1e-9,
                "低于 MIN_SPEED 的输入应被 clamp 到 MIN_SPEED");

        double zeroInput = applySensitivityCurve(0.0);
        assertEquals(MIN_SPEED, zeroInput, 1e-9,
                "0 输入应被 clamp 到 MIN_SPEED");

        double aboveMax = applySensitivityCurve(100.0);
        assertEquals(MAX_SPEED, aboveMax, 1e-9,
                "高于 MAX_SPEED 的输入应被 clamp 到 MAX_SPEED");

        double midInput = (MIN_SPEED + MAX_SPEED) / 2.0;
        double midOutput = applySensitivityCurve(midInput);
        assertTrue(midOutput > MIN_SPEED && midOutput < MAX_SPEED,
                "中间输入应在 (MIN, MAX) 区间内");
    }
}
