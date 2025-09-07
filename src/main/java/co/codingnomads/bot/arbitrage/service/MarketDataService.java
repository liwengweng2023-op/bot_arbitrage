package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.mapper.MarketDataMapper;
import co.codingnomads.bot.arbitrage.model.MarketData;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 行情数据服务
 * 
 * 该服务负责：
 * 1. 保存实时行情数据到数据库
 * 2. 查询历史行情数据
 * 3. 提供行情数据统计功能
 * 4. 管理不同交易所的行情数据
 * 
 * 数据来源：
 * - 币安 (Binance) WebSocket API
 * - 火币 (Huobi) WebSocket API
 * 
 * @author CodingNomads
 * @version 1.0
 * @since 2024
 */
@Service
public class MarketDataService {

    // ==================== 日志记录器 ====================
    private static final Logger logger = Logger.getLogger(MarketDataService.class);

    // ==================== 依赖注入 ====================
    @Autowired
    private MarketDataMapper marketDataMapper;

    // ==================== 数据保存方法 ====================
    
    /**
     * 保存行情数据到数据库
     * 
     * @param marketData 行情数据对象
     */
    public void saveMarketData(MarketData marketData) {
        try {
            if (isValidMarketData(marketData)) {
                marketDataMapper.insertMarketData(marketData);
            } else {
                logWarning("无效的行情数据，跳过保存", marketData);
            }
        } catch (Exception e) {
            logError("保存行情数据失败", e);
        }
    }
    
    /**
     * 验证行情数据有效性
     * 
     * @param marketData 行情数据对象
     * @return 数据是否有效
     */
    private boolean isValidMarketData(MarketData marketData) {
        return marketData != null &&
               marketData.getExchange() != null && !marketData.getExchange().trim().isEmpty() &&
               marketData.getSymbol() != null && !marketData.getSymbol().trim().isEmpty() &&
               marketData.getBidPrice() != null && marketData.getBidPrice().compareTo(java.math.BigDecimal.ZERO) > 0 &&
               marketData.getAskPrice() != null && marketData.getAskPrice().compareTo(java.math.BigDecimal.ZERO) > 0 &&
               marketData.getTimestamp() != null && marketData.getTimestamp() > 0;
    }

    // ==================== 数据查询方法 ====================
    
    /**
     * 获取最新的行情数据
     * 
     * @param exchange 交易所名称，如果为null或空则查询所有交易所
     * @param symbol 交易对符号，如果为null或空则查询所有交易对
     * @param limit 返回记录数限制
     * @return 行情数据列表，按时间戳倒序排列
     */
    public List<MarketData> getLatestMarketData(String exchange, String symbol, int limit) {
        try {
            return marketDataMapper.getLatestMarketData(exchange, symbol, limit);
        } catch (Exception e) {
            logError("获取最新行情数据失败", e);
            return new java.util.ArrayList<>(); // 返回空列表而不是null
        }
    }

    /**
     * 获取今日数据统计
     * 
     * @param exchange 交易所名称，如果为null或空则统计所有交易所
     * @return 今日数据记录数量
     */
    public int getTodayDataCount(String exchange) {
        try {
            return marketDataMapper.getTodayDataCount(exchange);
        } catch (Exception e) {
            logError("获取今日数据统计失败", e);
            return 0;
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 记录错误日志
     * 
     * @param message 错误消息
     * @param e 异常对象
     */
    private void logError(String message, Exception e) {
        logger.error("[MarketDataService] " + message + ": " + e.getMessage(), e);
    }
    
    /**
     * 记录警告日志
     * 
     * @param message 警告消息
     * @param marketData 行情数据对象
     */
    private void logWarning(String message, MarketData marketData) {
        logger.warn("[MarketDataService] " + message + ": " + marketData);
    }
}

