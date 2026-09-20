package com.jzo2o.foundations.model.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 * @date 2024/11/2 15:48
 */
@Data
@ApiModel("服务新增更新")
public class ServeUpsertReqDTO {

 /**
  * 服务id
  */
 @ApiModelProperty(value = "服务项目id", required = true)
 private Long serveItemId;

 /**
  * 区域id
  */
 @ApiModelProperty(value = "区域id", required = true)
 private Long regionId;


}
