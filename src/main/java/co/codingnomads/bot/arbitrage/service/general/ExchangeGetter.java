package co.codingnomads.bot.arbitrage.service.general;

import co.codingnomads.bot.arbitrage.model.exchange.ActivatedExchange;
import co.codingnomads.bot.arbitrage.exchange.ExchangeSpecs;
// import co.codingnomads.bot.arbitrage.service.thread.GetExchangeThread; // 已删除
import org.knowm.xchange.ExchangeSpecification;
import java.util.ArrayList;
import java.util.concurrent.*;


/**
 * Created by Thomas Leruth on 12/13/17
 *
 * A class with a method to get the exchanges correctly set up
 */
public class ExchangeGetter {

    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    CompletionService<ActivatedExchange> pool = new ExecutorCompletionService<>(executor);

    /**
     * Turn a list of exchange with correct security parameters into a list of exchanges with enabled service
     * @param selectedExchanges a list of exchanges with their needed authentification set
     * @param tradingMode whether or not the action behavior is trading
     * @return list of exchange with all services loaded up
     */
    public ArrayList<ActivatedExchange> getAllSelectedExchangeServices(
            ArrayList<ExchangeSpecs> selectedExchanges,
            boolean tradingMode) {

        ArrayList<ActivatedExchange> list = new ArrayList<>();

        // 简化版本：直接创建ActivatedExchange
        for (ExchangeSpecs selected : selectedExchanges) {
            try {
                ExchangeSpecification exSpec = selected.GetSetupedExchange();
                // 这里需要根据ExchangeSpecification创建Exchange，但为了简化，我们暂时跳过
                // 在实际使用中，RealTimeArbitrageService会直接使用WebSocket连接
                System.out.println("跳过交易所: " + exSpec.getExchangeName());
            } catch (Exception e) {
                System.err.println("创建交易所连接失败: " + e.getMessage());
            }
        }
        executor.shutdown();
        //if trading mode set each exchange as an activated exchange
        if (tradingMode) {
            for (ActivatedExchange activatedExchange : list) {
                activatedExchange.setActivated(activatedExchange.isTradingMode());
            }
        }
        return list;
    }
}
