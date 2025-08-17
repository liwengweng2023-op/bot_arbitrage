package co.codingnomads.bot.arbitrage.service.websocket;

import info.bitrich.xchangestream.binance.BinanceStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
// import info.bitrich.xchangestream.huobi.HuobiStreamingExchange; // 火币暂不支持
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于XChange-stream的WebSocket套利监测服务
 * 目前支持币安实时价格监控，火币支持待添加
 */
@Service
public class StreamingArbitrageService {
    
    private static final Logger LOG = LoggerFactory.getLogger(StreamingArbitrageService.class);
    
    private StreamingExchange binanceExchange;
    private StreamingExchange huobiExchange;
    
    private Disposable binanceTickerSubscription;
    private Disposable huobiTickerSubscription;
    
    private final ConcurrentHashMap<String, Ticker> latestTickers = new ConcurrentHashMap<>();
    
    private volatile boolean isRunning = false;
    
    // 套利阈值 (0.5%)
    private final BigDecimal ARBITRAGE_THRESHOLD = new BigDecimal("0.005");
    
    /**
     * 启动WebSocket套利监测
     */
    public void startArbitrageDetection() {
        try {
            LOG.info("启动WebSocket套利监测服务...");
            
            // 初始化交易所连接
            initializeExchanges();
            
            // 连接到交易所WebSocket
            connectToExchanges();
            
            // 订阅价格数据
            subscribeToTickers();
            
            isRunning = true;
            LOG.info("WebSocket套利监测服务已启动，监控币安和火币交易所");
            
        } catch (Exception e) {
            LOG.error("启动WebSocket套利监测服务失败", e);
            throw new RuntimeException("Failed to start streaming arbitrage service", e);
        }
    }
    
    /**
     * 停止WebSocket套利监测
     */
    public void stopArbitrageDetection() {
        LOG.info("停止WebSocket套利监测服务...");
        isRunning = false;
        
        // 取消订阅
        if (binanceTickerSubscription != null && !binanceTickerSubscription.isDisposed()) {
            binanceTickerSubscription.dispose();
        }
        if (huobiTickerSubscription != null && !huobiTickerSubscription.isDisposed()) {
            huobiTickerSubscription.dispose();
        }
        
        // 断开连接
        if (binanceExchange != null) {
            binanceExchange.disconnect().subscribe(
                () -> LOG.info("币安WebSocket连接已断开"),
                error -> LOG.error("断开币安WebSocket连接时出错", error)
            );
        }
        if (huobiExchange != null) {
            huobiExchange.disconnect().subscribe(
                () -> LOG.info("火币WebSocket连接已断开"),
                error -> LOG.error("断开火币WebSocket连接时出错", error)
            );
        }
        
        LOG.info("WebSocket套利监测服务已停止");
    }
    
    /**
     * 初始化交易所实例
     */
    private void initializeExchanges() {
        LOG.info("初始化交易所连接...");
        
        // 初始化币安交易所
        binanceExchange = StreamingExchangeFactory.INSTANCE
            .createExchange(BinanceStreamingExchange.class.getName());
        
        // 初始化火币交易所 (暂时注释，等待支持)
        // huobiExchange = StreamingExchangeFactory.INSTANCE
        //     .createExchange(HuobiStreamingExchange.class.getName());
        
        LOG.info("交易所实例初始化完成");
    }
    
    /**
     * 连接到交易所WebSocket
     */
    private void connectToExchanges() {
        LOG.info("连接到交易所WebSocket...");
        
        try {
            // 连接币安
            binanceExchange.connect().blockingAwait(10, TimeUnit.SECONDS);
            LOG.info("币安WebSocket连接成功");
            
            // 连接火币 (暂时注释)
            // huobiExchange.connect().blockingAwait(10, TimeUnit.SECONDS);
            // LOG.info("火币WebSocket连接成功");
            
        } catch (Exception e) {
            LOG.error("连接交易所WebSocket失败", e);
            throw new RuntimeException("Failed to connect to exchanges", e);
        }
    }
    
    /**
     * 订阅价格数据
     */
    private void subscribeToTickers() {
        LOG.info("订阅价格数据...");
        
        CurrencyPair ethUsdt = CurrencyPair.ETH_USDT;
        
        // 订阅币安ETH/USDT价格
        binanceTickerSubscription = binanceExchange.getStreamingMarketDataService()
            .getTicker(ethUsdt)
            .subscribe(
                ticker -> {
                    latestTickers.put("BINANCE", ticker);
                    LOG.debug("收到币安价格更新: bid={}, ask={}", ticker.getBid(), ticker.getAsk());
                    checkArbitrageOpportunity();
                },
                error -> LOG.error("币安价格订阅出错", error)
            );
        
        // 订阅火币ETH/USDT价格 (暂时注释)
        // huobiTickerSubscription = huobiExchange.getStreamingMarketDataService()
        //     .getTicker(ethUsdt)
        //     .subscribe(
        //         ticker -> {
        //             latestTickers.put("HUOBI", ticker);
        //             LOG.debug("收到火币价格更新: bid={}, ask={}", ticker.getBid(), ticker.getAsk());
        //             checkArbitrageOpportunity();
        //         },
        //         error -> LOG.error("火币价格订阅出错", error)
        //     );
        
        LOG.info("价格数据订阅完成");
    }
    
    /**
     * 检查套利机会
     */
    private void checkArbitrageOpportunity() {
        if (!isRunning) return;
        
        Ticker binanceTicker = latestTickers.get("BINANCE");
        // Ticker huobiTicker = latestTickers.get("HUOBI");
        
        // 暂时只检查币安数据
        if (binanceTicker == null) {
            return;
        }
        
        try {
            // 暂时只显示币安价格信息
            LOG.info("币安当前价格 - 买价: {}, 卖价: {}", binanceTicker.getBid(), binanceTicker.getAsk());
            
            // 等待火币支持后再启用套利检查
            // checkArbitrage("BINANCE", "HUOBI", binanceTicker.getAsk(), huobiTicker.getBid());
            // checkArbitrage("HUOBI", "BINANCE", huobiTicker.getAsk(), binanceTicker.getBid());
            
        } catch (Exception e) {
            LOG.error("检查套利机会时出错", e);
        }
    }
    
    /**
     * 检查具体的套利机会
     */
    private void checkArbitrage(String buyExchange, String sellExchange, 
                               BigDecimal buyPrice, BigDecimal sellPrice) {
        if (buyPrice == null || sellPrice == null) return;
        
        // 计算价差百分比
        BigDecimal priceDiff = sellPrice.subtract(buyPrice);
        BigDecimal profitMargin = priceDiff.divide(buyPrice, 6, RoundingMode.HALF_UP);
        
        // 检查是否超过套利阈值
        if (profitMargin.compareTo(ARBITRAGE_THRESHOLD) > 0) {
            BigDecimal profitPercent = profitMargin.multiply(new BigDecimal("100"));
            
            LOG.info("🚨 发现套利机会！");
            LOG.info("   买入交易所: {} (价格: {})", buyExchange, buyPrice);
            LOG.info("   卖出交易所: {} (价格: {})", sellExchange, sellPrice);
            LOG.info("   价差: {}", priceDiff);
            LOG.info("   利润率: {}%", profitPercent.setScale(3, RoundingMode.HALF_UP));
            LOG.info("   时间: {}", java.time.LocalDateTime.now());
            LOG.info("----------------------------------------");
        }
    }
    
    /**
     * 获取服务运行状态
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 获取最新价格数据
     */
    public ConcurrentHashMap<String, Ticker> getLatestTickers() {
        return new ConcurrentHashMap<>(latestTickers);
    }
}
