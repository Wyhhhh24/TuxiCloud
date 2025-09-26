package com.air.yunpicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.SpaceUser;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.SpaceRoleEnum;
import com.air.yunpicturebackend.model.enums.SpaceTypeEnum;
import com.air.yunpicturebackend.service.PictureService;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.service.SpaceUserService;
import com.air.yunpicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.air.yunpicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 *
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 我们需要通过请求路径来判断应该给上下文传递什么参数
     * 我们要获取当前请求的上下文路径，这样才能从 url 中获取到指定的前缀  /api
     */
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    /**
     * 返回一个账号所拥有的权限码集合
     * 我们要在这个方法中编写根据用户id，以及我们获取到的一系列请求参数进行鉴权逻辑
     * 只要打上了注解，就强制校验用户必须登录
     * 只要给接口打了注解，这个接口就变成了用户登录之后才能访问
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 1.判断 loginType，仅对类型为 "space" 进行权限校验，我们项目现在有多套权限校验体系，我们只对空间账号体系进行校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }

        // 2.管理员所拥有的权限列表，权限校验通过的话，该用户就具有这些权限
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());

        // 3.获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();

        // 4.如果所有字段都为空，表示查询公共图库，可以通过
        // 需要检查上下文字段是否为空，如果上下文所有字段都为空，就表示权限中不需要编辑操作，可能就是浏览公共图库
        // 这个时候就直接返回管理员的所有权限列表
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS;
        }

        // 如果所有字段不为空，表示要进行权限校验
        // 5.获取 userId ，从 sa-token 的 session 中获取用户信息
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        // 5.1.获取到当前登录用户的信息
        Long userId = loginUser.getId();

        // 6.优先从上下文中获取 SpaceUser 对象，根据 SpaceRole 获取权限列表
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }

        // 7.如果上下文中有 spaceUserId，必然是团队空间，通过数据库查询 SpaceUser 对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 7.1.取出当前登录用户对应的 spaceUser
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                // 啥权限都没有
                return new ArrayList<>();
            }
            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理，根据角色拿到权限列表
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }

        // 8. 如果没有 spaceUserId ，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();
        // 8.1.如果没有 spaceId ，那可能就是公共图库了，或者说传信息的时候没有传 spaceId ，只能通过 pictureId 获取 Picture 对象和 Space 对象
        if (spaceId == null) {
            Long pictureId = authContext.getPictureId();
            // 8.1.1.pictureId 也没有，那就表示不需要校验，就默认返回所有的管理员权限
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            // 8.1.2.pictureId 有，则通过 pictureId 获取 Picture
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            // 8.1.3.如果 Picture 对象为空，则表示没有该图片，就返回错误
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            // 8.1.4.如果 Picture 存在，则通过 Picture 对象获取 SpaceId
            spaceId = picture.getSpaceId();
            // 8.1.5.公共图库，仅本人或管理员可操作
            if (spaceId == null) {
                // 如果是 管理员 或者是 本人图片 ，那么就返回所有权限，crud
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }

        // 8.2.通过 SpaceId 获取对应的 Space 对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                // 否则就啥权限没有，公共图库还有查看的权限，私有空间就啥权限没有
                return new ArrayList<>();
            }
        } else {
            // 团队空间，根据 spaceId 和 userId 查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                // 啥权限都没有
                return new ArrayList<>();
            }
            // 根据 spaceUser 的角色获取权限
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }


    /**
     * 判断所有字段都为空，‌通过反射动态的获取对象的所有字段，进行判空‍
     * 需要检查上下文字段是否为空，如果上下文所有字段都为空，就表示权限中不需要编辑操作，可能就是浏览公共图库
     * 这个时候就直接返回管理员的所有权限列表
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass())) //首先用反射，获取到这个对象所有的字段信息
                // 获取字段值，遍历字段的值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty); //全为空的话，就返回 true
    }



    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     * 我们基于角色校验，所以这里返回空集合，本项目中不使用
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    /**
     * 从请求中获取上下文对象
     * 通过请求对象获取到上下文
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // 1.首先获取到请求对象，转换成 Servlet 请求
        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = servletRequestAttributes.getRequest();
        // 2.从 request 对象中获取到当前执行的请求类别，我们的接口是有不同的请求类别的，有get、post，它们获取参数的方式是不一样的
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 3.1.获取请求参数
        // 3.2.如果我们的请求参数是Json
        if(ContentType.JSON.getValue().equals(contentType)){
            // 3.2.1.如果是 Json ，通过这个工具类直接根据请求对象拿到所有的请求体
            // todo 这里是需要注意的，body只可以读取一次，所以需要配置一下，在配置类中
            String body = ServletUtil.getBody(request); //如果是json的话，这个就是一个json
            //我们就可以直接把json字符串转换成我们需要的上下文对象 SpaceUserAuthContext
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        }else{
            //3.3.如果是 get 请求，get请求是没有 json 对象的，获取所有请求参数的一个 Map 集合
            Map<String, String> paramMap = ServletUtil.getParamMap(request);  // Key 和 Value 都是 String 的 Map
            //将 Map 也转换成我们需要的上下文对象
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 4.根据请求路径区分 id 字段的含义
        // 4.1.传的参数有 id 才进行区分
        Long id = authRequest.getId();
        if(ObjUtil.isNotNull(id)){
            // 4.2.获取到请求路径的业务前缀，先获取到完整的请求路径  /api/picture/aaa?a=1
            String requestURI = request.getRequestURI();
            // 4.2.1.将 /api/ 去掉，剩下的就是前缀了 picture/aaa?a=1
            String partURI = requestURI.replace(contextPath + "/", "");
            // 4.2.2.第一个斜杠前面的就是我们要的业务前缀
            String moduleName = StrUtil.subBefore(partURI, "/", false);
            switch (moduleName) {
                case "picture":
                    // 如果是 picture ，那么 id 就是 pictureId
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    // 如果是 spaceUser ，那么 id 就是 spaceUserId
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    // 如果是 space ，那么 id 就是 spaceId
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        // 5.获得的上下文参数传递出去
        return authRequest;
    }
}

