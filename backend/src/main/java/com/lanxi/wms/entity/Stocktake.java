package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 盘点单
 */
@Data
@TableName("wms_stocktake")
public class Stocktake {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long warehouseId;
    /** 1全盘 2循环盘 */
    private Integer type;
    /** 0待盘点 1盘点中 2待审核 3已完成 4已取消 */
    private Integer status;
    private Integer totalCount;
    private Integer gainQty;
    private Integer lossQty;
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
