package co.codingnomads.bot.arbitrage.websocket;

import co.codingnomads.bot.arbitrage.model.ticker.TickerData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knowm.xchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Poloniex WebSocket client for real-time ticker data
 */
public class PoloniexWebSocketClient extends ExchangeWebSocketClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(PoloniexWebSocketClient.class);
    private static final String POLONIEX_WS_URL = "wss://ws.poloniex.com/ws/public";
    
    private Consumer<TickerData> tickerUpdateCallback;
    private ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();
    private Exchange exchange;

    public PoloniexWebSocketClient() {
        super(java.net.URI.create(POLONIEX_WS_URL));
        this.exchange = exchange;
    }

    public void setTickerUpdateCallback(Consumer<TickerData> callback) {
        this.tickerUpdateCallback = callback;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public void onOpen(org.java_websocket.handshake.ServerHandshake handshake) {
        super.onOpen(handshake);
        LOG.info("Poloniex WebSocket connection opened");
        
        // Register message handler for ticker updates
        registerMessageHandler("ticker", this::handleTickerUpdate);
    }

    private void handleTickerUpdate(JsonNode tickerData) {
        try {
            LOG.debug("Processing Poloniex ticker data: {}", tickerData);
            
            if (tickerData.has("data")) {
                JsonNode data = tickerData.get("data");
                String symbol = data.get("symbol").asText();
                BigDecimal bid = new BigDecimal(data.get("bid").asText());
                BigDecimal ask = new BigDecimal(data.get("ask").asText());
                BigDecimal last = new BigDecimal(data.get("last").asText());
                
                TickerData ticker = new TickerData();
                ticker.setBid(bid);
                ticker.setAsk(ask);
                ticker.setLast(last);
                ticker.setExchange(exchange);
                
                // Cache the ticker data
                tickerCache.put(symbol, ticker);
                
                LOG.debug("Updated Poloniex ticker for {}: bid={}, ask={}", symbol, bid, ask);
                
                // Notify callback if set
                if (tickerUpdateCallback != null) {
                    tickerUpdateCallback.accept(ticker);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error processing Poloniex ticker update", e);
        }
    }

    public TickerData getLatestTicker(String symbol) {
        return tickerCache.get(symbol);
    }

    public void subscribeToTicker(String symbol) {
        // Poloniex uses format like "ETH_USDT"
        String poloniexSymbol = symbol.replace("/", "_");
        
        String subscriptionMessage = String.format(
            "{\"event\": \"subscribe\", \"channel\": [\"ticker\"], \"symbols\": [\"%s\"]}",
            poloniexSymbol
        );
        
        if (isConnected()) {
            send(subscriptionMessage);
            LOG.info("Subscribed to Poloniex ticker for symbol: {}", poloniexSymbol);
        } else {
            LOG.warn("Cannot subscribe to Poloniex ticker - not connected");
        }
    }

}
