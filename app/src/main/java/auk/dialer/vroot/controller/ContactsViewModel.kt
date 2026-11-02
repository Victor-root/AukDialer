package auk.dialer.vroot.controller

import android.accounts.Account
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import auk.dialer.vroot.BuildConfig
import auk.dialer.vroot.modal.`interface`.IContactsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.modal.data.Contact
import auk.dialer.vroot.modal.data.ContactSaveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import auk.dialer.vroot.controller.util.indexLetter
import auk.dialer.vroot.controller.util.nameCollator

class ContactsViewModel(
    private val contactsRepo: IContactsRepository,
    private val preferenceManager: PreferenceManager,
    private val contentResolver: ContentResolver
) : ViewModel() {

    // Contacts added or edited elsewhere (another app, account sync) show up without a manual
    // refresh. A sync writes in bursts, taken here as one change.
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observerJob?.cancel()
            observerJob = viewModelScope.launch {
                delay(CHANGE_DEBOUNCE_MS)
                fetchContacts()
            }
        }
    }
    private var observerJob: Job? = null

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    val allContacts: StateFlow<List<Contact>> = _allContacts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _standardizeProgress = MutableStateFlow<Float?>(null)
    val standardizeProgress: StateFlow<Float?> = _standardizeProgress.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<Account>>(emptyList())
    val availableAccounts = _availableAccounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount = _selectedAccount.asStateFlow()

    private val _showPrivateOnly = MutableStateFlow(false)
    val showPrivateOnly = _showPrivateOnly.asStateFlow()

    private val _showLocalOnly = MutableStateFlow(false)
    val showLocalOnly = _showLocalOnly.asStateFlow()

    private val _visibleAccounts = MutableStateFlow<Set<String>?>(preferenceManager.getVisibleAccounts())
    val visibleAccountsFlow = _visibleAccounts.asStateFlow()

    private val _sortOrder = MutableStateFlow(preferenceManager.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0))
    val sortOrder = _sortOrder.asStateFlow()

    private val _displayOrder = MutableStateFlow(preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0))
    val displayOrder = _displayOrder.asStateFlow()

    /**
     * Groups the four filter flows so the outer combine stays within the
     * type-safe overloads. Past five flows, combine hands out an Array<Any?>
     * and every value has to be cast back by hand.
     */
    private data class ContactFilters(
        val privateOnly: Boolean,
        val localOnly: Boolean,
        val visibleAccounts: Set<String>?,
        val sortOrder: Int,
    )

    val filteredContacts = combine(
        _allContacts,
        _selectedAccount,
        combine(
            _showPrivateOnly,
            _showLocalOnly,
            _visibleAccounts,
            _sortOrder,
        ) { privateOnly, localOnly, visibleAccounts, sortOrder ->
            ContactFilters(privateOnly, localOnly, visibleAccounts, sortOrder)
        },
    ) { contacts, account, filters ->
        val (privateOnly, localOnly, visibleAccounts, sortOrder) = filters

        val baseFiltered = when {
            privateOnly -> contacts.filter { it.isPrivate }
            // Private contacts have no account either, yet are not in the phone's local memory.
            localOnly -> contacts.filter { !it.isPrivate && it.accountName == null && it.accountType == null }
            account == null -> {
                if (visibleAccounts == null) contacts
                else contacts.filter { contact ->
                    val key = if (contact.accountType == null && contact.accountName == null) "local|local" else "${contact.accountType}|${contact.accountName}"
                    visibleAccounts.contains(key) || contact.isPrivate
                }
            }
            else -> contacts.filter { it.accountName == account.name && it.accountType == account.type }
        }
        
        val collator = nameCollator()
        if (sortOrder == 1) {
            baseFiltered.sortedWith(compareBy(collator) { it.sortingLastName() })
        } else {
            baseFiltered.sortedWith(compareBy(collator) { it.name })
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedContacts = combine(filteredContacts, _sortOrder) { contacts, sortOrder ->
        val mainGroups = contacts.groupBy {
            val nameToUse = if (sortOrder == 1) {
                it.sortingLastName()
            } else {
                it.name
            }
            indexLetter(nameToUse)
        }.toMutableMap()

        val finalMap = linkedMapOf<Char, List<Contact>>()

        mainGroups.keys.filter { it.isLetter() }.sorted().forEach { char ->
            finalMap[char] = mainGroups[char]!!
        }

        val hashGroup = mainGroups['#']
        if (hashGroup != null) finalMap['#'] = hashGroup

        finalMap
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        fetchAccounts()
        try {
            contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        contentResolver.unregisterContentObserver(contactsObserver)
    }

    fun fetchContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_allContacts.value.isEmpty()) {
                _isLoading.value = true
            }
            val result = contactsRepo.getContacts()
            _allContacts.value = result
            _isLoading.value = false
        }
    }

    suspend fun getFullContactById(contactId: String): Contact? {
        return withContext(Dispatchers.IO) {
            contactsRepo.getContactById(contactId)
        }
    }
    
    suspend fun getFullContactByNumber(number: String): Contact? {
        return withContext(Dispatchers.IO) {
            contactsRepo.getContactByNumber(number)
        }
    }

    suspend fun getCloudDefaultAccount(): Account? = withContext(Dispatchers.IO) {
        contactsRepo.getCloudDefaultAccount()
    }

    fun fetchAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableAccounts.value = contactsRepo.getAvailableAccounts()
        }
    }

    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
        if (account != null) {
            _showPrivateOnly.value = false
            _showLocalOnly.value = false
        }
    }

    fun setShowPrivateOnly(show: Boolean) {
        _showPrivateOnly.value = show
        if (show) {
            _selectedAccount.value = null
            _showLocalOnly.value = false
        }
    }

    fun setShowLocalOnly(show: Boolean) {
        _showLocalOnly.value = show
        if (show) {
            _selectedAccount.value = null
            _showPrivateOnly.value = false
        }
    }

    fun setVisibleAccounts(accounts: Set<String>?) {
        if (accounts == null) {
            preferenceManager.setString(PreferenceManager.KEY_VISIBLE_ACCOUNTS, null)
        } else {
            preferenceManager.setVisibleAccounts(accounts)
        }
        _visibleAccounts.value = accounts
    }

    fun setSortOrder(order: Int) {
        preferenceManager.setInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, order)
        _sortOrder.value = order
    }

    fun setDisplayOrder(order: Int) {
        preferenceManager.setInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, order)
        _displayOrder.value = order
    }

    fun toggleFavorite(contact: Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFavStatus = !contact.isFavorite
            contactsRepo.toggleFavorite(contact.id, newFavStatus)
            
            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            if (newFavStatus) {
                if (!currentOrder.contains(contact.id)) {
                    currentOrder.add(contact.id)
                    preferenceManager.setFavoritesOrder(currentOrder)
                }
            } else {
                if (currentOrder.contains(contact.id)) {
                    currentOrder.remove(contact.id)
                    preferenceManager.setFavoritesOrder(currentOrder)
                }
            }
            
            fetchContacts()
        }
    }

    suspend fun saveContact(contact: Contact): ContactSaveResult {
        return withContext(Dispatchers.IO) {
            val saved = contactsRepo.saveContact(contact)
            if (BuildConfig.DEBUG_TOOLS) {
                val found = contactsRepo.getContacts().filter { it.name == contact.name }.map { it.id }
                Log.d(
                    "AukContactDebug",
                    "saveContact private=${contact.isPrivate} saved=$saved found=$found " +
                        "filters: account=${_selectedAccount.value} visible=${_visibleAccounts.value} " +
                        "privateOnly=${_showPrivateOnly.value} localOnly=${_showLocalOnly.value}"
                )
            }

            if (saved == ContactSaveResult.SAVED) {
                // A private contact has no account: it would leave the next new contact on none.
                if (!contact.isPrivate) {
                    preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, contact.accountName)
                    preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, contact.accountType)
                }
                fetchContacts()
            }
            saved
        }
    }
    
    fun getLastUsedAccount(): Account? {
        val name = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, null)
        val type = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, null)
        return if (name != null && type != null) {
            Account(name, type)
        } else {
            null
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch { deleteContactNow(contactId) }
    }

    /** Deletes [contactId] and returns once it is gone, for a screen that has to leave it behind. */
    suspend fun deleteContactNow(contactId: String) {
        withContext(Dispatchers.IO) {
            contactsRepo.deleteContact(contactId)

            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            if (currentOrder.contains(contactId)) {
                currentOrder.remove(contactId)
                preferenceManager.setFavoritesOrder(currentOrder)
            }

            fetchContacts()
        }
    }

    fun deleteContacts(contactIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.deleteContacts(contactIds)
            
            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            var changed = false
            contactIds.forEach { id ->
                if (currentOrder.contains(id)) {
                    currentOrder.remove(id)
                    changed = true
                }
            }
            if (changed) {
                preferenceManager.setFavoritesOrder(currentOrder)
            }
            
            fetchContacts()
        }
    }

    fun moveContacts(contactIds: List<String>, account: Account?) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.moveContacts(contactIds, account?.name, account?.type)
            fetchContacts()
        }
    }

    fun setCustomRingtone(contactId: String, ringtoneUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.setCustomRingtone(contactId, ringtoneUri)
        }
    }

    fun formatAllPhoneNumbers() {
        viewModelScope.launch(Dispatchers.IO) {
            _standardizeProgress.value = 0f
            contactsRepo.formatAllPhoneNumbers { current, total ->
                _standardizeProgress.value = if (total > 0) current.toFloat() / total else 1f
            }
            fetchContacts()
            _standardizeProgress.value = null
        }
    }

    fun makeContactPrivate(contactId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.makeContactPrivate(contactId)
            fetchContacts()
        }
    }

    fun makeContactPublic(contactId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.makeContactPublic(contactId)
            fetchContacts()
        }
    }

    fun exportPrivateContacts(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.exportPrivateContacts(uri)
        }
    }

    fun importPrivateContacts(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.importPrivateContacts(uri)
            fetchContacts()
        }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 500L
    }
}

/** The name a contact is filed under when sorting by last name: its family name, else its last word. */
private fun Contact.sortingLastName(): String = familyName ?: name.split(" ").lastOrNull() ?: name
