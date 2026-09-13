package com.roinur.booktracker

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

internal enum class UniqueTrendSignificanceMode { READS, SHARE }

internal enum class UniqueTrendCandidateReason {
    NONE,
    CORE_SIGNIFICANCE,
    METRIC_STANDOUT,
    SHAPE_NOVELTY,
    CORE_AND_METRIC,
    CORE_AND_NOVELTY,
    METRIC_AND_NOVELTY,
    ALL
}

internal data class UniqueTrendScore(
    val targetId: Long,
    val sampleCount: Int,
    val totalReadCount: Int,
    val ratedCount: Int,
    val readCount: Int,
    val share: Float,
    val significanceScore: Float,
    val significancePercentile: Float,
    val rawMetricValue: Float,
    val adjustedMetricValue: Float,
    val metricStandoutScore: Float,
    val metricStandoutPercentile: Float,
    val shapeNovelty: Float,
    val noveltyPercentile: Float,
    val confidence: Float,
    val uniqueScore: Float,
    val combinedRank: Float,
    val finalCandidateReason: UniqueTrendCandidateReason = UniqueTrendCandidateReason.NONE
)

internal data class UniqueTrendSelection(
    val targetIds: Set<Long>,
    val scores: List<UniqueTrendScore>
)

private data class MetricAnalysis(
    val shapeValues: List<Float>,
    val sampleCount: Int,
    val ratedCount: Int,
    val rawMetricValue: Float,
    val adjustedMetricValue: Float,
    val standoutScore: Float,
    val activeBuckets: Int
)

/**
 * Scores three independent reasons for retaining a trend: core significance, a standout in
 * the currently selected metric, and confidence-aware temporal-shape novelty. Displayed graph
 * values remain unchanged; shrinkage is used only by this analysis pipeline.
 */
internal fun scoreUniqueTrends(
    series: List<TrendSeries>,
    valuesByTargetId: Map<Long, List<Float>>,
    significanceMode: UniqueTrendSignificanceMode = UniqueTrendSignificanceMode.READS,
    scale: TrendScale = TrendScale.READS,
    signal: TrendSignal = TrendSignal.ALL,
    ratingAdjustment: RatingAdjustment = RatingAdjustment.LINEAR,
    confidenceK: Float = 6f
): List<UniqueTrendScore> {
    if (series.isEmpty()) return emptyList()

    val globalRatedCount = series.sumOf { trend -> trend.points.sumOf { it.ratedEntries } }
    val globalPositiveCount = series.sumOf { trend -> trend.points.sumOf { it.positiveRatings } }
    val globalPositiveRate = if (globalRatedCount > 0) {
        globalPositiveCount.toFloat() / globalRatedCount
    } else {
        0.5f
    }
    val globalPreferenceSum = series.sumOf { trend ->
        trend.points.sumOf { point ->
            val counts = point.ratingCounts()
            counts.indices.sumOf { index ->
                counts[index] * ratingAdjustment.utility(index + 1).toDouble()
            }
        }
    }.toFloat()
    val globalPreference = if (globalRatedCount > 0) globalPreferenceSum / globalRatedCount else 0f
    val globalAdjustedRating = 3f + 2f * globalPreference

    val analyses = series.associate { trend ->
        trend.target.id to analyzeMetric(
            series = trend,
            rawValues = valuesByTargetId[trend.target.id].orEmpty(),
            scale = scale,
            signal = signal,
            ratingAdjustment = ratingAdjustment,
            globalPositiveRate = globalPositiveRate,
            globalPreference = globalPreference,
            globalAdjustedRating = globalAdjustedRating,
            confidenceK = confidenceK
        )
    }
    val readCounts = series.associate { trend ->
        trend.target.id to trend.points.sumOf { it.matchingReads }.coerceAtLeast(0)
    }
    val relevantCounts = series.associate { trend ->
        val analysis = analyses.getValue(trend.target.id)
        trend.target.id to when (signal) {
            TrendSignal.ALL -> readCounts.getValue(trend.target.id)
            TrendSignal.POSITIVE, TrendSignal.AVERAGE_RATING -> analysis.ratedCount
        }
    }
    val totalRelevantCount = relevantCounts.values.sum().coerceAtLeast(1)
    val shares = relevantCounts.mapValues { (_, count) -> count.toFloat() / totalRelevantCount }
    val rawSignificance = relevantCounts.mapValues { (targetId, count) ->
        when (significanceMode) {
            UniqueTrendSignificanceMode.READS -> ln(1f + count)
            UniqueTrendSignificanceMode.SHARE -> ln(1f + (shares[targetId] ?: 0f) * 100f)
        }
    }
    val maximumSignificance = rawSignificance.values.maxOrNull()?.coerceAtLeast(0.00001f) ?: 1f

    val features = series.mapNotNull { trend ->
        val feature = uniqueTrendFeature(analyses.getValue(trend.target.id).shapeValues)
            ?: return@mapNotNull null
        trend.target.id to feature
    }
    val dimensions = features.firstOrNull()?.second?.size ?: 0
    val centroid = FloatArray(dimensions)
    features.forEach { (_, feature) ->
        feature.forEachIndexed { index, value -> centroid[index] += value }
    }
    val centroidLength = sqrt(centroid.sumOf { (it * it).toDouble() }).toFloat()
    if (centroidLength > 0.00001f) {
        centroid.indices.forEach { centroid[it] /= centroidLength }
    }
    val featureById = features.toMap()

    val rawScores = series.map { trend ->
        val targetId = trend.target.id
        val analysis = analyses.getValue(targetId)
        val readCount = readCounts.getValue(targetId)
        val feature = featureById[targetId]
        val shapeNovelty = if (feature == null || centroidLength <= 0.00001f) {
            0f
        } else {
            val dot = feature.indices.sumOf { index ->
                (feature[index] * centroid[index]).toDouble()
            }.toFloat().coerceIn(-1f, 1f)
            ((1f - dot) / 2f).coerceIn(0f, 1f)
        }
        val confidence = sampleConfidence(analysis.sampleCount, confidenceK)
        val temporalSupport = if (analysis.activeBuckets <= 0) {
            0f
        } else {
            analysis.activeBuckets / (analysis.activeBuckets + 1.5f)
        }
        UniqueTrendScore(
            targetId = targetId,
            sampleCount = analysis.sampleCount,
            totalReadCount = readCount,
            ratedCount = analysis.ratedCount,
            readCount = readCount,
            share = shares[targetId] ?: 0f,
            significanceScore = ((rawSignificance[targetId] ?: 0f) / maximumSignificance).coerceIn(0f, 1f),
            significancePercentile = 0f,
            rawMetricValue = analysis.rawMetricValue,
            adjustedMetricValue = analysis.adjustedMetricValue,
            metricStandoutScore = analysis.standoutScore.coerceAtLeast(0f),
            metricStandoutPercentile = 0f,
            shapeNovelty = shapeNovelty,
            noveltyPercentile = 0f,
            confidence = confidence,
            uniqueScore = (shapeNovelty * confidence * temporalSupport).coerceIn(0f, 1f),
            combinedRank = 0f
        )
    }
    return addUniqueTrendPercentiles(rawScores)
}

internal fun selectMeaningfulUniqueTrends(
    scores: List<UniqueTrendScore>,
    maximumCount: Int = 24
): UniqueTrendSelection {
    if (maximumCount <= 0) return UniqueTrendSelection(emptySet(), scores)
    val eligible = scores.filter { it.sampleCount >= 3 }
    if (eligible.isEmpty()) return UniqueTrendSelection(emptySet(), scores)

    val targetCount = minOf(maximumCount, eligible.size)
    val coreQuota = (targetCount + 2) / 3
    val metricQuota = (targetCount + 1) / 3
    val noveltyQuota = targetCount / 3
    val coreIds = eligible
        .sortedWith(compareByDescending<UniqueTrendScore> { it.significancePercentile }.thenByDescending { it.sampleCount })
        .take(coreQuota)
        .mapTo(linkedSetOf()) { it.targetId }
    val metricIds = eligible
        .filter { it.metricStandoutScore > 0.00001f }
        .sortedWith(compareByDescending<UniqueTrendScore> { it.metricStandoutPercentile }.thenByDescending { it.metricStandoutScore })
        .take(metricQuota)
        .mapTo(linkedSetOf()) { it.targetId }
    val noveltyIds = eligible
        .filter { it.uniqueScore >= 0.035f }
        .sortedWith(compareByDescending<UniqueTrendScore> { it.noveltyPercentile }.thenByDescending { it.uniqueScore })
        .take(noveltyQuota)
        .mapTo(linkedSetOf()) { it.targetId }
    val selectedIds = linkedSetOf<Long>().apply {
        addAll(coreIds)
        addAll(metricIds)
        addAll(noveltyIds)
    }

    eligible.asSequence()
        .filterNot { it.targetId in selectedIds }
        .sortedWith(
            compareByDescending<UniqueTrendScore> { it.combinedRank }
                .thenByDescending { it.significancePercentile + it.metricStandoutPercentile + it.noveltyPercentile }
                .thenByDescending { it.sampleCount }
        )
        .take(targetCount - selectedIds.size)
        .forEach { score ->
            selectedIds += score.targetId
            val strongest = maxOf(score.significancePercentile, score.metricStandoutPercentile, score.noveltyPercentile)
            if (score.significancePercentile == strongest) coreIds += score.targetId
            if (score.metricStandoutPercentile == strongest) metricIds += score.targetId
            if (score.noveltyPercentile == strongest) noveltyIds += score.targetId
        }

    val selectedScores = scores.map { score ->
        if (score.targetId !in selectedIds) {
            score.copy(finalCandidateReason = UniqueTrendCandidateReason.NONE)
        } else {
            score.copy(
                finalCandidateReason = candidateReason(
                    core = score.targetId in coreIds,
                    metric = score.targetId in metricIds,
                    novelty = score.targetId in noveltyIds
                )
            )
        }
    }
    return UniqueTrendSelection(selectedIds, selectedScores)
}

private fun analyzeMetric(
    series: TrendSeries,
    rawValues: List<Float>,
    scale: TrendScale,
    signal: TrendSignal,
    ratingAdjustment: RatingAdjustment,
    globalPositiveRate: Float,
    globalPreference: Float,
    globalAdjustedRating: Float,
    confidenceK: Float
): MetricAnalysis {
    val readCount = series.points.sumOf { it.matchingReads }
    val ratedCount = series.points.sumOf { it.ratedEntries }
    val adjustedValues = ArrayList<Float>(series.points.size)
    var cumulativeTotal = 0
    var cumulativePositive = 0
    var cumulativeRated = 0
    val cumulativeRatings = IntArray(5)

    series.points.forEachIndexed { index, point ->
        if (scale == TrendScale.SHARE) {
            cumulativeTotal += point.totalReads
            cumulativePositive += point.positiveRatings
            cumulativeRated += point.ratedEntries
            val counts = point.ratingCounts()
            counts.indices.forEach { ratingIndex -> cumulativeRatings[ratingIndex] += counts[ratingIndex] }
        }
        val adjusted = when (signal) {
            TrendSignal.ALL -> when (scale) {
                TrendScale.READS -> point.matchingReads * sampleConfidence(point.matchingReads, confidenceK)
                TrendScale.SHARE -> {
                    val raw = rawValues.getOrNull(index) ?: 0f
                    raw * sampleConfidence(cumulativeTotal, confidenceK)
                }
            }
            TrendSignal.POSITIVE -> {
                val positive = if (scale == TrendScale.SHARE) cumulativePositive else point.positiveRatings
                val rated = if (scale == TrendScale.SHARE) cumulativeRated else point.ratedEntries
                val adjustedRate = (positive + confidenceK * globalPositiveRate) / (rated + confidenceK)
                if (scale == TrendScale.SHARE) adjustedRate * 100f else adjustedRate * rated
            }
            TrendSignal.AVERAGE_RATING -> {
                val counts = if (scale == TrendScale.SHARE) cumulativeRatings else point.ratingCounts()
                val sample = counts.sum()
                val preferenceSum = counts.indices.sumOf { ratingIndex ->
                    counts[ratingIndex] * ratingAdjustment.utility(ratingIndex + 1).toDouble()
                }.toFloat()
                val adjustedPreference = (preferenceSum + confidenceK * globalPreference) / (sample + confidenceK)
                3f + 2f * adjustedPreference
            }
        }
        adjustedValues += adjusted
    }

    val allRatings = IntArray(5)
    series.points.forEach { point ->
        val counts = point.ratingCounts()
        counts.indices.forEach { index -> allRatings[index] += counts[index] }
    }
    val totalPreference = allRatings.indices.sumOf { index ->
        allRatings[index] * ratingAdjustment.utility(index + 1).toDouble()
    }.toFloat()
    val rawMetricValue = when (signal) {
        TrendSignal.ALL -> if (scale == TrendScale.READS) readCount.toFloat() else rawValues.lastOrNull() ?: 0f
        TrendSignal.POSITIVE -> if (scale == TrendScale.READS) {
            series.points.sumOf { it.positiveRatings }.toFloat()
        } else {
            if (ratedCount > 0) series.points.sumOf { it.positiveRatings } * 100f / ratedCount else 0f
        }
        TrendSignal.AVERAGE_RATING -> if (ratedCount > 0) {
            series.points.sumOf { it.ratingSum.toDouble() }.toFloat() / ratedCount
        } else {
            0f
        }
    }
    val adjustedMetricValue = when (signal) {
        TrendSignal.ALL, TrendSignal.POSITIVE -> adjustedValues.lastOrNull() ?: 0f
        TrendSignal.AVERAGE_RATING -> 3f + 2f * (
            (totalPreference + confidenceK * globalPreference) / (ratedCount + confidenceK)
        )
    }
    val range = adjustedValues.valueRange()
    val maximumChange = adjustedValues.maximumAdjacentChange()
    val standoutScore = when (signal) {
        TrendSignal.ALL -> when (scale) {
            TrendScale.READS -> ln(1f + adjustedValues.sum()) +
                0.35f * ln(1f + adjustedValues.maxOrNull().orZero()) + 0.35f * ln(1f + range)
            TrendScale.SHARE -> adjustedValues.maxOrNull().orZero() + 0.75f * range + 0.35f * maximumChange
        }
        TrendSignal.POSITIVE -> when (scale) {
            TrendScale.READS -> ln(1f + adjustedValues.sum()) + 0.5f * ln(1f + range) +
                0.35f * ln(1f + maximumChange)
            TrendScale.SHARE -> {
                val baselinePercent = globalPositiveRate * 100f
                adjustedValues.maxOfOrNull { abs(it - baselinePercent) }.orZero() +
                    0.75f * range + 0.35f * maximumChange
            }
        }
        TrendSignal.AVERAGE_RATING -> {
            adjustedValues.maxOfOrNull { abs(it - globalAdjustedRating) }.orZero() +
                0.75f * range + 0.35f * maximumChange
        }
    }
    val sampleCount = when (signal) {
        TrendSignal.ALL -> readCount
        TrendSignal.POSITIVE, TrendSignal.AVERAGE_RATING -> ratedCount
    }
    val activeBuckets = series.points.count { point ->
        when (signal) {
            TrendSignal.ALL -> point.matchingReads > 0
            TrendSignal.POSITIVE, TrendSignal.AVERAGE_RATING -> point.ratedEntries > 0
        }
    }
    return MetricAnalysis(
        shapeValues = adjustedValues,
        sampleCount = sampleCount,
        ratedCount = ratedCount,
        rawMetricValue = rawMetricValue,
        adjustedMetricValue = adjustedMetricValue,
        standoutScore = standoutScore,
        activeBuckets = activeBuckets
    )
}

private fun addUniqueTrendPercentiles(scores: List<UniqueTrendScore>): List<UniqueTrendScore> {
    val significancePercentiles = percentileById(scores) { it.significanceScore }
    val metricPercentiles = percentileById(scores) { it.metricStandoutScore }
    val noveltyPercentiles = percentileById(scores) { it.uniqueScore }
    return scores.map { score ->
        val significancePercentile = significancePercentiles[score.targetId] ?: 0f
        val metricPercentile = metricPercentiles[score.targetId] ?: 0f
        val noveltyPercentile = noveltyPercentiles[score.targetId] ?: 0f
        score.copy(
            significancePercentile = significancePercentile,
            metricStandoutPercentile = metricPercentile,
            noveltyPercentile = noveltyPercentile,
            combinedRank = maxOf(significancePercentile, metricPercentile, noveltyPercentile)
        )
    }.sortedWith(
        compareByDescending<UniqueTrendScore> { it.combinedRank }
            .thenByDescending { it.significancePercentile + it.metricStandoutPercentile + it.noveltyPercentile }
    )
}

private fun percentileById(
    scores: List<UniqueTrendScore>,
    value: (UniqueTrendScore) -> Float
): Map<Long, Float> {
    if (scores.isEmpty()) return emptyMap()
    if (scores.size == 1) return mapOf(scores.single().targetId to 1f)
    val sorted = scores.sortedBy(value)
    val percentiles = HashMap<Long, Float>(scores.size)
    var start = 0
    while (start < sorted.size) {
        var end = start + 1
        val currentValue = value(sorted[start])
        while (end < sorted.size && value(sorted[end]) == currentValue) end += 1
        val averageRank = (start + end - 1) / 2f
        val percentile = averageRank / (sorted.size - 1).toFloat()
        for (index in start until end) percentiles[sorted[index].targetId] = percentile
        start = end
    }
    return percentiles
}

private fun uniqueTrendFeature(values: List<Float>): FloatArray? {
    if (values.size < 3) return null
    val smoothed = FloatArray(values.size) { index ->
        when (index) {
            0 -> values[0] * 0.75f + values[1] * 0.25f
            values.lastIndex -> values[index - 1] * 0.25f + values[index] * 0.75f
            else -> values[index - 1] * 0.25f + values[index] * 0.5f + values[index + 1] * 0.25f
        }
    }
    val mean = smoothed.average().toFloat()
    val centered = FloatArray(smoothed.size) { smoothed[it] - mean }
    val length = sqrt(centered.sumOf { (it * it).toDouble() }).toFloat()
    if (length <= 0.00001f) return null
    centered.indices.forEach { centered[it] /= length }

    val coefficientCount = minOf(8, centered.size - 1)
    val coefficients = FloatArray(coefficientCount) { coefficientIndex ->
        val frequency = coefficientIndex + 1
        centered.indices.sumOf { sampleIndex ->
            centered[sampleIndex] * cos(PI / centered.size * (sampleIndex + 0.5) * frequency)
        }.toFloat()
    }
    val coefficientLength = sqrt(coefficients.sumOf { (it * it).toDouble() }).toFloat()
    if (coefficientLength <= 0.00001f) return null
    coefficients.indices.forEach { coefficients[it] /= coefficientLength }
    return coefficients
}

private fun candidateReason(core: Boolean, metric: Boolean, novelty: Boolean): UniqueTrendCandidateReason = when {
    core && metric && novelty -> UniqueTrendCandidateReason.ALL
    core && metric -> UniqueTrendCandidateReason.CORE_AND_METRIC
    core && novelty -> UniqueTrendCandidateReason.CORE_AND_NOVELTY
    metric && novelty -> UniqueTrendCandidateReason.METRIC_AND_NOVELTY
    core -> UniqueTrendCandidateReason.CORE_SIGNIFICANCE
    metric -> UniqueTrendCandidateReason.METRIC_STANDOUT
    novelty -> UniqueTrendCandidateReason.SHAPE_NOVELTY
    else -> UniqueTrendCandidateReason.NONE
}

private fun TrendPoint.ratingCounts(): IntArray = intArrayOf(
    rating1Count,
    rating2Count,
    rating3Count,
    rating4Count,
    rating5Count
)

private fun sampleConfidence(sampleCount: Int, k: Float): Float = when {
    sampleCount <= 0 -> 0f
    else -> sampleCount / (sampleCount + k.coerceAtLeast(0.0001f))
}

private fun List<Float>.valueRange(): Float = if (isEmpty()) 0f else maxOrNull().orZero() - minOrNull().orZero()

private fun List<Float>.maximumAdjacentChange(): Float = zipWithNext { first, second -> abs(second - first) }
    .maxOrNull()
    .orZero()

private fun Float?.orZero(): Float = this ?: 0f
