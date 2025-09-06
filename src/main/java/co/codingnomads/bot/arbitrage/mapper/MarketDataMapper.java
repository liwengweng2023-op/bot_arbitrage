package co.codingnomads.bot.arbitrage.mapper;

import co.codingnomads.bot.arbitrage.model.MarketData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 行情数据Mapper
 */
@Mapper
public interface MarketDataMapper {

    @Insert("INSERT INTO market_data (exchange, symbol, bid_price, ask_price, bid_volume, ask_volume, timestamp, created_at) " +
            "VALUES (#{exchange}, #{symbol}, #{bidPrice}, #{askPrice}, #{bidVolume}, #{askVolume}, #{timestamp}, NOW())")
    int insertMarketData(MarketData marketData);

    @Select("SELECT * FROM market_data WHERE exchange = #{exchange} AND symbol = #{symbol} ORDER BY created_at DESC LIMIT #{limit}")
    List<MarketData> getLatestMarketData(String exchange, String symbol, int limit);

    @Select("SELECT COUNT(*) FROM market_data WHERE exchange = #{exchange} AND DATE(created_at) = CURDATE()")
    int getTodayDataCount(String exchange);
}
