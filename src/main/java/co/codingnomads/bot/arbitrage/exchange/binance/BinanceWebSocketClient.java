package co.codingnomads.bot.arbitrage.exchange.binance;

import co.codingnomads.bot.arbitrage.service.websocket.WebSocketMessageHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * 币安交易所WebSocket客户端
 *
 * 负责：
 * 1. 连接到币安WebSocket API
 * 2. 处理实时行情数据
 * 3. 解析价格信息并更新本地缓存
 * 4. 触发套利机会检查
 */
public class BinanceWebSocketClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketMessageHandler messageHandler;

    /**
     * 构造函数
     *
     * @param serverUri      币安WebSocket服务器URI
     * @param messageHandler 消息处理器
     */
    public BinanceWebSocketClient(URI serverUri, WebSocketMessageHandler messageHandler) {
        super(serverUri);
        this.messageHandler = messageHandler;
        this.setConnectionLostTimeout(60);
    }

    /**
     * 连接建立时的回调
     *
     * @param handshakedata 握手数据
     */
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("[Binance] 连接已建立，开始接收数据...");
    }

    /**
     * 处理文本消息
     *
     * @param message 接收到的文本消息
     */
    @Override
    public void onMessage(String message) {
        try {
            handleBinanceMessage(message);
        } catch (Exception e) {
            logger.error("[Binance] 处理文本消息时出错: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("[Binance] 连接已关闭: {} (code: {})", reason, code);
    }

    @Override
    public void onError(Exception ex) {
        logger.error("[Binance] 连接错误: {}", ex.getMessage(), ex);
    }

    /**
     * 处理币安消息内容
     *
     * @param message 解压后的消息内容
     */
    private void handleBinanceMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            double bestBid = jsonNode.get("b").asDouble();  // 买一价
            double bestAsk = jsonNode.get("a").asDouble();  // 卖一价

            messageHandler.handlePriceUpdate("币安", bestBid, bestAsk);
        } catch (Exception e) {
            logger.error("[Binance] 处理消息时出错: {}", e.getMessage(), e);
        }
    }
}