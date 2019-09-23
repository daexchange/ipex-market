package ai.turbochain.ipex.processor;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

import ai.turbochain.ipex.component.CoinExchangeRate;
import ai.turbochain.ipex.entity.CoinThumb;
import ai.turbochain.ipex.entity.ExchangeTrade;
import ai.turbochain.ipex.entity.KLine;
import ai.turbochain.ipex.handler.MarketHandler;
import ai.turbochain.ipex.service.MarketService;
import ai.turbochain.ipex.util.DateUtil;
import lombok.ToString;

/**
 * 默认交易处理器，产生1mK线信息
 */
@ToString
public class DefaultCoinProcessor implements CoinProcessor {
    private Logger logger = LoggerFactory.getLogger(DefaultCoinProcessor.class);
    private String symbol;
    private String baseCoin;
    private KLine currentKLine;
    private List<MarketHandler> handlers;
    private CoinThumb coinThumb;
    private MarketService service;
    private CoinExchangeRate coinExchangeRate;
    //是否暂时处理
    private Boolean isHalt = true;

    public DefaultCoinProcessor(String symbol, String baseCoin) {
        handlers = new ArrayList<>();
        createNewKLine();
        this.baseCoin = baseCoin;
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public void initializeThumb() {
        Calendar calendar = Calendar.getInstance();
        //将秒、微秒字段置为0
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long nowTime = calendar.getTimeInMillis();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        long firstTimeOfToday = calendar.getTimeInMillis();
        String period = "1min";
        logger.info("initializeThumb from {} to {}", firstTimeOfToday, nowTime);
        List<KLine> lines = service.findAllKLine(this.symbol, firstTimeOfToday, nowTime, period);
        coinThumb = new CoinThumb();
        synchronized (coinThumb) {
            coinThumb.setSymbol(symbol);
            for (KLine kline : lines) {
                if (kline.getOpenPrice().compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                if (coinThumb.getOpen().compareTo(BigDecimal.ZERO) == 0) {
                    coinThumb.setOpen(kline.getOpenPrice());
                }
                if (coinThumb.getHigh().compareTo(kline.getHighestPrice()) < 0) {
                    coinThumb.setHigh(kline.getHighestPrice());
                }
                if (kline.getLowestPrice().compareTo(BigDecimal.ZERO) > 0 && coinThumb.getLow().compareTo(kline.getLowestPrice()) > 0) {
                    coinThumb.setLow(kline.getLowestPrice());
                }
                if (kline.getClosePrice().compareTo(BigDecimal.ZERO) > 0) {
                    coinThumb.setClose(kline.getClosePrice());
                }
                coinThumb.setVolume(coinThumb.getVolume().add(kline.getVolume()));
                coinThumb.setTurnover(coinThumb.getTurnover().add(kline.getTurnover()));
            }
            coinThumb.setChange(coinThumb.getClose().subtract(coinThumb.getOpen()));
            if (coinThumb.getOpen().compareTo(BigDecimal.ZERO) > 0) {
                coinThumb.setChg(coinThumb.getChange().divide(coinThumb.getOpen(), 4, RoundingMode.UP));
            }
        }
    }

    public void createNewKLine() {
        currentKLine = new KLine();
        synchronized (currentKLine) {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            //1Min时间要是下一整分钟的
            calendar.add(Calendar.MINUTE, 1);
            currentKLine.setTime(calendar.getTimeInMillis());
            currentKLine.setPeriod("1min");
            currentKLine.setCount(0);
        }
    }

    /**
     * 00:00:00 时重置CoinThumb
     */
    @Override
    public void resetThumb() {
        logger.info("reset coinThumb");
        synchronized (coinThumb) {
            coinThumb.setOpen(BigDecimal.ZERO);
            coinThumb.setHigh(BigDecimal.ZERO);
            //设置昨收价格
            coinThumb.setLastDayClose(coinThumb.getClose());
            //coinThumb.setClose(BigDecimal.ZERO);
            coinThumb.setLow(BigDecimal.ZERO);
            coinThumb.setChg(BigDecimal.ZERO);
            coinThumb.setChange(BigDecimal.ZERO);
        }
    }

    @Override
    public void setExchangeRate(CoinExchangeRate coinExchangeRate) {
        this.coinExchangeRate = coinExchangeRate;
    }

    @Override
    public void update24HVolume(long time) {
        if(coinThumb!=null) {
            synchronized (coinThumb) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(time);
                calendar.add(Calendar.HOUR_OF_DAY, -24);
                long timeStart = calendar.getTimeInMillis();
                BigDecimal volume = service.findTradeVolume(this.symbol, timeStart, time);
                coinThumb.setVolume(volume.setScale(4, RoundingMode.DOWN));
            }
        }
    }

    @Override
    public void initializeUsdRate() {
        logger.info("symbol = {} ,baseCoin = {}",this.symbol,this.baseCoin);
        BigDecimal baseUsdRate = coinExchangeRate.getUsdRate(baseCoin);
        coinThumb.setBaseUsdRate(baseUsdRate);
        logger.info("setBaseUsdRate = ",baseUsdRate);
        BigDecimal multiply = coinThumb.getClose().multiply(coinExchangeRate.getUsdRate(baseCoin));
        logger.info("setUsdRate = ",multiply);
        coinThumb.setUsdRate(multiply);
    }


    @Override
    public void autoGenerate() {
        DateFormat df = new SimpleDateFormat("HH:mm:ss");
        logger.info("auto generate 1min kline in {},data={}", df.format(new Date(currentKLine.getTime())), JSON.toJSONString(currentKLine));
        if(coinThumb != null) {
            synchronized (currentKLine) {
                //没有成交价时存储上一笔成交价
                if (currentKLine.getOpenPrice().compareTo(BigDecimal.ZERO) == 0) {
                    currentKLine.setOpenPrice(coinThumb.getClose());
                    currentKLine.setLowestPrice(coinThumb.getClose());
                    currentKLine.setHighestPrice(coinThumb.getClose());
                    currentKLine.setClosePrice(coinThumb.getClose());
                }
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                currentKLine.setTime(calendar.getTimeInMillis());
                handleKLineStorage(currentKLine);
                createNewKLine();
            }
        }
    }

    @Override
    public void setIsHalt(boolean status) {
        this.isHalt = status;
    }

    @Override
    public void process(List<ExchangeTrade> trades) {
        if (!isHalt) {
            if (trades == null || trades.size() == 0) {
                return;
            }
            synchronized (currentKLine) {
                for (ExchangeTrade exchangeTrade : trades) {
                    //处理K线
                    processTrade(currentKLine, exchangeTrade);
                    //处理今日概况信息
                    logger.debug("处理今日概况信息");
                    handleThumb(exchangeTrade);
                    //存储并推送成交信息
                    handleTradeStorage(exchangeTrade);
                }
            }
        }
    }

    public void processTrade(KLine kLine, ExchangeTrade exchangeTrade) {
        if (kLine.getClosePrice().compareTo(BigDecimal.ZERO) == 0) {
            //第一次设置K线值
            kLine.setOpenPrice(exchangeTrade.getPrice());
            kLine.setHighestPrice(exchangeTrade.getPrice());
            kLine.setLowestPrice(exchangeTrade.getPrice());
            kLine.setClosePrice(exchangeTrade.getPrice());
        } else {
            kLine.setHighestPrice(exchangeTrade.getPrice().max(kLine.getHighestPrice()));
            kLine.setLowestPrice(exchangeTrade.getPrice().min(kLine.getLowestPrice()));
            kLine.setClosePrice(exchangeTrade.getPrice());
        }
        kLine.setCount(kLine.getCount() + 1);
        kLine.setVolume(kLine.getVolume().add(exchangeTrade.getAmount()));
        BigDecimal turnover = exchangeTrade.getPrice().multiply(exchangeTrade.getAmount());
        kLine.setTurnover(kLine.getTurnover().add(turnover));
    }

    public void processTrade(KLine kLine, KLine newKLine) {
        if (kLine.getClosePrice().compareTo(BigDecimal.ZERO) == 0) {
            //第一次设置K线值
            kLine.setOpenPrice(newKLine.getOpenPrice());
            kLine.setHighestPrice(newKLine.getHighestPrice());
            kLine.setLowestPrice(newKLine.getLowestPrice());
            kLine.setClosePrice(newKLine.getClosePrice());
        } else {
            kLine.setHighestPrice(newKLine.getHighestPrice().max(kLine.getHighestPrice()));
            kLine.setLowestPrice(newKLine.getLowestPrice().min(kLine.getLowestPrice()));
            kLine.setClosePrice(newKLine.getClosePrice());
        }
        kLine.setCount(kLine.getCount() + 1);
        kLine.setVolume(kLine.getVolume().add(newKLine.getVolume()));
        BigDecimal turnover = newKLine.getOpenPrice().multiply(newKLine.getVolume());
        kLine.setTurnover(kLine.getTurnover().add(turnover));
    }
    
    public void handleTradeStorage(ExchangeTrade exchangeTrade) {
        for (MarketHandler storage : handlers) {
            storage.handleTrade(symbol, exchangeTrade, coinThumb);
        }
    }

    public void handleKLineStorage(KLine kLine) {
        for (MarketHandler storage : handlers) {
            storage.handleKLine(symbol, kLine);
        }
    }

    public void handleThumb(ExchangeTrade exchangeTrade) {
        logger.info("handleThumb symbol = {}", this.symbol);
        synchronized (coinThumb) {
            if (coinThumb.getOpen().compareTo(BigDecimal.ZERO) == 0) {
                //第一笔交易记为开盘价
                coinThumb.setOpen(exchangeTrade.getPrice());
            }
            coinThumb.setHigh(exchangeTrade.getPrice().max(coinThumb.getHigh()));
            if (coinThumb.getLow().compareTo(BigDecimal.ZERO) == 0) {
                coinThumb.setLow(exchangeTrade.getPrice());
            } else {
                coinThumb.setLow(exchangeTrade.getPrice().min(coinThumb.getLow()));
            }
            coinThumb.setClose(exchangeTrade.getPrice());
            coinThumb.setVolume(coinThumb.getVolume().add(exchangeTrade.getAmount()).setScale(4, RoundingMode.UP));
            BigDecimal turnover = exchangeTrade.getPrice().multiply(exchangeTrade.getAmount()).setScale(4, RoundingMode.UP);
            coinThumb.setTurnover(coinThumb.getTurnover().add(turnover));
            BigDecimal change = coinThumb.getClose().subtract(coinThumb.getOpen());
            coinThumb.setChange(change);
            if (coinThumb.getOpen().compareTo(BigDecimal.ZERO) > 0) {
                coinThumb.setChg(change.divide(coinThumb.getOpen(), 4, BigDecimal.ROUND_UP));
            }
            if ("USDT".equalsIgnoreCase(baseCoin)) {
                logger.info("setUsdRate", exchangeTrade.getPrice());
                coinThumb.setUsdRate(exchangeTrade.getPrice());
            } else {

            }
            coinThumb.setBaseUsdRate(coinExchangeRate.getUsdRate(baseCoin));
            coinThumb.setUsdRate(exchangeTrade.getPrice().multiply(coinExchangeRate.getUsdRate(baseCoin)));
            logger.info("setUsdRate", exchangeTrade.getPrice().multiply(coinExchangeRate.getUsdRate(baseCoin)));
            logger.info("thumb = {}", coinThumb);
        }
    }

    @Override
    public void addHandler(MarketHandler storage) {
        handlers.add(storage);
    }


    @Override
    public CoinThumb getThumb() {
        return coinThumb;
    }

    @Override
    public void setMarketService(MarketService service) {
        this.service = service;
    }

    @Override
    public void generateKLine(int range, int field, long time) {
        Calendar calendar = Calendar.getInstance();
        
        calendar.setTimeInMillis(time);
        
        long endTick = calendar.getTimeInMillis();
        String endTime = DateUtil.dateToString(calendar.getTime());
        
        //往前推range个时间单位
        calendar.add(field, -range);
        
        String fromTime = DateUtil.dateToString(calendar.getTime());
        long startTick = calendar.getTimeInMillis();
        System.out.println("time range from " + fromTime + " to " + endTime);
       
        List<ExchangeTrade> exchangeTrades = service.findTradeByTimeRange(this.symbol, startTick, endTick);

        KLine kLine = new KLine();
        kLine.setTime(startTick);
       // kLine.setTime(endTick);
        String rangeUnit = "";
        if (field == Calendar.MINUTE) {
            rangeUnit = "min";
        } else if (field == Calendar.HOUR_OF_DAY) {
            rangeUnit = "hour";
        } else if (field == Calendar.WEEK_OF_YEAR) {
            rangeUnit = "week";
        } else if (field == Calendar.DAY_OF_YEAR) {
            rangeUnit = "day";
        } else if (field == Calendar.MONTH) {
            rangeUnit = "month";
        }
        kLine.setPeriod(range + rangeUnit);

        // 处理K线信息
        for (ExchangeTrade exchangeTrade : exchangeTrades) {
            processTrade(kLine, exchangeTrade);
        }
        service.saveKLine(symbol, kLine);
    }

    @Override
    public KLine getKLine() {
        return currentKLine;
    }
    
    
    public void generateKLine2(long startTick, long endTick,String period) {
    	
        List<ExchangeTrade> exchangeTrades = service.findTradeByTimeRange(this.symbol, startTick, endTick);

        KLine kLine = new KLine();
        
        kLine.setTime(startTick);
        kLine.setPeriod(period);

        // 处理K线信息
        for (ExchangeTrade exchangeTrade : exchangeTrades) {
            processTrade(kLine, exchangeTrade);
        }
        
        // 查询
        service.saveKLine(symbol,kLine);
    }
    
}