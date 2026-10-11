#ifndef SECURE_VALIDATOR_HPP
#define SECURE_VALIDATOR_HPP

/* Aiqiyi: stub for missing upstream 'security/secure_validator.hpp'.
 * Upstream only actively calls maybeForceDisconnectOrUpdate();
 * has_jni_hook/has_xhook/validate_signature are referenced only in
 * commented-out code, but we declare them anyway for safety. */

#include <jni.h>

namespace secure_validator {

inline void maybeForceDisconnectOrUpdate(JNIEnv *env, jint instanceNum, jint reason) {
    (void) env;
    (void) instanceNum;
    (void) reason;
    /* no-op: stub */
}

inline bool has_jni_hook(JNIEnv *env) {
    (void) env;
    return false;
}

inline bool has_xhook() {
    return false;
}

inline bool validate_signature(JNIEnv *env) {
    (void) env;
    return true;
}

} /* namespace secure_validator */

#endif /* SECURE_VALIDATOR_HPP */
