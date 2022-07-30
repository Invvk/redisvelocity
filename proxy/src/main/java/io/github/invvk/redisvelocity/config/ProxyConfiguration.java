package io.github.invvk.redisvelocity.config;

import ch.jalu.configme.SettingsManager;
import ch.jalu.configme.SettingsManagerBuilder;
import lombok.Getter;

import java.io.File;

@Getter
public class ProxyConfiguration {

    private final SettingsManager config;

    public ProxyConfiguration() {
        File file = new File("./plugins/RedisVelocity");

        this.config = SettingsManagerBuilder.
                withYamlFile(new File(file, "config.yml"))
                .useDefaultMigrationService()
                .configurationData(ProxyConfigProperties.class)
                .create();
    }

}
