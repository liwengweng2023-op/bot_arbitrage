package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.config.ArbitrageConfig;
import co.codingnomads.bot.arbitrage.exchange.binance.BinanceWebSocketClient;
import co.codingnomads.bot.arbitrage.exchange.huobi.HuobiWebSocketClient;
import co.codingnomads.bot.arbitrage.model.MarketData;
import co.codingnomads.bot.arbitrage.service.websocket.WebSocketMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 实时套利服务
 * 负责协调WebSocket连接、处理价格数据和检测套利机会
 */
@Service
public class RealTimeArbitrageService implements WebSocketMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeArbitrageService.class);

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private StatisticsService statisticsService;

    private final ConcurrentHashMap<String, MarketData> latestMarketData = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private BinanceWebSocketClient binanceWebSocketClient;
    private HuobiWebSocketClient huobiWebSocketClient;

    /**
     * 初始化WebSocket连接
     */
    @PostConstruct
    public void init() {
        initializeWebSocketConnections();
        scheduledExecutorService.scheduleAtFixedRate(statisticsService::printStats, 0, ArbitrageConfig.STATS_PRINT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 销毁资源
     */
    @PreDestroy
    public void destroy() {
        if (binanceWebSocketClient != null) {
            binanceWebSocketClient.close();
        }
        if (huobiWebSocketClient != null) {
            huobiWebSocketClient.close();
        }
        scheduledExecutorService.shutdown();
        statisticsService.printFinalStats();
    }

    /**
     * 初始化WebSocket连接
     */
    private void initializeWebSocketConnections() {
        try {
            binanceWebSocketClient = new BinanceWebSocketClient(new URI(ArbitrageConfig.BINANCE_WS_URL), this);
            binanceWebSocketClient.connect();

            huobiWebSocketClient = new HuobiWebSocketClient(new URI(ArbitrageConfig.HUOBI_WS_URL), this);
            huobiWebSocketClient.connect();
        } catch (Exception e) {
            logger.error("初始化WebSocket连接时出错", e);
        }
    }

    /**
     * 处理价格更新
     *
     * @param exchange  交易所名称
     * @param bestBid   最优买价
     * @param bestAsk   最优卖价
     */
    @Override
    public void handlePriceUpdate(String exchange, double bestBid, double bestAsk) {
        updateLatestPrice(exchange, bestBid, bestAsk);
        checkForArbitrageOpportunity();
    }

    /**
     * 更新最新价格
     *
     * @param exchange  交易所名称
     * @param bestBid   最优买价
     * @param bestAsk   最优卖价
     */
    private void updateLatestPrice(String exchange, double bestBid, double bestAsk) {
        MarketData marketData = new MarketData(exchange, ArbitrageConfig.SYMBOL, BigDecimal.valueOf(bestBid), BigDecimal.valueOf(bestAsk), System.currentTimeMillis());
        latestMarketData.put(exchange, marketData);
        marketDataService.saveMarketData(marketData);
    }

    /**
     * 检查套利机会
     */
    private void checkForArbitrageOpportunity() {
        MarketData binanceData = latestMarketData.get(ArbitrageConfig.BINANCE_EXCHANGE_NAME);
        MarketData huobiData = latestMarketData.get(ArbitrageConfig.HUOBI_EXCHANGE_NAME);

        if (binanceData != null && huobiData != null) {
            statisticsService.incrementCheckCount();
            if (isDataFresh(binanceData) && isDataFresh(huobiData)) {
                arbitrageService.checkForArbitrage(binanceData, huobiData);
            } else {
                statisticsService.incrementSkippedOpportunities();
            }
        }
    }

    /**
     * 检查数据是否新鲜
     *
     * @param marketData 市场数据
     * @return 如果数据新鲜则返回true，否则返回false
     */
    private boolean isDataFresh(MarketData marketData) {
        return (System.currentTimeMillis() - marketData.getTimestamp()) < ArbitrageConfig.PRICE_EXPIRY_MS;
    }
}