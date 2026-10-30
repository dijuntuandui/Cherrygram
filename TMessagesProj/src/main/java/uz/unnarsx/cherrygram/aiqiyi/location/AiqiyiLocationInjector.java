/*
 * 爱琦艺客户端 - 本地坐标伪造 · 注入点
 *
 * 只在构造位置消息的那一瞬间替换经纬度，
 * 不动系统定位、不动地图显示（地图还是真实位置，只有发出去的是假的）。
 */
package uz.unnarsx.cherrygram.aiqiyi.location;

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
}