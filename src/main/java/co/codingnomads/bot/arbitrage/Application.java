package co.codingnomads.bot.arbitrage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * 简化版套利交易机器人主应用
 * 专注于WebSocket接收火币、币安的ETH现货信息，对比套利机会
 */
@SpringBootApplication
public class Application {

    // ==================== 日志记录器 ====================
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        // 设置控制台输出编码为UTF-8
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        SpringApplication.run(Application.class, args);

        logger.info("=== 启动简化版套利监控服务 ===");
        logger.info("监控交易所: 币安(Binance) + 火币(Huobi)");
        logger.info("监控交易对: ETH/USDT");
        logger.info("套利阈值: 0.03%");
        logger.info("数据保存: MySQL数据库");
        logger.info("按 Ctrl+C 停止监控");
        logger.info("=====================================");
    }
}



