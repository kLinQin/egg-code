package com.egg.codeeditor.terminal

import android.view.View
import android.widget.Button
import com.termux.terminal.TerminalSession

interface IVirtualKeysView {
    fun onVirtualKeyButtonClick(view: View?, buttonInfo: VirtualKeyButton?, button: Button?)
    fun performVirtualKeyButtonHapticFeedback(view: View?, buttonInfo: VirtualKeyButton?, button: Button?): Boolean
}

data class VirtualKeyButton(val key: String, val display: String)

class VirtualKeysListener(val session: TerminalSession) : IVirtualKeysView {
    override fun onVirtualKeyButtonClick(view: View?, buttonInfo: VirtualKeyButton?, button: Button?) {
        val key = buttonInfo?.key ?: return
        val writeable: String = when (key) {
            "UP" -> "\u001B[A"
            "DOWN" -> "\u001B[B"
            "LEFT" -> "\u001B[D"
            "RIGHT" -> "\u001B[C"
            "ENTER" -> "\u000D"
            "PGUP" -> "\u001B[5~"
            "PGDN" -> "\u001B[6~"
            "TAB" -> "\u0009"
            "HOME" -> "\u001B[H"
            "END" -> "\u001B[F"
            "ESC" -> "\u001B"
            else -> key
        }
        session.write(writeable)
    }
    override fun performVirtualKeyButtonHapticFeedback(view: View?, buttonInfo: VirtualKeyButton?, button: Button?) = false
}

val DEFAULT_EXTRA_KEYS: List<VirtualKeyButton> = listOf(
    VirtualKeyButton("ESC", "ESC"),
    VirtualKeyButton("/", "/"),
    VirtualKeyButton("-", "-"),
    VirtualKeyButton("HOME", "HOME"),
    VirtualKeyButton("UP", "↑"),
    VirtualKeyButton("END", "END"),
    VirtualKeyButton("PGUP", "PGUP"),
    VirtualKeyButton("LEFT", "←"),
    VirtualKeyButton("DOWN", "↓"),
    VirtualKeyButton("RIGHT", "→"),
    VirtualKeyButton("PGDN", "PGDN"),
    VirtualKeyButton("TAB", "TAB"),
)
