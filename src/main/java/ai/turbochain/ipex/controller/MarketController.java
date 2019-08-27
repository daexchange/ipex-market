package ai.turbochain.ipex.controller;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import ai.turbochain.ipex.constant.SysConstant;
import ai.turbochain.ipex.entity.CoinThumb;
import ai.turbochain.ipex.entity.ExchangeCoin;
import ai.turbochain.ipex.entity.ExchangeTrade;
import ai.turbochain.ipex.entity.KLine;
import ai.turbochain.ipex.entity.TradePlateItem;
import ai.turbochain.ipex.processor.CoinProcessor;
import ai.turbochain.ipex.processor.CoinProcessorFactory;
import ai.turbochain.ipex.service.CoinService;
import ai.turbochain.ipex.service.ExchangeCoinService;
import ai.turbochain.ipex.service.ExchangeCoinSettlementService;
import ai.turbochain.ipex.service.ExchangeTradeService;
import ai.turbochain.ipex.service.MarketService;
import ai.turbochain.ipex.util.MessageResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class MarketController {
    @Autowired
    private MarketService marketService;
    @Autowired
    private ExchangeCoinService exchangeCoinService;
    @Autowired
    private CoinProcessorFactory coinProcessorFactory;
    @Autowired
    private ExchangeTradeService exchangeTradeService;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private CoinService coinService;
    @Autowired
    private ExchangeCoinSettlementService exchangeCoinSettlementService;
   
    /**
     * 获取支持的交易币种
     * @return
     */
    @RequestMapping("symbol")
    public List<ExchangeCoin> findAllSymbol(){
        List<ExchangeCoin> coins = exchangeCoinService.findAllEnabled();
        return coins;
    }

    @RequestMapping("overview")
    public Map<String,List<CoinThumb>> overview(){
        log.info("/market/overview");
        Map<String,List<CoinThumb>> result = new HashMap<>();
        List<ExchangeCoin> recommendCoin = exchangeCoinService.findAllByFlag(1);
        List<CoinThumb> recommendThumbs = new ArrayList<>();
        for(ExchangeCoin coin:recommendCoin){
            CoinProcessor processor = coinProcessorFactory.getProcessor(coin.getSymbol());
            if(processor!= null) {
                CoinThumb thumb = processor.getThumb();
                recommendThumbs.add(thumb);
            }
        }
        result.put("recommend",recommendThumbs);
        List<CoinThumb> allThumbs = findSymbolThumb(null,null);
        Collections.sort(allThumbs, (o1, o2) -> o2.getChg().compareTo(o1.getChg()));
        int limit = allThumbs.size() > 5 ? 5 : allThumbs.size();
        result.put("changeRank",allThumbs.subList(0,limit));
        return result;
    }


    /**
     * 获取某交易对详情
     * @param symbol
     * @return
     */
    @RequestMapping("symbol-info")
    public ExchangeCoin findSymbol(String symbol){
        ExchangeCoin coin = exchangeCoinService.findBySymbol(symbol);
        return coin;
    }

    /**
     * 获取币种缩略行情
     * @return
     */
    @RequestMapping("symbol-thumb")
    public List<CoinThumb> findSymbolThumb(String coinSymbol,String basecion){
    	List<ExchangeCoin> coins = null;
    	 
    	if (StringUtils.isNotBlank(coinSymbol)||
    			StringUtils.isNotBlank(basecion)) {
    		coins = exchangeCoinService.findAllEnabled(coinSymbol,basecion);
    	} else {
    		coins = exchangeCoinService.findAllEnabled();
    	}
       
        List<CoinThumb> thumbs = new ArrayList<>();
        for(ExchangeCoin coin:coins){
            CoinProcessor processor = coinProcessorFactory.getProcessor(coin.getSymbol());
            if (processor==null) {
            	continue;
            }
            CoinThumb thumb = processor.getThumb();
            thumbs.add(thumb);
        }
        return thumbs;
    }

    @RequestMapping("symbol-thumb-trend")
    public JSONArray findSymbolThumbWithTrend(){
        List<ExchangeCoin> coins = exchangeCoinService.findAllEnabled();
        //List<CoinThumb> thumbs = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        //将秒、微秒字段置为0
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        calendar.set(Calendar.MINUTE,0);
        long nowTime = calendar.getTimeInMillis();
        calendar.add(Calendar.HOUR_OF_DAY,-24);
        JSONArray array = new JSONArray();
        long firstTimeOfToday = calendar.getTimeInMillis();
        for(ExchangeCoin coin:coins){
            CoinProcessor processor = coinProcessorFactory.getProcessor(coin.getSymbol());
            CoinThumb thumb = processor.getThumb();
            JSONObject json = (JSONObject) JSON.toJSON(thumb);
            json.put("zone",coin.getZone());
            List<KLine> lines = marketService.findAllKLine(thumb.getSymbol(),firstTimeOfToday,nowTime,"1hour");
            JSONArray trend = new JSONArray();
            for(KLine line:lines){
                trend.add(line.getClosePrice());
            }
            json.put("trend",trend);
            array.add(json);
        }
        return array;
    }

    
    /**
     * 获取币种历史K线
     * @param symbol
     * @param from
     * @param to
     * @param resolution
     * @return
     */
    @RequestMapping("history")
    public List<KLine> findKHistory(String symbol,long from,long to,String resolution){
    	
    	String period = null;
        
        if ("1H".equals(resolution) || resolution.endsWith("h")) {//按小时
            period = "1hour";
        } else if("1D".equals(resolution) || resolution.endsWith("d")){//按天
            period = "1day";
        } else if("1W".equals(resolution) || resolution.endsWith("w")){//按周
            period = "1week";
        } else if("1M".equals(resolution) || resolution.endsWith("m")){//按月
            period = "1month";
        } else {
            Integer val = Integer.parseInt(resolution);
            if(val == 1) { // 1分钟
                period = resolution + "min";
            } else if(val == 60)  {// 按时
                period =  "1hour";
            } else {// 按分钟
                period =  val+"min";
            }
            List<KLine> list = marketService.findAllKLine(symbol,from,to,period);

            return list;
        } 
        List<KLine> list = marketService.findAllKLineByType(symbol,period);
        
    	return list;
    }

    
    /**
     * 查询最近成交记录
     * @param symbol 交易对符号
     * @param size 返回记录最大数量
     * @return
     */
    @RequestMapping("latest-trade")
    public List<ExchangeTrade> latestTrade(String symbol, int size){
        return exchangeTradeService.findLatest(symbol,size);
    }

    @RequestMapping("exchange-plate")
    public Map<String,List<TradePlateItem>> findTradePlate(String symbol){
        //远程RPC服务URL,后缀为币种单位
        String serviceName = "SERVICE-EXCHANGE-TRADE";
        String url = "http://" + serviceName + "/monitor/plate?symbol="+symbol;
        ResponseEntity<HashMap> result = restTemplate.getForEntity(url, HashMap.class);
        return (Map<String, List<TradePlateItem>>) result.getBody();
    }


    @RequestMapping("exchange-plate-mini")
    public Map<String,JSONObject> findTradePlateMini(String symbol){
        //远程RPC服务URL,后缀为币种单位
        String serviceName = "SERVICE-EXCHANGE-TRADE";
        String url = "http://" + serviceName + "/monitor/plate-mini?symbol="+symbol;
        ResponseEntity<HashMap> result = restTemplate.getForEntity(url, HashMap.class);
        return (Map<String, JSONObject>) result.getBody();
    }


    @RequestMapping("exchange-plate-full")
    public Map<String,JSONObject> findTradePlateFull(String symbol){
        //远程RPC服务URL,后缀为币种单位
        String serviceName = "SERVICE-EXCHANGE-TRADE";
        String url = "http://" + serviceName + "/monitor/plate-full?symbol="+symbol;
        ResponseEntity<HashMap> result = restTemplate.getForEntity(url, HashMap.class);
        return (Map<String,JSONObject>) result.getBody();
    }

    @GetMapping("add_dcitionary/{bond}/{value}")
    public MessageResult addDcitionaryForAdmin(@PathVariable("bond") String bond,@PathVariable("value") String value){
        log.info(">>>>字典表数据已修改>>>修改缓存中的数据>>>>>bond>"+bond+">>>>>value>>"+value);
        String key = SysConstant.DATA_DICTIONARY_BOUND_KEY+bond;
        ValueOperations valueOperations = redisTemplate.opsForValue();
        Object bondvalue =valueOperations.get(key );
        if(bondvalue==null){
            log.info(">>>>>>缓存中无数据>>>>>");
            valueOperations.set(key,value);
        }else{
           log.info(">>>>缓存中有数据>>>");
           valueOperations.getOperations().delete(key);
           valueOperations.set(key,value);
        }
        MessageResult re = new MessageResult();
        re.setCode(0);
        re.setMessage("success");
        return re;
    }
    
   
    /**
     * 获取支持的交易币种
     * @return
     */
    @RequestMapping("/base-symbols")
    public Set<String> findAllBaseSymbol(){
        String key = SysConstant.DATA_COIN_SETTLEMENT_KEY;
        
        ValueOperations valueOperations = redisTemplate.opsForValue();
        
        Set<String> symbols = (Set<String>) valueOperations.get(key);
        
        if (symbols==null) {
            log.info(">>>>>>缓存中无数据>>>>>");
            //symbols = coinService.findAllUnit(CommonStatus.NORMAL);
            symbols = exchangeCoinSettlementService.findSymbols();
          //  coins.forEach(exchangeCoin-> set.add(exchangeCoin.getBaseSymbol()));
            valueOperations.set(key,symbols,2,TimeUnit.HOURS);
        } else {
            log.info(">>>>缓存中有数据>>>");
        }

        return symbols;
    }
    
    /**
     * 获取币种缩略行情
     * @return
     */
    @RequestMapping("/get-symbols-thumb")
    public List<CoinThumb> findSymbolsThumb(String[] symbols){
    	 
        List<CoinThumb> thumbs = new ArrayList<>();
        
        for(String symbol:symbols){
            CoinProcessor processor = coinProcessorFactory.getProcessor(symbol);
            CoinThumb thumb = processor.getThumb();
            thumbs.add(thumb);
        }
        
        return thumbs;
    }
    
}
