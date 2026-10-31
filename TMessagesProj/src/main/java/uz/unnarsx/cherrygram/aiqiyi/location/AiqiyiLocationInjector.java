/*
 * 爱琦艺客户端 - 坐标伪造 · 注入点
 *
 * 只在构造位置消息的那一瞬间替换经纬度，
 * 不动系统定位、不动地图显示（地图还是真实位置，只有发出去的是假的）。
 */
package uz.unnarsx.cherrygram.aiqiyi.location;

import android.location.Location;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;

public class AiqiyiLocationInjector {

    /**
     * 普通位置：把真实坐标换成设置里的坐标。
     *
     * @param geo 目标 geo 对象，就地修改
     * @return 是否发生了替换
     */
    public static boolean applyToGeo(TLRPC.GeoPoint geo) {
        if (geo == null) {
            return false;
        }
        try {
            if (!AiqiyiLocationStore.shouldOverride()) {
                return false;
            }
            geo.lat = AndroidUtilities.fixLocationCoord(AiqiyiLocationStore.getLat());
            geo._long = AndroidUtilities.fixLocationCoord(AiqiyiLocationStore.getLon());
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * 实时位置：只有开了「实时位置也伪造」才替换。
     */
    public static boolean applyToLiveGeo(TLRPC.GeoPoint geo) {
        if (geo == null) {
            return false;
        }
        try {
            if (!AiqiyiLocationStore.shouldOverride()) {
                return false;
            }
            if (!AiqiyiLocationStore.isLiveEnabled()) {
                return false;
            }
            geo.lat = AndroidUtilities.fixLocationCoord(AiqiyiLocationStore.getLat());
            geo._long = AndroidUtilities.fixLocationCoord(AiqiyiLocationStore.getLon());
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * 实时位置的朝向/精度字段在伪造时没有意义，
     * 一并做一次清理，避免真实朝向泄露。
     */
    public static void clearHeadingIfFaked(TLRPC.MessageMedia media) {
        if (media == null) {
            return;
        }
        try {
            if (!AiqiyiLocationStore.shouldOverride() || !AiqiyiLocationStore.isLiveEnabled()) {
                return;
            }
            media.flags &= ~1; // 去掉 heading
        } catch (Throwable ignore) {
        }
    }

    /**
     * 地图层伪造：把 LocationController 拿到的系统定位直接换成伪坐标。
     *
     * 这里返回一个新的 Location 对象而不是改原对象，
     * 因为原对象可能还被系统/其它监听器持有，就地改会污染真实定位状态。
     * 精度统一设为 5 米，避免「精确到 29 米」这种真实值泄露。
     *
     * @param real 系统返回的真实定位，可为 null
     * @return 需要向上层暴露的定位；不需要伪造时原样返回 real
     */
    public static Location fakeLocation(Location real) {
        try {
            org.telegram.messenger.FileLog.d("[AQY-LOC] fakeLocation in: real="
                    + (real == null ? "null" : (real.getLatitude() + "," + real.getLongitude()))
                    + " enabled=" + AiqiyiLocationStore.isEnabled()
                    + " mapEnabled=" + AiqiyiLocationStore.isMapEnabled()
                    + " valid=" + AiqiyiLocationStore.hasValidPoint()
                    + " shouldMap=" + AiqiyiLocationStore.shouldOverrideMap()
                    + " lat=" + AiqiyiLocationStore.getLat()
                    + " lon=" + AiqiyiLocationStore.getLon());
            if (!AiqiyiLocationStore.shouldOverrideMap()) {
                return real;
            }
            double lat = AiqiyiLocationStore.getLat();
            double lon = AiqiyiLocationStore.getLon();
            Location faked = real != null ? new Location(real) : new Location("aqy-fake");
            faked.setLatitude(lat);
            faked.setLongitude(lon);
            faked.setAccuracy(5f);
            if (real != null) {
                // 保留时间戳，否则 setLastKnownLocation 的 5 分钟过期判断会直接丢弃
                faked.setTime(real.getTime());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    faked.setElapsedRealtimeNanos(real.getElapsedRealtimeNanos());
                }
            } else {
                faked.setTime(System.currentTimeMillis());
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    faked.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
                }
            }
            // 清掉方向/速度，真实朝向和移动状态都不该带出去
            try {
                faked.removeBearing();
                faked.removeSpeed();
            } catch (Throwable ignore) {
            }
            return faked;
        } catch (Throwable ignore) {
            return real;
        }
    }

}