package com.jade.ai_photography_director

import java.io.File

/**
 * Optional local inspection of an already resized 64x64 ARGB numeric fixture. No image is saved.
 */
fun main(args: Array<String>) {
    val pixels = File(args[0]).readText().trim().split(" ").map { it.toLong().toInt() }.toIntArray()
    val map = SceneMap.fromPixels(pixels, 64)
    val f = Features(emptyList(), map.mean, 0f, 0f, null, 0f, .1f, .5f, map)
    println("mean=${map.mean}, deviation=${map.deviation}")
    for (p in ShotPlanner().rank(f, "Landscape", "Nearest third", .55f)) println(p)
    val engine = CompositionEngine()
    for (t in listOf(1000L, 1700L, 2400L)) println(
        engine.evaluate(f, "Landscape", "Nearest third", .55f, t)
    )
}
