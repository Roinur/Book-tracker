package com.roinur.booktracker

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/** Read-only bridge: each stored session is one read; metadata stays in the book database. */
internal class BookTrendData(private val books: List<BookRow>, private val sessions: List<BookReadingSessionWithBook>) {
    private fun names(book: BookRow, kind: TrendTargetKind) =
        (if (kind == TrendTargetKind.TAGS) book.collections else book.authors)
            .split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase(Locale.ROOT) }
    private fun id(name: String, kind: TrendTargetKind) = UUID.nameUUIDFromBytes(
        "${kind.name}:${name.lowercase(Locale.ROOT)}".toByteArray(Charsets.UTF_8)
    ).mostSignificantBits.and(Long.MAX_VALUE)
    fun targets(kind: TrendTargetKind): List<TrendTarget> = books.flatMap { book -> names(book, kind).map { it to book.id } }
        .groupBy { it.first.lowercase(Locale.ROOT) }.values.map { rows ->
            val name = rows.first().first
            TrendTarget(id(name, kind), name, if (kind == TrendTargetKind.TAGS) "Collection" else "Author", rows.map { it.second }.distinct().size)
        }.sortedWith(compareByDescending<TrendTarget> { it.entryCount }.thenBy { it.name })
    private fun date(raw: String): LocalDateTime? = runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()
        ?: runCatching { LocalDate.parse(raw).atStartOfDay() }.getOrNull()
    fun snapshot(request: TrendRequest): TrendSnapshot {
        val byId = books.associateBy { it.id }
        val events = sessions.mapNotNull { row ->
            val book = byId[row.session.bookId] ?: return@mapNotNull null
            date(row.session.activityAt)?.let { Triple(it, book, row.session) }
        }
        fun amount(event: Triple<LocalDateTime, BookRow, BookReadingSessionRow>): Float = when(request.metric) {
            TrendMetric.PAGES -> event.third.pagesRead.coerceAtLeast(0).toFloat()
            TrendMetric.TIME -> (event.third.durationMilliseconds ?: (event.third.durationSeconds * 1000L)).coerceAtLeast(0L) / 60000f
            TrendMetric.SESSIONS -> 1f
        }
        fun total(events: List<Triple<LocalDateTime, BookRow, BookReadingSessionRow>>) = events.sumOf { amount(it).toDouble() }.toFloat()
        val today = LocalDate.now()
        val earliest = events.minOfOrNull { it.first.toLocalDate() }
        val months = ChronoUnit.MONTHS.between((earliest ?: today).withDayOfMonth(1), today.withDayOfMonth(1)) + 1
        val granularity = if (request.bucketMode == TrendBucketMode.LEGACY) {
            if (request.range == StatsRange.YEAR || request.range == StatsRange.ALL_TIME) TrendBucketGranularity.MONTH else TrendBucketGranularity.DAY
        } else when(request.range) {
            StatsRange.TODAY -> TrendBucketGranularity.FOUR_HOURS
            StatsRange.WEEK -> TrendBucketGranularity.DAY
            StatsRange.MONTH -> TrendBucketGranularity.WEEK
            StatsRange.YEAR -> TrendBucketGranularity.MONTH
            StatsRange.ALL_TIME -> when { months <= 18 -> TrendBucketGranularity.MONTH; months <= 48 -> TrendBucketGranularity.QUARTER; months <= 96 -> TrendBucketGranularity.HALF_YEAR; else -> TrendBucketGranularity.YEAR }
        }
        val start = when(request.range) {
            StatsRange.TODAY -> today; StatsRange.WEEK -> today.minusDays(6); StatsRange.MONTH -> today.withDayOfMonth(1)
            StatsRange.YEAR -> today.withDayOfYear(1); StatsRange.ALL_TIME -> earliest ?: today
        }
        fun key(time: LocalDateTime): String {
            val d = time.toLocalDate()
            return when(granularity) {
                TrendBucketGranularity.FOUR_HOURS -> "$d ${((time.hour / 4) * 4).toString().padStart(2, '0')}"
                TrendBucketGranularity.DAY -> d.toString()
                TrendBucketGranularity.WEEK -> d.minusDays((d.dayOfWeek.value - 1).toLong()).toString()
                TrendBucketGranularity.MONTH -> d.toString().take(7)
                TrendBucketGranularity.QUARTER -> "${d.year}-Q${(d.monthValue - 1) / 3 + 1}"
                TrendBucketGranularity.HALF_YEAR -> "${d.year}-H${if(d.monthValue <= 6) 1 else 2}"
                TrendBucketGranularity.YEAR -> d.year.toString()
            }
        }
        val grouped = events.filter { it.first.toLocalDate() in start..today }.groupBy { key(it.first) }
        val buckets = continuousTrendBuckets(request.range, granularity, grouped.keys)
        val selected = targets(request.targetKind).filter { request.viewAll || it.id in request.targetIds.take(5) }
        val links = books.associate { book -> book.id to names(book, request.targetKind).map { id(it, request.targetKind) }.toSet() }
        return TrendSnapshot(request.range, granularity, buckets, selected.map { target ->
            TrendSeries(target, buckets.map { bucket ->
                val all = grouped[bucket].orEmpty()
                val matching = all.filter { target.id in links[it.second.id].orEmpty() }
                val ratings = IntArray(5)
                matching.forEach { (_, book) -> if(book.rating in 1..5) ratings[book.rating - 1]++ }
                val count = ratings.sum()
                val sum = ratings.indices.sumOf { (it + 1) * ratings[it] }.toFloat()
                TrendPoint(bucket, matching.size, all.size, ratings[3] + ratings[4], count, sum,
                    if(count > 0) sum / count else 0f, ratings[0], ratings[1], ratings[2], ratings[3], ratings[4],
                    trendReadNormalizationFactor(bucket, granularity, earliest),
                    total(matching), total(all), total(matching.filter { it.second.rating in 4..5 }),
                    total(matching.filter { it.second.rating in 1..5 }))
            })
        })
    }
    private fun continuousTrendBuckets(
        range: StatsRange,
        granularity: TrendBucketGranularity,
        recordedBuckets: Collection<String>
    ): List<String> {
        val today = LocalDate.now()
        return when (granularity) {
            TrendBucketGranularity.FOUR_HOURS -> (0..20 step 4).map { hour ->
                "${today.format(DateTimeFormatter.ISO_LOCAL_DATE)} ${hour.toString().padStart(2, '0')}"
            }
            TrendBucketGranularity.DAY -> {
                val start = when (range) {
                    StatsRange.TODAY -> today
                    StatsRange.WEEK -> today.minusDays(6)
                    StatsRange.MONTH -> today.withDayOfMonth(1)
                    StatsRange.YEAR -> today.withDayOfYear(1)
                    StatsRange.ALL_TIME -> recordedBuckets.minOrNull()?.let(LocalDate::parse) ?: today
                }
                generateSequence(start) { current -> current.plusDays(1).takeIf { it <= today } }
                    .map { it.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                    .toList()
            }
            TrendBucketGranularity.WEEK -> {
                val firstOfMonth = today.withDayOfMonth(1)
                val start = firstOfMonth.minusDays(((firstOfMonth.dayOfWeek.value - 1) % 7).toLong())
                generateSequence(start) { current -> current.plusWeeks(1).takeIf { it <= today } }
                    .map { it.format(DateTimeFormatter.ISO_LOCAL_DATE) }
                    .toList()
            }
            TrendBucketGranularity.MONTH -> {
                val currentMonth = today.withDayOfMonth(1)
                val start = when (range) {
                    StatsRange.YEAR -> today.withDayOfYear(1).withDayOfMonth(1)
                    StatsRange.ALL_TIME -> recordedBuckets.minOrNull()
                        ?.let { runCatching { LocalDate.parse("$it-01") }.getOrNull() }
                        ?: currentMonth
                    else -> currentMonth
                }
                generateSequence(start) { current -> current.plusMonths(1).takeIf { it <= currentMonth } }
                    .map { it.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)) }
                    .toList()
            }
            TrendBucketGranularity.QUARTER -> {
                val first = recordedBuckets.minOrNull()?.let(::parseQuarterStart)
                    ?: today.withDayOfYear(1)
                val current = LocalDate.of(today.year, ((today.monthValue - 1) / 3) * 3 + 1, 1)
                generateSequence(first) { value -> value.plusMonths(3).takeIf { it <= current } }
                    .map { value -> "${value.year}-Q${((value.monthValue - 1) / 3) + 1}" }
                    .toList()
            }
            TrendBucketGranularity.HALF_YEAR -> {
                val first = recordedBuckets.minOrNull()?.let(::parseHalfYearStart)
                    ?: today.withDayOfYear(1)
                val current = LocalDate.of(today.year, if (today.monthValue <= 6) 1 else 7, 1)
                generateSequence(first) { value -> value.plusMonths(6).takeIf { it <= current } }
                    .map { value -> "${value.year}-H${if (value.monthValue <= 6) 1 else 2}" }
                    .toList()
            }
            TrendBucketGranularity.YEAR -> {
                val firstYear = recordedBuckets.minOrNull()?.toIntOrNull() ?: today.year
                (firstYear..today.year).map(Int::toString)
            }
        }
    }

    private fun parseQuarterStart(key: String): LocalDate? = runCatching {
        val parts = key.split("-Q")
        LocalDate.of(parts[0].toInt(), (parts[1].toInt() - 1) * 3 + 1, 1)
    }.getOrNull()

    private fun parseHalfYearStart(key: String): LocalDate? = runCatching {
        val parts = key.split("-H")
        LocalDate.of(parts[0].toInt(), if (parts[1].toInt() == 1) 1 else 7, 1)
    }.getOrNull()

    private fun trendReadNormalizationFactor(
        bucket: String,
        granularity: TrendBucketGranularity,
        earliestReadDate: LocalDate?
    ): Float {
        val (bucketStart, bucketEndExclusive) = when (granularity) {
            TrendBucketGranularity.QUARTER -> {
                val start = parseQuarterStart(bucket) ?: return 1f
                start to start.plusMonths(3)
            }
            TrendBucketGranularity.HALF_YEAR -> {
                val start = parseHalfYearStart(bucket) ?: return 1f
                start to start.plusMonths(6)
            }
            TrendBucketGranularity.YEAR -> {
                val year = bucket.toIntOrNull() ?: return 1f
                val start = LocalDate.of(year, 1, 1)
                start to start.plusYears(1)
            }
            else -> return 1f
        }
        return thirtyDayRateFactor(
            bucketStart = bucketStart,
            bucketEndExclusive = bucketEndExclusive,
            earliestObservedDate = earliestReadDate,
            today = LocalDate.now()
        )
    }

}
