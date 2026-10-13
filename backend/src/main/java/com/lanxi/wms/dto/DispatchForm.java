package com.lanxi.wms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DispatchForm {

    @NotNull(message = "车辆不能为空")
    private Long vehicleId;
}
