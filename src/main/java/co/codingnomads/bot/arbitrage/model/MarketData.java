package co.codingnomads.bot.arbitrage.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行情数据模型
 */
public class MarketData {
    private Long id;
    private String exchange;
    private String symbol;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private BigDecimal bidVolume;
    private BigDecimal askVolume;
    private Long timestamp;
    private LocalDateTime createdAt;

    // 构造函数
    public MarketData() {}

    public MarketData(String exchange, String symbol, BigDecimal bidPrice, BigDecimal askPrice, Long timestamp) {
        this.exchange = exchange;
        this.symbol = symbol;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.timestamp = timestamp;
    }

    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getBidPrice() { return bidPrice; }
    public void setBidPrice(BigDecimal bidPrice) { this.bidPrice = bidPrice; }

    public BigDecimal getAskPrice() { return askPrice; }
    public void setAskPrice(BigDecimal askPrice) { this.askPrice = askPrice; }

    public BigDecimal getBidVolume() { return bidVolume; }
    public void setBidVolume(BigDecimal bidVolume) { this.bidVolume = bidVolume; }

    public BigDecimal getAskVolume() { return askVolume; }
    public void setAskVolume(BigDecimal askVolume) { this.askVolume = askVolume; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "MarketData{" +
                "exchange='" + exchange + '\'' +
                ", symbol='" + symbol + '\'' +
                ", bidPrice=" + bidPrice +
                ", askPrice=" + askPrice +
                ", timestamp=" + timestamp +
                '}';
    }
}
