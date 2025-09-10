package com.air.yunpicturebackend.service;

import com.air.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import com.air.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.air.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.air.yunpicturebackend.model.entity.Picture;
import com.air.yunpicturebackend.model.entity.Space;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.enums.SpaceLevelEnum;
import com.air.yunpicturebackend.model.vo.PictureVO;
import com.air.yunpicturebackend.model.vo.SpaceVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;

/**
* @author 30280
* @description 针对表【space(空间)】的数据库操作 Service
* @createDate 2025-09-05 15:26:40
*/
public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);


    /**
     * 校验空间数据的方法，新建空间和修改空间信息的校验逻辑是不同的
     * 通过 boolean add 参数进行区分是新建空间还是校验空间
     */
    void validSpace(Space space,boolean add);


    /**
     * 根据空间级别,自动填充限额，补充信息
     */
    void fillSpaceBySpaceLevel(Space space);


    /**
     * 获取查询条件
     * 通过 SpaceQueryRequest ，构造 QueryWrapper 条件
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);



    /**
     * 获取 SpaceVO 封装
     * 编写获؜取 SpaceVO 的方法，可以为原有的 Space 关‌联所属用户的信息
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);


    /**
     * 获取 SpaceVO 分页封装
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);
}
