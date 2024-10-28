package com.microsoft.maps.v9.toolsapp

import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import com.google.gson.JsonElement
import java.io.Serializable

data class Rgba(val r: Float, val g: Float, val b: Float, val a: Float) : Serializable {
    companion object {
        fun fromArray(rgba: Array<Float>): Rgba {
            if (rgba.size < 3 || rgba.size > 4) {
                throw IllegalArgumentException("only support 3-4 color components")
            }
            val (r, g, b) = rgba.take(3)
            val a = rgba.getOrElse(3) { 1.0f }
            return Rgba(r, g, b, a)
        }
    }

    override fun toString(): String {
        return "Rgba($r, $g, $b, $a)"
    }
}

fun JsonElement?.isString(): Boolean = (this as? JsonPrimitive)?.isString == true

fun JsonElement?.isFloat(): Boolean = (this as? JsonPrimitive)?.isNumber == true

fun JsonElement?.isRgba(): Boolean = (this as? JsonArray).run {
    this?.size() == 4 && this.all { it.isFloat() }
}

fun JsonElement?.getStringOrDefault(default: String? = null): String? =
    if (this.isString()) (this as JsonPrimitive).asString else default

fun JsonElement?.getFloatOrDefault(default: Float? = null): Float? =
    if (this.isFloat()) (this as JsonPrimitive).asFloat else default

fun JsonElement?.getRgbaOrDefault(default: Rgba? = null): Rgba? =
    if (this.isRgba()) Rgba.fromArray((this as JsonArray).map { it.asFloat }.toTypedArray()) else default

