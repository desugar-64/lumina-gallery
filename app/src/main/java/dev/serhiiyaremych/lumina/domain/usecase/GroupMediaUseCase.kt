package dev.serhiiyaremych.lumina.domain.usecase

import dev.serhiiyaremych.lumina.domain.model.Media
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class GroupingPeriod { DAILY, WEEKLY, MONTHLY }

class GroupMediaUseCase @Inject constructor() {
    operator fun invoke(
        mediaList: List<Media>,
        period: GroupingPeriod = GroupingPeriod.DAILY
    ): Map<LocalDate, List<Media>> = when (period) {
        GroupingPeriod.DAILY -> mediaList.groupBy {
            Instant.ofEpochMilli(it.dateAdded).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        GroupingPeriod.WEEKLY -> mediaList.groupBy {
            val date = Instant.ofEpochMilli(it.dateAdded).atZone(ZoneId.systemDefault()).toLocalDate()
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }

        GroupingPeriod.MONTHLY -> mediaList.groupBy {
            val date = Instant.ofEpochMilli(it.dateAdded).atZone(ZoneId.systemDefault()).toLocalDate()
            date.withDayOfMonth(1)
        }
    }
}
