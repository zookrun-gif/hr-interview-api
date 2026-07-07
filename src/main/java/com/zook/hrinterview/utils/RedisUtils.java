package com.zook.hrinterview.utils;

import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.enums.RedisKeyEnum;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {

    private static final RedisSerializer<String> STRING_SERIALIZER = new StringRedisSerializer();

    private static final RedisSerializer<Long> LONG_SERIALIZER = new GenericToStringSerializer<>(Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean hasKey(RedisKeyEnum keyEnum) {
        return hasKey(keyEnum.getKey());
    }

    public Boolean hasKey(RedisKeyEnum keyEnum, Object keyData) {
        return hasKey(keyEnum.buildKey(keyData));
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Boolean delete(RedisKeyEnum keyEnum) {
        return delete(keyEnum.getKey());
    }

    public Boolean delete(RedisKeyEnum keyEnum, Object keyData) {
        return delete(keyEnum.buildKey(keyData));
    }

    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    public Boolean expire(String key, Duration duration) {
        return redisTemplate.expire(key, duration);
    }

    public Boolean expire(RedisKeyEnum keyEnum) {
        return expire(keyEnum.getKey(), keyEnum.getTtl());
    }

    public Boolean expire(RedisKeyEnum keyEnum, Object keyData) {
        return expire(keyEnum.buildKey(keyData), keyEnum.getTtl());
    }

    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public String getString(String key) {
        byte[] value = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(STRING_SERIALIZER.serialize(key)));
        return value == null ? null : STRING_SERIALIZER.deserialize(value);
    }

    public String getString(RedisKeyEnum keyEnum, Object keyData) {
        return getString(keyEnum.buildKey(keyData));
    }

    public Object get(RedisKeyEnum keyEnum) {
        return get(keyEnum.getKey());
    }

    public Object get(RedisKeyEnum keyEnum, Object keyData) {
        return get(keyEnum.buildKey(keyData));
    }

    public Object getAndDelete(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    public Object getAndDelete(RedisKeyEnum keyEnum, Object keyData) {
        return getAndDelete(keyEnum.buildKey(keyData));
    }

    public <T> T get(String key, Class<T> targetType) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (!targetType.isInstance(value)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Redis 缓存类型不匹配");
        }
        return targetType.cast(value);
    }

    public <T> T get(RedisKeyEnum keyEnum, Class<T> targetType) {
        return get(keyEnum.getKey(), targetType);
    }

    public <T> T get(RedisKeyEnum keyEnum, Object keyData, Class<T> targetType) {
        return get(keyEnum.buildKey(keyData), targetType);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(RedisKeyEnum keyEnum, Object value) {
        set(keyEnum.getKey(), value, keyEnum.getTtl());
    }

    public void set(RedisKeyEnum keyEnum, Object keyData, Object value) {
        set(keyEnum.buildKey(keyData), value, keyEnum.getTtl());
    }

    public void set(RedisKeyEnum keyEnum, Object value, long timeout, TimeUnit unit) {
        set(keyEnum.getKey(), value, timeout, unit);
    }

    public void set(RedisKeyEnum keyEnum, Object keyData, Object value, long timeout, TimeUnit unit) {
        set(keyEnum.buildKey(keyData), value, timeout, unit);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void set(String key, Object value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    public Boolean setIfAbsent(RedisKeyEnum keyEnum, Object keyData, Object value) {
        return redisTemplate.opsForValue().setIfAbsent(keyEnum.buildKey(keyData), value, keyEnum.getTtl());
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long increment(RedisKeyEnum keyEnum, Object keyData) {
        Long value = increment(keyEnum.buildKey(keyData));
        expire(keyEnum, keyData);
        return value;
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Double increment(String key, double delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public void hSetAll(String key, Map<String, Object> values) {
        redisTemplate.opsForHash().putAll(key, values);
    }

    public Boolean hHasKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    public Long hDelete(String key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    public Long lLeftPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    public Long lLeftPush(RedisKeyEnum keyEnum, Object value) {
        Long count = lLeftPush(keyEnum.getKey(), value);
        expire(keyEnum);
        return count;
    }

    public Long lRightPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    public Long lRightPush(RedisKeyEnum keyEnum, Object value) {
        Long count = lRightPush(keyEnum.getKey(), value);
        expire(keyEnum);
        return count;
    }

    public Object lLeftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public Object lLeftPop(RedisKeyEnum keyEnum) {
        return lLeftPop(keyEnum.getKey());
    }

    public Object lRightPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    public Object lRightPop(RedisKeyEnum keyEnum) {
        return lRightPop(keyEnum.getKey());
    }

    public Object lRightPopAndLeftPush(RedisKeyEnum sourceKeyEnum, RedisKeyEnum destinationKeyEnum) {
        Object value = redisTemplate.opsForList().rightPopAndLeftPush(sourceKeyEnum.getKey(), destinationKeyEnum.getKey());
        if (value != null) {
            expire(sourceKeyEnum);
            expire(destinationKeyEnum);
        }
        return value;
    }

    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    public Long lSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    public Long lSize(RedisKeyEnum keyEnum) {
        return lSize(keyEnum.getKey());
    }

    public Long lRemove(RedisKeyEnum keyEnum, long count, Object value) {
        return redisTemplate.opsForList().remove(keyEnum.getKey(), count, value);
    }

    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    public Long sAdd(RedisKeyEnum keyEnum, Object... values) {
        Long count = sAdd(keyEnum.getKey(), values);
        expire(keyEnum);
        return count;
    }

    public Long sSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public Set<Object> sMembers(RedisKeyEnum keyEnum) {
        return sMembers(keyEnum.getKey());
    }

    public Set<String> sStringMembers(String key) {
        Set<byte[]> values = redisTemplate.execute((RedisCallback<Set<byte[]>>) connection ->
                connection.setCommands().sMembers(STRING_SERIALIZER.serialize(key)));
        if (values == null) {
            return Collections.emptySet();
        }
        return values.stream()
                .map(STRING_SERIALIZER::deserialize)
                .collect(Collectors.toSet());
    }

    public Set<String> sStringMembers(RedisKeyEnum keyEnum) {
        return sStringMembers(keyEnum.getKey());
    }

    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public Long sRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    public Long sRemove(RedisKeyEnum keyEnum, Object... values) {
        return sRemove(keyEnum.getKey(), values);
    }

    public Long sStringRemove(String key, String... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        byte[][] serializedValues = new byte[values.length][];
        for (int i = 0; i < values.length; i++) {
            serializedValues[i] = STRING_SERIALIZER.serialize(values[i]);
        }
        return redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.setCommands().sRem(STRING_SERIALIZER.serialize(key), serializedValues));
    }

    public Long sStringRemove(RedisKeyEnum keyEnum, String... values) {
        return sStringRemove(keyEnum.getKey(), values);
    }

    public Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    public Set<ZSetOperations.TypedTuple<Object>> zRangeWithScores(String key, long start, long end) {
        return redisTemplate.opsForZSet().rangeWithScores(key, start, end);
    }

    public Long zRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    public Boolean tryLock(String key, String lockValue, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, lockValue, timeout, unit));
    }

    public Boolean tryLock(RedisKeyEnum keyEnum, Object keyData, String lockValue) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                keyEnum.buildKey(keyData),
                lockValue,
                keyEnum.getTtl()
        ));
    }

    public Boolean unlock(String key, String lockValue) {
        Object value = get(key);
        if (lockValue.equals(value)) {
            return delete(key);
        }
        return Boolean.FALSE;
    }

    public Boolean unlock(RedisKeyEnum keyEnum, Object keyData, String lockValue) {
        return unlock(keyEnum.buildKey(keyData), lockValue);
    }

    public Long executeLongScript(String script, List<String> keys, Object... args) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        Object[] stringArgs = args == null ? new Object[0] : new Object[args.length];
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                stringArgs[i] = String.valueOf(args[i]);
            }
        }
        return redisTemplate.execute(
                redisScript,
                STRING_SERIALIZER,
                LONG_SERIALIZER,
                keys == null ? Collections.emptyList() : keys,
                stringArgs
        );
    }
}
