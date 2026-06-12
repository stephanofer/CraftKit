package com.hera.craftkit.redis;

import com.hera.craftkit.redis.internal.LettuceRedisClient;

import java.util.Objects;

public final class RedisClients {

    private RedisClients() {
    }

    public static RedisClient lettuce(final RedisConfig config) {
        return LettuceRedisClient.create(Objects.requireNonNull(config, "Redis config must not be null."));
    }
}
