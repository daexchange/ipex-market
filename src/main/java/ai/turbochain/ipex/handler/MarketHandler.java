package ai.turbochain.ipex.handler;

import ai.turbochain.ipex.entity.CoinThumb;
import ai.turbochain.ipex.entity.ExchangeTrade;
import ai.turbochain.ipex.entity.KLine;

public interface MarketHandler {

    /**
     * 存储交易信息
     * @param exchangeTrade
     */
    void handleTrade(String symbol, ExchangeTrade exchangeTrade, CoinThumb thumb);


    /**
     * 存储K线信息
     *
     * @param kLine
     */
    void handleKLine(String symbol,KLine kLine);
}
