package com.hera.craftkit.redis;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RedisConfigTest {

    @Test
    void buildUsesSafeDefaults() {
        final RedisConfig config = RedisConfig.builder().build();

        assertEquals("localhost", config.host());
        assertEquals(6379, config.port());
        assertEquals(0, config.database());
        assertEquals("", config.username());
        assertEquals("", config.password());
        assertFalse(config.ssl());
        assertTrue(config.verifyPeer());
        assertEquals(Duration.ofSeconds(3), config.commandTimeout());
        assertEquals(Duration.ofSeconds(3), config.connectTimeout());
        assertEquals(Duration.ofSeconds(10), config.shutdownTimeout());
        assertTrue(config.autoReconnect());
        assertTrue(config.pingBeforeActivate());
        assertEquals(10_000, config.requestQueueSize());
        assertEquals(Duration.ofMillis(100), config.reconnectMinDelay());
        assertEquals(Duration.ofSeconds(10), config.reconnectMaxDelay());
        assertEquals(2, config.ioThreads());
        assertEquals(2, config.computationThreads());
        assertEquals("hera", config.keyPrefix());
        assertEquals("default", config.environment());
        assertEquals("unknown", config.serverId());
    }

    @Test
    void buildRejectsInvalidCoreValues() {
        assertThrows(RedisException.class, () -> RedisConfig.builder().host(" ").build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().port(0).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().port(65_536).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().database(-1).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().requestQueueSize(0).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().requestQueueSize(RedisConfig.MAX_REQUEST_QUEUE_SIZE + 1).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().ioThreads(0).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().computationThreads(0).build());
    }

    @Test
    void buildRejectsInvalidDurations() {
        assertThrows(RedisException.class, () -> RedisConfig.builder().commandTimeout(Duration.ZERO).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().connectTimeout(Duration.ZERO).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().shutdownTimeout(Duration.ZERO).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().reconnectMinDelay(Duration.ZERO).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().reconnectMaxDelay(Duration.ZERO).build());
        assertThrows(RedisException.class, () -> RedisConfig.builder()
            .reconnectMinDelay(Duration.ofSeconds(10))
            .reconnectMaxDelay(Duration.ofMillis(100))
            .build());
    }

    @Test
    void buildRejectsInvalidIdentityComponents() {
        assertThrows(RedisException.class, () -> RedisConfig.builder().keyPrefix("bad prefix").build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().environment("*").build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().serverId(" ").build());
        assertThrows(RedisException.class, () -> RedisConfig.builder().keyPrefix("a".repeat(257)).build());
    }

    @Test
    void toStringDoesNotExposePassword() {
        final RedisConfig config = RedisConfig.builder()
            .username("default")
            .password("super-secret")
            .build();

        final String rendered = config.toString();

        assertTrue(rendered.contains("password=<hidden>"));
        assertFalse(rendered.contains("super-secret"));
    }
}
