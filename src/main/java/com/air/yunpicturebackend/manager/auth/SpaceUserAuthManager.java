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
     * 静态变量，读取权限配置，接收 json 权限配置文件的变量，把配置文件中的配置加载到内存中
     */
    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    /**
     * 在静态代码块中，当项目启动的时候，类加载的时候，就把配置文件中的数据映射到 SpaceUserAuthConfig 这个对象中
     */
    static {
        // 1.读取配置文件得到 json 字符串
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        // 2.把这个json转换成我们需要的类
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }


    /**
     * 根据传过来的 角色 获取该角色对应的权限列表
     * 我们的 空间用户关联表 中给用户只定义了角色，所以如果我们想要知道这个用户在空间中有什么权限
     * 就要根据该用户的角色去查来获取权限了
     */
    public List<String> getPermissionsByRole(String spaceUserRole) {
        // 1.判空
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        // 2.从角色列表中找到对应的角色
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles() // 获得配置文件中定义的角色列表
                .stream()
                .filter(r -> spaceUserRole.equals(r.getKey()))  // Key 进行匹配，获得对应的角色
                .findFirst()
                .orElse(null); // 若无该角色就返回 null
        if (role == null) {
            return new ArrayList<>();
        }
        // 3.返回角色对应的权限列表
        return role.getPermissions();
    }


    /**
     * 通过传过来的空间对象，用户
     * 获取该用户在这个空间中的权限
     * 这个方法是便于封装了，传过来一个 Space 以及 User ，可以快速获取该用户在这个空间中的权限
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        // 1.登录用户为 null 肯定没权限
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 2.定义一个管理员所具有的所有权限列表
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 3.如果是公共图库
        if (space == null) {
            // 3.1.如果当前用户是管理员，他就有全部权限，因为对于公共图库只有管理员才有权限
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            // 3.2.否则，该用户就只有 只读权限
            return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
        }
        // 4.判断空间所属类别
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            // 若既不是团队也不是私有空间，其实是错误的，返回空权限列表
            return new ArrayList<>();
        }
        // 4.1.根据空间类别获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser ，获得该用户在团队空间中的角色
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
        // 5.没有权限
        return new ArrayList<>();
    }
}

