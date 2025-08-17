package co.codingnomads.bot.arbitrage;

import co.codingnomads.bot.arbitrage.action.arbitrage.ArbitragePrintAction;
import co.codingnomads.bot.arbitrage.exception.EmailLimitException;
import co.codingnomads.bot.arbitrage.exception.ExchangeDataException;
import co.codingnomads.bot.arbitrage.exception.WaitTimeException;
import co.codingnomads.bot.arbitrage.service.websocket.WebSocketArbitrageService;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * WebSocket-based streaming controller for real-time arbitrage detection
 */
@Service
public class StreamingController {
    
    private static final Logger LOG = LoggerFactory.getLogger(StreamingController.class);

    @Autowired
    private WebSocketArbitrageService webSocketArbitrageService;

    @Autowired
    private ArbitragePrintAction arbitragePrintAction;

    /**
     * Run the streaming bot with WebSocket connections
     */
    public void runStreamingBot() throws IOException, InterruptedException, EmailLimitException, WaitTimeException, ExchangeDataException {
        
        LOG.info("启动WebSocket流式模式，支持5个交易所：币安、Kraken、火币、Coinbase Pro、Gemini...");

        // 配置WebSocket套利设置
        webSocketArbitrageService.setTimeIntervalRepeater(2000); // 每2秒检查一次
        // webSocketArbitrageService.setLoopIterations(100); // 取消注释以限制迭代次数

        // 配置套利操作
        arbitragePrintAction.setArbitrageMargin(0.03); // 0.03%保证金

        LOG.info("配置信息：保证金={}%，间隔={}毫秒，交易所=5个（币安+Kraken+火币+Coinbase Pro+Gemini）", 
                0.03, 2000);

        try {
            // Start WebSocket arbitrage
            webSocketArbitrageService.runWebSocketArbitrage(
                CurrencyPair.ETH_USD,
                arbitragePrintAction
            );
        } catch (Exception e) {
            LOG.error("流式机器人遇到错误", e);
            throw new ExchangeDataException("WebSocket套利失败: " + e.getMessage());
        } finally {
            // 清理资源
            LOG.info("停止WebSocket套利服务...");
            webSocketArbitrageService.stop();
        }
    }

    /**
     * Stop the streaming bot
     */
    public void stopStreamingBot() {
        LOG.info("Stopping streaming bot...");
        webSocketArbitrageService.stop();
    }

    /**
     * Get current connection status
     */
    public String getStatus() {
        return webSocketArbitrageService.getConnectionStatus();
    }
}
