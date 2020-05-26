package ai.turbochain.ipex.job;

import ai.turbochain.ipex.constant.SysConstant;
import ai.turbochain.ipex.util.HttpUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
		coinKeys.put("USDT", "8660");
		coinKeys.put("EOS", "7505");
		coinKeys.put("PWR", "");// 暂时定义PWR价格为2.000000人民币
		coinKeys.put("ETE", "");// 暂时定义PWR价格为2.000000人民币
	}

	/**
	 * 定时获取数字货币行情价格(人民币)
	 */
	@Scheduled(fixedRate = 120000)
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
				} else if (coinKey.getKey().equals("PWR") == true) {
					valueOperations.set(SysConstant.DIGITAL_CURRENCY_MARKET_PREFIX + coinKey.getKey(), 2.000000);
				} /*
					 * else if (coinKey.getKey().equals("USDT") == true) { Double coinMarketPrice =
					 * DigitalCurrencyMarketJob.getRate().setScale(6, BigDecimal.ROUND_HALF_UP)
					 * .doubleValue();
					 * valueOperations.set(SysConstant.DIGITAL_CURRENCY_MARKET_PREFIX +
					 * coinKey.getKey(), coinMarketPrice); }
					 */else if (coinKey.getKey().equals("ETE") == true) {
					try {
						String result = HttpUtil.sendGet("https://senbit.com/api/x/v1/common/timestamp");
						String timestamp = new JSONObject(result).get("ms").toString();
						String data = "_=" + timestamp
								+ "&access=1ieffJyheRLCsYvgN6AEiU&method=GET&path=%2Fapi%2Fx%2Fv1%2Fmarket%2Ftickers&symbol=ETE%2FUSDT";
						String sign = DigitalCurrencyMarketJob.HMACSHA256(data, "0j7AOlxlMRUIi2rMuW81Rr");
						String url = "https://senbit.com/api/x/v1/market/tickers?symbol=ETE%2FUSDT&_=" + timestamp
								+ "&access=1ieffJyheRLCsYvgN6AEiU&sign=" + sign;
						result = HttpUtil.sendGet(url);
						JSONArray jsonArray = new JSONArray(result);
						String last = jsonArray.getJSONObject(0).optString("last");
						Double coinMarketPrice = DigitalCurrencyMarketJob.getRate().multiply(new BigDecimal(last))
								.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
						valueOperations.set(SysConstant.DIGITAL_CURRENCY_MARKET_PREFIX + coinKey.getKey(),
								coinMarketPrice);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String HMACSHA256(String data, String key) throws Exception {
		Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
		SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
		sha256_HMAC.init(secret_key);
		byte[] array = sha256_HMAC.doFinal(data.getBytes("UTF-8"));
		StringBuilder sb = new StringBuilder();
		for (byte item : array) {
			sb.append(Integer.toHexString((item & 0xFF) | 0x100).substring(1, 3));
		}
		return sb.toString().toLowerCase();
	}
}
