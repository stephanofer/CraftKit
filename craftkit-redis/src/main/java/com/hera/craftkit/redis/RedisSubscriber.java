package com.hera.craftkit.redis;

public interface RedisSubscriber {

    RedisSubscription subscribe(String channel, RedisMessageHandler handler);

    RedisSubscription subscribePattern(String pattern, RedisMessageHandler handler);
}
