package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流订单
 */
@Data
@TableName("log_order")
public class LogisticsOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long outboundOrderId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Long vehicleId;
    private String driverName;
    /** 状态 0待调度 1运输中 2已签收 3已取消 */
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
