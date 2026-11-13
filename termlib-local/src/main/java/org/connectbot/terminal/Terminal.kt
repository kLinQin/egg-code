/*
 * ConnectBot Terminal
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.connectbot.terminal

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.text.TextPaint
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.ceil

private val DRAW_TEXT_BUFFER = ThreadLocal.withInitial { CharArray(1) }
private val CURLY_UNDERLINE_PATH = ThreadLocal.withInitial { Path() }

/**
 * Gesture type for unified gesture handling state machine.
 */
private enum class GestureType {
    Undetermined,
    Scroll,
    Selection,
    Zoom,
    HandleDrag,
}

/**
 * The rate at which the cursor blinks in milliseconds when enabled.
 */
private const val CURSOR_BLINK_RATE_MS = 500L

/**
 * Amount of time to wait for second touch to detect multitouch gesture in milliseconds.
 */
private const val WAIT_FOR_SECOND_TOUCH_MS = 40L
private const val AUTO_SCROLL_FRAME_INTERVAL = 4

/**
 * Text selection magnifier loupe size in dp.
 */
private const val MAGNIFIER_SIZE_DP = 100

/**
 * How much to scale up the text in the magnifier loupe.
 */
private const val MAGNIFIER_SCALE = 2.5f

/**
 * Delay in milliseconds before showing the IME (Input Method Editor).
 */
private const val IME_SHOW_DELAY_MS = 100L

/**
 * Delay in milliseconds to allow UI to settle before requesting focus.
 */
private const val UI_SETTLE_DELAY_MS = 100L

/**
 * Delay in milliseconds before showing the soft keyboard.
 */
private const val KEYBOARD_SHOW_DELAY_MS = 50L

/**
 * Border width for the terminal display in dp.
 */
private val TERMINAL_BORDER_WIDTH = 2.dp

/**
 * Minimum zoom scale for pinch-to-zoom gesture.
 */
private const val MIN_ZOOM_SCALE = 0.5f

/**
 * Maximum zoom scale for pinch-to-zoom gesture.
 */
private const val MAX_ZOOM_SCALE = 3f

/**
 * Size of the copy button when selection is active in dp.
 */
private val COPY_BUTTON_SIZE = 48.dp

/**
 * Vertical offset for the copy button above the selection in dp.
 */
private val COPY_BUTTON_OFFSET = 48.dp

/**
 * Touch radius in pixels for detecting selection handle touches.
 */
private const val HANDLE_HIT_RADIUS = 80f

/**
 * Vertical offset in dp to position the magnifier above (or below) the finger.
 */
private val MAGNIFIER_VERTICAL_OFFSET = 40.dp

/**
 * Estimated finger contact height in dp, used to avoid positioning the magnifier under the finger.
 */
private val FINGER_HEIGHT_DP = 50.dp

/**
 * Center offset multiplier for magnifier positioning.
 */
private const val MAGNIFIER_CENTER_OFFSET_MULTIPLIER = 1.2f

/**
 * Border width for the magnifier loupe in dp.
 */
private val MAGNIFIER_BORDER_WIDTH = 2.dp

/**
 * Background alpha for the magnifier loupe (0.0 = transparent, 1.0 = opaque).
 */
private const val MAGNIFIER_BACKGROUND_ALPHA = 0.9f

/**
 * Number of rows to display on each side of the touch point in the magnifier.
 */
private const val MAGNIFIER_ROW_RANGE = 3

/**
 * Width of selection handles (teardrop shape) in dp.
 */
private val SELECTION_HANDLE_WIDTH = 24.dp

/**
 * Alpha value for the block cursor.
 */
private const val CURSOR_BLOCK_ALPHA = 0.7f

/**
 * Alpha value for the underline and bar cursors.
 */
private const val CURSOR_LINE_ALPHA = 0.9f

/**
 * Percentage of cell height for underline cursor (0.0 to 1.0).
 */
private const val CURSOR_UNDERLINE_HEIGHT_RATIO = 0.15f

/**
 * Percentage of cell width for bar cursor (0.0 to 1.0).
 */
private const val CURSOR_BAR_WIDTH_RATIO = 0.15f

/**
 * Convergence threshold for binary search when finding optimal font size.
 */
private const val FONT_SIZE_SEARCH_EPSILON = 0.1f

/**
 * Number of wavelengths per character width for the curly underline pattern.
 */
private const val CURLY_UNDERLINE_CYCLES_PER_CHAR = 2f

/**
 * Amplitude (height) of the curly underline pattern in pixels.
 */
private const val CURLY_UNDERLINE_AMPLITUDE = 1.5f

/**
 * Spacing between the two lines in a double underline in pixels.
 */
private const val DOUBLE_UNDERLINE_SPACING = 2f

/**
 * Terminal - A Jetpack Compose terminal screen component.
 *
 * This component:
 * - Renders terminal output using Canvas
 * - Handles terminal resize based on available space
 * - Displays cursor
 * - Supports colors, bold, italic, underline, etc.
 *
 * @param terminalEmulator The terminal emulator containing terminal state
 * @param modifier Modifier for the composable
 * @param typeface Typeface for terminal text (default: Typeface.MONOSPACE)
 * @param initialFontSize Initial font size for terminal text (can be changed with pinch-to-zoom)
 * @param minFontSize Minimum font size for pinch-to-zoom
 * @param maxFontSize Maximum font size for pinch-to-zoom
 * @param backgroundColor Default background color
 * @param foregroundColor Default foreground color
 * @param keyboardEnabled Enable keyboard input handling (default: false for display-only mode).
 *                        When false, no keyboard input (hardware or soft) is accepted.
 * @param showSoftKeyboard Whether to show the soft keyboard/IME (default: true when keyboardEnabled=true).
 *                         Only applies when keyboardEnabled=true. Hardware keyboard always works when keyboardEnabled=true.
 * @param focusRequester Focus requester for keyboard input (if enabled)
 * @param onTerminalTap Callback for a simple tap event on the terminal (when no selection is active)
 * @param onImeVisibilityChanged Callback invoked when IME visibility changes (true = shown, false = hidden)
 * @param forcedSize Force terminal to specific dimensions (rows, cols). When set, font size is calculated to fit.
 * @param onSelectionControllerAvailable Optional callback providing access to the SelectionController for controlling selection mode
 * @param onHyperlinkClick Callback when user taps on an OSC8 hyperlink. Receives the URL as parameter.
 * @param selectionBackgroundColor Background color for selected text (default: 0xFFB3D7FF)
 * @param selectionForegroundColor Foreground color for selected text (default: Black)
 */
@Composable
fun Terminal(
    terminalEmulator: TerminalEmulator,
    modifier: Modifier = Modifier,
    typeface: Typeface = Typeface.MONOSPACE,
    initialFontSize: TextUnit = 11.sp,
    minFontSize: TextUnit = 6.sp,
    maxFontSize: TextUnit = 30.sp,
    backgroundColor: Color = Color.Black,
    foregroundColor: Color = Color.White,
    selectionBackgroundColor: Color = Color(0xFFB3D7FF),
    selectionForegroundColor: Color = Color.Black,
    keyboardEnabled: Boolean = false,
    showSoftKeyboard: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onTerminalTap: () -> Unit = {},
    onImeVisibilityChanged: (Boolean) -> Unit = {},
    forcedSize: Pair<Int, Int>? = null,
    modifierManager: ModifierManager? = null,
    onSelectionControllerAvailable: ((SelectionController) -> Unit)? = null,
    onHyperlinkClick: (String) -> Unit = {},
    onComposeControllerAvailable: ((ComposeController) -> Unit)? = null,
    onPasteRequest: (() -> Unit)? = null,
    rightAltMode: RightAltMode = RightAltMode.CharacterModifier,
    delKeyMode: DelKeyMode = DelKeyMode.Delete,
) {
    if (LocalInspectionMode.current) {
        TerminalPreview(modifier, backgroundColor, foregroundColor)
        return
    }

    TerminalWithAccessibility(
        terminalEmulator = terminalEmulator,
        modifier = modifier,
        typeface = typeface,
        initialFontSize = initialFontSize,
        minFontSize = minFontSize,
        maxFontSize = maxFontSize,
        backgroundColor = backgroundColor,
        foregroundColor = foregroundColor,
        keyboardEnabled = keyboardEnabled,
        showSoftKeyboard = showSoftKeyboard,
        focusRequester = focusRequester,
        onTerminalTap = onTerminalTap,
        onImeVisibilityChanged = onImeVisibilityChanged,
        forcedSize = forcedSize,
        modifierManager = modifierManager,
        onSelectionControllerAvailable = onSelectionControllerAvailable,
        onHyperlinkClick = onHyperlinkClick,
        onComposeControllerAvailable = onComposeControllerAvailable,
        onScrollControllerAvailable = null,
        onPasteRequest = onPasteRequest,
        rightAltMode = rightAltMode,
        selectionBackgroundColor = selectionBackgroundColor,
        selectionForegroundColor = selectionForegroundColor,
        delKeyMode = delKeyMode,
    )
}

/**
 * Used for testing accessibility.
 *
 * @see Terminal
 */
@VisibleForTesting
@Composable
internal fun TerminalWithAccessibility(
    terminalEmulator: TerminalEmulator,
    modifier: Modifier = Modifier,
    typeface: Typeface = Typeface.MONOSPACE,
    initialFontSize: TextUnit = 11.sp,
    minFontSize: TextUnit = 6.sp,
    maxFontSize: TextUnit = 30.sp,
    backgroundColor: Color = Color.Black,
    foregroundColor: Color = Color.White,
    keyboardEnabled: Boolean = false,
    showSoftKeyboard: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onTerminalTap: () -> Unit = {},
    onImeVisibilityChanged: (Boolean) -> Unit = {},
    forcedSize: Pair<Int, Int>? = null,
    modifierManager: ModifierManager? = null,
    forceAccessibilityEnabled: Boolean? = null,
    onSelectionControllerAvailable: ((SelectionController) -> Unit)? = null,
    onHyperlinkClick: (String) -> Unit = {},
    onComposeControllerAvailable: ((ComposeController) -> Unit)? = null,
    onScrollControllerAvailable: ((ScrollController) -> Unit)? = null,
    onPasteRequest: (() -> Unit)? = null,
    rightAltMode: RightAltMode = RightAltMode.CharacterModifier,
    selectionBackgroundColor: Color = Color(0xFFB3D7FF),
    selectionForegroundColor: Color = Color.Black,
    delKeyMode: DelKeyMode = DelKeyMode.Delete,
) {
    if (terminalEmulator !is TerminalEmulatorImpl) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Text("Unknown TerminalEmulator type")
        }
        return
    }

    val terminalEmulator: TerminalEmulatorImpl = terminalEmulator


    val currentOnTerminalTap by rememberUpdatedState(onTerminalTap)
    val currentOnHyperlinkClick by rememberUpdatedState(onHyperlinkClick)

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()


    val systemAccessibilityEnabled by rememberAccessibilityState()
    val accessibilityEnabled = forceAccessibilityEnabled ?: systemAccessibilityEnabled


    val screenState = rememberTerminalScreenState(terminalEmulator)


    val keyboardHandler = remember(terminalEmulator) {
        KeyboardHandler(terminalEmulator, modifierManager)
    }


    var zoomScale by remember(terminalEmulator) { mutableStateOf(1f) }
    var zoomOffset by remember(terminalEmulator) { mutableStateOf(Offset.Zero) }
    var zoomOrigin by remember(terminalEmulator) { mutableStateOf(TransformOrigin.Center) }
    var isZooming by remember(terminalEmulator) { mutableStateOf(false) }
    var isUserScrolling by remember(terminalEmulator) { mutableStateOf(false) }
    var isDraggingHandle by remember(terminalEmulator) { mutableStateOf(false) }
    var calculatedFontSize by remember(terminalEmulator) { mutableStateOf(initialFontSize) }


    var showMagnifier by remember(terminalEmulator) { mutableStateOf(false) }
    var magnifierPosition by remember(terminalEmulator) { mutableStateOf(Offset.Zero) }


    var cursorBlinkVisible by remember(terminalEmulator) { mutableStateOf(true) }


    val imeFocusRequester = remember { FocusRequester() }


    var isReviewMode by remember(terminalEmulator) { mutableStateOf(false) }
    val reviewFocusRequester = remember { FocusRequester() }





    val shouldShowIme = keyboardEnabled && showSoftKeyboard


    var imeInputView by remember { mutableStateOf<ImeInputView?>(null) }


    DisposableEffect(imeInputView) {
        onDispose {
            Log.d("Terminal", "Disposing Terminal - hiding IME")
            imeInputView?.hideIme()
        }
    }


    LaunchedEffect(shouldShowIme, imeInputView) {
        Log.d("Terminal", "IME state changed: shouldShowIme=$shouldShowIme (imeInputView=$imeInputView)")

        imeInputView?.let { view ->
            if (shouldShowIme) {
                Log.d("Terminal", "Showing IME via InputMethodManager")
                delay(IME_SHOW_DELAY_MS)
                view.showIme()
                Log.d("Terminal", "IME show completed")
                onImeVisibilityChanged(true)
            } else {
                Log.d("Terminal", "Hiding IME via InputMethodManager")
                view.hideIme()
                Log.d("Terminal", "IME hide completed")
                onImeVisibilityChanged(false)
            }
        }
    }


    LaunchedEffect(isReviewMode) {
        if (isReviewMode) {

            keyboardController?.hide()
            delay(UI_SETTLE_DELAY_MS)
            try {
                reviewFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {

            }
        } else {

            if (keyboardEnabled && shouldShowIme) {
                delay(UI_SETTLE_DELAY_MS)
                imeFocusRequester.requestFocus()
                delay(KEYBOARD_SHOW_DELAY_MS)
                keyboardController?.show()
            }
        }
    }


    LaunchedEffect(screenState) {
        snapshotFlow {
            val s = screenState.snapshot
            Triple(s.cursorVisible, s.cursorBlink, s.cursorRow to s.cursorCol)
        }.collectLatest { (visible, blink, _) ->
            if (visible) {
                cursorBlinkVisible = true
                if (blink) {

                    while (true) {
                        delay(CURSOR_BLINK_RATE_MS)
                        cursorBlinkVisible = !cursorBlinkVisible
                    }
                }
            } else {
                cursorBlinkVisible = false
            }
        }
    }


    val textPaint = remember(typeface, calculatedFontSize) {
        TextPaint().apply {
            this.typeface = typeface
            textSize = with(density) { calculatedFontSize.toPx() }
            isAntiAlias = true
        }
    }


    val baseCharWidth = remember(textPaint) {
        textPaint.measureText("M")
    }



    val baseCharHeight = remember(textPaint) {
        val metrics = textPaint.fontMetrics
        val height = ceil(metrics.descent - metrics.ascent)
        if (height <= 0f) 20f else height
    }

    val baseCharBaseline = remember(textPaint) {
        ceil(-textPaint.fontMetrics.ascent)
    }
    val underlinePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
    }


    val scrollOffset = remember(terminalEmulator) { Animatable(0f) }
    val maxScroll by remember(terminalEmulator, baseCharHeight) {
        derivedStateOf { screenState.snapshot.scrollback.size * baseCharHeight }
    }
    LaunchedEffect(maxScroll) {
        scrollOffset.updateBounds(0f, maxScroll)
    }


    val selectionManager = remember(terminalEmulator) {
        SelectionManager()
    }


    val selectionController = remember(terminalEmulator, selectionManager, clipboardManager, screenState) {
        object : SelectionController {
            override val isSelectionActive: Boolean
                get() = selectionManager.mode != SelectionMode.NONE

            override fun startSelection(mode: SelectionMode) {
                if (selectionManager.mode == SelectionMode.NONE) {

                    val row = screenState.snapshot.cursorRow.coerceIn(0, screenState.snapshot.rows - 1)
                    val col = screenState.snapshot.cursorCol.coerceIn(0, screenState.snapshot.cols - 1)
                    selectionManager.startSelection(row, col, screenState.snapshot.cols, mode, screenState.snapshot, screenState.scrollbackPosition)
                }
            }

            override fun toggleSelection() {
                if (selectionManager.mode == SelectionMode.NONE) {
                    startSelection()
                } else {
                    clearSelection()
                }
            }

            override fun moveSelectionUp() {
                selectionManager.moveSelectionUp(screenState.snapshot.rows)
            }

            override fun moveSelectionDown() {
                selectionManager.moveSelectionDown(screenState.snapshot.rows)
            }

            override fun moveSelectionLeft() {
                selectionManager.moveSelectionLeft(screenState.snapshot.cols)
            }

            override fun moveSelectionRight() {
                selectionManager.moveSelectionRight(screenState.snapshot.cols)
            }

            override fun toggleSelectionMode() {
                selectionManager.toggleMode(screenState.snapshot.cols, screenState.snapshot, screenState.scrollbackPosition)
            }

            override fun setSelectionMode(mode: SelectionMode) {
                selectionManager.setMode(mode, screenState.snapshot.cols, screenState.snapshot, screenState.scrollbackPosition)
            }

            override fun selectAll() {
                selectionManager.selectAll(screenState.snapshot.rows, screenState.snapshot.cols)
            }

            override fun finishSelection() {
                selectionManager.endSelection()
            }

            override fun copySelection(): String {
                val text = selectionManager.getSelectedText(screenState.snapshot, screenState.scrollbackPosition)
                if (text.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(text))
                    selectionManager.clearSelection()
                }
                return text
            }

            override fun clearSelection() {
                selectionManager.clearSelection()
            }
        }
    }


    val composeMode = remember(terminalEmulator) {
        ComposeMode()
    }


    val composeController = remember(terminalEmulator, composeMode, keyboardHandler, selectionController) {
        object : ComposeController {
            override val isComposeModeActive: Boolean
                get() = composeMode.isActive

            override fun startComposeMode() {
                selectionController.clearSelection()
                composeMode.activate()
            }

            override fun stopComposeMode() {


                composeMode.commit()?.codePoints()?.forEach { codepoint ->
                    terminalEmulator.dispatchCharacter(0, codepoint)
                }
                composeMode.deactivate()
            }

            override fun toggleComposeMode() {
                if (composeMode.isActive) {
                    stopComposeMode()
                } else {
                    startComposeMode()
                }
            }

            override fun getComposedText(): String = composeMode.buffer

            override val pendingDeadChar: Int
                get() = keyboardHandler.pendingDeadChar
        }
    }


    LaunchedEffect(composeMode) {
        keyboardHandler.composeMode = composeMode
    }


    LaunchedEffect(selectionController) {
        keyboardHandler.selectionController = selectionController
        onSelectionControllerAvailable?.invoke(selectionController)
    }


    LaunchedEffect(keyboardHandler, rightAltMode, delKeyMode) {
        keyboardHandler.rightAltMode = rightAltMode
        keyboardHandler.delKeyMode = delKeyMode
    }


    LaunchedEffect(composeController) {
        onComposeControllerAvailable?.invoke(composeController)
    }


    val scrollController = remember(screenState, scrollOffset, scope, maxScroll) {
        object : ScrollController {
            override val scrollbackPosition: Int
                get() = screenState.scrollbackPosition

            override val maxScrollback: Int
                get() = screenState.snapshot.scrollback.size

            override fun scrollToBottom() {
                screenState.scrollToBottom()
                scope.launch {
                    scrollOffset.snapTo(0f)
                }
            }

            override fun scrollToTop() {
                screenState.scrollToTop()
                scope.launch {
                    scrollOffset.snapTo(maxScroll)
                }
            }

            override fun scrollBy(lines: Int) {
                val newPosition = (screenState.scrollbackPosition + lines)
                    .coerceIn(0, maxScrollback)
                screenState.scrollBy(newPosition - screenState.scrollbackPosition)
                scope.launch {
                    scrollOffset.snapTo(newPosition * baseCharHeight)
                }
            }
        }
    }


    LaunchedEffect(scrollController) {
        onScrollControllerAvailable?.invoke(scrollController)
    }



    LaunchedEffect(composeMode.isActive, imeInputView) {
        imeInputView?.isComposeModeActive = composeMode.isActive
    }


    val coroutineScope = rememberCoroutineScope()



    val tapTracker = remember(terminalEmulator) {
        object {
            var lastTimestamp = 0L
            var lastPosition = Offset.Zero
        }
    }


    LaunchedEffect(screenState, scrollOffset) {
        keyboardHandler.onInputProcessed = {
            screenState.scrollToBottom()
            coroutineScope.launch {
                scrollOffset.snapTo(0f)
            }
        }
    }
    val viewConfiguration = LocalViewConfiguration.current

    var availableWidth by remember { mutableStateOf(0) }
    var availableHeight by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                availableWidth = it.width
                availableHeight = it.height
            }
            .then(
                if (keyboardEnabled) {
                    Modifier
                        .focusable()
                        .onPreviewKeyEvent { event ->

                            if (isReviewMode) {

                                when (event.key) {
                                    Key.DirectionUp,
                                    Key.DirectionDown,
                                    Key.DirectionLeft,
                                    Key.DirectionRight,
                                    Key.PageUp,
                                    Key.PageDown,
                                    -> false


                                    else -> {

                                        isReviewMode = false
                                        keyboardHandler.onKeyEvent(event)
                                    }
                                }
                            } else {

                                keyboardHandler.onKeyEvent(event)
                            }
                        }
                } else {
                    Modifier
                },
            ),
    ) {

        if (forcedSize != null) {
            val (forcedRows, forcedCols) = forcedSize
            LaunchedEffect(availableWidth, availableHeight, forcedRows, forcedCols) {
                if (availableWidth == 0 || availableHeight == 0) {
                    return@LaunchedEffect
                }

                val optimalSize = findOptimalFontSize(
                    targetRows = forcedRows,
                    targetCols = forcedCols,
                    availableWidth = availableWidth,
                    availableHeight = availableHeight,
                    minSize = minFontSize.value,
                    maxSize = maxFontSize.value,
                    typeface = typeface,
                    density = density.density,
                )
                calculatedFontSize = optimalSize.sp
            }
        } else {

            LaunchedEffect(initialFontSize) {
                if (calculatedFontSize != initialFontSize) {
                    calculatedFontSize = initialFontSize
                }
            }
        }


        LaunchedEffect(terminalEmulator, availableWidth, availableHeight, forcedSize, baseCharWidth, baseCharHeight) {
            if (availableWidth == 0 || availableHeight == 0 || baseCharWidth <= 0f || baseCharHeight <= 0f) {
                return@LaunchedEffect
            }


            val newCols =
                forcedSize?.second ?: charsPerDimension(availableWidth, baseCharWidth)
            val newRows =
                forcedSize?.first ?: charsPerDimension(availableHeight, baseCharHeight)

            val dimensions = terminalEmulator.dimensions
            if (newRows != dimensions.rows || newCols != dimensions.columns) {
                terminalEmulator.resize(newRows, newCols)




                if (selectionManager.mode != SelectionMode.NONE) {
                    selectionManager.clampToDimensions(newRows, newCols)
                }
            }
        }


        val newCols =
            forcedSize?.second ?: charsPerDimension(availableWidth, baseCharWidth)
        val newRows =
            forcedSize?.first ?: charsPerDimension(availableHeight, baseCharHeight)


        LaunchedEffect(screenState) {
            snapshotFlow { screenState.snapshot.lines.size to screenState.snapshot.scrollback.size }
                .collect {

                    if (screenState.scrollbackPosition == 0) {
                        screenState.scrollToBottom()
                        scrollOffset.snapTo(0f)
                    }
                }
        }


        LaunchedEffect(screenState.scrollbackPosition) {
            val targetOffset = screenState.scrollbackPosition * baseCharHeight
            if (!isUserScrolling && !scrollOffset.isRunning && scrollOffset.value != targetOffset) {
                scrollOffset.snapTo(targetOffset)
            }
        }


        val terminalWidthPx = newCols * baseCharWidth
        val terminalHeightPx = newRows * baseCharHeight


        Box(
            modifier = (
                if (forcedSize != null && !isZooming && zoomScale == 1f) {

                    Modifier
                        .size(
                            width = with(density) { terminalWidthPx.toDp() },
                            height = with(density) { terminalHeightPx.toDp() },
                        )
                        .border(
                            width = TERMINAL_BORDER_WIDTH,
                            color = Color(0xFF4CAF50).copy(alpha = 0.6f),
                        )
                } else {
                    Modifier.fillMaxSize()
                }
                ).pointerInput(terminalEmulator, baseCharWidth, baseCharHeight) {
                val touchSlopSquared =
                    viewConfiguration.touchSlop * viewConfiguration.touchSlop
                var scrollJob: Job? = null
                coroutineScope {
                    awaitEachGesture {
                        var gestureType: GestureType = GestureType.Undetermined
                        val down = awaitFirstDown(requireUnconsumed = false)
                        scrollJob?.cancel()


                        val isDoubleTap = (down.uptimeMillis - tapTracker.lastTimestamp) < viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - tapTracker.lastPosition).getDistanceSquared() < touchSlopSquared

                        if (isDoubleTap) {
                            gestureType = GestureType.Selection
                            val tapCol = (down.position.x / baseCharWidth).toInt()
                                .coerceIn(0, screenState.snapshot.cols - 1)
                            val tapRow = (down.position.y / baseCharHeight).toInt()
                                .coerceIn(0, screenState.snapshot.rows - 1)
                            selectionManager.startSelection(tapRow, tapCol, screenState.snapshot.cols, SelectionMode.WORD, screenState.snapshot, screenState.scrollbackPosition)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMagnifier = true
                            magnifierPosition = down.position
                            tapTracker.lastTimestamp = 0L
                        }


                        if (gestureType == GestureType.Undetermined && selectionManager.mode != SelectionMode.NONE && !selectionManager.isSelecting) {
                            val range = selectionManager.selectionRange
                            if (range != null) {
                                val (touchingStart, touchingEnd) = isTouchingHandle(
                                    down.position,
                                    range,
                                    baseCharWidth,
                                    baseCharHeight,
                                )
                                if (touchingStart || touchingEnd) {
                                    gestureType = GestureType.HandleDrag
                                    isDraggingHandle = true

                                    showMagnifier = true
                                    magnifierPosition = down.position


                                    var isMovingStart = touchingStart
                                    var autoScrollCooldown = 0

                                    try {
                                        drag(down.id) { change ->
                                            val newCol =
                                                (change.position.x / baseCharWidth).toInt()
                                                    .coerceIn(0, screenState.snapshot.cols - 1)
                                            val newRow =
                                                (change.position.y / baseCharHeight).toInt()
                                                    .coerceIn(0, screenState.snapshot.rows - 1)





                                            val fingerY = change.position.y
                                            val targetScrollback = when {
                                                fingerY < 0f -> screenState.scrollbackPosition + 1
                                                fingerY > availableHeight.toFloat() -> screenState.scrollbackPosition - 1
                                                else -> screenState.scrollbackPosition
                                            }.coerceIn(0, screenState.snapshot.scrollback.size)
                                            val scrollDelta = targetScrollback - screenState.scrollbackPosition

                                            if (scrollDelta != 0 && autoScrollCooldown <= 0) {
                                                val currentRange = selectionManager.selectionRange
                                                if (currentRange != null) {
                                                    selectionManager.updateSelectionStart(
                                                        currentRange.startRow + scrollDelta,
                                                        currentRange.startCol,
                                                    )
                                                    selectionManager.updateSelectionEnd(
                                                        currentRange.endRow + scrollDelta,
                                                        currentRange.endCol,
                                                    )
                                                }
                                                scrollController.scrollBy(scrollDelta)
                                                autoScrollCooldown = AUTO_SCROLL_FRAME_INTERVAL
                                            } else if (scrollDelta == 0) {
                                                autoScrollCooldown = 0
                                            }
                                            autoScrollCooldown--

                                            val current = selectionManager.selectionRange
                                            if (current != null) {
                                                val result = applyHandleDrag(
                                                    startRow = current.startRow,
                                                    startCol = current.startCol,
                                                    endRow = current.endRow,
                                                    endCol = current.endCol,
                                                    isMovingStart = isMovingStart,
                                                    newRow = newRow,
                                                    newCol = newCol,
                                                )
                                                isMovingStart = result.isMovingStart
                                                selectionManager.updateSelectionStart(result.startRow, result.startCol)
                                                selectionManager.updateSelectionEnd(result.endRow, result.endCol)
                                                selectionManager.adjustSelectionForMode(
                                                    screenState.snapshot.cols,
                                                    screenState.snapshot,
                                                    screenState.scrollbackPosition,
                                                )
                                            }

                                            magnifierPosition = change.position
                                            change.consume()
                                        }
                                    } finally {
                                        isDraggingHandle = false
                                    }


                                    selectionManager.adjustSelectionForMode(
                                        screenState.snapshot.cols,
                                        screenState.snapshot,
                                        screenState.scrollbackPosition,
                                    )

                                    showMagnifier = false

                                    return@awaitEachGesture
                                }
                            }
                        }



                        var longPressDetected = false
                        var gestureEnded = false
                        val longPressJob = if (gestureType == GestureType.Undetermined) {
                            launch {
                                delay(viewConfiguration.longPressTimeoutMillis)

                                if (gestureType == GestureType.Undetermined &&
                                    selectionManager.mode == SelectionMode.NONE &&
                                    !gestureEnded
                                ) {
                                    longPressDetected = true
                                    gestureType = GestureType.Selection


                                    val col = (down.position.x / baseCharWidth).toInt()
                                        .coerceIn(0, screenState.snapshot.cols - 1)
                                    val row = (down.position.y / baseCharHeight).toInt()
                                        .coerceIn(0, screenState.snapshot.rows - 1)
                                    selectionManager.startSelection(
                                        row,
                                        col,
                                        screenState.snapshot.cols,
                                        SelectionMode.CHARACTER,
                                    )
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMagnifier = true
                                    magnifierPosition = down.position
                                }
                            }
                        } else {
                            null
                        }


                        val velocityTracker = VelocityTracker()
                        val primaryPointerId = down.id
                        velocityTracker.addPosition(down.uptimeMillis, down.position)

                        var secondPointer: PointerInputChange? = null
                        val multiTouchTimeout = down.uptimeMillis + WAIT_FOR_SECOND_TOUCH_MS
                        var panAccumulator = Offset.Zero
                        var initialScrollOffset = 0f


                        try {
                            while (true) {
                                val event: PointerEvent =
                                    awaitPointerEvent(PointerEventPass.Main)


                                val change =
                                    event.changes.find { it.id == primaryPointerId } ?: event.changes.first()



                                velocityTracker.addPointerInputChange(change)

                                val dragAmount = change.positionChange()
                                panAccumulator += dragAmount



                                if (gestureType == GestureType.Undetermined && change.uptimeMillis <= multiTouchTimeout && event.changes.size > 1) {
                                    secondPointer =
                                        event.changes.firstOrNull { it.id != primaryPointerId && it.pressed }
                                    if (secondPointer != null) break
                                }


                                if (gestureType == GestureType.Undetermined && !longPressDetected) {

                                    val isGracePeriod = change.uptimeMillis <= multiTouchTimeout

                                    if (!isGracePeriod && panAccumulator.getDistanceSquared() > touchSlopSquared) {
                                        longPressJob?.cancel()
                                        gestureType = GestureType.Scroll
                                        isUserScrolling = true

                                        initialScrollOffset = scrollOffset.value - panAccumulator.y

                                        if (selectionManager.mode != SelectionMode.NONE) {
                                            selectionManager.clearSelection()
                                        }
                                    }
                                }


                                when (gestureType) {
                                    GestureType.Selection -> {
                                        if (selectionManager.isSelecting) {
                                            val dragCol =
                                                (change.position.x / baseCharWidth).toInt()
                                                    .coerceIn(0, screenState.snapshot.cols - 1)
                                            val dragRow =
                                                (change.position.y / baseCharHeight).toInt()
                                                    .coerceIn(0, screenState.snapshot.rows - 1)
                                            selectionManager.updateSelection(
                                                dragRow,
                                                dragCol,
                                            )
                                            selectionManager.adjustSelectionForMode(
                                                screenState.snapshot.cols,
                                                screenState.snapshot,
                                                screenState.scrollbackPosition,
                                            )
                                            magnifierPosition = change.position
                                        }
                                    }

                                    GestureType.Scroll -> {


                                        val currentMaxScroll =
                                            screenState.snapshot.scrollback.size * baseCharHeight
                                        val newOffset = (initialScrollOffset + panAccumulator.y)
                                            .coerceIn(0f, currentMaxScroll)



                                        scrollJob?.cancel()
                                        scrollJob = launch {
                                            scrollOffset.snapTo(newOffset)
                                        }


                                        val scrolledLines =
                                            (newOffset / baseCharHeight).toInt()
                                        screenState.scrollBy(scrolledLines - screenState.scrollbackPosition)
                                    }

                                    else -> {}
                                }

                                if (event.changes.all { !it.pressed }) break
                                change.consume()
                            }
                        } finally {
                            isUserScrolling = false
                        }


                        if (secondPointer != null) {
                            longPressJob?.cancel()
                            gestureType = GestureType.Zoom


                            isZooming = true

                            val centerX = (down.position.x + secondPointer.position.x) / 2f
                            val centerY = (down.position.y + secondPointer.position.y) / 2f
                            zoomOrigin = TransformOrigin(
                                pivotFractionX = centerX / size.width,
                                pivotFractionY = centerY / size.height,
                            )

                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break

                                if (event.changes.size > 1) {
                                    val gestureZoom = event.calculateZoom()
                                    val gesturePan = event.calculatePan()

                                    val oldScale = zoomScale
                                    val newScale =
                                        (oldScale * gestureZoom).coerceIn(
                                            MIN_ZOOM_SCALE,
                                            MAX_ZOOM_SCALE,
                                        )

                                    zoomOffset += gesturePan
                                    zoomScale = newScale

                                    event.changes.forEach { it.consume() }
                                }
                            }


                            isZooming = false
                            zoomScale = 1f
                            zoomOffset = Offset.Zero

                            return@awaitEachGesture
                        }


                        gestureEnded = true
                        longPressJob?.cancel()

                        when (gestureType) {
                            GestureType.Scroll -> {

                                val velocity = velocityTracker.calculateVelocity()
                                scrollJob?.cancel()
                                scrollJob = launch {
                                    scrollOffset.animateDecay(
                                        initialVelocity = velocity.y,
                                        animationSpec = splineBasedDecay(density),
                                    ) {

                                        val scrolledLines =
                                            (value / baseCharHeight).toInt()
                                        screenState.scrollBy(scrolledLines - screenState.scrollbackPosition)
                                    }
                                }
                            }

                            GestureType.Selection -> {
                                showMagnifier = false
                                if (selectionManager.isSelecting) {
                                    selectionManager.endSelection()
                                }
                            }

                            GestureType.Undetermined -> {


                                if (selectionManager.mode != SelectionMode.NONE) {
                                    selectionManager.clearSelection()
                                } else {

                                    val tapCol = (down.position.x / baseCharWidth).toInt()
                                        .coerceIn(0, screenState.snapshot.cols - 1)
                                    val tapRow = (down.position.y / baseCharHeight).toInt()
                                        .coerceIn(0, screenState.snapshot.rows - 1)
                                    val hyperlinkUrl = screenState.getHyperlinkUrlAt(
                                        tapRow,
                                        tapCol,
                                        terminalEmulator.autoDetectUrls,
                                    )

                                    if (hyperlinkUrl != null) {

                                        currentOnHyperlinkClick(hyperlinkUrl)
                                    } else {

                                        if (keyboardEnabled) {
                                            focusRequester.requestFocus()
                                        }
                                        currentOnTerminalTap()
                                    }
                                }

                                tapTracker.lastTimestamp = down.uptimeMillis
                                tapTracker.lastPosition = down.position
                            }

                            else -> {}
                        }
                    }
                }
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .clearAndSetSemantics {

                    }
                    .graphicsLayer {
                        translationX = zoomOffset.x * zoomScale
                        translationY = zoomOffset.y * zoomScale
                        scaleX = zoomScale
                        scaleY = zoomScale
                        transformOrigin = zoomOrigin
                    },
            ) {
                TerminalRows(
                    screenState = screenState,
                    charWidth = baseCharWidth,
                    charHeight = baseCharHeight,
                    charBaseline = baseCharBaseline,
                    textPaint = textPaint,
                    underlinePaint = underlinePaint,
                    defaultFg = foregroundColor,
                    defaultBg = backgroundColor,
                    autoDetectUrls = terminalEmulator.autoDetectUrls,
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val snapshot = screenState.snapshot

                    if (selectionManager.mode != SelectionMode.NONE) {
                        for (row in 0 until snapshot.rows) {
                            val line = screenState.getVisibleLine(row)
                            drawLine(
                                line = line,
                                row = row,
                                charWidth = baseCharWidth,
                                charHeight = baseCharHeight,
                                charBaseline = baseCharBaseline,
                                textPaint = textPaint,
                                underlinePaint = underlinePaint,
                                defaultFg = foregroundColor,
                                defaultBg = backgroundColor,
                                selectionManager = selectionManager,
                                autoDetectUrls = terminalEmulator.autoDetectUrls,
                                selectionBackgroundColor = selectionBackgroundColor,
                                selectionForegroundColor = selectionForegroundColor,
                                selectedOnly = true,
                            )
                        }
                    }


                    if (snapshot.cursorVisible && screenState.scrollbackPosition == 0 && cursorBlinkVisible) {
                        drawCursor(
                            row = snapshot.cursorRow,
                            col = snapshot.cursorCol,
                            charWidth = baseCharWidth,
                            charHeight = baseCharHeight,
                            foregroundColor = foregroundColor,
                            backgroundColor = backgroundColor,
                            cursorShape = snapshot.cursorShape,
                            pendingDeadChar = composeController.pendingDeadChar,
                            charBaseline = baseCharBaseline,
                            textPaint = textPaint,
                        )
                    }


                    if (composeMode.isActive && screenState.scrollbackPosition == 0) {
                        drawComposeOverlay(
                            buffer = composeMode.buffer,
                            cursorRow = snapshot.cursorRow,
                            cursorCol = snapshot.cursorCol,
                            totalCols = snapshot.cols,
                            charWidth = baseCharWidth,
                            charHeight = baseCharHeight,
                            charBaseline = baseCharBaseline,
                            textPaint = textPaint,
                        )
                    }


                    if (selectionManager.mode != SelectionMode.NONE && !selectionManager.isSelecting) {
                        val range = selectionManager.selectionRange
                        if (range != null) {
                            val startPosition = range.getStartPosition()
                            drawSelectionHandle(
                                row = startPosition.first,
                                col = startPosition.second,
                                charWidth = baseCharWidth,
                                charHeight = baseCharHeight,
                                pointingDown = false,
                            )

                            val endPosition = range.getEndPosition()
                            drawSelectionHandle(
                                row = endPosition.first,
                                col = endPosition.second,
                                charWidth = baseCharWidth,
                                charHeight = baseCharHeight,
                                pointingDown = true,
                            )
                        }
                    }
                }
            }

            if (accessibilityEnabled) {
                AccessibilityOverlay(
                    screenState = screenState,
                    charHeight = baseCharHeight,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(reviewFocusRequester)
                        .focusable(),
                    onToggleReviewMode = { isReviewMode = !isReviewMode },
                    isReviewMode = isReviewMode,
                )

                if (!isReviewMode && keyboardEnabled) {
                    LiveOutputRegion(
                        screenState = screenState,
                        enabled = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }


        if (showMagnifier) {
            MagnifyingGlass(
                position = magnifierPosition,
                screenState = screenState,
                baseCharWidth = baseCharWidth,
                baseCharHeight = baseCharHeight,
                baseCharBaseline = baseCharBaseline,
                textPaint = textPaint,
                backgroundColor = backgroundColor,
                foregroundColor = foregroundColor,
                selectionManager = selectionManager,
                autoDetectUrls = terminalEmulator.autoDetectUrls,
                componentWidth = availableWidth,
                componentHeight = availableHeight,
                selectionBackgroundColor = selectionBackgroundColor,
                selectionForegroundColor = selectionForegroundColor,
            )
        }


        if (selectionManager.mode != SelectionMode.NONE && !selectionManager.isSelecting && !isDraggingHandle) {
            val range = selectionManager.selectionRange
            if (range != null) {
                val endPosition = range.getEndPosition()
                val buttonRowWidthPx = with(density) { (COPY_BUTTON_SIZE * 2 + 8.dp).toPx() }
                val buttonHeightPx = with(density) { COPY_BUTTON_SIZE.toPx() }

                val buttonX = (endPosition.second * baseCharWidth)
                    .coerceAtMost(availableWidth - buttonRowWidthPx)
                    .coerceAtLeast(0f)


                val handleHeightPx = with(density) { SELECTION_HANDLE_WIDTH.toPx() }
                val marginPx = with(density) { 8.dp.toPx() }
                var buttonY = (endPosition.first + 1) * baseCharHeight + handleHeightPx + marginPx

                if (buttonY + buttonHeightPx > availableHeight) {



                    buttonY = endPosition.first * baseCharHeight - handleHeightPx - buttonHeightPx - marginPx
                }

                val context = LocalContext.current
                var overflowMenuExpanded by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { buttonX.toDp() },
                            y = with(density) { buttonY.coerceAtLeast(0f).toDp() },
                        ),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloatingActionButton(
                            onClick = {
                                val selectedText =
                                    selectionManager.getSelectedText(screenState.snapshot, screenState.scrollbackPosition)
                                clipboardManager.setText(AnnotatedString(selectedText))
                                selectionManager.clearSelection()
                            },
                            modifier = Modifier.size(COPY_BUTTON_SIZE),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text(stringResource(R.string.terminal_selection_copy), style = MaterialTheme.typography.labelSmall)
                        }

                        Box {
                            val moreOptionsLabel = stringResource(R.string.terminal_selection_more_options_description)
                            FloatingActionButton(
                                onClick = {
                                    overflowMenuExpanded = true
                                    if (shouldShowIme) {
                                        scope.launch {
                                            delay(IME_SHOW_DELAY_MS)
                                            imeInputView?.showIme()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(COPY_BUTTON_SIZE)
                                    .semantics {
                                        contentDescription = moreOptionsLabel
                                        role = Role.Button
                                    },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                Text(stringResource(R.string.terminal_selection_more_options), style = MaterialTheme.typography.labelSmall)
                            }

                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = {
                                    overflowMenuExpanded = false
                                    if (shouldShowIme) {
                                        scope.launch {
                                            delay(IME_SHOW_DELAY_MS)
                                            imeInputView?.showIme()
                                        }
                                    }
                                },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.terminal_selection_select_all)) },
                                    onClick = {
                                        selectionController.selectAll()
                                        overflowMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.terminal_selection_share)) },
                                    onClick = {
                                        val selectedText = selectionManager.getSelectedText(
                                            screenState.snapshot,
                                            screenState.scrollbackPosition,
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, selectedText)
                                            if (context !is Activity) {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        }
                                        try {
                                            context.startActivity(Intent.createChooser(intent, null))
                                        } catch (_: ActivityNotFoundException) {

                                        }
                                        overflowMenuExpanded = false
                                    },
                                )
                                if (onPasteRequest != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.terminal_selection_paste)) },
                                        onClick = {
                                            selectionManager.clearSelection()
                                            onPasteRequest()
                                            overflowMenuExpanded = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        val modeLabel = when (selectionManager.mode) {
                                            SelectionMode.CHARACTER -> stringResource(R.string.terminal_selection_mode_character)
                                            SelectionMode.WORD -> stringResource(R.string.terminal_selection_mode_word)
                                            else -> stringResource(R.string.terminal_selection_mode_line)
                                        }
                                        Text(modeLabel)
                                    },
                                    onClick = {
                                        selectionController.toggleSelectionMode()

                                    },
                                )
                            }
                        }
                    }
                }
            }
        }




        if (keyboardEnabled) {
            AndroidView(
                factory = { context ->
                    ImeInputView(context, keyboardHandler).apply {

                        setOnKeyListener { _, _, event ->
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                resetImeBuffer()
                            }
                            keyboardHandler.onKeyEvent(
                                androidx.compose.ui.input.key.KeyEvent(event),
                            )
                        }

                        imeInputView = this
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .focusable()
                    .focusRequester(focusRequester),
            )
        }
    }
}

/**
 * Draw a single terminal line.
 */
@Composable
private fun TerminalRows(
    screenState: TerminalScreenState,
    charWidth: Float,
    charHeight: Float,
    charBaseline: Float,
    textPaint: TextPaint,
    underlinePaint: Paint,
    defaultFg: Color,
    defaultBg: Color,
    autoDetectUrls: Boolean,
) {
    val density = LocalDensity.current
    val rowHeight = with(density) { charHeight.toDp() }
    val snapshot = screenState.snapshot
    val hyperlinkMasks = remember(snapshot.sequenceNumber, screenState.scrollbackPosition, autoDetectUrls) {
        if (!autoDetectUrls) {
            emptyList()
        } else {
            List(snapshot.rows) { row ->
                BooleanArray(snapshot.cols) { col ->
                    screenState.getHyperlinkUrlAt(row, col, autoDetectUrls = true) != null
                }
            }
        }
    }

    for (row in 0 until snapshot.rows) {
        val line = screenState.getVisibleLine(row)
        key(row, line.lastModified, line.semanticSegments, screenState.scrollbackPosition) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .offset(y = with(density) { (row * charHeight).toDp() }),
            ) {
                drawLine(
                    line = line,
                    row = 0,
                    charWidth = charWidth,
                    charHeight = charHeight,
                    charBaseline = charBaseline,
                    textPaint = textPaint,
                    underlinePaint = underlinePaint,
                    defaultFg = defaultFg,
                    defaultBg = defaultBg,
                    selectionManager = null,
                    autoDetectUrls = autoDetectUrls,
                    hyperlinkMask = hyperlinkMasks.getOrNull(row),
                )
            }
        }
    }
}

private fun DrawScope.drawLine(
    line: TerminalLine,
    row: Int,
    charWidth: Float,
    charHeight: Float,
    charBaseline: Float,
    textPaint: TextPaint,
    underlinePaint: Paint,
    defaultFg: Color,
    defaultBg: Color,
    selectionManager: SelectionManager?,
    autoDetectUrls: Boolean = false,
    hyperlinkMask: BooleanArray? = null,
    selectionBackgroundColor: Color = Color(0xFFB3D7FF),
    selectionForegroundColor: Color = Color.Black,
    selectedOnly: Boolean = false,
) {
    val y = row * charHeight
    var x = 0f

    line.cells.forEachIndexed { col, cell ->
        val cellWidth = charWidth * cell.width


        val isSelected = selectionManager?.isCellSelected(row, col, line) == true
        if (selectedOnly && !isSelected) {
            x += cellWidth
            return@forEachIndexed
        }


        val isHyperlink = hyperlinkMask?.getOrNull(col) ?: (line.getHyperlinkUrlAt(col, autoDetectUrls) != null)


        val baseFgColor = if (cell.reverse) cell.bgColor else cell.fgColor
        val bgColor = if (cell.reverse) cell.fgColor else cell.bgColor


        val finalBgColor = if (isSelected) selectionBackgroundColor else bgColor
        if (finalBgColor != defaultBg || isSelected) {
            drawRect(
                color = finalBgColor,
                topLeft = Offset(x, y),
                size = Size(cellWidth, charHeight),
            )
        }


        if ((cell.char != ' ' && cell.char != '\u0000') || cell.combiningChars.isNotEmpty()) {

            val fgColor = if (isSelected) selectionForegroundColor else baseFgColor


            textPaint.color = fgColor.toArgb()
            textPaint.isFakeBoldText = cell.bold
            textPaint.textSkewX = if (cell.italic) -0.25f else 0f

            textPaint.isUnderlineText = cell.underline == 1 || isHyperlink
            textPaint.isStrikeThruText = cell.strike


            if (cell.combiningChars.isEmpty()) {
                val textBuffer = DRAW_TEXT_BUFFER.get()!!
                textBuffer[0] = cell.char
                drawContext.canvas.nativeCanvas.drawText(
                    textBuffer,
                    0,
                    1,
                    x,
                    y + charBaseline,
                    textPaint,
                )
            } else {
                val text = buildString {
                    append(cell.char)
                    cell.combiningChars.forEach { append(it) }
                }
                drawContext.canvas.nativeCanvas.drawText(
                    text,
                    x,
                    y + charBaseline,
                    textPaint,
                )
            }


            if (cell.underline == 2) {
                drawDoubleUnderline(
                    x = x,
                    y = y + charBaseline,
                    width = cellWidth,
                    color = fgColor,
                    paint = underlinePaint,
                )
            }


            if (cell.underline == 3) {
                drawCurlyUnderline(
                    x = x,
                    y = y + charBaseline,
                    width = cellWidth,
                    charWidth = charWidth,
                    color = fgColor,
                    paint = underlinePaint,
                )
            }
        }

        x += cellWidth
    }
}

/**
 * Draw a double underline (two parallel lines).
 *
 * @param x Start x position
 * @param y Baseline y position
 * @param width Width to draw the underline
 * @param color Color of the underline
 */
private fun DrawScope.drawDoubleUnderline(
    x: Float,
    y: Float,
    width: Float,
    color: Color,
    paint: Paint,
) {
    paint.color = color.toArgb()

    val baseY = y + 2f
    val canvas = drawContext.canvas.nativeCanvas


    canvas.drawLine(x, baseY, x + width, baseY, paint)


    canvas.drawLine(x, baseY + DOUBLE_UNDERLINE_SPACING, x + width, baseY + DOUBLE_UNDERLINE_SPACING, paint)
}

/**
 * Draw a curly/zigzag underline pattern (like spell-check underlines).
 * The pattern repeats seamlessly across characters by aligning with the character grid.
 * Each character starts at the same phase (top of wave) ensuring perfect continuity.
 *
 * @param x Start x position
 * @param y Baseline y position
 * @param width Width to draw the underline
 * @param charWidth Base character width (used to calculate wavelength)
 * @param color Color of the underline
 */
private fun DrawScope.drawCurlyUnderline(
    x: Float,
    y: Float,
    width: Float,
    charWidth: Float,
    color: Color,
    paint: Paint,
) {
    val path = CURLY_UNDERLINE_PATH.get()!!.apply { reset() }
    val wavelength = charWidth / CURLY_UNDERLINE_CYCLES_PER_CHAR
    val amplitude = CURLY_UNDERLINE_AMPLITUDE
    val halfWave = wavelength / 2

    val baseY = y + 3f


    path.moveTo(x, baseY - amplitude)


    var currentX = x
    var goingDown = true

    while (currentX < x + width) {
        currentX += halfWave
        if (currentX > x + width) {
            currentX = x + width
        }

        val nextY = if (goingDown) baseY + amplitude else baseY - amplitude
        path.lineTo(currentX, nextY)

        goingDown = !goingDown
    }


    paint.color = color.toArgb()
    drawContext.canvas.nativeCanvas.drawPath(
        path,
        paint,
    )
}

/**
 * Result of a single handle-drag update step.
 *
 * @param startRow Updated selection start row
 * @param startCol Updated selection start col
 * @param endRow Updated selection end row
 * @param endCol Updated selection end col
 * @param isMovingStart Which handle the finger is now dragging after this step
 */
internal data class HandleDragResult(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
    val isMovingStart: Boolean,
)

/**
 * Computes the next selection range and active-handle flag for one drag event, handling
 * the case where the two handles cross each other.
 *
 * When the moving handle passes the anchor, ownership flips and the anchor is restored to
 * its pre-cross position so it doesn't jump to the dragged handle's column.
 *
 * @param startRow Current selection start row (before this event)
 * @param startCol Current selection start col (before this event)
 * @param endRow Current selection end row (before this event)
 * @param endCol Current selection end col (before this event)
 * @param isMovingStart True if the start handle is being dragged
 * @param newRow Row the finger moved to
 * @param newCol Col the finger moved to
 */
@VisibleForTesting
internal fun applyHandleDrag(
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int,
    isMovingStart: Boolean,
    newRow: Int,
    newCol: Int,
): HandleDragResult {
    val anchorRow = if (isMovingStart) endRow else startRow
    val anchorCol = if (isMovingStart) endCol else startCol

    val updatedStartRow = if (isMovingStart) newRow else startRow
    val updatedStartCol = if (isMovingStart) newCol else startCol
    val updatedEndRow = if (isMovingStart) endRow else newRow
    val updatedEndCol = if (isMovingStart) endCol else newCol

    val crossed = if (isMovingStart) {
        newRow > anchorRow || (newRow == anchorRow && newCol > anchorCol)
    } else {
        newRow < anchorRow || (newRow == anchorRow && newCol < anchorCol)
    }

    return if (!crossed) {
        HandleDragResult(updatedStartRow, updatedStartCol, updatedEndRow, updatedEndCol, isMovingStart)
    } else {

        val newIsMovingStart = !isMovingStart
        if (newIsMovingStart) {

            HandleDragResult(newRow, newCol, anchorRow, anchorCol, newIsMovingStart)
        } else {

            HandleDragResult(anchorRow, anchorCol, newRow, newCol, newIsMovingStart)
        }
    }
}

/**
 * Check if a touch position is near a selection handle.
 * Returns (touchingStart, touchingEnd).
 */
private fun isTouchingHandle(
    touchPos: Offset,
    range: SelectionRange,
    charWidth: Float,
    charHeight: Float,
    hitRadius: Float = HANDLE_HIT_RADIUS,
): Pair<Boolean, Boolean> {




    val handleRadius = charHeight * 0.4f
    val startPos = Offset(
        range.startCol * charWidth + charWidth / 2,
        range.startRow * charHeight - handleRadius,
    )
    val endPos = Offset(
        range.endCol * charWidth + charWidth / 2,
        range.endRow * charHeight + charHeight + handleRadius,
    )

    val distToStart = (touchPos - startPos).getDistance()
    val distToEnd = (touchPos - endPos).getDistance()

    return Pair(
        distToStart < hitRadius,
        distToEnd < hitRadius,
    )
}

/**
 * Lightweight placeholder shown when the Terminal composable is rendered
 * inside Android Studio's Compose Preview (LocalInspectionMode).
 */
@Composable
private fun TerminalPreview(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black,
    foregroundColor: Color = Color.White,
) {
    val previewLines = listOf(
        "$ echo hello",
        "hello",
        "$ _",
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val textPaint = TextPaint().apply {
            color = foregroundColor.toArgb()
            typeface = Typeface.MONOSPACE
            textSize = 14f * density
        }
        val metrics = textPaint.fontMetrics
        val lineHeight = ceil(metrics.descent - metrics.ascent)
        val baseline = ceil(-metrics.ascent)

        previewLines.forEachIndexed { index, line ->
            drawContext.canvas.nativeCanvas.drawText(
                line,
                0f,
                baseline + index * lineHeight,
                textPaint,
            )
        }
    }
}

/**
 * Calculates the top-left offset for the magnifying glass box so it stays visible and
 * never obscured by the finger.
 *
 * Prefers positioning above the touch point (with [verticalOffsetPx] gap). Flips below
 * the finger (adding [fingerHeightPx] + [verticalOffsetPx]) when there is not enough
 * room above. The result is clamped to the component bounds on all sides.
 */
@VisibleForTesting
internal fun magnifierOffset(
    position: Offset,
    magnifierSizePx: Float,
    verticalOffsetPx: Float,
    fingerHeightPx: Float,
    componentWidth: Int,
    componentHeight: Int,
): Offset {
    val clampedX = (position.x - magnifierSizePx / 2)
        .coerceIn(0f, (componentWidth.toFloat() - magnifierSizePx).coerceAtLeast(0f))
    val spaceAbove = position.y - verticalOffsetPx - magnifierSizePx
    return if (spaceAbove >= 0f) {
        Offset(x = clampedX, y = spaceAbove)
    } else {
        val yBelow = position.y + fingerHeightPx + verticalOffsetPx
        Offset(x = clampedX, y = yBelow.coerceAtMost((componentHeight.toFloat() - magnifierSizePx).coerceAtLeast(0f)))
    }
}

/**
 * Magnifying glass for text selection.
 */
@Composable
private fun MagnifyingGlass(
    position: Offset,
    screenState: TerminalScreenState,
    baseCharWidth: Float,
    baseCharHeight: Float,
    baseCharBaseline: Float,
    textPaint: TextPaint,
    backgroundColor: Color,
    foregroundColor: Color,
    selectionManager: SelectionManager,
    autoDetectUrls: Boolean = false,
    componentWidth: Int = 0,
    componentHeight: Int = 0,
    selectionBackgroundColor: Color = Color(0xFFB3D7FF),
    selectionForegroundColor: Color = Color.Black,
) {
    val magnifierSize = MAGNIFIER_SIZE_DP.dp
    val magnifierScale = MAGNIFIER_SCALE
    val density = LocalDensity.current
    val magnifierSizePx = with(density) { magnifierSize.toPx() }

    val verticalOffset = with(density) { MAGNIFIER_VERTICAL_OFFSET.toPx() }
    val fingerHeightPx = with(density) { FINGER_HEIGHT_DP.toPx() }
    val underlinePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
    }

    val magnifierPos = magnifierOffset(
        position = position,
        magnifierSizePx = magnifierSizePx,
        verticalOffsetPx = verticalOffset,
        fingerHeightPx = fingerHeightPx,
        componentWidth = componentWidth,
        componentHeight = componentHeight,
    )


    val centerOffset = Offset(
        x = position.x - (magnifierSizePx / magnifierScale) * MAGNIFIER_CENTER_OFFSET_MULTIPLIER,
        y = position.y - (magnifierSizePx / magnifierScale) * MAGNIFIER_CENTER_OFFSET_MULTIPLIER,
    )

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { magnifierPos.x.toDp() },
                y = with(density) { magnifierPos.y.toDp() },
            )
            .size(magnifierSize)
            .border(
                width = MAGNIFIER_BORDER_WIDTH,
                color = Color.Gray,
                shape = CircleShape,
            )
            .background(
                color = Color.White.copy(alpha = MAGNIFIER_BACKGROUND_ALPHA),
                shape = CircleShape,
            )
            .clip(CircleShape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {

            drawRect(
                color = backgroundColor,
                size = size,
            )


            translate(-centerOffset.x * magnifierScale, -centerOffset.y * magnifierScale) {
                scale(magnifierScale, magnifierScale) {

                    val centerRow = (position.y / baseCharHeight).toInt().coerceIn(0, screenState.snapshot.rows - 1)


                    for (rowOffset in -MAGNIFIER_ROW_RANGE..MAGNIFIER_ROW_RANGE) {
                        val row = (centerRow + rowOffset).coerceIn(0, screenState.snapshot.rows - 1)
                        val line = screenState.getVisibleLine(row)
                        drawLine(
                            line = line,
                            row = row,
                            charWidth = baseCharWidth,
                            charHeight = baseCharHeight,
                            charBaseline = baseCharBaseline,
                            textPaint = textPaint,
                            underlinePaint = underlinePaint,
                            defaultFg = foregroundColor,
                            defaultBg = backgroundColor,
                            selectionManager = selectionManager,
                            autoDetectUrls = autoDetectUrls,
                            selectionBackgroundColor = selectionBackgroundColor,
                            selectionForegroundColor = selectionForegroundColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draw a selection handle (teardrop shape).
 */
private fun DrawScope.drawSelectionHandle(
    row: Int,
    col: Int,
    charWidth: Float,
    charHeight: Float,
    pointingDown: Boolean,
    color: Color = Color.White,
) {
    val handleWidthPx = SELECTION_HANDLE_WIDTH.toPx()


    val charX = col * charWidth
    val charY = row * charHeight


    val handleX = charX + charWidth / 2


    val handleY = if (pointingDown) {
        charY + charHeight
    } else {
        charY
    }

    val circleRadius = handleWidthPx / 2
    val circleY = if (pointingDown) {
        handleY + circleRadius
    } else {
        handleY - circleRadius
    }

    drawCircle(
        color = color,
        radius = circleRadius,
        center = Offset(handleX, circleY),
    )
}

/**
 * Draw the cursor with shape support (block, underline, bar).
 */
private fun DrawScope.drawCursor(
    row: Int,
    col: Int,
    charWidth: Float,
    charHeight: Float,
    foregroundColor: Color,
    backgroundColor: Color = Color.Transparent,
    cursorShape: CursorShape = CursorShape.BLOCK,
    pendingDeadChar: Int = 0,
    charBaseline: Float = 0f,
    textPaint: TextPaint? = null,
) {
    val x = col * charWidth
    val y = row * charHeight

    when (cursorShape) {
        CursorShape.BLOCK -> {

            drawRect(
                color = foregroundColor,
                topLeft = Offset(x, y),
                size = Size(charWidth, charHeight),
                alpha = CURSOR_BLOCK_ALPHA,
            )
        }

        CursorShape.UNDERLINE -> {

            val underlineHeight = charHeight * CURSOR_UNDERLINE_HEIGHT_RATIO
            drawRect(
                color = foregroundColor,
                topLeft = Offset(x, y + charHeight - underlineHeight),
                size = Size(charWidth, underlineHeight),
                alpha = CURSOR_LINE_ALPHA,
            )
        }

        CursorShape.BAR_LEFT -> {

            val barWidth = charWidth * CURSOR_BAR_WIDTH_RATIO
            drawRect(
                color = foregroundColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, charHeight),
                alpha = CURSOR_LINE_ALPHA,
            )
        }
    }


    if (pendingDeadChar != 0 && textPaint != null) {
        val savedColor = textPaint.color
        val savedBold = textPaint.isFakeBoldText
        val savedSkew = textPaint.textSkewX
        val savedUnderline = textPaint.isUnderlineText
        val savedStrike = textPaint.isStrikeThruText


        val accentColor =
            if (cursorShape == CursorShape.BLOCK) {
                if (foregroundColor.luminance() > 0.5f) Color.Black else Color.White
            } else {
                foregroundColor
            }

        textPaint.color = accentColor.toArgb()
        textPaint.isFakeBoldText = false
        textPaint.textSkewX = 0f
        textPaint.isUnderlineText = false
        textPaint.isStrikeThruText = false

        drawContext.canvas.nativeCanvas.drawText(
            String(Character.toChars(pendingDeadChar)),
            x,
            y + charBaseline,
            textPaint,
        )

        textPaint.color = savedColor
        textPaint.isFakeBoldText = savedBold
        textPaint.textSkewX = savedSkew
        textPaint.isUnderlineText = savedUnderline
        textPaint.isStrikeThruText = savedStrike
    }
}

/**
 * Background color for the compose mode overlay.
 */
private val COMPOSE_OVERLAY_BACKGROUND = Color(0xFF2E7D32).copy(alpha = 0.85f)

/**
 * Width of the compose mode cursor bar in pixels.
 */
private const val COMPOSE_CURSOR_BAR_WIDTH = 2f

/**
 * Draw the compose mode overlay at the cursor position.
 *
 * Displays the buffered text with a slide-left behavior when the buffer
 * exceeds available space. Shows an ellipsis at the left edge when text
 * is truncated.
 */
private fun DrawScope.drawComposeOverlay(
    buffer: String,
    cursorRow: Int,
    cursorCol: Int,
    totalCols: Int,
    charWidth: Float,
    charHeight: Float,
    charBaseline: Float,
    textPaint: TextPaint,
) {
    val x = cursorCol * charWidth
    val y = cursorRow * charHeight
    val availableCols = totalCols - cursorCol




    if (buffer.isEmpty()) return






    val displayText = truncateForOverlay(buffer, availableCols)
    if (displayText.isEmpty()) return
    val displayWidth = textPaint.measureText(displayText)


    drawRect(
        color = COMPOSE_OVERLAY_BACKGROUND,
        topLeft = Offset(x, y),
        size = Size(displayWidth.coerceAtLeast(charWidth), charHeight),
    )


    if (displayText.isNotEmpty()) {
        val savedColor = textPaint.color
        val savedBold = textPaint.isFakeBoldText
        val savedSkew = textPaint.textSkewX
        val savedUnderline = textPaint.isUnderlineText
        val savedStrike = textPaint.isStrikeThruText

        textPaint.color = Color.White.toArgb()
        textPaint.isFakeBoldText = false
        textPaint.textSkewX = 0f
        textPaint.isUnderlineText = false
        textPaint.isStrikeThruText = false

        drawContext.canvas.nativeCanvas.drawText(
            displayText,
            x,
            y + charBaseline,
            textPaint,
        )

        textPaint.color = savedColor
        textPaint.isFakeBoldText = savedBold
        textPaint.textSkewX = savedSkew
        textPaint.isUnderlineText = savedUnderline
        textPaint.isStrikeThruText = savedStrike
    }


    val cursorX = x + displayWidth
    drawRect(
        color = Color.White,
        topLeft = Offset(cursorX, y),
        size = Size(COMPOSE_CURSOR_BAR_WIDTH, charHeight),
        alpha = CURSOR_LINE_ALPHA,
    )
}

/**
 * Visual column width of a single codepoint. Fullwidth/wide East Asian characters
 * (kanji, kana, hangul, fullwidth ASCII, etc.) occupy two terminal columns; everything
 * else occupies one. Combining marks aren't special-cased here because the IME-composed
 * buffer rarely contains them on their own; if needed they can be treated as zero-width.
 */
private fun codepointColumns(codepoint: Int): Int {
    val eastAsianWidth = UCharacter.getIntPropertyValue(codepoint, UProperty.EAST_ASIAN_WIDTH)
    return if (eastAsianWidth == UCharacter.EastAsianWidth.FULLWIDTH ||
        eastAsianWidth == UCharacter.EastAsianWidth.WIDE
    ) {
        2
    } else {
        1
    }
}

private fun String.visualColumnWidth(): Int {
    var total = 0
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        total += codepointColumns(cp)
        i += Character.charCount(cp)
    }
    return total
}

/**
 * Trim the buffer so its visual column width fits in [availableCols], keeping the right
 * end of the buffer (most recent characters) and prefixing an ellipsis when truncating.
 */
private fun truncateForOverlay(buffer: String, availableCols: Int): String {
    if (availableCols <= 0) return ""
    if (buffer.visualColumnWidth() <= availableCols) return buffer



    val budget = availableCols - 1
    val codepoints = mutableListOf<Int>()
    var taken = 0
    var i = buffer.length
    while (i > 0) {
        val cp = buffer.codePointBefore(i)
        val w = codepointColumns(cp)
        if (taken + w > budget) break
        codepoints.add(0, cp)
        taken += w
        i -= Character.charCount(cp)
    }
    val builder = StringBuilder("\u2026")
    codepoints.forEach { builder.appendCodePoint(it) }
    return builder.toString()
}

/**
 * Calculate pixel dimensions for a specific row/column count at a given font size.
 *
 * @param rows Number of rows
 * @param cols Number of columns
 * @param fontSize Font size in sp
 * @param typeface The typeface to use for measurement
 * @param density Screen density for sp to px conversion
 * @return Pair of (width in pixels, height in pixels)
 */
private fun calculateDimensions(
    rows: Int,
    cols: Int,
    fontSize: Float,
    typeface: Typeface,
    density: Float,
): Pair<Float, Float> {
    val textPaint = TextPaint().apply {
        this.typeface = typeface
        textSize = fontSize * density
    }

    val charWidth = textPaint.measureText("M")
    val metrics = textPaint.fontMetrics
    val charHeight = ceil(metrics.descent - metrics.ascent)

    val width = cols * charWidth
    val height = rows * charHeight

    return Pair(width, height)
}

/**
 * Find the optimal font size that allows the terminal to fit within the available space
 * while maintaining the exact target rows and columns.
 *
 * Uses binary search to efficiently find the largest font size that fits.
 *
 * @param targetRows Target number of rows
 * @param targetCols Target number of columns
 * @param availableWidth Available width in pixels
 * @param availableHeight Available height in pixels
 * @param minSize Minimum font size in sp
 * @param maxSize Maximum font size in sp
 * @param typeface The typeface to use for measurement
 * @param density Screen density for sp to px conversion
 * @return Optimal font size in sp
 */
private fun findOptimalFontSize(
    targetRows: Int,
    targetCols: Int,
    availableWidth: Int,
    availableHeight: Int,
    minSize: Float,
    maxSize: Float,
    typeface: Typeface,
    density: Float,
): Float {
    var minSizeCurrent = minSize
    var maxSizeCurrent = maxSize


    while (maxSizeCurrent - minSizeCurrent > FONT_SIZE_SEARCH_EPSILON) {
        val midSize = (minSizeCurrent + maxSizeCurrent) / 2f
        val (width, height) = calculateDimensions(
            rows = targetRows,
            cols = targetCols,
            fontSize = midSize,
            typeface = typeface,
            density = density,
        )

        if (width <= availableWidth && height <= availableHeight) {

            minSizeCurrent = midSize
        } else {

            maxSizeCurrent = midSize
        }
    }


    return minSizeCurrent.coerceIn(minSize, maxSize)
}

private fun charsPerDimension(pixels: Int, charPixels: Float) = (pixels / charPixels).toInt().coerceAtLeast(1)
