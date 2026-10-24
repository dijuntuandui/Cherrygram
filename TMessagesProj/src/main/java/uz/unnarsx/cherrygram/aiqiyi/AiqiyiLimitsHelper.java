package uz.unnarsx.cherrygram.aiqiyi;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import uz.unnarsx.cherrygram.core.configs.CherrygramAppearanceConfig;

/**
 * [Aiqiyi] 无限置顶 / 无限文件夹 / 无限收藏贴纸
 *
 * 这些上限本来是从服务端 config 下发、存进 preferences 的可变值。
 * 这里不碰结构，只在读取入口把它们顶成 Integer.MAX_VALUE，
 * 并在服务端同步之后（各账号 MessagesController.init 完成时）重新施加一次，
 * 避免刚启动就被远端值覆盖回去。
 */
public final class AiqiyiLimitsHelper {

    private AiqiyiLimitsHelper() {}

    /** 把开关状态写进该账号的 MessagesController 上限字段 */
    public static void apply(MessagesController mc) {
        if (mc == null) return;

        CherrygramAppearanceConfig cfg = CherrygramAppearanceConfig.INSTANCE;

        if (cfg.getAiqiyiUnlimitedPins()) {
            mc.maxPinnedDialogsCountDefault = Integer.MAX_VALUE;
            mc.maxPinnedDialogsCountPremium = Integer.MAX_VALUE;
            mc.maxFolderPinnedDialogsCountDefault = Integer.MAX_VALUE;
            mc.maxFolderPinnedDialogsCountPremium = Integer.MAX_VALUE;
            mc.dialogFiltersPinnedLimitDefault = Integer.MAX_VALUE;
            mc.dialogFiltersPinnedLimitPremium = Integer.MAX_VALUE;
        }

        if (cfg.getAiqiyiUnlimitedFolders()) {
            mc.dialogFiltersLimitDefault = Integer.MAX_VALUE;
            mc.dialogFiltersLimitPremium = Integer.MAX_VALUE;
        }

        if (cfg.getAiqiyiUnlimitedFavedStickers()) {
            mc.stickersFavedLimitDefault = Integer.MAX_VALUE;
            mc.stickersFavedLimitPremium = Integer.MAX_VALUE;
        }
    }

    /** 对所有已激活账号施加 */
    public static void applyLimits() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                apply(MessagesController.getInstance(a));
            }
        }
    }
}
