package io.github.kotlinmania.antlr4

/**
 * Runtime assertion for ANTLR4 internal invariants.
 * Mirrors Java `assert` behavior - throws [IllegalStateException] on failure.
 */
internal fun assert(
    condition: Boolean,
    lazyMessage: () -> String = { "Assertion failed" },
) {
    if (!condition) {
        throw IllegalStateException(lazyMessage())
    }
}
