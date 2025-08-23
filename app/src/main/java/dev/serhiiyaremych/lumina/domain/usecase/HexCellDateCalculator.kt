package dev.serhiiyaremych.lumina.domain.usecase

import dev.serhiiyaremych.lumina.domain.model.HexCellWithMedia
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Utility for calculating and formatting representative dates for hex cells.
 * Extracts the appropriate date representation based on the grouping period
 * and provides formatted strings suitable for canvas text rendering.
 */
class HexCellDateCalculator @Inject constructor() {

    companion object {
        private val DAILY_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy")
        private val WEEKLY_FORMATTER = DateTimeFormatter.ofPattern("MMM d")
        private val MONTHLY_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy")
        private val YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy")
    }

    /**
     * Calculates the representative date for a hex cell based on its media items.
     * Uses the first media item's date as the representative date since items
     * in the same cell are already grouped by the same time period.
     */
    fun getRepresentativeDate(hexCellWithMedia: HexCellWithMedia): LocalDate? {
        val firstMedia = hexCellWithMedia.mediaItems.firstOrNull()?.media
        return firstMedia?.let { media ->
            Instant.ofEpochMilli(media.dateModified)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }

    /**
     * Formats the date according to the grouping period for display.
     * Returns a formatted string suitable for canvas text rendering.
     */
    fun formatDateForDisplay(
        hexCellWithMedia: HexCellWithMedia,
        groupingPeriod: GroupingPeriod
    ): String? {
        val date = getRepresentativeDate(hexCellWithMedia) ?: return null

        return when (groupingPeriod) {
            GroupingPeriod.DAILY -> date.format(DAILY_FORMATTER)

            GroupingPeriod.WEEKLY -> {
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = weekStart.plusDays(6)

                if (weekStart.month == weekEnd.month) {
                    // Same month: "Mar 11-17"
                    "${weekStart.format(WEEKLY_FORMATTER)}-${weekEnd.dayOfMonth}"
                } else {
                    // Different months: "Mar 30 - Apr 5"
                    "${weekStart.format(WEEKLY_FORMATTER)} - ${weekEnd.format(WEEKLY_FORMATTER)}"
                }
            }

            GroupingPeriod.MONTHLY -> date.format(MONTHLY_FORMATTER)
        }
    }

    /**
     * Provides a shorter date format for lower zoom levels.
     * Returns simplified representations to avoid clutter when zoomed out.
     */
    fun formatDateForZoomLevel(
        hexCellWithMedia: HexCellWithMedia,
        groupingPeriod: GroupingPeriod,
        zoomLevel: Float
    ): String? {
        val date = getRepresentativeDate(hexCellWithMedia) ?: return null

        return when {
            zoomLevel >= 2.0f -> formatDateForDisplay(hexCellWithMedia, groupingPeriod)
            zoomLevel >= 1.0f -> when (groupingPeriod) {
                GroupingPeriod.DAILY -> date.format(DateTimeFormatter.ofPattern("MMM d"))
                GroupingPeriod.WEEKLY -> date.format(DateTimeFormatter.ofPattern("MMM"))
                GroupingPeriod.MONTHLY -> date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            }
            zoomLevel >= 0.5f -> date.format(YEAR_FORMATTER)
            zoomLevel >= 0.3f -> when (groupingPeriod) {
                GroupingPeriod.DAILY -> date.format(DateTimeFormatter.ofPattern("MM/yy"))
                GroupingPeriod.WEEKLY -> date.format(DateTimeFormatter.ofPattern("MM/yy"))
                GroupingPeriod.MONTHLY -> date.format(DateTimeFormatter.ofPattern("MM/yy"))
            }
            zoomLevel >= 0.2f -> date.format(DateTimeFormatter.ofPattern("yy"))
            else -> null // Hide dates when too zoomed out
        }
    }

    /**
     * Determines if the date should be visible at the current zoom level.
     * Helps with performance by avoiding text rendering when not useful.
     */
    fun shouldShowDateAtZoom(zoomLevel: Float): Boolean = zoomLevel >= 0.2f
}
