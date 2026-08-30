package com.v2ray.ang.ui.brand

import com.tencent.mmkv.MMKV

/**
 * Own MMKV store (separate from v2rayNG's own [com.v2ray.ang.handler.MmkvManager] ids)
 * for this white-label client's session token. MMKV.initialize() is already called once
 * in AngApplication.onCreate() before any Activity runs, so mmkvWithID is always safe here.
 */
object AuthStore {
    private const val ID = "BRAND_AUTH"
    private const val KEY_TOKEN = "token"
    private const val KEY_EMAIL = "email"

    private val storage by lazy { MMKV.mmkvWithID(ID, MMKV.MULTI_PROCESS_MODE) }

    fun saveSession(token: String, email: String) {
        storage.encode(KEY_TOKEN, token)
        storage.encode(KEY_EMAIL, email)
    }

    fun getToken(): String? = storage.decodeString(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    fun getEmail(): String? = storage.decodeString(KEY_EMAIL)?.takeIf { it.isNotBlank() }

    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() {
        storage.removeValueForKey(KEY_TOKEN)
        storage.removeValueForKey(KEY_EMAIL)
    }
}
