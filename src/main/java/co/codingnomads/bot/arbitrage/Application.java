package co.codingnomads.bot.arbitrage;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;


/**
 * @author Kevin Neag
 */

/**
 * Application Class for starting the entire application
 * Now supports both REST API and WebSocket streaming modes
 */
@SpringBootApplication
public class Application {

    @Autowired
    Controller controller;
    
    @Autowired
    StreamingController streamingController;

    // Configuration: Set to true for WebSocket mode, false for REST API mode
    private static final boolean USE_WEBSOCKET_MODE = true;

    public static void main(String args[]) {
        SpringApplication.run(Application.class);
    }

    /**
     * CommandLineRunner method that starts the appropriate controller based on configuration
     *
     * @throws Exception
     */
    @Bean
    public CommandLineRunner run() throws Exception {
        return args -> {
            if (USE_WEBSOCKET_MODE) {
                System.out.println("=== Starting WebSocket Streaming Mode ===");
                streamingController.runStreamingBot();
            } else {
                System.out.println("=== Starting REST API Mode ===");
                controller.runBot();
            }
        };
    }
}



