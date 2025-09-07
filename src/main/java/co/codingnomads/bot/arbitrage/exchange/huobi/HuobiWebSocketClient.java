package co.codingnomads.bot.arbitrage.exchange.huobi;

import co.codingnomads.bot.arbitrage.service.websocket.WebSocketMessageHandler;
import co.codingnomads.bot.arbitrage.util.GzipUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

import static co.codingnomads.bot.arbitrage.config.ArbitrageConfig.SYMBOL;

/**
 * 火币交易所WebSocket客户端
 *
 * 负责：
 * 1. 连接到火币WebSocket API
 * 2. 处理实时行情数据（GZIP压缩）
 * 3. 维护心跳连接（Ping/Pong）
 * 4. 解析价格信息并更新本地缓存
 * 5. 触发套利机会检查
 */
public class HuobiWebSocketClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(HuobiWebSocketClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketMessageHandler messageHandler;
    private Timer pingTimer;
    private final Object pingLock = new Object();

    /**
     * 构造函数
     *
     * @param serverUri      火币WebSocket服务器URI
     * @param messageHandler 消息处理器
     */
    public HuobiWebSocketClient(URI serverUri, WebSocketMessageHandler messageHandler) {
        super(serverUri);
        this.messageHandler = messageHandler;
        this.setConnectionLostTimeout(60);
    }

    @Override
    public void onMessage(String message) {
        logger.debug("[Huobi] 收到文本消息: {}", message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            byte[] byteArray = new byte[bytes.remaining()];
            bytes.get(byteArray);
            handleHuobiMessage(byteArray);
        } catch (Exception e) {
            logger.error("[Huobi] 处理二进制消息时出错: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("[Huobi] 连接已建立，发送订阅请求...");
        sendSubscriptionMessage();
        startPingTimer();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("[Huobi] 连接已关闭: {} (code: {})", reason, code);
        stopPingTimer();
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        logger.error("[Huobi] 连接错误: {}", ex.getMessage(), ex);
        stopPingTimer();
    }

    private void sendSubscriptionMessage() {
        String subscribeMsg = String.format("{\"sub\":\"market.%s.bbo\",\"id\":\"%d\"}",
                SYMBOL, System.currentTimeMillis());
        this.send(subscribeMsg);
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                logger.info("[Huobi] 5秒后尝试重新连接...");
                Thread.sleep(5000);
                logger.info("[Huobi] 正在重新连接...");
                this.reconnectBlocking();
                logger.info("[Huobi] 重新连接成功");
            } catch (Exception e) {
                logger.error("[Huobi] 重连失败: {}", e.getMessage(), e);
                this.onClose(0, "重连失败", false);
            }
        }).start();
    }

    private void startPingTimer() {
        stopPingTimer();
        synchronized (pingLock) {
            pingTimer = new Timer("Huobi-Ping-Timer", true);
            pingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (isOpen()) {
                        try {
                            sendPingMessage();
                        } catch (Exception e) {
                            logger.error("[Huobi] 发送Ping失败: {}", e.getMessage(), e);
                            reconnect();
                        }
                    }
                }
            }, 10000, 20000); // 10秒后开始，每20秒执行一次
        }
    }

    private void stopPingTimer() {
        synchronized (pingLock) {
            if (pingTimer != null) {
                pingTimer.cancel();
                pingTimer = null;
            }
        }
    }

    private void sendPingMessage() {
        String pingMsg = "{\"ping\":" + System.currentTimeMillis() + "}";
        send(pingMsg);
        logger.debug("[Huobi] 发送Ping...");
    }

    private void handleHuobiMessage(byte[] message) {
        try {
            String decompressedMessage = GzipUtil.decompressGzip(message);
            if (decompressedMessage == null || decompressedMessage.isEmpty()) {
                return;
            }

            if (decompressedMessage.contains("pong")) {
                logger.debug("[Huobi] 收到Pong响应");
                return;
            }

            if (decompressedMessage.contains("subbed") || decompressedMessage.contains("err-msg")) {
                logger.info("[Huobi] {}", decompressedMessage);
                return;
            }

            JsonNode jsonNode = objectMapper.readTree(decompressedMessage);
            if (jsonNode.has("ch") && jsonNode.has("tick")) {
                String channel = jsonNode.get("ch").asText();
                if (channel.endsWith(".bbo")) {
                    JsonNode tick = jsonNode.get("tick");
                    if (tick.has("bid") && tick.has("ask")) {
                        double bestBid = tick.get("bid").asDouble();
                        double bestAsk = tick.get("ask").asDouble();
                        messageHandler.handlePriceUpdate("火币", bestBid, bestAsk);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[Huobi] 处理消息时出错: {}", e.getMessage(), e);
        }
    }
}