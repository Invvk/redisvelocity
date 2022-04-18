package io.github.invvk.redisvelocity.util;

import io.github.invvk.redisvelocity.RedisVelocity;
import lombok.AllArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.concurrent.Callable;

@AllArgsConstructor
public abstract class RedisCallable<T> implements Callable<T>, Runnable {
    private final RedisVelocity plugin;

    @Override
    public T call() {
        return run(false);
    }

    public void run() {
        call();
    }

    private T run(boolean retry) {
        try (Jedis jedis = plugin.getPool().getResource()) {
            return call(jedis);
        } catch (JedisConnectionException e) {
            plugin.getLogger().error("Unable to get connection", e);

            if (!retry) {
                // Wait one second before retrying the task
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException("task failed to run", e1);
                }
                return run(true);
            }
        }

        throw new RuntimeException("task failed to run");
    }

    protected abstract T call(Jedis jedis);
}