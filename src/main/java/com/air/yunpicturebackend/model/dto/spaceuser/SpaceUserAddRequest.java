package com.air.yunpicturebackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * @author WyH524
 * @since 2025/9/19 16:08
 * 创建空间成员请求
 */
@Data
public class SpaceUserAddRequest implements Serializable {
    /**
     * 空间 ID ，将成员加入哪个空间
     */
    private Long spaceId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     * 默认情况下，新添加的成员是这个空间的浏览者，邀请进来再去设置权限也是可以的
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}

