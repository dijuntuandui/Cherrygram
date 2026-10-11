#ifndef COLORADO_H
#define COLORADO_H

#ifdef __cplusplus
extern "C" {
#endif

/* Aiqiyi: stub for missing upstream 'colorado' module.
 * The only known entry point was check_signature(), which upstream
 * has already commented out in jni.c. Provide a no-op stub. */
int check_signature(void);

#ifdef __cplusplus
}
#endif

#endif /* COLORADO_H */
