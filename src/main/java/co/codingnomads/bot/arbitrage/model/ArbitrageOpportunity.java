package co.codingnomads.bot.arbitrage.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套利机会模型
 */
public class ArbitrageOpportunity {
    private Long id;
    private String symbol;
    private String buyExchange;
    private String sellExchange;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private BigDecimal profitMargin;
    private BigDecimal profitAmount;
    private LocalDateTime detectedAt;

    // 构造函数
    public ArbitrageOpportunity() {}

    public ArbitrageOpportunity(String symbol, String buyExchange, String sellExchange, 
                               BigDecimal buyPrice, BigDecimal sellPrice, BigDecimal profitMargin) {
        this.symbol = symbol;
        this.buyExchange = buyExchange;
        this.sellExchange = sellExchange;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.profitMargin = profitMargin;
    }

    // Getter和Setter方法
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getBuyExchange() { return buyExchange; }
    public void setBuyExchange(String buyExchange) { this.buyExchange = buyExchange; }

    public String getSellExchange() { return sellExchange; }
    public void setSellExchange(String sellExchange) { this.sellExchange = sellExchange; }

    public BigDecimal getBuyPrice() { return buyPrice; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }

    public BigDecimal getSellPrice() { return sellPrice; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }

    public BigDecimal getProfitMargin() { return profitMargin; }
    public void setProfitMargin(BigDecimal profitMargin) { this.profitMargin = profitMargin; }

    public BigDecimal getProfitAmount() { return profitAmount; }
    public void setProfitAmount(BigDecimal profitAmount) { this.profitAmount = profitAmount; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    @Override
    public String toString() {
        return "ArbitrageOpportunity{" +
                "symbol='" + symbol + '\'' +
                ", buyExchange='" + buyExchange + '\'' +
                ", sellExchange='" + sellExchange + '\'' +
                ", buyPrice=" + buyPrice +
                ", sellPrice=" + sellPrice +
                ", profitMargin=" + profitMargin + "%" +
                '}';
    }
}
