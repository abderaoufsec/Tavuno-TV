package com.tavuno.tv.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tavuno.tv.data.model.ViewingProfile
import com.tavuno.tv.data.repository.ProfileRepository
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.LocalFocusBorderWidth
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.launch

/**
 * "Who's watching?" — manage the viewers that share this account (Slice D).
 *
 * One account row plus any number of child profiles. The account row can be renamed and
 * flagged but never removed (that would orphan the subscription), which the screen enforces by
 * not rendering a Remove control for it rather than letting the API answer 404.
 *
 * Adding a profile needs a name, so the screen carries one text field. It is a real
 * [BasicTextField] so the system IME drives input, exactly as the search box does — inventing a
 * D-pad character picker for one field would be the wrong trade.
 */
@Composable
fun ProfilesScreen(
    profileRepository: ProfileRepository,
    onNavigateBack: () -> Unit,
) {
    val colors = TavunoTheme.colors
    val scope = rememberCoroutineScope()

    var profiles by remember { mutableStateOf<List<ViewingProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    var isAdding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newIsKids by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var editName by remember { mutableStateOf("") }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = null
        profileRepository.getProfiles().fold(
            onSuccess = {
                profiles = it
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load profiles"
                isLoading = false
            },
        )
    }

    fun create() {
        val name = newName.trim()
        if (name.isEmpty()) {
            status = "Give the profile a name first"
            return
        }
        scope.launch {
            profileRepository.createProfile(name, isKids = newIsKids).fold(
                onSuccess = {
                    status = "Added $name"
                    newName = ""
                    newIsKids = false
                    isAdding = false
                    reloadKey++
                },
                onFailure = { error -> status = error.message ?: "Could not add that profile" },
            )
        }
    }

    fun rename(profile: ViewingProfile) {
        val name = editName.trim()
        if (name.isEmpty() || name == profile.displayName) {
            editingId = null
            return
        }
        scope.launch {
            profileRepository.updateProfile(profile.id, displayName = name).fold(
                onSuccess = {
                    status = "Renamed to $name"
                    editingId = null
                    reloadKey++
                },
                onFailure = { error -> status = error.message ?: "Could not rename that profile" },
            )
        }
    }

    fun remove(profile: ViewingProfile) {
        if (profile.isOwner) {
            status = "The account profile cannot be removed"
            return
        }
        scope.launch {
            profileRepository.deleteProfile(profile.id).fold(
                onSuccess = {
                    status = "Removed ${profile.displayName}"
                    reloadKey++
                },
                onFailure = { error -> status = error.message ?: "Could not remove that profile" },
            )
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(bottom = Dimens.GapSmall)) {
            Text(
                text = "Who's watching?",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )
            Text(
                text = "Add a profile for everyone who watches here, then reorder and hide what they never watch.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        when {
            isLoading -> LoadingState("Loading profiles...")

            errorMessage != null -> ErrorState(
                message = errorMessage!!,
                onRetry = { reloadKey++ },
            )

            else -> Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Dimens.GapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
                ) {
                    TavunoButton(
                        label = if (isAdding) "Cancel" else "Add profile",
                        onClick = {
                            isAdding = !isAdding
                            if (!isAdding) newName = ""
                        },
                        style = if (isAdding) TavunoButtonStyle.SECONDARY else TavunoButtonStyle.PRIMARY,
                        compact = true,
                    )
                    status?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TavunoButton(
                        label = "Back",
                        onClick = onNavigateBack,
                        style = TavunoButtonStyle.SECONDARY,
                        compact = true,
                    )
                }

                if (isAdding) {
                    AddProfilePanel(
                        name = newName,
                        onNameChange = { newName = it },
                        isKids = newIsKids,
                        onToggleKids = { newIsKids = !newIsKids },
                        onSubmit = { create() },
                    )
                }

                if (profiles.isEmpty()) {
                    EmptyState(
                        title = "No profiles",
                        message = "Add a profile to start splitting this account between viewers.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = Dimens.GapTiny),
                        verticalArrangement = Arrangement.spacedBy(Dimens.GapTiny),
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileRow(
                                profile = profile,
                                isEditing = editingId == profile.id,
                                editName = editName,
                                onEditNameChange = { editName = it },
                                onStartEdit = {
                                    editingId = profile.id
                                    editName = profile.displayName
                                },
                                onCommitEdit = { rename(profile) },
                                onRemove = { remove(profile) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The add form: name, kids flag, Add. */
@Composable
private fun AddProfilePanel(
    name: String,
    onNameChange: (String) -> Unit,
    isKids: Boolean,
    onToggleKids: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = TavunoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.GapSmall)
            .clip(RoundedCornerShape(Dimens.CornerMedium))
            .background(colors.surfaceContainerLow)
            .padding(Dimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall),
    ) {
        ProfileNameField(
            value = name,
            onValueChange = onNameChange,
            onSubmit = onSubmit,
            modifier = Modifier.weight(1f),
        )
        TavunoButton(
            label = if (isKids) "Kids: on" else "Kids: off",
            onClick = onToggleKids,
            selected = isKids,
            style = TavunoButtonStyle.SECONDARY,
            compact = true,
            modifier = Modifier.width(200.dp),
        )
        TavunoButton(
            label = "Add",
            onClick = onSubmit,
            style = TavunoButtonStyle.PRIMARY,
            compact = true,
            modifier = Modifier.width(160.dp),
        )
    }
}

/** A single profile: avatar, name, badges, and the actions its row allows. */
@Composable
private fun ProfileRow(
    profile: ViewingProfile,
    isEditing: Boolean,
    editName: String,
    onEditNameChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = TavunoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(Dimens.CornerMedium))
            .background(colors.surfaceContainerLow)
            .padding(horizontal = Dimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(profile, Modifier.size(56.dp))
        Spacer(Modifier.width(Dimens.GapMedium))
        Column(modifier = Modifier.weight(1f)) {
            if (isEditing) {
                ProfileNameField(
                    value = editName,
                    onValueChange = onEditNameChange,
                    onSubmit = onCommitEdit,
                )
            } else {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profileSubtitle(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        if (!isEditing) {
            TavunoButton(
                label = "Rename",
                onClick = onStartEdit,
                style = TavunoButtonStyle.SECONDARY,
                compact = true,
                modifier = Modifier.width(170.dp),
            )
            Spacer(Modifier.width(Dimens.GapSmall))
            TavunoButton(
                label = "Remove",
                onClick = onRemove,
                // The account row is never removable; hiding the control is clearer than
                // a button that always answers "that profile cannot be removed".
                enabled = !profile.isOwner,
                style = TavunoButtonStyle.SECONDARY,
                compact = true,
                modifier = Modifier.width(170.dp),
            )
        }
    }
}

/** "Account profile", "Kids", or a plain no-preferences-yet line. */
private fun profileSubtitle(profile: ViewingProfile): String {
    val badges = buildList {
        if (profile.isOwner) add("Account profile")
        if (profile.isKids) add("Kids")
    }
    return if (badges.isEmpty()) "No preferences yet" else badges.joinToString("  •  ")
}

/** Avatar artwork, or the profile's initials on a tonal plate. */
@Composable
private fun ProfileAvatar(profile: ViewingProfile, modifier: Modifier = Modifier) {
    val colors = TavunoTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (profile.avatar.isNullOrBlank()) {
            Text(
                text = initialsOf(profile.displayName),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textSecondary,
                maxLines = 1,
            )
        } else {
            AsyncImage(
                model = profile.avatar,
                contentDescription = profile.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** First letters of up to two words, for the no-avatar placeholder. */
private fun initialsOf(name: String): String =
    name.trim()
        .split(' ')
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

/**
 * A name field driven by the system IME, dressed as a rounded panel like the search box.
 *
 * The focus ring is drawn by the panel, not by a [FocusableSurface], because a text field has
 * to keep the real focus node for the IME; wrapping it in a focusable surface would steal
 * every keystroke.
 */
@Composable
private fun ProfileNameField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TavunoTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Dimens.CornerSmall)
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .then(
                if (focused) {
                    Modifier.border(LocalFocusBorderWidth.current, colors.focusBorder, shape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Dimens.GapMedium),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = "Profile name",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}