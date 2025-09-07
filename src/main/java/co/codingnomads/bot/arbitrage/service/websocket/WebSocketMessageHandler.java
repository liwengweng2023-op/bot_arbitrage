package co.codingnomads.bot.arbitrage.service.websocket;

/**
 * WebSocket消息处理器接口
 * 定义了处理来自交易所WebSocket的价格更新的契约
 */
public interface WebSocketMessageHandler {

    /**
     * 处理价格更新
     *
     * @param exchange  交易所名称
     * @param bestBid   最优买价
     * @param bestAsk   最优卖价
     */
    void handlePriceUpdate(String exchange, double bestBid, double bestAsk);
}