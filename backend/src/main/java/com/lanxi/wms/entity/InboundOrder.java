package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入库单
 */
@Data
@TableName("wms_inbound_order")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long warehouseId;
    private Long supplierId;
    private BigDecimal totalAmount;
    /** 状态 0待审核 1已入库 2已驳回 */
    private Integer status;
    private String remark;
    private String createBy;
    private String auditBy;
    private LocalDateTime auditTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
