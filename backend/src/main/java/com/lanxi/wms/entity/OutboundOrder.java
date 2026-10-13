package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出库单
 */
@Data
@TableName("wms_outbound_order")
public class OutboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long warehouseId;
    private Long customerId;
    private BigDecimal totalAmount;
    /** 状态 0待审核 1已出库 2已驳回 */
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
