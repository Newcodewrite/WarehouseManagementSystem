package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退货单（逆向物流）
 */
@Data
@TableName("wms_return_order")
public class ReturnOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long customerId;
    private Long warehouseId;
    /** 0待收货 1待质检 2已完成 3已取消 */
    private Integer status;
    private Integer totalQty;
    /** 可二次销售数量 */
    private Integer resaleQty;
    /** 报废数量 */
    private Integer scrapQty;
    private String remark;
    private String createBy;
    /** 质检处理人 */
    private String auditBy;
    private LocalDateTime auditTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
