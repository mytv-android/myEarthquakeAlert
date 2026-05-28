package com.github.mytv.myearthquakealert.ui.adaptive

import android.os.Build
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Conditional Modifier
// ---------------------------------------------------------------------------

/**
 * Conditionally applies [modifier] when [condition] is true, otherwise returns an empty Modifier.
 */
@Stable
fun Modifier.ifElse(condition: Boolean, modifier: Modifier): Modifier =
    if (condition) then(modifier) else this

/**
 * Conditionally applies [ifModifier] when [condition] is true, or [elseModifier] when false.
 */
@Stable
fun Modifier.ifElse(condition: Boolean, ifModifier: Modifier, elseModifier: Modifier): Modifier =
    if (condition) then(ifModifier) else then(elseModifier)

// ---------------------------------------------------------------------------
// Focus helpers
// ---------------------------------------------------------------------------

/**
 * Requests focus once when the composable is first launched.
 */
fun Modifier.focusOnLaunched(): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    focusRequester(focusRequester)
}

/**
 * Requests focus once when the composable is first launched, using [rememberSaveable]
 * so that focus is re-requested after configuration changes only if it hasn't been
 * restored by the saveable state.
 */
fun Modifier.focusOnLaunchedSaveable(): Modifier = composed {
    val focusRequester = rememberSaveable { FocusRequester() }
    var hasRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasRequested) {
            focusRequester.requestFocus()
            hasRequested = true
        }
    }
    focusRequester(focusRequester)
}

/**
 * Extension function to safely request focus, catching any exceptions
 * (e.g., when the focus requester is not yet attached to a composable).
 */
fun FocusRequester.tryRequestFocus() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
        // FocusRequester not yet attached to a focusable node
    }
}

/**
 * Saves the current focus within a group so it can be restored later.
 * Typically used at the parent level with [requestFocusRestorer] on children.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.saveFocusRestorer(): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    focusRequester(focusRequester)
        .focusRestorer(focusRequester)
}

/**
 * Restores focus to the saved focus requester from [saveFocusRestorer].
 * Use this on child composables that need to return focus to the parent's saved node.
 */
fun Modifier.requestFocusRestorer(focusRequester: FocusRequester): Modifier = composed {
    focusRequester(focusRequester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                focusRequester.requestFocus()
            }
        }
}

/**
 * Clears focus when the software keyboard is dismissed.
 */
fun Modifier.clearFocusOnKeyboardDismiss(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    var isKeyboardVisible by remember { mutableStateOf(false) }

    @OptIn(ExperimentalLayoutApi::class)
    val currentKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(currentKeyboardVisible) {
        val wasKeyboardVisible = isKeyboardVisible
        isKeyboardVisible = currentKeyboardVisible
        if (wasKeyboardVisible && !currentKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    this
}

// ---------------------------------------------------------------------------
// D-pad speed limiter
// ---------------------------------------------------------------------------

/**
 * Limits the rate at which D-pad key events are processed, preventing
 * rapid-fire navigation that can overwhelm the UI.
 *
 * @param intervalMs Minimum interval between processed key events in milliseconds.
 */
fun Modifier.dpadSpeedLimit(intervalMs: Long = 150L): Modifier = composed {
    var lastEventTime by remember { mutableStateOf(0L) }

    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            val isDpad = event.key == Key.DirectionUp ||
                    event.key == Key.DirectionDown ||
                    event.key == Key.DirectionLeft ||
                    event.key == Key.DirectionRight

            if (isDpad) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastEventTime
                if (elapsed < intervalMs) {
                    true // consume the event (too fast)
                } else {
                    lastEventTime = now
                    false // allow the event
                }
            } else {
                false
            }
        } else {
            false
        }
    }
}

// ---------------------------------------------------------------------------
// Sound effect helpers
// ---------------------------------------------------------------------------

/**
 * Maps Android [KeyEvent] keycode values to [View.FOCUS_*] direction constants
 * for use with [SoundEffectConstants].
 */
val toDirection: Map<Int, Int> = buildMap {
    put(KeyEvent.KEYCODE_DPAD_LEFT, View.FOCUS_LEFT)
    put(KeyEvent.KEYCODE_DPAD_RIGHT, View.FOCUS_RIGHT)
    put(KeyEvent.KEYCODE_DPAD_UP, View.FOCUS_UP)
    put(KeyEvent.KEYCODE_DPAD_DOWN, View.FOCUS_DOWN)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        @Suppress("DEPRECATION")
        put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT, View.FOCUS_LEFT)
        @Suppress("DEPRECATION")
        put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT, View.FOCUS_RIGHT)
        @Suppress("DEPRECATION")
        put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP, View.FOCUS_UP)
        @Suppress("DEPRECATION")
        put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN, View.FOCUS_DOWN)
    }
}

/**
 * Returns the [SoundEffectConstants] constant for a given focus direction.
 * Uses the API 31+ [SoundEffectConstants.getConstantForFocusDirection] when available,
 * falling back to the legacy [SoundEffectConstants.getContantForFocusDirection] (note the typo).
 */
fun getConstantForFocusDirection(direction: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SoundEffectConstants.getConstantForFocusDirection(direction, false)
    } else {
        @Suppress("DEPRECATION")
        SoundEffectConstants.getContantForFocusDirection(direction)
    }
}

// ---------------------------------------------------------------------------
// Clickable without ripple indication (for TV)
// ---------------------------------------------------------------------------

/**
 * A clickable modifier without ripple indication, suitable for TV interfaces
 * where ripple feedback is not appropriate. Provides haptic and sound feedback instead.
 *
 * @param onClick Callback invoked on tap.
 * @param role Semantic role for accessibility.
 * @param interactionSource Optional [MutableInteractionSource] to observe interactions.
 */
fun Modifier.clickableNoIndication(
    onClick: () -> Unit,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)

    pointerInput(Unit) {
        detectTapGestures {
            view.performHapticFeedback(HapticFeedbackConstantsCompat.KEYBOARD_TAP)
            currentOnClick()
        }
    }.combinedClickable(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstantsCompat.KEYBOARD_TAP)
            currentOnClick()
        },
        indication = null,
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
    )
}

// ---------------------------------------------------------------------------
// Back handler
// ---------------------------------------------------------------------------

/**
 * Handles the system Back button press.
 *
 * @param onBack Callback invoked when Back is pressed.
 */
@Composable
fun backHandler(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
}

/**
 * Handles the system Back button press only when [enabled] is true.
 *
 * @param enabled Whether the back handler is active.
 * @param onBack Callback invoked when Back is pressed and [enabled] is true.
 */
@Composable
fun backHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

// ---------------------------------------------------------------------------
// Low-level handleKeyEvents
// ---------------------------------------------------------------------------

/**
 * Low-level key event handler that maps raw Android keycodes to tap, long-press,
 * and continuous long-press callbacks.
 *
 * @param onKeyTap Map of keycode to callback for single-tap (key up) events. Null values mean the key is consumed but no action is taken.
 * @param onKeyLongTap Map of keycode to callback for long-press events.
 * @param onKeyContinuousLongTap Map of keycode to callback for continuous long-press (repeated) events.
 */
fun Modifier.handleKeyEvents(
    onKeyTap: Map<Int, (() -> Unit)?> = emptyMap(),
    onKeyLongTap: Map<Int, (() -> Unit)?> = emptyMap(),
    onKeyContinuousLongTap: Map<Int, (() -> Unit)?> = emptyMap(),
): Modifier = composed {
    var isLongPress by remember { mutableStateOf(false) }
    var longPressKey by remember { mutableStateOf(-1) }

    onPreviewKeyEvent { event ->
        val keyCode = event.nativeKeyEvent?.keyCode ?: return@onPreviewKeyEvent false

        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent?.repeatCount == 0) {
                    // First press
                    isLongPress = false
                    longPressKey = keyCode
                    false
                } else {
                    // Repeated key down (long press)
                    if (!isLongPress && longPressKey == keyCode) {
                        isLongPress = true
                        onKeyLongTap[keyCode]?.invoke()
                    }
                    if (isLongPress) {
                        onKeyContinuousLongTap[keyCode]?.invoke()
                    }
                    onKeyLongTap.containsKey(keyCode) || onKeyContinuousLongTap.containsKey(keyCode)
                }
            }

            KeyEventType.KeyUp -> {
                val consumed = if (!isLongPress && longPressKey == keyCode) {
                    onKeyTap[keyCode]?.invoke()
                    onKeyTap.containsKey(keyCode)
                } else {
                    isLongPress
                }
                isLongPress = false
                longPressKey = -1
                consumed
            }

            else -> false
        }
    }
}

// ---------------------------------------------------------------------------
// High-level handleKeyEvents
// ---------------------------------------------------------------------------

/**
 * High-level key event handler that maps directional and action keys to semantic callbacks.
 * This is the primary TV interaction modifier.
 *
 * @param onLeft Callback for D-pad left / channel down / media previous / page up / minus / A key.
 * @param onRight Callback for D-pad right / channel up / media next / page down / plus / D key.
 * @param onUp Callback for D-pad up / W key.
 * @param onDown Callback for D-pad down / S key.
 * @param onSelect Callback for D-pad center / enter / numpad enter (single press).
 * @param onLongSelect Callback for D-pad center / enter / numpad enter (long press).
 * @param onContinuousLongSelect Callback for D-pad center / enter / numpad enter (continuous long press).
 * @param onContinuousLongUp Callback for continuous long-press up.
 * @param onContinuousLongDown Callback for continuous long-press down.
 * @param onContinuousLongLeft Callback for continuous long-press left.
 * @param onContinuousLongRight Callback for continuous long-press right.
 * @param onSettings Callback for menu / settings / help / H / L key.
 * @param onNumber Callback for number keys (0-9). Receives the digit as Int.
 * @param clickableNoIndication If true, adds [clickableNoIndication] for tap support.
 * @param role Semantic role for accessibility (used with clickableNoIndication).
 * @param interactionSource Interaction source for observing focus/press state.
 */
fun Modifier.handleKeyEvents(
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onContinuousLongSelect: (() -> Unit)? = null,
    onContinuousLongUp: (() -> Unit)? = null,
    onContinuousLongDown: (() -> Unit)? = null,
    onContinuousLongLeft: (() -> Unit)? = null,
    onContinuousLongRight: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
    clickableNoIndication: Boolean = false,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val view = LocalView.current

    // Build keycode → action maps for the low-level handler
    val onKeyTap = mutableMapOf<Int, (() -> Unit)?>()
    val onKeyLongTap = mutableMapOf<Int, (() -> Unit)?>()
    val onKeyContinuousLongTap = mutableMapOf<Int, (() -> Unit)?>()

    // Directional keys
    fun addDirectional(
        keyCodes: List<Int>,
        onTap: (() -> Unit)?,
        onContinuous: (() -> Unit)?,
    ) {
        onTap?.let { action -> keyCodes.forEach { onKeyTap[it] = action } }
        onContinuous?.let { action -> keyCodes.forEach { onKeyContinuousLongTap[it] = action } }
    }

    // Left
    addDirectional(
        listOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
            KeyEvent.KEYCODE_A,
        ),
        onLeft, onContinuousLongLeft,
    )

    // Right
    addDirectional(
        listOf(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_PLUS,
            KeyEvent.KEYCODE_NUMPAD_ADD,
            KeyEvent.KEYCODE_D,
        ),
        onRight, onContinuousLongRight,
    )

    // Up
    addDirectional(
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_W,
        ),
        onUp, onContinuousLongUp,
    )

    // Down
    addDirectional(
        listOf(
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_S,
        ),
        onDown, onContinuousLongDown,
    )

    // Select (single press)
    onSelect?.let { action ->
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        ).forEach { onKeyTap[it] = action }
    }

    // Select (long press)
    onLongSelect?.let { action ->
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        ).forEach { onKeyLongTap[it] = action }
    }

    // Select (continuous long press)
    onContinuousLongSelect?.let { action ->
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        ).forEach { onKeyContinuousLongTap[it] = action }
    }

    // Settings
    onSettings?.let { action ->
        listOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_HELP,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_L,
        ).forEach { onKeyTap[it] = action }
    }

    // Number keys (0-9)
    onNumber?.let { numberCallback ->
        for (digit in 0..9) {
            val keyCode = KeyEvent.KEYCODE_0 + digit
            onKeyTap[keyCode] = { numberCallback(digit) }
        }
    }

    // Play sound on navigation
    val playNavigationSound: (Int) -> Unit = { keyCode ->
        toDirection[keyCode]?.let { direction ->
            val constant = getConstantForFocusDirection(direction)
            view.playSoundEffect(constant)
        }
    }

    var isLongPress by remember { mutableStateOf(false) }
    var longPressKey by remember { mutableStateOf(-1) }

    val base = this
        .onPreviewKeyEvent { event ->
            val keyCode = event.nativeKeyEvent?.keyCode ?: return@onPreviewKeyEvent false

            when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent?.repeatCount == 0) {
                        isLongPress = false
                        longPressKey = keyCode
                        false
                    } else {
                        if (!isLongPress && longPressKey == keyCode) {
                            isLongPress = true
                            onKeyLongTap[keyCode]?.invoke()
                        }
                        if (isLongPress) {
                            onKeyContinuousLongTap[keyCode]?.invoke()
                        }
                        onKeyLongTap.containsKey(keyCode) || onKeyContinuousLongTap.containsKey(keyCode)
                    }
                }

                KeyEventType.KeyUp -> {
                    val consumed = if (!isLongPress && longPressKey == keyCode) {
                        onKeyTap[keyCode]?.invoke()
                        playNavigationSound(keyCode)
                        onKeyTap.containsKey(keyCode)
                    } else {
                        isLongPress
                    }
                    isLongPress = false
                    longPressKey = -1
                    consumed
                }

                else -> false
            }
        }

    if (clickableNoIndication) {
        base.clickableNoIndication(
            onClick = { onSelect?.invoke() ?: Unit },
            role = role,
            interactionSource = interactionSource,
        )
    } else {
        base
    }
}

// ---------------------------------------------------------------------------
// Focus-gated handleKeyEvents
// ---------------------------------------------------------------------------

/**
 * Focus-gated key event handler. If the composable is not focused, requesting focus
 * takes priority over firing any callback. This prevents accidental key actions
 * on unfocused elements.
 *
 * @param isFocused Lambda returning whether the composable is currently focused.
 * @param focusRequester The [FocusRequester] used to request focus when not focused.
 * @param onLeft Callback for D-pad left (only fires when focused).
 * @param onRight Callback for D-pad right (only fires when focused).
 * @param onUp Callback for D-pad up (only fires when focused).
 * @param onDown Callback for D-pad down (only fires when focused).
 * @param onSelect Callback for select (only fires when focused).
 * @param onLongSelect Callback for long select (only fires when focused).
 * @param onContinuousLongSelect Callback for continuous long select (only fires when focused).
 * @param onContinuousLongUp Callback for continuous long-press up (only fires when focused).
 * @param onContinuousLongDown Callback for continuous long-press down (only fires when focused).
 * @param onContinuousLongLeft Callback for continuous long-press left (only fires when focused).
 * @param onContinuousLongRight Callback for continuous long-press right (only fires when focused).
 * @param onSettings Callback for settings (only fires when focused).
 * @param onNumber Callback for number keys (only fires when focused).
 * @param clickableNoIndication If true, adds [clickableNoIndication] for tap support.
 * @param role Semantic role for accessibility.
 * @param interactionSource Interaction source for observing focus/press state.
 */
fun Modifier.handleKeyEvents(
    isFocused: () -> Boolean,
    focusRequester: FocusRequester,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    onLongSelect: (() -> Unit)? = null,
    onContinuousLongSelect: (() -> Unit)? = null,
    onContinuousLongUp: (() -> Unit)? = null,
    onContinuousLongDown: (() -> Unit)? = null,
    onContinuousLongLeft: (() -> Unit)? = null,
    onContinuousLongRight: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onNumber: ((Int) -> Unit)? = null,
    clickableNoIndication: Boolean = false,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    // Wrap each callback with focus-gating
    fun gate(action: (() -> Unit)?): (() -> Unit)? = action?.let {
        {
            if (isFocused()) {
                it()
            } else {
                focusRequester.tryRequestFocus()
            }
        }
    }

    fun gateNumber(action: ((Int) -> Unit)?): ((Int) -> Unit)? = action?.let {
        { digit: Int ->
            if (isFocused()) {
                it(digit)
            } else {
                focusRequester.tryRequestFocus()
            }
        }
    }

    handleKeyEvents(
        onLeft = gate(onLeft),
        onRight = gate(onRight),
        onUp = gate(onUp),
        onDown = gate(onDown),
        onSelect = gate(onSelect),
        onLongSelect = gate(onLongSelect),
        onContinuousLongSelect = gate(onContinuousLongSelect),
        onContinuousLongUp = gate(onContinuousLongUp),
        onContinuousLongDown = gate(onContinuousLongDown),
        onContinuousLongLeft = gate(onContinuousLongLeft),
        onContinuousLongRight = gate(onContinuousLongRight),
        onSettings = gate(onSettings),
        onNumber = gateNumber(onNumber),
        clickableNoIndication = clickableNoIndication,
        role = role,
        interactionSource = interactionSource,
    )
}
