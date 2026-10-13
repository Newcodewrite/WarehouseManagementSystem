package com.lanxi.wms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TrackForm {

    private String location;

    @NotBlank(message = "轨迹内容不能为空")
    private String content;
}
