package com.waterwheel.chaintransmission;

import com.waterwheel.chaintransmission.entity.EraType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EraComparator 模块独立测试")
class EraComparatorModuleTest {

    @Test
    @DisplayName("testEraTypeEnumIntegrity - 完整性测试：EraType.values().length==2")
    void testEraTypeEnumIntegrity() {
        EraType[] values = EraType.values();
        assertEquals(2, values.length, "EraType 应有2个枚举值");

        boolean hasAncient = false;
        boolean hasModern = false;
        for (EraType era : values) {
            if (era == EraType.ANCIENT_SONG) hasAncient = true;
            if (era == EraType.MODERN_ELECTRIC) hasModern = true;
        }
        assertTrue(hasAncient, "应包含 ANCIENT_SONG");
        assertTrue(hasModern, "应包含 MODERN_ELECTRIC");

        assertNotNull(EraType.valueOf("ANCIENT_SONG"));
        assertNotNull(EraType.valueOf("MODERN_ELECTRIC"));
    }

    @Test
    @DisplayName("testModernPumpStandardCompliance - 现代泵有标准合规字段包含GB/T 3216")
    void testModernPumpStandardCompliance() {
        String compliance = EraType.MODERN_ELECTRIC.getStandardCompliance();
        assertNotNull(compliance, "现代泵标准合规字段不应为 null");
        assertFalse(compliance.trim().isEmpty(), "现代泵标准合规字段不应为空");

        assertTrue(compliance.contains("GB/T 3216"),
                "现代泵应包含 GB/T 3216 回转动力泵标准，实际: " + compliance);

        assertTrue(compliance.contains("GB") || compliance.contains("JB/T"),
                "现代泵应引用国家标准或行业标准");
    }

    @Test
    @DisplayName("testAncientSource - 古代引用《农书》《天工开物》等经典文献")
    void testAncientSource() {
        String ancientSource = EraType.ANCIENT_SONG.getStandardCompliance();
        assertNotNull(ancientSource, "古代标准来源字段不应为 null");

        boolean hasNongShu = ancientSource.contains("《农书》") || ancientSource.contains("农书");
        boolean hasTiangong = ancientSource.contains("《天工开物》") || ancientSource.contains("天工开物");
        boolean hasHistory = ancientSource.contains("机械工程史")
                || ancientSource.contains("王祯")
                || ancientSource.contains("中国古代");

        assertTrue(hasNongShu || hasTiangong || hasHistory,
                "古代应引用经典文献，实际: " + ancientSource);

        assertNotNull(EraType.ANCIENT_SONG.getDisplayName());
        assertTrue(EraType.ANCIENT_SONG.getDisplayName().contains("宋")
                        || EraType.ANCIENT_SONG.getDisplayName().contains("古代"),
                "古代名称应体现宋代或古代特征");
    }

    @Test
    @DisplayName("testTotalEfficiencyCalculation - 效率三级乘法")
    void testTotalEfficiencyCalculation() {
        double ancientMech = EraType.ANCIENT_SONG.getMechanicalEfficiency();
        double ancientTrans = EraType.ANCIENT_SONG.getTransmissionEfficiency();
        double ancientControl = EraType.ANCIENT_SONG.getControlEfficiency();
        double ancientExpected = ancientMech * ancientTrans * (ancientControl == 0 ? 1.0 : ancientControl);
        double ancientActual = EraType.ANCIENT_SONG.getTotalEfficiency();
        assertEquals(ancientExpected, ancientActual, 1e-9,
                "古代总效率应为机械×传动×(控制或1)");

        double modernMech = EraType.MODERN_ELECTRIC.getMechanicalEfficiency();
        double modernTrans = EraType.MODERN_ELECTRIC.getTransmissionEfficiency();
        double modernControl = EraType.MODERN_ELECTRIC.getControlEfficiency();
        double modernExpected = modernMech * modernTrans * modernControl;
        double modernActual = EraType.MODERN_ELECTRIC.getTotalEfficiency();
        assertEquals(modernExpected, modernActual, 1e-9,
                "现代总效率应为机械×传动×控制的三级乘法");

        assertTrue(modernMech > 0 && modernMech <= 1.0, "机械效率应在 (0, 1]");
        assertTrue(modernTrans > 0 && modernTrans <= 1.0, "传动效率应在 (0, 1]");
        assertTrue(modernControl >= 0 && modernControl <= 1.0, "控制效率应在 [0, 1]");
        assertTrue(modernActual > 0 && modernActual <= 1.0, "总效率应在 (0, 1]");
    }

    @Test
    @DisplayName("testFromCodeFallBack - 编码回退默认值")
    void testFromCodeFallBack() {
        EraType nullResult = EraType.fromCode(null);
        assertEquals(EraType.ANCIENT_SONG, nullResult,
                "fromCode(null) 应回退到 ANCIENT_SONG");

        EraType emptyResult = EraType.fromCode("");
        assertEquals(EraType.ANCIENT_SONG, emptyResult,
                "fromCode(\"\") 应回退到 ANCIENT_SONG");

        EraType invalidResult = EraType.fromCode("NON_EXISTENT_ERA");
        assertEquals(EraType.ANCIENT_SONG, invalidResult,
                "fromCode(无效) 应回退到 ANCIENT_SONG");

        EraType garbageResult = EraType.fromCode("!!!@@@###");
        assertEquals(EraType.ANCIENT_SONG, garbageResult,
                "fromCode(乱码) 应回退到 ANCIENT_SONG");

        EraType ancientResult = EraType.fromCode("ancient_song");
        assertEquals(EraType.ANCIENT_SONG, ancientResult, "ancient_song 应匹配");

        EraType modernResult = EraType.fromCode("MODERN_ELECTRIC");
        assertEquals(EraType.MODERN_ELECTRIC, modernResult, "MODERN_ELECTRIC 大小写不敏感");

        EraType mixedCase = EraType.fromCode("Ancient_Song");
        assertEquals(EraType.ANCIENT_SONG, mixedCase, "Ancient_Song 大小写不敏感");
    }

    @Test
    @DisplayName("testMultiplicativeLawPreserved - 现代总效率>古代总效率")
    void testMultiplicativeLawPreserved() {
        double ancientTotal = EraType.ANCIENT_SONG.getTotalEfficiency();
        double modernTotal = EraType.MODERN_ELECTRIC.getTotalEfficiency();

        assertTrue(modernTotal > ancientTotal,
                "现代总效率 (" + modernTotal + ") 应大于古代总效率 (" + ancientTotal + ")");

        double improvementRatio = modernTotal / ancientTotal;
        assertTrue(improvementRatio > 1.5,
                "现代效率应为古代的1.5倍以上，实际: " + improvementRatio + "倍");
        assertTrue(improvementRatio < 3.0,
                "效率提升倍数应合理 (<3倍)，实际: " + improvementRatio + "倍");

        double ancientMech = EraType.ANCIENT_SONG.getMechanicalEfficiency();
        double modernMech = EraType.MODERN_ELECTRIC.getMechanicalEfficiency();
        assertTrue(modernMech > ancientMech, "现代机械效率应更高");

        double ancientTrans = EraType.ANCIENT_SONG.getTransmissionEfficiency();
        double modernTrans = EraType.MODERN_ELECTRIC.getTransmissionEfficiency();
        assertTrue(modernTrans > ancientTrans, "现代传动效率应更高");
    }
}
