package co.codingnomads.bot.arbitrage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * WebSocket实时套利监测主应用
 * 集成XChange-stream 4.4.2，支持币安WebSocket实时价格监控
 */
@SpringBootApplication
public class WebSocketArbitrageApp {
    
    public static void main(String[] args) {
        System.out.println("===============================================");
        System.out.println("🚀 币安WebSocket实时套利监测系统");
        System.out.println("📊 基于XChange-stream 4.4.2");
        System.out.println("⚡ 实时价格数据流监控");
        System.out.println("===============================================");
        
        try {
            // 启动Spring Boot应用
            ConfigurableApplicationContext context = SpringApplication.run(WebSocketArbitrageApp.class, args);
            
            // 获取Controller
            Controller controller = context.getBean(Controller.class);
            
            System.out.println("\n选择运行模式:");
            System.out.println("1. 传统轮询套利检测 (runBot)");
            System.out.println("2. WebSocket实时套利监测 (runWebSocketArbitrage)");
            System.out.println("\n当前启动: WebSocket实时套利监测");
            System.out.println("===============================================\n");
            
            // 启动WebSocket实时套利监测
            controller.runWebSocketArbitrage();
            
        } catch (Exception e) {
            System.err.println("❌ 应用启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
