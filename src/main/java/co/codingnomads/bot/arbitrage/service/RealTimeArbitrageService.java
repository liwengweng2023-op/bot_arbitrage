package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.exchange.ExchangeWebSocketClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;

@Service
public class RealTimeArbitrageService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, Double>> exchangePrices = new ConcurrentHashMap<>();
    private static final String SYMBOL = "ethusdt";
    
    private ExchangeWebSocketClient binanceClient;
    private ExchangeWebSocketClient huobiClient;
    
    @PostConstruct
    public void init() {
        try {
            // 初始化币安WebSocket连接
            String binanceWsUrl = "wss://stream.binance.com:9443/ws/" + SYMBOL + "@ticker";
            binanceClient = new ExchangeWebSocketClient(
                    "币安",
                    new URI(binanceWsUrl),
                    message -> {
                        if (message instanceof String) {
                            handleBinanceMessage((String) message);
                        } else if (message instanceof byte[]) {
                            // 币安不使用GZIP压缩，但为了安全起见处理一下
                            try {
                                String decompressed = decompressGzip((byte[]) message);
                                if (!decompressed.isEmpty()) {
                                    handleBinanceMessage(decompressed);
                                }
                            } catch (Exception e) {
                                System.err.println("处理币安二进制消息时出错: " + e.getMessage());
                            }
                        }
                    }
            ) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    super.onOpen(handshakedata);
                    System.out.println("[" + getExchangeName() + "] 连接已建立，开始接收数据...");
                }
            };
            binanceClient.connect();
            
            // 初始化火币WebSocket连接 - 使用主站API地址
            String huobiWsUrl = "wss://api.huobi.pro/ws";
            huobiClient = new ExchangeWebSocketClient(
                    "火币",
                    new URI(huobiWsUrl),
                    message -> {
                        if (message instanceof byte[]) {
                            // 火币使用GZIP压缩
                            handleHuobiMessage((byte[]) message);
                        } else if (message instanceof String) {
                            // 处理文本消息（如Pong响应）
                            String msg = (String) message;
                            System.out.println("\n[火币] 收到文本消息: " + msg);
                        }
                    }
            ) {
                private Timer pingTimer;
                
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    super.onOpen(handshakedata);
                    System.out.println("[" + getExchangeName() + "] 连接已建立，发送订阅请求...");
                    
                    // 发送订阅消息 - 使用bbo（最优买一卖一）频道
                    String subscribeMsg = String.format("{\"sub\":\"market.%s.bbo\",\"id\":\"%d\"}", 
                        SYMBOL, System.currentTimeMillis());
                    send(subscribeMsg);
                    
                    // 启动定时发送Ping消息
                    startPingTimer();
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("[" + getExchangeName() + "] 连接已关闭: " + reason + " (code: " + code + ")");
                    stopPingTimer();
                    super.onClose(code, reason, remote);
                    
                    // 使用线程池进行异步重连，避免阻塞
                    new Thread(() -> {
                        try {
                            System.out.println("[" + getExchangeName() + "] 5秒后尝试重新连接...");
                            Thread.sleep(5000);
                            System.out.println("[" + getExchangeName() + "] 正在重新连接...");
                            reconnectBlocking();
                            System.out.println("[" + getExchangeName() + "] 重新连接成功");
                        } catch (Exception e) {
                            System.err.println("[" + getExchangeName() + "] 重连失败: " + e.getMessage());
                            // 如果重连失败，继续尝试
                            onClose(code, reason, remote);
                        }
                    }).start();
                }
                
                @Override
                public void onError(Exception ex) {
                    System.err.println("[" + getExchangeName() + "] 连接错误: " + ex.getMessage());
                    ex.printStackTrace();
                    stopPingTimer();
                    super.onError(ex);
                }
                
                private void startPingTimer() {
                    stopPingTimer();
                    pingTimer = new Timer("Huobi-Ping-Timer", true);
                    pingTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            if (isOpen()) {
                                try {
                                    String pingMsg = "{\"ping\":" + System.currentTimeMillis() + "}";
                                    send(pingMsg);
                                    System.out.println("[" + getExchangeName() + "] 发送Ping...");
                                } catch (Exception e) {
                                    System.err.println("[" + getExchangeName() + "] 发送Ping失败: " + e.getMessage());
                                    // 如果发送Ping失败，尝试重新连接
                                    reconnect();
                                }
                            }
                        }
                    }, 10000, 20000); // 初始延迟10秒，之后每20秒发送一次Ping
                }
                
                private void stopPingTimer() {
                    if (pingTimer != null) {
                        pingTimer.cancel();
                        pingTimer = null;
                    }
                }
            };
            
            // 设置连接超时
            huobiClient.setConnectionLostTimeout(60); // 60秒连接超时
            
            huobiClient.connect();
            
            // 添加关闭钩子，确保程序退出时关闭连接
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (binanceClient != null) {
                    binanceClient.close();
                }
                if (huobiClient != null) {
                    huobiClient.close();
                }
            }));
            
        } catch (Exception e) {
            System.err.println("初始化WebSocket连接时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleBinanceMessage(String message) {
        try {
            // 打印原始消息
            System.out.println("\n[币安] 收到消息: " + message);
            
            JsonNode jsonNode = objectMapper.readTree(message);
            double bestBid = jsonNode.get("b").asDouble();  // 买一价
            double bestAsk = jsonNode.get("a").asDouble();  // 卖一价
            
            updatePrice("币安", bestBid, bestAsk);
            checkArbitrageOpportunity();
        } catch (Exception e) {
            System.err.println("处理币安消息错误: " + e.getMessage());
        }
    }
    
    private String decompressGzip(byte[] compressed) throws Exception {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        GZIPInputStream gzip = new GZIPInputStream(bis);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        byte[] buffer = new byte[1024];
        int len;
        while ((len = gzip.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        
        gzip.close();
        baos.close();
        
        return baos.toString("UTF-8");
    }
    
    private void handleHuobiMessage(byte[] message) {
        try {
            // 先尝试解压GZIP
            String decompressedMessage = decompressGzip(message);
            
            // 打印原始消息
            System.out.println("\n[火币] 收到消息: " + decompressedMessage);
            
            // 处理Pong响应
            if (decompressedMessage.contains("pong")) {
                System.out.println("[火币] 收到Pong响应");
                return;
            }
            
            // 处理订阅响应
            if (decompressedMessage.contains("subbed") || decompressedMessage.contains("err-msg")) {
                System.out.println("[火币] " + decompressedMessage);
                return;
            }
            
            JsonNode jsonNode = objectMapper.readTree(decompressedMessage);
            
            // 处理Ping响应
            if (jsonNode.has("ping")) {
                long pingValue = jsonNode.get("ping").asLong();
                String pongMsg = String.format("{\"pong\":%d}", pingValue);
                huobiClient.send(pongMsg);
                System.out.println("[火币] 响应Ping: " + pongMsg);
                return;
            }
            
            // 处理行情数据
            if (jsonNode.has("ch") && jsonNode.has("tick")) {
                String channel = jsonNode.get("ch").asText();
                if (channel.endsWith(".bbo")) {
                    JsonNode tick = jsonNode.get("tick");
                    if (tick.has("bid") && tick.has("ask")) {
                        double bestBid = tick.get("bid").asDouble();
                        double bestAsk = tick.get("ask").asDouble();
                        
                        updatePrice("火币", bestBid, bestAsk);
                        checkArbitrageOpportunity();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("处理火币消息错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private synchronized void updatePrice(String exchange, double bid, double ask) {
        Map<String, Double> prices = new HashMap<>();
        prices.put("bid", bid);
        prices.put("ask", ask);
        exchangePrices.put(exchange, prices);
    }
    
    private long lastPrintTime = 0;
    private static final long PRINT_INTERVAL = 5000; // 5秒打印一次无套利信息
    
    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private static final long PRICE_EXPIRY_MS = 5000; // 5秒价格过期时间
    
    private synchronized void updatePrice(String exchange, double bid, double ask) {
        Map<String, Double> prices = new HashMap<>();
        prices.put("bid", bid);
        prices.put("ask", ask);
        exchangePrices.put(exchange, prices);
        lastUpdateTime.put(exchange, System.currentTimeMillis());
    }
    
    private String formatTime(long timestamp) {
        return new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date(timestamp));
    }
    
    private synchronized void checkArbitrageOpportunity() {
        if (exchangePrices.size() < 2) return;
        
        String exchange1 = "币安";
        String exchange2 = "火币";
        
        // 检查数据是否过期
        long currentTime = System.currentTimeMillis();
        Long lastUpdate1 = lastUpdateTime.get(exchange1);
        Long lastUpdate2 = lastUpdateTime.get(exchange2);
        
        if (lastUpdate1 == null || lastUpdate2 == null || 
            currentTime - lastUpdate1 > PRICE_EXPIRY_MS || 
            currentTime - lastUpdate2 > PRICE_EXPIRY_MS) {
            return; // 数据已过期，不计算套利机会
        }
        
        Map<String, Double> prices1 = exchangePrices.get(exchange1);
        Map<String, Double> prices2 = exchangePrices.get(exchange2);
        
        if (prices1 == null || prices2 == null) return;
        
        double bid1 = prices1.get("bid");
        double ask1 = prices1.get("ask");
        double bid2 = prices2.get("bid");
        double ask2 = prices2.get("ask");
        
        // 计算套利机会
        double arbitrageBuyAtExchange2SellAtExchange1 = (bid1 - ask2) / ask2 * 100;
        double arbitrageBuyAtExchange1SellAtExchange2 = (bid2 - ask1) / ask1 * 100;
        
        boolean hasArbitrage = false;
        
        // 打印套利机会
        System.out.println("\n=== 实时套利机会 (" + formatTime(currentTime) + ") ===");
        System.out.println(String.format("[%s] 时间: %s  买一价: %.2f  卖一价: %.2f", 
            exchange1, formatTime(lastUpdate1), bid1, ask1));
        System.out.println(String.format("[%s] 时间: %s  买一价: %.2f  卖一价: %.2f", 
            exchange2, formatTime(lastUpdate2), bid2, ask2));
        
        // 套利方向1: 在火币买入，在币安卖出
        if (arbitrageBuyAtExchange2SellAtExchange1 > 0.1) {
            System.out.println(String.format("\n套利机会: 在 %s(%.2f) 买入, 在 %s(%.2f) 卖出, 利润: %.4f%%", 
                    exchange2, ask2, exchange1, bid1, arbitrageBuyAtExchange2SellAtExchange1));
            hasArbitrage = true;
        }
        
        // 套利方向2: 在币安买入，在火币卖出
        if (arbitrageBuyAtExchange1SellAtExchange2 > 0.1) {
            System.out.println(String.format("套利机会: 在 %s(%.2f) 买入, 在 %s(%.2f) 卖出, 利润: %.4f%%", 
                    exchange1, ask1, exchange2, bid2, arbitrageBuyAtExchange1SellAtExchange2));
            hasArbitrage = true;
        }
        
        if (!hasArbitrage) {
            System.out.println("\n当前无显著套利机会，继续监控中...");
        }
        
        // 显示价格差
        double priceDiff = Math.abs(bid1 - bid2);
        double priceDiffPercent = priceDiff / Math.min(bid1, bid2) * 100;
        System.out.println(String.format("\n价格差异: %.4f (%.4f%%)", priceDiff, priceDiffPercent));
        
        System.out.println("==========================================\n");
    }
}
