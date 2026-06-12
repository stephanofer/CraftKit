package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisException;

import java.util.regex.Pattern;

public final class RedisNames {

    private static final int MAX_LENGTH = 256;
    private static final Pattern COMPONENT = Pattern.compile("[a-zA-Z0-9._:-]+");

    private RedisNames() {
    }

    public static String validateKey(final String key) {
        return validate(key, "Redis key");
    }

    public static String validateValue(final String value, final String name) {
        if (value == null) {
            throw new RedisException(name + " must not be null.");
        }
        return value;
    }

    public static String buildKey(final String keyPrefix, final String environment, final String domain, final String... parts) {
        final StringBuilder builder = new StringBuilder()
            .append(validate(keyPrefix, "Redis key prefix"))
            .append(':')
            .append(validate(environment, "Redis environment"))
            .append(':')
            .append(validate(domain, "Redis key domain"));
        if (parts != null) {
            for (final String part : parts) {
                builder.append(':').append(validate(part, "Redis key part"));
            }
        }
        return validateLength(builder.toString(), "Redis key");
    }

    public static String buildChannel(final String keyPrefix, final String environment, final String domain, final String event) {
        return validateLength(validate(keyPrefix, "Redis key prefix")
            + ':' + validate(environment, "Redis environment")
            + ":events:"
            + validate(domain, "Redis channel domain")
            + ':' + validate(event, "Redis channel event"), "Redis channel");
    }

    public static String validateChannel(final String channel) {
        return validate(channel, "Redis channel");
    }

    public static String validatePattern(final String pattern) {
        if (pattern == null) {
            throw new RedisException("Redis channel pattern must not be null.");
        }
        final String trimmed = pattern.trim();
        if (trimmed.isEmpty()) {
            throw new RedisException("Redis channel pattern must not be blank.");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new RedisException("Redis channel pattern must not be longer than 256 characters.");
        }
        if (trimmed.indexOf(' ') >= 0) {
            throw new RedisException("Redis channel pattern must not contain spaces.");
        }
        return trimmed;
    }

    private static String validate(final String value, final String name) {
        if (value == null) {
            throw new RedisException(name + " must not be null.");
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new RedisException(name + " must not be blank.");
        }
        if (!COMPONENT.matcher(trimmed).matches()) {
            throw new RedisException(name + " may only contain letters, numbers, dots, underscores, colons, and hyphens.");
        }
        return validateLength(trimmed, name);
    }

    private static String validateLength(final String value, final String name) {
        if (value.length() > MAX_LENGTH) {
            throw new RedisException(name + " must not be longer than 256 characters.");
        }
        return value;
    }
}
