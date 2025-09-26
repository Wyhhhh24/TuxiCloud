package com.air.yunpicturebackend.model.dto.space;

import com.air.yunpicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询空间请求
 * 我们可以按照哪些字段来搜索空间
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceQueryRequest extends PageRequest implements Serializable {
    //首先继承我们通用的分页请求类

    /**
     * id
     */
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 管理员可以按照空间类型进行查询
     * 用户也可能要查自己加了哪些团队空间，或者自己的私有空间
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    private static final long serialVersionUID = 1L;
}
