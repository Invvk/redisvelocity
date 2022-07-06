# RedisVelocity
### A fork for 'limework redisbungee'
[![RedisBungee Build](https://github.com/UltimisMC/RedisVelocity/actions/workflows/gradle.yml/badge.svg)](https://github.com/UltimisMC/RedisVelocity/actions/workflows/gradle.yml) [![](https://jitpack.io/v/ultimismc/redisvelocity.svg)](https://jitpack.io/#ultimismc/redisvelocity)

This plugin is used for Velocity network since it lacks from multi proxy synchronization plugins.

RedisVelocity uses [Redis](https://redis.io) to Synchronize data between [Velocity](https://github.com/PaperMC/Velocity) proxies

## Notice: about older versions of redis than redis 6.0

As of now Version 0.6.4 is only supporting redis 6.0 and above!

## Compiling

RedisVelocity is distributed as a [gradle](https://gradle.org) project. 

To compile and installing to in your local Maven repository:

    git clone https://github.com/UltimisMC/RedisVelocity.git .
    gradlew :proxy:clean :proxy:build
    gradlew :spigot:clean :spigot:build

To use the repository in your project, you need to add jitpack maven server:

For maven users:

    <repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>

for Gradle users:

    maven {
        name 'jitpack.io'
        url 'https://jitpack.io'
    }
    
And use it in your pom file.

for Maven users:    

    <dependency>
	    <groupId>com.github.UltimisMC</groupId>
	    <artifactId>RedisVelocity</artifactId>
	    <version>2.0.1</version>
        <scope>provided</scope>
	</dependency>

for Gradle users:

    compileOnly 'com.github.UltimisMC:RedisVelocity:2.0.1'

## Configuration

**REDISVELOCITY REQUIRES A REDIS SERVER**, preferably with reasonably low latency. The default [config](https://github.com/UltimisMC/RedisVelocity/blob/master/src/main/resources/example_config.yml) is saved when the plugin first starts.

## License!

This project is distributed under Eclipse Public License 1.0

You can find it [here](https://github.com/Limework/RedisBungee/blob/master/LICENSE)

You can find the original RedisBungee by minecrafter [here](https://github.com/minecrafter/RedisBungee) or spigot page [here](https://www.spigotmc.org/resources/redisbungee.13494/)

You can also find the fork we used by Limework [here](https://github.com/limework/redisbungee)

## YourKit

YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/), [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).

![YourKit](https://www.yourkit.com/images/yklogo.png)
