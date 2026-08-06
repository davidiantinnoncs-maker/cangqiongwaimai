package com.sky.utils;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.exception.LoginFailedException;
import com.sky.properties.WeChatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序登录工具类
 */
@Component
@Slf4j
public class WeChatUtil {

    public static final String WX_LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Autowired
    private WeChatProperties weChatProperties;

    /**
     * 使用 wx.login() 获取的临时登录凭证 code 换取 openid
     *
     * @param code 临时登录凭证
     * @return 微信用户唯一标识 openid
     */
    public String getOpenid(String code) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", weChatProperties.getAppid());
        paramMap.put("secret", weChatProperties.getSecret());
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");

        String json = HttpClientUtil.doGet(WX_LOGIN_URL, paramMap);
        log.info("微信登录接口响应：{}", json);

        if (json == null || json.trim().isEmpty()) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        JSONObject jsonObject = JSONObject.parseObject(json);
        String openid = jsonObject.getString("openid");
        if (openid == null) {
            String errmsg = jsonObject.getString("errmsg");
            throw new LoginFailedException(errmsg == null ? MessageConstant.LOGIN_FAILED : errmsg);
        }
        return openid;
    }
}
