package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业任务
 */
@Data
@TableName("wms_task")
public class WmsTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    /** 1上架 2拣选 3盘点 4移库 5补货 6退货质检 */
    private Integer type;
    /** INBOUND/OUTBOUND/STOCKTAKE/RETURN */
    private String refType;
    private Long refId;
    private Long warehouseId;
    private Long locationId;
    private String content;
    private Long assigneeId;
    private String assigneeName;
    /** 0待分配 1待执行 2进行中 3已完成 4已取消 */
    private Integer status;
    private LocalDateTime finishTime;
    private String remark;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
