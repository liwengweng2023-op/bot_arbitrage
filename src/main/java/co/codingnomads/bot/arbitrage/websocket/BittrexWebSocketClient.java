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
 * Bittrex WebSocket client for real-time ticker data
 */
public class BittrexWebSocketClient extends ExchangeWebSocketClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(BittrexWebSocketClient.class);
    private static final String BITTREX_WS_URL = "wss://socket-v3.bittrex.com/signalr";
    
    private Consumer<TickerData> tickerUpdateCallback;
    private ConcurrentHashMap<String, TickerData> tickerCache = new ConcurrentHashMap<>();
    private Exchange exchange;

    public BittrexWebSocketClient() {
        super(java.net.URI.create(BITTREX_WS_URL));
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
        LOG.info("Bittrex WebSocket connection opened");
        
        // Register message handler for ticker updates
        registerMessageHandler("ticker", this::handleTickerUpdate);
    }

    private void handleTickerUpdate(JsonNode tickerData) {
        try {
            LOG.debug("Processing Bittrex ticker data: {}", tickerData);
            
            String symbol = tickerData.get("symbol").asText();
            BigDecimal bidRate = new BigDecimal(tickerData.get("bidRate").asText());
            BigDecimal askRate = new BigDecimal(tickerData.get("askRate").asText());
            BigDecimal lastTradeRate = new BigDecimal(tickerData.get("lastTradeRate").asText());
            
            TickerData ticker = new TickerData();
            ticker.setBid(bidRate);
            ticker.setAsk(askRate);
            ticker.setLast(lastTradeRate);
            ticker.setExchange(exchange);
            
            // Cache the ticker data
            tickerCache.put(symbol, ticker);
            
            LOG.debug("Updated Bittrex ticker for {}: bid={}, ask={}", symbol, bidRate, askRate);
            
            // Notify callback if set
            if (tickerUpdateCallback != null) {
                tickerUpdateCallback.accept(ticker);
            }
            
        } catch (Exception e) {
            LOG.error("Error processing Bittrex ticker update", e);
        }
    }

    public TickerData getLatestTicker(String symbol) {
        return tickerCache.get(symbol);
    }

    public void subscribeToTicker(String symbol) {
        // Bittrex uses format like "ETH-USD"
        String bittrexSymbol = symbol.replace("/", "-");
        
        String subscriptionMessage = String.format(
            "{\"protocol\":\"json\",\"version\":1,\"type\":1,\"target\":\"Subscribe\",\"arguments\":[\"%s\"]}",
            "ticker_" + bittrexSymbol
        );
        
        if (isConnected()) {
            send(subscriptionMessage);
            LOG.info("Subscribed to Bittrex ticker for symbol: {}", bittrexSymbol);
        } else {
            LOG.warn("Cannot subscribe to Bittrex ticker - not connected");
        }
    }

}
