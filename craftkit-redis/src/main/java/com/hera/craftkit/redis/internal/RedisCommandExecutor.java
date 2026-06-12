package com.hera.craftkit.redis.internal;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

interface RedisCommandExecutor {

    CompletableFuture<Boolean> setIfAbsent(String key, String value, Duration ttl);

    CompletableFuture<Long> evalLong(String script, String[] keys, String... values);
}
