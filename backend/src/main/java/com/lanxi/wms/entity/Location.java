package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库位
 */
@Data
@TableName("wms_location")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    /** 库位编码，如 A-01-03 */
    private String code;
    /** 库区：RECEIVE收货/STORE存储/PICK拣选/DEFECT残次/SHIP发货 */
    private String zone;
    private Integer capacity;
    private Integer used;
    /** 1启用 0停用 */
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
