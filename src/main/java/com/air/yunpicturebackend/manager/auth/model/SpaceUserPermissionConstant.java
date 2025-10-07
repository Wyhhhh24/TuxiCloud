package com.air.yunpicturebackend.manager.auth.model;

/**
 * 空间成员权限常量
 * 和我们写枚举类一样的
 * 就是把刚刚那几个权限用了一个变量来定义
 * 这样打的时候只需要打常量而不是字符串，否则容易出错
 * 角色已经有枚举了
 */
public interface SpaceUserPermissionConstant {
    /**
     * 空间用户管理权限
     */
    String SPACE_USER_MANAGE = "spaceUser:manage";

    /**
     * 图片查看权限
     */
    String PICTURE_VIEW = "picture:view";

    /**
     * 图片上传权限
     */
    String PICTURE_UPLOAD = "picture:upload";

    /**
     * 图片编辑权限
     */
    String PICTURE_EDIT = "picture:edit";

    /**
     * 图片删除权限
     */
    String PICTURE_DELETE = "picture:delete";
}
