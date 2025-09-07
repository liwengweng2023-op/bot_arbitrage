package co.codingnomads.bot.arbitrage.config;

import org.springframework.context.annotation.Configuration;

/**
 * 套利系统配置类
 * 包含所有套利相关的配置常量和设置
 */
@Configuration
public class ArbitrageConfig {
    // ==================== 交易配置 ====================
    /** 监控的交易对 */
    public static final String SYMBOL = "ethusdt";
    
    /** 最小套利利润率（百分比） */
    public static final double MIN_ARBITRAGE_MARGIN = 0.03;
    
    // ==================== 时间配置 ====================
    /** 价格数据过期时间（毫秒） */
    public static final long PRICE_EXPIRY_MS = 5000;
    
    /** 初始最大时间戳差异阈值（毫秒） */
    public static final long INITIAL_MAX_TIMESTAMP_DIFF_MS = 300;
    
    /** 统计信息打印间隔（毫秒） */
    public static final long STATS_PRINT_INTERVAL_MS = 60000;
    
    /** 连接超时时间（毫秒） */
    public static final int CONNECTION_TIMEOUT_MS = 60_000;
    
    /** 重连延迟时间（毫秒） */
    public static final int RECONNECT_DELAY_MS = 5000;
    
    // ==================== 性能配置 ====================
    /** 时间差样本数量 */
    public static final int TIME_DIFF_SAMPLES = 50;
    
    /** 时间差阈值乘数 */
    public static final double TIME_DIFF_THRESHOLD_MULTIPLIER = 1.5;
    
    // ==================== WebSocket配置 ====================
    /** 币安WebSocket URL */
    public static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/ws/" + SYMBOL + "@ticker";
    
    /** 火币WebSocket URL */
    public static final String HUOBI_WS_URL = "wss://api.huobi.pro/ws";

    // ==================== 交易所名称 ====================
    public static final String BINANCE_EXCHANGE_NAME = "Binance";
    public static final String HUOBI_EXCHANGE_NAME = "Huobi";
    
    // ==================== 控制台输出颜色 ====================
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_BOLD = "\u001B[1m";
}