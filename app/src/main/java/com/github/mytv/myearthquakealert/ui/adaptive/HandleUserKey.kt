package com.github.mytv.myearthquakealert.ui.adaptive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent

fun Modifier.handleUserKey(
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
): Modifier = this
    .focusable()
    .onFocusChanged { state ->
        onFocusChanged?.invoke(state.isFocused)
    }
    .clickable { onConfirm() }
    .onKeyEvent { event ->
        when (event.key) {
            Key.Enter, Key.NumPadEnter -> {
                onConfirm()
                true
            }
            Key.Back, Key.Escape -> {
                onDismiss?.invoke()
                onDismiss != null
            }
            else -> false
        }
    }
