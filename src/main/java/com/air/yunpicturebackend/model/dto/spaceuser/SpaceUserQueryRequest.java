package com.air.yunpicturebackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间用户查询请求
 * 这里就不继承分页类了，暂时假设它不会特别多
 * 为了提高开发效率，我们空间内的成员就直接获取所有的成员，不用分页了
 */
@Data
public class SpaceUserQueryRequest implements Serializable {
    /**
     * 空间用户关联表主键 ID
     */
    private Long id;

    /**
     * 空间 ID
     */
    private Long spaceId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     * 给管理员，给空间管理员去管理成员使用的
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
