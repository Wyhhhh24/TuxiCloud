package com.air.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.util.List;

/**
 * 图片标签和分类的封装类
 */
@Data
public class PictureTagCategory {
    // 标签列表（如：热门、搞笑、生活...）
    private List<String> tagList;
    
    // 分类列表（如：模板、电商、表情包...）
    private List<String> categoryList;
}