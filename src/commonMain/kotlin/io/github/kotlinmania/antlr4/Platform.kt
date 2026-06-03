package io.github.kotlinmania.antlr4

class Platform {
    val name: String = "Kotlin Multiplatform"
}

fun currentPlatformName(): String = Platform().name
