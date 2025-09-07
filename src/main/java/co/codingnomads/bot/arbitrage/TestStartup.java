package co.codingnomads.bot.arbitrage;

import org.apache.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 测试启动类 - 用于诊断启动问题
 */
@SpringBootApplication
public class TestStartup {

    private static final Logger logger = Logger.getLogger(TestStartup.class);

    @Autowired
    private DataSource dataSource;

    public static void main(String[] args) {
        logger.info("开始测试启动...");
        SpringApplication.run(TestStartup.class, args);
    }

    @Bean
    public CommandLineRunner testDatabase() {
        return args -> {
            try {
                logger.info("测试数据库连接...");
                Connection connection = dataSource.getConnection();
                logger.info("数据库连接成功！");
                connection.close();
                
                logger.info("测试完成，程序将退出");
                System.exit(0);
            } catch (Exception e) {
                logger.error("数据库连接失败: " + e.getMessage(), e);
                System.exit(1);
            }
        };
    }
}
