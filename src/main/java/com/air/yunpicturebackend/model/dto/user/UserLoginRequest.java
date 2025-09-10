package com.air.yunpicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1141764935855484815L;

    /**
     * 账号
     */
    private String userAccount;


    /**
     * 密码
     */
    private String userPassword;
}
