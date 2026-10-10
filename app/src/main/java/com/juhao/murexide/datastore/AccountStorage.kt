package com.juhao.murexide.datastore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.userDataStore by preferencesDataStore(name = "user_data")

@Serializable
data class UserAccount(
    val username: String = "用户",
    val avatar: String = "",
    val id: String = "1",
    val token: String = "",
    val isValidated: Boolean = false,
    val drafts: Map<String, String> = emptyMap()
)

internal fun upsertAccountInList(
    accounts: List<UserAccount>,
    account: UserAccount,
    obsoleteAccountId: String? = null,
    moveToFront: Boolean = false
): List<UserAccount> {
    val replacedIds = setOfNotNull(account.id, obsoleteAccountId)
    val firstReplacedIndex = accounts.indexOfFirst { it.id in replacedIds }
    val remaining = accounts.filterNot { it.id in replacedIds }.toMutableList()
    val insertionIndex = when {
        moveToFront -> 0
        firstReplacedIndex < 0 -> remaining.size
        else -> firstReplacedIndex.coerceAtMost(remaining.size)
    }
    remaining.add(insertionIndex, account)
    return remaining
}

class AccountStorage private constructor(context: Context) {
    companion object {
        private val ACCOUNTS_KEY = stringPreferencesKey("user_accounts")
        private val CURRENT_USER_ID_KEY = stringPreferencesKey("current_user_id")
        private const val KEY_ALIAS = "account_encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BITS = 128

        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        @Volatile
        private var instance: AccountStorage? = null

        fun getInstance(context: Context): AccountStorage =
            instance ?: synchronized(this) {
                instance ?: AccountStorage(context.applicationContext).also { instance = it }
            }
    }

    private data class StoredAccounts(
        val accounts: List<UserAccount>,
        val currentUserId: String?
    )

    private val appContext = context.applicationContext
    private val dataStore = appContext.userDataStore
    private val secretKey: SecretKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadOrCreateSecretKey()
    }

    @Volatile
    private var currentUserIdSnapshot: String? = null

    // ========== 加密相关 ==========

    private fun loadOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
        require(combined.size > GCM_IV_SIZE_BYTES) { "Invalid encrypted account data" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = combined.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val encrypted = combined.copyOfRange(GCM_IV_SIZE_BYTES, combined.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun decodeAccounts(encryptedJson: String?): List<UserAccount> {
        if (encryptedJson.isNullOrEmpty()) return emptyList()
        return json.decodeFromString(decrypt(encryptedJson))
    }

    private fun encodeAccounts(accounts: List<UserAccount>): String =
        encrypt(json.encodeToString(accounts))

    // ========== 原子存储操作 ==========

    private suspend fun readStoredAccounts(): StoredAccounts = withContext(Dispatchers.IO) {
        val preferences = dataStore.data.first()
        StoredAccounts(
            accounts = decodeAccounts(preferences[ACCOUNTS_KEY]),
            currentUserId = preferences[CURRENT_USER_ID_KEY]
        ).also { currentUserIdSnapshot = it.currentUserId }
    }

    /**
     * DataStore serializes edit transforms. Reading, decrypting, changing, and encrypting the
     * account list in this single transform prevents a stale instance from overwriting newer data.
     */
    private suspend fun updateStoredAccounts(
        transform: (StoredAccounts) -> StoredAccounts
    ): StoredAccounts = withContext(Dispatchers.IO) {
        var updated: StoredAccounts? = null
        dataStore.edit { preferences ->
            val stored = StoredAccounts(
                accounts = decodeAccounts(preferences[ACCOUNTS_KEY]),
                currentUserId = preferences[CURRENT_USER_ID_KEY]
            )
            val transformed = transform(stored)
            val normalized = transformed.copy(
                currentUserId = transformed.currentUserId?.takeIf { selectedId ->
                    transformed.accounts.any { it.id == selectedId }
                }
            )

            if (normalized != stored) {
                preferences[ACCOUNTS_KEY] = encodeAccounts(normalized.accounts)
                normalized.currentUserId?.let { selectedId ->
                    preferences[CURRENT_USER_ID_KEY] = selectedId
                } ?: preferences.remove(CURRENT_USER_ID_KEY)
            }
            updated = normalized
        }

        checkNotNull(updated).also { currentUserIdSnapshot = it.currentUserId }
    }

    // ========== 账户存储相关 ==========

    suspend fun getDefaultAccount(): UserAccount {
        val stored = readStoredAccounts()
        return stored.currentUserId
            ?.let { currentId -> stored.accounts.find { it.id == currentId } }
            ?: stored.accounts.firstOrNull()
            ?: UserAccount()
    }

    suspend fun addAccount(account: UserAccount) {
        updateStoredAccounts { stored ->
            if (stored.accounts.any { it.id == account.id }) {
                stored
            } else {
                stored.copy(
                    accounts = stored.accounts + account,
                    currentUserId = if (stored.accounts.isEmpty()) {
                        account.id
                    } else {
                        stored.currentUserId
                    }
                )
            }
        }
    }

    suspend fun updateAccount(account: UserAccount) {
        updateStoredAccounts { stored ->
            stored.copy(
                accounts = stored.accounts.map { existing ->
                    if (existing.id == account.id) account else existing
                }
            )
        }
    }

    /**
     * Replaces every stored copy of this account in one DataStore transaction.
     * [obsoleteAccountId] is used to remove a temporary pre-validation account ID.
     */
    suspend fun upsertAccount(
        account: UserAccount,
        makeCurrent: Boolean,
        obsoleteAccountId: String? = null
    ) {
        updateStoredAccounts { stored ->
            stored.copy(
                accounts = upsertAccountInList(
                    accounts = stored.accounts,
                    account = account,
                    obsoleteAccountId = obsoleteAccountId,
                    moveToFront = makeCurrent
                ),
                currentUserId = if (makeCurrent) account.id else stored.currentUserId
            )
        }
    }

    suspend fun validateAccount(newAccount: UserAccount): Boolean {
        var validated = false
        updateStoredAccounts { stored ->
            val currentAccount = stored.currentUserId
                ?.let { currentId -> stored.accounts.find { it.id == currentId } }
                ?: return@updateStoredAccounts stored
            val validatedAccount = newAccount.copy(
                isValidated = true,
                token = currentAccount.token
            )
            validated = true
            stored.copy(
                accounts = upsertAccountInList(
                    accounts = stored.accounts,
                    account = validatedAccount,
                    obsoleteAccountId = currentAccount.id.takeIf { it != validatedAccount.id },
                    moveToFront = true
                ),
                currentUserId = validatedAccount.id
            )
        }
        return validated
    }

    suspend fun removeAccount(accountId: String) {
        updateStoredAccounts { stored ->
            stored.copy(
                accounts = stored.accounts.filter { it.id != accountId },
                currentUserId = stored.currentUserId.takeUnless { it == accountId }
            )
        }
    }

    suspend fun clearAccounts() {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences.remove(ACCOUNTS_KEY)
                preferences.remove(CURRENT_USER_ID_KEY)
            }
        }
        currentUserIdSnapshot = null
    }

    suspend fun getAccounts(): List<UserAccount> = readStoredAccounts().accounts

    val userAccountsFlow: Flow<List<UserAccount>> = dataStore.data
        .map { preferences ->
            try {
                decodeAccounts(preferences[ACCOUNTS_KEY])
            } catch (_: Exception) {
                emptyList()
            }
        }
        .catch { emit(emptyList()) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    // ========== 当前用户管理 ==========

    suspend fun setCurrentUser(accountId: String) {
        updateStoredAccounts { stored ->
            val account = stored.accounts.find { it.id == accountId }
                ?: return@updateStoredAccounts stored
            stored.copy(
                accounts = listOf(account) + stored.accounts.filter { it.id != accountId },
                currentUserId = accountId
            )
        }
    }

    suspend fun removeCurrentUser() {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences -> preferences.remove(CURRENT_USER_ID_KEY) }
        }
        currentUserIdSnapshot = null
    }

    suspend fun getCurrentUserId(): String? = withContext(Dispatchers.IO) {
        dataStore.data.first()[CURRENT_USER_ID_KEY]
    }.also { currentUserIdSnapshot = it }

    val currentUserIdFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[CURRENT_USER_ID_KEY].also { currentUserIdSnapshot = it }
        }
        .distinctUntilChanged()

    /**
     * 当前账号的原子快照。
     *
     * 用户 ID 与账号列表来自同一次 DataStore 发射，避免切换账号时把新 ID 和旧 Token
     * 临时组合到一起。WebSocket 登录应直接使用这里已经校验并持久化的账号信息。
     */
    val currentAccountFlow: Flow<UserAccount?> = dataStore.data
        .map { preferences ->
            val currentUserId = preferences[CURRENT_USER_ID_KEY]
                .also { currentUserIdSnapshot = it }
            val accounts = try {
                decodeAccounts(preferences[ACCOUNTS_KEY])
            } catch (_: Exception) {
                emptyList()
            }
            currentUserId?.let { id -> accounts.find { it.id == id } }
        }
        .catch { emit(null) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    suspend fun getCurrentAccount(): UserAccount? {
        val stored = readStoredAccounts()
        return stored.currentUserId
            ?.let { currentId -> stored.accounts.find { it.id == currentId } }
    }

    suspend fun getCurrentToken(): String? =
        getCurrentAccount()?.token?.takeIf { it.isNotEmpty() }

    val currentTokenFlow: Flow<String?> = currentAccountFlow
        .map { account -> account?.token?.takeIf { it.isNotEmpty() } }
        .distinctUntilChanged()

    /** 获取最近一次异步读取到的用户 ID；应用启动前可能为 null。 */
    fun getCurrentUserIdSync(): String? = currentUserIdSnapshot

    suspend fun updateCurrentToken(token: String) {
        updateStoredAccounts { stored ->
            val currentId = stored.currentUserId ?: return@updateStoredAccounts stored
            stored.copy(
                accounts = stored.accounts.map { account ->
                    if (account.id == currentId) account.copy(token = token) else account
                }
            )
        }
    }

    // ========== 便捷方法 ==========

    suspend fun getAccountById(accountId: String): UserAccount? =
        getAccounts().find { it.id == accountId }

    suspend fun accountExists(accountId: String): Boolean =
        getAccounts().any { it.id == accountId }

    suspend fun getAccountCount(): Int = getAccounts().size

    suspend fun getToken(accountId: String): String? = getAccountById(accountId)?.token

    suspend fun updateToken(token: String, accountId: String? = null) {
        updateStoredAccounts { stored ->
            val targetId = accountId
                ?: stored.currentUserId
                ?: stored.accounts.firstOrNull()?.id
                ?: return@updateStoredAccounts stored
            stored.copy(
                accounts = stored.accounts.map { account ->
                    if (account.id == targetId) account.copy(token = token) else account
                }
            )
        }
    }

    suspend fun switchAccount(accountId: String) {
        setCurrentUser(accountId)
    }

    suspend fun getCurrentUsername(): String? = getCurrentAccount()?.username

    suspend fun getCurrentAvatar(): String? = getCurrentAccount()?.avatar

    suspend fun getCurrentUserInfo(): UserAccount? = getCurrentAccount()
    
    suspend fun saveDraft(chatId: String, chatType: Int, draft: String) {
        updateStoredAccounts { stored ->
            val currentId = stored.currentUserId ?: return@updateStoredAccounts stored
            stored.copy(
                accounts = stored.accounts.map { account ->
                    if (account.id == currentId) {
                        val key = "${chatType}_$chatId"
                        val newDrafts = if (draft.isBlank()) {
                            account.drafts - key
                        } else {
                            account.drafts + (key to draft)
                        }
                        account.copy(drafts = newDrafts)
                    } else account
                }
            )
        }
    }
    
    suspend fun getDraft(chatId: String, chatType: Int): String {
        val account = getCurrentAccount() ?: return ""
        return account.drafts["${chatType}_$chatId"] ?: ""
    }
    
    suspend fun clearDraft(chatId: String, chatType: Int) {
        saveDraft(chatId, chatType, "")
    }
    
    val currentDraftsFlow: Flow<Map<String, String>> = currentAccountFlow
        .map { it?.drafts ?: emptyMap() }
        .distinctUntilChanged()
}
