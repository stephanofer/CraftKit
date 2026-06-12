package com.hera.craftkit.redis;

public record RedisMessage(String channel, String pattern, String payload) {
}
