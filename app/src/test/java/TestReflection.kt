package com.example.remusic

import org.burnoutcrew.reorderable.ReorderableLazyListState
import kotlin.reflect.full.memberProperties

fun main() {
    ReorderableLazyListState::class.memberProperties.forEach {
        println(it.name)
    }
}
