package co.codingnomads.bot.arbitrage;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;

/**
 * 套利交易机器人主应用
 * 支持以下模式：
 * 1. REST API 模式 - 通过 REST API 获取市场数据
 * 2. WebSocket 流模式 - 通过 WebSocket 实时监控市场
 * 3. WebSocket 套利检测模式 - 实时检测套利机会
 */
@SpringBootApplication
public class Application {

    @Autowired
    private Controller controller;
    
    @Autowired
    private StreamingController streamingController;

    /**
     * 运行模式配置
     * 可选值：REST, WEBSOCKET, ARBITRAGE
     */
    private static final String RUN_MODE = "REST";

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
     * 根据配置启动相应的服务
     */
    @Bean
    public CommandLineRunner run() throws Exception {
        return args -> {
            switch (RUN_MODE) {
                case "WEBSOCKET":
                    System.out.println("=== 启动 WebSocket 流模式 ===");
                    streamingController.runStreamingBot();
                    break;
                    
                case "ARBITRAGE":
                    System.out.println("=== 启动 WebSocket 套利检测 ===");
                    controller.runWebSocketArbitrage();
                    break;
                    
                case "REST":
                    System.out.println("启动实时套利监控...");
                    System.out.println("监控币安和火币的ETH/USDT交易对");
                    System.out.println("按 Ctrl+C 停止监控");
                    break;
                default:
                    System.out.println("未知的运行模式: " + RUN_MODE);
                    System.exit(1);
            }
        };
    }
}



