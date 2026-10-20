# Agni — personal fitness app

Kotlin + Jetpack Compose, Android 8.0+ (minSdk 26). Built for one person: no accounts, no server, everything stays on the phone.

## Build and install

1. Install the latest **Android Studio** and open this folder (File › Open › `agni`).
2. Let Gradle sync. If Studio offers to upgrade the Android Gradle Plugin or libraries, see "Versions" below before accepting.
3. On your phone: Settings › About phone › Software information › tap **Build number** 7 times, then Settings › Developer options › turn on **USB debugging** (or Wireless debugging).
4. Plug the phone in, pick it in the device dropdown and press **Run**.

For a standalone APK: Build › Build App Bundle(s) / APK(s) › Build APK(s).

## Cloud builds and signing

Every push to `main` builds an APK and a Play bundle (.aab) on GitHub Actions and publishes them as a release. Builds are signed with `signing/agni-upload.jks` when the repo secret `AGNI_KEYSTORE_PASSWORD` is set (Settings › Secrets and variables › Actions). Keep the same key forever: Android only installs an update over an existing app when the signature matches, and Google Play uses this key as the upload key.

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
| Player | Start/end position photos that alternate like a slow animation (bundled, work offline) + in-app YouTube player, form cues, reps/weight steppers pre-filled from last session, progression hint (+2.5 kg when you hit the top of the range on every set), auto rest timer with vibration, live-ish heart rate from your watch, keeps the screen on |
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
- **Exercise photos** come from [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (public domain) and are bundled in `assets/exercises`, so they work offline at the gym. Bird dog has no photo; use the Video button.
- **Video button** opens YouTube's mobile site inside the app on a search for that exercise. Tap a result to play it; close to return to your set.
- **Food values are home-cooking averages.** Your mom's thepla may be oilier than mine. Adjust any value in `app/src/main/assets/foods.json` before first launch, or add a custom food in the app.
- **Versions**: libraries are pinned (AGP 8.7.3, Kotlin 2.1.0, Health Connect `1.1.0-alpha10`). Newer Health Connect releases changed how record `Metadata` is created; if you upgrade it, replace `Metadata(clientRecordId = …)` with `Metadata.manualEntry(clientRecordId = …)` and add `metadata = Metadata.manualEntry()` to the weight and exercise records in `health/HealthConnectManager.kt`.
- This was written without an Android SDK available to compile it, so expect a few small fixes on the first build. Paste any build error back to me.

## Coach

Today shows the top suggestions; tap **See all** for the full list. Suggestions are fixed rules over the last 7 finished days: calories vs target (with your biggest calorie sources), protein gap with vegetarian/egg ideas, carb- and fat-heavy days (framed as swaps), farsan/treats, sweet chai, heavy dinners, weekend spikes, strength sessions, steps, weight-loss pace (too fast / plateau) and sleep from the journal. Rules live in `domain/Coach.kt`.

## Units

Settings › Units switches body and lifting weight between kg and lb, and height and measurements between inches and cm. The check-in dialog has its own unit chips, converts anything already typed and remembers your choice. Everything is stored as kg / inches / cm underneath, so switching never changes your history.

## Watch workouts and steps

Workouts recorded on the Galaxy Watch (or anything else that writes to Health Connect) are imported into Workout › History with duration, calories, average heart rate, steps and distance. Each one is imported once; deleting it here hides it without touching Samsung Health. If it overlaps a workout run in Agni, its watch stats are merged into that workout instead of creating a duplicate. An imported session counts for the day's plan when it lasts 20+ minutes, and on strength days only when it's a strength-type session. The Workout tab also shows the last 7 days of steps.

## Updates

The app checks GitHub for a newer build when you open Today, and Settings has a "Check for updates" button. Tap **Update now**, then **Install**. The first time, Android asks you to allow Agni to install apps; allow it, come back and tap Install again. Your data is kept.

## Changing things

- Workout plan and exercises: `data/Training.kt`
- Food list: `assets/foods.json` (seeded on first launch only; uninstall/clear data to reseed)
- XP values, streak and bank rules: `domain/Engine.kt`
- Colors: `ui/theme/Theme.kt`
- App icon: `res/drawable/ic_launcher_foreground.xml`

## Not built yet

Progress photos, barcode scanning, reminders/notifications, a Wear OS companion for live heart rate, data export.
