package co.codingnomads.bot.arbitrage.mapper;

import co.codingnomads.bot.arbitrage.model.ArbitrageOpportunity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 套利机会Mapper
 */
@Mapper
public interface ArbitrageOpportunityMapper {

    @Insert("INSERT INTO arbitrage_opportunities (symbol, buy_exchange, sell_exchange, buy_price, sell_price, profit_margin, profit_amount, detected_at) " +
            "VALUES (#{symbol}, #{buyExchange}, #{sellExchange}, #{buyPrice}, #{sellPrice}, #{profitMargin}, #{profitAmount}, NOW())")
    int insertArbitrageOpportunity(ArbitrageOpportunity opportunity);

    @Select("SELECT * FROM arbitrage_opportunities WHERE symbol = #{symbol} ORDER BY detected_at DESC LIMIT #{limit}")
    List<ArbitrageOpportunity> getLatestOpportunities(String symbol, int limit);

    @Select("SELECT COUNT(*) FROM arbitrage_opportunities WHERE DATE(detected_at) = CURDATE()")
    int getTodayOpportunityCount();
}
