package com.air.yunpicturebackend.model.vo.space.anayze;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 空间图片分类分析响应
 * 分类分析的结果需要返回图片‌分类、分类图片数量和分类图片总大小
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceCategoryAnalyzeResponse implements Serializable {

    /**
     * 图片分类，分类的名称
     */
    private String category;

    /**
     * 图片数量，每个分类的图片数量
     */
    private Long count;

    /**
     * 对应分类下的图片总大小
     */
    private Long totalSize;

    private static final long serialVersionUID = 1L;
}
