package co.codingnomads.bot.arbitrage.service;

import co.codingnomads.bot.arbitrage.mapper.ArbitrageOpportunityMapper;
import co.codingnomads.bot.arbitrage.model.ArbitrageOpportunity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 套利服务
 */
@Service
public class ArbitrageService {

    @Autowired
    private ArbitrageOpportunityMapper arbitrageMapper;

    /**
     * 保存套利机会
     */
    public void saveArbitrageOpportunity(ArbitrageOpportunity opportunity) {
        try {
            arbitrageMapper.insertArbitrageOpportunity(opportunity);
        } catch (Exception e) {
            System.err.println("保存套利机会失败: " + e.getMessage());
        }
    }

    /**
     * 检测套利机会
     */
    public void checkArbitrageOpportunity(String symbol, String exchange1, String exchange2,
                                        BigDecimal bid1, BigDecimal ask1, 
                                        BigDecimal bid2, BigDecimal ask2,
                                        double minMargin) {
        
        // 计算套利机会1: 在exchange2买入，在exchange1卖出
        BigDecimal profitMargin1 = bid1.subtract(ask2).divide(ask2, 6, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("100"));
        
        // 计算套利机会2: 在exchange1买入，在exchange2卖出
        BigDecimal profitMargin2 = bid2.subtract(ask1).divide(ask1, 6, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("100"));

        // 检查套利机会1
        if (profitMargin1.doubleValue() > minMargin) {
            ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
                symbol, exchange2, exchange1, ask2, bid1, profitMargin1
            );
            saveArbitrageOpportunity(opportunity);
            
            System.out.println(String.format("🚨 发现套利机会: 在%s买入(%.4f)，在%s卖出(%.4f)，利润率: %.4f%%",
                exchange2, ask2, exchange1, bid1, profitMargin1.doubleValue()));
        }

        // 检查套利机会2
        if (profitMargin2.doubleValue() > minMargin) {
            ArbitrageOpportunity opportunity = new ArbitrageOpportunity(
                symbol, exchange1, exchange2, ask1, bid2, profitMargin2
            );
            saveArbitrageOpportunity(opportunity);
            
            System.out.println(String.format("🚨 发现套利机会: 在%s买入(%.4f)，在%s卖出(%.4f)，利润率: %.4f%%",
                exchange1, ask1, exchange2, bid2, profitMargin2.doubleValue()));
        }
    }

    /**
     * 获取最新的套利机会
     */
    public List<ArbitrageOpportunity> getLatestOpportunities(String symbol, int limit) {
        return arbitrageMapper.getLatestOpportunities(symbol, limit);
    }

    /**
     * 获取今日套利机会统计
     */
    public int getTodayOpportunityCount() {
        return arbitrageMapper.getTodayOpportunityCount();
    }
}
