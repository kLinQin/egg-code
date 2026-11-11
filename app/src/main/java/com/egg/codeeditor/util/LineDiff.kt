package com.egg.codeeditor.util

/** A single line in a unified diff view. */
data class DiffLine(val kind: Kind, val text: String, val newLineNo: Int?) {
    enum class Kind { SAME, ADD, REMOVE }
}

/**
 * Myers O(ND) line diff. Memory O((N+M)D) time, O(N+M) trace storage.
 * Adequate for files with tens of thousands of lines.
 */
fun computeLineDiff(oldText: String, newText: String): List<DiffLine> {
    val a = oldText.split('\n')
    val b = newText.split('\n')
    val n = a.size
    val m = b.size

    if (n == 0 && m == 0) return emptyList()
    if (n == 0) {
        val out = ArrayList<DiffLine>(m)
        for (j in 0 until m) out.add(DiffLine(DiffLine.Kind.ADD, b[j], j + 1))
        return out
    }
    if (m == 0) {
        val out = ArrayList<DiffLine>(n)
        for (i in 0 until n) out.add(DiffLine(DiffLine.Kind.REMOVE, a[i], null))
        return out
    }

    val max = n + m
    val vSize = 2 * max + 1
    val v = IntArray(vSize) { 0 }
    val trace = ArrayList<IntArray>()
    var foundD = -1
    outer@ for (d in 0..max) {
        trace.add(v.copyOf())
        var k = -d
        while (k <= d) {
            val ki = k + max
            var x = if (k == -d || (k != d && v[ki - 1] < v[ki + 1])) v[ki + 1] else v[ki - 1] + 1
            var y = x - k
            while (x < n && y < m && a[x] == b[y]) { x++; y++ }
            v[ki] = x
            if (x >= n && y >= m) { foundD = d; break@outer }
            k += 2
        }
    }

    val ops = ArrayList<Triple<Int, Int, DiffLine.Kind>>() // (x, y, kind)
    var x = n
    var y = m
    for (d in foundD downTo 1) {
        val vPrev = trace[d]
        val k = x - y
        val ki = k + max
        val prevK = if (k == -d || (k != d && vPrev[ki - 1] < vPrev[ki + 1])) k + 1 else k - 1
        val prevX = vPrev[prevK + max]
        val prevY = prevX - prevK

        while (x > prevX && y > prevY) {
            x--; y--
            ops.add(Triple(x, y, DiffLine.Kind.SAME))
        }
        if (d > 0) {
            if (x == prevX) {
                y--
                ops.add(Triple(x, y, DiffLine.Kind.ADD))
            } else {
                x--
                ops.add(Triple(x, y, DiffLine.Kind.REMOVE))
            }
        }
    }
    while (x > 0 && y > 0) {
        x--; y--
        ops.add(Triple(x, y, DiffLine.Kind.SAME))
    }
    while (x > 0) { x--; ops.add(Triple(x, y, DiffLine.Kind.REMOVE)) }
    while (y > 0) { y--; ops.add(Triple(x, y, DiffLine.Kind.ADD)) }

    ops.reverse()

    val out = ArrayList<DiffLine>(ops.size)
    var newNo = 0
    for ((px, py, kind) in ops) {
        when (kind) {
            DiffLine.Kind.SAME -> {
                newNo = py + 1
                out.add(DiffLine(kind, a[px], newNo))
            }
            DiffLine.Kind.ADD -> {
                newNo = py + 1
                out.add(DiffLine(kind, b[py], newNo))
            }
            DiffLine.Kind.REMOVE -> {
                out.add(DiffLine(kind, a[px], null))
            }
        }
    }
    return out
}
