/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Aiqiyi plugin: Ghost Mode.
 *
 * Network layer: when enabled, outgoing requests that would leak "I am here"
 * signals are swallowed before they ever hit the socket. Four buckets are
 * suppressed, mirroring the reference implementation:
 *
 *   1. typing notifications        → TL_messages_setTyping / setEncryptedTyping
 *   2. read receipts               → messages_readHistory / messages_readMessageContents
 *                                    / channels_readHistory / messages_readEncryptedHistory
 *   3. story read receipts         → stories_readStories / stories_incrementStoryViews
 *   4. online status               → account.updateStatus (forced offline)
 *
 * The hook lives at the top of ConnectionsManager.sendRequestInternal.
 */
package uz.unnarsx.cherrygram.aiqiyi;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;
import uz.unnarsx.cherrygram.core.configs.CherrygramMessagesConfig;

public class AiqiyiGhostUtils {

    /** Result of an interception decision. */
    public static class InterceptResult {
        public final boolean blocked;
        public final RequestDelegate effectiveDelegate;

        private InterceptResult(boolean blocked, RequestDelegate delegate) {
            this.blocked = blocked;
            this.effectiveDelegate = delegate;
        }

        public static InterceptResult none(RequestDelegate delegate) {
            return new InterceptResult(false, delegate);
        }

        public static InterceptResult block(RequestDelegate delegate) {
            return new InterceptResult(true, delegate);
        }
    }

    public static boolean isEnabled() {
        try {
            return CherrygramMessagesConfig.INSTANCE.getAiqiyiGhostMode();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Decide whether a request should be suppressed while Ghost Mode is on.
     * Called from the send path, so it must stay cheap and never throw.
     *
     * @param request  the outgoing TL request
     * @param delegate the original completion delegate (kept for API symmetry)
     */
    public static InterceptResult interceptRequest(TLObject request, RequestDelegate delegate) {
        if (request == null || !isEnabled()) {
            return InterceptResult.none(delegate);
        }
        try {
            // 1. typing notifications
            if (request instanceof TLRPC.TL_messages_setTyping
                    || request instanceof TLRPC.TL_messages_setEncryptedTyping) {
                if (BuildVarsLog("ghost: blocking typing notification")) {
                    return InterceptResult.block(delegate);
                }
            }

            // 2. read receipts (private / channel / encrypted)
            if (request instanceof TLRPC.TL_messages_readHistory
                    || request instanceof TLRPC.TL_messages_readMessageContents
                    || request instanceof TLRPC.TL_channels_readHistory
                    || request instanceof TLRPC.TL_messages_readEncryptedHistory) {
                if (BuildVarsLog("ghost: blocking read receipt")) {
                    return InterceptResult.block(delegate);
                }
            }

            // 3. story read receipts / view increments
            if (request instanceof TL_stories.TL_stories_readStories
                    || request instanceof TL_stories.TL_stories_incrementStoryViews) {
                if (BuildVarsLog("ghost: blocking story read")) {
                    return InterceptResult.block(delegate);
                }
            }

            // 4. online status — do not block, but force the request offline
            if (request instanceof TL_account.updateStatus) {
                TL_account.updateStatus updateStatus = (TL_account.updateStatus) request;
                updateStatus.offline = true;
                FileLog.d("ghost: forcing offline status");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return InterceptResult.none(delegate);
    }

    private static boolean BuildVarsLog(String message) {
        try {
            FileLog.d(message);
        } catch (Throwable ignored) {
        }
        return true;
    }

    private AiqiyiGhostUtils() {
    }
}