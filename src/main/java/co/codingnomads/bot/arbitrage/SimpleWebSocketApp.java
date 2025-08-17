package co.codingnomads.bot.arbitrage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * WebSocket套利检测应用
 * 集成到主Controller，支持币安实时套利检测
 */
@SpringBootApplication
public class SimpleWebSocketApp {
    
    public static void main(String[] args) {
        try {
            System.out.println("=== WebSocket套利检测应用 ===");
            
            // 启动Spring Boot应用
            ConfigurableApplicationContext context = SpringApplication.run(SimpleWebSocketApp.class, args);
            
            // 获取Controller
            Controller controller = context.getBean(Controller.class);
            
            // 启动WebSocket实时套利监测
            controller.runWebSocketArbitrage();
            
        } catch (Exception e) {
            System.err.println("❌ 启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
