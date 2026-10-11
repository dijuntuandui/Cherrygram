#ifndef SK_CRYPTER_HPP
#define SK_CRYPTER_HPP

/* Aiqiyi: stub for missing upstream 'security/skCrypter.hpp'.
 * Provides a compile-time string "encryption" facade compatible with
 * skCrypt("...") usage: the returned value is usable wherever a
 * const char* / jstring-cast is expected.
 *
 * Implementation is intentionally a pass-through (no real obfuscation). */

#include <cstddef>

namespace skcrypter_detail {

/* Wrapper that keeps the string alive and supports implicit conversion
 * to const char*, mimicking skCrypter's skCrypt_t return object. */
template<std::size_t N>
struct skCrypt_t {
    char buf[N];
    constexpr skCrypt_t(const char (&s)[N]) : buf{} {
        for (std::size_t i = 0; i < N; ++i) buf[i] = s[i];
    }
    operator const char *() const { return buf; }
};

} /* namespace skcrypter_detail */

template<std::size_t N>
constexpr skcrypter_detail::skCrypt_t<N> skCrypt(const char (&s)[N]) {
    return skcrypter_detail::skCrypt_t<N>(s);
}

#endif /* SK_CRYPTER_HPP */
