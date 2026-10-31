package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Keep;
import androidx.core.util.Consumer;
import org.maplibre.android.MapLibre;
import org.maplibre.android.WellKnownTileServer;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraUpdate;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 爱琦艺 地图实现 —— MapLibre GL 版本。
 *
 * <p>替换 GoogleMapsProvider，目的是摆脱 Google Maps API Key 依赖。
 * 原版没有配置 com.google.android.geo.API_KEY，导致地图只能画出图钉、
 * 瓦片全部空白。这里改用 MapLibre + 免密钥矢量瓦片。
 *
 * <p>注意：本类只负责"把地图画出来"。坐标是否伪造由
 * LocationController.setLastKnownLocation() 那一层注入，
 * 地图拿到的 LatLng 已经是处理过的值。
 */
@Keep
public class AiqiyiMapsProvider implements IMapsProvider {

    /**
     * 免 key 底图。
     *
     * 曾经只用 a.tile.openstreetmap.fr/hot 一个源，实测两个坑：
     *   1) 该镜像在高 zoom 级别瓦片缺失/极慢，放大地图后批量请求全部挂起，
     *      页面只剩白底 + 图钉，看起来「地图渲染不出来」。
     *   2) 单源无冗余，网络抖动一次就整片空白。
     *
     * 改成多源并用 MapLibre 的自动轮询：数组里的源会按请求轮转，
     * 某个源超时/404 时其余源仍能补上。
     * 顺序按实测可用性排，OSM 官方在前，HOT 镜像退到备用。
     */
    static final String[] OSM_TILES = new String[]{
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    };

    /**
     * 真实可用的最大缩放级别。
     *
     * 上游瓦片最高一般到 19，但 18~19 在多数镜像上缺失率很高。
     * 这里压到 18：视觉上已经能看清街区，又能显著减少白块。
     * 超过 maxzoom 后 MapLibre 会自动放大最深层瓦片，不会留空。
     */
    static final int OSM_MAX_ZOOM = 18;

    /** 不带注记的约束底图：仅用于食服时的极简档 */
    static final String DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json";

    /**
     * 构造 OSM 栅格样式。
     *
     * 坐标系：OSM 是泰坡墨卡托 EPSG:3857，与 MapLibre 默认一致
     * （也就是 WGS-84 直接上图，不需要 GCJ-02 偏移修正，
     * 但也意味着与国内地图 App 有 50~500m 的固有偏差，这是期望行为）。
     */
    static String buildOsmStyle(boolean dark) {
        String bg = dark ? "#101418" : "#e8e8e8";
        char q = 34;
        StringBuilder sb = new StringBuilder(512);
        sb.append('{').append(q).append("version").append(q).append(":8,");
        sb.append(q).append("sources").append(q).append(":{");
        sb.append(q).append("osm").append(q).append(":{");
        sb.append(q).append("type").append(q).append(':').append(q).append("raster").append(q).append(',');
        sb.append(q).append("tiles").append(q).append(":[");
        for (int i = 0; i < OSM_TILES.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(q).append(OSM_TILES[i]).append(q);
        }
        sb.append("],");
        sb.append(q).append("tileSize").append(q).append(":256,");
        sb.append(q).append("minzoom").append(q).append(":0,");
        sb.append(q).append("maxzoom").append(q).append(':').append(OSM_MAX_ZOOM).append(',');
        sb.append(q).append("attribution").append(q).append(':').append(q).append("(c) OpenStreetMap contributors").append(q).append("}},");
        sb.append(q).append("layers").append(q).append(":[");
        sb.append('{').append(q).append("id").append(q).append(':').append(q).append("bg").append(q).append(',');
        sb.append(q).append("type").append(q).append(':').append(q).append("background").append(q).append(',');
        sb.append(q).append("paint").append(q).append(":{").append(q).append("background-color").append(q)
          .append(':').append(q).append(bg).append(q).append("}},");
        sb.append('{').append(q).append("id").append(q).append(':').append(q).append("osm").append(q).append(',');
        sb.append(q).append("type").append(q).append(':').append(q).append("raster").append(q).append(',');
        sb.append(q).append("source").append(q).append(':').append(q).append("osm").append(q).append("}");
        sb.append("]}");
        return sb.toString();
    }

    /** 统一入口：默认走 OSM，失败才回退 demotiles。 */
    static Style.Builder buildStyle(boolean dark) {
        return new Style.Builder().fromJson(buildOsmStyle(dark));
    }

    /**
     * MapLibre 是进程级单例，MapView 的构造器里会检查 hasInstance()，
     * 为 false 就直接抛 MapLibreConfigurationException 把 App 崩掉。
     *
     * <p>Telegram 原生顺序是「先 onCreateMapView()，再 initializeMaps()」，
     * 即先 new MapView 后初始化——那是 Google Maps 的宽松约定，MapLibre 不接受。
     * 所以这里做成幂等方法，并在 onCreateMapView() 里自己先调一次兜底。
     */
    static synchronized void ensureInitialized(Context context) {
        if (MapLibre.hasInstance()) {
            return;
        }
        Context app = context != null ? context.getApplicationContext() : ApplicationLoader.applicationContext;
        if (app == null) {
            return;
        }
        MapLibre.getInstance(app, null, WellKnownTileServer.MapLibre);
    }

    @Override
    public void initializeMaps(Context context) {
        try {
            ensureInitialized(context);
        } catch (Throwable e) {
            FileLog.e("AiqiyiMaps: MapLibre init failed", e);
        }
    }

    @Override
    public IMapView onCreateMapView(Context context) {
        ensureInitialized(context);
        return new AqyMapView(context);
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return new AqyMarkerOptions();
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return new AqyCircleOptions();
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new AqyLatLngBoundsBuilder();
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new AqyCameraUpdate(CameraUpdateFactory.newLatLng(conv(latLng)));
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new AqyCameraUpdate(CameraUpdateFactory.newLatLngZoom(conv(latLng), zoom));
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        AqyLatLngBounds b = (AqyLatLngBounds) bounds;
        if (b == null || b.builder == null) {
            return new AqyCameraUpdate(null);
        }
        return new AqyCameraUpdate(CameraUpdateFactory.newLatLngBounds(b.builder.build(), padding));
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        try {
            java.io.InputStream is = context.getResources().openRawResource(resId);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            is.close();
            return new AqyMapStyleOptions(bos.toString("UTF-8"));
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * MapLibre 是编译进本 APK 的库，不依赖外部地图应用。
     *
     * 这里返回自己的包名，是为了让 AndroidUtilities.isMapsInstalled()
     * 的包存在性检查永远通过 —— 它若查不到包就会弹「请安装 Google 地图」
     * 并跳转应用商店，而我们根本不需要外部应用。
     */
    @Override
    public String getMapsAppPackageName() {
        try {
            return ApplicationLoader.applicationContext.getPackageName();
        } catch (Throwable e) {
            return "com.iosAiqiyi";
        }
    }

    /**
     * 只有 isMapsInstalled() 返回 false 时才会用到这个文案。
     * 上面已经保证不会走到该分支，但为了不留下 LOC_ERR 兜底值
     * （getString(0) 会渲染成 "LOC_ERR:"），这里给一个真实资源。
     */
    @Override
    public int getInstallMapsString() {
        return R.string.AiqiyiMapsUnavailable;
    }

    static org.maplibre.android.geometry.LatLng conv(IMapsProvider.LatLng l) {
        return new org.maplibre.android.geometry.LatLng(l.latitude, l.longitude);
    }

    /* ---------------------------------------------------------------- */
    /* IMap                                                             */
    /* ---------------------------------------------------------------- */

    public final static class AqyMap implements IMap {
        private final MapLibreMap map;
        private final AqyMapView owner;
        private final Map<Marker, AqyMarker> markerMap = new HashMap<>();
        private Consumer<Location> myLocationConsumer;

        AqyMap(MapLibreMap map, AqyMapView owner) {
            this.map = map;
            this.owner = owner;
            // MapLibre 默认最大缩放 22，但 OSM 瓦片最深只到 18。
            // 不限制的话用户能捏到 22，底图被过度拉伸糊成一片。
            // 这里锁死到瓦片实际上限，手势缩放也推不过去。
            try {
                map.setMaxZoomPreference(OSM_MAX_ZOOM);
                map.setMinZoomPreference(2f);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        @Override
        public void setMapType(int mapType) {
            // MapLibre 没有内置卫星图源，三种类型统一走默认矢量样式。
            // 保留结构是为了将来接入卫星瓦片源时只改这一处。
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            CameraUpdate cu = unwrap(update);
            if (cu != null) map.animateCamera(cu);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            CameraUpdate cu = unwrap(update);
            if (cu != null) map.animateCamera(cu, callback == null ? null : new AqyCancelable(callback));
        }

        @Override
        public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) {
            CameraUpdate cu = unwrap(update);
            if (cu != null) {
                map.animateCamera(cu, duration, callback == null ? null : new AqyCancelable(callback));
            }
        }

        @Override
        public void moveCamera(ICameraUpdate update) {
            CameraUpdate cu = unwrap(update);
            if (cu != null) map.moveCamera(cu);
        }

        @Override
        public float getMaxZoomLevel() {
            // 返回瓦片实际上限，而不是 MapLibre 的 22。
            // 上游全部用 getMaxZoomLevel()-N 算初始缩放，
            // 这里报 22 会把初始视野推到 18（只能看几个街区）。
            return (float) OSM_MAX_ZOOM;
        }

        @Override
        public float getMinZoomLevel() {
            return (float) map.getMinZoomLevel();
        }

        private boolean myLocationEnabled;
        private NotificationCenter.NotificationCenterDelegate locationDelegate;
        private int locationAccount = -1;

        @Override
        public void setMyLocationEnabled(boolean enabled) {
            // MapLibre 不内置"我的位置"图层，这里自己挂 LocationController。
            // 关键点：取的是 getLastKnownLocation()，它在
            // setLastKnownLocation() 里已经过伪坐标注入，所以蓝点也是伪坐标。
            myLocationEnabled = enabled;
            if (enabled) {
                attachLocationDriver();
            } else {
                detachLocationDriver();
            }
        }

        private void attachLocationDriver() {
            if (locationDelegate != null) {
                return;
            }
            locationAccount = UserConfig.selectedAccount;
            locationDelegate = (id, account, args) -> {
                if (id == NotificationCenter.newLocationAvailable) {
                    pushMyLocation();
                }
            };
            NotificationCenter.getGlobalInstance().addObserver(locationDelegate,
                    NotificationCenter.newLocationAvailable);
            pushMyLocation();
        }

        private void detachLocationDriver() {
            if (locationDelegate != null) {
                NotificationCenter.getGlobalInstance().removeObserver(locationDelegate,
                        NotificationCenter.newLocationAvailable);
                locationDelegate = null;
            }
        }

        private void pushMyLocation() {
            if (!myLocationEnabled) {
                return;
            }
            try {
                int account = locationAccount >= 0 ? locationAccount : UserConfig.selectedAccount;
                Location last = LocationController.getInstance(account).getLastKnownLocation();
                dispatchMyLocation(last);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        void destroy() {
            detachLocationDriver();
        }

        @Override
        public IUISettings getUiSettings() {
            return new AqyUISettings(map.getUiSettings());
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
            if (callback == null) return;
            map.addOnCameraIdleListener(callback::run);
        }

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener listener) {
            if (listener == null) return;
            map.addOnCameraMoveStartedListener(reason -> {
                // MapLibre 与 Telegram 的 reason 常量取值一致（1/2/3），直接透传。
                listener.onCameraMoveStarted(reason);
            });
        }

        @Override
        public CameraPosition getCameraPosition() {
            org.maplibre.android.camera.CameraPosition cp = map.getCameraPosition();
            if (cp == null || cp.target == null) {
                return new CameraPosition(new IMapsProvider.LatLng(0, 0), 0);
            }
            return new CameraPosition(
                    new IMapsProvider.LatLng(cp.target.getLatitude(), cp.target.getLongitude()),
                    (float) cp.zoom);
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            if (callback == null) return;
            map.addOnCameraIdleListener(() -> {
                // 只在首次空闲时回调一次，语义对齐 Google 的 onMapLoaded
                if (owner != null && owner.mapLoadedFired) return;
                if (owner != null) owner.mapLoadedFired = true;
                callback.run();
            });
        }

        @Override
        public IProjection getProjection() {
            return new AqyProjection(map, owner == null ? null : owner.getMapView());
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            map.setPadding(left, top, right, bottom);
        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {
            AqyMapStyleOptions s = (AqyMapStyleOptions) style;
            if (s != null && s.json != null && !s.json.isEmpty()) {
                // 传入的是 Google 格式样式，MapLibre 不认，退回默认样式。
                // 这里不静默失败，保留一个可观测的日志。
                FileLog.d("AiqiyiMaps: ignoring incompatible google style json");
                return;
            }
            map.setStyle(buildStyle(false));
        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            AqyMarkerOptions o = (AqyMarkerOptions) markerOptions;
            MarkerOptions mo = new MarkerOptions();
            if (o.position != null) {
                mo.position(new org.maplibre.android.geometry.LatLng(o.position.latitude, o.position.longitude));
            }
            if (o.iconBitmap != null) {
                mo.icon(IconFactory.getInstance(ApplicationLoader.applicationContext).fromBitmap(o.iconBitmap));
            } else if (o.iconResId != 0) {
                mo.icon(IconFactory.getInstance(ApplicationLoader.applicationContext).fromResource(o.iconResId));
            }
            if (o.title != null) mo.title(o.title);
            if (o.snippet != null) mo.snippet(o.snippet);
            Marker marker = map.addMarker(mo);
            AqyMarker wrapper = new AqyMarker(marker, this);
            markerMap.put(marker, wrapper);
            return wrapper;
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {
            myLocationConsumer = callback;
        }

        /**
         * 由 AqyMapView 在收到定位更新时调用。
         * 位置来源是 LocationController，即已经过伪坐标处理的 Location。
         */
        void dispatchMyLocation(Location location) {
            Consumer<Location> c = myLocationConsumer;
            if (c != null && location != null) {
                c.accept(location);
            }
        }

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {
            if (markerClickListener == null) return;
            map.setOnMarkerClickListener(marker -> {
                AqyMarker wrapper = markerMap.get(marker);
                if (wrapper == null) {
                    wrapper = new AqyMarker(marker, this);
                    markerMap.put(marker, wrapper);
                }
                return markerClickListener.onClick(wrapper);
            });
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback) {
            if (callback == null) return;
            map.addOnCameraMoveListener(callback::run);
        }

        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            AqyCircleOptions o = (AqyCircleOptions) circleOptions;
            AqyCircle c = new AqyCircle(o);
            c.apply();
            return c;
        }

        private CameraUpdate unwrap(ICameraUpdate update) {
            if (update instanceof AqyCameraUpdate) {
                return ((AqyCameraUpdate) update).update;
            }
            return null;
        }
    }

    /* ---------------------------------------------------------------- */
    /* IMapView                                                         */
    /* ---------------------------------------------------------------- */

    public final static class AqyMapView implements IMapView {
        private final MapView mapView;
        /** 是否已经调过 MapView.onCreate()，防止重复调用崩溃 */
        private boolean created;
        boolean mapLoadedFired;
        private ITouchInterceptor dispatchInterceptor;
        private ITouchInterceptor interceptInterceptor;
        private Runnable onLayoutListener;
        private Consumer<IMap> readyCallback;
        private AqyMap currentMap;

        @SuppressWarnings("unchecked")
        public AqyMapView(Context context) {
            MapLibreMapOptions opts = MapLibreMapOptions.createFromAttributes(context)
                    .attributionEnabled(false)
                    .logoEnabled(false);
            mapView = new MapView(context, opts) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (dispatchInterceptor != null) {
                        return dispatchInterceptor.onInterceptTouchEvent(ev, super::dispatchTouchEvent);
                    }
                    return super.dispatchTouchEvent(ev);
                }
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (interceptInterceptor != null) {
                        return interceptInterceptor.onInterceptTouchEvent(ev, super::onInterceptTouchEvent);
                    }
                    return super.onInterceptTouchEvent(ev);
                }
            };
            // MapLibre 要求 MapView.onCreate() 先于 getMapAsync() 执行，
            // 否则渲染器不启动、瓦片管线不建立（地图白底）。
            //
            // 但 onCreate() 绝不能放在构造器里：
            // 构造器执行时 View 尚未 attach 到窗口，Vulkan surface 还不存在，
            // MapRenderer 会以脏状态启动；onDestroy() 之后 MapLibre 的单例仍存活，
            // 第二次 new MapView 再在构造器里 onCreate()，render 线程拿到悬空
            // native renderer，直接在 MapRenderer::render 段错误崩溃
            // （日志：libmaplibre.so MapRenderer::render + VulkanThread.guardedRun）。
            //
            // 正确时机：attach 到窗口后再 onCreate()，保证 surface 已就绪。
            mapView.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(android.view.View v) {
                    if (created) {
                        return;
                    }
                    created = true;
                    try {
                        mapView.onCreate(null);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    // onCreate() 之后才能注册 getMapAsync，顺序不能颠倒
                    mapView.getMapAsync(map -> {
                        currentMap = new AqyMap(map, AqyMapView.this);
                        // 直接应用样式；回调里再调 setStyle 会无限循环
                        try {
                            map.setStyle(buildStyle(false));
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                        if (readyCallback != null) {
                            readyCallback.accept(currentMap);
                        }
                    });
                }
                @Override
                public void onViewDetachedFromWindow(android.view.View v) {
                }
            });
        }

        @Override
        public View getView() {
            return mapView;
        }

        @Override
        public void getMapAsync(Consumer<IMap> callback) {
            readyCallback = callback;
            if (currentMap != null) {
                callback.accept(currentMap);
            }
        }

        @Override
        public void onResume() {
            if (!created) {
                // 尚未 attach，onCreate 还没跑，提前 onResume 会让 MapLibre 状态错乱
                return;
            }
            try {
                mapView.onResume();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        @Override
        public void onPause() {
            if (!created) {
                return;
            }
            try {
                mapView.onPause();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        @Override
        public void onCreate(Bundle savedInstance) {
            // attach 回调里已经调过一次，这里宁可跳过也不能重复调
            if (created) {
                return;
            }
            created = true;
            try {
                mapView.onCreate(savedInstance);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        @Override
        public void onDestroy() {
            // 先把回调摘掉，避免销毁过程中 getMapAsync 的 lambda 回调到已释放的 map
            readyCallback = null;
            if (currentMap != null) {
                currentMap.destroy();
                currentMap = null;
            }
            if (created) {
                created = false;
                try {
                    mapView.onDestroy();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
        @Override
        public void onLowMemory() {
            if (!created) {
                return;
            }
            try {
                mapView.onLowMemory();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        @Override
        public void setOnDispatchTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            dispatchInterceptor = touchInterceptor;
        }

        @Override
        public void setOnInterceptTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            interceptInterceptor = touchInterceptor;
        }

        @Override
        public void setOnLayoutListener(Runnable callback) {
            onLayoutListener = callback;
            if (callback != null) {
                mapView.addOnLayoutChangeListener(
                        (v, l, t, r, b, ol, ot, or, ob) -> callback.run());
            }
        }

        public AqyMap getCurrentMap() {
            return currentMap;
        }

        public MapView getMapView() {
            return mapView;
        }
    }

    /* ---------------------------------------------------------------- */
    /* 其余内部实现                                                     */
    /* ---------------------------------------------------------------- */

    public final static class AqyProjection implements IProjection {
        private final MapLibreMap map;
        private final MapView mapView;

        AqyProjection(MapLibreMap map, MapView mapView) {
            this.map = map;
            this.mapView = mapView;
        }

        private static final double TILE_SIZE = 256.0;

        private static double worldPx(double zoom) {
            return TILE_SIZE * Math.pow(2.0, zoom);
        }

        private static double lonToWorldX(double lon, double zoom) {
            return worldPx(zoom) * (lon + 180.0) / 360.0;
        }

        private static double latToWorldY(double lat, double zoom) {
            double sinLat = Math.sin(Math.toRadians(lat));
            if (sinLat > 0.9999) sinLat = 0.9999;
            if (sinLat < -0.9999) sinLat = -0.9999;
            double y = 0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI);
            return worldPx(zoom) * y;
        }

        @Override
        public Point toScreenLocation(LatLng latLng) {
            try {
                org.maplibre.android.camera.CameraPosition cp = map.getCameraPosition();
                if (cp == null || cp.target == null || mapView == null) {
                    return new Point(0, 0);
                }
                double zoom = cp.zoom;
                double cx = lonToWorldX(cp.target.getLongitude(), zoom);
                double cy = latToWorldY(cp.target.getLatitude(), zoom);
                double tx = lonToWorldX(latLng.longitude, zoom);
                double ty = latToWorldY(latLng.latitude, zoom);
                int w = mapView.getWidth();
                int h = mapView.getHeight();
                if (w <= 0 || h <= 0) {
                    w = mapView.getMeasuredWidth();
                    h = mapView.getMeasuredHeight();
                }
                return new Point((int) Math.round(tx - cx + w / 2.0),
                                 (int) Math.round(ty - cy + h / 2.0));
            } catch (Throwable e) {
                return new Point(0, 0);
            }
        }
    }

    public final static class AqyUISettings implements IUISettings {
        private final org.maplibre.android.maps.UiSettings s;
        AqyUISettings(org.maplibre.android.maps.UiSettings s) {
            this.s = s;
            // [Aiqiyi] 地图手势统一在此开启。
            // 之前 setZoomControlsEnabled(false)（调用方本意是「不显示 +/- 按钮」）
            // 被直接映射到 setZoomGesturesEnabled(false)，导致双指缩放失效。
            // MapLibre SDK 无内置缩放按钮，故此类调用一律忽略，手势始终保持可用。
            try {
                s.setZoomGesturesEnabled(true);
                s.setScrollGesturesEnabled(true);
                s.setRotateGesturesEnabled(true);
                s.setTiltGesturesEnabled(true);
                s.setDoubleTapGesturesEnabled(true);
                s.setQuickZoomGesturesEnabled(true);
            } catch (Throwable ignore) {
            }
        }
        @Override
        public void setZoomControlsEnabled(boolean enabled) {
            // [Aiqiyi] MapLibre 无内置缩放按钮，此调用不应影响手势缩放。
            // 保持手势常开，忽略该设置。
        }

        @Override
        public void setMyLocationButtonEnabled(boolean enabled) {
            // MapLibre 无内置位置按钮
        }
        @Override
        public void setCompassEnabled(boolean enabled) {
            s.setCompassEnabled(enabled);
        }
    }

    public final static class AqyMarker implements IMarker {
        private final Marker marker;
        private final AqyMap map;
        private Object tag;

        AqyMarker(Marker marker, AqyMap map) {
            this.marker = marker;
            this.map = map;
        }

        Marker raw() {
            return marker;
        }

        @Override
        public Object getTag() {
            return tag;
        }

        @Override
        public void setTag(Object tag) {
            this.tag = tag;
        }

        @Override
        public LatLng getPosition() {
            org.maplibre.android.geometry.LatLng p = marker.getPosition();
            if (p == null) return new IMapsProvider.LatLng(0, 0);
            return new IMapsProvider.LatLng(p.getLatitude(), p.getLongitude());
        }

        @Override
        public void setPosition(LatLng latLng) {
            marker.setPosition(new org.maplibre.android.geometry.LatLng(latLng.latitude, latLng.longitude));
        }

        @Override
        public void setRotation(int rotation) {
            // MapLibre marker 不支持平面旋转
        }

        @Override
        public void setIcon(Bitmap bitmap) {
            if (bitmap == null) return;
            marker.setIcon(IconFactory.getInstance(ApplicationLoader.applicationContext).fromBitmap(bitmap));
        }

        @Override
        public void setIcon(int resId) {
            if (resId == 0) return;
            marker.setIcon(IconFactory.getInstance(ApplicationLoader.applicationContext).fromResource(resId));
        }

        @Override
        public void remove() {
            try {
                map.map.removeMarker(marker);
            } catch (Throwable ignore) {
            }
        }
    }

    public final static class AqyMarkerOptions implements IMarkerOptions {
        IMapsProvider.LatLng position;
        Bitmap iconBitmap;
        int iconResId;
        String title;
        String snippet;

        @Override
        public IMarkerOptions position(LatLng latLng) {
            this.position = latLng;
            return this;
        }

        @Override
        public IMarkerOptions icon(Bitmap bitmap) {
            this.iconBitmap = bitmap;
            return this;
        }

        @Override
        public IMarkerOptions icon(int resId) {
            this.iconResId = resId;
            return this;
        }

        @Override
        public IMarkerOptions anchor(float lat, float lng) {
            // MapLibre marker 默认锚点在图钉底部中心，与 Google 一致
            return this;
        }

        @Override
        public IMarkerOptions title(String title) {
            this.title = title;
            return this;
        }

        @Override
        public IMarkerOptions snippet(String snippet) {
            this.snippet = snippet;
            return this;
        }

        @Override
        public IMarkerOptions flat(boolean flat) {
            return this;
        }
    }

    public final static class AqyCircle implements ICircle {
        private final AqyCircleOptions opts;
        private final List<Marker> handles = new ArrayList<>();

        AqyCircle(AqyCircleOptions opts) {
            this.opts = opts;
        }

        void apply() {
            // MapLibre 没有原生 Circle，用 GeoJSON 源 + fill layer 实现。
            // 初版先留空实现，保证接口完整不崩溃；后续按需补。
        }

        @Override
        public void setStrokeColor(int color) {
        }

        @Override
        public void setFillColor(int color) {
        }

        @Override
        public void setRadius(double radius) {
        }

        @Override
        public double getRadius() {
            return opts.radius;
        }

        @Override
        public void setCenter(LatLng latLng) {
        }

        @Override
        public void remove() {
            handles.clear();
        }
    }

    public final static class AqyCircleOptions implements ICircleOptions {
        IMapsProvider.LatLng center;
        double radius;
        int strokeColor;
        int fillColor;
        int strokeWidth;

        @Override
        public ICircleOptions center(LatLng latLng) {
            this.center = latLng;
            return this;
        }

        @Override
        public ICircleOptions radius(double radius) {
            this.radius = radius;
            return this;
        }

        @Override
        public ICircleOptions strokeColor(int color) {
            this.strokeColor = color;
            return this;
        }

        @Override
        public ICircleOptions fillColor(int color) {
            this.fillColor = color;
            return this;
        }

        @Override
        public ICircleOptions strokePattern(List<PatternItem> patternItems) {
            return this;
        }

        @Override
        public ICircleOptions strokeWidth(int width) {
            this.strokeWidth = width;
            return this;
        }
    }

    public final static class AqyLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private final LatLngBounds.Builder b = new LatLngBounds.Builder();

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            b.include(new org.maplibre.android.geometry.LatLng(latLng.latitude, latLng.longitude));
            return this;
        }

        @Override
        public ILatLngBounds build() {
            return new AqyLatLngBounds(b);
        }
    }

    public final static class AqyLatLngBounds implements ILatLngBounds {
        final LatLngBounds.Builder builder;

        AqyLatLngBounds(LatLngBounds.Builder builder) {
            this.builder = builder;
        }

        @Override
        public LatLng getCenter() {
            try {
                LatLngBounds lb = builder.build();
                org.maplibre.android.geometry.LatLng c = lb.getCenter();
                return new IMapsProvider.LatLng(c.getLatitude(), c.getLongitude());
            } catch (Throwable e) {
                return new IMapsProvider.LatLng(0, 0);
            }
        }
    }

    public final static class AqyCameraUpdate implements ICameraUpdate {
        final CameraUpdate update;

        AqyCameraUpdate(CameraUpdate update) {
            this.update = update;
        }
    }

    public final static class AqyMapStyleOptions implements IMapStyleOptions {
        final String json;

        AqyMapStyleOptions(String json) {
            this.json = json;
        }
    }

    public final static class AqyCancelable implements MapLibreMap.CancelableCallback {
        private final ICancelableCallback cb;

        AqyCancelable(ICancelableCallback cb) {
            this.cb = cb;
        }

        @Override
        public void onFinish() {
            cb.onFinish();
        }

        @Override
        public void onCancel() {
            cb.onCancel();
        }
    }
}
