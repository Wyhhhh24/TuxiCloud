package com.air.yunpicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.air.yunpicturebackend.annotation.AuthCheck;
import com.air.yunpicturebackend.common.BaseResponse;
import com.air.yunpicturebackend.common.DeleteRequest;
import com.air.yunpicturebackend.common.ResultUtils;
import com.air.yunpicturebackend.constant.UserConstant;
import com.air.yunpicturebackend.exception.BusinessException;
import com.air.yunpicturebackend.exception.ErrorCode;
import com.air.yunpicturebackend.exception.ThrowUtils;
import com.air.yunpicturebackend.manager.auth.SpaceUserAuthManager;
import com.air.yunpicturebackend.model.dto.space.*;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.SpaceUser;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.SpaceLevelEnum;
import com.air.yunpicturebackend.model.enums.SpaceTypeEnum;
import com.air.yunpicturebackend.model.vo.SpaceVO;
import com.air.yunpicturebackend.service.SpaceService;
import com.air.yunpicturebackend.service.SpaceUserService;
import com.air.yunpicturebackend.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author WyH524
 * @since 2025/9/1 上午11:05
 */
@RestController
@RequestMapping("/space")
@Slf4j
public class SpaceController {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;


    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 创建空间
     * 用户可以自主创建私有空间，但是必须要加限制，最多只能创建一个
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request){
        // 1.参数校验不能为空
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 2.获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 3.创建空间
        long newSpaceId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(newSpaceId);
    }


    /**
     * 删除空间，传过来空间id，仅本人或管理员可删除空间，进行逻辑删除
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 1.判断参数
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.获取当前登录用户
        User loginUser = userService.getLoginUser(request);

        // 3.判断是所删除的空间是否存在
        long id = deleteRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");

        // 4.权限校验，仅本人或管理员可删除空间
        spaceService.checkSpaceAuth(loginUser, oldSpace);

        // 5.操作数据库，把对应的空间删除掉，逻辑删除
        // 如果是团队空间，还需要删除 SpaceUser 表中的数据
        if(oldSpace.getSpaceType() == SpaceTypeEnum.TEAM.getValue()){
            spaceUserService.remove(new LambdaQueryWrapper<SpaceUser>().eq(SpaceUser::getSpaceId, id));
        }
        boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 更新空间（仅管理员可用）
     * 管理员可以更改其它空间的级别，用户不能修改空间级别
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        // 1.参数校验
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.判断要更新的空间是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR,"该空间不存在，更新失败");

        // 3.将 DTO 和 实体类 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);

        // 4.数据校验，校验空间名称，空间级别是否正常 (这里不是创建空间，参数设置为 false )
        spaceService.validSpace(space,false);

        // 5.假如管理员要更改空间的级别，我们就应该把当前空间的大小容量进行更新，所以这里也需要进行填充限额信息
        spaceService.fillSpaceBySpaceLevel(space);

        // 6.操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"出现异常，更改空间失败");
        return ResultUtils.success(true);
    }



    /**
     * 根据 id 获取空间（仅管理员可用）
     * 管理员可以看到 Space 的全部信息，未包含用户的信息
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR,"空间 id 不存在");
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"未查询到该空间");
        // 获取封装类
        return ResultUtils.success(space);
    }


    /**
     * 获取空间详情接口
     * 根据 id 获取空间封装类（包含用户信息）
     * 这里正常来说是仅本人只可以访问自己的空间信息，需要设置权限
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR,"空间 id 不存在");
        // 1.查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"未查询到该空间");
        // 2.构建 VO 对象，里面是含有用户信息字段、权限列表字段
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
        User loginUser = userService.getLoginUser(request);
        // 3.根据用户在该空间的角色，获取权限列表
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        // 4.封装权限，因为当用户点击空间详情接口的时候，可以根据权限展示对应的按钮
        spaceVO.setPermissionList(permissionList);
        // 5.返回结果
        return ResultUtils.success(spaceVO);
    }


    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        //返回的就是 Page<Space> 对象
        return ResultUtils.success(spacePage);
    }


    /**
     * 主页展示的空间，就是请求这个接口的
     * 分页获取空间列表，给普通用户用的（封装类）
     * 未添加缓存
     * 其实这个接口应该是不应该让其他人看到自己的空间，只能看到自己的，其实看到空间也看不到里面的图片
     * 即使用户调用也没什么问题
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                             HttpServletRequest request) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫 一页显示的条数大于 20 的话，就报错
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));

        // 获取封装类，获取 Page<SpaceVO> 对象
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }


    /**
     * 编辑空间（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        // 1.先进行判空
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.判断空间是否存在，不存在就报错
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");

        // 3.在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);

        // 4.根据空间级别填充信息，但是实际用户是修改不了的
        spaceService.fillSpaceBySpaceLevel(space);

        // 5.设置编辑时间
        space.setEditTime(new Date());

        // 6.数据校验，对空间名称、空间级别进行正常校验
        spaceService.validSpace(space,false);

        // 7.仅本人或管理员可编辑空间
        User loginUser = userService.getLoginUser(request);
        spaceService.checkSpaceAuth(loginUser, oldSpace);

        // 8.操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"出现异常，编辑空间失败");
        return ResultUtils.success(true);
    }


    /**
     * 给前端展示所有的‌空间级别信息
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }
}
