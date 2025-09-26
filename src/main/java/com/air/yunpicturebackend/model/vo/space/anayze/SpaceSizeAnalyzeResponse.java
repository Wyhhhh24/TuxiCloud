package com.air.yunpicturebackend.model.vo.space.anayze;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 空间图片大小分析响应
 * 大小分析结果，需要返回图片大‌小范围和这个范围下的图片数量
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceSizeAnalyzeResponse implements Serializable {

    /**
     * 图片大小范围
     */
    private String sizeRange;

    /**
     * 该范围的图片数量
     */
    private Long count;

    private static final long serialVersionUID = 1L;
}
