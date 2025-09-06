package co.codingnomads.bot.arbitrage.service.general;

import co.codingnomads.bot.arbitrage.model.exchange.ActivatedExchange;
import co.codingnomads.bot.arbitrage.model.ticker.TickerData;
// import co.codingnomads.bot.arbitrage.service.thread.GetTickerDataThread; // 已删除
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Created by Thomas Leruth on 12/13/17
 *
 * A class to get data from exchanges and format it correctly
 */
@Service
public class ExchangeDataGetter {

    private final static int TIMEOUT = 30;

    /**
     *
     * Get All the TickerData from the selected exchanged
     * @param activatedExchanges list of currently acrivated exchanges
     * @param currencyPair the pair the TickerData is seeked for
     * @param tradeValueBase the value of the trade if using the trading action as behavior
     * @return A list of TickerData for all the exchanges
     */
    public ArrayList<TickerData> getAllTickerData(ArrayList<ActivatedExchange> activatedExchanges,

                                                  CurrencyPair currencyPair,
                                                  BigDecimal tradeValueBase) {

        ArrayList<TickerData> list = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CompletionService<TickerData> pool = new ExecutorCompletionService<>(executor);

        // 简化版本：直接获取数据，不使用线程池
        for (ActivatedExchange activatedExchange : activatedExchanges) {
            if (activatedExchange.isActivated()) {
                try {
                    Ticker ticker = activatedExchange.getExchange().getMarketDataService().getTicker(currencyPair);
                    if (ticker != null) {
                        TickerData tickerData = new TickerData(currencyPair, activatedExchange.getExchange(), 
                            ticker.getBid(), ticker.getAsk());
                        list.add(tickerData);
                    }
                } catch (Exception e) {
                    System.err.println("获取交易所数据失败: " + e.getMessage());
                }
            }
        }

        executor.shutdown();

        return list;
    }

    /**
     * Takes an exchange and currency pairs throws them in a thread and calls the corresponding api to the exchange and
     * returns the bid and ask price for each currency pair. If the api call is longer than the timeout, the thread is terminated.
     * @param exchange
     * @param currencyPair
     * @return TickerData object
     * @throws TimeoutException if it takes longer than 30 seconds for the api to be called
     */
    public static TickerData getTickerData(Exchange exchange, CurrencyPair currencyPair) throws TimeoutException {

        final ExecutorService service = Executors.newSingleThreadExecutor();

        try {
            final Future<TickerData> f = service.submit(() -> {
                Ticker ticker = exchange.getMarketDataService().getTicker(currencyPair);
                return new TickerData(currencyPair, exchange, ticker.getBid(), ticker.getAsk());
            });

            return f.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            service.shutdown();
        }
    }
}



