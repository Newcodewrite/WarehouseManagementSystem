package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 盘点明细
 */
@Data
@TableName("wms_stocktake_item")
public class StocktakeItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stocktakeId;
    private Long productId;
    private Long locationId;
    /** 账面数量 */
    private Integer bookQty;
    /** 实盘数量 */
    private Integer actualQty;
    /** 差异 = 实盘 - 账面 */
    private Integer diffQty;
}
