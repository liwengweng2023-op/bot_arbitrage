package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.mapper.ArbitrageOpportunityMapper;
import co.codingnomads.bot.arbitrage.model.ArbitrageOpportunity;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 套利服务
 * 
 * 该服务负责：
 * 1. 检测不同交易所间的套利机会
 * 2. 计算套利利润率和利润金额
 * 3. 保存套利机会到数据库
 * 4. 提供套利机会查询功能
 * 
 * 套利检测逻辑：
 * - 检查在两个交易所之间是否存在价格差异
 * - 计算买入和卖出价格之间的利润率
 * - 只保存超过最小利润率阈值的套利机会
 * 
 * @author CodingNomads
 * @version 1.0
 * @since 2024
 */
@Service
public class ArbitrageService {

    // ==================== 日志记录器 ====================
    private static final Logger logger = Logger.getLogger(ArbitrageService.class);

    // ==================== 依赖注入 ====================
    @Autowired
    private ArbitrageOpportunityMapper arbitrageMapper;
    
    // ==================== 配置常量 ====================
    /** 默认最小套利利润率（百分比） */
    private static final double DEFAULT_MIN_MARGIN = 0.01;
    
    /** 利润率计算精度 */
    private static final int PROFIT_MARGIN_SCALE = 6;
    
    /** 百分比转换乘数 */
    private static final BigDecimal PERCENTAGE_MULTIPLIER = new BigDecimal("100");

    // ==================== 套利机会管理 ====================
    
    /**
     * 保存套利机会到数据库
     * 
     * @param opportunity 套利机会对象
     */
    public void saveArbitrageOpportunity(ArbitrageOpportunity opportunity) {
        try {
            arbitrageMapper.insertArbitrageOpportunity(opportunity);
        } catch (Exception e) {
            logError("保存套利机会失败", e);
        }
    }

    /**
     * 检测套利机会
     * 
     * 检查两个交易所之间是否存在套利机会，包括两个方向：
     * 1. 在exchange2买入，在exchange1卖出
     * 2. 在exchange1买入，在exchange2卖出
     * 
     * @param symbol 交易对符号
     * @param exchange1 第一个交易所名称
     * @param exchange2 第二个交易所名称
     * @param bid1 第一个交易所买一价
     * @param ask1 第一个交易所卖一价
     * @param bid2 第二个交易所买一价
     * @param ask2 第二个交易所卖一价
     * @param minMargin 最小利润率阈值（百分比）
     */
    public void checkArbitrageOpportunity(String symbol, String exchange1, String exchange2,
                                        BigDecimal bid1, BigDecimal ask1, 
                                        BigDecimal bid2, BigDecimal ask2,
                                        double minMargin) {
        
        // 验证输入参数
        if (!isValidInput(symbol, exchange1, exchange2, bid1, ask1, bid2, ask2)) {
            return;
        }
        
        // 检查套利机会1: 在exchange2买入，在exchange1卖出
        checkArbitrageDirection(symbol, exchange2, exchange1, ask2, bid1, minMargin);
        
        // 检查套利机会2: 在exchange1买入，在exchange2卖出
        checkArbitrageDirection(symbol, exchange1, exchange2, ask1, bid2, minMargin);
    }
    
    /**
     * 验证输入参数
     * 
     * @param symbol 交易对符号
     * @param exchange1 第一个交易所名称
     * @param exchange2 第二个交易所名称
     * @param bid1 第一个交易所买一价
     * @param ask1 第一个交易所卖一价
     * @param bid2 第二个交易所买一价
     * @param ask2 第二个交易所卖一价
     * @return 参数是否有效
     */
    private boolean isValidInput(String symbol, String exchange1, String exchange2,
                               BigDecimal bid1, BigDecimal ask1, BigDecimal bid2, BigDecimal ask2) {
        return symbol != null && !symbol.trim().isEmpty() &&
               exchange1 != null && !exchange1.trim().isEmpty() &&
               exchange2 != null && !exchange2.trim().isEmpty() &&
               bid1 != null && bid1.compareTo(BigDecimal.ZERO) > 0 &&
               ask1 != null && ask1.compareTo(BigDecimal.ZERO) > 0 &&
               bid2 != null && bid2.compareTo(BigDecimal.ZERO) > 0 &&
               ask2 != null && ask2.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * 检查特定方向的套利机会
     * 
     * @param symbol 交易对符号
     * @param buyExchange 买入交易所
     * @param sellExchange 卖出交易所
     * @param buyPrice 买入价格
     * @param sellPrice 卖出价格
     * @param minMargin 最小利润率阈值
     */
    private void checkArbitrageDirection(String symbol, String buyExchange, String sellExchange,
                                       BigDecimal buyPrice, BigDecimal sellPrice, double minMargin) {
        BigDecimal profitMargin = calculateProfitMargin(buyPrice, sellPrice);
        
        if (profitMargin.doubleValue() > minMargin) {
            ArbitrageOpportunity opportunity = createArbitrageOpportunity(
                symbol, buyExchange, sellExchange, buyPrice, sellPrice, profitMargin
            );
            saveArbitrageOpportunity(opportunity);
            logArbitrageOpportunity(buyExchange, sellExchange, buyPrice, sellPrice, profitMargin);
        }
    }
    
    /**
     * 计算利润率
     * 
     * @param buyPrice 买入价格
     * @param sellPrice 卖出价格
     * @return 利润率（百分比）
     */
    private BigDecimal calculateProfitMargin(BigDecimal buyPrice, BigDecimal sellPrice) {
        return sellPrice.subtract(buyPrice)
                .divide(buyPrice, PROFIT_MARGIN_SCALE, BigDecimal.ROUND_HALF_UP)
                .multiply(PERCENTAGE_MULTIPLIER);
    }
    
    /**
     * 创建套利机会对象
     * 
     * @param symbol 交易对符号
     * @param buyExchange 买入交易所
     * @param sellExchange 卖出交易所
     * @param buyPrice 买入价格
     * @param sellPrice 卖出价格
     * @param profitMargin 利润率
     * @return 套利机会对象
     */
    private ArbitrageOpportunity createArbitrageOpportunity(String symbol, String buyExchange, 
                                                          String sellExchange, BigDecimal buyPrice, 
                                                          BigDecimal sellPrice, BigDecimal profitMargin) {
        return new ArbitrageOpportunity(symbol, buyExchange, sellExchange, buyPrice, sellPrice, profitMargin);
    }
    
    /**
     * 记录套利机会日志
     * 
     * @param buyExchange 买入交易所
     * @param sellExchange 卖出交易所
     * @param buyPrice 买入价格
     * @param sellPrice 卖出价格
     * @param profitMargin 利润率
     */
    private void logArbitrageOpportunity(String buyExchange, String sellExchange, 
                                       BigDecimal buyPrice, BigDecimal sellPrice, BigDecimal profitMargin) {
        logger.info(String.format("🚨 发现套利机会: 在%s买入(%.4f)，在%s卖出(%.4f)，利润率: %.4f%%",
            buyExchange, buyPrice, sellExchange, sellPrice, profitMargin.doubleValue()));
    }

    // ==================== 查询方法 ====================
    
    /**
     * 获取最新的套利机会
     * 
     * @param symbol 交易对符号，如果为null或空则查询所有交易对
     * @param limit 返回记录数限制
     * @return 套利机会列表，按检测时间倒序排列
     */
    public List<ArbitrageOpportunity> getLatestOpportunities(String symbol, int limit) {
        try {
            return arbitrageMapper.getLatestOpportunities(symbol, limit);
        } catch (Exception e) {
            logError("获取最新套利机会失败", e);
            return new java.util.ArrayList<>(); // 返回空列表而不是null
        }
    }

    /**
     * 获取今日套利机会统计
     * 
     * @return 今日检测到的套利机会数量
     */
    public int getTodayOpportunityCount() {
        try {
            return arbitrageMapper.getTodayOpportunityCount();
        } catch (Exception e) {
            logError("获取今日套利机会统计失败", e);
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
        logger.error("[ArbitrageService] " + message + ": " + e.getMessage(), e);
    }
}
