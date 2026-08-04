package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    //配置 RedisTemplate，就是把默认的“JDK 专用加密通道”改造成“通用的 UTF-8+JSON 标准通道”，
    // 让 Java 程序存得优雅、查得方便、取的安全，并且对 Redis 运维和跨语言调用更友好。
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate redisTemplate = new RedisTemplate();
        //连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //设置redis key的序列化器
        //为了避免redis缓存中存在乱码或二进制不可读的情况，保证缓存的可维护性和系统的稳定性
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        return redisTemplate;
    }

}
