package com.akash.agni.data

import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate

enum class Kind { REPS, TIMED, CARDIO }

data class Exercise(
    val id: String,
    val name: String,
    val muscles: String,
    val equipment: String,
    val cues: List<String>,
    /** Folder in assets/exercises (start and end photos, from the public-domain free-exercise-db). */
    val imageId: String?,
) {
    /** Two frames (start, end position) that the player alternates to show the movement. */
    val frames: List<String>
        get() = imageId?.let { listOf("file:///android_asset/exercises/$it/0.jpg", "file:///android_asset/exercises/$it/1.jpg") }.orEmpty()

    val youtubeUrl: String
        get() = "https://m.youtube.com/results?search_query=" +
            URLEncoder.encode("$name proper form tutorial", "UTF-8")
}

/** One exercise inside a workout. Reps for REPS, seconds per set for TIMED, total seconds for CARDIO. */
data class Block(
    val exerciseId: String,
    val kind: Kind = Kind.REPS,
    val sets: Int = 3,
    val repsMin: Int = 0,
    val repsMax: Int = 0,
    val seconds: Int = 0,
    val perSide: Boolean = false,
    val restSec: Int = 60,
) {
    val exercise get() = Exercises.byId.getValue(exerciseId)
    val prescription: String
        get() = when (kind) {
            Kind.REPS -> "$sets × " + (if (repsMax > repsMin) "$repsMin–$repsMax" else "$repsMin") + if (perSide) " / side" else ""
            Kind.TIMED -> "$sets × ${seconds}s" + if (perSide) " / side" else ""
            Kind.CARDIO -> "${seconds / 60} min"
        }
}

data class Workout(
    val id: String,
    val title: String,
    val minutes: Int,
    val xp: Int,
    val blocks: List<Block>,
    val cardioOnly: Boolean = false,
) {
    val summary get() = "$minutes min · ${blocks.size} " + (if (blocks.size == 1) "block" else "exercises") + " · +$xp XP"
}

object Exercises {
    val all = listOf(
        Exercise("bike_warmup", "Easy bike warm-up", "Warm-up", "Stationary bike",
            listOf("Easy pace: you can talk in full sentences", "Raise cadence slowly over 5 minutes"), "Bicycling_Stationary"),
        Exercise("goblet_squat", "Goblet squat", "Quads, glutes, core", "Dumbbell",
            listOf("Hold the dumbbell at your chest, elbows down", "Sit down between your heels, chest up", "Knees track over your toes"), "Goblet_Squat"),
        Exercise("db_rdl", "Romanian deadlift", "Hamstrings, glutes", "Dumbbells",
            listOf("Soft knees, push your hips back", "Dumbbells slide close to your legs", "Stop at a hamstring stretch with a flat back"), "Romanian_Deadlift"),
        Exercise("walking_lunge", "Walking lunge", "Quads, glutes", "Dumbbells",
            listOf("Long step, back knee drops toward the floor", "Front knee stays over mid-foot", "Push through the front heel"), "Dumbbell_Lunges"),
        Exercise("cable_pull_through", "Cable pull-through", "Glutes, hamstrings", "Cable machine + rope",
            listOf("Rope on the low pulley, face away and hold it between your legs", "Push your hips back with soft knees, back flat", "Drive your hips forward and squeeze your glutes; arms just hang"), "Pull_Through"),
        Exercise("leg_curl", "Seated leg curl", "Hamstrings", "Machine",
            listOf("Pad just above your heels", "Curl fully, control the way back"), "Seated_Leg_Curl"),
        Exercise("leg_press", "Leg press", "Quads, glutes", "Machine",
            listOf("Feet shoulder width, middle of the platform", "Lower until knees are near 90°, back stays on the pad", "Don't lock your knees at the top"), "Leg_Press"),
        Exercise("step_up", "Dumbbell step-up", "Glutes, quads", "Dumbbells + bench",
            listOf("Whole foot on the box", "Drive up with the front leg, don't push off the back one"), "Dumbbell_Step_Ups"),
        Exercise("glute_bridge", "Glute bridge", "Glutes", "Bodyweight",
            listOf("Feet close to your hips", "Squeeze glutes and hold 1 second at the top"), "Butt_Lift_Bridge"),
        Exercise("lat_pulldown", "Lat pulldown", "Back, biceps", "Cable",
            listOf("Pull the bar to your upper chest", "Drive elbows down and back", "Don't lean back much"), "Wide-Grip_Lat_Pulldown"),
        Exercise("db_bench", "Dumbbell bench press", "Chest, shoulders, triceps", "Dumbbells + bench",
            listOf("Shoulder blades pinched together", "Lower to chest level, elbows about 45°"), "Dumbbell_Bench_Press"),
        Exercise("cable_row", "Seated cable row", "Back", "Cable",
            listOf("Sit tall, pull to your lower ribs", "Squeeze shoulder blades, slow return"), "Seated_Cable_Rows"),
        Exercise("db_shoulder_press", "Seated shoulder press", "Shoulders", "Dumbbells",
            listOf("Brace your core, don't arch", "Press up and slightly in"), "Dumbbell_Shoulder_Press"),
        Exercise("db_row", "One-arm dumbbell row", "Back", "Dumbbell + bench",
            listOf("Flat back, hand and knee on the bench", "Pull your elbow toward your hip"), "One-Arm_Dumbbell_Row"),
        Exercise("incline_pushup", "Incline push-up", "Chest, triceps, core", "Bench",
            listOf("Body in one straight line", "Lower chest to the bench edge, push away"), "Incline_Push-Up"),
        Exercise("dead_bug", "Dead bug", "Core", "Mat",
            listOf("Lower back pressed into the floor", "Extend opposite arm and leg slowly", "Exhale as you extend"), "Dead_Bug"),
        Exercise("plank", "Plank", "Core", "Mat",
            listOf("Elbows under shoulders", "Squeeze glutes, ribs down", "Keep breathing"), "Plank"),
        Exercise("side_plank", "Side plank", "Obliques", "Mat",
            listOf("Hips high, body in a straight line", "Stack or stagger your feet"), "Side_Bridge"),
        Exercise("pallof_press", "Pallof press", "Core, obliques", "Cable or band",
            listOf("Stand side-on to the cable", "Press straight out and resist the twist", "Hold 2 seconds"), "Pallof_Press"),
        Exercise("bird_dog", "Bird dog", "Core, glutes", "Mat",
            listOf("Reach long, don't rotate your hips", "Pause at full extension"), null),
        Exercise("bike_intervals", "Bike intervals", "Conditioning", "Stationary bike",
            listOf("30 seconds hard, 60 seconds easy", "Hard means you can't talk"), "Bicycling_Stationary"),
        Exercise("stair_climber", "Stair climber", "Conditioning, glutes, legs", "Stair climber",
            listOf("Steady pace you can hold for 8 minutes", "Stand upright, hands light on the rails, no leaning", "Push through the whole foot to work the glutes"), "Stairmaster"),
        Exercise("incline_walk", "Incline treadmill walk", "Fat burning (zone 2)", "Treadmill",
            listOf("Incline 8–12%, speed 4.5–5.5 km/h", "Don't hold the rails", "You can talk, but not sing"), "Walking_Treadmill"),
        Exercise("free_activity", "Your activity", "Fat burning, fun", "Whatever you're playing",
            listOf("Badminton, cricket, swimming, a hike: anything that keeps you moving for 30+ minutes counts",
                "Start a workout on your watch so the calories and heart rate come into Agni",
                "No watch? Log it from the Workout tab afterwards"), null),
        Exercise("long_walk", "Brisk outdoor walk", "Fat burning (zone 2)", "None",
            listOf("Brisk pace, swing your arms", "Good one to do with family"), "Trail_Running_Walking"),
    )
    val byId = all.associateBy { it.id }
}

object Plan {
    private val warmup = Block("bike_warmup", Kind.CARDIO, sets = 1, seconds = 300, restSec = 0)

    val lowerCore = Workout(
        "lower_core", "Lower body + core", 45, 60, listOf(
            warmup,
            Block("goblet_squat", repsMin = 10, repsMax = 12, restSec = 75),
            Block("db_rdl", repsMin = 10, repsMax = 12, restSec = 75),
            Block("walking_lunge", sets = 2, repsMin = 10, perSide = true, restSec = 75),
            Block("cable_pull_through", repsMin = 12, repsMax = 15, restSec = 75),
            Block("leg_curl", sets = 2, repsMin = 12, repsMax = 15, restSec = 60),
            Block("dead_bug", sets = 2, repsMin = 8, perSide = true, restSec = 45),
            Block("plank", Kind.TIMED, sets = 3, seconds = 30, restSec = 45),
        )
    )

    val upperConditioning = Workout(
        "upper_cond", "Upper body + conditioning", 45, 60, listOf(
            warmup,
            Block("lat_pulldown", repsMin = 10, repsMax = 12, restSec = 75),
            Block("db_bench", repsMin = 8, repsMax = 12, restSec = 90),
            Block("cable_row", repsMin = 10, repsMax = 12, restSec = 75),
            Block("db_shoulder_press", sets = 2, repsMin = 10, repsMax = 12, restSec = 75),
            Block("pallof_press", sets = 2, repsMin = 10, perSide = true, restSec = 45),
            Block("bike_intervals", Kind.CARDIO, sets = 1, seconds = 600, restSec = 0),
        )
    )

    val fullBody = Workout(
        "full_body", "Full body + glutes", 45, 60, listOf(
            warmup,
            Block("leg_press", repsMin = 12, repsMax = 15, restSec = 75),
            Block("step_up", sets = 2, repsMin = 10, perSide = true, restSec = 75),
            Block("db_row", repsMin = 10, perSide = true, restSec = 75),
            Block("incline_pushup", repsMin = 8, repsMax = 12, restSec = 60),
            Block("glute_bridge", sets = 2, repsMin = 15, restSec = 45),
            Block("side_plank", Kind.TIMED, sets = 2, seconds = 25, perSide = true, restSec = 45),
            Block("stair_climber", Kind.CARDIO, sets = 1, seconds = 480, restSec = 0),
        )
    )

    val inclineWalk = Workout(
        "incline_walk", "Incline walk", 35, 30,
        listOf(Block("incline_walk", Kind.CARDIO, sets = 1, seconds = 1800, restSec = 0)), cardioOnly = true
    )

    val longWalk = Workout(
        "long_walk", "Long walk", 50, 30,
        listOf(Block("long_walk", Kind.CARDIO, sets = 1, seconds = 2700, restSec = 0)), cardioOnly = true
    )

    /** A day you replaced with your own sport or activity. The title is set per day (e.g. "Badminton"). */
    val activity = Workout(
        "activity", "Your activity", 45, 30,
        listOf(Block("free_activity", Kind.CARDIO, sets = 1, seconds = 2700, restSec = 0)), cardioOnly = true
    )

    val all = listOf(lowerCore, upperConditioning, fullBody, inclineWalk, longWalk, activity)
    val byId = all.associateBy { it.id }

    /** What a day in the schedule can be: a plan workout id, "activity:<name>", or "rest". */
    fun valueOf(w: Workout?): String = when {
        w == null -> REST
        w.id == activity.id -> "activity:" + w.title
        else -> w.id
    }

    fun fromValue(value: String): Workout? = when {
        value == REST -> null
        value.startsWith("activity:") -> activity.copy(title = value.removePrefix("activity:").ifBlank { "Your activity" })
        else -> byId[value]
    }

    /** The plan for a specific date, after any changes you made (swaps, activities, rest). */
    fun forDate(date: LocalDate, overrides: Map<Long, String>): Workout? {
        val v = overrides[date.toEpochDay()] ?: return forDay(date.dayOfWeek)
        val known = v == REST || v.startsWith("activity:") || byId.containsKey(v)
        return if (known) fromValue(v) else forDay(date.dayOfWeek)
    }

    const val REST = "rest"

    /** null = rest day. */
    fun forDay(day: DayOfWeek): Workout? = when (day) {
        DayOfWeek.MONDAY -> lowerCore
        DayOfWeek.TUESDAY -> inclineWalk
        DayOfWeek.WEDNESDAY -> upperConditioning
        DayOfWeek.THURSDAY -> inclineWalk
        DayOfWeek.FRIDAY -> fullBody
        DayOfWeek.SATURDAY -> longWalk
        DayOfWeek.SUNDAY -> null
    }
}
