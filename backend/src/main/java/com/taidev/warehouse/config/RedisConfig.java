package com.taidev.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Loads each Lua script from {@code classpath:lua/*.lua} into a Spring
 * {@link RedisScript} bean. Spring Data Redis caches the SHA1 on first
 * use, so subsequent calls go through {@code EVALSHA}.
 *
 * Scripts return a plain integer status:
 *   {@code  1} — success
 *   {@code -1} — insufficient stock (reserve)
 *   {@code -2} — over-release / over-consume
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<Long> reserveScript() {
        return script("lua/reserve.lua");
    }

    @Bean
    public RedisScript<Long> releaseScript() {
        return script("lua/release.lua");
    }

    @Bean
    public RedisScript<Long> consumeScript() {
        return script("lua/consume.lua");
    }

    @Bean
    public RedisScript<Long> initSkuScript() {
        return script("lua/init_sku.lua");
    }

    private static RedisScript<Long> script(String classpathLocation) {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource(classpathLocation)));
        s.setResultType(Long.class);
        return s;
    }
}
