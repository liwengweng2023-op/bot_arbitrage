package co.codingnomads.bot.arbitrage.websocket;

import co.codingnomads.bot.arbitrage.model.ticker.TickerData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import org.java_websocket.framing.Framedata;

/**
 * Huobi WebSocket client for real-time ticker data
 */
public class HuobiWebSocketClient extends ExchangeWebSocketClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(HuobiWebSocketClient.class);
    private static final String HUOBI_WS_URL = "wss://api-aws.huobi.pro/ws";
    
    private Consumer<TickerData> tickerUpdateCallback;
    private ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();
    private Exchange exchange;
    private ObjectMapper objectMapper = new ObjectMapper();

    public HuobiWebSocketClient() {
        super(java.net.URI.create(HUOBI_WS_URL));
        setupMessageHandlers();
    }

    public HuobiWebSocketClient(Exchange exchange) {
        super(java.net.URI.create(HUOBI_WS_URL));
        this.exchange = exchange;
        setupMessageHandlers();
    }

    private void setupMessageHandlers() {
        registerMessageHandler("ticker", this::handleTickerUpdate);
        registerMessageHandler("ping", this::handlePingMessage);
    }
    
    @Override
    public void onMessage(String message) {
        // 火币使用GZIP压缩，字符串消息不常见，但仍需处理
        try {
            LOG.debug("收到火币字符串消息: {}", message);
            JsonNode jsonMessage = objectMapper.readTree(message);
            processMessage(jsonMessage);
        } catch (Exception e) {
            LOG.error("处理火币字符串消息时出错", e);
        }
    }
    
    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            // 解压缩GZIP消息
            String message = decompressGzip(bytes.array());
            LOG.debug("收到火币GZIP消息: {}", message);
            
            JsonNode jsonMessage = objectMapper.readTree(message);
            processMessage(jsonMessage);
            
        } catch (Exception e) {
            LOG.error("处理火币WebSocket消息时出错", e);
        }
    }
    
    private void processMessage(JsonNode jsonMessage) {
        try {
            // 处理心跳消息
            if (jsonMessage.has("ping")) {
                handlePingMessage(jsonMessage);
                return;
            }
            
            // 处理订阅确认
            if (jsonMessage.has("subbed")) {
                LOG.info("火币订阅成功: {}", jsonMessage.get("subbed").asText());
                return;
            }
            
            // 处理ticker数据
            if (jsonMessage.has("ch") && jsonMessage.get("ch").asText().contains("ticker")) {
                handleTickerUpdate(jsonMessage);
            }
        } catch (Exception e) {
            LOG.error("处理火币消息时出错", e);
        }
    }
    
    private String decompressGzip(byte[] compressed) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toString("UTF-8");
        }
    }
    
    private void handlePingMessage(JsonNode message) {
        try {
            if (message.has("ping")) {
                long pingValue = message.get("ping").asLong();
                String pongMessage = String.format("{\"pong\": %d}", pingValue);
                send(pongMessage);
                LOG.debug("响应火币心跳: {}", pongMessage);
            }
        } catch (Exception e) {
            LOG.error("处理火币心跳消息时出错", e);
        }
    }

    private void handleTickerUpdate(JsonNode message) {
        try {
            // Huobi ticker format: {"ch":"market.ethusdt.ticker","ts":1234567890,"tick":{"bid":4450.16,"ask":4450.17,...}}
            if (message.has("ch") && message.get("ch").asText().contains("ticker") && message.has("tick")) {
                String channel = message.get("ch").asText();
                String symbol = extractSymbolFromChannel(channel);
                JsonNode tick = message.get("tick");
                
                if (tick.has("bid") && tick.has("ask")) {
                    BigDecimal bid = new BigDecimal(tick.get("bid").asText());
                    BigDecimal ask = new BigDecimal(tick.get("ask").asText());
                    
                    // Convert Huobi symbol to XChange CurrencyPair
                    CurrencyPair currencyPair = convertSymbolToCurrencyPair(symbol);
                    if (currencyPair != null) {
                        TickerData tickerData = new TickerData(currencyPair, exchange, bid, ask);
                        tickerCache.put(symbol, tickerData);
                        
                        if (tickerUpdateCallback != null) {
                            tickerUpdateCallback.accept(tickerData);
                        }
                        
                        LOG.debug("更新火币行情数据 {}: 买价={}, 卖价={}", symbol, bid, ask);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("处理火币行情数据更新时出错", e);
        }
    }

    private String extractSymbolFromChannel(String channel) {
        // Extract symbol from "market.ethusdt.ticker" -> "ethusdt"
        String[] parts = channel.split("\\.");
        if (parts.length >= 2) {
            return parts[1].toUpperCase();
        }
        return null;
    }

    private CurrencyPair convertSymbolToCurrencyPair(String symbol) {
        switch (symbol.toUpperCase()) {
            case "ETHUSDT":
                return CurrencyPair.ETH_USD;
            case "BTCUSDT":
                return CurrencyPair.BTC_USD;
            default:
                LOG.warn("未知的火币交易对: {}", symbol);
                return null;
        }
    }

    public void setTickerUpdateCallback(Consumer<TickerData> callback) {
        this.tickerUpdateCallback = callback;
    }

    public TickerData getLatestTicker(String symbol) {
        return tickerCache.get(symbol);
    }

    public void subscribeToTicker(String symbol) {
        String subscriptionMessage = String.format(
            "{\"sub\": \"market.%s.ticker\", \"id\": \"ticker_%s\"}",
            symbol.toLowerCase(),
            System.currentTimeMillis()
        );
        
        if (isConnected()) {
            send(subscriptionMessage);
            LOG.info("已订阅火币行情数据，交易对: {}", symbol);
        } else {
            LOG.warn("无法订阅火币行情数据 - 连接未建立");
        }
    }


}
