package com.dockstandby.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Permission(s) that unlock the photo slideshow on this Android version. */
val photoPermissions: Array<String>
    get() = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** Full or "selected photos only" access both count. */
fun Context.hasPhotoAccess() = photoPermissions.any { hasPermission(it) }

/** Why a photo is in front of the queue, shown as a caption. */
sealed interface Memory {
    data class OnThisDay(val year: Int) : Memory
    data object Favourite : Memory
}

private data class PhotoRef(val uri: Uri, val taken: LocalDate?, val favourite: Boolean)

/**
 * Up to 300 recent photos. With [memoriesFirst]: photos taken on today's date in earlier years,
 * then favourites (Android 11+), then the rest shuffled; otherwise everything shuffled.
 */
private fun queryPhotos(context: Context, memoriesFirst: Boolean): List<Pair<Uri, Memory?>> {
    if (!context.hasPhotoAccess()) return emptyList()
    val refs = mutableListOf<PhotoRef>()
    val proj = buildList {
        add(MediaStore.Images.Media._ID)
        add(MediaStore.Images.Media.DATE_TAKEN)
        if (Build.VERSION.SDK_INT >= 30) add(MediaStore.MediaColumns.IS_FAVORITE)
    }.toTypedArray()
    runCatching {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val zone = ZoneId.systemDefault()
            while (c.moveToNext() && refs.size < 300) {
                val taken = if (c.isNull(1)) null else Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDate()
                refs += PhotoRef(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                    taken,
                    favourite = Build.VERSION.SDK_INT >= 30 && c.getInt(2) == 1,
                )
            }
        }
    }
    if (!memoriesFirst) return refs.shuffled().map { it.uri to null }
    val today = LocalDate.now()
    val onThisDay = refs.filter { it.taken != null && it.taken.year < today.year && it.taken.monthValue == today.monthValue && it.taken.dayOfMonth == today.dayOfMonth }
    val favourites = refs.filter { it.favourite && it !in onThisDay }
    val rest = refs.filter { it !in onThisDay && it !in favourites }
    return onThisDay.shuffled().map { it.uri to Memory.OnThisDay(it.taken!!.year) } +
        favourites.shuffled().map { it.uri to Memory.Favourite } +
        rest.shuffled().map { it.uri to null }
}

/** Decodes [uri] scaled down to roughly [maxSide] px on its longest edge, honouring EXIF rotation. */
private fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val s = maxOf(info.size.width, info.size.height)
            if (s > maxSide) d.setTargetSampleSize(s / maxSide)
            // Palette needs to read pixels, which hardware bitmaps don't allow.
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / maxSide).coerceAtLeast(1)
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}.getOrNull()

/** One slide: the picture, why it was picked, and two clock colours taken from it. */
data class Slide(val bitmap: Bitmap, val memory: Memory?, val colors: PhotoColors)

/**
 * Colours from the current photo. [a]/[b] are readable over the photo behind the clock and
 * [lightBackground] says whether that spot is light (so other text should be dark). [darkA]/[darkB]
 * are the same hues made readable on a black page, for the Colorful clock.
 */
data class PhotoColors(val a: Int, val b: Int, val lightBackground: Boolean, val darkA: Int, val darkB: Int)

object PhotoPalette {
    private val _current = MutableStateFlow<PhotoColors?>(null)
    val current: StateFlow<PhotoColors?> = _current.asStateFlow()
    internal fun set(c: PhotoColors) { _current.value = c }
}

/** Slideshow: the current slide, changing every [intervalMs]. Null when there are no photos. */
@Composable
fun rememberSlideshow(intervalMs: Long, memoriesFirst: Boolean): State<Slide?> {
    val context = LocalContext.current
    return produceState<Slide?>(null, intervalMs, memoriesFirst) {
        while (true) {
            val photos = withContext(Dispatchers.IO) { queryPhotos(context, memoriesFirst) }
            if (photos.isEmpty()) {
                value = null
                delay(30_000) // permission may be granted later
                continue
            }
            for ((uri, memory) in photos) {
                val slide = withContext(Dispatchers.IO) {
                    decode(context, uri, 1600)?.let { bmp -> Slide(bmp, memory, photoColors(bmp)) }
                } ?: continue
                value = slide
                PhotoPalette.set(slide.colors)
                delay(intervalMs)
            }
        }
    }
}

/**
 * Two vivid colours from the photo for the clock gradient, adjusted to at least 4.5:1 contrast
 * against what is actually behind the clock: the photo's bottom-left area under the scrim.
 */
private fun photoColors(bmp: Bitmap): PhotoColors {
    val p = Palette.from(bmp).maximumColorCount(16).generate()
    val swatches = listOfNotNull(p.vibrantSwatch, p.lightVibrantSwatch, p.mutedSwatch, p.lightMutedSwatch, p.dominantSwatch)
    val first = swatches.firstOrNull()?.rgb ?: Color.WHITE
    val second = swatches.drop(1).firstOrNull { it.rgb != first }?.rgb ?: first
    val bg = clockBackground(bmp)
    val black = Color.rgb(20, 20, 20)
    return PhotoColors(
        readable(first, bg), readable(second, bg), inkFor(bg) == Color.BLACK,
        readable(first, black), readable(second, black),
    )
}

/**
 * Average colour of the bottom-left region where the clock sits (left 55%, bottom 45%), with the
 * page's scrim (black, ~30% at clock height) applied on top.
 */
private fun clockBackground(bmp: Bitmap): Int {
    // Just the area behind the digits: left 40%, from 55% to 85% of the height (the photo is center-cropped
    // to landscape, so this is approximate for portrait photos).
    val region = Bitmap.createBitmap(bmp, 0, (bmp.height * .55f).toInt(), (bmp.width * .4f).toInt().coerceAtLeast(1), (bmp.height * .3f).toInt().coerceAtLeast(1))
    val avg = Bitmap.createScaledBitmap(region, 1, 1, true).getPixel(0, 0)
    return ColorUtils.blendARGB(avg, Color.BLACK, .3f)
}

/**
 * Moves [c] toward black (on light backgrounds) or white (on dark ones) until its contrast
 * against [bg] is at least 4.5:1, keeping as much of the photo's hue as possible.
 */
private fun readable(c: Int, bg: Int): Int {
    val target = inkFor(bg)
    var color = c
    var t = 0f
    while (ColorUtils.calculateContrast(color, bg) < 4.5 && t < 1f) {
        t += .05f
        color = ColorUtils.blendARGB(c, target, t)
    }
    return color
}

/** Black or white, whichever contrasts more with [bg]. */
private fun inkFor(bg: Int): Int =
    if (ColorUtils.calculateContrast(Color.WHITE, bg) >= ColorUtils.calculateContrast(Color.BLACK, bg)) Color.WHITE else Color.BLACK
