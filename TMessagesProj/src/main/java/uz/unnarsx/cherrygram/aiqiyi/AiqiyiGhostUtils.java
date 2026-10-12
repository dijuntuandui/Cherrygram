/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Ghost Mode skeleton.
 * NOTE: network-layer interception is NOT implemented yet.
 *       This class only exposes the API surface so the UI toggle can be wired
 *       up and compiled. Wiring into ConnectionsManager is a separate step.
 */
package uz.unnarsx.cherrygram.aiqiyi;

import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;

public class AiqiyiGhostUtils {

    /** Result of an interception decision. */
    public static class InterceptResult {
        public final boolean blocked;

        private InterceptResult(boolean blocked) {
            this.blocked = blocked;
        }

        public static InterceptResult none() {
            return new InterceptResult(false);
        }

        public static InterceptResult block() {
            return new InterceptResult(true);
        }
    }

    /**
     * Decide whether a request should be suppressed while Ghost Mode is on.
     *
     * TODO(ghost-mode): recognize read-receipt / online-status / typing requests
     *       and return {@link InterceptResult#block()} for them. Until then this
     *       always allows the request through, so enabling the toggle is a no-op.
     */
    public static InterceptResult interceptRequest(TLObject request, RequestDelegate delegate) {
        if (!isGhostModeEnabled()) {
            return InterceptResult.none();
        }
        return InterceptResult.none();
    }

    private static boolean isGhostModeEnabled() {
        try {
            return uz.unnarsx.cherrygram.core.configs.CherrygramMessagesConfig.INSTANCE.getAiqiyiGhostMode();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private AiqiyiGhostUtils() {
    }
}