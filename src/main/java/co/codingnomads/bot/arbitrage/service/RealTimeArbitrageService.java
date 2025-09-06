package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.exchange.ExchangeWebSocketClient;
import co.codingnomads.bot.arbitrage.model.MarketData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.zip.GZIPInputStream;
import java.util.Date;
 import java.nio.ByteBuffer;

@Service
public class RealTimeArbitrageService {
    
    @Autowired
    private MarketDataService marketDataService;
    
    @Autowired
    private ArbitrageService arbitrageService;
    
    // ANSI color codes for console output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_BOLD = "\u001B[1m";
    
    // Configuration constants
    private static final String SYMBOL = "ethusdt";
    private static final long PRICE_EXPIRY_MS = 5000; // 5 seconds
    private static final long INITIAL_MAX_TIMESTAMP_DIFF_MS = 300; // 300ms
    private static final long STATS_PRINT_INTERVAL_MS = 60000; // 1 minute
    private static final double MIN_ARBITRAGE_MARGIN = 0.03; // 0.03%
    private static final int CONNECTION_TIMEOUT_MS = 60_000; // 60 seconds
    private static final int RECONNECT_DELAY_MS = 5000; // 5 seconds
    private static final int TIME_DIFF_SAMPLES = 50;
    private static final double TIME_DIFF_THRESHOLD_MULTIPLIER = 1.5;
    
    // State management
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, Double>> exchangePrices = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private final List<Long> recentTimeDiffs = new CopyOnWriteArrayList<>();
    private volatile long dynamicMaxTimestampDiffMs = INITIAL_MAX_TIMESTAMP_DIFF_MS;
    
    // Performance metrics
    private final AtomicInteger checkCount = new AtomicInteger(0);
    private final AtomicInteger skippedOpportunities = new AtomicInteger(0);
    private final AtomicInteger processedOpportunities = new AtomicInteger(0);
    private volatile long lastStatsPrintTime = System.currentTimeMillis();
    
    // WebSocket clients
    private ExchangeWebSocketClient binanceClient;
    private ExchangeWebSocketClient huobiClient;
    
    // WebSocket client implementation for Binance
    private class BinanceWebSocketClient extends ExchangeWebSocketClient {
        public BinanceWebSocketClient(URI serverUri) {
            // 关键修复：super() 传入一个不捕获外部实例的空处理器，避免在构造期间引用外部 this
            super("币安", serverUri, msg -> {});
            // 连接空闲超时
            this.setConnectionLostTimeout(60);
        }

        // 覆盖消息回调，在这里安全地访问外部类方法
        @Override
        public void onMessage(String message) {
            try {
                handleBinanceMessage(message);
            } catch (Exception e) {
                System.err.println(ANSI_RED + "[Binance] 处理文本消息时出错: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                byte[] byteArray = new byte[bytes.remaining()];
                bytes.get(byteArray);
                String decompressed = decompressGzip(byteArray);
                if (!decompressed.isEmpty()) {
                    handleBinanceMessage(decompressed);
                }
            } catch (Exception e) {
                System.err.println(ANSI_RED + "[Binance] 处理二进制消息时出错: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        }
        
        private void handleBinanceMessage(String message) {
            try {
                JsonNode jsonNode = objectMapper.readTree(message);
                double bestBid = jsonNode.get("b").asDouble();  // 买一价
                double bestAsk = jsonNode.get("a").asDouble();  // 卖一价
                
                updatePrice("币安", bestBid, bestAsk);
                checkArbitrageOpportunity();
            } catch (Exception e) {
                System.err.println(ANSI_RED + "[Binance] 处理消息时出错: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            super.onOpen(handshakedata);
            System.out.println(ANSI_GREEN + "[Binance] 连接已建立，开始接收数据..." + ANSI_RESET);
        }
    }

    // WebSocket client implementation for Huobi
    private class HuobiWebSocketClient extends ExchangeWebSocketClient {
        private Timer pingTimer;
        private final Object pingLock = new Object();

        public HuobiWebSocketClient(URI serverUri) {
            // 关键修复：避免在 super() 中捕获外部 this
            super("火币", serverUri, msg -> {});
            // Set connection timeout
            this.setConnectionLostTimeout(60); // 60 seconds
        }

        // 覆盖消息回调，安全处理文本/二进制
        @Override
        public void onMessage(String message) {
            System.out.println(ANSI_CYAN + "[Huobi] 收到文本消息: " + message + ANSI_RESET);
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                byte[] byteArray = new byte[bytes.remaining()];
                bytes.get(byteArray);
                handleHuobiMessage(byteArray);
            } catch (Exception e) {
                System.err.println(ANSI_RED + "[Huobi] 处理二进制消息时出错: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            super.onOpen(handshakedata);
            System.out.println(ANSI_GREEN + "[Huobi] 连接已建立，发送订阅请求..." + ANSI_RESET);
            
            // 发送订阅消息
            String subscribeMsg = String.format("{\"sub\":\"market.%s.bbo\",\"id\":\"%d\"}", 
                SYMBOL, System.currentTimeMillis());
            this.send(subscribeMsg);
            
            // 启动Ping定时器
            startPingTimer();
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println(ANSI_YELLOW + "[Huobi] 连接已关闭: " + reason + " (code: " + code + ")" + ANSI_RESET);
            stopPingTimer();
            super.onClose(code, reason, remote);
            
            // 异步重连
            new Thread(() -> {
                try {
                    System.out.println(ANSI_CYAN + "[Huobi] 5秒后尝试重新连接..." + ANSI_RESET);
                    Thread.sleep(5000);
                    System.out.println(ANSI_CYAN + "[Huobi] 正在重新连接..." + ANSI_RESET);
                    this.reconnectBlocking();
                    System.out.println(ANSI_GREEN + "[Huobi] 重新连接成功" + ANSI_RESET);
                } catch (Exception e) {
                    System.err.println(ANSI_RED + "[Huobi] 重连失败: " + e.getMessage() + ANSI_RESET);
                    this.onClose(code, reason, remote);
                }
            }).start();
        }

        @Override
        public void onError(Exception ex) {
            System.err.println(ANSI_RED + "[Huobi] 连接错误: " + ex.getMessage() + ANSI_RESET);
            ex.printStackTrace();
            stopPingTimer();
            super.onError(ex);
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
                                String pingMsg = "{\"ping\":" + System.currentTimeMillis() + "}";
                                send(pingMsg);
                                System.out.println(ANSI_CYAN + "[Huobi] 发送Ping..." + ANSI_RESET);
                            } catch (Exception e) {
                                System.err.println(ANSI_RED + "[Huobi] 发送Ping失败: " + e.getMessage() + ANSI_RESET);
                                reconnect();
                            }
                        }
                    }
                }, 10000, 20000);
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
    }

    @javax.annotation.PostConstruct
    public void init() {
        try {
            String binanceWsUrl = "wss://stream.binance.com:9443/ws/" + SYMBOL + "@ticker";
            binanceClient = new BinanceWebSocketClient(new URI(binanceWsUrl));
            binanceClient.connect();
            
            String huobiWsUrl = "wss://api.huobi.pro/ws";
            huobiClient = new HuobiWebSocketClient(new URI(huobiWsUrl));
            huobiClient.connect();
            
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
            
            System.out.println(ANSI_GREEN + "[系统] 套利服务已启动，开始监控 " + SYMBOL.toUpperCase() + " 交易对..." + ANSI_RESET);
        } catch (Exception e) {
            System.err.println(ANSI_RED + "[系统] 初始化WebSocket连接时出错: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }
    
    @PreDestroy
    public void cleanup() {
        System.out.println(ANSI_YELLOW + "[系统] 正在关闭套利服务..." + ANSI_RESET);
        try {
            if (binanceClient != null) {
                binanceClient.close();
                System.out.println(ANSI_YELLOW + "[币安] 连接已关闭" + ANSI_RESET);
            }
            if (huobiClient != null) {
                huobiClient.close();
                System.out.println(ANSI_YELLOW + "[火币] 连接已关闭" + ANSI_RESET);
            }
            
            // 打印最终统计信息
            printStats();
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "[系统] 关闭WebSocket连接时出错: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }
    
    private String decompressGzip(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return "";
        }
        
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipIn = new GZIPInputStream(bis);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            System.err.println(ANSI_RED + "解压GZIP数据时出错: " + e.getMessage() + ANSI_RESET);
            return "";
        }
    }
    
    private void handleHuobiMessage(byte[] message) {
        try {
            // 先尝试解压GZIP
            String decompressedMessage = decompressGzip(message);
            
            // 处理解压后的消息
            if (decompressedMessage == null || decompressedMessage.isEmpty()) {
                return;
            }
            
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
            
            // 解析JSON
            JsonNode jsonNode = objectMapper.readTree(decompressedMessage);
            
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
            System.err.println(ANSI_RED + "[Huobi] 处理消息时出错: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    private void checkArbitrageOpportunity() {
        if (exchangePrices.size() < 2) return;
        
        String exchange1 = "币安";
        String exchange2 = "火币";
        
        // 获取两个交易所的最新价格
        Map<String, Double> prices1 = exchangePrices.get(exchange1);
        Map<String, Double> prices2 = exchangePrices.get(exchange2);
        
        if (prices1 == null || prices2 == null) return;
        
        double bid1 = prices1.get("bid");
        double ask1 = prices1.get("ask");
        double bid2 = prices2.get("bid");
        double ask2 = prices2.get("ask");
        
        // 计算套利机会
        calculateArbitrage(exchange1, exchange2, bid1, ask1, bid2, ask2);
        
        // 更新检查计数
        checkCount.incrementAndGet();
        
        // 定期打印统计信息
        printStats();
    }

    private void calculateArbitrage(String exchange1, String exchange2, 
                                  double bid1, double ask1, double bid2, double ask2) {
        // 使用ArbitrageService进行套利检测
        arbitrageService.checkArbitrageOpportunity(
            SYMBOL.toUpperCase(),
            exchange1, exchange2,
            BigDecimal.valueOf(bid1), BigDecimal.valueOf(ask1),
            BigDecimal.valueOf(bid2), BigDecimal.valueOf(ask2),
            MIN_ARBITRAGE_MARGIN
        );
        
        // 更新统计信息
        processedOpportunities.incrementAndGet();
    }

    private void updatePrice(String exchange, double bid, double ask) {
        long currentTime = System.currentTimeMillis();
        
        // 更新价格数据
        Map<String, Double> prices = new HashMap<>();
        prices.put("bid", bid);
        prices.put("ask", ask);
        exchangePrices.put(exchange, prices);
        lastUpdateTime.put(exchange, currentTime);
        
        // 保存到数据库
        MarketData marketData = new MarketData(
            exchange, 
            SYMBOL.toUpperCase(), 
            BigDecimal.valueOf(bid), 
            BigDecimal.valueOf(ask), 
            currentTime
        );
        marketDataService.saveMarketData(marketData);
        
        // 如果有至少两个交易所的数据，检查时间差
        if (exchangePrices.size() >= 2) {
            // 获取所有交易所的更新时间
            List<Long> updateTimes = new ArrayList<>(lastUpdateTime.values());
            long maxTime = Collections.max(updateTimes);
            long minTime = Collections.min(updateTimes);
            long timeDiff = maxTime - minTime;
            
            // 更新动态阈值
            updateDynamicThreshold(timeDiff);
            
            // 如果时间差超过动态阈值，记录警告
            if (timeDiff > dynamicMaxTimestampDiffMs) {
                System.out.println(ANSI_YELLOW + String.format("[警告] 交易所数据时间差过大: %dms (阈值: %dms)", 
                    timeDiff, dynamicMaxTimestampDiffMs) + ANSI_RESET);
            }
        }
    }

    private void updateDynamicThreshold(long timeDiff) {
        if (timeDiff < 0) return; // invalid time difference
        
        synchronized (recentTimeDiffs) {
            // add current time difference to history
            recentTimeDiffs.add(timeDiff);
            
            // keep recent N samples
            while (recentTimeDiffs.size() > TIME_DIFF_SAMPLES) {
                recentTimeDiffs.remove(0);
            }
            
            if (!recentTimeDiffs.isEmpty()) {
                // calculate moving average time difference
                double avgDiff = recentTimeDiffs.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(INITIAL_MAX_TIMESTAMP_DIFF_MS);
                
                // calculate standard deviation for dynamic adjustment
                double stdDev = Math.sqrt(recentTimeDiffs.stream()
                    .mapToDouble(d -> Math.pow(d - avgDiff, 2))
                    .average()
                    .orElse(0));
                
                // dynamically adjust threshold considering network fluctuations
                double dynamicMultiplier = TIME_DIFF_THRESHOLD_MULTIPLIER * (1 + stdDev / (avgDiff + 1));
                dynamicMultiplier = Math.min(dynamicMultiplier, 3.0); // set maximum multiplier limit
                
                // update dynamic threshold, ensuring it's not lower than the initial value
                dynamicMaxTimestampDiffMs = Math.max(
                    INITIAL_MAX_TIMESTAMP_DIFF_MS,
                    (long)(avgDiff * dynamicMultiplier)
                );
                
                // log threshold adjustment information (for debugging)
                if (checkCount.get() % 100 == 0) {
                    System.out.println(String.format("[%s时间同步%s] 平均时差: %.2fms | 标准差: %.2f | 动态乘数: %.2f | 新阈值: %dms",
                        ANSI_CYAN, ANSI_RESET, avgDiff, stdDev, dynamicMultiplier, dynamicMaxTimestampDiffMs));
                }
            }
        }
    }

    private void printStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsPrintTime > STATS_PRINT_INTERVAL_MS) {
            int totalChecks = checkCount.get();
            int skipped = skippedOpportunities.get();
            int processed = processedOpportunities.get();
            double skipRate = totalChecks > 0 ? (double)skipped / totalChecks * 100 : 0;
            
            System.out.println(String.format("\n%s[统计] 检查次数: %d | 处理: %d | 跳过: %d (%.1f%%) | 动态时间差阈值: %dms%s",
                ANSI_PURPLE, totalChecks, processed, skipped, skipRate, dynamicMaxTimestampDiffMs, ANSI_RESET));
                
            lastStatsPrintTime = now;
        }
    }
    
    private String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(timestamp));
    }
    
    private long getTimeDiff(String exchange1, String exchange2) {
        Long time1 = lastUpdateTime.get(exchange1);
        Long time2 = lastUpdateTime.get(exchange2);
        
        if (time1 == null || time2 == null) {
            return Long.MAX_VALUE;
        }
        
        return Math.abs(time1 - time2);
    }
    
    /**
     * 启动套利监控服务
     */
    public void startArbitrageMonitoring() {
        System.out.println(ANSI_GREEN + "[系统] 套利监控服务已启动，等待WebSocket连接..." + ANSI_RESET);
    }
}
