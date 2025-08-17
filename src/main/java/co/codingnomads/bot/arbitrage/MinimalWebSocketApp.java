package co.codingnomads.bot.arbitrage;

import info.bitrich.xchangestream.binance.BinanceStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 最小化WebSocket套利检测应用
 * 直接使用XChange-stream 4.4.2连接币安WebSocket
 */
public class MinimalWebSocketApp {
    
    private static final Logger LOG = LoggerFactory.getLogger(MinimalWebSocketApp.class);
    private static final CurrencyPair ETH_USDT = CurrencyPair.ETH_USDT;
    
    public static void main(String[] args) {
        try {
            System.out.println("启动最小化WebSocket套利检测应用...");
            
            // 初始化币安流式交易所
            StreamingExchange binanceExchange = StreamingExchangeFactory.INSTANCE
                .createExchange(BinanceStreamingExchange.class.getName());
            
            // 连接到币安WebSocket
            LOG.info("连接币安WebSocket...");
            binanceExchange.connect().blockingAwait(10, TimeUnit.SECONDS);
            LOG.info("币安WebSocket连接成功");
            
            // 订阅ETH/USDT价格数据
            LOG.info("订阅ETH/USDT价格数据...");
            Disposable subscription = binanceExchange.getStreamingMarketDataService()
                .getTicker(ETH_USDT)
                .subscribe(
                    ticker -> {
                        LOG.info("收到币安价格更新 - ETH/USDT: 买价={}, 卖价={}, 时间={}", 
                            ticker.getBid(), ticker.getAsk(), ticker.getTimestamp());
                    },
                    error -> LOG.error("币安价格订阅出错", error)
                );
            
            LOG.info("WebSocket服务已启动，正在监控币安ETH/USDT价格...");
            LOG.info("按 Ctrl+C 停止服务");
            
            // 保持程序运行
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("正在关闭WebSocket连接...");
                subscription.dispose();
                binanceExchange.disconnect().subscribe();
                LOG.info("WebSocket连接已关闭");
            }));
            
            // 保持主线程运行
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("启动失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
