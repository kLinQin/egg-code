package com.egg.codeeditor.terminal.extrakeys;

import android.view.KeyEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtraKeysConstants {

    /** Defines the repetitive keys that can be passed to {@link ExtraKeysView#setRepetitiveKeys(List)}. */
    public static List<String> PRIMARY_REPETITIVE_KEYS = Arrays.asList(
        "UP", "DOWN", "LEFT", "RIGHT",
        "BKSP", "DEL",
        "PGUP", "PGDN");



    /** Defines the {@link KeyEvent} for common keys. */
    public static Map<String, Integer> PRIMARY_KEY_CODES_FOR_STRINGS = new HashMap<String, Integer>() {{
        put("SPACE", KeyEvent.KEYCODE_SPACE);
        put("ESC", KeyEvent.KEYCODE_ESCAPE);
        put("TAB", KeyEvent.KEYCODE_TAB);
        put("HOME", KeyEvent.KEYCODE_MOVE_HOME);
        put("END", KeyEvent.KEYCODE_MOVE_END);
        put("PGUP", KeyEvent.KEYCODE_PAGE_UP);
        put("PGDN", KeyEvent.KEYCODE_PAGE_DOWN);
        put("INS", KeyEvent.KEYCODE_INSERT);
        put("DEL", KeyEvent.KEYCODE_FORWARD_DEL);
        put("BKSP", KeyEvent.KEYCODE_DEL);
        put("UP", KeyEvent.KEYCODE_DPAD_UP);
        put("LEFT", KeyEvent.KEYCODE_DPAD_LEFT);
        put("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT);
        put("DOWN", KeyEvent.KEYCODE_DPAD_DOWN);
        put("ENTER", KeyEvent.KEYCODE_ENTER);
        put("F1", KeyEvent.KEYCODE_F1);
        put("F2", KeyEvent.KEYCODE_F2);
        put("F3", KeyEvent.KEYCODE_F3);
        put("F4", KeyEvent.KEYCODE_F4);
        put("F5", KeyEvent.KEYCODE_F5);
        put("F6", KeyEvent.KEYCODE_F6);
        put("F7", KeyEvent.KEYCODE_F7);
        put("F8", KeyEvent.KEYCODE_F8);
        put("F9", KeyEvent.KEYCODE_F9);
        put("F10", KeyEvent.KEYCODE_F10);
        put("F11", KeyEvent.KEYCODE_F11);
        put("F12", KeyEvent.KEYCODE_F12);
    }};



    /**
     * HashMap that implements Python dict.get(key, default) function.
     * Default java.util .get(key) is then the same as .get(key, null);
     */
    static class CleverMap<K,V> extends HashMap<K,V> {
        V get(K key, V defaultValue) {
            if (containsKey(key))
                return get(key);
            else
                return defaultValue;
        }
    }

    public static class ExtraKeyDisplayMap extends CleverMap<String, String> {}



    /*
     * Multiple maps are available to quickly change
     * the style of the keys.
     */

    public static class EXTRA_KEY_DISPLAY_MAPS {
        /**
         * Keys are displayed in a natural looking way, like "→" for "RIGHT"
         */
        public static final ExtraKeyDisplayMap CLASSIC_ARROWS_DISPLAY = new ExtraKeyDisplayMap() {{

            put("LEFT", "←");
            put("RIGHT", "→");
            put("UP", "↑");
            put("DOWN", "↓");
        }};

        public static final ExtraKeyDisplayMap WELL_KNOWN_CHARACTERS_DISPLAY = new ExtraKeyDisplayMap() {{

            put("ENTER", "↲");
            put("TAB", "↹");
            put("BKSP", "⌫");
            put("DEL", "⌦");
            put("DRAWER", "☰");
            put("KEYBOARD", "⌨");
            put("PASTE", "⎘");
            put("SCROLL", "⇳");
        }};

        public static final ExtraKeyDisplayMap LESS_KNOWN_CHARACTERS_DISPLAY = new ExtraKeyDisplayMap() {{


            put("HOME", "⇱");
            put("END", "⇲");
            put("PGUP", "⇑");
            put("PGDN", "⇓");
        }};

        public static final ExtraKeyDisplayMap ARROW_TRIANGLE_VARIATION_DISPLAY = new ExtraKeyDisplayMap() {{

            put("LEFT", "◀");
            put("RIGHT", "▶");
            put("UP", "▲");
            put("DOWN", "▼");
        }};

        public static final ExtraKeyDisplayMap NOT_KNOWN_ISO_CHARACTERS = new ExtraKeyDisplayMap() {{


            put("CTRL", "⎈");
            put("ALT", "⎇");
            put("ESC", "⎋");
        }};

        public static final ExtraKeyDisplayMap NICER_LOOKING_DISPLAY = new ExtraKeyDisplayMap() {{

            put("-", "―");
        }};

        /**
         * Full Iso
         */
        public static final ExtraKeyDisplayMap FULL_ISO_CHAR_DISPLAY = new ExtraKeyDisplayMap() {{
            putAll(CLASSIC_ARROWS_DISPLAY);
            putAll(WELL_KNOWN_CHARACTERS_DISPLAY);
            putAll(LESS_KNOWN_CHARACTERS_DISPLAY);
            putAll(NICER_LOOKING_DISPLAY);
            putAll(NOT_KNOWN_ISO_CHARACTERS);
        }};

        /**
         * Only arrows
         */
        public static final ExtraKeyDisplayMap ARROWS_ONLY_CHAR_DISPLAY = new ExtraKeyDisplayMap() {{
            putAll(CLASSIC_ARROWS_DISPLAY);


            putAll(NICER_LOOKING_DISPLAY);
        }};

        /**
         * Classic symbols and less known symbols
         */
        public static final ExtraKeyDisplayMap LOTS_OF_ARROWS_CHAR_DISPLAY = new ExtraKeyDisplayMap() {{
            putAll(CLASSIC_ARROWS_DISPLAY);
            putAll(WELL_KNOWN_CHARACTERS_DISPLAY);
            putAll(LESS_KNOWN_CHARACTERS_DISPLAY);
            putAll(NICER_LOOKING_DISPLAY);
        }};

        /**
         * Some classic symbols everybody knows
         */
        public static final ExtraKeyDisplayMap DEFAULT_CHAR_DISPLAY = new ExtraKeyDisplayMap() {{
            putAll(CLASSIC_ARROWS_DISPLAY);
            putAll(WELL_KNOWN_CHARACTERS_DISPLAY);
            putAll(NICER_LOOKING_DISPLAY);

        }};

    }



    /**
     * Aliases for the keys
     */
    public static final ExtraKeyDisplayMap CONTROL_CHARS_ALIASES = new ExtraKeyDisplayMap() {{
        put("ESCAPE", "ESC");
        put("CONTROL", "CTRL");
        put("SHFT", "SHIFT");
        put("RETURN", "ENTER");
        put("FUNCTION", "FN");



        put("LT", "LEFT");
        put("RT", "RIGHT");
        put("DN", "DOWN");


        put("PAGEUP", "PGUP");
        put("PAGE_UP", "PGUP");
        put("PAGE UP", "PGUP");
        put("PAGE-UP", "PGUP");




        put("PAGEDOWN", "PGDN");
        put("PAGE_DOWN", "PGDN");
        put("PAGE-DOWN", "PGDN");

        put("DELETE", "DEL");
        put("BACKSPACE", "BKSP");


        put("BACKSLASH", "\\");
        put("QUOTE", "\"");
        put("APOSTROPHE", "'");
    }};

}
