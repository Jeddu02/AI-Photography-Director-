package com.jade.ai_photography_director

import kotlin.system.measureNanoTime

/** Measures crop ranking only. Run on a host JVM or an Android test harness. */
fun main() {
    val values =
        FloatArray(4096) { i ->
            val x = (i % 64 + .5f) / 64
            val y = (i / 64 + .5f) / 64
            if (kotlin.math.abs(x - .5f) < (y - .33f) * .7f) .72f else .22f + x * .35f
        }
    val map = SceneMap(64, values, values, values)
    val features = Features(emptyList(), map.mean, 0f, 0f, null, 0f, 0f, 0f, map)
    val planner = ShotPlanner()
    fun run() = check(planner.rank(features, "Street", "Nearest third", .55f).isNotEmpty())
    repeat(30) { run() }
    val samples = List(150) { measureNanoTime { run() } / 1e6 }.sorted()
    println("150 crop rankings after 30 warm-ups, 64×64 synthetic converging edges")
    println("median %.2f ms, p95 %.2f ms".format(java.util.Locale.US, samples[74], samples[142]))
}
