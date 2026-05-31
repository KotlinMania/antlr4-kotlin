package io.github.kotlinmania.antlr4.misc

class Pair<A, B>(
    val a: A?,
    val b: B?,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        } else if (other !is Pair<*, *>) {
            return false
        }
        return AnyEqualityComparator.INSTANCE.equals(a, other.a) &&
            AnyEqualityComparator.INSTANCE.equals(b, other.b)
    }

    override fun hashCode(): Int {
        var hash: Int = MurmurHash.initialize()
        hash = MurmurHash.update(hash, a)
        hash = MurmurHash.update(hash, b)
        return MurmurHash.finish(hash, 2)
    }

    override fun toString(): String = "($a, $b)"
}
