package com.roinur.booktracker

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

enum class TrendTargetKind(val label: String) {
    TAGS("Collections"),
    CREATORS("Authors")
}

enum class TrendMetric(val label: String) { PAGES("Pages"), TIME("Time"), SESSIONS("Sessions") }

enum class TrendScale(val label: String) {
    READS("Reads"),
    SHARE("Share")
}

enum class TrendSignal(val label: String) {
    ALL("All"),
    POSITIVE("Positive"),
    AVERAGE_RATING("Avg rating")
}

enum class RatingAdjustment(
    val label: String,
    private val exponent: Float,
    val neutralPrior: Float
) {
    LINEAR("Linear", 1f, 0f),
    BALANCED("Balanced", 1.6f, 5f),
    STRONG("Strong", 2.2f, 5f);

    fun utility(rating: Int): Float {
        val distance = rating.coerceIn(1, 5) - 3
        if (distance == 0) return 0f
        val magnitude = (abs(distance) / 2f).pow(exponent)
        return if (distance > 0) magnitude else -magnitude
    }
}

enum class TrendBucketGranularity {
    FOUR_HOURS,
    DAY,
    WEEK,
    MONTH,
    QUARTER,
    HALF_YEAR,
    YEAR
}

enum class TrendBucketMode(val label: String) {
    LEGACY("Legacy bins"),
    ADAPTIVE("Adaptive bins")
}

data class TrendTarget(
    val id: Long,
    val name: String,
    val type: String,
    val entryCount: Int
)

data class TrendRequest(
    val targetKind: TrendTargetKind,
    val targetIds: List<Long>,
    val range: StatsRange,
    val viewAll: Boolean = false,
    val includeMisc: Boolean = false,
    val bucketMode: TrendBucketMode = TrendBucketMode.ADAPTIVE,
    val metric: TrendMetric = TrendMetric.SESSIONS
)

data class TrendPoint(
    val bucketKey: String,
    val matchingReads: Int,
    val totalReads: Int,
    val positiveRatings: Int,
    val ratedEntries: Int,
    val ratingSum: Float,
    val averageRating: Float,
    val rating1Count: Int,
    val rating2Count: Int,
    val rating3Count: Int,
    val rating4Count: Int,
    val rating5Count: Int,
    /** Converts long adaptive All Time buckets to a comparable 30-day read rate. */
    val readNormalizationFactor: Float = 1f,
    val matchingAmount: Float = matchingReads.toFloat(),
    val totalAmount: Float = totalReads.toFloat(),
    val positiveAmount: Float = positiveRatings.toFloat(),
    val ratedAmount: Float = ratedEntries.toFloat()
)

data class TrendSeries(
    val target: TrendTarget,
    val points: List<TrendPoint>
)

data class TrendSnapshot(
    val range: StatsRange,
    val granularity: TrendBucketGranularity,
    val buckets: List<String>,
    val series: List<TrendSeries>
)

/**
 * Converts stored bucket facts to the selected display model. Share is deliberately cumulative:
 * an empty day adds no new evidence and therefore retains the previous ratio/rating.
 */
fun trendValues(
    series: TrendSeries,
    scale: TrendScale,
    signal: TrendSignal,
    ratingAdjustment: RatingAdjustment = RatingAdjustment.LINEAR
): List<Float> {
    var matchingReads = 0f
    var totalReads = 0f
    var positiveRatings = 0f
    var ratedEntries = 0f
    var latestAverageRating = 0f
    val cumulativeRatings = IntArray(5)

    return series.points.map { point ->
        val pointRatings = point.ratingCounts()
        if (scale == TrendScale.SHARE) {
            matchingReads += point.matchingAmount
            totalReads += point.totalAmount
            positiveRatings += point.positiveAmount
            ratedEntries += point.ratedAmount
            pointRatings.forEachIndexed { index, count -> cumulativeRatings[index] += count }
        }
        val ratings = if (scale == TrendScale.SHARE) cumulativeRatings else pointRatings
        val ratingCount = ratings.sum()
        val preferenceSum = ratings.indices.sumOf { index ->
            (ratings[index] * ratingAdjustment.utility(index + 1)).toDouble()
        }.toFloat()
        val adjustedAverage = if (ratingCount > 0) {
            3f + 2f * preferenceSum / (ratingCount + ratingAdjustment.neutralPrior)
        } else {
            0f
        }
        if (adjustedAverage.isFinite() && adjustedAverage > 0f) {
            latestAverageRating = adjustedAverage.coerceIn(1f, 5f)
        }

        when (signal) {
            TrendSignal.ALL -> when (scale) {
                TrendScale.READS -> point.matchingAmount * point.readNormalizationFactor
                TrendScale.SHARE -> if (totalReads > 0) matchingReads * 100f / totalReads else 0f
            }
            TrendSignal.POSITIVE -> when (scale) {
                TrendScale.READS -> point.positiveAmount * point.readNormalizationFactor
                TrendScale.SHARE -> if (ratedEntries > 0) positiveRatings * 100f / ratedEntries else 0f
            }
            TrendSignal.AVERAGE_RATING -> when (scale) {
                // Zero is not a valid rating. Keep the latest observed value through an
                // empty bucket rather than drawing a false drop to the graph floor.
                TrendScale.READS, TrendScale.SHARE -> latestAverageRating
            }
        }.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
    }
}

private fun TrendPoint.ratingCounts(): IntArray = intArrayOf(
    rating1Count,
    rating2Count,
    rating3Count,
    rating4Count,
    rating5Count
)

internal fun thirtyDayRateFactor(
    bucketStart: LocalDate,
    bucketEndExclusive: LocalDate,
    earliestObservedDate: LocalDate?,
    today: LocalDate
): Float {
    val observedStart = maxOf(bucketStart, earliestObservedDate ?: bucketStart)
    val observedEndExclusive = minOf(bucketEndExclusive, today.plusDays(1))
    val observedDays = ChronoUnit.DAYS
        .between(observedStart, observedEndExclusive)
        .coerceAtLeast(1L)
    return 30f / observedDays.toFloat()
}
