package ai.turbochain.ipex.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.turbochain.ipex.component.CoinExchangeRate;
import ai.turbochain.ipex.entity.ExchangeCoin;
import ai.turbochain.ipex.handler.MongoMarketHandler;
import ai.turbochain.ipex.handler.NettyHandler;
import ai.turbochain.ipex.handler.WebsocketMarketHandler;
import ai.turbochain.ipex.processor.CoinProcessor;
import ai.turbochain.ipex.processor.CoinProcessorFactory;
import ai.turbochain.ipex.processor.DefaultCoinProcessor;
import ai.turbochain.ipex.service.ExchangeCoinService;
import ai.turbochain.ipex.service.MarketService;

import java.util.List;

@Configuration
@Slf4j
public class ProcessorConfig {

    @Bean
    public CoinProcessorFactory processorFactory(MongoMarketHandler mongoMarketHandler,
                                                 WebsocketMarketHandler wsHandler,
                                                 NettyHandler nettyHandler,
                                                 MarketService marketService,
                                                 CoinExchangeRate exchangeRate,
                                                 ExchangeCoinService coinService) {

        log.info("====initialized CoinProcessorFactory start==================================");

        CoinProcessorFactory factory = new CoinProcessorFactory();
        List<ExchangeCoin> coins = coinService.findAllEnabled();
        log.info("exchange-coin result:{}",coins);

        for (ExchangeCoin coin : coins) {
            CoinProcessor processor = new DefaultCoinProcessor(coin.getSymbol(), coin.getBaseSymbol());
            processor.addHandler(mongoMarketHandler);
            processor.addHandler(wsHandler);
            processor.addHandler(nettyHandler);
            processor.setMarketService(marketService);
            processor.setExchangeRate(exchangeRate);
            factory.addProcessor(coin.getSymbol(), processor);
            log.info("new processor = ", processor);
        }

        log.info("====initialized CoinProcessorFactory completed====");
        log.info("CoinProcessorFactory = ", factory);
        exchangeRate.setCoinProcessorFactory(factory);
        return factory;
    }


}
