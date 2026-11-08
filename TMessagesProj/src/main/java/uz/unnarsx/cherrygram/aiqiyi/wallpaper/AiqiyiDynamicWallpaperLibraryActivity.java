package uz.unnarsx.cherrygram.aiqiyi.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import tw.nekomimi.nekogram.helpers.DynamicVideoWallpaperHelper;
import tw.nekomimi.nekogram.helpers.MultiDynamicVideoWallpaperHelper;
import uz.unnarsx.cherrygram.core.huanghun.HuanghunWallpaperLayout;

/**
 * [Aiqiyi] 动态视频壁纸库管理页。
 *
 * 以缩略图列表展示当前已导入的动态壁纸，支持逐项勾选、全选，确认后删除本机副本。
 */
public class AiqiyiDynamicWallpaperLibraryActivity extends BaseFragment {

    /** 列表条目。 */
    public static final class Item {
        public final String path;
        public final String fileName;
        public final long durationMs;
        public final long size;
        /** true 表示这是「单视频/聊天专用」那条，不是多轮播放列表里的。 */
        public final boolean single;

        Item(String path, boolean single) {
            this.path = path;
            this.single = single;
            File f = new File(path);
            this.fileName = f.getName();
            this.size = f.exists() ? f.length() : 0L;
            this.durationMs = readDuration(path);
        }

        private static long readDuration(String path) {
            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
            try {
                r.setDataSource(path);
                String d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                return d == null ? 0L : Long.parseLong(d);
            } catch (Throwable e) {
                return 0L;
            } finally {
                try {
                    r.release();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private final boolean deleteMode;
    private RecyclerListView listView;
    private ItemAdapter adapter;
    private TextView selectionButton;
    private TextView deleteButton;
    private TextView emptyView;
    private final ArrayList<Item> items = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();

    public AiqiyiDynamicWallpaperLibraryActivity(boolean deleteMode) {
        this.deleteMode = deleteMode;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(deleteMode ? "删除动态壁纸" : "动态壁纸库");
        actionBar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        actionBar.setCastShadows(false);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        HuanghunWallpaperLayout root = new HuanghunWallpaperLayout(context, currentAccount);
        fragmentView = root;
        actionBar.setDrawBlurBackground(root);
        actionBar.setBackgroundColor(0x20FFFFFF);

        int topOffset = 0;
        if (deleteMode) {
            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            actions.setBackground(createGlassDrawable(0x26FFFFFF, 0x55FFFFFF, 16));

            selectionButton = createActionText(context, "全选");
            selectionButton.setOnClickListener(v -> toggleSelectAll());
            actions.addView(selectionButton, LayoutHelper.createLinear(0, 40, 1f));

            deleteButton = createActionText(context, "删除已选");
            deleteButton.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            deleteButton.setOnClickListener(v -> confirmDeleteSelected());
            actions.addView(deleteButton, LayoutHelper.createLinear(0, 40, 1f, 8, 0, 0, 0));

            root.addView(actions, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.TOP));
            topOffset = 58;
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(adapter = new ItemAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(18));
        listView.setOnItemClickListener((view, position) -> {
            if (deleteMode && position >= 0 && position < items.size()) {
                toggleSelected(items.get(position).path);
            }
        });
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, topOffset, 0, 0));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24));
        emptyView.setText("暂无动态壁纸\n请先在「设置动态壁纸」或「导入多个视频」中添加。");
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 24, topOffset, 24, 0));

        reloadItems();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadItems();
    }

    private void reloadItems() {
        if (getParentActivity() == null) {
            return;
        }
        items.clear();
        Context ctx = ApplicationLoader.applicationContext;

        ArrayList<String> multiPaths = MultiDynamicVideoWallpaperHelper.getVideoPaths(ctx, currentAccount);
        if (multiPaths != null) {
            for (String p : multiPaths) {
                if (p != null && new File(p).exists()) {
                    items.add(new Item(p, false));
                }
            }
        }
        String single = DynamicVideoWallpaperHelper.getVideoPath(ctx, currentAccount, 0);
        if (single != null && new File(single).exists() && !containsPath(single)) {
            items.add(new Item(single, true));
        }

        Set<String> validPaths = new HashSet<>();
        for (Item item : items) {
            validPaths.add(item.path);
        }
        selectedPaths.retainAll(validPaths);
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateControls();
    }

    private boolean containsPath(String path) {
        for (Item item : items) {
            if (item.path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void toggleSelected(String path) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateControls();
    }

    private void toggleSelectAll() {
        if (items.isEmpty()) {
            return;
        }
        if (selectedPaths.size() == items.size()) {
            selectedPaths.clear();
        } else {
            selectedPaths.clear();
            for (Item item : items) {
                selectedPaths.add(item.path);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateControls();
    }

    private void updateControls() {
        if (emptyView != null) {
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (!deleteMode || selectionButton == null || deleteButton == null) {
            return;
        }
        boolean hasItems = !items.isEmpty();
        selectionButton.setEnabled(hasItems);
        deleteButton.setEnabled(!selectedPaths.isEmpty());
        selectionButton.setAlpha(hasItems ? 1f : .45f);
        deleteButton.setAlpha(selectedPaths.isEmpty() ? .45f : 1f);
        selectionButton.setText(hasItems && selectedPaths.size() == items.size() ? "取消全选" : "全选");
        deleteButton.setText(selectedPaths.isEmpty() ? "删除已选" : "删除已选（" + selectedPaths.size() + "）");
    }

    private void confirmDeleteSelected() {
        if (selectedPaths.isEmpty() || getParentActivity() == null) {
            return;
        }
        final int count = selectedPaths.size();
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle("确认删除")
                .setMessage("确定删除已选的 " + count + " 个动态壁纸吗？删除后无法恢复，但不会影响手机相册中的原始文件。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> doDeleteSelected(count))
                .create());
    }

    private void doDeleteSelected(int count) {
        Context ctx = ApplicationLoader.applicationContext;
        ArrayList<String> fromMulti = new ArrayList<>();
        boolean needSingle = false;
        for (String path : new ArrayList<>(selectedPaths)) {
            boolean isSingle = false;
            for (Item item : items) {
                if (item.path.equals(path) && item.single) {
                    isSingle = true;
                    break;
                }
            }
            if (isSingle) {
                needSingle = true;
            } else {
                fromMulti.add(path);
            }
        }
        try {
            if (needSingle) {
                DynamicVideoWallpaperHelper.clearVideo(ctx, currentAccount, 0);
            }
            if (!fromMulti.isEmpty()) {
                MultiDynamicVideoWallpaperHelper.deleteVideos(ctx, currentAccount, fromMulti);
            }
            DynamicVideoWallpaperHelper.notifyWallpaperChanged(currentAccount, 0);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        selectedPaths.clear();
        reloadItems();
        try {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "已删除 " + count + " 个动态壁纸。").show();
        } catch (Throwable ignore) {
        }
    }

    private TextView createActionText(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        view.setBackground(createGlassDrawable(0x24FFFFFF, 0x5CFFFFFF, 13));
        return view;
    }

    private GradientDrawable createGlassDrawable(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(AndroidUtilities.dp(radiusDp));
        drawable.setStroke(Math.max(1, AndroidUtilities.dp(1)), strokeColor);
        return drawable;
    }

    private final class ItemAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ItemAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ItemCell(context));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ItemCell cell = (ItemCell) holder.itemView;
            Item item = items.get(position);
            cell.bind(item, deleteMode, selectedPaths.contains(item.path), position != items.size() - 1);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class ItemCell extends FrameLayout {
        private final ImageView thumbnail;
        private final TextView title;
        private final TextView subtitle;
        private final TextView badge;
        private final CheckBox2 checkBox;
        private String boundPath;

        private ItemCell(Context context) {
            super(context);
            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));

            FrameLayout card = new FrameLayout(context);
            card.setBackground(createGlassDrawable(0x22FFFFFF, 0x5CFFFFFF, 18));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 82, Gravity.TOP));

            thumbnail = new ImageView(context);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackground(createGlassDrawable(0x29000000, 0x46FFFFFF, 12));
            card.addView(thumbnail, LayoutHelper.createFrame(100, 64, Gravity.LEFT | Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(textContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 122, 0, deleteMode ? 48 : 14, 0));

            title = new TextView(context);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            textContainer.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitle = new TextView(context);
            subtitle.setSingleLine(true);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            textContainer.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));

            badge = new TextView(context);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badge.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
            badge.setVisibility(View.GONE);
            textContainer.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            checkBox = new CheckBox2(context, 21, getResourceProvider());
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            card.addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));
        }

        private void bind(Item item, boolean selectable, boolean checked, boolean divider) {
            boundPath = item.path;
            thumbnail.setImageDrawable(null);
            title.setText(item.fileName);
            subtitle.setText(formatDuration(item.durationMs) + "  ·  " + formatSize(item.size));
            badge.setText(item.single ? "聊天专用" : "多轮播放");
            badge.setVisibility(View.VISIBLE);
            checkBox.setVisibility(selectable ? View.VISIBLE : View.GONE);
            checkBox.setChecked(checked, false);
            loadThumbnail(item.path);
        }

        private void loadThumbnail(String path) {
            Utilities.globalQueue.postRunnable(() -> {
                Bitmap bitmap = null;
                try {
                    bitmap = SendMessagesHelper.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                final Bitmap result = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    if (TextUtils.equals(boundPath, path)) {
                        thumbnail.setImageBitmap(result);
                    }
                });
            });
        }
    }

    private static String formatDuration(long durationMs) {
        long totalSeconds = Math.max(0L, durationMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, bytes / 1024L) + " KB";
        }
        return String.format(Locale.CHINA, "%.1f MB", bytes / (1024f * 1024f));
    }
}