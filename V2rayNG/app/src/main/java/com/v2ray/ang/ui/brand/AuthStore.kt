package com.v2ray.ang.ui.brand

import com.tencent.mmkv.MMKV

/**
 * Own MMKV store (separate from v2rayNG's own [com.v2ray.ang.handler.MmkvManager] ids)
 * for this white-label client's session token, role, and active subscription target.
 * MMKV.initialize() is already called once in AngApplication.onCreate() before any Activity runs,
 * so mmkvWithID is always safe here.
 */
object AuthStore {
    private const val ID = "BRAND_AUTH"
    private const val KEY_TOKEN = "token"
    private const val KEY_EMAIL = "email"
    private const val KEY_ROLE = "role"
    private const val KEY_SELECTED_SUB = "selected_subscription_id"
    private const val KEY_SELECTED_SERVER = "selected_server_id"

    private val storage by lazy { MMKV.mmkvWithID(ID, MMKV.MULTI_PROCESS_MODE) }

    fun saveSession(token: String, email: String, role: String) {
        storage.encode(KEY_TOKEN, token)
        storage.encode(KEY_EMAIL, email)
        storage.encode(KEY_ROLE, role)
    }

    fun getToken(): String? = storage.decodeString(KEY_TOKEN)?.takeIf { it.isNotBlank() }

    fun getEmail(): String? = storage.decodeString(KEY_EMAIL)?.takeIf { it.isNotBlank() }

    fun getRole(): String? = storage.decodeString(KEY_ROLE)?.takeIf { it.isNotBlank() }

    fun getSelectedSubscriptionId(): String? = storage.decodeString(KEY_SELECTED_SUB)?.takeIf { it.isNotBlank() }

    fun setSelectedSubscriptionId(subId: String?) {
        if (subId.isNullOrBlank()) {
            storage.removeValueForKey(KEY_SELECTED_SUB)
        } else {
            storage.encode(KEY_SELECTED_SUB, subId)
        }
    }

    fun getSelectedServerId(): String? = storage.decodeString(KEY_SELECTED_SERVER)?.takeIf { it.isNotBlank() }

    fun setSelectedServerId(serverId: String?) {
        if (serverId.isNullOrBlank()) {
            storage.removeValueForKey(KEY_SELECTED_SERVER)
        } else {
            storage.encode(KEY_SELECTED_SERVER, serverId)
        }
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun clear() {
        storage.removeValueForKey(KEY_TOKEN)
        storage.removeValueForKey(KEY_EMAIL)
        storage.removeValueForKey(KEY_ROLE)
        storage.removeValueForKey(KEY_SELECTED_SUB)
        storage.removeValueForKey(KEY_SELECTED_SERVER)
    }
}
