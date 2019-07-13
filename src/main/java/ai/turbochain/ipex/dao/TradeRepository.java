package ai.turbochain.ipex.dao;

import org.springframework.data.mongodb.repository.MongoRepository;

import ai.turbochain.ipex.entity.ExchangeTrade;

public interface TradeRepository extends MongoRepository<ExchangeTrade,Long>{
}
