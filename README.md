# Agni — personal fitness app

Kotlin + Jetpack Compose, Android 8.0+ (minSdk 26). Built for one person: no accounts, no server, everything stays on the phone.

## Build and install

1. Install the latest **Android Studio** and open this folder (File › Open › `agni`).
2. Let Gradle sync. If Studio offers to upgrade the Android Gradle Plugin or libraries, see "Versions" below before accepting.
3. On your phone: Settings › About phone › Software information › tap **Build number** 7 times, then Settings › Developer options › turn on **USB debugging** (or Wireless debugging).
4. Plug the phone in, pick it in the device dropdown and press **Run**.

For a standalone APK: Build › Build App Bundle(s) / APK(s) › Build APK(s). The release build is signed with the debug key so it installs directly.

## First launch

1. Today › **Connect Samsung Health** › allow all Health Connect permissions.
2. In **Samsung Health**: Settings › Health Connect › allow sharing of steps, heart rate, active calories, exercise and weight.
3. Progress › **Log weight & measurements** once to set your baseline (waist at the navel, hips and thigh at the widest point).
4. Rewards › tap each milestone and name your own reward.

## What's in it

| Area | What it does |
| --- | --- |
| Today | Calories left (ring), weekly calorie bank toward your treat, streak + freezes, protein, steps, weight, today's workout |
| Workout | Weekly plan: Mon lower + core, Wed upper + conditioning, Fri full body + glutes, Tue/Thu incline walk, Sat long walk, Sun rest |
| Player | Animated demo (ExerciseDB GIF) + YouTube button, form cues, reps/weight steppers pre-filled from last session, progression hint (+2.5 kg when you hit the top of the range on every set), auto rest timer with vibration, live-ish heart rate from your watch, keeps the screen on |
| Food | 101 foods (Gujarati, South Indian, eating out, eggs/protein, basics) in home portions (piece, katori, plate); quick meals ("My usual lunch"), frequent foods, custom foods, past days |
| Progress | Weight trend, waist/hips/thigh changes, journal with energy and sleep |
| Rewards | XP + levels, weekly calorie bank, 4-week streak calendar, milestone rewards you define |
| Settings | Light/dark/system theme, Health Connect, calorie target (Mifflin-St Jeor, desk job, −500/day by default), protein and step goals, weekly treat |

### How the motivation system works

- **Weekly calorie bank**: every day you log food and stay under target, the difference is banked (Mon–Sun). When the bank reaches your treat's calories (default: Saturday thali, 400 kcal), the treat fits your week with no damage.
- **Streak**: a day is on track when food is logged (2+ items), calories are within target (or covered by the bank), and the workout is done on training days. Today never breaks the streak until it's over.
- **Freezes**: start with 1, earn 1 per 7 on-track days (max 3). A missed day uses a freeze instead of resetting the streak.
- **XP**: workout 60 (walk days 30), logging 3+ items 10, on-track day 20, protein goal 10, step goal 10, journal 5, weekly measurements 20, every 7-day streak 50, milestone unlocked 50.

## Things to know

- **Heart rate during workouts**: Samsung Health syncs Galaxy Watch data into Health Connect in batches, so the player's heart rate can lag a few minutes behind the watch. Start a "Weight training" workout on the watch at the same time for the most accurate numbers; the session summary reads average heart rate and calories from Health Connect afterward. Truly live heart rate would need a small Wear OS companion app.
- **Exercise GIFs** come from the free ExerciseDB API. I couldn't confirm its exact endpoint while building, so the lookup tries two known URLs and falls back to the YouTube button if neither answers. If GIFs never show, update `endpoints` in `data/ExerciseMediaRepository.kt`.
- **Food values are home-cooking averages.** Your mom's thepla may be oilier than mine. Adjust any value in `app/src/main/assets/foods.json` before first launch, or add a custom food in the app.
- **Versions**: libraries are pinned (AGP 8.7.3, Kotlin 2.1.0, Health Connect `1.1.0-alpha10`). Newer Health Connect releases changed how record `Metadata` is created; if you upgrade it, replace `Metadata(clientRecordId = …)` with `Metadata.manualEntry(clientRecordId = …)` and add `metadata = Metadata.manualEntry()` to the weight and exercise records in `health/HealthConnectManager.kt`.
- This was written without an Android SDK available to compile it, so expect a few small fixes on the first build. Paste any build error back to me.

## Changing things

- Workout plan and exercises: `data/Training.kt`
- Food list: `assets/foods.json` (seeded on first launch only; uninstall/clear data to reseed)
- XP values, streak and bank rules: `domain/Engine.kt`
- Colors: `ui/theme/Theme.kt`
- App icon: `res/drawable/ic_launcher_foreground.xml`

## Not built yet

Progress photos, barcode scanning, reminders/notifications, a Wear OS companion for live heart rate, data export.
