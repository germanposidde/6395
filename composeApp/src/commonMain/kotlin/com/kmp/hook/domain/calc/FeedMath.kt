package com.kmp.hook.domain.calc

/** Result of a full feed/growth computation. All masses kg, weights grams. */
data class FeedResult(
    val biomassKg: Double,
    val dailyFeedKg: Double,
    val dailyGainPerFishGrams: Double,
    val predictedWeightGrams: Double,
    val totalFeedKg: Double,
    val feedCost: Double,
    val fcr: Double,
)

/** Pure aquaculture math — biomass, daily feed, FCR, growth prediction & cost. */
object FeedMath {

    fun biomassKg(quantity: Int, avgWeightGrams: Double): Double =
        quantity * avgWeightGrams / 1000.0

    fun dailyFeedKg(biomassKg: Double, feedingPercent: Double): Double =
        biomassKg * feedingPercent / 100.0

    /** Feed Conversion Ratio = feed given / weight gained. */
    fun fcr(feedKg: Double, gainKg: Double): Double =
        if (gainKg <= 0.0) 0.0 else feedKg / gainKg

    /** Daily weight gain per fish implied by the feeding rate & FCR. */
    fun dailyGainGrams(avgWeightGrams: Double, feedingPercent: Double, fcr: Double): Double {
        if (fcr <= 0.0) return 0.0
        val dailyFeedPerFishG = avgWeightGrams * feedingPercent / 100.0
        return dailyFeedPerFishG / fcr
    }

    fun compute(
        quantity: Int,
        avgWeightGrams: Double,
        feedingPercent: Double,
        fcr: Double,
        horizonDays: Int,
        pricePerKg: Double,
    ): FeedResult {
        val biomass = biomassKg(quantity, avgWeightGrams)
        val dailyFeed = dailyFeedKg(biomass, feedingPercent)
        val dailyGain = dailyGainGrams(avgWeightGrams, feedingPercent, fcr)
        val predicted = avgWeightGrams + dailyGain * horizonDays
        val endBiomass = biomassKg(quantity, predicted)
        val avgBiomass = (biomass + endBiomass) / 2.0
        val totalFeed = dailyFeedKg(avgBiomass, feedingPercent) * horizonDays
        val cost = totalFeed * pricePerKg
        return FeedResult(
            biomassKg = biomass,
            dailyFeedKg = dailyFeed,
            dailyGainPerFishGrams = dailyGain,
            predictedWeightGrams = predicted,
            totalFeedKg = totalFeed,
            feedCost = cost,
            fcr = fcr,
        )
    }
}
