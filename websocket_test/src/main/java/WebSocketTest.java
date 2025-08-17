import org.knowm.xchange.binance.BinanceStreamingExchange;
import org.knowm.xchange.stream.core.StreamingExchange;
import org.knowm.xchange.stream.core.StreamingExchangeFactory;
import io.reactivex.disposables.Disposable;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 测试XChange-stream 4.4.2币安WebSocket连接
 */
public class WebSocketTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketTest.class);
    private static final CurrencyPair ETH_USDT = CurrencyPair.ETH_USDT;
    
    public static void main(String[] args) {
        try {
            System.out.println("=== XChange-stream 4.4.2 币安WebSocket测试 ===");
            
            // 初始化币安流式交易所
            StreamingExchange binanceExchange = StreamingExchangeFactory.INSTANCE
                .createExchange(BinanceStreamingExchange.class.getName());
            
            // 连接到币安WebSocket
            LOG.info("连接币安WebSocket...");
            binanceExchange.connect().blockingAwait(10, TimeUnit.SECONDS);
            LOG.info("✅ 币安WebSocket连接成功");
            
            // 订阅ETH/USDT价格数据
            LOG.info("订阅ETH/USDT价格数据...");
            Disposable subscription = binanceExchange.getStreamingMarketDataService()
                .getTicker(ETH_USDT)
                .subscribe(
                    ticker -> {
                        System.out.printf("📊 币安价格更新 - ETH/USDT: 买价=%.4f, 卖价=%.4f, 时间=%s%n", 
                            ticker.getBid().doubleValue(), 
                            ticker.getAsk().doubleValue(), 
                            ticker.getTimestamp());
                    },
                    error -> {
                        LOG.error("❌ 币安价格订阅出错", error);
                        System.exit(1);
                    }
                );
            
            System.out.println("🚀 WebSocket服务已启动，正在监控币安ETH/USDT价格...");
            System.out.println("💡 按 Ctrl+C 停止服务");
            
            // 保持程序运行
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🔄 正在关闭WebSocket连接...");
                subscription.dispose();
                binanceExchange.disconnect().subscribe();
                System.out.println("✅ WebSocket连接已关闭");
            }));
            
            // 保持主线程运行
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("❌ 启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
