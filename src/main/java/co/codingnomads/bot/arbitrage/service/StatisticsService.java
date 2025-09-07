package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.config.ArbitrageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统计服务
 * 负责收集和打印套利操作的性能指标
 */
@Service
public class StatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsService.class);

    private final AtomicInteger checkCount = new AtomicInteger(0);
    private final AtomicInteger skippedOpportunities = new AtomicInteger(0);
    private final AtomicInteger processedOpportunities = new AtomicInteger(0);
    private volatile long lastStatsPrintTime = System.currentTimeMillis();

    /**
     * 增加套利检查次数
     */
    public void incrementCheckCount() {
        checkCount.incrementAndGet();
    }

    /**
     * 增加跳过的套利机会次数
     */
    public void incrementSkippedOpportunities() {
        skippedOpportunities.incrementAndGet();
    }

    /**
     * 增加处理的套利机会次数
     */
    public void incrementProcessedOpportunities() {
        processedOpportunities.incrementAndGet();
    }

    /**
     * 定期打印统计信息
     */
    public void printStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsPrintTime > ArbitrageConfig.STATS_PRINT_INTERVAL_MS) {
            lastStatsPrintTime = now;
            String formattedTime = new SimpleDateFormat("HH:mm:ss").format(new Date());

            logger.info("==================== 统计信息 ({}) ====================", formattedTime);
            logger.info("套利检查总次数: {}", checkCount.get());
            logger.info("跳过的套利机会: {}", skippedOpportunities.get());
            logger.info("已处理的套利机会: {}", processedOpportunities.get());
            logger.info("===============================================================");
        }
    }

    /**
     * 打印最终统计信息
     */
    public void printFinalStats() {
        printStats(); // 强制打印一次
    }
}