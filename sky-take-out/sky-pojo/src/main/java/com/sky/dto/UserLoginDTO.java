package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
public class UserLoginDTO implements Serializable {

    private String code;

    //昵称
    private String name;

    //头像
    private String avatar;

    //性别 0 未知 1 男 2 女
    private String sex;

}
