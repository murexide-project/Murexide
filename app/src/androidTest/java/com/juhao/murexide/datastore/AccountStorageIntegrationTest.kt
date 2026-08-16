package com.juhao.murexide.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountStorageIntegrationTest {
    private lateinit var storage: AccountStorage

    @Before
    fun setUp() {
        storage = AccountStorage.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        runBlocking { storage.clearAccounts() }
    }

    @After
    fun tearDown() {
        runBlocking { storage.clearAccounts() }
    }

    @Test
    fun emptyStorageHasDefaultValueButNoCurrentAccountOrToken() = runBlocking {
        assertEquals(UserAccount(), storage.getDefaultAccount())
        assertEquals(emptyList<UserAccount>(), storage.getAccounts())
        assertNull(storage.getCurrentAccount())
        assertNull(storage.getCurrentToken())
        assertNull(storage.currentAccountFlow.first())
        assertNull(storage.currentTokenFlow.first())
    }

    @Test
    fun firstAccountBecomesCurrentAndDuplicateAddDoesNotOverwriteIt() = runBlocking {
        val account = account("user-1", "token-1")

        storage.addAccount(account)
        storage.addAccount(account.copy(username = "changed", token = "token-2"))

        assertEquals(listOf(account), storage.getAccounts())
        assertEquals(account, storage.getCurrentAccount())
        assertEquals("token-1", storage.getCurrentToken())
    }

    @Test
    fun concurrentAddsKeepBothAccountsAndAValidCurrentSelection() = runBlocking {
        coroutineScope {
            listOf(
                async { storage.addAccount(account("user-a", "token-a")) },
                async { storage.addAccount(account("user-b", "token-b")) }
            ).awaitAll()
        }

        assertEquals(setOf("user-a", "user-b"), storage.getAccounts().map { it.id }.toSet())
        assertEquals(2, storage.getAccountCount())
        assertTrue(storage.getCurrentUserId() in setOf("user-a", "user-b"))
    }

    @Test
    fun concurrentAccountScopedTokenUpdatesDoNotLoseEitherUpdate() = runBlocking {
        storage.addAccount(account("user-a", "token-a"))
        storage.addAccount(account("user-b", "token-b"))

        coroutineScope {
            listOf(
                async { storage.updateToken("token-a-new", accountId = "user-a") },
                async { storage.updateToken("token-b-new", accountId = "user-b") }
            ).awaitAll()
        }

        assertEquals("token-a-new", storage.getToken("user-a"))
        assertEquals("token-b-new", storage.getToken("user-b"))
    }

    @Test
    fun upsertCanReplaceTemporaryAccountAndSelectTheValidatedRecord() = runBlocking {
        storage.addAccount(account("temporary", "temporary-token"))
        storage.addAccount(account("other", "other-token"))

        storage.upsertAccount(
            account("real", "login-token"),
            makeCurrent = true,
            obsoleteAccountId = "temporary"
        )

        assertEquals(listOf("real", "other"), storage.getAccounts().map { it.id })
        assertEquals("real", storage.getCurrentUserId())
        assertEquals("login-token", storage.getCurrentToken())
        assertFalse(storage.accountExists("temporary"))
    }

    @Test
    fun validationCarriesForwardCurrentTokenAndRemovesTemporaryId() = runBlocking {
        storage.addAccount(account("temporary", "session-token", validated = false))

        val validated = UserAccount(
            username = "validated-user",
            id = "real-id",
            token = "client-supplied-token",
            isValidated = false
        )

        assertTrue(storage.validateAccount(validated))

        val stored = storage.getAccountById("real-id")
        assertEquals("session-token", stored?.token)
        assertTrue(stored?.isValidated == true)
        assertFalse(storage.accountExists("temporary"))
        assertEquals("real-id", storage.getCurrentUserId())
        assertEquals("real-id", storage.getAccounts().first().id)
    }

    @Test
    fun validationReturnsFalseAndDoesNotAddAnAccountWithoutCurrentSelection() = runBlocking {
        assertFalse(storage.validateAccount(account("real-id", "ignored-token")))
        assertEquals(emptyList<UserAccount>(), storage.getAccounts())

        storage.addAccount(account("existing", "existing-token"))
        storage.removeCurrentUser()

        assertFalse(storage.validateAccount(account("another-id", "ignored-token")))
        assertEquals(listOf("existing"), storage.getAccounts().map { it.id })
        assertNull(storage.getCurrentAccount())
    }

    @Test
    fun switchingMovesAValidAccountToTheFrontAndIgnoresUnknownIds() = runBlocking {
        storage.addAccount(account("user-a", "token-a"))
        storage.addAccount(account("user-b", "token-b"))

        storage.switchAccount("user-b")
        assertEquals("user-b", storage.getCurrentUserId())
        assertEquals(listOf("user-b", "user-a"), storage.getAccounts().map { it.id })

        storage.switchAccount("missing")
        assertEquals("user-b", storage.getCurrentUserId())
        assertEquals(listOf("user-b", "user-a"), storage.getAccounts().map { it.id })
    }

    @Test
    fun accountAndTokenFlowsObserveTheSameCurrentAccountUpdate() = runBlocking {
        val account = account("flow-user", "flow-token")

        assertEquals(emptyList<UserAccount>(), storage.userAccountsFlow.first())
        storage.addAccount(account)

        assertEquals(account, storage.currentAccountFlow.first { it?.id == account.id })
        assertEquals(
            listOf(account),
            storage.userAccountsFlow.first { it.any { stored -> stored.id == account.id } }
        )
        assertEquals("flow-token", storage.currentTokenFlow.first { it == "flow-token" })

        storage.updateCurrentToken("flow-token-new")
        assertEquals("flow-token-new", storage.currentTokenFlow.first { it == "flow-token-new" })
        assertEquals("flow-token-new", storage.getCurrentAccount()?.token)
    }

    @Test
    fun accountScopedTokenUpdateLeavesSelectedAccountUnchanged() = runBlocking {
        storage.addAccount(account("user-a", "token-a"))
        storage.addAccount(account("user-b", "token-b"))
        storage.switchAccount("user-b")

        storage.updateToken("token-a-new", accountId = "user-a")

        assertEquals("user-b", storage.getCurrentUserId())
        assertEquals("token-b", storage.getCurrentToken())
        assertEquals("token-a-new", storage.getToken("user-a"))

        storage.updateToken("token-b-new")
        assertEquals("token-b-new", storage.getCurrentToken())
    }

    @Test
    fun removingCurrentAccountClearsSelectionButKeepsOtherAccounts() = runBlocking {
        storage.addAccount(account("user-a", "token-a"))
        storage.addAccount(account("user-b", "token-b"))
        storage.switchAccount("user-b")

        storage.removeAccount("user-b")

        assertEquals(listOf("user-a"), storage.getAccounts().map { it.id })
        assertNull(storage.getCurrentAccount())
        assertNull(storage.getCurrentToken())
        assertEquals("user-a", storage.getDefaultAccount().id)
    }

    private fun account(id: String, token: String, validated: Boolean = true) = UserAccount(
        username = id,
        id = id,
        token = token,
        isValidated = validated
    )
}
