package io.base14.scout.ios

internal fun demangleSwiftClassName(raw: String): String? {
    if (!raw.startsWith("_Tt")) return null
    var i = 3
    var last: String? = null
    while (i < raw.length) {
        if (!raw[i].isDigit()) {
            i++
            continue
        }
        var len = 0
        while (i < raw.length && raw[i].isDigit()) {
            len = len * 10 + (raw[i] - '0')
            i++
        }
        if (len <= 0 || i + len > raw.length) break
        last = raw.substring(i, i + len)
        i += len
    }
    return last
}

internal fun cleanScreenName(raw: String): String {
    val name = demangleSwiftClassName(raw) ?: raw.substringAfterLast('.')
    return name.ifEmpty { raw.substringAfterLast('.') }
}
