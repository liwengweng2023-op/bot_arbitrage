package co.codingnomads.bot.arbitrage.websocket;

import co.codingnomads.bot.arbitrage.model.ticker.TickerData;
import com.fasterxml.jackson.databind.JsonNode;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Binance WebSocket client for real-time ticker data
 */
public class BinanceWebSocketClient extends ExchangeWebSocketClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/ws/ethusdt@ticker";
    
    private final ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();
    private final BinanceExchange exchange = new BinanceExchange();
    private Consumer<TickerData> tickerUpdateCallback;

    public BinanceWebSocketClient() {
        super(URI.create(BINANCE_WS_URL));
        setupMessageHandlers();
    }

    private void setupMessageHandlers() {
        registerMessageHandler("ticker", this::handleTickerUpdate);
    }

    private void handleTickerUpdate(JsonNode message) {
        try {
            if (message.has("s") && message.has("b") && message.has("a")) {
                String symbol = message.get("s").asText();
                BigDecimal bid = new BigDecimal(message.get("b").asText());
                BigDecimal ask = new BigDecimal(message.get("a").asText());
                
                // Convert Binance symbol to XChange CurrencyPair
                CurrencyPair currencyPair = convertSymbolToCurrencyPair(symbol);
                if (currencyPair != null) {
                    TickerData tickerData = new TickerData(currencyPair, exchange, bid, ask);
                    tickerCache.put(symbol, tickerData);
                    
                    if (tickerUpdateCallback != null) {
                        tickerUpdateCallback.accept(tickerData);
                    }
                    
                    LOG.debug("Updated Binance ticker for {}: bid={}, ask={}", symbol, bid, ask);
                }
            }
        } catch (Exception e) {
            LOG.error("Error processing Binance ticker update", e);
        }
    }

    private CurrencyPair convertSymbolToCurrencyPair(String symbol) {
        switch (symbol.toUpperCase()) {
            case "ETHUSDT":
                return CurrencyPair.ETH_USD;
            case "BTCUSDT":
                return CurrencyPair.BTC_USD;
            default:
                LOG.warn("Unknown symbol: {}", symbol);
                return null;
        }
    }

    public void setTickerUpdateCallback(Consumer<TickerData> callback) {
        this.tickerUpdateCallback = callback;
    }

    public TickerData getLatestTicker(String symbol) {
        return tickerCache.get(symbol);
    }

    public void subscribeToTicker(String symbol) {
        String subscriptionMessage = String.format(
            "{\"method\": \"SUBSCRIBE\", \"params\": [\"%s@ticker\"], \"id\": 1}",
            symbol.toLowerCase()
        );
        subscribe(subscriptionMessage);
    }
}
