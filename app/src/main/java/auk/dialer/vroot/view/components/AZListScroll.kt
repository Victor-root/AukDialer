package auk.dialer.vroot.view.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import auk.dialer.vroot.R
import auk.dialer.vroot.modal.data.Contact
import auk.dialer.vroot.controller.util.ContactUtils
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.controller.util.formatPhoneNumber
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import auk.dialer.vroot.controller.util.indexLetter

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AZListScroll(
    contacts: List<Contact>,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    grouped: Map<Char, List<Contact>>? = null
) {
    val prefs = org.koin.compose.koinInject<auk.dialer.vroot.controller.util.PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()

    val displayOrder = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0) }

    ListScrollHaptics(listState, prefs)

    val finalGrouped = remember(contacts, grouped) {
        if (grouped != null) return@remember grouped
        
        val mainGroups = contacts.groupBy { indexLetter(it.name) }.toMutableMap()

        val finalMap = linkedMapOf<Char, List<Contact>>()

        mainGroups.keys.filter { it.isLetter() }.sorted().forEach { char ->
            finalMap[char] = mainGroups[char]!!
        }

        val hashGroup = mainGroups['#']
        if (hashGroup != null) finalMap['#'] = hashGroup

        finalMap
    }

    val alphabetIndices = remember(finalGrouped) {
        val map = mutableMapOf<Char, Int>()
        var currentIndex = 0
        finalGrouped.forEach { (char, contactsForChar) ->
            map[char] = currentIndex
            currentIndex += 1 + contactsForChar.size
        }
        map
    }

    val scope = rememberCoroutineScope()
    var draggingChar by remember { mutableStateOf<Char?>(null) }

    val scrollingChar by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            alphabetIndices.entries
                .filter { it.value <= firstVisible }
                .maxByOrNull { it.value }
                ?.key ?: alphabetIndices.keys.firstOrNull()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val unknownLabel = stringResource(R.string.label_unknown)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
        ) {
            finalGrouped.entries.forEach { (initial, contactsForChar) ->
                stickyHeader(key = "header:$initial", contentType = "header") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                itemsIndexed(
                    contactsForChar,
                    key = { _, contact -> contact.id },
                    contentType = { _, _ -> "contact" }
                ) { index, contact ->
                    val isLast = index == contactsForChar.lastIndex
                    val displayName = ContactUtils.formatContactName(
                        contact.name.ifEmpty {
                            contact.phoneNumbers.firstOrNull()?.let { formatPhoneNumber(it) } ?: unknownLabel
                        },
                        displayOrder
                    )
                    // Right side leaves room for the alphabet index bar so its letters don't sit on
                    // top of the card's own border.
                    AukExpressiveCardSegment(
                        isFirst = index == 0,
                        isLast = isLast,
                        isCompact = true,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 36.dp)
                            .padding(bottom = if (isLast) 8.dp else 0.dp)
                    ) {
                        AukListItem(
                            headline = displayName,
                            supporting = null,
                            headlineMaxLines = 2,
                            avatarName = contact.name,
                            photoUri = contact.photoUri,
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    onToggleSelection(contact.id)
                                } else {
                                    navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                                }
                            },
                            onLongClick = {
                                onToggleSelection(contact.id)
                            },
                            selected = selectedIds.contains(contact.id),
                            isCompact = true
                        )
                    }
                }
            }
        }

        AlphabetSideBar(
            alphabet = alphabetIndices.keys.toList(),
            selectedChar = draggingChar ?: scrollingChar,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            onLetterSelected = { char ->
                // A drag reports every move: only a new letter scrolls.
                if (char == draggingChar) return@AlphabetSideBar
                draggingChar = char
                val index = alphabetIndices[char] ?: return@AlphabetSideBar
                scope.launch { listState.scrollToItem(index) }
            },
            onDragEnd = { draggingChar = null }
        )

        if (draggingChar != null) {
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = draggingChar.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun AlphabetSideBar(
    alphabet: List<Char>,
    selectedChar: Char?,
    modifier: Modifier = Modifier,
    onLetterSelected: (Char) -> Unit,
    onDragEnd: () -> Unit
) {
    var columnHeight by remember { mutableStateOf(0) }
    val verticalPadding = 8.dp

    Surface(
        modifier = modifier
            .width(24.dp)
            .wrapContentHeight()
            .onGloballyPositioned { columnHeight = it.size.height }
            .pointerInput(alphabet) {
                // The letter under y, measured on the letters alone, without the bar's own padding.
                fun letterAt(y: Float): Char? {
                    val padding = verticalPadding.toPx()
                    val lettersHeight = columnHeight - 2 * padding
                    if (lettersHeight <= 0f || alphabet.isEmpty()) return null
                    val index = ((y - padding) / (lettersHeight / alphabet.size)).toInt()
                    return alphabet[index.coerceIn(0, alphabet.lastIndex)]
                }
                // Started at once: the first touch is what starts this block, and detectors launched
                // the usual way only began listening after it had gone by, losing the first tap.
                coroutineScope {
                    // A plain tap jumps too: a drag only starts once the finger has moved.
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        detectTapGestures { offset ->
                            letterAt(offset.y)?.let(onLetterSelected)
                            onDragEnd()
                        }
                    }
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        detectVerticalDragGestures(
                            onDragStart = { offset -> letterAt(offset.y)?.let(onLetterSelected) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        ) { change, _ ->
                            letterAt(change.position.y)?.let(onLetterSelected)
                        }
                    }
                }
            },
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = verticalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { char ->
                val isSelected = char == selectedChar

                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
