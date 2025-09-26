package com.air.yunpicturebackend.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.manager.auth.model.SpaceUserAuthConfig;
import com.air.yunpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.air.yunpicturebackend.manager.auth.model.SpaceUserRole;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.SpaceUser;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.SpaceRoleEnum;
import com.air.yunpicturebackend.model.enums.SpaceTypeEnum;
import com.air.yunpicturebackend.service.SpaceUserService;
import com.air.yunpicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 空间成员权限管理
 * 写一个类来读取配置到实体类中
 */
@Component
public class SpaceUserAuthManager {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    /**
     * 静态变量，读取权限配置，接收 json 配置文件的变量，把配置文件中的配置加载到内存中
     */
    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    /**
     * 在静态代码块中，当项目启动的时候，当类加载的时候，就把它读取到这里来
     * 把配置文件加载到内存中，
     */
    static {
        // 1.读取配置文件得到 json 字符串
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        // 2.把这个json转换成我们需要的类
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    /**
     * 根据角色获取权限列表
     * 我们的 空间用户关联表中给用户只定义了角色，所以如果我们想要知道这个用户在空间中有什么权限
     * 就要根据角色去查了，所以这里定义一个方法
     */
    public List<String> getPermissionsByRole(String spaceUserRole) {
        // 1.判空
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        // 2.从这么多的角色中找到对应的角色
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles() //得到角色列表
                .stream()
                .filter(r -> spaceUserRole.equals(r.getKey()))  //过滤
                .findFirst()
                .orElse(null); //如果没找到就返回 null
        if (role == null) {
            return new ArrayList<>();
        }
        return role.getPermissions();
    }

    /**
     * 获取权限列表
     * 根据空间对象获取权限列表，这个方法是在代码中可以调用的，所以我们只需要有空间对象就可以了
     * 不用从请求参数中拿信息了
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        // 1.登录用户为 null 肯定没权限
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 2.定义一个管理员权限，也就是全部权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 3.如果是公共图库
        if (space == null) {
            // 3.1.如果是管理员就有全部权限，因为只有管理员才有权限，否则就返回一个只读权限
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
        }
        // 4.判断空间类别是什么
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 4.1.根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }
}

