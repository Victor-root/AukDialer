package auk.dialer.vroot.view.screen

import auk.dialer.vroot.view.theme.softAccentColors
import android.accounts.Account
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.view.components.AukTopBarTitle
import auk.dialer.vroot.controller.ContactsViewModel
import auk.dialer.vroot.controller.util.ContactTypeLabels
import auk.dialer.vroot.controller.util.ContactUtils
import auk.dialer.vroot.controller.util.deduplicateNumbers
import auk.dialer.vroot.modal.data.AddressEntry
import auk.dialer.vroot.modal.data.Contact
import auk.dialer.vroot.modal.data.ContactSaveResult
import auk.dialer.vroot.modal.data.EmailEntry
import auk.dialer.vroot.modal.data.PhoneNumberEntry
import auk.dialer.vroot.view.components.AukAvatar
import auk.dialer.vroot.view.components.AukConfirmationDialog
import auk.dialer.vroot.view.components.AukDialog
import auk.dialer.vroot.view.components.AukDialogAction
import auk.dialer.vroot.view.components.AukDropdownMenu
import auk.dialer.vroot.view.components.AukDropdownMenuItem
import auk.dialer.vroot.view.components.AukExpressiveCard
import auk.dialer.vroot.view.components.AukSectionHeader
import auk.dialer.vroot.view.components.AukSelectionDialog
import auk.dialer.vroot.view.components.AukSelectionRow
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel
import auk.dialer.vroot.view.theme.aukAccentTopAppBarColors
import auk.dialer.vroot.view.theme.aukExceptBottom
import auk.dialer.vroot.view.theme.aukScrollContentPadding

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactEditScreen(
    contactId: String? = null,
    initialName: String? = null,
    initialPhone: String? = null,
    navigator: DestinationsNavigator
) {
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val availableAccounts by contactsVM.availableAccounts.collectAsState()
    val context = LocalContext.current

    val initialSplit = remember { splitDisplayName(initialName ?: "") }
    var givenName by remember { mutableStateOf(initialSplit.first) }
    var familyName by remember { mutableStateOf(initialSplit.second) }
    var nickname by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var isPrivate by remember { mutableStateOf(false) }
    // What this screen does not edit is carried over from it, or saving would wipe it.
    var loadedContact by remember { mutableStateOf<Contact?>(null) }
    // Set when the system only takes new contacts into this cloud account, not into device storage.
    var cloudDefaultAccount by remember { mutableStateOf<Account?>(null) }

    val phones = remember { mutableStateListOf(PhoneNumberEntry("")) }
    val emails = remember { mutableStateListOf(EmailEntry("")) }
    val addresses = remember { mutableStateListOf(AddressEntry("")) }

    val displayName = "${givenName.trim()} ${familyName.trim()}".trim()

    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var saveFailure by remember { mutableStateOf<ContactSaveResult?>(null) }

    LaunchedEffect(contactId, availableAccounts) {
        cloudDefaultAccount = contactsVM.getCloudDefaultAccount()
        if (contactId != null && contactId != "0" && contactId != "null") {
            val existing = contactsVM.getFullContactById(contactId)
            if (existing != null) {
                loadedContact = existing
                val split = if (!existing.givenName.isNullOrBlank() || !existing.familyName.isNullOrBlank()) {
                    (existing.givenName ?: "") to (existing.familyName ?: "")
                } else {
                    splitDisplayName(existing.name)
                }
                givenName = split.first
                familyName = split.second
                nickname = existing.nickname ?: ""
                notes = existing.notes ?: ""
                photoUri = existing.photoUri
                isPrivate = existing.isPrivate
                if (selectedAccount == null && !existing.isPrivate) {
                    selectedAccount = existing.account()
                }

                phones.clear()
                val existingPhones = existing.phones.ifEmpty {
                    existing.phoneNumbers.map { PhoneNumberEntry(it) }
                }
                if (existingPhones.isNotEmpty()) {
                    phones.addAll(existingPhones)
                } else {
                    phones.add(PhoneNumberEntry(""))
                }

                emails.clear()
                val existingEmails = existing.emailEntries.ifEmpty {
                    existing.emails.map { EmailEntry(it) }
                }
                if (existingEmails.isNotEmpty()) {
                    emails.addAll(existingEmails)
                } else {
                    emails.add(EmailEntry(""))
                }

                addresses.clear()
                val existingAddresses = existing.addressEntries.ifEmpty {
                    existing.addresses.map { AddressEntry(it) }
                }
                if (existingAddresses.isNotEmpty()) {
                    addresses.addAll(existingAddresses)
                } else {
                    addresses.add(AddressEntry(""))
                }
            }
        } else {
            if (selectedAccount == null) {
                selectedAccount = listOfNotNull(contactsVM.getLastUsedAccount(), cloudDefaultAccount)
                    .firstNotNullOfOrNull { preferred ->
                        availableAccounts.find { it.name == preferred.name && it.type == preferred.type }
                    }
            }

            if (!initialPhone.isNullOrBlank() && phones.all { it.number.isBlank() }) {
                phones.clear()
                phones.add(PhoneNumberEntry(initialPhone))
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) photoUri = uri.toString() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AukTopBarTitle(
                        if (contactId == null || contactId == "0") stringResource(R.string.contact_create_title) else stringResource(R.string.contact_edit_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (contactId != null && contactId != "0") {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Icon(Icons.Default.Delete, null)
                        }
                    }
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val savedPhones = phones.filter { it.number.isNotBlank() }
                                    val savedEmails = emails.filter { it.address.isNotBlank() }
                                    val savedAddresses = addresses.filter { it.address.isNotBlank() }
                                    val editedContact = Contact(
                                        id = if (contactId == "null" || contactId == "0" || contactId == null) "0" else contactId,
                                        name = displayName,
                                        givenName = givenName.trim().ifBlank { null },
                                        familyName = familyName.trim().ifBlank { null },
                                        nickname = nickname.ifBlank { null },
                                        phoneNumbers = savedPhones.map { it.number },
                                        emails = savedEmails.map { it.address },
                                        phones = savedPhones,
                                        emailEntries = savedEmails,
                                        addresses = savedAddresses.map { it.address },
                                        addressEntries = savedAddresses,
                                        photoUri = photoUri,
                                        accountName = if (isPrivate) null else selectedAccount?.name,
                                        accountType = if (isPrivate) null else selectedAccount?.type,
                                        isPrivate = isPrivate,
                                        notes = notes.ifBlank { null }
                                    )
                                    val contactToSave = loadedContact?.let { loaded ->
                                        editedContact.copy(
                                            isFavorite = loaded.isFavorite,
                                            customRingtone = loaded.customRingtone,
                                            events = loaded.events
                                        )
                                    } ?: editedContact
                                    val result = contactsVM.saveContact(contactToSave)
                                    if (result == ContactSaveResult.SAVED) {
                                        navigator.navigateUp()
                                    } else {
                                        isSaving = false
                                        saveFailure = result
                                    }
                                }
                            },
                            enabled = displayName.isNotBlank() && phones.any { it.number.isNotBlank() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                        }
                    }
                },
                colors = aukAccentTopAppBarColors()
            )
        }
    ) { innerPadding ->
        saveFailure?.let { failure ->
            SaveFailureDialog(failure = failure, onDismissRequest = { saveFailure = null })
        }

        if (showDeleteDialog) {
            AukConfirmationDialog(
                onDismissRequest = { showDeleteDialog = false },
                onConfirm = {
                    if (contactId != null) {
                        // Gone before going back, so the screen below finds it missing.
                        scope.launch {
                            contactsVM.deleteContactNow(contactId)
                            navigator.navigateUp()
                        }
                    }
                },
                title = stringResource(R.string.contact_delete_dialog_title),
                message = stringResource(R.string.contact_delete_dialog_message),
                confirmLabel = stringResource(R.string.action_delete),
                icon = Icons.Default.Delete,
                isDestructive = true
            )
        }

        LazyColumn(
            modifier = Modifier
                .padding(innerPadding.aukExceptBottom())
                .fillMaxSize()
                // Edge to edge, the window no longer shrinks for the keyboard: the form makes room itself.
                .imePadding(),
            contentPadding = aukScrollContentPadding(16.dp, innerPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AukAvatar(
                            name = displayName,
                            photoUri = photoUri,
                            modifier = Modifier.size(120.dp),
                            textStyle = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            iconSize = 64.dp
                        )

                        Row {
                            if (photoUri != null) {
                                SmallFloatingActionButton(
                                    onClick = { photoUri = null },
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            SmallFloatingActionButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            item {
                AukSectionHeader(title = stringResource(R.string.contact_edit_account_header))
                var showPicker by remember { mutableStateOf(false) }

                val privateTitle = stringResource(R.string.contact_edit_private_storage)
                val privateDesc = stringResource(R.string.contact_edit_private_storage_description)
                val localTitle = stringResource(R.string.label_local_memory)
                val localDesc = if (cloudDefaultAccount != null) {
                    stringResource(R.string.contact_edit_local_memory_unavailable)
                } else {
                    ""
                }

                // The contact's own account stays offered even when it is not one new contacts can use
                // (a phone maker's own account): editing must not quietly move the contact out of it.
                val pickerAccounts = remember(availableAccounts, loadedContact) {
                    (availableAccounts + listOfNotNull(loadedContact?.account())).distinct()
                }
                val accountOptions = remember(pickerAccounts, privateTitle, localTitle, localDesc) {
                    listOf(
                        "private" to Triple(privateTitle, privateDesc, Icons.Default.Lock),
                        "local" to Triple(localTitle, localDesc, Icons.Default.CloudOff)
                    ) + pickerAccounts.map { acc ->
                        accountKey(acc) to Triple(
                            ContactUtils.getAccountTitle(context, acc),
                            ContactUtils.getAccountSubtitle(context, acc).orEmpty(),
                            ContactUtils.getAccountIcon(acc)
                        )
                    }
                }
                val isCurrent: (Pair<String, Triple<String, String, ImageVector>>) -> Boolean = { option ->
                    when (option.first) {
                        "private" -> isPrivate
                        "local" -> !isPrivate && selectedAccount == null
                        else -> !isPrivate && selectedAccount?.let(::accountKey) == option.first
                    }
                }

                // Drawn like the chosen row of the picker it opens, so both read the same.
                accountOptions.firstOrNull(isCurrent)?.let { current ->
                    AukSelectionRow(
                        label = current.second.first,
                        supporting = current.second.second.takeIf { it.isNotBlank() },
                        icon = current.second.third,
                        selected = true,
                        trailingIcon = Icons.Default.ArrowDropDown,
                        onClick = { showPicker = true }
                    )
                }

                if (showPicker) {
                    AukSelectionDialog(
                        onDismissRequest = { showPicker = false },
                        title = stringResource(R.string.contact_edit_select_account_title),
                        icon = Icons.Default.AccountBalance,
                        items = accountOptions,
                        itemLabel = { option -> option.second.first },
                        itemSupporting = { option -> option.second.second },
                        itemIcon = { option -> option.second.third },
                        isEnabled = { option -> option.first != "local" || cloudDefaultAccount == null },
                        isSelected = isCurrent,
                        onItemSelected = { selectedOption ->
                            when (selectedOption.first) {
                                "private" -> {
                                    selectedAccount = null
                                    isPrivate = true
                                }
                                "local" -> {
                                    selectedAccount = null
                                    isPrivate = false
                                }
                                else -> {
                                    selectedAccount = pickerAccounts.find { accountKey(it) == selectedOption.first }
                                    isPrivate = false
                                }
                            }
                            showPicker = false
                        }
                    )
                }
            }

            item {
                AukSectionHeader(title = stringResource(R.string.contact_edit_identity_header))
                AukExpressiveCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContactTextField(
                            value = givenName,
                            onValueChange = { givenName = it },
                            label = { Text(stringResource(R.string.contact_edit_first_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Person, null) }
                        )
                        ContactTextField(
                            value = familyName,
                            onValueChange = { familyName = it },
                            label = { Text(stringResource(R.string.contact_edit_last_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Badge, null) }
                        )
                        ContactTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text(stringResource(R.string.contact_edit_nickname)) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) }
                        )
                    }
                }
            }


            item {
                AukSectionHeader(title = stringResource(R.string.contact_edit_phone_numbers_header))
                AukExpressiveCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val phoneFieldLabel = stringResource(R.string.contact_edit_phone_field_label)
                        phones.forEachIndexed { index, phone ->
                            TypedEditField(
                                value = phone.number,
                                onValueChange = { phones[index] = phone.copy(number = it) },
                                label = phoneFieldLabel,
                                typeIcon = ContactTypeLabels::phoneTypeIcon,
                                keyboardType = KeyboardType.Phone,
                                typeValue = phone.type,
                                typeOptions = ContactTypeLabels.phoneTypeOptions,
                                typeLabel = { ContactTypeLabels.phoneTypeLabel(context, it, null) },
                                onTypeChange = { phones[index] = phone.copy(type = it) },
                                onDelete = {
                                    if (phones.size > 1) {
                                        phones.removeAt(index)
                                    } else {
                                        phones[0] = PhoneNumberEntry("")
                                    }
                                }
                            )
                        }
                        TextButton(
                            onClick = { phones.add(PhoneNumberEntry("")) },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.contact_edit_add_phone))
                        }
                    }
                }
            }


            item {
                AukSectionHeader(title = stringResource(R.string.contact_edit_emails_header))
                AukExpressiveCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val emailFieldLabel = stringResource(R.string.label_email)
                        emails.forEachIndexed { index, email ->
                            TypedEditField(
                                value = email.address,
                                onValueChange = { emails[index] = email.copy(address = it) },
                                label = emailFieldLabel,
                                typeIcon = ContactTypeLabels::emailTypeIcon,
                                keyboardType = KeyboardType.Email,
                                typeValue = email.type,
                                typeOptions = ContactTypeLabels.emailTypeOptions,
                                typeLabel = { ContactTypeLabels.emailTypeLabel(context, it, null) },
                                onTypeChange = { emails[index] = email.copy(type = it) },
                                onDelete = {
                                    if (emails.size > 1) {
                                        emails.removeAt(index)
                                    } else {
                                        emails[0] = EmailEntry("")
                                    }
                                }
                            )
                        }
                        TextButton(
                            onClick = { emails.add(EmailEntry("")) },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.contact_edit_add_email))
                        }
                    }
                }
            }


            item {
                AukSectionHeader(title = stringResource(R.string.contact_edit_address_header))
                AukExpressiveCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val addressFieldLabel = stringResource(R.string.label_address)
                        addresses.forEachIndexed { index, address ->
                            TypedEditField(
                                value = address.address,
                                onValueChange = { addresses[index] = address.copy(address = it) },
                                label = addressFieldLabel,
                                typeIcon = ContactTypeLabels::addressTypeIcon,
                                typeValue = address.type,
                                typeOptions = ContactTypeLabels.addressTypeOptions,
                                typeLabel = { ContactTypeLabels.addressTypeLabel(context, it, null) },
                                onTypeChange = { addresses[index] = address.copy(type = it) },
                                onDelete = {
                                    if (addresses.size > 1) {
                                        addresses.removeAt(index)
                                    } else {
                                        addresses[0] = AddressEntry("")
                                    }
                                }
                            )
                        }
                        TextButton(
                            onClick = { addresses.add(AddressEntry("")) },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.contact_edit_add_address))
                        }
                    }
                }
            }

            item {
                AukSectionHeader(title = stringResource(R.string.contact_edit_notes_header))
                AukExpressiveCard {
                    ContactTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                        minLines = 3
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

fun splitDisplayName(full: String): Pair<String, String> {
    val trimmed = full.trim()
    if (trimmed.isEmpty()) return "" to ""
    val parts = trimmed.split(Regex("\\s+"))
    return if (parts.size == 1) {
        parts[0] to ""
    } else {
        parts.first() to parts.drop(1).joinToString(" ")
    }
}

/** A phone number or email whose type is picked from its own icon, which then shows that type. */
@Composable
fun TypedEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    typeIcon: (Int) -> ImageVector,
    typeValue: Int,
    typeOptions: List<Int>,
    typeLabel: (Int) -> String,
    onTypeChange: (Int) -> Unit,
    onDelete: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    EditField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardType = keyboardType,
        onDelete = onDelete,
        leadingIcon = {
            var showTypeMenu by remember { mutableStateOf(false) }
            Box {
                // The chevron tells the icon opens a choice, like the account field's.
                Row(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .clickable { showTypeMenu = true }
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(typeIcon(typeValue), contentDescription = typeLabel(typeValue))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                AukDropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false }
                ) {
                    typeOptions.forEach { option ->
                        AukDropdownMenuItem(
                            text = { Text(typeLabel(option)) },
                            leadingIcon = { Icon(typeIcon(option), null) },
                            onClick = {
                                onTypeChange(option)
                                showTypeMenu = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable () -> Unit,
    onDelete: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        ContactTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            leadingIcon = leadingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                Icons.Default.RemoveCircleOutline,
                null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * A field of the form: filled without an outline, so the card around it is the only frame, and
 * tinted with the accent while it is being typed in.
 */
@Composable
private fun ContactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: @Composable () -> Unit,
    label: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        leadingIcon = leadingIcon,
        modifier = modifier,
        keyboardOptions = keyboardOptions,
        minLines = minLines,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedContainerColor = MaterialTheme.softAccentColors.field,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.primary
        )
    )
}

/** An account told apart by type as well as name: one address can hold several kinds of account. */
private fun accountKey(account: Account): String = "${account.type}|${account.name}"

private fun Contact.account(): Account? {
    val name = accountName ?: return null
    val type = accountType ?: return null
    return Account(name, type)
}

/** Why the contact was not saved; the screen stays open so nothing typed is lost. */
@Composable
private fun SaveFailureDialog(failure: ContactSaveResult, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    if (failure == ContactSaveResult.DEVICE_STORAGE_REFUSED) {
        AukConfirmationDialog(
            onDismissRequest = onDismissRequest,
            onConfirm = { openDefaultContactsAccountSettings(context) },
            title = stringResource(R.string.contact_save_failed_title),
            message = stringResource(R.string.contact_save_device_refused_message),
            confirmLabel = stringResource(R.string.contact_save_open_settings),
            dismissLabel = stringResource(R.string.action_close),
            icon = Icons.Default.Warning
        )
    } else {
        AukDialog(
            onDismissRequest = onDismissRequest,
            title = stringResource(R.string.contact_save_failed_title),
            icon = Icons.Default.Warning,
            supportingText = stringResource(R.string.contact_save_failed_message),
            confirmAction = AukDialogAction(stringResource(R.string.action_close), onDismissRequest)
        ) {}
    }
}

/** The system screen choosing the account new contacts go to, or the main settings without one. */
private fun openDefaultContactsAccountSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(ContactsContract.Settings.ACTION_SET_DEFAULT_ACCOUNT)
    } else {
        Intent(Settings.ACTION_SETTINGS)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
