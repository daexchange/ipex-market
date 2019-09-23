package ai.turbochain.ipex.service;


import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.mongodb.WriteResult;

import ai.turbochain.ipex.entity.ExchangeTrade;
import ai.turbochain.ipex.entity.KLine;
import ai.turbochain.ipex.entity.Page;

@Service
public class MarketService {
    @Autowired
    private MongoTemplate mongoTemplate;

    public List<KLine> findAllKLine(String symbol,String peroid){
        Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC,"time"));
        Query query = new Query().with(sort).limit(1000);

        return mongoTemplate.find(query,KLine.class,"exchange_kline_"+symbol+"_"+peroid);
    }

    public List<KLine> findAllKLine(String symbol,long fromTime,long toTime,String period){
        Criteria criteria = Criteria.where("time").gte(fromTime).andOperator(Criteria.where("time").lte(toTime));
        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC,"time"));
        Query query = new Query(criteria).with(sort);
        List<KLine> kLines = mongoTemplate.find(query,KLine.class,"exchange_kline_"+symbol+"_"+ period);
        return kLines;
    }
    
    public List<KLine> findAllKLineByType(String symbol,String period){
        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC,"time"));
        Query query = new Query().with(sort);
        List<KLine> kLines = mongoTemplate.find(query,KLine.class,"exchange_kline_"+symbol+"_"+ period);
        return kLines;
    }

    public ExchangeTrade findFirstTrade(String symbol,long fromTime,long toTime){
        Criteria criteria = Criteria.where("time").gte(fromTime).andOperator(Criteria.where("time").lte(toTime));
        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC,"time"));
        Query query = new Query(criteria).with(sort);
        return mongoTemplate.findOne(query,ExchangeTrade.class,"exchange_trade_"+symbol);
    }

    public ExchangeTrade findLastTrade(String symbol,long fromTime,long toTime){
        Criteria criteria = Criteria.where("time").gte(fromTime).andOperator(Criteria.where("time").lte(toTime));
        Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC,"time"));
        Query query = new Query(criteria).with(sort);
        return mongoTemplate.findOne(query,ExchangeTrade.class,"exchange_trade_"+symbol);
    }

    public ExchangeTrade findTrade(String symbol, long fromTime, long toTime, Sort.Order order){
        Criteria criteria = Criteria.where("time").gte(fromTime).andOperator(Criteria.where("time").lte(toTime));
        Sort sort = new Sort(order);
        Query query = new Query(criteria).with(sort);
        return mongoTemplate.findOne(query,ExchangeTrade.class,"exchange_trade_"+symbol);
    }

    public List<ExchangeTrade> findTradeByTimeRange(String symbol, long timeStart, long timeEnd){
        Criteria criteria = Criteria.where("time").gte(timeStart).andOperator(Criteria.where("time").lt(timeEnd));
        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC,"time"));
        Query query = new Query(criteria).with(sort);

        return mongoTemplate.find(query,ExchangeTrade.class,"exchange_trade_"+symbol);
    }

    public void saveKLine(String symbol,KLine kLine){
        mongoTemplate.insert(kLine,"exchange_kline_"+symbol+"_"+kLine.getPeriod());
    }

    public int updateKLine(String symbol,KLine kLine){
    	 Query query = new Query();  
         query.addCriteria(Criteria.where("time").is(kLine.getTime()));  
        
         Update update = new Update();  
         update.addToSet("kLines", kLine);
         
         String collectionName = "exchange_kline_"+symbol+"_"+kLine.getPeriod();
         
         WriteResult writeResult = mongoTemplate.upsert(query, update, collectionName);
         
         return writeResult.getN();
    }
    
    /**
     * 查找某时间段内的交易量
     * @param symbol
     * @param timeStart
     * @param timeEnd
     * @return
     */
    public BigDecimal findTradeVolume(String symbol, long timeStart, long timeEnd){
        Criteria criteria = Criteria.where("time").gt(timeStart)
                .andOperator(Criteria.where("time").lte(timeEnd));
                //.andOperator(Criteria.where("volume").gt(0));
        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC,"time"));
        Query query = new Query(criteria).with(sort);
        List<KLine> kLines =  mongoTemplate.find(query,KLine.class,"exchange_kline_"+symbol+"_1min");
        BigDecimal totalVolume = BigDecimal.ZERO;
        for(KLine kLine:kLines){
            totalVolume = totalVolume.add(kLine.getVolume());
        }
        return totalVolume;
    }
    
    
    public Page findTradeByPage(String symbol, long timeStart, long timeEnd,
    		Integer currentPage, Integer pageSize) {
        Criteria criteria = Criteria.where("time").gte(timeStart).andOperator(Criteria.where("time").lt(timeEnd));
        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC,"time"));
        Query query = new Query(criteria).with(sort);
        //设置起始数
        query.skip((currentPage - 1) * pageSize);
        //设置查询条数
        query.limit(pageSize);

        String collectionName = "exchange_trade_"+symbol;
        List<ExchangeTrade> list = mongoTemplate.find(query,ExchangeTrade.class,collectionName);
        
        //查询总记录数
        long count = (long) mongoTemplate.count(query, collectionName);
        
        Page page = new Page();
        
        //添加每页的集合、数据总条数、总页数
        page.setData(list);
        page.setPageSize(count);
        page.setTotal(count % pageSize == 0 ? 1 : count / pageSize + 1);
       
        return page;
    }
    
}
