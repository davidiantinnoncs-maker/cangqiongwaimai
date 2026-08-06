package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;

public interface UserService {

    /**
     * 微信用户登录
     *
     * @param userLoginDTO 登录参数
     * @return 登录用户（新用户会自动注册）
     */
    User wxLogin(UserLoginDTO userLoginDTO);
}
