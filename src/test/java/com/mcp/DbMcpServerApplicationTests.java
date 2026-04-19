package com.mcp;

import org.junit.jupiter.api.Test;

class DbMcpServerApplicationTests {

    @Test
    void contextLoads() {
        // 完整上下文依赖真实数据库连接，集成测试在有 DB 环境时单独运行。
        // 单元测试阶段仅验证此入口存在，不启动 Spring 上下文。
    }
}
