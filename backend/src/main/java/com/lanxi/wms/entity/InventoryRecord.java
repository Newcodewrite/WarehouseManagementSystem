package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水
 */
@Data
@TableName("wms_inventory_record")
public class InventoryRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long productId;
    /** 类型 IN入库 OUT出库 ADJUST调整 */
    private String type;
    /** 变动数量（正负） */
    private Integer quantity;
    private Integer afterQuantity;
    private Long orderId;
    private String remark;
    /** 批次号 */
    private String batchNo;
    /** 库位ID */
    private Long locationId;
    private String createBy;
    private LocalDateTime createTime;
}
