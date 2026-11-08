package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Today's weather under the date line: the city is picked in Launcher settings (one list of Chinese
 * cities with their coordinates), the numbers come from Open-Meteo's free forecast API, and the last
 * answer stays in the settings file so the line still reads on a day the TV has no network.
 * One request every half hour at most, and only while Home is in front.
 */
final class Weather {

    /** City (Config.SETTINGS_NAME): the name from CITIES, absent means DEFAULT_CITY. */
    static final String KEY_CITY = "weather_city";
    private static final String KEY_TEMP = "weather_temp";
    private static final String KEY_CODE = "weather_code";
    private static final String KEY_AT = "weather_at";

    /** Open-Meteo is asked again at most this often; its forecast moves slowly. */
    static final long REFRESH_MS = 30 * 60 * 1000L;

    static final String DEFAULT_CITY = "成都";

    private static final int TIMEOUT_MS = 12_000;

    /** name, latitude, longitude — the Chinese cities people actually put on a TV. */
    static final String[][] CITIES = {
            {"成都", "30.5728", "104.0668"},
            {"北京", "39.9042", "116.4074"},
            {"上海", "31.2304", "121.4737"},
            {"广州", "23.1291", "113.2644"},
            {"深圳", "22.5431", "114.0579"},
            {"重庆", "29.5630", "106.5516"},
            {"杭州", "30.2741", "120.1551"},
            {"南京", "32.0603", "118.7969"},
            {"武汉", "30.5928", "114.3055"},
            {"西安", "34.3416", "108.9398"},
            {"天津", "39.3434", "117.3616"},
            {"苏州", "31.2989", "120.5853"},
            {"郑州", "34.7466", "113.6254"},
            {"长沙", "28.2282", "112.9390"},
            {"东莞", "23.0207", "113.7518"},
            {"沈阳", "41.8057", "123.4315"},
            {"青岛", "36.0671", "120.3826"},
            {"合肥", "31.8206", "117.2272"},
            {"佛山", "23.0219", "113.1214"},
            {"福州", "26.0745", "119.2965"},
            {"厦门", "24.4798", "118.0894"},
            {"昆明", "25.0389", "102.7183"},
            {"哈尔滨", "45.8038", "126.5349"},
            {"济南", "36.6512", "117.1201"},
            {"南昌", "28.6820", "115.8579"},
            {"贵阳", "26.6470", "106.6302"},
            {"南宁", "22.8170", "108.3665"},
            {"石家庄", "38.0428", "114.5149"},
            {"太原", "37.8706", "112.5489"},
            {"长春", "43.8171", "125.3235"},
            {"大连", "38.9140", "121.6147"},
            {"无锡", "31.4912", "120.3119"},
            {"宁波", "29.8683", "121.5440"},
            {"温州", "27.9938", "120.6994"},
            {"珠海", "22.2707", "113.5767"},
            {"三亚", "18.2528", "109.5119"},
            {"海口", "20.0440", "110.1999"},
            {"兰州", "36.0611", "103.8343"},
            {"西宁", "36.6171", "101.7782"},
            {"银川", "38.4872", "106.2309"},
            {"乌鲁木齐", "43.8256", "87.6168"},
            {"呼和浩特", "40.8424", "111.7490"},
            {"拉萨", "29.6520", "91.1721"},
            {"香港", "22.3193", "114.1694"},
            {"澳门", "22.1987", "113.5439"},
            {"台北", "25.0330", "121.5654"}
    };

    private static final ExecutorService FETCH = Executors.newSingleThreadExecutor();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        /** The line to show, e.g. "成都 · 多云 18℃"; never empty when the call went through. */
        void onLine(String line);
    }

    private Weather() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    /** The picked city's name; DEFAULT_CITY when nothing was picked or the name left the list. */
    static String city(Context ctx) {
        String name = prefs(ctx).getString(KEY_CITY, DEFAULT_CITY);
        return coordsOf(name) != null ? name : DEFAULT_CITY;
    }

    static void setCity(Context ctx, String name) {
        if (coordsOf(name) == null) return;
        prefs(ctx).edit().putString(KEY_CITY, name).apply();
    }

    /** {latitude, longitude} of a city in CITIES, or null when the name is not one of them. */
    static String[] coordsOf(String name) {
        for (String[] city : CITIES) if (city[0].equals(name)) return new String[]{city[1], city[2]};
        return null;
    }

    /** What the last successful fetch left behind, or "" when there never was one. */
    static String cached(Context ctx) {
        return line(ctx, prefs(ctx).getString(KEY_CITY, DEFAULT_CITY),
                prefs(ctx).getFloat(KEY_TEMP, Float.NaN), prefs(ctx).getInt(KEY_CODE, -1));
    }

    /** WMO code stored by the last fetch, or -1 when there never was one; drives the weather icon. */
    static int lastCode(Context ctx) {
        return prefs(ctx).getInt(KEY_CODE, -1);
    }

    /** Drawable that pictures a WMO code, grouped into the seven bundled sky icons. */
    static int iconRes(int code) {
        switch (code) {
            case 0:
            case 1:
                return R.drawable.w_sunny;
            case 2:
                return R.drawable.w_partly;
            case 3:
                return R.drawable.w_cloud;
            case 45:
            case 48:
                return R.drawable.w_fog;
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
            case 61:
            case 63:
            case 65:
            case 66:
            case 67:
            case 80:
            case 81:
            case 82:
                return R.drawable.w_rain;
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86:
                return R.drawable.w_snow;
            case 95:
            case 96:
            case 99:
                return R.drawable.w_storm;
            default:
                return R.drawable.w_cloud;
        }
    }

    /** The line for the given city and readings; "" while either is missing. */
    private static String line(Context ctx, String city, float temp, int code) {
        if (city == null || Float.isNaN(temp) || code < 0) return "";
        String sky = describe(code);
        return ctx.getString(R.string.weather_line, city, sky, Math.round(temp));
    }

    /**
     * Asks Open-Meteo for the picked city and hands the line to the callback on the main thread.
     * force skips the half-hour wait (a newly picked city must show at once); a failed fetch keeps the
     * cached numbers, so the line never blinks away for a passing network hiccup.
     */
    static void refresh(Context ctx, boolean force, Callback cb) {
        String city = city(ctx);
        String cachedLine = line(ctx, city,
                prefs(ctx).getFloat(KEY_TEMP, Float.NaN), prefs(ctx).getInt(KEY_CODE, -1));
        if (!force && !cachedLine.isEmpty()
                && System.currentTimeMillis() - prefs(ctx).getLong(KEY_AT, 0L) < REFRESH_MS) {
            cb.onLine(cachedLine);
            return;
        }
        String[] coords = coordsOf(city);
        if (coords == null) {
            cb.onLine(cachedLine);
            return;
        }
        FETCH.execute(() -> {
            float temp = Float.NaN;
            int code = -1;
            try {
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + coords[0]
                        + "&longitude=" + coords[1]
                        + "&current=temperature_2m,weather_code&timezone="
                        + URLEncoder.encode("Asia/Shanghai", "UTF-8");
                JSONObject json = new JSONObject(get(url));
                JSONObject current = json.optJSONObject("current");
                if (current != null) {
                    temp = (float) current.optDouble("temperature_2m", Double.NaN);
                    code = current.optInt("weather_code", -1);
                }
            } catch (Exception ignored) {
                // Offline, a changed API or a slow DNS: the cached line below is what Home keeps showing.
            }
            String fresh = line(ctx, city, temp, code);
            if (!fresh.isEmpty()) {
                prefs(ctx).edit().putFloat(KEY_TEMP, temp).putInt(KEY_CODE, code)
                        .putLong(KEY_AT, System.currentTimeMillis()).apply();
            }
            String out = fresh.isEmpty() ? cachedLine : fresh;
            MAIN.post(() -> cb.onLine(out));
        });
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "cleanmitv");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    /** WMO weather code (Open-Meteo's `weather_code`) as the two-word Chinese sky people read. */
    static String describe(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "大部晴";
            case 2: return "多云";
            case 3: return "阴";
            case 45:
            case 48: return "雾";
            case 51:
            case 53:
            case 55: return "毛毛雨";
            case 56:
            case 57: return "冻雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66:
            case 67: return "冻雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 77: return "米雪";
            case 80: return "阵雨";
            case 81: return "强阵雨";
            case 82: return "暴雨";
            case 85:
            case 86: return "阵雪";
            case 95: return "雷阵雨";
            case 96:
            case 99: return "雷雨冰雹";
            default: return "未知";
        }
    }
}
