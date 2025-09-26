package com.air.yunpicturebackend.manager.auth;

import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.SpaceUser;
import lombok.Data;

/**
 * SpaceUserAuthContext
 * 表示用户在特定空间内的授权上下文，包括关联的图片、空间和用户信息。
 * 所有请求参数中，跟权限有关的参数，我们全部写到这个上下文类中
 * 还可以封装一些对象，以便于我们传递参数，比如说图片信息，空间信息，空间用户信息
 * 万一能拿到呢？直接从这个上下文类里拿到就行了
 * 它的作用就是统一的存储我们要从请求参数中拿到的值
 */
@Data
public class SpaceUserAuthContext {

    /**
     * 临时参数，不同请求对应的 id 含义可能不同，只是一个占坑位用的
     */
    private Long id;

    /**
     * 图片 ID ，因为假如说是公共图库，没有空间 id ，只能通过图片 id 来判断它是否有权限
     * 根据图片 id 来找到图片，看这个图片的创建人是不是当前登录用户
     */
    private Long pictureId;

    /**
     * 空间 ID ，需要知道操作哪个空间，才能查到空间的用户信息
     */
    private Long spaceId;

    /**
     * 空间用户 ID ，假如说你在去给这个空间成员管理添加权限的时候，可以直接拿到空间用户id的
     * 可以直接从空间用户表中直接取到角色了，这是最方便的字段
     */
    private Long spaceUserId;

    /**
     * 图片信息
     */
    private Picture picture;

    /**
     * 空间信息
     */
    private Space space;

    /**
     * 空间用户信息
     */
    private SpaceUser spaceUser;
}
