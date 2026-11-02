package auk.dialer.vroot.view.screen

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.ContactsViewModel
import auk.dialer.vroot.controller.util.ContactTypeLabels
import auk.dialer.vroot.controller.util.ContactUtils
import auk.dialer.vroot.controller.util.deduplicateNumbers
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
    val addresses = remember { mutableStateListOf<String>("") }

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
                    selectedAccount = availableAccounts.find {
                        it.name == existing.accountName && it.type == existing.accountType
                    }
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
                if (existing.addresses.isNotEmpty()) {
                    addresses.addAll(existing.addresses)
                } else {
                    addresses.add("")
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
            // Title on its own row under the actions: beside a delete and a labelled save button
            // it has no room left and wraps.
            MediumTopAppBar(
                title = {
                    Text(
                        if (contactId == null || contactId == "0") stringResource(R.string.contact_create_title) else stringResource(R.string.contact_edit_title),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val savedPhones = phones.filter { it.number.isNotBlank() }
                                    val savedEmails = emails.filter { it.address.isNotBlank() }
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
                                        addresses = addresses.filter { it.isNotBlank() },
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
                            modifier = Modifier.padding(end = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_save))
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
                .fillMaxSize(),
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
                            textStyle = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold)
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
                AukExpressiveCard {
                    var showPicker by remember { mutableStateOf(false) }

                    Surface(
                        onClick = { showPicker = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isPrivate -> Icons.Default.Lock
                                    selectedAccount != null -> ContactUtils.getAccountIcon(selectedAccount!!)
                                    else -> Icons.Default.CloudOff
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.contact_edit_save_to_account),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = when {
                                        isPrivate -> stringResource(R.string.contact_edit_private_storage)
                                        selectedAccount != null -> ContactUtils.getFriendlyAccountName(context, selectedAccount!!)
                                        else -> stringResource(R.string.label_local_memory)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }

                    if (showPicker) {
                        val privateTitle = stringResource(R.string.contact_edit_private_storage)
                        val privateDesc = stringResource(R.string.contact_edit_private_storage_description)
                        val localTitle = stringResource(R.string.label_local_memory)
                        val localDesc = if (cloudDefaultAccount != null) {
                            stringResource(R.string.contact_edit_local_memory_unavailable)
                        } else {
                            ""
                        }

                        val accountOptions = remember(availableAccounts, privateTitle, localTitle, localDesc) {
                            listOf(
                                "private" to Triple(privateTitle, privateDesc, Icons.Default.Lock),
                                "local" to Triple(localTitle, localDesc, Icons.Default.CloudOff)
                            ) + availableAccounts.map { acc ->
                                acc.name to Triple(ContactUtils.getFriendlyAccountName(context, acc), acc.name, ContactUtils.getAccountIcon(acc))
                            }
                        }

                        AukSelectionDialog(
                            onDismissRequest = { showPicker = false },
                            title = stringResource(R.string.contact_edit_select_account_title),
                            icon = Icons.Default.AccountBalance,
                            items = accountOptions,
                            itemLabel = { option -> option.second.first },
                            itemSupporting = { option -> option.second.second },
                            itemIcon = { option -> option.second.third },
                            isEnabled = { option -> option.first != "local" || cloudDefaultAccount == null },
                            isSelected = { option ->
                                when (option.first) {
                                    "private" -> isPrivate
                                    "local" -> !isPrivate && selectedAccount == null
                                    else -> !isPrivate && selectedAccount?.name == option.first
                                }
                            },
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
                                        selectedAccount = availableAccounts.find { it.name == selectedOption.first }
                                        isPrivate = false
                                    }
                                }
                                showPicker = false
                            }
                        )
                    }
                }
            }

            item {
                AukSectionHeader(title = stringResource(R.string.contact_edit_identity_header))
                AukExpressiveCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = givenName,
                            onValueChange = { givenName = it },
                            label = { Text(stringResource(R.string.contact_edit_first_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        OutlinedTextField(
                            value = familyName,
                            onValueChange = { familyName = it },
                            label = { Text(stringResource(R.string.contact_edit_last_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.Default.Badge, null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text(stringResource(R.string.contact_edit_nickname)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
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
                                icon = Icons.Default.Phone,
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
                                icon = Icons.Default.Email,
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
                            EditField(
                                value = address,
                                onValueChange = { addresses[index] = it },
                                label = addressFieldLabel,
                                icon = Icons.Default.LocationOn,
                                onDelete = {
                                    if (addresses.size > 1) {
                                        addresses.removeAt(index)
                                    } else {
                                        addresses[0] = ""
                                    }
                                }
                            )
                        }
                        TextButton(
                            onClick = { addresses.add("") },
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
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.label_notes)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
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

@Composable
fun TypedEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    typeValue: Int,
    typeOptions: List<Int>,
    typeLabel: (Int) -> String,
    onTypeChange: (Int) -> Unit,
    onDelete: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        EditField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            icon = icon,
            keyboardType = keyboardType,
            onDelete = onDelete
        )
        var showTypeMenu by remember { mutableStateOf(false) }
        Box(modifier = Modifier.padding(start = 8.dp)) {
            TextButton(onClick = { showTypeMenu = true }) {
                Icon(Icons.Default.Sell, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(typeLabel(typeValue), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
            }
            AukDropdownMenu(
                expanded = showTypeMenu,
                onDismissRequest = { showTypeMenu = false }
            ) {
                typeOptions.forEach { option ->
                    AukDropdownMenuItem(
                        text = { Text(typeLabel(option)) },
                        onClick = {
                            onTypeChange(option)
                            showTypeMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    onDelete: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
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
