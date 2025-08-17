package co.codingnomads.bot.arbitrage.service.websocket;

import co.codingnomads.bot.arbitrage.action.arbitrage.ArbitrageEmailAction;
import co.codingnomads.bot.arbitrage.action.arbitrage.ArbitragePrintAction;
import co.codingnomads.bot.arbitrage.action.arbitrage.ArbitrageTradingAction;
import co.codingnomads.bot.arbitrage.action.arbitrage.selection.ArbitrageActionSelection;
import co.codingnomads.bot.arbitrage.model.ticker.TickerData;
import co.codingnomads.bot.arbitrage.service.general.DataUtil;
import co.codingnomads.bot.arbitrage.websocket.BinanceWebSocketClient;
// import co.codingnomads.bot.arbitrage.websocket.KrakenWebSocketClient; // 暂时移除
import co.codingnomads.bot.arbitrage.websocket.HuobiWebSocketClient;
import co.codingnomads.bot.arbitrage.websocket.GDAXWebSocketClient;
import co.codingnomads.bot.arbitrage.websocket.BittrexWebSocketClient;
import co.codingnomads.bot.arbitrage.websocket.PoloniexWebSocketClient;
import co.codingnomads.bot.arbitrage.websocket.GeminiWebSocketClient;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket-based arbitrage service using custom WebSocket clients
 */
@Service
public class WebSocketArbitrageService {
    
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketArbitrageService.class);
    
    private final DataUtil dataUtil = new DataUtil();
    private final ConcurrentHashMap<String, TickerData> latestTickerData = new ConcurrentHashMap<>();
    
    private BinanceWebSocketClient binanceClient;
    // private KrakenWebSocketClient krakenClient; // 暂时移除
    private HuobiWebSocketClient huobiClient;
    private GDAXWebSocketClient gdaxClient;
    private BittrexWebSocketClient bittrexClient;
    private PoloniexWebSocketClient poloniexClient;
    private GeminiWebSocketClient geminiClient;
    
    private int timeIntervalRepeater = 2000; // 2 seconds for WebSocket mode
    private int loopIterations = -1; // Run indefinitely by default
    private volatile boolean running = false;

    public int getTimeIntervalRepeater() {
        return timeIntervalRepeater;
    }

    public void setTimeIntervalRepeater(int timeIntervalRepeater) {
        this.timeIntervalRepeater = Math.max(timeIntervalRepeater, 1000); // Minimum 1 second
    }

    public int getLoopIterations() {
        return loopIterations;
    }

    public void setLoopIterations(int loopIterations) {
        this.loopIterations = loopIterations;
    }

    /**
     * Run WebSocket-based arbitrage
     */
    public void runWebSocketArbitrage(CurrencyPair currencyPair,
                                    ArbitrageActionSelection arbitrageActionSelection) throws Exception {

        // Determine arbitrage action type
        Boolean tradingMode = arbitrageActionSelection instanceof ArbitrageTradingAction;
        Boolean emailMode = arbitrageActionSelection instanceof ArbitrageEmailAction;
        Boolean printMode = arbitrageActionSelection instanceof ArbitragePrintAction;

        LOG.info("Starting WebSocket arbitrage for {} with print mode: {}", currencyPair, printMode);

        try {
            // Initialize WebSocket clients
            initializeWebSocketClients();
            
            // Wait for initial data
            waitForInitialData();
            
            LOG.info("WebSocket连接已建立。开始套利监控...");
            
            running = true;
            int currentIteration = loopIterations;
            
            // 主要套利监控循环
            while (running && (loopIterations < 0 || currentIteration >= 0)) {
                
                // 获取所有交易所的最新行情数据
                ArrayList<TickerData> listTickerData = getLatestTickerData();
                
                if (listTickerData.size() < 1) {
                    LOG.debug("等待交易所数据... 当前: {}", listTickerData.size());
                    Thread.sleep(timeIntervalRepeater);
                    continue;
                }
                
                // 单交易所测试，仅记录行情数据
                if (listTickerData.size() == 1) {
                    TickerData ticker = listTickerData.get(0);
                    LOG.info("单交易所行情 - 交易所: {}, 买价: {}, 卖价: {}, 价差: {}", 
                        ticker.getExchange().getClass().getSimpleName(),
                        ticker.getBid(), 
                        ticker.getAsk(),
                        ticker.getAsk().subtract(ticker.getBid()));
                    Thread.sleep(timeIntervalRepeater);
                    continue;
                }
                
                // Find best arbitrage opportunities
                TickerData highBid = dataUtil.highBidFinder(listTickerData);
                TickerData lowAsk = dataUtil.lowAskFinder(listTickerData);
                
                if (highBid == null || lowAsk == null) {
                    LOG.debug("Could not find valid bid/ask data, waiting...");
                    Thread.sleep(timeIntervalRepeater);
                    continue;
                }
                
                // Execute appropriate action
                if (printMode) {
                    ArbitragePrintAction arbitragePrintAction = (ArbitragePrintAction) arbitrageActionSelection;
                    arbitragePrintAction.print(lowAsk, highBid, arbitrageActionSelection.getArbitrageMargin());
                }
                
                if (emailMode) {
                    ArbitrageEmailAction arbitrageEmailAction = (ArbitrageEmailAction) arbitrageActionSelection;
                    arbitrageEmailAction.email(arbitrageEmailAction.getEmail(), lowAsk, highBid, 
                                             arbitrageActionSelection.getArbitrageMargin());
                }
                
                if (tradingMode) {
                    ArbitrageTradingAction arbitrageTradingAction = (ArbitrageTradingAction) arbitrageActionSelection;
                    if (arbitrageTradingAction.canTrade(lowAsk, highBid, arbitrageTradingAction)) {
                        arbitrageTradingAction.makeTrade(lowAsk, highBid, arbitrageTradingAction);
                    }
                }
                
                // Wait before next iteration
                Thread.sleep(timeIntervalRepeater);
                
                if (loopIterations >= 0) {
                    currentIteration--;
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error in WebSocket arbitrage", e);
            throw e;
        } finally {
            // Clean up connections
            stop();
        }
    }

    private void initializeWebSocketClients() throws Exception {
        // Initialize Binance WebSocket client
        binanceClient = new BinanceWebSocketClient();
        binanceClient.setTickerUpdateCallback(tickerData -> {
            latestTickerData.put("BINANCE", tickerData);
            LOG.debug("Updated Binance ticker: bid={}, ask={}", tickerData.getBid(), tickerData.getAsk());
        });
        
        // Initialize Kraken WebSocket client
        // Initialize Huobi WebSocket client
        huobiClient = new HuobiWebSocketClient();
        huobiClient.setTickerUpdateCallback(tickerData -> {
            latestTickerData.put("HUOBI", tickerData);
            LOG.debug("Updated Huobi ticker: bid={}, ask={}", tickerData.getBid(), tickerData.getAsk());
        });
        
        
        // 其他交易所WebSocket客户端已注释，只保留火币和币安
        /*
        // Initialize GDAX WebSocket client
        gdaxClient = new GDAXWebSocketClient();
        gdaxClient.setTickerUpdateCallback(tickerData -> {
            latestTickerData.put("GDAX", tickerData);
            LOG.debug("Updated GDAX ticker: bid={}, ask={}", tickerData.getBid(), tickerData.getAsk());
        });
        
        // Initialize Bittrex WebSocket client
        bittrexClient = new BittrexWebSocketClient();
        bittrexClient.setTickerUpdateCallback(tickerData -> {
            latestTickerData.put("BITTREX", tickerData);
            LOG.debug("Updated Bittrex ticker: bid={}, ask={}", tickerData.getBid(), tickerData.getAsk());
        });
        
        // Initialize Poloniex WebSocket client
        poloniexClient = new PoloniexWebSocketClient();
        poloniexClient.setTickerUpdateCallback(tickerData -> {
            latestTickerData.put("POLONIEX", tickerData);
            LOG.debug("Updated Poloniex ticker: bid={}, ask={}", tickerData.getBid(), tickerData.getAsk());
        });
        
        // Initialize Gemini WebSocket client
        geminiClient = new GeminiWebSocketClient();
        geminiClient.setTickerUpdateCallback(tickerData -> {
            latestTickerData.put("GEMINI", tickerData);
            LOG.debug("Updated Gemini ticker: bid={}, ask={}", tickerData.getBid(), tickerData.getAsk());
        });
        */
        
        // 连接到2个网络正常的交易所
        LOG.info("开始连接WebSocket...");
        
        try {
            binanceClient.connect();
            LOG.info("币安WebSocket连接已启动");
        } catch (Exception e) {
            LOG.error("币安WebSocket连接失败", e);
        }
        
        try {
            huobiClient.connect();
            LOG.info("火币WebSocket连接已启动");
        } catch (Exception e) {
            LOG.error("火币WebSocket连接失败", e);
        }
        
        // 其他交易所连接已注释
        /*
        try {
            krakenClient.connect();
            LOG.info("Kraken WebSocket连接已启动");
        } catch (Exception e) {
            LOG.error("Kraken WebSocket连接失败", e);
        }
        
        try {
            gdaxClient.connect();
            LOG.info("Coinbase Pro WebSocket连接已启动");
        } catch (Exception e) {
            LOG.error("Coinbase Pro WebSocket连接失败", e);
        }
        
        try {
            geminiClient.connect();
            LOG.info("Gemini WebSocket连接已启动");
        } catch (Exception e) {
            LOG.error("Gemini WebSocket连接失败", e);
        }
        */
        
        // 等待连接建立
        LOG.info("等待WebSocket连接建立...");
        
        try {
            binanceClient.waitForConnection().get(10, TimeUnit.SECONDS);
            LOG.info("币安WebSocket连接成功");
        } catch (Exception e) {
            LOG.error("币安WebSocket连接超时或失败", e);
        }
        
        try {
            huobiClient.waitForConnection().get(10, TimeUnit.SECONDS);
            LOG.info("火币WebSocket连接成功");
        } catch (Exception e) {
            LOG.error("火币WebSocket连接超时或失败", e);
        }
        
        LOG.info("所有WebSocket连接已建立");
        
        // 订阅行情数据流（只保留火币和币安）
        binanceClient.subscribeToTicker("ETHUSDT"); // 币安使用USDT
        huobiClient.subscribeToTicker("ethusdt"); // Huobi uses lowercase
    }

    private void waitForInitialData() throws InterruptedException {
        int maxWaitTime = 30000; // 30 seconds
        int waitTime = 0;
        int checkInterval = 1000; // 1 second
        
        while (latestTickerData.size() < 2 && waitTime < maxWaitTime) {
            Thread.sleep(checkInterval);
            waitTime += checkInterval;
            LOG.debug("等待初始数据... 已接收 {}/2 个交易所", latestTickerData.size());
        }
        
        if (latestTickerData.size() < 2) {
            LOG.warn("未能连接到所有交易所，当前已连接: {}/2", latestTickerData.size());
            if (latestTickerData.size() == 0) {
                throw new RuntimeException("等待交易所初始数据超时");
            }
        }
        
        LOG.info("已接收到 {} 个交易所的初始数据", latestTickerData.size());
    }

    private ArrayList<TickerData> getLatestTickerData() {
        return new ArrayList<>(latestTickerData.values());
    }

    /**
     * Stop the WebSocket arbitrage and disconnect from all exchanges
     */
    public void stop() {
        running = false;
        
        if (binanceClient != null && binanceClient.isConnected()) {
            binanceClient.close();
        }
        
        // if (krakenClient != null && krakenClient.isConnected()) {
        //     krakenClient.close();
        // }
        
        if (huobiClient != null && huobiClient.isConnected()) {
            huobiClient.close();
        }
        
        if (gdaxClient != null && gdaxClient.isConnected()) {
            gdaxClient.close();
        }
        
        if (bittrexClient != null && bittrexClient.isConnected()) {
            bittrexClient.close();
        }
        
        if (poloniexClient != null && poloniexClient.isConnected()) {
            poloniexClient.close();
        }
        
        if (geminiClient != null && geminiClient.isConnected()) {
            geminiClient.close();
        }
        
        LOG.info("WebSocket arbitrage service stopped");
    }

    /**
     * Check if the WebSocket arbitrage is currently running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get connection status for all exchanges
     */
    public String getConnectionStatus() {
        int connectedCount = 0;
        if (binanceClient != null && binanceClient.isConnected()) connectedCount++;
        // if (krakenClient != null && krakenClient.isConnected()) connectedCount++;
        if (huobiClient != null && huobiClient.isConnected()) connectedCount++;
        if (gdaxClient != null && gdaxClient.isConnected()) connectedCount++;
        if (bittrexClient != null && bittrexClient.isConnected()) connectedCount++;
        if (poloniexClient != null && poloniexClient.isConnected()) connectedCount++;
        if (geminiClient != null && geminiClient.isConnected()) connectedCount++;
        
        return String.format("Connected exchanges: %d/7", connectedCount);
    }
}
