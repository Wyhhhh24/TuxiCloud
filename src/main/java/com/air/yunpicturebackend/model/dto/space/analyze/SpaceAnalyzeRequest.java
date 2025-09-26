package com.air.yunpicturebackend.model.dto.space.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用空间分析请求类
 * 由于我؜们的很多分析需求都需要传递空间查询范‌围，所以一个公共的图片分析请求封‍装类
 */
@Data
public class SpaceAnalyzeRequest implements Serializable {

    /**
     * 空间 ID
     */
    private Long spaceId;

    /**
     * 是否查询公共图库
     */
    private boolean queryPublic;

    /**
     * 是否分析全空间
     */
    private boolean queryAll;

    private static final long serialVersionUID = 1L;
}
