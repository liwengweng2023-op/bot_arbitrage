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
 * Gemini WebSocket client for real-time ticker data
 */
public class GeminiWebSocketClient extends ExchangeWebSocketClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(GeminiWebSocketClient.class);
    private static final String GEMINI_WS_URL = "wss://api.gemini.com/v1/marketdata";
    
    private Consumer<TickerData> tickerUpdateCallback;
    private ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();
    private Exchange exchange;

    public GeminiWebSocketClient() {
        super(java.net.URI.create(GEMINI_WS_URL));
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
        LOG.info("Gemini WebSocket connection opened");
        
        // Register message handler for ticker updates
        registerMessageHandler("l2_updates", this::handleTickerUpdate);
    }

    private void handleTickerUpdate(JsonNode tickerData) {
        try {
            LOG.debug("Processing Gemini ticker data: {}", tickerData);
            
            String symbol = tickerData.get("symbol").asText();
            
            // Process changes array for bid/ask updates
            if (tickerData.has("changes")) {
                JsonNode changes = tickerData.get("changes");
                BigDecimal bestBid = null;
                BigDecimal bestAsk = null;
                
                for (JsonNode change : changes) {
                    String side = change.get("side").asText();
                    BigDecimal price = new BigDecimal(change.get("price").asText());
                    
                    if ("bid".equals(side)) {
                        bestBid = price;
                    } else if ("ask".equals(side)) {
                        bestAsk = price;
                    }
                }
                
                if (bestBid != null && bestAsk != null) {
                    TickerData ticker = new TickerData();
                    ticker.setBid(bestBid);
                    ticker.setAsk(bestAsk);
                    ticker.setLast(bestBid); // Use bid as last price approximation
                    ticker.setExchange(exchange);
                    
                    // Cache the ticker data
                    tickerCache.put(symbol, ticker);
                    
                    LOG.debug("Updated Gemini ticker for {}: bid={}, ask={}", symbol, bestBid, bestAsk);
                    
                    // Notify callback if set
                    if (tickerUpdateCallback != null) {
                        tickerUpdateCallback.accept(ticker);
                    }
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error processing Gemini ticker update", e);
        }
    }

    public TickerData getLatestTicker(String symbol) {
        return tickerCache.get(symbol);
    }

    public void subscribeToTicker(String symbol) {
        // Gemini uses lowercase format like "ethusd"
        String geminiSymbol = symbol.replace("/", "").toLowerCase();
        
        String subscriptionMessage = String.format(
            "{\"type\": \"subscribe\", \"subscriptions\": [{\"name\": \"l2\", \"symbols\": [\"%s\"]}]}",
            geminiSymbol
        );
        
        if (isConnected()) {
            send(subscriptionMessage);
            LOG.info("Subscribed to Gemini ticker for symbol: {}", geminiSymbol);
        } else {
            LOG.warn("Cannot subscribe to Gemini ticker - not connected");
        }
    }

}
