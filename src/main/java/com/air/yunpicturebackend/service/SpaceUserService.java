package com.air.yunpicturebackend.service;

import com.air.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.air.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.air.yunpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.air.yunpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.SpaceUser;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.vo.SpaceUserVO;
import com.air.yunpicturebackend.model.vo.SpaceVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 30280
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2025-09-19 16:00:31
*/
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 添加成员到空间
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);


    /**
     * 参数校验
     */
    void validSpaceUser(SpaceUser spaceUser,boolean add);


    /**
     * 获取查询条件
     * 通过 SpaceQueryRequest ，构造 QueryWrapper 条件
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);


    /**
     * 获取空间成员包装类（单条）
     * @param spaceUser
     * @param request
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);


    /**
     * 获取空间成员包装类分页（列表，这里没有分页）
     * @param spaceUserList
     * @return
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
