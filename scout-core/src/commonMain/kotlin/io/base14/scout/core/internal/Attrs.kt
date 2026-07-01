package io.base14.scout.core.internal

import io.opentelemetry.kotlin.attributes.AttributesMutator

fun AttributesMutator.putAny(key: String, value: Any) {
    when (value) {
        is String -> setStringAttribute(key, value)
        is Boolean -> setBooleanAttribute(key, value)
        is Int -> setLongAttribute(key, value.toLong())
        is Long -> setLongAttribute(key, value)
        is Double -> setDoubleAttribute(key, value)
        is Float -> setDoubleAttribute(key, value.toDouble())
        else -> setStringAttribute(key, value.toString())
    }
}
