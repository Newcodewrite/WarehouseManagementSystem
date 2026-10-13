package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆
 */
@Data
@TableName("log_vehicle")
public class Vehicle {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String vehicleType;
    private String driverName;
    private String driverPhone;
    private Integer capacityKg;
    /** 状态 0空闲 1运输中 2维修 */
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
