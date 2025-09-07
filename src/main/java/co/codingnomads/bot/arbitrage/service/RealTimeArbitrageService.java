package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.exchange.ExchangeWebSocketClient;
import co.codingnomads.bot.arbitrage.model.MarketData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
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

/**
 * 实时套利服务
 * 
 * 该服务负责：
 * 1. 连接多个交易所的WebSocket API获取实时行情数据
 * 2. 监控不同交易所之间的价格差异
 * 3. 检测套利机会并保存到数据库
 * 4. 提供实时统计信息和监控功能
 * 
 * 支持的交易所：
 * - 币安 (Binance)
 * - 火币 (Huobi)
 * 
 * @author CodingNomads
 * @version 1.0
 * @since 2024
 */
@Service
public class RealTimeArbitrageService {
    
    // ==================== 日志记录器 ====================
    private static final Logger logger = Logger.getLogger(RealTimeArbitrageService.class);
    
    // ==================== 依赖注入 ====================
    @Autowired
    private MarketDataService marketDataService;
    
    @Autowired
    private ArbitrageService arbitrageService;
    
    // ==================== 控制台输出颜色常量 ====================
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_BOLD = "\u001B[1m";
    
    // ==================== 配置常量 ====================
    /** 监控的交易对 */
    private static final String SYMBOL = "ethusdt";
    
    /** 价格数据过期时间（毫秒） */
    private static final long PRICE_EXPIRY_MS = 5000;
    
    /** 初始最大时间戳差异阈值（毫秒） */
    private static final long INITIAL_MAX_TIMESTAMP_DIFF_MS = 300;
    
    /** 统计信息打印间隔（毫秒） */
    private static final long STATS_PRINT_INTERVAL_MS = 60000;
    
    /** 最小套利利润率（百分比） */
    private static final double MIN_ARBITRAGE_MARGIN = 0.03;
    
    /** 连接超时时间（毫秒） */
    private static final int CONNECTION_TIMEOUT_MS = 60_000;
    
    /** 重连延迟时间（毫秒） */
    private static final int RECONNECT_DELAY_MS = 5000;
    
    /** 时间差样本数量 */
    private static final int TIME_DIFF_SAMPLES = 50;
    
    /** 时间差阈值乘数 */
    private static final double TIME_DIFF_THRESHOLD_MULTIPLIER = 1.5;
    
    // ==================== 状态管理 ====================
    /** JSON解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /** 各交易所价格数据缓存 - Key: 交易所名称, Value: {bid: 买一价, ask: 卖一价} */
    private final Map<String, Map<String, Double>> exchangePrices = new ConcurrentHashMap<>();
    
    /** 各交易所最后更新时间 - Key: 交易所名称, Value: 时间戳 */
    private final Map<String, Long> lastUpdateTime = new ConcurrentHashMap<>();
    
    /** 最近时间差样本列表，用于动态调整阈值 */
    private final List<Long> recentTimeDiffs = new CopyOnWriteArrayList<>();
    
    /** 动态最大时间戳差异阈值（毫秒） */
    private volatile long dynamicMaxTimestampDiffMs = INITIAL_MAX_TIMESTAMP_DIFF_MS;
    
    // ==================== 性能指标 ====================
    /** 套利检查次数计数器 */
    private final AtomicInteger checkCount = new AtomicInteger(0);
    
    /** 跳过的套利机会计数器 */
    private final AtomicInteger skippedOpportunities = new AtomicInteger(0);
    
    /** 处理的套利机会计数器 */
    private final AtomicInteger processedOpportunities = new AtomicInteger(0);
    
    /** 上次打印统计信息的时间 */
    private volatile long lastStatsPrintTime = System.currentTimeMillis();
    
    // ==================== WebSocket客户端 ====================
    /** 币安WebSocket客户端 */
    private ExchangeWebSocketClient binanceClient;
    
    /** 火币WebSocket客户端 */
    private ExchangeWebSocketClient huobiClient;
    
    // ==================== 币安WebSocket客户端实现 ====================
    /**
     * 币安交易所WebSocket客户端
     * 
     * 负责：
     * 1. 连接到币安WebSocket API
     * 2. 处理实时行情数据
     * 3. 解析价格信息并更新本地缓存
     * 4. 触发套利机会检查
     */
    private class BinanceWebSocketClient extends ExchangeWebSocketClient {
        
        /**
         * 构造函数
         * 
         * @param serverUri 币安WebSocket服务器URI
         */
        public BinanceWebSocketClient(URI serverUri) {
            // 关键修复：super() 传入一个不捕获外部实例的空处理器，避免在构造期间引用外部 this
            super("币安", serverUri, msg -> {});
            // 设置连接空闲超时时间为60秒
            this.setConnectionLostTimeout(60);
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
                logError("处理文本消息时出错", e);
            }
        }

        /**
         * 处理二进制消息（GZIP压缩）
         * 
         * @param bytes 接收到的二进制数据
         */
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
                logError("处理二进制消息时出错", e);
            }
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
                
                updatePrice("币安", bestBid, bestAsk);
                checkArbitrageOpportunity();
            } catch (Exception e) {
                logError("处理消息时出错", e);
            }
        }

        /**
         * 连接建立时的回调
         * 
         * @param handshakedata 握手数据
         */
        @Override
        public void onOpen(ServerHandshake handshakedata) {
            super.onOpen(handshakedata);
            logger.info("[Binance] 连接已建立，开始接收数据...");
        }
        
        /**
         * 记录错误日志
         * 
         * @param message 错误消息
         * @param e 异常对象
         */
        private void logError(String message, Exception e) {
            logger.error("[Binance] " + message + ": " + e.getMessage(), e);
        }
    }

    // ==================== 火币WebSocket客户端实现 ====================
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
    private class HuobiWebSocketClient extends ExchangeWebSocketClient {
        /** Ping定时器 */
        private Timer pingTimer;
        
        /** Ping定时器同步锁 */
        private final Object pingLock = new Object();

        /**
         * 构造函数
         * 
         * @param serverUri 火币WebSocket服务器URI
         */
        public HuobiWebSocketClient(URI serverUri) {
            // 关键修复：避免在 super() 中捕获外部 this
            super("火币", serverUri, msg -> {});
            // 设置连接超时时间为60秒
            this.setConnectionLostTimeout(60);
        }

        /**
         * 处理文本消息
         * 
         * @param message 接收到的文本消息
         */
        @Override
        public void onMessage(String message) {
            logger.debug("[Huobi] 收到文本消息: " + message);
        }

        /**
         * 处理二进制消息（GZIP压缩）
         * 
         * @param bytes 接收到的二进制数据
         */
        @Override
        public void onMessage(ByteBuffer bytes) {
            try {
                byte[] byteArray = new byte[bytes.remaining()];
                bytes.get(byteArray);
                handleHuobiMessage(byteArray);
            } catch (Exception e) {
                logError("处理二进制消息时出错", e);
            }
        }

        /**
         * 连接建立时的回调
         * 
         * @param handshakedata 握手数据
         */
        @Override
        public void onOpen(ServerHandshake handshakedata) {
            super.onOpen(handshakedata);
            logger.info("[Huobi] 连接已建立，发送订阅请求...");
            
            // 发送订阅消息
            sendSubscriptionMessage();
            
            // 启动Ping定时器
            startPingTimer();
        }
        
        /**
         * 发送订阅消息
         */
        private void sendSubscriptionMessage() {
            String subscribeMsg = String.format("{\"sub\":\"market.%s.bbo\",\"id\":\"%d\"}", 
                SYMBOL, System.currentTimeMillis());
            this.send(subscribeMsg);
        }

        /**
         * 连接关闭时的回调
         * 
         * @param code 关闭代码
         * @param reason 关闭原因
         * @param remote 是否由远程端关闭
         */
        @Override
        public void onClose(int code, String reason, boolean remote) {
            logger.warn("[Huobi] 连接已关闭: " + reason + " (code: " + code + ")");
            stopPingTimer();
            super.onClose(code, reason, remote);
            
            // 异步重连
            scheduleReconnect();
        }

        /**
         * 连接错误时的回调
         * 
         * @param ex 异常对象
         */
        @Override
        public void onError(Exception ex) {
            logError("连接错误", ex);
            stopPingTimer();
            super.onError(ex);
        }
        
        /**
         * 调度重连任务
         */
        private void scheduleReconnect() {
            new Thread(() -> {
                try {
                    logger.info("[Huobi] 5秒后尝试重新连接...");
                    Thread.sleep(5000);
                    logger.info("[Huobi] 正在重新连接...");
                    this.reconnectBlocking();
                    logger.info("[Huobi] 重新连接成功");
                } catch (Exception e) {
                    logger.error("[Huobi] 重连失败: " + e.getMessage(), e);
                    this.onClose(0, "重连失败", false);
                }
            }).start();
        }
        
        /**
         * 记录错误日志
         * 
         * @param message 错误消息
         * @param e 异常对象
         */
        private void logError(String message, Exception e) {
            logger.error("[Huobi] " + message + ": " + e.getMessage(), e);
        }

        /**
         * 启动Ping定时器
         * 每20秒发送一次Ping消息以保持连接活跃
         */
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
                                logError("发送Ping失败", e);
                                reconnect();
                            }
                        }
                    }
                }, 10000, 20000); // 10秒后开始，每20秒执行一次
            }
        }

        /**
         * 停止Ping定时器
         */
        private void stopPingTimer() {
            synchronized (pingLock) {
                if (pingTimer != null) {
                    pingTimer.cancel();
                    pingTimer = null;
                }
            }
        }
        
        /**
         * 发送Ping消息
         */
        private void sendPingMessage() {
            String pingMsg = "{\"ping\":" + System.currentTimeMillis() + "}";
            send(pingMsg);
            logger.debug("[Huobi] 发送Ping...");
        }
    }

    // ==================== 服务生命周期管理 ====================
    
    /**
     * 服务初始化方法
     * 在Spring容器创建Bean后自动调用
     */
    @javax.annotation.PostConstruct
    public void init() {
        try {
            initializeWebSocketConnections();
            registerShutdownHook();
            logServiceStartup();
        } catch (Exception e) {
            logError("初始化WebSocket连接时出错", e);
        }
    }
    
    /**
     * 初始化WebSocket连接
     * 
     * @throws Exception 连接初始化异常
     */
    private void initializeWebSocketConnections() throws Exception {
        // 初始化币安连接
        String binanceWsUrl = "wss://stream.binance.com:9443/ws/" + SYMBOL + "@ticker";
        binanceClient = new BinanceWebSocketClient(new URI(binanceWsUrl));
        binanceClient.connect();
        
        // 初始化火币连接
        String huobiWsUrl = "wss://api.huobi.pro/ws";
        huobiClient = new HuobiWebSocketClient(new URI(huobiWsUrl));
        huobiClient.connect();
    }
    
    /**
     * 注册JVM关闭钩子
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
    }
    
    /**
     * 记录服务启动日志
     */
    private void logServiceStartup() {
        logger.info("[系统] 套利服务已启动，开始监控 " + SYMBOL.toUpperCase() + " 交易对...");
    }
    
    /**
     * 服务清理方法
     * 在Spring容器销毁Bean前自动调用
     */
    @PreDestroy
    public void cleanup() {
        logger.info("[系统] 正在关闭套利服务...");
        try {
            closeWebSocketConnections();
            printFinalStats();
        } catch (Exception e) {
            logError("关闭WebSocket连接时出错", e);
        }
    }
    
    /**
     * 关闭WebSocket连接
     */
    private void closeWebSocketConnections() {
        if (binanceClient != null) {
            binanceClient.close();
            logger.info("[币安] 连接已关闭");
        }
        if (huobiClient != null) {
            huobiClient.close();
            logger.info("[火币] 连接已关闭");
        }
    }
    
    /**
     * 打印最终统计信息
     */
    private void printFinalStats() {
        printStats();
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 解压GZIP数据
     * 
     * @param compressed 压缩的字节数组
     * @return 解压后的字符串，如果解压失败返回空字符串
     */
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
            logError("解压GZIP数据时出错", e);
            return "";
        }
    }
    
    /**
     * 记录错误日志（通用方法）
     * 
     * @param message 错误消息
     * @param e 异常对象
     */
    private void logError(String message, Exception e) {
        System.err.println(ANSI_RED + "[系统] " + message + ": " + e.getMessage() + ANSI_RESET);
        e.printStackTrace();
    }
    
    // ==================== 消息处理方法 ====================
    
    /**
     * 处理火币消息
     * 
     * @param message 压缩的消息字节数组
     */
    private void handleHuobiMessage(byte[] message) {
        try {
            String decompressedMessage = decompressGzip(message);
            
            if (decompressedMessage == null || decompressedMessage.isEmpty()) {
                return;
            }
            
            // 处理不同类型的消息
            if (isPongMessage(decompressedMessage)) {
                handlePongMessage();
                return;
            }
            
            if (isSubscriptionMessage(decompressedMessage)) {
                handleSubscriptionMessage(decompressedMessage);
                return;
            }
            
            // 处理行情数据
            processMarketDataMessage(decompressedMessage);
            
        } catch (Exception e) {
            logError("[Huobi] 处理消息时出错", e);
        }
    }
    
    /**
     * 判断是否为Pong消息
     * 
     * @param message 消息内容
     * @return 是否为Pong消息
     */
    private boolean isPongMessage(String message) {
        return message.contains("pong");
    }
    
    /**
     * 判断是否为订阅响应消息
     * 
     * @param message 消息内容
     * @return 是否为订阅响应消息
     */
    private boolean isSubscriptionMessage(String message) {
        return message.contains("subbed") || message.contains("err-msg");
    }
    
    /**
     * 处理Pong消息
     */
    private void handlePongMessage() {
        logger.debug("[火币] 收到Pong响应");
    }
    
    /**
     * 处理订阅响应消息
     * 
     * @param message 消息内容
     */
    private void handleSubscriptionMessage(String message) {
        logger.info("[火币] " + message);
    }
    
    /**
     * 处理行情数据消息
     * 
     * @param message 消息内容
     */
    private void processMarketDataMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            
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
            logError("处理行情数据消息时出错", e);
        }
    }

    // ==================== 套利检测方法 ====================
    
    /**
     * 检查套利机会
     * 当有至少两个交易所的价格数据时，检查是否存在套利机会
     */
    private void checkArbitrageOpportunity() {
        if (exchangePrices.size() < 2) {
            return;
        }
        
        String exchange1 = "币安";
        String exchange2 = "火币";
        
        // 获取两个交易所的最新价格
        Map<String, Double> prices1 = exchangePrices.get(exchange1);
        Map<String, Double> prices2 = exchangePrices.get(exchange2);
        
        if (prices1 == null || prices2 == null) {
            return;
        }
        
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

    /**
     * 计算套利机会
     * 
     * @param exchange1 第一个交易所名称
     * @param exchange2 第二个交易所名称
     * @param bid1 第一个交易所买一价
     * @param ask1 第一个交易所卖一价
     * @param bid2 第二个交易所买一价
     * @param ask2 第二个交易所卖一价
     */
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

    // ==================== 价格更新方法 ====================
    
    /**
     * 更新交易所价格数据
     * 
     * @param exchange 交易所名称
     * @param bid 买一价
     * @param ask 卖一价
     */
    private void updatePrice(String exchange, double bid, double ask) {
        long currentTime = System.currentTimeMillis();
        
        // 更新本地价格缓存
        updateLocalPriceCache(exchange, bid, ask, currentTime);
        
        // 保存到数据库
        saveMarketDataToDatabase(exchange, bid, ask, currentTime);
        
        // 检查时间同步性
        checkTimeSynchronization();
    }
    
    /**
     * 更新本地价格缓存
     * 
     * @param exchange 交易所名称
     * @param bid 买一价
     * @param ask 卖一价
     * @param currentTime 当前时间戳
     */
    private void updateLocalPriceCache(String exchange, double bid, double ask, long currentTime) {
        Map<String, Double> prices = new HashMap<>();
        prices.put("bid", bid);
        prices.put("ask", ask);
        exchangePrices.put(exchange, prices);
        lastUpdateTime.put(exchange, currentTime);
    }
    
    /**
     * 保存行情数据到数据库
     * 
     * @param exchange 交易所名称
     * @param bid 买一价
     * @param ask 卖一价
     * @param currentTime 当前时间戳
     */
    private void saveMarketDataToDatabase(String exchange, double bid, double ask, long currentTime) {
        MarketData marketData = new MarketData(
            exchange, 
            SYMBOL.toUpperCase(), 
            BigDecimal.valueOf(bid), 
            BigDecimal.valueOf(ask), 
            currentTime
        );
        marketDataService.saveMarketData(marketData);
    }
    
    /**
     * 检查时间同步性
     */
    private void checkTimeSynchronization() {
        if (exchangePrices.size() >= 2) {
            long timeDiff = calculateTimeDifference();
            updateDynamicThreshold(timeDiff);
            
            if (timeDiff > dynamicMaxTimestampDiffMs) {
                logTimeSyncWarning(timeDiff);
            }
        }
    }
    
    /**
     * 计算交易所间的时间差
     * 
     * @return 最大时间差（毫秒）
     */
    private long calculateTimeDifference() {
        List<Long> updateTimes = new ArrayList<>(lastUpdateTime.values());
        long maxTime = Collections.max(updateTimes);
        long minTime = Collections.min(updateTimes);
        return maxTime - minTime;
    }
    
    /**
     * 记录时间同步警告
     * 
     * @param timeDiff 时间差
     */
    private void logTimeSyncWarning(long timeDiff) {
        String timeInfo = getExchangeTimeInfo();
        logger.warn(String.format("[警告] 交易所数据时间差过大: %dms (阈值: %dms)%s", 
            timeDiff, dynamicMaxTimestampDiffMs, timeInfo));
    }

    // ==================== 动态阈值管理 ====================
    
    /**
     * 更新动态时间差阈值
     * 基于历史时间差数据动态调整阈值，以适应网络波动
     * 
     * @param timeDiff 当前时间差
     */
    private void updateDynamicThreshold(long timeDiff) {
        if (timeDiff < 0) {
            return; // 无效的时间差
        }
        
        synchronized (recentTimeDiffs) {
            addTimeDiffToHistory(timeDiff);
            maintainSampleSize();
            
            if (!recentTimeDiffs.isEmpty()) {
                double avgDiff = calculateAverageTimeDiff();
                double stdDev = calculateStandardDeviation(avgDiff);
                double dynamicMultiplier = calculateDynamicMultiplier(avgDiff, stdDev);
                
                updateThreshold(avgDiff, dynamicMultiplier);
                logThresholdAdjustment(avgDiff, stdDev, dynamicMultiplier);
            }
        }
    }
    
    /**
     * 添加时间差到历史记录
     * 
     * @param timeDiff 时间差
     */
    private void addTimeDiffToHistory(long timeDiff) {
        recentTimeDiffs.add(timeDiff);
    }
    
    /**
     * 维护样本大小
     */
    private void maintainSampleSize() {
        while (recentTimeDiffs.size() > TIME_DIFF_SAMPLES) {
            recentTimeDiffs.remove(0);
        }
    }
    
    /**
     * 计算平均时间差
     * 
     * @return 平均时间差
     */
    private double calculateAverageTimeDiff() {
        return recentTimeDiffs.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(INITIAL_MAX_TIMESTAMP_DIFF_MS);
    }
    
    /**
     * 计算标准差
     * 
     * @param avgDiff 平均时间差
     * @return 标准差
     */
    private double calculateStandardDeviation(double avgDiff) {
        return Math.sqrt(recentTimeDiffs.stream()
            .mapToDouble(d -> Math.pow(d - avgDiff, 2))
            .average()
            .orElse(0));
    }
    
    /**
     * 计算动态乘数
     * 
     * @param avgDiff 平均时间差
     * @param stdDev 标准差
     * @return 动态乘数
     */
    private double calculateDynamicMultiplier(double avgDiff, double stdDev) {
        double dynamicMultiplier = TIME_DIFF_THRESHOLD_MULTIPLIER * (1 + stdDev / (avgDiff + 1));
        return Math.min(dynamicMultiplier, 3.0); // 设置最大乘数限制
    }
    
    /**
     * 更新阈值
     * 
     * @param avgDiff 平均时间差
     * @param dynamicMultiplier 动态乘数
     */
    private void updateThreshold(double avgDiff, double dynamicMultiplier) {
        dynamicMaxTimestampDiffMs = Math.max(
            INITIAL_MAX_TIMESTAMP_DIFF_MS,
            (long)(avgDiff * dynamicMultiplier)
        );
    }
    
    /**
     * 记录阈值调整信息
     * 
     * @param avgDiff 平均时间差
     * @param stdDev 标准差
     * @param dynamicMultiplier 动态乘数
     */
    private void logThresholdAdjustment(double avgDiff, double stdDev, double dynamicMultiplier) {
        if (checkCount.get() % 100 == 0) {
            logger.debug(String.format("[时间同步] 平均时差: %.2fms | 标准差: %.2f | 动态乘数: %.2f | 新阈值: %dms",
                avgDiff, stdDev, dynamicMultiplier, dynamicMaxTimestampDiffMs));
        }
    }

    // ==================== 统计和监控方法 ====================
    
    /**
     * 打印统计信息
     * 定期打印系统运行统计信息
     */
    private void printStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsPrintTime > STATS_PRINT_INTERVAL_MS) {
            printStatisticsInfo();
            lastStatsPrintTime = now;
        }
    }
    
    /**
     * 打印统计信息详情
     */
    private void printStatisticsInfo() {
        int totalChecks = checkCount.get();
        int skipped = skippedOpportunities.get();
        int processed = processedOpportunities.get();
        double skipRate = totalChecks > 0 ? (double)skipped / totalChecks * 100 : 0;
        
        logger.info(String.format("[统计] 检查次数: %d | 处理: %d | 跳过: %d (%.1f%%) | 动态时间差阈值: %dms",
            totalChecks, processed, skipped, skipRate, dynamicMaxTimestampDiffMs));
    }
    
    // ==================== 时间工具方法 ====================
    
    /**
     * 格式化时间戳
     * 
     * @param timestamp 时间戳
     * @return 格式化后的时间字符串
     */
    private String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(timestamp));
    }
    
    /**
     * 获取两个交易所间的时间差
     * 
     * @param exchange1 第一个交易所
     * @param exchange2 第二个交易所
     * @return 时间差（毫秒）
     */
    private long getTimeDiff(String exchange1, String exchange2) {
        Long time1 = lastUpdateTime.get(exchange1);
        Long time2 = lastUpdateTime.get(exchange2);
        
        if (time1 == null || time2 == null) {
            return Long.MAX_VALUE;
        }
        
        return Math.abs(time1 - time2);
    }
    
    /**
     * 获取交易所时间信息（东八区时间）
     * 
     * @return 格式化的时间信息字符串
     */
    private String getExchangeTimeInfo() {
        StringBuilder timeInfo = new StringBuilder();
        timeInfo.append("\n");
        
        // 当前时间（东八区）
        long currentTime = System.currentTimeMillis();
        timeInfo.append(String.format("当前时间: %s", formatToBeijingTime(currentTime)));
        
        // 各个交易所的时间
        for (Map.Entry<String, Long> entry : lastUpdateTime.entrySet()) {
            String exchange = entry.getKey();
            Long timestamp = entry.getValue();
            timeInfo.append(String.format("\n%s时间: %s", exchange, formatToBeijingTime(timestamp)));
        }
        
        return timeInfo.toString();
    }
    
    /**
     * 将时间戳格式化为东八区时间
     * 
     * @param timestamp 时间戳
     * @return 东八区时间字符串
     */
    private String formatToBeijingTime(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.ZonedDateTime beijingTime = instant.atZone(java.time.ZoneId.of("Asia/Shanghai"));
        return beijingTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
    
    // ==================== 公共接口方法 ====================
    
    /**
     * 启动套利监控服务
     * 公共接口方法，供外部调用启动监控
     */
    public void startArbitrageMonitoring() {
        logger.info("[系统] 套利监控服务已启动，等待WebSocket连接...");
    }
}
