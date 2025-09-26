package com.air.yunpicturebackend.model.dto.space.analyze;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 空间资源使用情况分析请求封装类
 * 空间资源使用分析，能够分析出某个空间、全空间、公共图库的总的图片数量以及总的图片容量
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceUsageAnalyzeRequest extends SpaceAnalyzeRequest {
}
