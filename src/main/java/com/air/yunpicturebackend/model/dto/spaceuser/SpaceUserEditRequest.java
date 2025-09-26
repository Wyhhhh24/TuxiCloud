package com.air.yunpicturebackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑空间成员请求
 */
@Data
public class SpaceUserEditRequest implements Serializable {

    /**
     * id ，修改哪一条记录
     */
    private Long id;

    /**
     * 空间角色：viewer/editor/admin
     * 只能修改用户的空间角色，只能把用户从查看者变成管理员或者编辑者，但是不能改其它的信息了
     * 不能把一个用户从一个空间直接改到另一个空间里
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
