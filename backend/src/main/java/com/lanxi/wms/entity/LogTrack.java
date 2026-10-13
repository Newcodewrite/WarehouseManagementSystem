package com.lanxi.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流轨迹
 */
@Data
@TableName("log_track")
public class LogTrack {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String location;
    private String content;
    private LocalDateTime trackTime;
    private LocalDateTime createTime;
}
