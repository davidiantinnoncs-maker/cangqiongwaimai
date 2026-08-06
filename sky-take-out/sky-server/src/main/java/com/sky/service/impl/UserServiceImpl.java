package com.sky.service.impl;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.mapper.UserMapper;
import com.sky.service.UserService;
import com.sky.utils.WeChatUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private WeChatUtil weChatUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        // 用 code 向微信服务器换取 openid
        String openid = weChatUtil.getOpenid(userLoginDTO.getCode());

        // 根据 openid 查询用户，不存在则自动注册
        User user = userMapper.getByOpenid(openid);
        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .name(userLoginDTO.getName())
                    .avatar(userLoginDTO.getAvatar())
                    .sex(userLoginDTO.getSex())
                    .createTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
        } else {
            // 老用户登录时更新用户主动填写的资料
            boolean needUpdate = false;
            if (StringUtils.hasText(userLoginDTO.getName())) {
                user.setName(userLoginDTO.getName());
                needUpdate = true;
            }
            if (StringUtils.hasText(userLoginDTO.getAvatar())) {
                user.setAvatar(userLoginDTO.getAvatar());
                needUpdate = true;
            }
            if (StringUtils.hasText(userLoginDTO.getSex())) {
                user.setSex(userLoginDTO.getSex());
                needUpdate = true;
            }
            if (needUpdate) {
                userMapper.update(user);
            }
        }
        log.info("微信用户登录，openid：{}", openid);
        return user;
    }
}
