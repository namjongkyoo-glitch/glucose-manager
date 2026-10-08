package com.jadennam.glucose.domain.estimation

import com.jadennam.glucose.domain.model.GlucoseReading
import com.jadennam.glucose.domain.model.MeasureContext
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

data class EstimatorConfig(
    val minDays: Int = 14,
    val minTotalReadings: Int = 20,
    val minPerContext: Int = 3,
    val windowDays: Int = 90,
    val maxPerContext: Int = 60,
    val level: Double = 0.95,
)

sealed interface Estimate {
    data class Available(
        val context: MeasureContext,
        val pointMgDl: Int,
        val lowerMgDl: Int,
        val upperMgDl: Int,
        val sampleCount: Int,
    ) : Estimate

    data class InsufficientData(val reason: Reason) : Estimate

    enum class Reason { TOO_FEW_DAYS, TOO_FEW_READINGS, TOO_FEW_FOR_CONTEXT }
}

/**
 * Per-context median on log scale + pooled leave-one-out absolute residuals, with a
 * split-conformal quantile (k = ceil((n+1) * level)) giving a 95% prediction interval
 * for the next reading. Estimates are reference-only and never stored as measurements.
 */
class GlucoseEstimator(private val config: EstimatorConfig = EstimatorConfig()) {

    fun estimate(readings: List<GlucoseReading>, context: MeasureContext, nowMs: Long): Estimate =
        estimateAll(readings, nowMs)[context] ?: Estimate.InsufficientData(Estimate.Reason.TOO_FEW_FOR_CONTEXT)

    fun estimateAll(readings: List<GlucoseReading>, nowMs: Long): Map<MeasureContext, Estimate> {
        val past = readings.filter { it.measuredAt <= nowMs }
        fun all(reason: Estimate.Reason) = MeasureContext.entries.associateWith { Estimate.InsufficientData(reason) }

        val first = past.minOfOrNull { it.measuredAt } ?: return all(Estimate.Reason.TOO_FEW_READINGS)
        if (nowMs - first < config.minDays * DAY_MS) return all(Estimate.Reason.TOO_FEW_DAYS)
        if (past.size < config.minTotalReadings) return all(Estimate.Reason.TOO_FEW_READINGS)

        val windowStart = nowMs - config.windowDays * DAY_MS
        val byContext: Map<MeasureContext, List<Double>> = past
            .filter { it.measuredAt >= windowStart }
            .groupBy { it.context }
            .mapValues { (_, list) ->
                list.sortedByDescending { it.measuredAt }.take(config.maxPerContext).map { ln(it.valueMgDl.toDouble()) }
            }
            .filterValues { it.size >= config.minPerContext }

        val residuals = byContext.values.flatMap { leaveOneOutResiduals(it) }.sorted()
        val k = ceil((residuals.size + 1) * config.level).toInt()
        if (residuals.isEmpty() || k > residuals.size) return all(Estimate.Reason.TOO_FEW_READINGS)
        val q = residuals[k - 1]

        return MeasureContext.entries.associateWith { ctx ->
            val xs = byContext[ctx] ?: return@associateWith Estimate.InsufficientData(Estimate.Reason.TOO_FEW_FOR_CONTEXT)
            val m = median(xs)
            Estimate.Available(
                context = ctx,
                pointMgDl = Math.round(exp(m)).toInt(),
                lowerMgDl = floor(exp(m - q)).toInt(),
                upperMgDl = ceil(exp(m + q)).toInt(),
                sampleCount = xs.size,
            )
        }
    }

    private fun leaveOneOutResiduals(xs: List<Double>): List<Double> = xs.indices.map { i ->
        val others = xs.filterIndexed { j, _ -> j != i }
        kotlin.math.abs(xs[i] - median(others))
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        fun median(xs: List<Double>): Double {
            val s = xs.sorted()
            val n = s.size
            return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
        }
    }
}

data class BacktestResult(val evaluated: Int, val covered: Int, val insufficient: Int) {
    val coverage: Double get() = if (evaluated == 0) 0.0 else covered.toDouble() / evaluated
}

/** Rolling-origin backtest: each reading is predicted only from readings before it. */
object Backtester {
    fun run(readings: List<GlucoseReading>, estimator: GlucoseEstimator): BacktestResult {
        val sorted = readings.sortedBy { it.measuredAt }
        var evaluated = 0
        var covered = 0
        var insufficient = 0
        for (i in sorted.indices) {
            val target = sorted[i]
            when (val e = estimator.estimate(sorted.subList(0, i), target.context, target.measuredAt - 1)) {
                is Estimate.Available -> {
                    evaluated++
                    if (target.valueMgDl in e.lowerMgDl..e.upperMgDl) covered++
                }
                is Estimate.InsufficientData -> insufficient++
            }
        }
        return BacktestResult(evaluated, covered, insufficient)
    }
}
