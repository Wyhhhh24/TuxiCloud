package com.air.yunpicturebackend.model.dto.picture;

/**
 * @author WyH524
 * @since 2025/9/14 下午7:16
 */

import lombok.Data;

import java.util.List;

/**
 * 图片批量编辑请求
 */
@Data
public class PictureEditByBatchRequest {

    /**
     * 图片id 列表
     * 你要更新哪些图片，传一个图片 id 列表就可以了
     */
    private List<Long> pictureIdList;


    /**
     * 空间 Id
     * 你要更新的图片所处于哪个空间
     */
    private Long spaceId;


    /**
     * 分类
     * 想将这些图片归到哪个分类下
     */
    private String category;


    /**
     * 标签
     */
    private List<String> tags;


    /**
     * 命名规则
     */
    private String nameRule;


    private static final long serialVersionUID = 1L;
}
