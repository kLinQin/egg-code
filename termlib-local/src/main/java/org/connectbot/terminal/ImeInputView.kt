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

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/**
 * A minimal invisible View that provides proper IME input handling for terminal emulation.
 *
 * This view creates a custom InputConnection that:
 * - Handles backspace via deleteSurroundingText by sending KEYCODE_DEL
 * - Handles enter/return keys properly via sendKeyEvent
 * - Configures the keyboard to disable suggestions while allowing voice input
 * - Handles composing text from IME (for voice input partial results)
 * - Manages IME visibility using InputMethodManager for reliable show/hide
 *
 * Based on the ConnectBot v1.9.13 TerminalView implementation.
 */
internal class ImeInputView(
    context: Context,
    private val keyboardHandler: KeyboardHandler,
    internal val inputMethodManager: InputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager,
    internal val onUpdateSelection: (view: View, selStart: Int, selEnd: Int, candidatesStart: Int, candidatesEnd: Int) -> Unit =
        { view, selStart, selEnd, candidatesStart, candidatesEnd ->
            inputMethodManager.updateSelection(view, selStart, selEnd, candidatesStart, candidatesEnd)
        },
    internal val onRestartInput: (view: View) -> Unit =
        { view -> inputMethodManager.restartInput(view) },
) : View(context) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    var isComposeModeActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (windowToken != null) {
                onRestartInput(this)
            }
        }

    /**
     * Show the IME forcefully. This is more reliable than SoftwareKeyboardController.
     */
    @Suppress("DEPRECATION")
    fun showIme() {
        if (requestFocus()) {
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_FORCED)
        }
    }

    /**
     * Hide the IME.
     */
    fun hideIme() {
        inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()


        hideIme()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {

        outAttrs.imeOptions = outAttrs.imeOptions or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_ENTER_ACTION or
            EditorInfo.IME_ACTION_NONE

        if (isComposeModeActive) {





            outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
            outAttrs.initialSelStart = 0
            outAttrs.initialSelEnd = 0
        } else {





            outAttrs.inputType = EditorInfo.TYPE_NULL or
                EditorInfo.TYPE_TEXT_VARIATION_PASSWORD or
                EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }

        return TerminalInputConnection(this, isComposeModeActive).also { activeConnection = it }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    private var activeConnection: TerminalInputConnection? = null

    /**
     * Clears the IME's internal text buffer and resets its selection state to (0, 0).
     *
     * Call this after key events that are dispatched outside the InputConnection (e.g. physical
     * keyboard events handled via onPreviewKeyEvent or setOnKeyListener), so that the IME's
     * suggestion context stays in sync with the terminal's stateless text model.
     */
    fun resetImeBuffer() {








        activeConnection?.editable?.clear()
        activeConnection?.resetComposition()
        onUpdateSelection(this, 0, 0, -1, -1)
    }

    private fun restartInputSoon() {
        onRestartInput(this)
    }

    /**
     * Custom InputConnection that handles backspace and other special keys for terminal input.
     */
    private inner class TerminalInputConnection(
        targetView: View,
        private val fullEditor: Boolean,
    ) : BaseInputConnection(targetView, fullEditor) {

        private var composingText: String = ""
        private var awaitingPostEnterCommitReplay: Boolean = false
        private var postEnterSubmittedText: String? = null

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!fullEditor) return super.setComposingText(text, newCursorPosition)

            val newText = text?.toString() ?: ""
            if (awaitingPostEnterCommitReplay &&
                newText.isNotEmpty() &&
                postEnterSubmittedText != null &&
                newText == postEnterSubmittedText
            ) {
                super.setComposingText(text, newCursorPosition)
                awaitingPostEnterCommitReplay = false
                postEnterSubmittedText = null
                composingText = ""
                editable?.clear()
                onUpdateSelection(this@ImeInputView, 0, 0, -1, -1)


                restartInputSoon()
                return true
            }

            if (awaitingPostEnterCommitReplay && newText.isNotEmpty()) {
                awaitingPostEnterCommitReplay = false
                postEnterSubmittedText = null
            }
            super.setComposingText(text, newCursorPosition)

            if (newText == composingText) {
                return true
            }

            if (newText.isEmpty()) {
                if (composingText.isNotEmpty()) {

                    sendBackspaces(composingText.length)
                }
                composingText = ""
                return true
            }

            when {
                newText.startsWith(composingText) -> {

                    val delta = newText.substring(composingText.length)
                    sendTextInput(delta)
                }

                composingText.startsWith(newText) -> {

                    val deleteCount = composingText.length - newText.length
                    sendBackspaces(deleteCount)
                }

                else -> {

                    sendBackspaces(composingText.length)
                    sendTextInput(newText)
                }
            }

            composingText = newText
            return true
        }

        override fun finishComposingText(): Boolean {
            if (!fullEditor) return super.finishComposingText()

            super.finishComposingText()
            composingText = ""

            editable?.clear()
            return true
        }

        override fun deleteSurroundingText(leftLength: Int, rightLength: Int): Boolean {


            if (rightLength == 0 && leftLength == 0) {
                return sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }





            val bounded = leftLength.coerceIn(0, MAX_DELETE_SURROUNDING)
            for (i in 0 until bounded) {
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }


            if (bounded > 0 && composingText.isNotEmpty()) {
                val newLength = (composingText.length - bounded).coerceAtLeast(0)
                composingText = composingText.substring(0, newLength)
            }

            super.deleteSurroundingText(leftLength, rightLength)
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (fullEditor) {
                val isKeyDown = event.action == KeyEvent.ACTION_DOWN
                val postEnterSubmittedBeforeDispatch = if (isKeyDown && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    composingText.takeIf { it.isNotEmpty() }
                } else {
                    null
                }




                val result = this@ImeInputView.dispatchKeyEvent(event)
                if (isKeyDown) {
                    awaitingPostEnterCommitReplay = event.keyCode == KeyEvent.KEYCODE_ENTER
                    postEnterSubmittedText = postEnterSubmittedBeforeDispatch
                    editable?.clear()
                    onUpdateSelection(this@ImeInputView, 0, 0, -1, -1)
                }
                return result
            } else {








                if (event.action == KeyEvent.ACTION_DOWN) {
                    keyboardHandler.onKeyEvent(ComposeKeyEvent(event))
                }
                return true
            }
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val committedText = text?.toString() ?: ""
            if (!fullEditor) {
                if (committedText.isNotEmpty()) {









                    sendTextInput(committedText)
                }
                return true
            }



            val previousComposingText = composingText
            super.commitText(text, newCursorPosition)

            if (awaitingPostEnterCommitReplay &&
                postEnterSubmittedText != null &&
                committedText == postEnterSubmittedText
            ) {
                awaitingPostEnterCommitReplay = false
                postEnterSubmittedText = null
                composingText = ""
                editable?.clear()
                onUpdateSelection(this@ImeInputView, 0, 0, -1, -1)



                restartInputSoon()
                return true
            }

            awaitingPostEnterCommitReplay = false
            postEnterSubmittedText = null
            if (committedText.isNotEmpty()) {
                if (previousComposingText.isNotEmpty()) {
                    sendBackspaces(previousComposingText.length)
                }
                keyboardHandler.onCommittedText(committedText)
            }
            composingText = ""
            editable?.clear()
            return true
        }

        private fun sendBackspaces(count: Int) {
            repeat(count.coerceAtLeast(0)) {
                sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            }
        }

        private fun sendTextInput(text: String) {
            if (text.isNotEmpty()) {
                keyboardHandler.onTextInput(text.toByteArray(Charsets.UTF_8))
            }
        }

        /**
         * Drop in-flight composition tracking without touching the terminal
         * output. Called from [ImeInputView.resetImeBuffer] so an interrupted
         * composition (e.g. external hardware-key events, IME dismissal mid-
         * conversion) doesn't leave stale length state that a subsequent
         * [setComposingText] would compute its backspace count against.
         */
        fun resetComposition() {
            composingText = ""
        }
    }

    companion object {
        /** Upper bound on [InputConnection.deleteSurroundingText]'s `leftLength`. */
        private const val MAX_DELETE_SURROUNDING = 4096
    }
}
