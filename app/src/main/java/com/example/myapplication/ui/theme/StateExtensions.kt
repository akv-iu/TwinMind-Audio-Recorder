package com.example.myapplication.ui.theme



import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import kotlin.reflect.KProperty

// ───── Primitive State Delegates ─────
operator fun androidx.compose.runtime.FloatState.getValue(
    thisRef: Any?,
    property: KProperty<*>
): Float = value

operator fun androidx.compose.runtime.DoubleState.getValue(
    thisRef: Any?,
    property: KProperty<*>
): Double = value

operator fun androidx.compose.runtime.IntState.getValue(
    thisRef: Any?,
    property: KProperty<*>
): Int = value

operator fun androidx.compose.runtime.LongState.getValue(
    thisRef: Any?,
    property: KProperty<*>
): Long = value

// ───── Generic State<T> Delegate ─────
operator fun <T> State<T>.getValue(
    thisRef: Any?,
    property: KProperty<*>
): T = value

// ───── MutableState<T> (for var) ─────
operator fun <T> MutableState<T>.setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: T
) {
    this.value = value
}