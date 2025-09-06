package co.codingnomads.bot.arbitrage;

import co.codingnomads.bot.arbitrage.service.RealTimeArbitrageService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

/**
 * 简化版套利交易机器人主应用
 * 专注于WebSocket接收火币、币安的ETH现货信息，对比套利机会
 */
@SpringBootApplication
public class Application {

    @Autowired
    private RealTimeArbitrageService arbitrageService;

    public static void main(String[] args) {
        // 设置控制台输出编码为UTF-8
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        SpringApplication.run(Application.class, args);
    }

    /**
     * 启动WebSocket套利监控服务
     */
    @Bean
    public CommandLineRunner run() throws Exception {
        return args -> {
            System.out.println("=== 启动简化版套利监控服务 ===");
            System.out.println("监控交易所: 币安(Binance) + 火币(Huobi)");
            System.out.println("监控交易对: ETH/USDT");
            System.out.println("套利阈值: 0.03%");
            System.out.println("数据保存: MySQL数据库");
            System.out.println("按 Ctrl+C 停止监控");
            System.out.println("=====================================");
            
            // 启动套利监控
            arbitrageService.startArbitrageMonitoring();
        };
    }
}



