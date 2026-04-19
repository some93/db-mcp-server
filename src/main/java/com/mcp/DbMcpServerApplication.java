package com.mcp;

import com.mcp.infrastructure.config.AppConfig;
import com.mcp.infrastructure.config.ConfigLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

/**
 * @author ouyanghang
 */
@SpringBootApplication
@EnableScheduling
public class DbMcpServerApplication {

    /** 启动阶段单线程赋值，Spring 容器初始化前已就绪 */
    static volatile AppConfig appConfig;

    public static void main(String[] args) {
        String configPath = parseConfigPath(args);
        if (configPath == null) {
            System.err.println("[ERROR] --config argument is required.");
            System.err.println("Usage: java -jar db-mcp-server.jar --config /path/to/config.yml");
            System.exit(1);
        }
        appConfig = ConfigLoader.load(configPath);

        // 将外部 YAML 中的 HTTP 配置注入 Spring 环境
        System.setProperty("server.address", appConfig.getHttp().getHost());
        System.setProperty("server.port", String.valueOf(appConfig.getHttp().getPort()));

        SpringApplication.run(DbMcpServerApplication.class, args);
    }

    @Bean
    public AppConfig appConfig() {
        return appConfig;
    }

    private static String parseConfigPath(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i])) {
                return args[i + 1];
            }
        }
        return Arrays.stream(args)
                .filter(a -> a.startsWith("--config="))
                .map(a -> a.substring("--config=".length()))
                .findFirst()
                .orElse(null);
    }
}

