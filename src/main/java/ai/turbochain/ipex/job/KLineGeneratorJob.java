package ai.turbochain.ipex.job;

import java.util.Calendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.turbochain.ipex.processor.CoinProcessorFactory;
import ai.turbochain.ipex.util.DateUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 生成各时间段的K线信息
 *
 */
@Component
@Slf4j
public class KLineGeneratorJob {
    @Autowired
    private CoinProcessorFactory processorFactory;
    public static final String Period_1month = "1month";
    public static final String Period_1week = "1week";
    public static final String Period_1day = "1day";
    
    /**
     * 每分钟定时器，处理分钟K线
     */
    @Scheduled(cron = "0 * * * * *")
    public void handle5minKLine(){
        Calendar calendar = Calendar.getInstance();
        log.debug("分钟K线:{}",calendar.getTime());
        //将秒、微秒字段置为0
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        long time = calendar.getTimeInMillis();
        int minute = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        processorFactory.getProcessorMap().forEach((symbol,processor)->{
            log.debug("生成{}分钟k线:{}",symbol);
            //生成1分钟K线
            processor.autoGenerate();
            //更新24H成交量
            processor.update24HVolume(time);
            if(minute%5 == 0) {
                processor.generateKLine(5, Calendar.MINUTE, time);
            }
            if(minute%10 == 0){
                processor.generateKLine(10, Calendar.MINUTE,time);
            }
            if(minute%15 == 0){
                processor.generateKLine(15, Calendar.MINUTE,time);
            }
            if(minute%30 == 0){
                processor.generateKLine(30, Calendar.MINUTE,time);
            }
            if(hour == 0 && minute == 0){
                processor.resetThumb();
            }
        });
    }

    /**
     * 每小时运行
     */
    @Scheduled(cron = "0 0 * * * *")
    public void handleHourKLine(){
        processorFactory.getProcessorMap().forEach((symbol,processor)-> {
            Calendar calendar = Calendar.getInstance();
            log.info("小时K线:{}",calendar.getTime());
            //将秒、微秒字段置为0
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long time = calendar.getTimeInMillis();

            processor.generateKLine(1, Calendar.HOUR_OF_DAY, time);
        });
    }

    
    //@Scheduled(cron = "0 40 16 * * ? ")
   
    /**
         * 每日0点5分处理器，处理日K线
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void handleDayKLine2() {
    	System.out.println("=====按日统计报表程序启动======");
    	
    	final long startTick = DateUtil.getYestDayBeginTime();
    	final long endTick =  DateUtil.getTodayBeginTime();
		
		System.out.println(startTick + "========" + endTick);
    	
    	// TODO 需要修改
    	processorFactory.getProcessorMap().forEach((symbol,processor)->{
             processor.generateKLine2(startTick, endTick,Period_1day);
        });
    	
    	System.out.println("=====按日统计报表程序结束======");
    }
    
    
    /**
         * 每周一统计上周的交易数据
     * 
     */
    @Scheduled(cron = "0 10 0 ? * MON") 
    public void handleWeekKLine() {//每周一上午0:10触发 
    	System.out.println("=====按周统计报表程序启动======");
    	
    	final long startTick = DateUtil.getBeforeFirstWeekDate();
    	final long endTick =  DateUtil.getBeginDayOfWeek();
    	
		System.out.println(startTick + "========" + endTick);
    	
    	// TODO 需要修改
    	processorFactory.getProcessorMap().forEach((symbol,processor)->{
             processor.generateKLine2(startTick, endTick,Period_1week);
        });
    	
    	System.out.println("=====按周统计报表程序结束======");
    }
    
    
    /**
       * 每月一号统计上月的交易数据
     *  
     */
    @Scheduled(cron = "0 15 0 1 * ?")  
    public void handleMonthKLine() {//每月1日上午0:15触发
    	System.out.println("=====按月统计报表程序启动======");
    	
    	final long startTick = DateUtil.getBeforeFirstMonthDate();
    	final long endTick =  DateUtil.getFirstDate();
		 
		System.out.println(startTick + "========" + endTick);
    	
    	// TODO 需要修改
    	processorFactory.getProcessorMap().forEach((symbol,processor)->{
             processor.generateKLine2(startTick, endTick,Period_1month);
        });
    	 
    	System.out.println("=====按月统计报表程序结束======");
    }
}
