package com.tavuno.tv.ui.screens.customize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tavuno.tv.data.model.CustomizableItem
import com.tavuno.tv.data.repository.CatalogRepository
import com.tavuno.tv.data.repository.CustomizeRepository
import com.tavuno.tv.ui.components.ChannelLogoPlate
import com.tavuno.tv.ui.components.EmptyState
import com.tavuno.tv.ui.components.ErrorState
import com.tavuno.tv.ui.components.LoadingState
import com.tavuno.tv.ui.components.TavunoButton
import com.tavuno.tv.ui.components.TavunoButtonStyle
import com.tavuno.tv.ui.theme.Dimens
import com.tavuno.tv.ui.theme.TavunoTheme
import kotlinx.coroutines.launch

/** The kinds the backend accepts, mirrored here so a typo can never reach the API. */
object CustomizeKind {
    const val LIVE_CHANNEL = "live_channel"
    const val LIVE_CATEGORY = "live_category"
    const val MOVIE_CATEGORY = "movie_category"
    const val SERIES_CATEGORY = "series_category"
}

/**
 * Reorder and hide one slice of the catalog for the current profile (Slice D).
 *
 * The screen is a working copy: edits apply to a local list and only reach the server on
 * **Save**, which PUTs the complete list the viewer just saw. That mirrors the backend's
 * replace-all contract exactly — and it means a half-finished reorder never leaks into the
 * rail. **Reset** drops the kind's overrides entirely and restores the catalog's own order.
 *
 * D-pad shape: every row is three controls — up, down, hide — so UP/DOWN walk one column and
 * LEFT/RIGHT walk within a row. The first row's up (and the last row's down) is disabled,
 * which also keeps them out of the focus ring instead of dead-ending the viewer.
 */
@Composable
fun CustomizeScreen(
    title: String,
    kind: String,
    customizeRepository: CustomizeRepository,
    loadCatalog: suspend () -> Result<List<CustomizableItem>>,
    onNavigateBack: () -> Unit,
) {
    val colors = TavunoTheme.colors
    val scope = rememberCoroutineScope()

    var catalog by remember { mutableStateOf<List<CustomizableItem>>(emptyList()) }
    var items by remember { mutableStateOf<List<CustomizableItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var isDirty by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(kind, reloadKey) {
        isLoading = true
        errorMessage = null
        status = null
        isDirty = false
        loadCatalog().fold(
            onSuccess = { loaded ->
                // The catalog says which items exist; the overrides say how this profile
                // likes them ordered and hidden.
                customizeRepository.getOverrides(kind).fold(
                    onSuccess = { set ->
                        catalog = loaded
                        items = CustomizeItems.seed(loaded, set)
                        isLoading = false
                    },
                    onFailure = { error ->
                        errorMessage = error.message ?: "Failed to load your preferences"
                        isLoading = false
                    },
                )
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load the catalog"
                isLoading = false
            },
        )
    }
    fun save() {
        if (isSaving) return
        scope.launch {
            isSaving = true
            status = null
            customizeRepository.saveOverrides(kind, CustomizeItems.toPayload(items)).fold(
                onSuccess = {
                    isDirty = false
                    status = "Saved. Your rails will follow this order."
                },
                onFailure = { error ->
                    status = error.message ?: "Could not save your changes"
                },
            )
            isSaving = false
        }
    }

    fun reset() {
        if (isSaving) return
        scope.launch {
            isSaving = true
            status = null
            customizeRepository.resetOverrides(kind).fold(
                onSuccess = {
                    // Back to the catalog's own order, nothing hidden.
                    items = catalog.map { it.copy(isHidden = false) }
                    isDirty = false
                    status = "Reset to the default order."
                },
                onFailure = { error ->
                    status = error.message ?: "Could not reset your changes"
                },
            )
            isSaving = false
        }
    }

    // Entry focus. Opening this screen unmounts the Settings row that was focused, so the very
    // first D-pad press finds no focused node at all and Compose's focus system falls back to
    // the first focusable in the tree — the rail's Home item. That is what stranded the remote:
    // DOWN walked the rail instead of the rows. Hand focus to the first usable control on the
    // first row instead, which is the contract TavunoShell already documents ("focus lands on
    // the first content item"). The requester can only resolve once the rows exist *and* have
    // been placed, so both waits live here rather than in a fire-once effect.
    val entryFocus = remember { FocusRequester() }
    LaunchedEffect(isLoading, items.isEmpty()) {
        if (isLoading || items.isEmpty()) return@LaunchedEffect
        repeat(3) {
            withFrameNanos {}
            if (runCatching { entryFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(bottom = Dimens.GapSmall)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
            )
            Text(
                text = "Reorder with the arrows and hide what you never watch. Changes apply to this profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        when {
            isLoading -> LoadingState("Loading...")

            errorMessage != null -> ErrorState(
                message = errorMessage!!,
                onRetry = { reloadKey++ },
            )

            items.isEmpty() -> EmptyState(
                title = "Nothing to customize",
                message = "This list is empty right now.",
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
                        label = if (isDirty) "Save changes" else "Saved",
                        onClick = { save() },
                        enabled = isDirty && !isSaving,
                        style = TavunoButtonStyle.PRIMARY,
                        compact = true,
                    )
                    TavunoButton(
                        label = "Reset",
                        onClick = { reset() },
                        enabled = !isSaving,
                        style = TavunoButtonStyle.SECONDARY,
                        compact = true,
                    )
                    Text(
                        text = "${CustomizeItems.hiddenCount(items)} hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    status?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TavunoButton(
                        label = "Back",
                        onClick = onNavigateBack,
                        style = TavunoButtonStyle.SECONDARY,
                        compact = true,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Dimens.GapTiny),
                    verticalArrangement = Arrangement.spacedBy(Dimens.GapTiny),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { position, item ->
                        CustomizeRow(
                            item = item,
                            position = position,
                            total = items.size,
                            entryFocus = if (position == 0) entryFocus else null,
                            onMoveUp = {
                                items = CustomizeItems.moveUp(items, position)
                                isDirty = true
                            },
                            onMoveDown = {
                                items = CustomizeItems.moveDown(items, position)
                                isDirty = true
                            },
                            onToggleHidden = {
                                items = CustomizeItems.toggleHidden(items, position)
                                isDirty = true
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One item: logo, name, then the three controls that edit it. */
@Composable
private fun CustomizeRow(
    item: CustomizableItem,
    position: Int,
    total: Int,
    // Non-null only on the row the screen hands entry focus to; see CustomizeScreen.
    entryFocus: FocusRequester? = null,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    val colors = TavunoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(Dimens.CornerMedium))
            .background(colors.surfaceContainerLow)
            // A hidden row stays visible but visibly inert: the viewer has to be able to
            // find it again in order to unhide it.
            .alpha(if (item.isHidden) 0.55f else 1f)
            .padding(horizontal = Dimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogoPlate(
            channelName = item.name,
            logoUrl = item.logoUrl,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(Dimens.GapMedium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textDecoration = if (item.isHidden) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.isHidden) {
                    "Hidden — will not appear in your rails"
                } else {
                    "Position ${position + 1} of $total"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.GapSmall)) {
            TavunoButton(
                label = "▲",
                onClick = onMoveUp,
                enabled = position > 0,
                style = TavunoButtonStyle.SECONDARY,
                compact = true,
                modifier = Modifier.width(84.dp),
            )
            TavunoButton(
                label = "▼",
                onClick = onMoveDown,
                enabled = position < total - 1,
                style = TavunoButtonStyle.SECONDARY,
                compact = true,
                // The first row's ▲ is disabled by construction (nowhere to move to), so entry
                // focus lands on ▼ — the first control there that can actually hold focus. A
                // single-item list disables ▼ too, so Hide below takes over as the target.
                modifier = Modifier
                    .width(84.dp)
                    .then(
                        if (entryFocus != null && total > 1) {
                            Modifier.focusRequester(entryFocus)
                        } else {
                            Modifier
                        }
                    ),
            )
            TavunoButton(
                label = if (item.isHidden) "Show" else "Hide",
                onClick = onToggleHidden,
                style = if (item.isHidden) TavunoButtonStyle.PRIMARY else TavunoButtonStyle.SECONDARY,
                compact = true,
                modifier = Modifier
                    .width(150.dp)
                    .then(
                        if (entryFocus != null && total <= 1) {
                            Modifier.focusRequester(entryFocus)
                        } else {
                            Modifier
                        }
                    ),
            )
        }
    }
}

/** Live channels, in the order this profile wants to see them. */
@Composable
fun CustomizeChannelsScreen(
    catalogRepository: CatalogRepository,
    customizeRepository: CustomizeRepository,
    onNavigateBack: () -> Unit,
) {
    CustomizeScreen(
        title = "Customize channels",
        kind = CustomizeKind.LIVE_CHANNEL,
        customizeRepository = customizeRepository,
        loadCatalog = {
            catalogRepository.getChannels().map { channels ->
                channels.map { CustomizableItem(it.id, it.name, it.logo) }
            }
        },
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Category rails (Live / Movies / Series).
 *
 * All three share one screen and one implementation; only the backend [kind] and the catalog
 * read differ, so the wiring lives here instead of being copy-pasted three times.
 */
@Composable
fun CustomizeCategoriesScreen(
    catalogRepository: CatalogRepository,
    customizeRepository: CustomizeRepository,
    section: CategorySection = CategorySection.LIVE,
    onNavigateBack: () -> Unit,
) {
    CustomizeScreen(
        title = "Customize ${section.label.lowercase()} categories",
        kind = section.kind,
        customizeRepository = customizeRepository,
        loadCatalog = {
            catalogRepository.getCategories(kind = section.catalogKind).map { categories ->
                categories.map { CustomizableItem(it.id, it.name) }
            }
        },
        onNavigateBack = onNavigateBack,
    )
}

/** The three category rails, and the customization kind each one writes to. */
enum class CategorySection(
    val label: String,
    val kind: String,
    val catalogKind: String,
) {
    LIVE("Live", CustomizeKind.LIVE_CATEGORY, "live"),
    MOVIES("Movies", CustomizeKind.MOVIE_CATEGORY, "movie"),
    SERIES("Series", CustomizeKind.SERIES_CATEGORY, "series"),
}