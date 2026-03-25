package br.com.techbr.fiscalanalyzer.agent.security;

import br.com.techbr.fiscalanalyzer.common.exception.TooManyRequestsException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ApiRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitService.class);
    private static final long MIN_RETRY_AFTER_SECONDS = 1L;
    private static final String DEFAULT_REDIS_PREFIX = "fiscalanalyzer:security:";
    private static final int DEFAULT_REDIS_TOKEN_BUCKET_TTL_SECONDS = 600;

    private static final String TOKEN_BUCKET_LUA = """
            local now = tonumber(ARGV[1])
            local refillPerSecond = tonumber(ARGV[2])
            local capacity = tonumber(ARGV[3])
            local ttlSeconds = tonumber(ARGV[4])

            local data = redis.call('HMGET', KEYS[1], 'tokens', 'last')
            local tokens = tonumber(data[1])
            local last = tonumber(data[2])

            if tokens == nil or last == nil then
                tokens = capacity
                last = now
            end

            local elapsed = now - last
            if elapsed < 0 then
                elapsed = 0
            end

            tokens = math.min(capacity, tokens + (elapsed / 1000.0) * refillPerSecond)

            local allowed = 0
            local retryMs = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            else
                local missing = 1 - tokens
                if refillPerSecond > 0 then
                    retryMs = math.ceil((missing / refillPerSecond) * 1000)
                else
                    retryMs = 1000
                end
            end

            redis.call('HMSET', KEYS[1], 'tokens', tostring(tokens), 'last', tostring(now))
            redis.call('EXPIRE', KEYS[1], ttlSeconds)

            return {allowed, retryMs}
            """;

    private final boolean enabled;
    private final String backend;
    private final String redisKeyPrefix;
    private final int redisTokenBucketTtlSeconds;

    private final RateRule sessionRule;
    private final RateRule uploadUrlRule;
    private final RateRule manifestRule;
    private final RateRule defaultAgentRule;
    private final RateRule anonymousRule;

    private final int authFailureThreshold;
    private final int authFailureWindowSeconds;
    private final int authLockoutSeconds;

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tokenBucketScript;

    private final Map<String, TokenBucketState> buckets = new ConcurrentHashMap<>();
    private final Map<String, AuthFailureState> authFailures = new ConcurrentHashMap<>();
    private final AtomicLong checkCounter = new AtomicLong(0);

    @Autowired
    public ApiRateLimitService(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.agent.session.rps:1}") double sessionRps,
            @Value("${app.security.rate-limit.agent.session.burst:5}") long sessionBurst,
            @Value("${app.security.rate-limit.agent.upload-url.rps:20}") double uploadUrlRps,
            @Value("${app.security.rate-limit.agent.upload-url.burst:200}") long uploadUrlBurst,
            @Value("${app.security.rate-limit.agent.manifest.rps:5}") double manifestRps,
            @Value("${app.security.rate-limit.agent.manifest.burst:20}") long manifestBurst,
            @Value("${app.security.rate-limit.agent.default.rps:10}") double defaultRps,
            @Value("${app.security.rate-limit.agent.default.burst:50}") long defaultBurst,
            @Value("${app.security.rate-limit.anonymous.rps:2}") double anonymousRps,
            @Value("${app.security.rate-limit.anonymous.burst:10}") long anonymousBurst,
            @Value("${app.security.rate-limit.auth-failure.threshold:20}") int authFailureThreshold,
            @Value("${app.security.rate-limit.auth-failure.window-seconds:300}") int authFailureWindowSeconds,
            @Value("${app.security.rate-limit.auth-failure.lockout-seconds:600}") int authLockoutSeconds,
            @Value("${app.security.rate-limit.backend:in-memory}") String backend,
            @Value("${app.security.rate-limit.redis-key-prefix:" + DEFAULT_REDIS_PREFIX + "}") String redisKeyPrefix,
            @Value("${app.security.rate-limit.redis-token-bucket-ttl-seconds:" + DEFAULT_REDIS_TOKEN_BUCKET_TTL_SECONDS + "}") int redisTokenBucketTtlSeconds,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            MeterRegistry meterRegistry
    ) {
        this(
                enabled,
                sessionRps, sessionBurst,
                uploadUrlRps, uploadUrlBurst,
                manifestRps, manifestBurst,
                defaultRps, defaultBurst,
                anonymousRps, anonymousBurst,
                authFailureThreshold, authFailureWindowSeconds, authLockoutSeconds,
                backend, redisKeyPrefix, redisTokenBucketTtlSeconds,
                redisTemplateProvider.getIfAvailable(),
                meterRegistry
        );
    }

    ApiRateLimitService(
            boolean enabled,
            double sessionRps, long sessionBurst,
            double uploadUrlRps, long uploadUrlBurst,
            double manifestRps, long manifestBurst,
            double defaultRps, long defaultBurst,
            double anonymousRps, long anonymousBurst,
            int authFailureThreshold, int authFailureWindowSeconds, int authLockoutSeconds,
            MeterRegistry meterRegistry
    ) {
        this(
                enabled,
                sessionRps, sessionBurst,
                uploadUrlRps, uploadUrlBurst,
                manifestRps, manifestBurst,
                defaultRps, defaultBurst,
                anonymousRps, anonymousBurst,
                authFailureThreshold, authFailureWindowSeconds, authLockoutSeconds,
                "in-memory", DEFAULT_REDIS_PREFIX, DEFAULT_REDIS_TOKEN_BUCKET_TTL_SECONDS,
                (StringRedisTemplate) null,
                meterRegistry
        );
    }

    ApiRateLimitService(
            boolean enabled,
            double sessionRps, long sessionBurst,
            double uploadUrlRps, long uploadUrlBurst,
            double manifestRps, long manifestBurst,
            double defaultRps, long defaultBurst,
            double anonymousRps, long anonymousBurst,
            int authFailureThreshold, int authFailureWindowSeconds, int authLockoutSeconds,
            String backend,
            String redisKeyPrefix,
            int redisTokenBucketTtlSeconds,
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry
    ) {
        this.enabled = enabled;
        this.backend = StringUtils.hasText(backend) ? backend.trim().toLowerCase() : "in-memory";
        this.redisKeyPrefix = StringUtils.hasText(redisKeyPrefix) ? redisKeyPrefix : DEFAULT_REDIS_PREFIX;
        this.redisTokenBucketTtlSeconds = Math.max(60, redisTokenBucketTtlSeconds);

        this.sessionRule = new RateRule("agent.session", sessionRps, sessionBurst);
        this.uploadUrlRule = new RateRule("agent.upload-url", uploadUrlRps, uploadUrlBurst);
        this.manifestRule = new RateRule("imports.manifest", manifestRps, manifestBurst);
        this.defaultAgentRule = new RateRule("agent.default", defaultRps, defaultBurst);
        this.anonymousRule = new RateRule("anonymous", anonymousRps, anonymousBurst);

        this.authFailureThreshold = authFailureThreshold;
        this.authFailureWindowSeconds = authFailureWindowSeconds;
        this.authLockoutSeconds = authLockoutSeconds;

        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, List.class);
    }

    public void enforceAuthenticated(String route, Long apiKeyId, String fingerprint) {
        if (!enabled || apiKeyId == null) {
            return;
        }

        RateRule rule = resolveRouteRule(route);
        String bucketKey = "auth:" + apiKeyId + ":" + rule.name();

        if (useRedis()) {
            try {
                enforceRedis(rule, bucketKey, "authenticated", route, fingerprint);
                return;
            } catch (TooManyRequestsException ex) {
                throw ex;
            } catch (Exception ex) {
                meterRegistry.counter("security.rate_limit.redis.fallback", "route", safeRoute(route)).increment();
                log.warn("security.rate_limit.redis_error route={} fallback=in-memory message={}",
                        safeRoute(route), ex.getMessage());
            }
        }

        enforceInMemory(rule, bucketKey, "authenticated", route, fingerprint);
    }

    public void registerAuthFailure(String route, String fingerprint, String reason) {
        if (!enabled || !StringUtils.hasText(fingerprint)) {
            return;
        }

        if (useRedis()) {
            try {
                registerAuthFailureRedis(route, fingerprint);
                return;
            } catch (TooManyRequestsException ex) {
                throw ex;
            } catch (Exception ex) {
                meterRegistry.counter("security.auth.redis.fallback", "route", safeRoute(route)).increment();
                log.warn("security.auth.redis_error route={} fallback=in-memory message={}",
                        safeRoute(route), ex.getMessage());
            }
        }

        registerAuthFailureInMemory(route, fingerprint);
    }

    public void registerAuthSuccess(String fingerprint) {
        if (!enabled || !StringUtils.hasText(fingerprint)) {
            return;
        }

        if (useRedis()) {
            try {
                registerAuthSuccessRedis(fingerprint);
                return;
            } catch (Exception ex) {
                meterRegistry.counter("security.auth.redis.fallback", "route", "auth.success").increment();
                log.warn("security.auth.redis_error route=auth.success fallback=in-memory message={}", ex.getMessage());
            }
        }

        authFailures.remove(fingerprint);
    }

    public String resolveRoute(String path) {
        if (!StringUtils.hasText(path)) {
            return defaultAgentRule.name();
        }
        if ("/agent/session".equals(path)) {
            return sessionRule.name();
        }
        if ("/agent/upload-url".equals(path)) {
            return uploadUrlRule.name();
        }
        if ("/imports/manifest".equals(path) || "/api/importacoes/manifest".equals(path)) {
            return manifestRule.name();
        }
        return defaultAgentRule.name();
    }

    private boolean useRedis() {
        return "redis".equals(backend) && redisTemplate != null;
    }

    private void registerAuthFailureRedis(String route, String fingerprint) {
        enforceRedis(anonymousRule, "anon:" + fingerprint, "anonymous", route, fingerprint);

        String lockKey = redisKey("auth:lock:" + fingerprint);
        String failKey = redisKey("auth:fail:" + fingerprint);

        String locked = redisTemplate.opsForValue().get(lockKey);
        if (locked != null) {
            Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            long retryAfter = ttl != null ? Math.max(1L, ttl) : MIN_RETRY_AFTER_SECONDS;
            meterRegistry.counter("security.auth.lockout.blocked", "route", safeRoute(route)).increment();
            throw new TooManyRequestsException("Muitas tentativas invalidas; aguarde para tentar novamente", retryAfter);
        }

        Long failures = redisTemplate.opsForValue().increment(failKey);
        if (failures == null) {
            failures = 1L;
        }
        if (failures == 1L) {
            redisTemplate.expire(failKey, authFailureWindowSeconds, TimeUnit.SECONDS);
        }

        meterRegistry.counter("security.auth.failure", "route", safeRoute(route)).increment();

        if (failures >= authFailureThreshold) {
            redisTemplate.opsForValue().set(lockKey, "1", authLockoutSeconds, TimeUnit.SECONDS);
            redisTemplate.delete(failKey);
            meterRegistry.counter("security.auth.lockout.started", "route", safeRoute(route)).increment();
            log.warn("security.auth.lockout.started route={} fingerprint={} lockoutSeconds={}",
                    safeRoute(route), fingerprint, authLockoutSeconds);
            throw new TooManyRequestsException("Muitas tentativas invalidas; tente novamente mais tarde", authLockoutSeconds);
        }
    }

    private void registerAuthSuccessRedis(String fingerprint) {
        redisTemplate.delete(redisKey("auth:fail:" + fingerprint));
        redisTemplate.delete(redisKey("auth:lock:" + fingerprint));
    }

    private void enforceRedis(RateRule rule, String bucketKey, String scope, String route, String fingerprint) {
        if (rule.refillPerSecond() <= 0 || rule.capacity() <= 0) {
            return;
        }

        String key = redisKey("bucket:" + bucketKey);
        long nowMillis = System.currentTimeMillis();
        List result = redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(nowMillis),
                String.valueOf(rule.refillPerSecond()),
                String.valueOf(rule.capacity()),
                String.valueOf(redisTokenBucketTtlSeconds)
        );

        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Resposta invalida do script de rate-limit");
        }

        long allowed = asLong(result.get(0));
        long retryMs = asLong(result.get(1));

        if (allowed == 1L) {
            meterRegistry.counter("security.rate_limit.allowed", "scope", scope, "route", safeRoute(route)).increment();
            return;
        }

        long retryAfter = retryAfterFromMillis(retryMs);
        meterRegistry.counter("security.rate_limit.blocked", "scope", scope, "route", safeRoute(route)).increment();
        log.warn("security.rate_limit.blocked scope={} route={} fingerprint={} retryAfter={}s backend=redis",
                scope, safeRoute(route), fingerprint, retryAfter);
        throw new TooManyRequestsException("Limite de requisicoes excedido para a rota", retryAfter);
    }

    private void registerAuthFailureInMemory(String route, String fingerprint) {
        enforceInMemory(anonymousRule, "anon:" + fingerprint, "anonymous", route, fingerprint);

        long nowMillis = System.currentTimeMillis();
        AuthFailureState state = authFailures.computeIfAbsent(fingerprint, ignored -> new AuthFailureState());

        synchronized (state) {
            if (state.lockoutUntilMillis > nowMillis) {
                long retryAfter = retryAfterFromMillis(state.lockoutUntilMillis - nowMillis);
                meterRegistry.counter("security.auth.lockout.blocked", "route", safeRoute(route)).increment();
                throw new TooManyRequestsException("Muitas tentativas invalidas; aguarde para tentar novamente", retryAfter);
            }

            if (nowMillis - state.firstFailureAtMillis > authFailureWindowSeconds * 1000L) {
                state.firstFailureAtMillis = nowMillis;
                state.failureCount = 0;
            }
            if (state.firstFailureAtMillis == 0L) {
                state.firstFailureAtMillis = nowMillis;
            }

            state.failureCount++;
            meterRegistry.counter("security.auth.failure", "route", safeRoute(route)).increment();

            if (state.failureCount >= authFailureThreshold) {
                state.failureCount = 0;
                state.firstFailureAtMillis = nowMillis;
                state.lockoutUntilMillis = nowMillis + authLockoutSeconds * 1000L;

                meterRegistry.counter("security.auth.lockout.started", "route", safeRoute(route)).increment();
                log.warn("security.auth.lockout.started route={} fingerprint={} lockoutUntil={}",
                        safeRoute(route), fingerprint, Instant.ofEpochMilli(state.lockoutUntilMillis));

                throw new TooManyRequestsException(
                        "Muitas tentativas invalidas; tente novamente mais tarde",
                        authLockoutSeconds
                );
            }
        }
    }

    private void enforceInMemory(RateRule rule, String bucketKey, String scope, String route, String fingerprint) {
        if (rule.refillPerSecond() <= 0 || rule.capacity() <= 0) {
            return;
        }

        long nowNanos = System.nanoTime();
        TokenBucketState bucket = buckets.computeIfAbsent(bucketKey, ignored -> new TokenBucketState(rule.capacity(), nowNanos));

        synchronized (bucket) {
            refill(bucket, rule, nowNanos);
            if (bucket.tokens >= 1d) {
                bucket.tokens -= 1d;
                meterRegistry.counter("security.rate_limit.allowed", "scope", scope, "route", safeRoute(route)).increment();
                maybeCleanup(nowNanos);
                return;
            }

            long retryAfter = retryAfterFromNanos(waitNanosForNextToken(bucket, rule));
            meterRegistry.counter("security.rate_limit.blocked", "scope", scope, "route", safeRoute(route)).increment();
            log.warn("security.rate_limit.blocked scope={} route={} fingerprint={} retryAfter={}s backend=in-memory",
                    scope, safeRoute(route), fingerprint, retryAfter);
            maybeCleanup(nowNanos);
            throw new TooManyRequestsException("Limite de requisicoes excedido para a rota", retryAfter);
        }
    }

    private void refill(TokenBucketState bucket, RateRule rule, long nowNanos) {
        long elapsed = nowNanos - bucket.lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        double refillTokens = (elapsed / 1_000_000_000d) * rule.refillPerSecond();
        if (refillTokens <= 0d) {
            return;
        }
        bucket.tokens = Math.min(rule.capacity(), bucket.tokens + refillTokens);
        bucket.lastRefillNanos = nowNanos;
    }

    private long waitNanosForNextToken(TokenBucketState bucket, RateRule rule) {
        double missing = 1d - bucket.tokens;
        if (missing <= 0d || rule.refillPerSecond() <= 0d) {
            return 0L;
        }
        return (long) ((missing / rule.refillPerSecond()) * 1_000_000_000d);
    }

    private long retryAfterFromNanos(long nanos) {
        if (nanos <= 0) {
            return MIN_RETRY_AFTER_SECONDS;
        }
        return Math.max(MIN_RETRY_AFTER_SECONDS, (long) Math.ceil(nanos / 1_000_000_000d));
    }

    private long retryAfterFromMillis(long millis) {
        if (millis <= 0) {
            return MIN_RETRY_AFTER_SECONDS;
        }
        return Math.max(MIN_RETRY_AFTER_SECONDS, (long) Math.ceil(millis / 1000d));
    }

    private RateRule resolveRouteRule(String route) {
        if (sessionRule.name().equals(route)) return sessionRule;
        if (uploadUrlRule.name().equals(route)) return uploadUrlRule;
        if (manifestRule.name().equals(route)) return manifestRule;
        return defaultAgentRule;
    }

    private String safeRoute(String route) {
        return StringUtils.hasText(route) ? route : defaultAgentRule.name();
    }

    private String redisKey(String suffix) {
        return redisKeyPrefix + suffix;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private void maybeCleanup(long nowNanos) {
        long check = checkCounter.incrementAndGet();
        if (check % 5000 != 0) {
            return;
        }
        long maxIdleNanos = 10L * 60L * 1_000_000_000L;
        buckets.entrySet().removeIf(entry -> nowNanos - entry.getValue().lastRefillNanos > maxIdleNanos);

        long nowMillis = System.currentTimeMillis();
        long expirationMillis = (long) authFailureWindowSeconds * 1000L + (long) authLockoutSeconds * 1000L;
        authFailures.entrySet().removeIf(entry -> nowMillis - entry.getValue().firstFailureAtMillis > expirationMillis
                && entry.getValue().lockoutUntilMillis <= nowMillis);
    }

    private record RateRule(String name, double refillPerSecond, long capacity) {
    }

    private static final class TokenBucketState {
        double tokens;
        long lastRefillNanos;

        private TokenBucketState(long capacity, long nowNanos) {
            this.tokens = capacity;
            this.lastRefillNanos = nowNanos;
        }
    }

    private static final class AuthFailureState {
        long firstFailureAtMillis;
        int failureCount;
        long lockoutUntilMillis;
    }
}
