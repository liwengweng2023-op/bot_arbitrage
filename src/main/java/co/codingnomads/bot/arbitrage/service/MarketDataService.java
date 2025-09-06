package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.mapper.MarketDataMapper;
import co.codingnomads.bot.arbitrage.model.MarketData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 行情数据服务
 */
@Service
public class MarketDataService {

    @Autowired
    private MarketDataMapper marketDataMapper;

    /**
     * 保存行情数据
     */
    public void saveMarketData(MarketData marketData) {
        try {
            marketDataMapper.insertMarketData(marketData);
        } catch (Exception e) {
            System.err.println("保存行情数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新的行情数据
     */
    public List<MarketData> getLatestMarketData(String exchange, String symbol, int limit) {
        return marketDataMapper.getLatestMarketData(exchange, symbol, limit);
    }

    /**
     * 获取今日数据统计
     */
    public int getTodayDataCount(String exchange) {
        return marketDataMapper.getTodayDataCount(exchange);
    }
}
