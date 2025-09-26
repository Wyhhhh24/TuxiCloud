package com.air.yunpicturebackend.model.dto.space.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员对空间使用排行分析
 * 需要接收一个参数 topN，指定要返回的前 N 名空间信息，默认值为 10
 */
@Data
public class SpaceRankAnalyzeRequest implements Serializable {

    /**
     * 排名前 N 的空间，默认排名前 10 的空间
     */
    private Integer topN = 10;

    private static final long serialVersionUID = 1L;
}
