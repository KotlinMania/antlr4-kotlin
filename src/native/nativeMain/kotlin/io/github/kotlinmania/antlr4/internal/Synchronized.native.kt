@file:OptIn(
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package io.github.kotlinmania.antlr4.internal

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.native.concurrent.ThreadLocal
import kotlin.native.identityHashCode

private const val MONITOR_STRIPE_MASK = 63
private const val MONITOR_STRIPE_COUNT = 64

@ThreadLocal
private val currentThreadMonitorToken = Any()

private val stripeOwners: Array<AtomicReference<Any?>> =
    Array(MONITOR_STRIPE_COUNT) { AtomicReference(null) }

private val stripeDepths: Array<AtomicInt> =
    Array(MONITOR_STRIPE_COUNT) { AtomicInt(0) }

@PublishedApi
internal fun enterNativeMonitor(lock: Any): Int {
    val stripe = lock.identityHashCode() and MONITOR_STRIPE_MASK
    val token = currentThreadMonitorToken
    val ownerRef = stripeOwners[stripe]
    if (ownerRef.load() === token) {
        stripeDepths[stripe].addAndFetch(1)
        return stripe
    }
    while (!ownerRef.compareAndSet(null, token)) {
        // Spin briefly on short DFA/PredictionContext critical sections.
    }
    stripeDepths[stripe].store(1)
    return stripe
}

@PublishedApi
internal fun exitNativeMonitor(stripe: Int) {
    val remaining = stripeDepths[stripe].addAndFetch(-1)
    if (remaining == 0) {
        stripeOwners[stripe].store(null)
    }
}

internal actual inline fun <T> synchronized(
    lock: Any,
    block: () -> T,
): T {
    val stripe = enterNativeMonitor(lock)
    try {
        return block()
    } finally {
        exitNativeMonitor(stripe)
    }
}
