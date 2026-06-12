package com.hera.craftkit.redis;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface RedisLease {

    String key();

    String token();

    Duration ttl();

    CompletableFuture<Boolean> release();
}
