#ifndef ES_PRIVACY_CONTROLS_H
#define ES_PRIVACY_CONTROLS_H
/* Internal, credential-free primitive; failure never authorizes admission. */
typedef enum {
    ES_PRIVACY_OK = 0,
    ES_PRIVACY_PLATFORM = 1,
    ES_PRIVACY_SUPPRESSION = 2,
    ES_PRIVACY_THREAD_SYNC = 3,
    ES_PRIVACY_RESET_CONTROL = 4
} es_privacy_result;
es_privacy_result es_privacy_establish(void);
#endif
