package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RedisNamesTest {

    @Test
    void buildKeyUsesApprovedFormat() {
        assertEquals("hera:prod:players:uuid:profile", RedisNames.buildKey("hera", "prod", "players", "uuid", "profile"));
    }

    @Test
    void buildChannelUsesApprovedFormat() {
        assertEquals("hera:prod:events:players:joined", RedisNames.buildChannel("hera", "prod", "players", "joined"));
    }

    @Test
    void rejectsBlankSpaceAndOversizedComponents() {
        assertThrows(RedisException.class, () -> RedisNames.buildKey("hera", "prod", "bad domain"));
        assertThrows(RedisException.class, () -> RedisNames.buildChannel("hera", "prod", "players", ""));
        assertThrows(RedisException.class, () -> RedisNames.buildKey("hera", "prod", "players", "a".repeat(257)));
    }

    @Test
    void patternAllowsWildcardsButNotSpaces() {
        assertEquals("hera:prod:events:*", RedisNames.validatePattern("hera:prod:events:*"));

        assertThrows(RedisException.class, () -> RedisNames.validatePattern("hera prod *"));
    }
}
