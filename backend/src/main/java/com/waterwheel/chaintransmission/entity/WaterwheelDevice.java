package com.waterwheel.chaintransmission.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "waterwheel_device")
public class WaterwheelDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Integer deviceId;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "location_code", length = 50)
    private String locationCode;

    @Column(name = "chain_length", precision = 10, scale = 2)
    private BigDecimal chainLength;

    @Column(name = "chain_length_cm", precision = 10, scale = 2)
    private BigDecimal chainLengthCm;

    @Column(name = "nominal_sprocket_speed_rpm", precision = 10, scale = 2)
    private BigDecimal nominalSprocketSpeedRpm;

    @Column(name = "num_links")
    private Integer numLinks;

    @Column(name = "sprocket_radius", precision = 10, scale = 2)
    private BigDecimal sprocketRadius;

    @Column(name = "sprocket_radius_cm", precision = 10, scale = 2)
    private BigDecimal sprocketRadiusCm;

    @Column(name = "scraper_count")
    private Integer scraperCount;

    @Column(name = "scraper_shape", length = 50)
    private String scraperShape;

    @Column(name = "chain_type_code", length = 20)
    private String chainTypeCode;

    @Column(name = "era_code", length = 30)
    private String eraCode;

    @Column(name = "parallel_group_id")
    private Integer parallelGroupId;

    @Column(name = "installation_date")
    private LocalDateTime installationDate;

    @Column(name = "status", length = 20)
    private String status;

    public double getNominalSprocketSpeedRpmDouble() {
        if (nominalSprocketSpeedRpm != null) {
            return nominalSprocketSpeedRpm.doubleValue();
        }
        return 15.0;
    }

    public double getSprocketRadiusCmDouble() {
        if (sprocketRadiusCm != null) {
            return sprocketRadiusCm.doubleValue();
        }
        if (sprocketRadius != null) {
            return sprocketRadius.doubleValue() * 100.0;
        }
        return 30.0;
    }

    public double getChainLengthCmDouble() {
        if (chainLengthCm != null) {
            return chainLengthCm.doubleValue();
        }
        if (chainLength != null) {
            return chainLength.doubleValue() * 100.0;
        }
        return 500.0;
    }

    public int getScraperCountInt() {
        return scraperCount != null ? scraperCount : 24;
    }
}
