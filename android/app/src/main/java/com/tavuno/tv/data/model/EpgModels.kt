package com.tavuno.tv.data.model

import com.google.gson.annotations.SerializedName

data class EpgProgram(
    val id: Int,
    val title: String,
    @SerializedName("starts_at")
    val startsAt: String,
    @SerializedName("ends_at")
    val endsAt: String,
    val description: String?,
    @SerializedName("channel_id")
    val channelId: Int
)

data class ChannelNowNext(
    @SerializedName("channel_id")
    val channelId: Int,
    val now: EpgProgram?,
    val next: EpgProgram?,
    val later: EpgProgram?
)

/**
 * One channel row inside a guide window (Slice C).
 *
 * Mirrors the backend `GuideChannel`: the guide lists a channel even when its EPG has
 * not synced yet, so [programmes] is empty rather than the row disappearing — the grid
 * renders that as "no guide data" instead of losing the channel entirely.
 */
data class GuideChannel(
    val id: Int,
    val name: String,
    val slug: String = "",
    @SerializedName("category_id")
    val categoryId: Int? = null,
    val logo: String? = null,
    val programmes: List<EpgProgram> = emptyList()
)

/** The whole guide payload for one request: the window plus its channel rows. */
/** The whole guide payload for one request: the window plus its channel rows. */
data class EpgWindow(
    @SerializedName("window_start")
    val windowStart: String,
    @SerializedName("window_end")
    val windowEnd: String,
    val channels: List<GuideChannel> = emptyList()
)

/**
 * Customization and profile models (Slice D).
 *
 * Mirrors the backend contracts in `app/customize/models.py` and `app/profiles/models.py`.
 * `CustomizationItem.itemId` is the *catalog* id (a channel id or a category id) — the
 * customization table is keyed on `(profile, kind, item_id)`, so one id space per kind.
 */
data class CustomizationItem(
    @SerializedName("item_id")
    val itemId: Int,
    @SerializedName("sort_order")
    val sortOrder: Int = 0,
    @SerializedName("is_hidden")
    val isHidden: Boolean = false
)

/** The complete override set for one kind. A PUT replaces; it never merges. */
data class CustomizationPayload(
    val items: List<CustomizationItem> = emptyList()
)

/** The stored overrides for one kind. */
data class CustomizationSet(
    val kind: String,
    val items: List<CustomizationItem> = emptyList()
)

/** `DELETE /v1/customize/{kind}` — [removed] counts the rows it dropped. */
data class CustomizationReset(
    val kind: String,
    val removed: Int = 0
)

/**
 * A viewing profile as the client sees it.
 *
 * [isOwner] marks the account row itself (credentials, subscription); the rest are child
 * profiles that only exist to split one subscription across viewers. The account row is
 * editable but never deletable.
 */
data class ViewingProfile(
    val id: Int,
    @SerializedName("display_name")
    val displayName: String,
    val email: String? = null,
    val role: String = "user",
    val status: String? = null,
    val avatar: String? = null,
    @SerializedName("is_kids")
    val isKids: Boolean = false,
    @SerializedName("is_owner")
    val isOwner: Boolean = false
)

data class CreateProfileRequest(
    @SerializedName("display_name")
    val displayName: String,
    val avatar: String? = null,
    @SerializedName("is_kids")
    val isKids: Boolean = false
)

/** Unset fields are left alone by the backend; `avatar = ""` clears the avatar. */
data class UpdateProfileRequest(
    @SerializedName("display_name")
    val displayName: String? = null,
    val avatar: String? = null,
    @SerializedName("is_kids")
    val isKids: Boolean? = null
)

data class DeleteProfileResult(
    val deleted: Boolean = false,
    @SerializedName("profile_id")
    val profileId: Int = 0
)

/**
 * A catalog entry being reordered/hidden on a customize screen.
 *
 * Deliberately not a [Channel] or a [Category]: the screen renders both, and it must also
 * carry the *editable* state (is this hidden right now?) that the catalog models have no
 * field for.
 */
data class CustomizableItem(
    val id: Int,
    val name: String,
    val logoUrl: String? = null,
    val isHidden: Boolean = false
)
