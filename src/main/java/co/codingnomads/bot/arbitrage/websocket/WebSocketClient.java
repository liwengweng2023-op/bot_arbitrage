package co.codingnomads.bot.arbitrage.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Generic WebSocket client for cryptocurrency exchange connections
 */
class ExchangeWebSocketClient extends WebSocketClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(ExchangeWebSocketClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Consumer<JsonNode>> messageHandlers = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> connectionFuture = new CompletableFuture<>();
    private volatile boolean isConnected = false;

    public ExchangeWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        LOG.info("WebSocket connection opened to {}", getURI());
        isConnected = true;
        connectionFuture.complete(null);
    }

    @Override
    public void onMessage(String message) {
        try {
            LOG.debug("Received WebSocket message: {}", message);
            JsonNode jsonMessage = objectMapper.readTree(message);
            
            // Check for different message formats
            boolean isTickerMessage = false;
            JsonNode tickerData = null;
            
            // Binance format: {"e":"24hrTicker", ...}
            if (jsonMessage.has("e") && "24hrTicker".equals(jsonMessage.get("e").asText())) {
                isTickerMessage = true;
                tickerData = jsonMessage;
                LOG.debug("Detected Binance ticker message");
            }
            // Stream format: {"stream":"ethusdt@ticker", "data":{...}}
            else if (jsonMessage.has("stream") && jsonMessage.get("stream").asText().contains("@ticker")) {
                isTickerMessage = true;
                tickerData = jsonMessage.get("data");
                LOG.debug("Detected stream ticker message");
            }
            // Kraken format: array with ticker data
            else if (jsonMessage.isArray() && jsonMessage.size() > 1) {
                isTickerMessage = true;
                tickerData = jsonMessage;
                LOG.debug("Detected Kraken ticker message");
            }
            // Huobi format: {"ch":"market.ethusdt.ticker", "tick":{...}}
            else if (jsonMessage.has("ch") && jsonMessage.get("ch").asText().contains("ticker") && jsonMessage.has("tick")) {
                isTickerMessage = true;
                tickerData = jsonMessage;
                LOG.debug("Detected Huobi ticker message");
            }
            
            if (isTickerMessage && tickerData != null && messageHandlers.containsKey("ticker")) {
                LOG.debug("Processing ticker data: {}", tickerData);
                messageHandlers.get("ticker").accept(tickerData);
            } else if (!jsonMessage.has("event")) { // Ignore heartbeat messages
                LOG.debug("Message type not recognized as ticker: {}", jsonMessage);
            }
        } catch (Exception e) {
            LOG.error("Error processing WebSocket message: {}", message, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOG.info("WebSocket connection closed: {} - {}", code, reason);
        isConnected = false;
    }

    @Override
    public void onError(Exception ex) {
        LOG.error("WebSocket error", ex);
        isConnected = false;
        if (!connectionFuture.isDone()) {
            connectionFuture.completeExceptionally(ex);
        }
    }

    /**
     * Register a message handler for specific message types
     */
    public void registerMessageHandler(String messageType, Consumer<JsonNode> handler) {
        messageHandlers.put(messageType, handler);
    }

    /**
     * Wait for connection to be established
     */
    public CompletableFuture<Void> waitForConnection() {
        return connectionFuture;
    }

    /**
     * Check if WebSocket is connected
     */
    public boolean isConnected() {
        return isConnected && !isClosed();
    }

    /**
     * Send subscription message
     */
    public void subscribe(String subscriptionMessage) {
        if (isConnected()) {
            send(subscriptionMessage);
            LOG.debug("Sent subscription: {}", subscriptionMessage);
        } else {
            LOG.warn("Cannot subscribe - WebSocket not connected");
        }
    }
}
