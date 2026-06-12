package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PubSubSubscriptionsTest {

    @Test
    void handlerExceptionDoesNotBreakOtherHandlers() {
        final PubSubSubscriptions subscriptions = new PubSubSubscriptions();
        final List<RedisMessage> received = new ArrayList<>();

        subscriptions.addChannel("hera:prod:events:test:boom", message -> { throw new IllegalStateException("boom"); }, () -> { });
        subscriptions.addChannel("hera:prod:events:test:boom", received::add, () -> { });

        subscriptions.dispatchChannel("hera:prod:events:test:boom", "payload");

        assertEquals(1, received.size());
        assertEquals("payload", received.getFirst().payload());
    }

    @Test
    void closeIsIdempotentAndUnsubscribesOnlyWhenLastHandlerCloses() {
        final PubSubSubscriptions subscriptions = new PubSubSubscriptions();
        final AtomicInteger unsubscribes = new AtomicInteger();

        final var first = subscriptions.addChannel("hera:prod:events:test:close", message -> { }, unsubscribes::incrementAndGet);
        final var second = subscriptions.addChannel("hera:prod:events:test:close", message -> { }, unsubscribes::incrementAndGet);

        first.close();
        first.close();

        assertTrue(first.isClosed());
        assertFalse(second.isClosed());
        assertEquals(0, unsubscribes.get());
        assertEquals(1, subscriptions.channelSubscriptionCount("hera:prod:events:test:close"));

        second.close();
        second.close();

        assertTrue(second.isClosed());
        assertEquals(1, unsubscribes.get());
        assertEquals(0, subscriptions.channelSubscriptionCount("hera:prod:events:test:close"));
    }

    @Test
    void closeAllClosesChannelsAndPatternsWithoutDuplicateUnsubscribe() {
        final PubSubSubscriptions subscriptions = new PubSubSubscriptions();
        final AtomicInteger channelUnsubscribes = new AtomicInteger();
        final AtomicInteger patternUnsubscribes = new AtomicInteger();

        subscriptions.addChannel("hera:prod:events:test:close-all", message -> { }, channelUnsubscribes::incrementAndGet);
        subscriptions.addPattern("hera:prod:events:test:*", message -> { }, patternUnsubscribes::incrementAndGet);

        subscriptions.closeAll();
        subscriptions.closeAll();

        assertEquals(1, channelUnsubscribes.get());
        assertEquals(1, patternUnsubscribes.get());
        assertEquals(0, subscriptions.channelSubscriptionCount("hera:prod:events:test:close-all"));
        assertEquals(0, subscriptions.patternSubscriptionCount("hera:prod:events:test:*"));
    }
}
