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
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import android.content.SharedPreferences;
import java.util.ArrayList;

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

    // =================== Privacy sync (server side) ===================
    // Local rendering only changes what WE see. Everyone else sees whatever the
    // server says, so the "last seen" privacy rule has to be flipped too.
    //
    // The original rule set is snapshotted before we overwrite it, so turning the
    // switch off restores exactly what the user had ("My Contacts" stays
    // "My Contacts" instead of being flattened to "Everybody").

    private static final String PREFS = "aqy_ghost_privacy";
    private static final String KEY_SNAPSHOT = "lastseen_snapshot";
    private static final String KEY_APPLIED = "lastseen_applied";

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalNotificationsSettings();
    }

    private static final int MODE_HIDE = 1;
    private static final int MODE_RESTORE = 2;

    /**
     * Called the moment the user flips the switch in settings.
     */
    public static void applyGhostPrivacy() {
        syncPrivacy(UserConfig.selectedAccount);
    }

    /**
     * Reconcile the server-side last-seen rule with the state of the switch.
     * Driven by what the server currently reports, not by a local flag.
     */
    public static void syncPrivacy(int account) {
        try {
            if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
                return;
            }
            if (!UserConfig.getInstance(account).isClientActivated()) {
                return;
            }
            boolean wantHidden = isEnabled();
            ArrayList<TLRPC.PrivacyRule> current = ContactsController.getInstance(account)
                    .getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
            if (current == null) {
                try {
                    ContactsController.getInstance(account).loadPrivacySettings();
                } catch (Throwable ignored) {
                }
                AndroidUtilities.runOnUIThread(() -> syncPrivacy(account), 1500);
                return;
            }
            boolean isHidden = isDisallowAll(current);
            SharedPreferences p = prefs();

            if (wantHidden) {
                if (isHidden) {
                    return;
                }
                p.edit().putString(KEY_SNAPSHOT, serializeRules(current)).apply();
                sendRules(account, null, true);
                FileLog.d("ghost: privacy -> disallow all");
            } else {
                if (!isHidden) {
                    return;
                }
                String snap = p.getString(KEY_SNAPSHOT, null);
                sendRules(account, snap, false);
                FileLog.d("ghost: privacy restore requested snapshot=" + snap);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void onRulesApplied(int account, int mode, String snapshot) {
        SharedPreferences p = prefs();
        if (mode == MODE_HIDE) {
            p.edit().putBoolean(KEY_APPLIED, true).apply();
        } else if (mode == MODE_RESTORE) {
            p.edit().putBoolean(KEY_APPLIED, false).remove(KEY_SNAPSHOT).apply();
        }
    }

    private static boolean isDisallowAll(ArrayList<TLRPC.PrivacyRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        boolean sawDisallowAll = false;
        for (TLRPC.PrivacyRule r : rules) {
            if (r instanceof TLRPC.TL_privacyValueAllowAll
                    || r instanceof TLRPC.TL_privacyValueAllowContacts
                    || r instanceof TLRPC.TL_privacyValueAllowUsers
                    || r instanceof TLRPC.TL_privacyValueAllowChatParticipants
                    || r instanceof TLRPC.TL_privacyValueAllowPremium
                    || r instanceof TLRPC.TL_privacyValueAllowBots) {
                return false;
            }
            if (r instanceof TLRPC.TL_privacyValueDisallowAll) {
                sawDisallowAll = true;
            }
        }
        return sawDisallowAll;
    }

    /**
     * @param snapshot serialized original rule set, or null to force disallow-all
     * @param hide     true = set disallow all, false = restore snapshot
     */
    private static void sendRules(int account, String snapshot, boolean hide) {
        final int mode = hide ? MODE_HIDE : MODE_RESTORE;
        TL_account.setPrivacy req = new TL_account.setPrivacy();
        req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
        if (hide) {
            req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
        } else {
            ArrayList<TLRPC.InputPrivacyRule> restored = deserializeRules(snapshot);
            if (restored == null || restored.isEmpty()) {
                req.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
            } else {
                req.rules.addAll(restored);
            }
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (error != null) {
                        FileLog.d("ghost: privacy request failed " + error.text);
                        return;
                    }
                    TL_account.privacyRules pr = (TL_account.privacyRules) response;
                    MessagesController.getInstance(account).putUsers(pr.users, false);
                    MessagesController.getInstance(account).putChats(pr.chats, false);
                    ContactsController.getInstance(account)
                            .setPrivacyRules(pr.rules, ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                    onRulesApplied(account, mode, snapshot);
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    // ---- tiny rule serializer: "ALLOWALL";"ALLOWCONTACTS";"D:123,456";... ----
    private static String serializeRules(ArrayList<TLRPC.PrivacyRule> rules) {
        if (rules == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (TLRPC.PrivacyRule r : rules) {
            String token = null;
            if (r instanceof TLRPC.TL_privacyValueAllowAll) {
                token = "ALLOWALL";
            } else if (r instanceof TLRPC.TL_privacyValueAllowContacts) {
                token = "ALLOWCONTACTS";
            } else if (r instanceof TLRPC.TL_privacyValueAllowPremium) {
                token = "ALLOWPREMIUM";
            } else if (r instanceof TLRPC.TL_privacyValueAllowBots) {
                token = "ALLOWBOTS";
            } else if (r instanceof TLRPC.TL_privacyValueDisallowAll) {
                token = "DISALLOWALL";
            } else if (r instanceof TLRPC.TL_privacyValueDisallowContacts) {
                token = "DISALLOWCONTACTS";
            } else if (r instanceof TLRPC.TL_privacyValueDisallowBots) {
                token = "DISALLOWBOTS";
            } else if (r instanceof TLRPC.TL_privacyValueAllowUsers) {
                StringBuilder ids = new StringBuilder();
                for (Long id : ((TLRPC.TL_privacyValueAllowUsers) r).users) {
                    if (ids.length() > 0) ids.append(',');
                    ids.append(id);
                }
                token = "AU:" + ids;
            } else if (r instanceof TLRPC.TL_privacyValueDisallowUsers) {
                StringBuilder ids = new StringBuilder();
                for (Long id : ((TLRPC.TL_privacyValueDisallowUsers) r).users) {
                    if (ids.length() > 0) ids.append(',');
                    ids.append(id);
                }
                token = "DU:" + ids;
            }
            if (token != null) {
                if (sb.length() > 0) sb.append(';');
                sb.append(token);
            }
        }
        return sb.toString();
    }

    private static ArrayList<TLRPC.InputPrivacyRule> deserializeRules(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        ArrayList<TLRPC.InputPrivacyRule> out = new ArrayList<>();
        for (String token : s.split(";")) {
            if (token.isEmpty()) continue;
            if (token.equals("ALLOWALL")) {
                out.add(new TLRPC.TL_inputPrivacyValueAllowAll());
            } else if (token.equals("ALLOWCONTACTS")) {
                out.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
            } else if (token.equals("ALLOWPREMIUM")) {
                out.add(new TLRPC.TL_inputPrivacyValueAllowPremium());
            } else if (token.equals("ALLOWBOTS")) {
                out.add(new TLRPC.TL_inputPrivacyValueAllowBots());
            } else if (token.equals("DISALLOWALL")) {
                out.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
            } else if (token.equals("DISALLOWCONTACTS")) {
                out.add(new TLRPC.TL_inputPrivacyValueDisallowContacts());
            } else if (token.equals("DISALLOWBOTS")) {
                out.add(new TLRPC.TL_inputPrivacyValueDisallowBots());
            } else if (token.startsWith("AU:") || token.startsWith("DU:")) {
                boolean allow = token.startsWith("AU:");
                String rest = token.substring(3);
                if (rest.isEmpty()) continue;
                TLRPC.InputPrivacyRule rule = allow
                        ? new TLRPC.TL_inputPrivacyValueAllowUsers()
                        : new TLRPC.TL_inputPrivacyValueDisallowUsers();
                for (String idStr : rest.split(",")) {
                    try {
                        long id = Long.parseLong(idStr.trim());
                        TLRPC.InputUser iu = MessagesController.getInstance(UserConfig.selectedAccount)
                                .getInputUser(id);
                        if (iu != null) {
                            if (allow) {
                                ((TLRPC.TL_inputPrivacyValueAllowUsers) rule).users.add(iu);
                            } else {
                                ((TLRPC.TL_inputPrivacyValueDisallowUsers) rule).users.add(iu);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                out.add(rule);
            }
        }
        return out;
    }

    private AiqiyiGhostUtils() {
    }
}