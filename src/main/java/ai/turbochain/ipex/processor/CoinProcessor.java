package ai.turbochain.ipex.processor;

import ai.turbochain.ipex.component.CoinExchangeRate;
import ai.turbochain.ipex.entity.CoinThumb;
import ai.turbochain.ipex.entity.ExchangeTrade;
import ai.turbochain.ipex.entity.KLine;
import ai.turbochain.ipex.handler.MarketHandler;
import ai.turbochain.ipex.service.MarketService;

import java.util.List;

public interface CoinProcessor {

    void setIsHalt(boolean status);

    /**
     * 处理新生成的交易信息
     * @param trades
     * @return
     */
    void process(List<ExchangeTrade> trades);

    /**
     * 添加存储器
     * @param storage
     */
    void addHandler(MarketHandler storage);

    CoinThumb getThumb();

    void setMarketService(MarketService service);

    void generateKLine(int range, int field, long time);

    KLine getKLine();

    void initializeThumb();

    void autoGenerate();

    void resetThumb();

    void setExchangeRate(CoinExchangeRate coinExchangeRate);

    void update24HVolume(long time);

    void initializeUsdRate();
}
