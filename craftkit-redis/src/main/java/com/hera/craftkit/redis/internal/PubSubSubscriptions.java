package com.hera.craftkit.redis.internal;

import com.hera.craftkit.redis.RedisMessage;
import com.hera.craftkit.redis.RedisMessageHandler;
import com.hera.craftkit.redis.RedisSubscription;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

final class PubSubSubscriptions {

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultRedisSubscription>> channelSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultRedisSubscription>> patternSubscriptions = new ConcurrentHashMap<>();

    RedisSubscription addChannel(final String channel, final RedisMessageHandler handler, final Runnable unsubscribe) {
        return this.add(this.channelSubscriptions, channel, false, handler, unsubscribe);
    }

    RedisSubscription addPattern(final String pattern, final RedisMessageHandler handler, final Runnable unsubscribe) {
        return this.add(this.patternSubscriptions, pattern, true, handler, unsubscribe);
    }

    void dispatchChannel(final String channel, final String payload) {
        this.dispatch(this.channelSubscriptions.get(channel), new RedisMessage(channel, null, payload));
    }

    void dispatchPattern(final String pattern, final String channel, final String payload) {
        this.dispatch(this.patternSubscriptions.get(pattern), new RedisMessage(channel, pattern, payload));
    }

    void closeAll() {
        this.channelSubscriptions.values().stream().flatMap(Set::stream).toList().forEach(DefaultRedisSubscription::close);
        this.patternSubscriptions.values().stream().flatMap(Set::stream).toList().forEach(DefaultRedisSubscription::close);
    }

    int channelSubscriptionCount(final String channel) {
        final CopyOnWriteArraySet<DefaultRedisSubscription> subscriptions = this.channelSubscriptions.get(channel);
        return subscriptions == null ? 0 : subscriptions.size();
    }

    int patternSubscriptionCount(final String pattern) {
        final CopyOnWriteArraySet<DefaultRedisSubscription> subscriptions = this.patternSubscriptions.get(pattern);
        return subscriptions == null ? 0 : subscriptions.size();
    }

    private RedisSubscription add(
        final ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultRedisSubscription>> subscriptions,
        final String topic,
        final boolean pattern,
        final RedisMessageHandler handler,
        final Runnable unsubscribe
    ) {
        final DefaultRedisSubscription subscription = new DefaultRedisSubscription(subscriptions, topic, pattern, handler, unsubscribe);
        subscriptions.computeIfAbsent(topic, ignored -> new CopyOnWriteArraySet<>()).add(subscription);
        return subscription;
    }

    private void dispatch(final Set<DefaultRedisSubscription> subscriptions, final RedisMessage message) {
        if (subscriptions == null) {
            return;
        }
        for (final DefaultRedisSubscription subscription : subscriptions) {
            if (subscription.isClosed()) {
                continue;
            }
            try {
                subscription.handler.handle(message);
            } catch (final RuntimeException ignored) {
                // User handlers must not break the shared Lettuce pub/sub listener.
            }
        }
    }

    private static final class DefaultRedisSubscription implements RedisSubscription {

        private final ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultRedisSubscription>> subscriptions;
        private final String topic;
        private final boolean pattern;
        private final RedisMessageHandler handler;
        private final Runnable unsubscribe;
        private final AtomicBoolean closed = new AtomicBoolean();

        private DefaultRedisSubscription(
            final ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultRedisSubscription>> subscriptions,
            final String topic,
            final boolean pattern,
            final RedisMessageHandler handler,
            final Runnable unsubscribe
        ) {
            this.subscriptions = subscriptions;
            this.topic = topic;
            this.pattern = pattern;
            this.handler = handler;
            this.unsubscribe = unsubscribe;
        }

        @Override
        public boolean isClosed() {
            return this.closed.get();
        }

        @Override
        public void close() {
            if (!this.closed.compareAndSet(false, true)) {
                return;
            }
            final CopyOnWriteArraySet<DefaultRedisSubscription> handlers = this.subscriptions.get(this.topic);
            if (handlers == null) {
                return;
            }
            handlers.remove(this);
            if (handlers.isEmpty() && this.subscriptions.remove(this.topic, handlers)) {
                this.unsubscribe.run();
            }
        }

        @Override
        public String toString() {
            return "RedisSubscription[topic=" + this.topic + ", pattern=" + this.pattern + ", closed=" + this.closed.get() + ']';
        }
    }
}
