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
 * GDAX/Coinbase Pro WebSocket client for real-time ticker data
 */
public class GDAXWebSocketClient extends ExchangeWebSocketClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(GDAXWebSocketClient.class);
    private static final String GDAX_WS_URL = "wss://ws-feed.pro.coinbase.com";
    
    private Consumer<TickerData> tickerUpdateCallback;
    private ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();
    private Exchange exchange;

    public GDAXWebSocketClient() {
        super(java.net.URI.create(GDAX_WS_URL));
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
        LOG.info("GDAX WebSocket connection opened");
        
        // Register message handler for ticker updates
        registerMessageHandler("ticker", this::handleTickerUpdate);
    }

    private void handleTickerUpdate(JsonNode tickerData) {
        try {
            LOG.debug("Processing GDAX ticker data: {}", tickerData);
            
            String productId = tickerData.get("product_id").asText();
            BigDecimal bestBid = new BigDecimal(tickerData.get("best_bid").asText());
            BigDecimal bestAsk = new BigDecimal(tickerData.get("best_ask").asText());
            BigDecimal price = new BigDecimal(tickerData.get("price").asText());
            
            TickerData ticker = new TickerData();
            ticker.setBid(bestBid);
            ticker.setAsk(bestAsk);
            ticker.setLast(price);
            ticker.setExchange(exchange);
            
            // Cache the ticker data
            tickerCache.put(productId, ticker);
            
            LOG.debug("Updated GDAX ticker for {}: bid={}, ask={}", productId, bestBid, bestAsk);
            
            // Notify callback if set
            if (tickerUpdateCallback != null) {
                tickerUpdateCallback.accept(ticker);
            }
            
        } catch (Exception e) {
            LOG.error("Error processing GDAX ticker update", e);
        }
    }

    public TickerData getLatestTicker(String symbol) {
        return tickerCache.get(symbol);
    }

    public void subscribeToTicker(String symbol) {
        // GDAX uses product_id format like "ETH-USD"
        String productId = symbol.replace("/", "-");
        
        String subscriptionMessage = String.format(
            "{\"type\": \"subscribe\", \"product_ids\": [\"%s\"], \"channels\": [\"ticker\"]}",
            productId
        );
        
        if (isConnected()) {
            send(subscriptionMessage);
            LOG.info("Subscribed to GDAX ticker for product: {}", productId);
        } else {
            LOG.warn("Cannot subscribe to GDAX ticker - not connected");
        }
    }

}
