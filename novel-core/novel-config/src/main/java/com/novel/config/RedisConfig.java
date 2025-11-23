package com.novel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public class RedisConfig {

    /**
     * 没有指定序列化方式，需要使用的时候手动序列化和反序列化
     * @param redisConnectionFactory 应用与 Redis 数据库之间的实际通信通道
     * @return edisTemplate实例
     */
    @Bean(name = "stringRedisTemplate")
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {

        // 创建RedisTemplate实例
        StringRedisTemplate template = new StringRedisTemplate(redisConnectionFactory);


        // 配置key的序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer); // 适用于 Hash 结构的 Key

        // 配置 Value 的序列化器
        // 同样使用 StringRedisSerializer。
        // 这样，当存入一个对象时，Spring 会尝试使用其 toString() 方法将其转为字符串。
        // 存入对象时，需要确保对象能被合理地转换为字符串（如先手动转为 JSON 字符串）
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer); // 适用于 Hash 结构的 Value

        template.afterPropertiesSet();
        return template;

    }
}
