package ai.turbochain.ipex.job;

import static ai.turbochain.ipex.constant.SysConstant.RESET_PASSWORD_CODE_PREFIX;
import static org.springframework.util.Assert.notNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.turbochain.ipex.constant.SysConstant;
import ai.turbochain.ipex.util.HttpUtil;

/**
 * 数字货币行情
 * 
 * @author zmc
 *
 */
@Component
public class DigitalCurrencyMarketJob {

	@Autowired
	private RedisTemplate redisTemplate;

	/**
	 * 获取汇率
	 * 
	 * @return
	 * @throws Exception
	 */
	public static BigDecimal getRate() throws Exception {
		String m_rateResult = HttpUtil.sendGet("https://x.szsing.com/v2/quote/price/m_rate");
		JSONObject jsonObject = new JSONObject(m_rateResult);
		JSONObject data = jsonObject.getJSONObject("data");
		Iterator<?> keys = data.keys();
		String CNY = "0";
		while (keys.hasNext()) {
			String key = String.valueOf(keys.next());
			JSONObject m_rate = data.getJSONObject(key);
			CNY = m_rate.getString("CNY");
			if (CNY.isEmpty() == false) {
				break;
			}
		}
		return new BigDecimal(CNY);
	}

	/**
	 * 获取类别版本
	 * 
	 * @return
	 * @throws Exception
	 */
	public String getCategoryVersion() throws Exception {
		String m_custom = HttpUtil.sendGet("https://x.szsing.com/v2/quote/price/m_custom");
		JSONObject jsonObject = new JSONObject(m_custom);
		JSONObject data = jsonObject.getJSONObject("data");
		JSONObject category = data.getJSONObject("category");
		return category.getString("version");
	}

	/**
	 * 币market_ids
	 */
	private static Map<String, String> coinKeys = new HashMap<>();

	static {
		coinKeys.put("ETH", "7843");
		coinKeys.put("BTC", "7496");
		coinKeys.put("PWR", "");// 暂时定义PWR价格为2.000000人民币
	}

	/**
	 * 定时获取数字货币行情价格(人民币)
	 */
	@Scheduled(fixedRate = 60000)
	public void orgaDigitalCurrencyMarket() {
		try {
			ValueOperations valueOperations = redisTemplate.opsForValue();
			for (Map.Entry<String, String> coinKey : coinKeys.entrySet()) {
				if (coinKey.getValue() != null && coinKey.getValue().isEmpty() == false) {
					try {
						String json = "{\"market_ids\":[" + coinKey.getValue() + "],\"category_version\":\""
								+ this.getCategoryVersion() + "\"}";
						String m_custom = HttpUtil.sentJsonPost("https://x.szsing.com/v2/quote/price/m_custom", null,
								json);
						JSONObject jsonObject = new JSONObject(m_custom);
						JSONObject data = jsonObject.getJSONObject("data");
						JSONArray jsonArray = data.getJSONArray("tickers");
						String last = jsonArray.getJSONObject(0).optString("last");
						Double coinMarketPrice = DigitalCurrencyMarketJob.getRate().multiply(new BigDecimal(last))
								.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
						valueOperations.set(SysConstant.DIGITAL_CURRENCY_MARKET_PREFIX + coinKey.getKey(),
								coinMarketPrice);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					valueOperations.set(SysConstant.DIGITAL_CURRENCY_MARKET_PREFIX + coinKey.getKey(), 2.000000);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
