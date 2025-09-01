package com.air.yunpicturebackend.service;

import com.air.yunpicturebackend.model.dto.user.UserQueryRequest;
import com.air.yunpicturebackend.model.entity.User;
import com.air.yunpicturebackend.model.vo.LoginUserVO;
import com.air.yunpicturebackend.model.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 30280
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-08-28 13:52:25
*/
public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 加密算法
     */
    String getEncryptPassword(String userPassword);


    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 获取脱敏后的用户信息
     *
     * @return
     */
    UserVO getUserVO(User user);


    /**
     * 获取脱敏后的用户信息列表
     *
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);


    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);


    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 根据查询条件，构建QueryWrapper
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

}
