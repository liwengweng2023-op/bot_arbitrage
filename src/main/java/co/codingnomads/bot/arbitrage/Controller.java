package co.codingnomads.bot.arbitrage;

import co.codingnomads.bot.arbitrage.action.arbitrage.ArbitragePrintAction;
import co.codingnomads.bot.arbitrage.action.arbitrage.ArbitrageTradingAction;
import co.codingnomads.bot.arbitrage.action.arbitrage.ArbitrageEmailAction;
import co.codingnomads.bot.arbitrage.action.detection.DetectionLogAction;
import co.codingnomads.bot.arbitrage.action.detection.DetectionPrintAction;
import co.codingnomads.bot.arbitrage.exception.ExchangeDataException;
import co.codingnomads.bot.arbitrage.exception.EmailLimitException;
import co.codingnomads.bot.arbitrage.exception.WaitTimeException;
import co.codingnomads.bot.arbitrage.exchange.BinanceSpecs;
import co.codingnomads.bot.arbitrage.exchange.ExchangeSpecs;
// import co.codingnomads.bot.arbitrage.exchange.HuobiSpecs; // 暂时移除
import co.codingnomads.bot.arbitrage.service.websocket.StreamingArbitrageService;
import co.codingnomads.bot.arbitrage.service.detection.Detection;
import co.codingnomads.bot.arbitrage.service.detection.DetectionService;
import co.codingnomads.bot.arbitrage.service.email.EmailService;
import co.codingnomads.bot.arbitrage.service.arbitrage.Arbitrage;
import co.codingnomads.bot.arbitrage.service.tradehistory.TradeHistoryService;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;


/**
 * Created by Thomas Leruth on 12/11/17
 *
 * Controller for running the arbitrage or detection bot
 */

@Service
public class Controller {


    @Autowired
    Arbitrage arbitrage;

    @Autowired
    Detection detection;

    @Autowired
    TradeHistoryService tradeHistoryService;

    @Autowired
    EmailService emailService;

    @Autowired
    DetectionService detectionService;

    @Autowired
    ArbitrageTradingAction arbitrageTradingAction;

    @Autowired
    ArbitrageEmailAction arbitrageEmailAction;

    @Autowired
    ArbitragePrintAction arbitragePrintAction;
    
    // 模拟数据模式
    private boolean useMockData = true;

    @Autowired
    DetectionLogAction detectionLogAction;

    @Autowired
    DetectionPrintAction detectionPrintAction;

    @Autowired
    StreamingArbitrageService streamingArbitrageService;


    /**
     * runBot method, choose one of the three arbitrage actions or two detection actions to run
     * choose the exchange specifications you would like to use, for arbitrage trade action you will need to manually add your api key,
     * api secret key and other exchange specifications depending on the exchange. For all arbitrage actions you have to option to manually set
     * the number of times you would like the method to run (loopIterations) and the time interval you would like the action to repeat at in milliseconds,
     * must be at least 5000. for all arbitrage actions you must set the arbitrage margin or percentage of return you would like to check for form the arbitrage.
     * For example a 0.03 would equal a 0.03 percent price difference between the exchanges.You must also pass the arbitrage method the currency pair you would like
     * to check for. For arbitrage trade action you must set the trade value base or amount of base currency you would like to trade, for example the currency
     * pair ETH_BTC the base currency is ETH. For best results please make sure you are trading the minimum amount of base currency set by your exchange, and you
     * have enough counter currency to purchase the min amount of base currency. Check the exchanges you are attempting to use. For arbitrage email action, enter your
     * email address that you would like to receive, must be verified by aws. for Detection actions, in addition to setting exchange list, choose currency pairs, for
     * best results make sure that each exchange you selects supports the currency pairs you select. For detection log action set the time interval
     * you want the send results to the database.
     * @throws IOException
     * @throws InterruptedException
     * @throws EmailLimitException
     * @throws WaitTimeException
     * @throws ExchangeDataException
     */

    public void runBot() throws IOException, InterruptedException, EmailLimitException, WaitTimeException, ExchangeDataException{
        if (useMockData) {
            System.out.println("\n=== 交易所套利信息 ===");
            System.out.println("【币安 Binance】");
            System.out.println("  ETH/USDT 买一价: 1800.00");
            System.out.println("  ETH/USDT 卖一价: 1801.50");
            System.out.println("  24小时交易量: 25,000 ETH");
            
            System.out.println("\n【火币 Huobi】");
            System.out.println("  ETH/USDT 买一价: 1799.80");
            System.out.println("  ETH/USDT 卖一价: 1801.20");
            System.out.println("  24小时交易量: 28,500 ETH");
            
            System.out.println("\n=== 套利机会分析 ===");
            System.out.println("  最佳买入价: 火币 1799.80 USDT");
            System.out.println("  最佳卖出价: 币安 1801.50 USDT");
            System.out.println("  理论套利空间: 0.09% (1.7 USDT)");
            System.out.println("  考虑手续费后收益: 0.04% (0.7 USDT)");
            
            System.out.println("\n=== 风险提示 ===");
            System.out.println("  1. 以上数据为模拟数据，仅供参考");
            System.out.println("  2. 实际交易请考虑滑点和市场波动风险");
            System.out.println("  3. 建议套利空间大于0.5%再执行交易");
            System.out.println("\n=== 模拟数据模式结束 ===\n");
            return;
        }

        // 真实数据模式
        ArrayList<ExchangeSpecs> ExchangeList = new ArrayList<>();
        // 只保留币安交易所进行差价对比
        ExchangeList.add(new BinanceSpecs());
        // ExchangeList.add(new HuobiSpecs()); // 暂时移除
        
        // 其他交易所已注释
        // ExchangeList.add(new KrakenSpecs());
        // ExchangeList.add(new BittrexSpecs());
        // ExchangeList.add(new PoloniexSpecs());
        // ExchangeList.add(new GeminiSpecs());

        //choose one and only one of the following Arbitrage, WebSocket Streaming, or Detection trade actions

//WebSocket Streaming Arbitrage (实时监控)
        // 启用WebSocket实时套利监测
//        streamingArbitrageService.startArbitrageDetection();

//Arbitrage

        //optional set how many times you would like the arbitrage action to run, if null will run once
//        arbitrage.setLoopIterations(5);

        //optional set to the time interval you would like the arbitrage action to rerun in milliseconds
        //if not set, the action will run every 5 seconds untill the loopIteration is complete
//        arbitrage.setTimeIntervalRepeater(5000);


//        Example of an Arbitrage trade action
//        arbitrageTradingAction.setArbitrageMargin(0.03);
//        arbitrageTradingAction.setTradeValueBase(0.020);
//        arbitrage.run(
//                CurrencyPair.ETH_USD,
//                ExchangeList,
//                arbitrageTradingAction);


//      ExamplarbitragePrintAction = {ArbitragePrintAction@3598} e of an Arbitrage print action that finds the best trading pair every hour
      arbitragePrintAction.setArbitrageMargin(0.03);  //0.03 = 0.03 %
      arbitrage.run(
                    CurrencyPair.ETH_USD,
                    ExchangeList,
                    arbitragePrintAction);

//    Example of an Arbitrage email action
//    arbitrageEmailAction.setArbitrageMargin(0.03);
//    arbitrageEmailAction.getEmail().setTO("your-email-address");
//    emailService.insertEmailRecords(arbitrageEmailAction.getEmail());
//    arbitrage.run(
//                CurrencyPair.ETH_USD,
//                ExchangeList,
//                arbitrageEmailAction);



//Detection

        //List of currencyPairs you would like to check, for Detection only
//      ArrayList<CurrencyPair> currencyPairList = new ArrayList<>();
//      currencyPairList.add(CurrencyPair.ETH_USD);
//      currencyPairList.add(CurrencyPair.ETH_BTC);
//      currencyPairList.add(CurrencyPair.BTC_USD);

//    Example of a Detection print action
//    DetectionActionSelection detectionActionSelection = new DetectionPrintAction();
//    detection.run(currencyPairList, ExchangeList, detectionActionSelection);

//    Example of a Detection log action
//    DetectionActionSelection detectionActionSelection1 = new DetectionLogAction(60000);
//    detection.run(currencyPairList, ExchangeList, detectionActionSelection1);


    }

    /**
     * 启动WebSocket实时套利监测
     * 使用XChange-stream 4.4.2进行币安和火币的实时价格监控
     * @throws Exception
     */
    public void runWebSocketArbitrage() throws Exception {
        System.out.println("🚀 启动WebSocket实时套利监测服务...");
        System.out.println("📊 监控交易所: 币安 (Binance)");
        System.out.println("💱 监控交易对: ETH/USDT");
        System.out.println("⚡ 实时数据流: WebSocket连接");
        System.out.println("💡 按 Ctrl+C 停止服务");
        
        // 启动WebSocket实时套利监测
        streamingArbitrageService.startArbitrageDetection();
    }
}

