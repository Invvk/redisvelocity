package io.github.invvk.redisvelocity.test;

import io.github.invvk.redisvelocity.RedisUtil;
import org.junit.Assert;
import org.junit.Test;

public class RedisUtilTest {
    @Test
    public void testRedisLuaCheck() {
        Assert.assertTrue(RedisUtil.canUseLua("6.2.0"));
        Assert.assertTrue(RedisUtil.canUseLua("6.1.0"));
        Assert.assertTrue(RedisUtil.canUseLua("6.0.0"));
        Assert.assertFalse(RedisUtil.canUseLua("2.6.0"));
        Assert.assertFalse(RedisUtil.canUseLua("2.2.12"));
        Assert.assertFalse(RedisUtil.canUseLua("1.2.4"));
        Assert.assertFalse(RedisUtil.canUseLua("2.8.4"));
        Assert.assertFalse(RedisUtil.canUseLua("3.0.0"));
        Assert.assertFalse(RedisUtil.canUseLua("3.2.1"));
    }
}
