package com.itnoduck.acmate.common.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Small JSON cache for public read models. Redis failures fall through to the database. */
@Component
public class RedisJsonCache {

    private static final Duration CACHE_TTL = Duration.ofSeconds(15);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); else return 0; end",
            Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisJsonCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        try {
            var connection = redis.getConnectionFactory().getConnection();
            try { return "PONG".equalsIgnoreCase(connection.ping()); }
            finally { connection.close(); }
        } catch (Exception ignored) {
            return false;
        }
    }

    public <T> T get(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return null;
            T value = objectMapper.readValue(json, type);
            if (value != null) {
                try { redis.expire(key, CACHE_TTL); }
                catch (Exception ignored) { /* Keep serving the cached value even if TTL refresh fails. */ }
            }
            return value;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // This cache is an optimization; the database remains authoritative.
        }
    }

    /** Cache-aside with a per-key Redis mutex to coalesce concurrent cache misses. */
    public <T> T getOrLoad(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) return cached;

        String lockKey = key + ":lock";
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + LOCK_WAIT.toNanos();

        while (true) {
            boolean acquired;
            try {
                acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL));
            } catch (Exception ignored) {
                return loader.get();
            }

            if (acquired) {
                try {
                    cached = get(key, type);
                    if (cached != null) return cached;
                    T loaded = loader.get();
                    if (loaded != null) put(key, loaded, ttl);
                    return loaded;
                } finally {
                    releaseLock(lockKey, token);
                }
            }

            cached = get(key, type);
            if (cached != null) return cached;
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) return loader.get();

            long sleepMillis = ThreadLocalRandom.current().nextLong(20, 51);
            try {
                Thread.sleep(Math.min(sleepMillis, Math.max(1, remainingNanos / 1_000_000)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return loader.get();
            }
        }
    }

    private void releaseLock(String lockKey, String token) {
        try { redis.execute(RELEASE_LOCK_SCRIPT, java.util.List.of(lockKey), token); }
        catch (Exception ignored) { /* The lock has a short expiry as a recovery path. */ }
    }

    public static String keyFor(String namespace, String material) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return namespace + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
