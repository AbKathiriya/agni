package com.akash.agni.data

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything is stored in one canonical unit (kg for weight, inches for body measurements, cm for height)
 * so history, goals and Health Connect never mix units. These helpers convert only at the screen edge.
 */
enum class WeightUnit(val label: String, val perKg: Double, val step: Double) {
    KG("kg", 1.0, 2.5),
    LB("lb", 2.20462262, 5.0);

    fun fromKg(kg: Double) = kg * perKg
    fun toKg(value: Double) = value / perKg
    /** Plate jump for lifts: 2.5 kg or 5 lb, returned in kg. */
    val stepKg get() = toKg(step)
}

enum class LengthUnit(val label: String, val mark: String, val perInch: Double) {
    IN("in", "″", 1.0),
    CM("cm", " cm", 2.54);

    fun fromIn(inches: Double) = inches * perInch
    fun toIn(value: Double) = value / perInch
}

/** 2.0 -> "2", 2.54 -> "2.5" */
fun Double.oneDp(): String {
    val r = (this * 10).roundToInt() / 10.0
    return if (abs(r - r.roundToInt()) < 1e-9) r.roundToInt().toString() else r.toString()
}

fun Settings.weight(kg: Double): String = "${weightUnit.fromKg(kg).oneDp()} ${weightUnit.label}"

fun Settings.weightDelta(kg: Double): String {
    val v = weightUnit.fromKg(kg)
    val sign = if (v > 0.05) "+" else if (v < -0.05) "−" else ""
    return "$sign${abs(v).oneDp()} ${weightUnit.label}"
}

fun Settings.length(inches: Double): String = "${lengthUnit.fromIn(inches).oneDp()}${lengthUnit.mark}"

fun Settings.lengthDelta(inches: Double): String {
    val v = lengthUnit.fromIn(inches)
    val sign = if (v > 0.05) "+" else if (v < -0.05) "−" else ""
    return "$sign${abs(v).oneDp()}${lengthUnit.mark}"
}

/** Height in the chosen length unit: cm, or feet and inches. */
fun Settings.height(): String = when (lengthUnit) {
    LengthUnit.CM -> "${heightCm.oneDp()} cm"
    LengthUnit.IN -> {
        val totalIn = heightCm / 2.54
        val ft = (totalIn / 12).toInt()
        "$ft′ ${(totalIn - ft * 12).oneDp()}″"
    }
}
