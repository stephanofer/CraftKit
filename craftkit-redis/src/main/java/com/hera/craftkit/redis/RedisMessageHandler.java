package com.hera.craftkit.redis;

@FunctionalInterface
public interface RedisMessageHandler {

    void handle(RedisMessage message);
}
