package com.akash.agni.domain

import com.akash.agni.data.AgniDatabase
import com.akash.agni.data.FoodLog
import com.akash.agni.data.Meal
import com.akash.agni.data.Plan
import com.akash.agni.data.Settings
import com.akash.agni.data.weight
import com.akash.agni.health.HealthConnectManager
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TipKind(val label: String) { FOOD("Food"), ACTIVITY("Activity"), HABIT("Habit"), WIN("Keep it up") }

data class Tip(
    val id: String,
    val kind: TipKind,
    /** Lower = more important. */
    val priority: Int,
    val title: String,
    val detail: String,
)

/**
 * Rule-based coach. Looks at the last 7 finished days (today is excluded because it isn't over) and
 * returns specific, numbers-backed suggestions. Everything runs on the phone; nothing leaves it.
 * These are general fitness habits, not medical advice.
 */
class Coach(private val db: AgniDatabase, private val health: HealthConnectManager) {

    private val treats = setOf(
        "Fafda", "Jalebi", "Chevdo", "Sev mamra", "Kachori", "Samosa", "Gulab jamun", "Mohanthal", "Shrikhand",
        "Soft drink", "Beer", "Cold coffee", "Pav bhaji", "Dabeli", "French fries", "Pani puri",
    )
    private val strengthPlans = setOf(Plan.lowerCore.id, Plan.upperConditioning.id, Plan.fullBody.id)

    suspend fun tips(s: Settings, snap: Snapshot): List<Tip> {
        val today = LocalDate.now()
        val to = today.minusDays(1).toEpochDay()
        val from = today.minusDays(7).toEpochDay()
        val logs = db.foodLogs().forRangeOnce(from, to)
        val byDay = logs.groupBy { it.day }.filterValues { it.size >= 2 } // a day with 1 item isn't really logged
        val days = byDay.size
        val target = snap.target
        val out = mutableListOf<Tip>()

        // ---------- Logging ----------
        if (days < 3) {
            out += Tip(
                "log_more", TipKind.HABIT, 5, "Log a few more days",
                "You've logged $days of the last 7 days. Food suggestions start after 3 logged days, " +
                    "and they get better the more complete your logs are. Quick meals make it a one-tap job.",
            )
        } else {
            val dayKcal = byDay.mapValues { (_, l) -> l.sumOf { it.kcal } }
            val avgKcal = dayKcal.values.average()
            val avgProtein = byDay.values.map { l -> l.sumOf { it.protein } }.average()
            val totalKcal = logs.sumOf { it.kcal }.coerceAtLeast(1.0)
            val carbShare = logs.sumOf { it.carbs } * 4 / totalKcal
            val fatShare = logs.sumOf { it.fat } * 9 / totalKcal

            // Calories
            val over = avgKcal - target
            when {
                over > 150 -> out += Tip(
                    "kcal_over", TipKind.FOOD, 10, "About ${over.roundToInt()} kcal a day over target",
                    "You averaged ${avgKcal.roundToInt()} kcal vs your ${target} target. " + biggestSources(logs, days),
                )
                avgKcal < target - 400 -> out += Tip(
                    "kcal_under", TipKind.FOOD, 15, "You're eating too little",
                    "You averaged ${avgKcal.roundToInt()} kcal, ${(target - avgKcal).roundToInt()} under target. " +
                        "Eating this far under costs muscle and usually ends in a binge. Add a protein snack " +
                        "(Greek yogurt, sprouts, 2 boiled eggs) rather than skipping meals.",
                )
                abs(over) <= 100 -> out += Tip(
                    "kcal_ok", TipKind.WIN, 90, "Calories right on target",
                    "You averaged ${avgKcal.roundToInt()} kcal against ${target}. That's what moves the scale.",
                )
            }

            // Protein
            val pTarget = s.proteinTarget
            if (avgProtein < pTarget * 0.8) {
                val gap = (pTarget - avgProtein).roundToInt()
                out += Tip(
                    "protein_low", TipKind.FOOD, 20, "Protein is $gap g a day short",
                    "You averaged ${avgProtein.roundToInt()} g vs ${pTarget} g. Protein keeps you full and protects " +
                        "muscle while you lose fat. Easy adds: " + proteinIdeas(gap) + ".",
                )
            } else if (byDay.values.count { l -> l.sumOf { it.protein } >= pTarget } >= 4) {
                out += Tip("protein_ok", TipKind.WIN, 91, "Protein goal hit most days", "Keep building meals around dal, paneer, dahi and eggs.")
            }

            // Carbs, as swaps rather than "cut carbs"
            if (carbShare > 0.60) {
                val topCarb = logs.groupBy { it.name }.mapValues { (_, l) -> l.sumOf { it.carbs } }
                    .entries.sortedByDescending { it.value }.take(2).joinToString(" and ") { it.key.substringBefore(" (") }
                out += Tip(
                    "carbs_high", TipKind.FOOD, 30, "${(carbShare * 100).roundToInt()}% of calories came from carbs",
                    "Mostly from $topCarb. You don't need to cut them out: swap one rotli or half the rice for " +
                        "a katori of dal, sprouts or paneer. Same fullness, more protein, fewer calories.",
                )
            }

            // Fat
            if (fatShare > 0.38) {
                out += Tip(
                    "fat_high", TipKind.FOOD, 35, "${(fatShare * 100).roundToInt()}% of calories came from fat",
                    "Usually ghee, oil in shaak and fried farsan. Try plain rotli instead of ghee rotli " +
                        "(saves ~30 kcal each) and measure oil by the spoon when cooking.",
                )
            }

            // Treats / farsan
            val treatLogs = logs.filter { l -> treats.any { l.name.startsWith(it) } }
            val treatKcal = treatLogs.sumOf { it.kcal }
            if (treatKcal / days > 250) {
                val names = treatLogs.groupBy { it.name }.entries.sortedByDescending { e -> e.value.sumOf { it.kcal } }
                    .take(3).joinToString(", ") { it.key }
                out += Tip(
                    "treats", TipKind.FOOD, 40, "Snacks and treats: ~${(treatKcal / days).roundToInt()} kcal a day",
                    "Mainly $names. Keep them, but move them into your weekly treat (the calorie bank) " +
                        "instead of having them on weekdays. Khakhra, roasted chana or fruit work as everyday snacks.",
                )
            }

            // Sugary chai
            val chai = logs.filter { it.name == "Masala chai" }.sumOf { it.servings }
            if (chai / days >= 2) {
                out += Tip(
                    "chai", TipKind.FOOD, 45, "${chai.roundToInt()} cups of sweet chai this week",
                    "Each cup of sugared chai is ~50 kcal of sugar. Going sugar-free (or half sugar) on " +
                        "${(chai / days).roundToInt()} cups a day saves about ${(chai * 50).roundToInt()} kcal a week.",
                )
            }

            // Late-heavy eating
            val dinnerShare = byDay.values.map { l ->
                val tot = l.sumOf { it.kcal }.coerceAtLeast(1.0)
                l.filter { it.meal == Meal.DINNER }.sumOf { it.kcal } / tot
            }.average()
            if (dinnerShare > 0.45) {
                out += Tip(
                    "dinner_heavy", TipKind.HABIT, 50, "Dinner is ${(dinnerShare * 100).roundToInt()}% of your day",
                    "Heavy late dinners at a desk job tend to come from under-eating at lunch. Move some of it " +
                        "earlier: a bigger protein lunch makes the evening hunger smaller.",
                )
            }

            // Weekend spike
            val weekend = dayKcal.filterKeys { LocalDate.ofEpochDay(it).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.values
            val weekday = dayKcal.filterKeys { LocalDate.ofEpochDay(it).dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.values
            if (weekend.isNotEmpty() && weekday.size >= 2 && weekend.average() - weekday.average() > 500) {
                out += Tip(
                    "weekend", TipKind.HABIT, 55, "Weekends run ${(weekend.average() - weekday.average()).roundToInt()} kcal higher",
                    "That's normal when you eat out, so plan for it: bank ~150 kcal on each weekday and the weekend " +
                        "meal is already paid for. Order a protein starter (paneer tikka) before the main.",
                )
            }
        }

        // ---------- Activity ----------
        val sessions = db.workouts().sessionsOnce(from, to).filter { it.completed }
        val strength = sessions.count { it.workoutId in strengthPlans || (it.imported && it.exerciseType in ActivityImporter.strengthTypes) }
        if (strength < 2) {
            out += Tip(
                "strength", TipKind.ACTIVITY, 25, "Only $strength strength " + (if (strength == 1) "session" else "sessions") + " this week",
                "Strength training is what reshapes belly, hips and thighs as the fat comes off; cardio alone " +
                    "loses muscle too. Aim for 3. Even a 25-minute version of the plan counts.",
            )
        } else if (strength >= 3) {
            out += Tip("strength_ok", TipKind.WIN, 92, "$strength strength sessions this week", "That's the full plan. Try adding weight where you hit the top of the rep range.")
        }

        val steps = health.stepsByDay(8).dropLast(1) // drop today
        if (steps.size >= 3) {
            val avg = steps.map { it.second }.average()
            if (avg < s.stepGoal * 0.8) {
                out += Tip(
                    "steps", TipKind.ACTIVITY, 35, "Steps average ${avg.roundToInt()} a day",
                    "Your goal is ${s.stepGoal}. A 10-minute walk after lunch and after dinner adds ~2,000 steps " +
                        "(about 80–100 kcal) and helps blood sugar after meals. Easiest win for a desk job.",
                )
            } else if (avg >= s.stepGoal) {
                out += Tip("steps_ok", TipKind.WIN, 93, "Step goal met on average", "${avg.roundToInt()} steps a day. Nice.")
            }
        }

        // ---------- Weight trend ----------
        val weights = db.measurements().all().first().filter { it.weightKg != null }
        if (weights.size >= 2) {
            val last = weights.last()
            val first = weights.lastOrNull { last.day - it.day >= 14 } // at least two weeks apart
            if (first != null) {
                val weeks = (last.day - first.day) / 7.0
                val rate = (first.weightKg!! - last.weightKg!!) / weeks // kg lost per week
                val pct = rate / first.weightKg * 100
                when {
                    pct > 1.0 -> out += Tip(
                        "too_fast", TipKind.FOOD, 12, "Losing ${s.weight(rate)} a week: a bit fast",
                        "More than 1% of body weight a week usually means muscle loss and rebound hunger. " +
                            "Add 150–200 kcal a day, mostly protein.",
                    )
                    rate < 0.1 && weeks >= 3 && snap.history.takeLast(14).count { it.onTrack } >= 8 -> out += Tip(
                        "plateau", TipKind.FOOD, 18, "Weight hasn't moved in ${weeks.roundToInt()} weeks",
                        "You've been consistent, so it's not effort. Either lower your target by 100–150 kcal in " +
                            "Settings, or add 2,000 steps a day. Also check portions: katori sizes creep up.",
                    )
                    rate >= 0.25 -> out += Tip(
                        "weight_ok", TipKind.WIN, 89, "Down ${s.weight(rate)} a week",
                        "A steady, sustainable pace. Measure your waist every 2 weeks; it shows belly fat loss the scale can hide.",
                    )
                }
            }
        } else {
            out += Tip("weigh_in", TipKind.HABIT, 60, "Weigh in once a week", "Same day, after waking. Suggestions about your pace start after two weeks of weigh-ins.")
        }

        // ---------- Sleep ----------
        val sleep = db.journal().recent().first().filter { it.day in from..to }.mapNotNull { it.sleepHours }
        if (sleep.size >= 3 && sleep.average() < 6.5) {
            out += Tip(
                "sleep", TipKind.HABIT, 40, "Sleeping ${"%.1f".format(sleep.average())} hours on average",
                "Short sleep raises hunger hormones and cravings for sweets the next day. Even 30 more minutes helps.",
            )
        }

        return out.sortedBy { it.priority }
    }

    /** "Biggest sources: Rotli with ghee (480/day), Masala chai (240/day)." */
    private fun biggestSources(logs: List<FoodLog>, days: Int): String {
        val top = logs.groupBy { it.name }.mapValues { (_, l) -> l.sumOf { it.kcal } / days }
            .entries.sortedByDescending { it.value }.take(3)
        return "Biggest daily sources: " + top.joinToString(", ") { "${it.key} (${it.value.roundToInt()} kcal)" } +
            ". Trimming one serving of the top one usually closes most of the gap."
    }

    private fun proteinIdeas(gap: Int): String = buildList {
        add("1 katori Greek yogurt or hung curd (+15 g)")
        add("a katori of moong sprouts (+7 g)")
        add("2 boiled eggs (+13 g)")
        if (gap > 20) add("100 g paneer bhurji or tofu (+15–18 g)")
        if (gap > 30) add("a scoop of whey in milk (+24 g)")
    }.joinToString(", ")
}
