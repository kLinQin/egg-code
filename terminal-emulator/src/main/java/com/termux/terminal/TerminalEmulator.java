package com.termux.terminal;

import android.util.Base64;

import java.net.URI;import java.net.URISyntaxException;import java.net.URLDecoder;import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 * <p>
 * References:
 * <ul>
 * <li>http://invisible-island.net/xterm/ctlseqs/ctlseqs.html</li>
 * <li>http://en.wikipedia.org/wiki/ANSI_escape_code</li>
 * <li>http://man.he.net/man4/console_codes</li>
 * <li>http://bazaar.launchpad.net/~leonerd/libvterm/trunk/view/head:/src/state.c</li>
 * <li>http://www.columbia.edu/~kermit/k95manual/iso2022.html</li>
 * <li>http://www.vt100.net/docs/vt510-rm/chapter4</li>
 * <li>http://en.wikipedia.org/wiki/ISO/IEC_2022 - for 7-bit and 8-bit GL GR explanation</li>
 * <li>http://bjh21.me.uk/all-escapes/all-escapes.txt - extensive!</li>
 * <li>http://woldlab.caltech.edu/~diane/kde4.10/workingdir/kubuntu/konsole/doc/developer/old-documents/VT100/techref.
 * html - document for konsole - accessible!</li>
 * </ul>
 */
public final class TerminalEmulator {

    /**
     * Log unknown or unimplemented escape sequences received from the shell process.
     */
    private static final boolean LOG_ESCAPE_SEQUENCES = false;

    public static final int MOUSE_LEFT_BUTTON = 0;

    /**
     * Mouse moving while having left mouse button pressed.
     */
    public static final int MOUSE_LEFT_BUTTON_MOVED = 32;
    public static final int MOUSE_WHEELUP_BUTTON = 64;
    public static final int MOUSE_WHEELDOWN_BUTTON = 65;

    /**
     * Used for invalid data - http://en.wikipedia.org/wiki/Replacement_character#Replacement_character
     */
    public static final int UNICODE_REPLACEMENT_CHAR = 0xFFFD;

    /**
     * Escape processing: Not currently in an escape sequence.
     */
    private static final int ESC_NONE = 0;
    /**
     * Escape processing: Have seen an ESC character - proceed to {@link #doEsc(int)}
     */
    private static final int ESC = 1;
    /**
     * Escape processing: Have seen ESC POUND
     */
    private static final int ESC_POUND = 2;
    /**
     * Escape processing: Have seen ESC and a character-set-select ( char
     */
    private static final int ESC_SELECT_LEFT_PAREN = 3;
    /**
     * Escape processing: Have seen ESC and a character-set-select ) char
     */
    private static final int ESC_SELECT_RIGHT_PAREN = 4;
    /**
     * Escape processing: "ESC [" or CSI (Control Sequence Introducer).
     */
    private static final int ESC_CSI = 6;
    /**
     * Escape processing: ESC [ ?
     */
    private static final int ESC_CSI_QUESTIONMARK = 7;
    /**
     * Escape processing: ESC [ $
     */
    private static final int ESC_CSI_DOLLAR = 8;
    /**
     * Escape processing: ESC %
     */
    private static final int ESC_PERCENT = 9;
    /**
     * Escape processing: ESC ] (AKA OSC - Operating System Controls)
     */
    private static final int ESC_OSC = 10;
    /**
     * Escape processing: ESC ] (AKA OSC - Operating System Controls) ESC
     */
    private static final int ESC_OSC_ESC = 11;
    /**
     * Escape processing: ESC [ >
     */
    private static final int ESC_CSI_BIGGERTHAN = 12;
    /**
     * Escape procession: "ESC P" or Device Control String (DCS)
     */
    private static final int ESC_P = 13;
    /**
     * Escape processing: CSI >
     */
    private static final int ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14;
    /**
     * Escape processing: CSI $ARGS ' '
     */
    private static final int ESC_CSI_ARGS_SPACE = 15;
    /**
     * Escape processing: CSI $ARGS '*'
     */
    private static final int ESC_CSI_ARGS_ASTERIX = 16;
    /**
     * Escape processing: CSI "
     */
    private static final int ESC_CSI_DOUBLE_QUOTE = 17;
    /**
     * Escape processing: CSI '
     */
    private static final int ESC_CSI_SINGLE_QUOTE = 18;
    /**
     * Escape processing: CSI !
     */
    private static final int ESC_CSI_EXCLAMATION = 19;
    /**
     * Escape processing: "ESC _" or Application Program Command (APC).
     */
    private static final int ESC_APC = 20;
    /**
     * Escape processing: "ESC _" or Application Program Command (APC), followed by Escape.
     */
    private static final int ESC_APC_ESCAPE = 21;
    /**
     * Escape processing: ESC [ <parameter bytes>
     */
    private static final int ESC_CSI_UNSUPPORTED_PARAMETER_BYTE = 22;
    /**
     * Escape processing: ESC [ <parameter bytes> <intermediate bytes>
     */
    private static final int ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE = 23;

    /**
     * The number of parameter arguments including colon separated sub-parameters.
     */
    private static final int MAX_ESCAPE_PARAMETERS = 32;

    /**
     * Needs to be large enough to contain reasonable OSC 52 pastes.
     */
    private static final int MAX_OSC_STRING_LENGTH = 8192;

    /**
     * DECSET 1 - application cursor keys.
     */
    private static final int DECSET_BIT_APPLICATION_CURSOR_KEYS = 1;
    private static final int DECSET_BIT_REVERSE_VIDEO = 1 << 1;
    /**
     * http://www.vt100.net/docs/vt510-rm/DECOM: "When DECOM is set, the home cursor position is at the upper-left
     * corner of the screen, within the margins. The starting point for line numbers depends on the current top margin
     * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
     * upper-left corner of the screen. The starting point for line numbers is independent of the margins. The cursor
     * can move outside of the margins."
     */
    private static final int DECSET_BIT_ORIGIN_MODE = 1 << 2;
    /**
     * http://www.vt100.net/docs/vt510-rm/DECAWM: "If the DECAWM function is set, then graphic characters received when
     * the cursor is at the right border of the page appear at the beginning of the next line. Any text on the page
     * scrolls up if the cursor is at the end of the scrolling region. If the DECAWM function is reset, then graphic
     * characters received when the cursor is at the right border of the page replace characters already on the page."
     */
    private static final int DECSET_BIT_AUTOWRAP = 1 << 3;
    /**
     * DECSET 25 - if the cursor should be enabled, {@link #isCursorEnabled()}.
     */
    private static final int DECSET_BIT_CURSOR_ENABLED = 1 << 4;
    private static final int DECSET_BIT_APPLICATION_KEYPAD = 1 << 5;
    /**
     * DECSET 1000 - if to report mouse press&release events.
     */
    private static final int DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 << 6;
    /**
     * DECSET 1002 - like 1000, but report moving mouse while pressed.
     */
    private static final int DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 << 7;
    /**
     * DECSET 1004 - NOT implemented.
     */
    private static final int DECSET_BIT_SEND_FOCUS_EVENTS = 1 << 8;
    /**
     * DECSET 1006 - SGR-like mouse protocol (the modern sane choice).
     */
    private static final int DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 << 9;
    /**
     * DECSET 2004 - see {@link #paste(String)}
     */
    private static final int DECSET_BIT_BRACKETED_PASTE_MODE = 1 << 10;
    /**
     * Toggled with DECLRMM - http://www.vt100.net/docs/vt510-rm/DECLRMM
     */
    private static final int DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 << 11;
    /**
     * Not really DECSET bit... - http://www.vt100.net/docs/vt510-rm/DECSACE
     */
    private static final int DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 << 12;


    private String mTitle;
    private final Stack<String> mTitleStack = new Stack<>();

    /**
     * The cursor position. Between (0,0) and (mRows-1, mColumns-1).
     */
    private int mCursorRow, mCursorCol;

    /**
     * The number of character rows and columns in the terminal screen.
     */
    public int mRows, mColumns;

    /**
     * Size of a terminal cell in pixels.
     */
    private int mCellWidthPixels, mCellHeightPixels;

    /**
     * The number of terminal transcript rows that can be scrolled back to.
     */
    public static final int TERMINAL_TRANSCRIPT_ROWS_MIN = 100;
    public static final int TERMINAL_TRANSCRIPT_ROWS_MAX = 50000;
    public static final int DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000;


    /* The supported terminal cursor styles. */

    public static final int TERMINAL_CURSOR_STYLE_BLOCK = 0;
    public static final int TERMINAL_CURSOR_STYLE_UNDERLINE = 1;
    public static final int TERMINAL_CURSOR_STYLE_BAR = 2;
    public static final int DEFAULT_TERMINAL_CURSOR_STYLE = TERMINAL_CURSOR_STYLE_BLOCK;
    public static final Integer[] TERMINAL_CURSOR_STYLES_LIST = new Integer[]{TERMINAL_CURSOR_STYLE_BLOCK, TERMINAL_CURSOR_STYLE_UNDERLINE, TERMINAL_CURSOR_STYLE_BAR};

    /**
     * The terminal cursor styles.
     */
    private int mCursorStyle = DEFAULT_TERMINAL_CURSOR_STYLE;


    /**
     * The normal screen buffer. Stores the characters that appear on the screen of the emulated terminal.
     */
    private final TerminalBuffer mMainBuffer;
    /**
     * The alternate screen buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate screen buffer is active, you cannot scroll back to view saved lines).
     * <p>
     * See http://www.xfree86.org/current/ctlseqs.html#The%20Alternate%20Screen%20Buffer
     */
    final TerminalBuffer mAltBuffer;
    /**
     * The current screen buffer, pointing at either {@link #mMainBuffer} or {@link #mAltBuffer}.
     */
    private TerminalBuffer mScreen;

    /**
     * The terminal session this emulator is bound to.
     */
    private final TerminalOutput mSession;

    TerminalSessionClient mClient;

    /**
     * Keeps track of the current argument of the current escape sequence. Ranges from 0 to MAX_ESCAPE_PARAMETERS-1.
     */
    private int mArgIndex;
    /**
     * Holds the arguments of the current escape sequence.
     */
    private final int[] mArgs = new int[MAX_ESCAPE_PARAMETERS];
    /**
     * Holds the bit flags which arguments are sub parameters (after a colon) - bit N is set if <code>mArgs[N]</code> is a sub parameter.
     */
    private int mArgsSubParamsBitSet = 0;

    /**
     * Holds OSC and device control arguments, which can be strings.
     */
    private final StringBuilder mOSCOrDeviceControlArgs = new StringBuilder();

    /**
     * True if the current escape sequence should continue, false if the current escape sequence should be terminated.
     * Used when parsing a single character.
     */
    private boolean mContinueSequence;

    /**
     * The current state of the escape sequence state machine. One of the ESC_* constants.
     */
    private int mEscapeState;

    private final SavedScreenState mSavedStateMain = new SavedScreenState();
    private final SavedScreenState mSavedStateAlt = new SavedScreenState();

    /**
     * http://www.vt100.net/docs/vt102-ug/table5-15.html
     */
    private boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;

    /**
     * @see TerminalEmulator#mapDecSetBitToInternalBit(int)
     */
    private int mCurrentDecSetFlags, mSavedDecSetFlags;

    /**
     * If insert mode (as opposed to replace mode) is active. In insert mode new characters are inserted, pushing
     * existing text to the right. Characters moved past the right margin are lost.
     */
    private boolean mInsertMode;

    /**
     * An array of tab stops. mTabStop[i] is true if there is a tab stop set for column i.
     */
    private boolean[] mTabStop;

    /**
     * Top margin of screen for scrolling ranges from 0 to mRows-2. Bottom margin ranges from mTopMargin + 2 to mRows
     * (Defines the first row after the scrolling region). Left/right margin in [0, mColumns].
     */
    private int mTopMargin, mBottomMargin, mLeftMargin, mRightMargin;

    /**
     * If the next character to be emitted will be automatically wrapped to the next line. Used to disambiguate the case
     * where the cursor is positioned on the last column (mColumns-1). When standing there, a written character will be
     * output in the last column, the cursor not moving but this flag will be set. When outputting another character
     * this will move to the next line.
     */
    private boolean mAboutToAutoWrap;

    /**
     * If the cursor blinking is enabled. It requires cursor itself to be enabled, which is controlled
     * byt whether {@link #DECSET_BIT_CURSOR_ENABLED} bit is set or not.
     */
    private boolean mCursorBlinkingEnabled;

    /**
     * If currently cursor should be in a visible state or not if {@link #mCursorBlinkingEnabled}
     * is {@code true}.
     */
    private boolean mCursorBlinkState;

    /**
     * Current foreground, background and underline colors. Can either be a color index in [0,259] or a truecolor (24-bit) value.
     * For a 24-bit value the top byte (0xff000000) is set.
     *
     * <p>Note that the underline color is currently parsed but not yet used during rendering.
     *
     * @see TextStyle
     */
    int mForeColor, mBackColor, mUnderlineColor;

    /**
     * Current {@link TextStyle} effect.
     */
    int mEffect;

    /**
     * The number of scrolled lines since last calling {@link #clearScrollCounter()}. Used for moving selection up along
     * with the scrolling text.
     */
    private int mScrollCounter = 0;

    /**
     * If automatic scrolling of terminal is disabled
     */
    private boolean mAutoScrollDisabled;

    private byte mUtf8ToFollow, mUtf8Index;
    private final byte[] mUtf8InputBuffer = new byte[4];
    private int mLastEmittedCodePoint = -1;

    public final TerminalColors mColors = new TerminalColors();

    private static final String LOG_TAG = "TerminalEmulator";

    private boolean isDecsetInternalBitSet(int bit) {
        return (mCurrentDecSetFlags & bit) != 0;
    }

    private void setDecsetinternalBit(int internalBit, boolean set) {
        if (set) {

            if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false);
            } else if (internalBit == DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false);
            }
        }
        if (set) {
            mCurrentDecSetFlags |= internalBit;
        } else {
            mCurrentDecSetFlags &= ~internalBit;
        }
    }

    static int mapDecSetBitToInternalBit(int decsetBit) {
        switch (decsetBit) {
            case 1:
                return DECSET_BIT_APPLICATION_CURSOR_KEYS;
            case 5:
                return DECSET_BIT_REVERSE_VIDEO;
            case 6:
                return DECSET_BIT_ORIGIN_MODE;
            case 7:
                return DECSET_BIT_AUTOWRAP;
            case 25:
                return DECSET_BIT_CURSOR_ENABLED;
            case 66:
                return DECSET_BIT_APPLICATION_KEYPAD;
            case 69:
                return DECSET_BIT_LEFTRIGHT_MARGIN_MODE;
            case 1000:
                return DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE;
            case 1002:
                return DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT;
            case 1004:
                return DECSET_BIT_SEND_FOCUS_EVENTS;
            case 1006:
                return DECSET_BIT_MOUSE_PROTOCOL_SGR;
            case 2004:
                return DECSET_BIT_BRACKETED_PASTE_MODE;
            default:
                return -1;

        }
    }

    public TerminalEmulator(TerminalOutput session, int columns, int rows, int cellWidthPixels, int cellHeightPixels, Integer transcriptRows, TerminalSessionClient client) {
        mSession = session;
        mScreen = mMainBuffer = new TerminalBuffer(columns, getTerminalTranscriptRows(transcriptRows), rows);
        mAltBuffer = new TerminalBuffer(columns, rows, rows);
        mClient = client;
        mRows = rows;
        mColumns = columns;
        mCellWidthPixels = cellWidthPixels;
        mCellHeightPixels = cellHeightPixels;
        mTabStop = new boolean[mColumns];
        reset();
    }

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
        setCursorStyle();
        setCursorBlinkState(true);
    }

    public TerminalBuffer getScreen() {
        return mScreen;
    }

    public boolean isAlternateBufferActive() {
        return mScreen == mAltBuffer;
    }

    private int getTerminalTranscriptRows(Integer transcriptRows) {
        if (transcriptRows == null || transcriptRows < TERMINAL_TRANSCRIPT_ROWS_MIN || transcriptRows > TERMINAL_TRANSCRIPT_ROWS_MAX)
            return DEFAULT_TERMINAL_TRANSCRIPT_ROWS;
        else
            return transcriptRows;
    }

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    public void sendMouseEvent(int mouseButton, int column, int row, boolean pressed) {
        if (column < 1) column = 1;
        if (column > mColumns) column = mColumns;
        if (row < 1) row = 1;
        if (row > mRows) row = mRows;

        if (mouseButton == MOUSE_LEFT_BUTTON_MOVED && !isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) {

        } else if (isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)) {
            mSession.write(String.format("\033[<%d;%d;%d" + (pressed ? 'M' : 'm'), mouseButton, column, row));
        } else {
            mouseButton = pressed ? mouseButton : 3;

            boolean out_of_bounds = column > 255 - 32 || row > 255 - 32;
            if (!out_of_bounds) {
                byte[] data = {'\033', '[', 'M', (byte) (32 + mouseButton), (byte) (32 + column), (byte) (32 + row)};
                mSession.write(data, 0, data.length);
            }
        }
    }

    public void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        this.mCellWidthPixels = cellWidthPixels;
        this.mCellHeightPixels = cellHeightPixels;

        if (mRows == rows && mColumns == columns) {
            return;
        } else if (columns < 2 || rows < 2) {
            throw new IllegalArgumentException("rows=" + rows + ", columns=" + columns);
        }

        if (mRows != rows) {
            mRows = rows;
            mTopMargin = 0;
            mBottomMargin = mRows;
        }
        if (mColumns != columns) {
            int oldColumns = mColumns;
            mColumns = columns;
            boolean[] oldTabStop = mTabStop;
            mTabStop = new boolean[mColumns];
            setDefaultTabStops();
            int toTransfer = Math.min(oldColumns, columns);
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer);
            mLeftMargin = 0;
            mRightMargin = mColumns;
        }

        resizeScreen();
    }

    private void resizeScreen() {
        final int[] cursor = {mCursorCol, mCursorRow};
        int newTotalRows = (mScreen == mAltBuffer) ? mRows : mMainBuffer.mTotalRows;
        mScreen.resize(mColumns, mRows, newTotalRows, cursor, getStyle(), isAlternateBufferActive());
        mCursorCol = cursor[0];
        mCursorRow = cursor[1];
    }

    public int getCursorRow() {
        return mCursorRow;
    }

    public int getCursorCol() {
        return mCursorCol;
    }

    /**
     * Get the terminal cursor style. It will be one of {@link #TERMINAL_CURSOR_STYLES_LIST}
     */
    public int getCursorStyle() {
        return mCursorStyle;
    }

    /**
     * Set the terminal cursor style.
     */
    public void setCursorStyle() {
        Integer cursorStyle = null;

        if (mClient != null)
            cursorStyle = mClient.getTerminalCursorStyle();

        if (cursorStyle == null || !Arrays.asList(TERMINAL_CURSOR_STYLES_LIST).contains(cursorStyle))
            mCursorStyle = DEFAULT_TERMINAL_CURSOR_STYLE;
        else
            mCursorStyle = cursorStyle;
    }

    public boolean isReverseVideo() {
        return isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO);
    }


    public boolean isCursorEnabled() {
        return isDecsetInternalBitSet(DECSET_BIT_CURSOR_ENABLED);
    }

    public boolean shouldCursorBeVisible() {
        if (!isCursorEnabled())
            return false;
        else
            return !mCursorBlinkingEnabled || mCursorBlinkState;
    }

    public void setCursorBlinkingEnabled(boolean cursorBlinkingEnabled) {
        this.mCursorBlinkingEnabled = cursorBlinkingEnabled;
    }

    public void setCursorBlinkState(boolean cursorBlinkState) {
        this.mCursorBlinkState = cursorBlinkState;
    }


    public boolean isKeypadApplicationMode() {
        return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD);
    }

    public boolean isCursorKeysApplicationMode() {
        return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS);
    }

    /**
     * If mouse events are being sent as escape codes to the terminal.
     */
    public boolean isMouseTrackingActive() {
        return isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT);
    }

    private void setDefaultTabStops() {
        for (int i = 0; i < mColumns; i++)
            mTabStop[i] = (i & 7) == 0 && i != 0;
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    public void append(byte[] buffer, int length) {
        for (int i = 0; i < length; i++)
            processByte(buffer[i]);
    }

    private void processByte(byte byteToProcess) {
        if (mUtf8ToFollow > 0) {
            if ((byteToProcess & 0b11000000) == 0b10000000) {

                mUtf8InputBuffer[mUtf8Index++] = byteToProcess;
                if (--mUtf8ToFollow == 0) {
                    byte firstByteMask = (byte) (mUtf8Index == 2 ? 0b00011111 : (mUtf8Index == 3 ? 0b00001111 : 0b00000111));
                    int codePoint = (mUtf8InputBuffer[0] & firstByteMask);
                    for (int i = 1; i < mUtf8Index; i++)
                        codePoint = ((codePoint << 6) | (mUtf8InputBuffer[i] & 0b00111111));
                    if (((codePoint <= 0b1111111) && mUtf8Index > 1) || (codePoint < 0b11111111111 && mUtf8Index > 2)
                            || (codePoint < 0b1111111111111111 && mUtf8Index > 3)) {

                        codePoint = UNICODE_REPLACEMENT_CHAR;
                    }

                    mUtf8Index = mUtf8ToFollow = 0;

                    if (codePoint >= 0x80 && codePoint <= 0x9F) {





                    } else {
                        switch (Character.getType(codePoint)) {
                            case Character.UNASSIGNED:
                            case Character.SURROGATE:
                                codePoint = UNICODE_REPLACEMENT_CHAR;
                        }
                        processCodePoint(codePoint);
                    }
                }
            } else {

                mUtf8Index = mUtf8ToFollow = 0;
                emitCodePoint(UNICODE_REPLACEMENT_CHAR);







                processByte(byteToProcess);
            }
        } else {
            if ((byteToProcess & 0b10000000) == 0) {
                processCodePoint(byteToProcess);
                return;
            } else if ((byteToProcess & 0b11100000) == 0b11000000) {
                mUtf8ToFollow = 1;
            } else if ((byteToProcess & 0b11110000) == 0b11100000) {
                mUtf8ToFollow = 2;
            } else if ((byteToProcess & 0b11111000) == 0b11110000) {
                mUtf8ToFollow = 3;
            } else {

                processCodePoint(UNICODE_REPLACEMENT_CHAR);
                return;
            }
            mUtf8InputBuffer[mUtf8Index++] = byteToProcess;
        }
    }

    public void processCodePoint(int b) {

        if (mEscapeState == ESC_APC) {
            doApc(b);
            return;
        } else if (mEscapeState == ESC_APC_ESCAPE) {
            doApcEscape(b);
            return;
        }

        switch (b) {
            case 0:
                break;
            case 7:
                if (mEscapeState == ESC_OSC)
                    doOsc(b);
                else
                    mSession.onBell();
                break;
            case 8:
                if (mLeftMargin == mCursorCol) {

                    int previousRow = mCursorRow - 1;
                    if (previousRow >= 0 && mScreen.getLineWrap(previousRow)) {
                        mScreen.clearLineWrap(previousRow);
                        setCursorRowCol(previousRow, mRightMargin - 1);
                    }
                } else {
                    setCursorCol(mCursorCol - 1);
                }
                break;
            case 9:







                mCursorCol = nextTabStop(1);
                break;
            case 10:
            case 11:
            case 12:
                doLinefeed();
                break;
            case 13:
                setCursorCol(mLeftMargin);
                break;
            case 14:
                mUseLineDrawingUsesG0 = false;
                break;
            case 15:
                mUseLineDrawingUsesG0 = true;
                break;
            case 24:
            case 26:
                if (mEscapeState != ESC_NONE) {

                    mEscapeState = ESC_NONE;
                    emitCodePoint(127);
                }
                break;
            case 27:

                if (mEscapeState == ESC_P) {

                    return;
                } else if (mEscapeState != ESC_OSC) {
                    startEscapeSequence();
                } else {
                    doOsc(b);
                }
                break;
            default:
                mContinueSequence = false;
                switch (mEscapeState) {
                    case ESC_NONE:
                        if (b >= 32) emitCodePoint(b);
                        break;
                    case ESC:
                        doEsc(b);
                        break;
                    case ESC_POUND:
                        doEscPound(b);
                        break;
                    case ESC_SELECT_LEFT_PAREN:
                        mUseLineDrawingG0 = (b == '0');
                        break;
                    case ESC_SELECT_RIGHT_PAREN:
                        mUseLineDrawingG1 = (b == '0');
                        break;
                    case ESC_CSI:
                        doCsi(b);
                        break;
                    case ESC_CSI_UNSUPPORTED_PARAMETER_BYTE:
                    case ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE:
                        doCsiUnsupportedParameterOrIntermediateByte(b);
                        break;
                    case ESC_CSI_EXCLAMATION:
                        if (b == 'p') {
                            reset();
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_QUESTIONMARK:
                        doCsiQuestionMark(b);
                        break;
                    case ESC_CSI_BIGGERTHAN:
                        doCsiBiggerThan(b);
                        break;
                    case ESC_CSI_DOLLAR:
                        boolean originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE);
                        int effectiveTopMargin = originMode ? mTopMargin : 0;
                        int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
                        int effectiveLeftMargin = originMode ? mLeftMargin : 0;
                        int effectiveRightMargin = originMode ? mRightMargin : mColumns;
                        switch (b) {
                            case 'v':









                                int topSource = Math.min(getArg(0, 1, true) - 1 + effectiveTopMargin, mRows);
                                int leftSource = Math.min(getArg(1, 1, true) - 1 + effectiveLeftMargin, mColumns);

                                int bottomSource = Math.min(Math.max(getArg(2, mRows, true) + effectiveTopMargin, topSource), mRows);
                                int rightSource = Math.min(Math.max(getArg(3, mColumns, true) + effectiveLeftMargin, leftSource), mColumns);

                                int destionationTop = Math.min(getArg(5, 1, true) - 1 + effectiveTopMargin, mRows);
                                int destinationLeft = Math.min(getArg(6, 1, true) - 1 + effectiveLeftMargin, mColumns);

                                int heightToCopy = Math.min(mRows - destionationTop, bottomSource - topSource);
                                int widthToCopy = Math.min(mColumns - destinationLeft, rightSource - leftSource);
                                mScreen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop);
                                break;
                            case '{':

                            case 'x':

                            case 'z':

                                boolean erase = b != 'x';
                                boolean selective = b == '{';

                                boolean keepVisualAttributes = erase && selective;
                                int argIndex = 0;
                                int fillChar = erase ? ' ' : getArg(argIndex++, -1, true);


                                if ((fillChar >= 32 && fillChar <= 126) || (fillChar >= 160 && fillChar <= 255)) {


                                    int top = Math.min(getArg(argIndex++, 1, true) + effectiveTopMargin, effectiveBottomMargin + 1);
                                    int left = Math.min(getArg(argIndex++, 1, true) + effectiveLeftMargin, effectiveRightMargin + 1);
                                    int bottom = Math.min(getArg(argIndex++, mRows, true) + effectiveTopMargin, effectiveBottomMargin);
                                    int right = Math.min(getArg(argIndex, mColumns, true) + effectiveLeftMargin, effectiveRightMargin);
                                    long style = getStyle();
                                    for (int row = top - 1; row < bottom; row++)
                                        for (int col = left - 1; col < right; col++)
                                            if (!selective || (TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                                                mScreen.setChar(col, row, fillChar, keepVisualAttributes ? mScreen.getStyleAt(row, col) : style);
                                }
                                break;
                            case 'r':

                            case 't':

                                boolean reverse = b == 't';

                                int top = Math.min(getArg(0, 1, true) - 1, effectiveBottomMargin) + effectiveTopMargin;
                                int left = Math.min(getArg(1, 1, true) - 1, effectiveRightMargin) + effectiveLeftMargin;
                                int bottom = Math.min(getArg(2, mRows, true) + 1, effectiveBottomMargin - 1) + effectiveTopMargin;
                                int right = Math.min(getArg(3, mColumns, true) + 1, effectiveRightMargin - 1) + effectiveLeftMargin;
                                if (mArgIndex >= 4) {
                                    if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
                                    for (int i = 4; i <= mArgIndex; i++) {
                                        int bits = 0;
                                        boolean setOrClear = true;
                                        switch (getArg(i, 0, false)) {
                                            case 0:
                                                bits = (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE | TextStyle.CHARACTER_ATTRIBUTE_BLINK
                                                        | TextStyle.CHARACTER_ATTRIBUTE_INVERSE);
                                                if (!reverse) setOrClear = false;
                                                break;
                                            case 1:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                                break;
                                            case 4:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                                break;
                                            case 5:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                                break;
                                            case 7:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                                break;
                                            case 22:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                                setOrClear = false;
                                                break;
                                            case 24:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                                setOrClear = false;
                                                break;
                                            case 25:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                                setOrClear = false;
                                                break;
                                            case 27:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                                setOrClear = false;
                                                break;
                                        }
                                        if (reverse && !setOrClear) {

                                        } else {
                                            mScreen.setOrClearEffect(bits, setOrClear, reverse, isDecsetInternalBitSet(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE),
                                                    effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right);
                                        }
                                    }
                                } else {

                                }
                                break;
                            default:
                                unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_DOUBLE_QUOTE:
                        if (b == 'q') {

                            int arg = getArg0(0);
                            if (arg == 0 || arg == 2) {

                                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
                            } else if (arg == 1) {

                                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
                            } else {
                                unknownSequence(b);
                            }
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_SINGLE_QUOTE:
                        if (b == '}') {
                            int columnsAfterCursor = mRightMargin - mCursorCol;
                            int columnsToInsert = Math.min(getArg0(1), columnsAfterCursor);
                            int columnsToMove = columnsAfterCursor - columnsToInsert;
                            mScreen.blockCopy(mCursorCol, 0, columnsToMove, mRows, mCursorCol + columnsToInsert, 0);
                            blockClear(mCursorCol, 0, columnsToInsert, mRows);
                        } else if (b == '~') {
                            int columnsAfterCursor = mRightMargin - mCursorCol;
                            int columnsToDelete = Math.min(getArg0(1), columnsAfterCursor);
                            int columnsToMove = columnsAfterCursor - columnsToDelete;
                            mScreen.blockCopy(mCursorCol + columnsToDelete, 0, columnsToMove, mRows, mCursorCol, 0);
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_PERCENT:
                        break;
                    case ESC_OSC:
                        doOsc(b);
                        break;
                    case ESC_OSC_ESC:
                        doOscEsc(b);
                        break;
                    case ESC_P:
                        doDeviceControl(b);
                        break;
                    case ESC_CSI_QUESTIONMARK_ARG_DOLLAR:
                        if (b == 'p') {

                            int mode = getArg0(0);
                            int value;
                            if (mode == 47 || mode == 1047 || mode == 1049) {

                                value = (mScreen == mAltBuffer) ? 1 : 2;
                            } else {
                                int internalBit = mapDecSetBitToInternalBit(mode);
                                if (internalBit != -1) {
                                    value = isDecsetInternalBitSet(internalBit) ? 1 : 2;
                                } else {
                                    Logger.logError(mClient, LOG_TAG, "Got DECRQM for unrecognized private DEC mode=" + mode);
                                    value = 0;
                                }
                            }
                            mSession.write(String.format(Locale.US, "\033[?%d;%d$y", mode, value));
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_ARGS_SPACE:
                        int arg = getArg0(0);
                        switch (b) {
                            case 'q':
                                switch (arg) {
                                    case 0:
                                    case 1:
                                    case 2:
                                        mCursorStyle = TERMINAL_CURSOR_STYLE_BLOCK;
                                        break;
                                    case 3:
                                    case 4:
                                        mCursorStyle = TERMINAL_CURSOR_STYLE_UNDERLINE;
                                        break;
                                    case 5:
                                    case 6:
                                        mCursorStyle = TERMINAL_CURSOR_STYLE_BAR;
                                        break;
                                }
                                break;
                            case 't':
                            case 'u':

                                break;
                            default:
                                unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_ARGS_ASTERIX:
                        int attributeChangeExtent = getArg0(0);
                        if (b == 'x' && (attributeChangeExtent >= 0 && attributeChangeExtent <= 2)) {

                            setDecsetinternalBit(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, attributeChangeExtent == 2);
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    default:
                        unknownSequence(b);
                        break;
                }
                if (!mContinueSequence) mEscapeState = ESC_NONE;
                break;
        }
    }

    /**
     * When in {@link #ESC_P} ("device control") sequence.
     */
    private void doDeviceControl(int b) {
        switch (b) {
            case (byte) '\\':
            {
                String dcs = mOSCOrDeviceControlArgs.toString();

                if (dcs.startsWith("$q")) {
                    if (dcs.equals("$q\"p")) {

                        String csiString = "64;1\"p";
                        mSession.write("\033P1$r" + csiString + "\033\\");
                    } else {
                        finishSequenceAndLogError("Unrecognized DECRQSS string: '" + dcs + "'");
                    }
                } else if (dcs.startsWith("+q")) {





































                    for (String part : dcs.substring(2).split(";")) {
                        if (part.length() % 2 == 0) {
                            StringBuilder transBuffer = new StringBuilder();
                            char c;
                            for (int i = 0; i < part.length(); i += 2) {
                                try {
                                    c = (char) Long.decode("0x" + part.charAt(i) + part.charAt(i + 1)).longValue();
                                } catch (NumberFormatException e) {
                                    Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Invalid device termcap/terminfo encoded name \"" + part + "\"", e);
                                    continue;
                                }
                                transBuffer.append(c);
                            }

                            String trans = transBuffer.toString();
                            String responseValue;
                            switch (trans) {
                                case "Co":
                                case "colors":
                                    responseValue = "256";
                                    break;
                                case "TN":
                                case "name":
                                    responseValue = "xterm";
                                    break;
                                default:
                                    responseValue = KeyHandler.getCodeFromTermcap(trans, isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS),
                                            isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD));
                                    break;
                            }
                            if (responseValue == null) {
                                switch (trans) {
                                    case "%1":
                                    case "&8":
                                        break;
                                    default:
                                        Logger.logWarn(mClient, LOG_TAG, "Unhandled termcap/terminfo name: '" + trans + "'");
                                }

                                mSession.write("\033P0+r" + part + "\033\\");
                            } else {
                                StringBuilder hexEncoded = new StringBuilder();
                                for (int j = 0; j < responseValue.length(); j++) {
                                    hexEncoded.append(String.format("%02X", (int) responseValue.charAt(j)));
                                }
                                mSession.write("\033P1+r" + part + "=" + hexEncoded + "\033\\");
                            }
                        } else {
                            Logger.logError(mClient, LOG_TAG, "Invalid device termcap/terminfo name of odd length: " + part);
                        }
                    }
                } else {
                    if (LOG_ESCAPE_SEQUENCES)
                        Logger.logError(mClient, LOG_TAG, "Unrecognized device control string: " + dcs);
                }
                finishSequence();
            }
            break;
            default:
                if (mOSCOrDeviceControlArgs.length() > MAX_OSC_STRING_LENGTH) {

                    mOSCOrDeviceControlArgs.setLength(0);
                    finishSequence();
                } else {
                    mOSCOrDeviceControlArgs.appendCodePoint(b);
                    continueSequence(mEscapeState);
                }
        }
    }

    /**
     * When in {@link #ESC_APC} (APC, Application Program Command) sequence.
     */
    private void doApc(int b) {
        if (b == 27) {
            continueSequence(ESC_APC_ESCAPE);
        }

    }

    /**
     * When in {@link #ESC_APC} (APC, Application Program Command) sequence.
     */
    private void doApcEscape(int b) {
        if (b == '\\') {

            finishSequence();
        } else {


            continueSequence(ESC_APC);
        }
    }

    private int nextTabStop(int numTabs) {
        for (int i = mCursorCol + 1; i < mColumns; i++)
            if (mTabStop[i] && --numTabs == 0) return Math.min(i, mRightMargin);
        return mRightMargin - 1;
    }

    /**
     * Process byte while in the {@link #ESC_CSI_UNSUPPORTED_PARAMETER_BYTE} or
     * {@link #ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE} escape state.
     * <p>
     * Parse unsupported parameter, intermediate and final bytes but ignore them.
     * <p>
     * > For Control Sequence Introducer, ... the ESC [ is followed by
     * > - any number (including none) of "parameter bytes" in the range 0x30–0x3F (ASCII 0–9:;<=>?),
     * > - then by any number of "intermediate bytes" in the range 0x20–0x2F (ASCII space and !"#$%&'()*+,-./),
     * > - then finally by a single "final byte" in the range 0x40–0x7E (ASCII @A–Z[\]^_`a–z{|}~).
     * <p>
     * - https://en.wikipedia.org/wiki/ANSI_escape_code#Control_Sequence_Introducer_commands
     * - https://invisible-island.net/xterm/ecma-48-parameter-format.html#section5.4
     */
    private void doCsiUnsupportedParameterOrIntermediateByte(int b) {
        if (mEscapeState == ESC_CSI_UNSUPPORTED_PARAMETER_BYTE && b >= 0x30 && b <= 0x3F) {


            continueSequence(ESC_CSI_UNSUPPORTED_PARAMETER_BYTE);
        } else if (b >= 0x20 && b <= 0x2F) {

            continueSequence(ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE);
        } else if (b >= 0x40 && b <= 0x7E) {


            finishSequence();
        } else {
            unknownSequence(b);
        }
    }

    /**
     * Process byte while in the {@link #ESC_CSI_QUESTIONMARK} escape state.
     */
    private void doCsiQuestionMark(int b) {
        switch (b) {
            case 'J':
            case 'K':
                mAboutToAutoWrap = false;
                int fillChar = ' ';
                int startCol = -1;
                int startRow = -1;
                int endCol = -1;
                int endRow = -1;
                boolean justRow = (b == 'K');
                switch (getArg0(0)) {
                    case 0:
                        startCol = mCursorCol;
                        startRow = mCursorRow;
                        endCol = mColumns;
                        endRow = justRow ? (mCursorRow + 1) : mRows;
                        break;
                    case 1:
                        startCol = 0;
                        startRow = justRow ? mCursorRow : 0;
                        endCol = mCursorCol + 1;
                        endRow = mCursorRow + 1;
                        break;
                    case 2:
                        startCol = 0;
                        startRow = justRow ? mCursorRow : 0;
                        endCol = mColumns;
                        endRow = justRow ? (mCursorRow + 1) : mRows;
                        break;
                    default:
                        unknownSequence(b);
                        break;
                }
                long style = getStyle();
                for (int row = startRow; row < endRow; row++) {
                    for (int col = startCol; col < endCol; col++) {
                        if ((TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                            mScreen.setChar(col, row, fillChar, style);
                    }
                }
                break;
            case 'h':
            case 'l':
                if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
                for (int i = 0; i <= mArgIndex; i++)
                    doDecSetOrReset(b == 'h', mArgs[i]);
                break;
            case 'n':
                switch (getArg0(-1)) {
                    case 6:

                        mSession.write(String.format(Locale.US, "\033[?%d;%d;1R", mCursorRow + 1, mCursorCol + 1));
                        break;
                    default:
                        finishSequence();
                        return;
                }
                break;
            case 'r':
            case 's':
                if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
                for (int i = 0; i <= mArgIndex; i++) {
                    int externalBit = mArgs[i];
                    int internalBit = mapDecSetBitToInternalBit(externalBit);
                    if (internalBit == -1) {
                        Logger.logWarn(mClient, LOG_TAG, "Ignoring request to save/recall decset bit=" + externalBit);
                    } else {
                        if (b == 's') {
                            mSavedDecSetFlags |= internalBit;
                        } else {
                            doDecSetOrReset((mSavedDecSetFlags & internalBit) != 0, externalBit);
                        }
                    }
                }
                break;
            case '$':
                continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR);
                return;
            default:
                parseArg(b);
        }
    }

    public void doDecSetOrReset(boolean setting, int externalBit) {
        int internalBit = mapDecSetBitToInternalBit(externalBit);
        if (internalBit != -1) {
            setDecsetinternalBit(internalBit, setting);
        }
        switch (externalBit) {
            case 1:
                break;
            case 3:




                mLeftMargin = mTopMargin = 0;
                mBottomMargin = mRows;
                mRightMargin = mColumns;

                setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false);

                blockClear(0, 0, mColumns, mRows);
                setCursorRowCol(0, 0);
                break;
            case 4:
                break;
            case 5:
                break;
            case 6:
                if (setting) setCursorPosition(0, 0);
                break;
            case 7:
            case 8:
            case 9:
            case 12:
            case 25:
                if (mClient != null)
                    mClient.onTerminalCursorStateChange(setting);
                break;
            case 40:
            case 45:
            case 66:
                break;
            case 69:
                if (!setting) {
                    mLeftMargin = 0;
                    mRightMargin = mColumns;
                }
                break;
            case 1000:
            case 1001:
            case 1002:
            case 1003:
            case 1004:
            case 1005:
            case 1006:
            case 1015:
            case 1034:
                break;
            case 1048:
                if (setting)
                    saveCursor();
                else
                    restoreCursor();
                break;
            case 47:
            case 1047:
            case 1049: {


                TerminalBuffer newScreen = setting ? mAltBuffer : mMainBuffer;
                if (newScreen != mScreen) {
                    boolean resized = !(newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows);
                    if (setting) saveCursor();
                    mScreen = newScreen;
                    if (!setting) {
                        int col = mSavedStateMain.mSavedCursorCol;
                        int row = mSavedStateMain.mSavedCursorRow;
                        restoreCursor();
                        if (resized) {

                            mCursorCol = col;
                            mCursorRow = row;
                        }
                    }

                    if (resized) resizeScreen();

                    if (newScreen == mAltBuffer)
                        newScreen.blockSet(0, 0, mColumns, mRows, ' ', getStyle());
                }
                break;
            }
            case 2004:

                break;
            default:
                unknownParameter(externalBit);
                break;
        }
    }

    private void doCsiBiggerThan(int b) {
        switch (b) {
            case 'c':










                mSession.write("\033[>41;320;0c");
                break;
            case 'm':
























































                Logger.logError(mClient, LOG_TAG, "(ignored) CSI > MODIFY RESOURCE: " + getArg0(-1) + " to " + getArg1(-1));
                break;
            default:
                parseArg(b);
                break;
        }
    }

    private void startEscapeSequence() {
        mEscapeState = ESC;
        mArgIndex = 0;
        Arrays.fill(mArgs, -1);
        mArgsSubParamsBitSet = 0;
    }

    private void doLinefeed() {
        boolean belowScrollingRegion = mCursorRow >= mBottomMargin;
        int newCursorRow = mCursorRow + 1;
        if (belowScrollingRegion) {

            if (mCursorRow != mRows - 1) {
                setCursorRow(newCursorRow);
            }
        } else {
            if (newCursorRow == mBottomMargin) {
                scrollDownOneLine();
                newCursorRow = mBottomMargin - 1;
            }
            setCursorRow(newCursorRow);
        }
    }

    private void continueSequence(int state) {
        mEscapeState = state;
        mContinueSequence = true;
    }

    private void doEscPound(int b) {
        switch (b) {
            case '8':
                mScreen.blockSet(0, 0, mColumns, mRows, 'E', getStyle());
                break;
            default:
                unknownSequence(b);
                break;
        }
    }

    /**
     * Encountering a character in the {@link #ESC} state.
     */
    private void doEsc(int b) {
        switch (b) {
            case '#':
                continueSequence(ESC_POUND);
                break;
            case '(':
                continueSequence(ESC_SELECT_LEFT_PAREN);
                break;
            case ')':
                continueSequence(ESC_SELECT_RIGHT_PAREN);
                break;
            case '6':
                if (mCursorCol > mLeftMargin) {
                    mCursorCol--;
                } else {
                    int rows = mBottomMargin - mTopMargin;
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin + 1, mTopMargin);
                    mScreen.blockSet(mLeftMargin, mTopMargin, 1, rows, ' ', TextStyle.encode(mForeColor, mBackColor, 0));
                }
                break;
            case '7':
                saveCursor();
                break;
            case '8':
                restoreCursor();
                break;
            case '9':
                if (mCursorCol < mRightMargin - 1) {
                    mCursorCol++;
                } else {
                    int rows = mBottomMargin - mTopMargin;
                    mScreen.blockCopy(mLeftMargin + 1, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin, mTopMargin);
                    mScreen.blockSet(mRightMargin - 1, mTopMargin, 1, rows, ' ', TextStyle.encode(mForeColor, mBackColor, 0));
                }
                break;
            case 'c':
                reset();
                mMainBuffer.clearTranscript();
                blockClear(0, 0, mColumns, mRows);
                setCursorPosition(0, 0);
                break;
            case 'D':
                doLinefeed();
                break;
            case 'E':
                setCursorCol(isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE) ? mLeftMargin : 0);
                doLinefeed();
                break;
            case 'F':
                setCursorRowCol(0, mBottomMargin - 1);
                break;
            case 'H':
                mTabStop[mCursorCol] = true;
                break;
            case 'M':


                if (mCursorRow <= mTopMargin) {
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, mBottomMargin - (mTopMargin + 1), mLeftMargin, mTopMargin + 1);
                    blockClear(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin);
                } else {
                    mCursorRow--;
                }
                break;
            case 'N':
            case '0':
                break;
            case 'P':
                mOSCOrDeviceControlArgs.setLength(0);
                continueSequence(ESC_P);
                break;
            case '[':
                continueSequence(ESC_CSI);
                break;
            case '=':
                setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true);
                break;
            case ']':
                mOSCOrDeviceControlArgs.setLength(0);
                continueSequence(ESC_OSC);
                break;
            case '>':
                setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, false);
                break;
            case '_':
                continueSequence(ESC_APC);
                break;
            default:
                unknownSequence(b);
                break;
        }
    }

    /**
     * DECSC save cursor - http://www.vt100.net/docs/vt510-rm/DECSC . See {@link #restoreCursor()}.
     */
    private void saveCursor() {
        SavedScreenState state = (mScreen == mMainBuffer) ? mSavedStateMain : mSavedStateAlt;
        state.mSavedCursorRow = mCursorRow;
        state.mSavedCursorCol = mCursorCol;
        state.mSavedEffect = mEffect;
        state.mSavedForeColor = mForeColor;
        state.mSavedBackColor = mBackColor;
        state.mSavedDecFlags = mCurrentDecSetFlags;
        state.mUseLineDrawingG0 = mUseLineDrawingG0;
        state.mUseLineDrawingG1 = mUseLineDrawingG1;
        state.mUseLineDrawingUsesG0 = mUseLineDrawingUsesG0;
    }

    /**
     * DECRS restore cursor - http://www.vt100.net/docs/vt510-rm/DECRC. See {@link #saveCursor()}.
     */
    private void restoreCursor() {
        SavedScreenState state = (mScreen == mMainBuffer) ? mSavedStateMain : mSavedStateAlt;
        setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol);
        mEffect = state.mSavedEffect;
        mForeColor = state.mSavedForeColor;
        mBackColor = state.mSavedBackColor;
        int mask = (DECSET_BIT_AUTOWRAP | DECSET_BIT_ORIGIN_MODE);
        mCurrentDecSetFlags = (mCurrentDecSetFlags & ~mask) | (state.mSavedDecFlags & mask);
        mUseLineDrawingG0 = state.mUseLineDrawingG0;
        mUseLineDrawingG1 = state.mUseLineDrawingG1;
        mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0;
    }

    /**
     * Following a CSI - Control Sequence Introducer, "\033[". {@link #ESC_CSI}.
     */
    private void doCsi(int b) {
        switch (b) {
            case '!':
                continueSequence(ESC_CSI_EXCLAMATION);
                break;
            case '"':
                continueSequence(ESC_CSI_DOUBLE_QUOTE);
                break;
            case '\'':
                continueSequence(ESC_CSI_SINGLE_QUOTE);
                break;
            case '$':
                continueSequence(ESC_CSI_DOLLAR);
                break;
            case '*':
                continueSequence(ESC_CSI_ARGS_ASTERIX);
                break;
            case '@': {

                mAboutToAutoWrap = false;
                int columnsAfterCursor = mColumns - mCursorCol;
                int spacesToInsert = Math.min(getArg0(1), columnsAfterCursor);
                int charsToMove = columnsAfterCursor - spacesToInsert;
                mScreen.blockCopy(mCursorCol, mCursorRow, charsToMove, 1, mCursorCol + spacesToInsert, mCursorRow);
                blockClear(mCursorCol, mCursorRow, spacesToInsert);
            }
            break;
            case 'A':
                setCursorRow(Math.max(0, mCursorRow - getArg0(1)));
                break;
            case 'B':
                setCursorRow(Math.min(mRows - 1, mCursorRow + getArg0(1)));
                break;
            case 'C':
            case 'a':
                setCursorCol(Math.min(mRightMargin - 1, mCursorCol + getArg0(1)));
                break;
            case 'D':
                setCursorCol(Math.max(mLeftMargin, mCursorCol - getArg0(1)));
                break;
            case 'E':
                setCursorPosition(0, mCursorRow + getArg0(1));
                break;
            case 'F':
                setCursorPosition(0, mCursorRow - getArg0(1));
                break;
            case 'G':
                setCursorCol(Math.min(Math.max(1, getArg0(1)), mColumns) - 1);
                break;
            case 'H':
            case 'f':
                setCursorPosition(getArg1(1) - 1, getArg0(1) - 1);
                break;
            case 'I':
                setCursorCol(nextTabStop(getArg0(1)));
                break;
            case 'J':

                switch (getArg0(0)) {
                    case 0:
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                        blockClear(0, mCursorRow + 1, mColumns, mRows - (mCursorRow + 1));
                        break;
                    case 1:
                        blockClear(0, 0, mColumns, mCursorRow);
                        blockClear(0, mCursorRow, mCursorCol + 1);
                        break;
                    case 2:

                        blockClear(0, 0, mColumns, mRows);
                        break;
                    case 3:
                        mMainBuffer.clearTranscript();
                        break;
                    default:
                        unknownSequence(b);
                        return;
                }
                mAboutToAutoWrap = false;
                break;
            case 'K':
                switch (getArg0(0)) {
                    case 0:
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                        break;
                    case 1:
                        blockClear(0, mCursorRow, mCursorCol + 1);
                        break;
                    case 2:
                        blockClear(0, mCursorRow, mColumns);
                        break;
                    default:
                        unknownSequence(b);
                        return;
                }
                mAboutToAutoWrap = false;
                break;
            case 'L':
            {
                int linesAfterCursor = mBottomMargin - mCursorRow;
                int linesToInsert = Math.min(getArg0(1), linesAfterCursor);
                int linesToMove = linesAfterCursor - linesToInsert;
                mScreen.blockCopy(0, mCursorRow, mColumns, linesToMove, 0, mCursorRow + linesToInsert);
                blockClear(0, mCursorRow, mColumns, linesToInsert);
            }
            break;
            case 'M':
            {
                mAboutToAutoWrap = false;
                int linesAfterCursor = mBottomMargin - mCursorRow;
                int linesToDelete = Math.min(getArg0(1), linesAfterCursor);
                int linesToMove = linesAfterCursor - linesToDelete;
                mScreen.blockCopy(0, mCursorRow + linesToDelete, mColumns, linesToMove, 0, mCursorRow);
                blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete);
            }
            break;
            case 'P':
            {





                mAboutToAutoWrap = false;
                int cellsAfterCursor = mColumns - mCursorCol;
                int cellsToDelete = Math.min(getArg0(1), cellsAfterCursor);
                int cellsToMove = cellsAfterCursor - cellsToDelete;
                mScreen.blockCopy(mCursorCol + cellsToDelete, mCursorRow, cellsToMove, 1, mCursorCol, mCursorRow);
                blockClear(mCursorCol + cellsToMove, mCursorRow, cellsToDelete);
            }
            break;
            case 'S': {
                final int linesToScroll = getArg0(1);
                for (int i = 0; i < linesToScroll; i++)
                    scrollDownOneLine();
                break;
            }
            case 'T':
                if (mArgIndex == 0) {




                    final int linesToScrollArg = getArg0(1);
                    final int linesBetweenTopAndBottomMargins = mBottomMargin - mTopMargin;
                    final int linesToScroll = Math.min(linesBetweenTopAndBottomMargins, linesToScrollArg);
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, linesBetweenTopAndBottomMargins - linesToScroll, mLeftMargin, mTopMargin + linesToScroll);
                    blockClear(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, linesToScroll);
                } else {

                    unimplementedSequence(b);
                }
                break;
            case 'X':
                mAboutToAutoWrap = false;
                mScreen.blockSet(mCursorCol, mCursorRow, Math.min(getArg0(1), mColumns - mCursorCol), 1, ' ', getStyle());
                break;
            case 'Z':
                int numberOfTabs = getArg0(1);
                int newCol = mLeftMargin;
                for (int i = mCursorCol - 1; i >= 0; i--)
                    if (mTabStop[i]) {
                        if (--numberOfTabs == 0) {
                            newCol = Math.max(i, mLeftMargin);
                            break;
                        }
                    }
                mCursorCol = newCol;
                break;
            case '?':
                continueSequence(ESC_CSI_QUESTIONMARK);
                break;
            case '>':
                continueSequence(ESC_CSI_BIGGERTHAN);
                break;
            case '<':
            case '=':
                continueSequence(ESC_CSI_UNSUPPORTED_PARAMETER_BYTE);
                break;
            case '`':
                setCursorColRespectingOriginMode(getArg0(1) - 1);
                break;
            case 'b':
                if (mLastEmittedCodePoint == -1) break;
                final int numRepeat = getArg0(1);
                for (int i = 0; i < numRepeat; i++) emitCodePoint(mLastEmittedCodePoint);
                break;
            case 'c':



                if (getArg0(0) == 0) mSession.write("\033[?64;1;2;6;9;15;18;21;22c");
                break;
            case 'd':
                setCursorRow(Math.min(Math.max(1, getArg0(1)), mRows) - 1);
                break;
            case 'e':
                setCursorPosition(mCursorCol, mCursorRow + getArg0(1));
                break;

            case 'g':
                switch (getArg0(0)) {
                    case 0:
                        mTabStop[mCursorCol] = false;
                        break;
                    case 3:
                        for (int i = 0; i < mColumns; i++) {
                            mTabStop[i] = false;
                        }
                        break;
                    default:

                        break;
                }
                break;
            case 'h':
                doSetMode(true);
                break;
            case 'l':
                doSetMode(false);
                break;
            case 'm':
                selectGraphicRendition();
                break;
            case 'n':

                switch (getArg0(0)) {
                    case 5:

                        byte[] dsr = {(byte) 27, (byte) '[', (byte) '0', (byte) 'n'};
                        mSession.write(dsr, 0, dsr.length);
                        break;
                    case 6:


                        mSession.write(String.format(Locale.US, "\033[%d;%dR", mCursorRow + 1, mCursorCol + 1));
                        break;
                    default:
                        break;
                }
                break;
            case 'r':
            {







                mTopMargin = Math.max(0, Math.min(getArg0(1) - 1, mRows - 2));
                mBottomMargin = Math.max(mTopMargin + 2, Math.min(getArg1(mRows), mRows));


                setCursorPosition(0, 0);
            }
            break;
            case 's':
                if (isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {

                    mLeftMargin = Math.min(getArg0(1) - 1, mColumns - 2);
                    mRightMargin = Math.max(mLeftMargin + 1, Math.min(getArg1(mColumns), mColumns));

                    setCursorPosition(0, 0);
                } else {

                    saveCursor();
                }
                break;
            case 't':
                switch (getArg0(0)) {
                    case 11:
                        mSession.write("\033[1t");
                        break;
                    case 13:
                        mSession.write("\033[3;0;0t");
                        break;
                    case 14:
                        mSession.write(String.format(Locale.US, "\033[4;%d;%dt", mRows * mCellHeightPixels, mColumns * mCellWidthPixels));
                        break;
                    case 16:
                        mSession.write(String.format(Locale.US, "\033[6;%d;%dt", mCellHeightPixels, mCellWidthPixels));
                        break;
                    case 18:
                        mSession.write(String.format(Locale.US, "\033[8;%d;%dt", mRows, mColumns));
                        break;
                    case 19:

                        mSession.write(String.format(Locale.US, "\033[9;%d;%dt", mRows, mColumns));
                        break;
                    case 20:
                        mSession.write("\033]LIconLabel\033\\");
                        break;
                    case 21:
                        mSession.write("\033]l\033\\");
                        break;
                    case 22:



                        mTitleStack.push(mTitle);
                        if (mTitleStack.size() > 20) {

                            mTitleStack.remove(0);
                        }
                        break;
                    case 23:
                        if (!mTitleStack.isEmpty()) setTitle(mTitleStack.pop());
                        break;
                    default:

                        break;
                }
                break;
            case 'u':
                restoreCursor();
                break;
            case ' ':
                continueSequence(ESC_CSI_ARGS_SPACE);
                break;
            default:
                parseArg(b);
                break;
        }
    }

    /**
     * Select Graphic Rendition (SGR) - see http://en.wikipedia.org/wiki/ANSI_escape_code#graphics.
     */
    private void selectGraphicRendition() {
        if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
        for (int i = 0; i <= mArgIndex; i++) {

            if ((mArgsSubParamsBitSet & (1 << i)) != 0) {
                continue;
            }

            int code = getArg(i, 0, false);
            if (code < 0) {
                if (mArgIndex > 0) {
                    continue;
                } else {
                    code = 0;
                }
            }
            if (code == 0) {
                mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
                mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
                mEffect = 0;
            } else if (code == 1) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BOLD;
            } else if (code == 2) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_DIM;
            } else if (code == 3) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
            } else if (code == 4) {
                if (i + 1 <= mArgIndex && ((mArgsSubParamsBitSet & (1 << (i + 1))) != 0)) {

                    i++;
                    if (mArgs[i] == 0) {

                        mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                    } else {

                        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                    }
                } else {
                    mEffect |= TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                }
            } else if (code == 5) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BLINK;
            } else if (code == 7) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
            } else if (code == 8) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
            } else if (code == 9) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
            } else if (code == 10) {

            } else if (code == 11) {

            } else if (code == 22) {
                mEffect &= ~(TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_DIM);
            } else if (code == 23) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
            } else if (code == 24) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
            } else if (code == 25) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_BLINK;
            } else if (code == 27) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
            } else if (code == 28) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
            } else if (code == 29) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
            } else if (code >= 30 && code <= 37) {
                mForeColor = code - 30;
            } else if (code == 38 || code == 48 || code == 58) {



                if (i + 2 > mArgIndex) continue;
                int firstArg = mArgs[i + 1];
                if (firstArg == 2) {
                    if (i + 4 > mArgIndex) {
                        Logger.logWarn(mClient, LOG_TAG, "Too few CSI" + code + ";2 RGB arguments");
                    } else {
                        int red = getArg(i + 2, 0, false);
                        int green = getArg(i + 3, 0, false);
                        int blue = getArg(i + 4, 0, false);

                        if (red < 0 || green < 0 || blue < 0 || red > 255 || green > 255 || blue > 255) {
                            finishSequenceAndLogError("Invalid RGB: " + red + "," + green + "," + blue);
                        } else {
                            int argbColor = 0xff_00_00_00 | (red << 16) | (green << 8) | blue;
                            switch (code) {
                                case 38:
                                    mForeColor = argbColor;
                                    break;
                                case 48:
                                    mBackColor = argbColor;
                                    break;
                                case 58:
                                    mUnderlineColor = argbColor;
                                    break;
                            }
                        }
                        i += 4;
                    }
                } else if (firstArg == 5) {
                    int color = getArg(i + 2, 0, false);
                    i += 2;
                    if (color >= 0 && color < TextStyle.NUM_INDEXED_COLORS) {
                        switch (code) {
                            case 38:
                                mForeColor = color;
                                break;
                            case 48:
                                mBackColor = color;
                                break;
                            case 58:
                                mUnderlineColor = color;
                                break;
                        }
                    } else {
                        if (LOG_ESCAPE_SEQUENCES)
                            Logger.logWarn(mClient, LOG_TAG, "Invalid color index: " + color);
                    }
                } else {
                    finishSequenceAndLogError("Invalid ISO-8613-3 SGR first argument: " + firstArg);
                }
            } else if (code == 39) {
                mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
            } else if (code >= 40 && code <= 47) {
                mBackColor = code - 40;
            } else if (code == 49) {
                mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
            } else if (code == 59) {
                mUnderlineColor = TextStyle.COLOR_INDEX_FOREGROUND;
            } else if (code >= 90 && code <= 97) {
                mForeColor = code - 90 + 8;
            } else if (code >= 100 && code <= 107) {
                mBackColor = code - 100 + 8;
            } else {
                if (LOG_ESCAPE_SEQUENCES)
                    Logger.logWarn(mClient, LOG_TAG, String.format("SGR unknown code %d", code));
            }
        }
    }

    private void doOsc(int b) {
        switch (b) {
            case 7:
                doOscSetTextParameters("\007");
                break;
            case 27:
                continueSequence(ESC_OSC_ESC);
                break;
            default:
                collectOSCArgs(b);
                break;
        }
    }

    private void doOscEsc(int b) {
        switch (b) {
            case '\\':
                doOscSetTextParameters("\033\\");
                break;
            default:


                collectOSCArgs(27);
                collectOSCArgs(b);
                continueSequence(ESC_OSC);
                break;
        }
    }

    /**
     * An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST.
     */
    private void doOscSetTextParameters(String bellOrStringTerminator) {
        int value = -1;
        String textParameter = "";

        for (int mOSCArgTokenizerIndex = 0; mOSCArgTokenizerIndex < mOSCOrDeviceControlArgs.length(); mOSCArgTokenizerIndex++) {
            char b = mOSCOrDeviceControlArgs.charAt(mOSCArgTokenizerIndex);
            if (b == ';') {
                textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1);
                break;
            } else if (b >= '0' && b <= '9') {
                value = ((value < 0) ? 0 : value * 10) + (b - '0');
            } else {
                unknownSequence(b);
                return;
            }
        }

        switch (value) {
            case 0:
            case 1:
            case 2:
                setTitle(textParameter);
                break;
            case 4:







                int colorIndex = -1;
                int parsingPairStart = -1;
                for (int i = 0; ; i++) {
                    boolean endOfInput = i == textParameter.length();
                    char b = endOfInput ? ';' : textParameter.charAt(i);
                    if (b == ';') {
                        if (parsingPairStart < 0) {
                            parsingPairStart = i + 1;
                        } else {
                            if (colorIndex < 0 || colorIndex > 255) {
                                unknownSequence(b);
                                return;
                            } else {
                                mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i));
                                mSession.onColorsChanged();
                                colorIndex = -1;
                                parsingPairStart = -1;
                            }
                        }
                    } else if (parsingPairStart >= 0) {

                    } else if (parsingPairStart < 0 && (b >= '0' && b <= '9')) {
                        colorIndex = ((colorIndex < 0) ? 0 : colorIndex * 10) + (b - '0');
                    } else {
                        unknownSequence(b);
                        return;
                    }
                    if (endOfInput) break;
                }
                break;
            case 7:
                handleOsc7(textParameter);
                break;
            case 10:
            case 11:
            case 12:
                int specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10);
                int lastSemiIndex = 0;
                for (int charIndex = 0; ; charIndex++) {
                    boolean endOfInput = charIndex == textParameter.length();
                    if (endOfInput || textParameter.charAt(charIndex) == ';') {
                        try {
                            String colorSpec = textParameter.substring(lastSemiIndex, charIndex);
                            if ("?".equals(colorSpec)) {

                                int rgb = mColors.mCurrentColors[specialIndex];
                                int r = (65535 * ((rgb & 0x00FF0000) >> 16)) / 255;
                                int g = (65535 * ((rgb & 0x0000FF00) >> 8)) / 255;
                                int b = (65535 * ((rgb & 0x000000FF))) / 255;
                                mSession.write("\033]" + value + ";rgb:" + String.format(Locale.US, "%04x", r) + "/" + String.format(Locale.US, "%04x", g) + "/"
                                        + String.format(Locale.US, "%04x", b) + bellOrStringTerminator);
                            } else {
                                mColors.tryParseColor(specialIndex, colorSpec);
                                mSession.onColorsChanged();
                            }
                            specialIndex++;
                            if (endOfInput || (specialIndex > TextStyle.COLOR_INDEX_CURSOR) || ++charIndex >= textParameter.length())
                                break;
                            lastSemiIndex = charIndex;
                        } catch (NumberFormatException e) {

                        }
                    }
                }
                break;
            case 52:
                int startIndex = textParameter.indexOf(";") + 1;
                try {
                    String clipboardText = new String(Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8);
                    mSession.onCopyTextToClipboard(clipboardText);
                } catch (Exception e) {
                    Logger.logError(mClient, LOG_TAG, "OSC Manipulate selection, invalid string '" + textParameter);
                }
                break;
            case 104:




                if (textParameter.isEmpty()) {
                    mColors.reset();
                    mSession.onColorsChanged();
                } else {
                    int lastIndex = 0;
                    for (int charIndex = 0; ; charIndex++) {
                        boolean endOfInput = charIndex == textParameter.length();
                        if (endOfInput || textParameter.charAt(charIndex) == ';') {
                            try {
                                int colorToReset = Integer.parseInt(textParameter.substring(lastIndex, charIndex));
                                mColors.reset(colorToReset);
                                mSession.onColorsChanged();
                                if (endOfInput) break;
                                charIndex++;
                                lastIndex = charIndex;
                            } catch (NumberFormatException e) {

                            }
                        }
                    }
                }
                break;
            case 110:
            case 111:
            case 112:
                mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110));
                mSession.onColorsChanged();
                break;
            case 119:
                break;
            default:
                unknownParameter(value);
                break;
        }
        finishSequence();
    }

    /** Lines scrolled into the main-screen history so far. */
    private long mTotalScrolledLines = 0;

    /** OSC 7 markers: from absolute row X on, the shell's cwd is Y. Sorted by row. */
    private final ArrayList<Long> mCwdRows = new ArrayList<>();
    private final ArrayList<String> mCwdPaths = new ArrayList<>();

    private void handleOsc7(String uri) {
        String path = parseOsc7Path(uri);
        if (path == null) return;

        if (mScreen == mMainBuffer) recordCwd(mTotalScrolledLines + mCursorRow, path);
        mSession.onCwdChanged(path);
    }

    private String parseOsc7Path(String uri) {
        if (uri == null || !uri.startsWith("file://")) return null;
        try {
            String path = new URI(uri).getPath();
            return (path != null && path.startsWith("/")) ? path : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private void recordCwd(long absRow, String path) {

        int n = mCwdRows.size();
        while (n > 0 && mCwdRows.get(n - 1) >= absRow) {
            mCwdRows.remove(n - 1);
            mCwdPaths.remove(n - 1);
            n--;
        }
        mCwdRows.add(absRow);
        mCwdPaths.add(path);


        long oldest = mTotalScrolledLines - mMainBuffer.getActiveTranscriptRows();
        while (mCwdRows.size() > 1 && mCwdRows.get(1) <= oldest) {
            mCwdRows.remove(0);
            mCwdPaths.remove(0);
        }
    }

    /**
     * Guest CWD that was active when the given row was printed, or null if the shell hadn't
     * reported one yet. externalRow is the same row index the renderer uses (negative = history).
     */
    public String getCwdForRow(int externalRow) {
        if (mCwdRows.isEmpty()) return null;
        if (mScreen != mMainBuffer) return mCwdPaths.get(mCwdPaths.size() - 1);
        long abs = mTotalScrolledLines + externalRow;
        int i = Collections.binarySearch(mCwdRows, abs);
        int idx = i >= 0 ? i : -i - 2;
        return idx < 0 ? null : mCwdPaths.get(idx);
    }

    private void blockClear(int sx, int sy, int w) {
        blockClear(sx, sy, w, 1);
    }

    private void blockClear(int sx, int sy, int w, int h) {
        mScreen.blockSet(sx, sy, w, h, ' ', getStyle());
    }

    private long getStyle() {
        return TextStyle.encode(mForeColor, mBackColor, mEffect);
    }

    /**
     * "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.
     */
    private void doSetMode(boolean newValue) {
        int modeBit = getArg0(0);
        switch (modeBit) {
            case 4:
                mInsertMode = newValue;
                break;
            case 20:
                unknownParameter(modeBit);

                break;
            case 34:


                break;
            default:
                unknownParameter(modeBit);
                break;
        }
    }

    /**
     * NOTE: The parameters of this function respect the {@link #DECSET_BIT_ORIGIN_MODE}. Use
     * {@link #setCursorRowCol(int, int)} for absolute pos.
     */
    private void setCursorPosition(int x, int y) {
        boolean originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE);
        int effectiveTopMargin = originMode ? mTopMargin : 0;
        int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
        int effectiveLeftMargin = originMode ? mLeftMargin : 0;
        int effectiveRightMargin = originMode ? mRightMargin : mColumns;
        int newRow = Math.max(effectiveTopMargin, Math.min(effectiveTopMargin + y, effectiveBottomMargin - 1));
        int newCol = Math.max(effectiveLeftMargin, Math.min(effectiveLeftMargin + x, effectiveRightMargin - 1));
        setCursorRowCol(newRow, newCol);
    }

    private void scrollDownOneLine() {
        mScrollCounter++;
        long currentStyle = getStyle();
        if (mLeftMargin != 0 || mRightMargin != mColumns) {

            mScreen.blockCopy(mLeftMargin, mTopMargin + 1, mRightMargin - mLeftMargin, mBottomMargin - mTopMargin - 1, mLeftMargin, mTopMargin);

            mScreen.blockSet(mLeftMargin, mBottomMargin - 1, mRightMargin - mLeftMargin, 1, ' ', currentStyle);
        } else {

            if (mScreen == mMainBuffer && mTopMargin == 0 && mBottomMargin == mRows) mTotalScrolledLines++;
            mScreen.scrollDownOneLine(mTopMargin, mBottomMargin, currentStyle);
        }
    }

    /**
     * Process the next ASCII character of a parameter.
     *
     * <p>You must use the ; character to separate parameters and : to separate sub-parameters.
     *
     * <p>Parameter characters modify the action or interpretation of the sequence. Originally
     * you can use up to 16 parameters per sequence, but following at least xterm and alacritty
     * we use a common space for parameters and sub-parameters, allowing 32 in total.
     *
     * <p>All parameters are unsigned, positive decimal integers, with the most significant
     * digit sent first. Any parameter greater than 9999 (decimal) is set to 9999
     * (decimal). If you do not specify a value, a 0 value is assumed. A 0 value
     * or omitted parameter indicates a default value for the sequence. For most
     * sequences, the default value is 1.
     *
     * <p>References:
     * <a href="https://vt100.net/docs/vt510-rm/chapter4.html#S4.3.3">VT510 Video Terminal Programmer Information: Control Sequences</a>
     * <a href="https://github.com/alacritty/vte/issues/22">alacritty/vte: Implement colon separated CSI parameters</a>
     *
     */
    private void parseArg(int b) {
        if (b >= '0' && b <= '9') {
            if (mArgIndex < mArgs.length) {
                int oldValue = mArgs[mArgIndex];
                int thisDigit = b - '0';
                int value;
                if (oldValue >= 0) {
                    value = oldValue * 10 + thisDigit;
                } else {
                    value = thisDigit;
                }
                if (value > 9999)
                    value = 9999;
                mArgs[mArgIndex] = value;
            }
            continueSequence(mEscapeState);
        } else if (b == ';' || b == ':') {
            if (mArgIndex + 1 < mArgs.length) {
                mArgIndex++;
                if (b == ':') {
                    mArgsSubParamsBitSet |= 1 << mArgIndex;
                }
            } else {
                logError("Too many parameters when in state: " + mEscapeState);
            }
            continueSequence(mEscapeState);
        } else {
            unknownSequence(b);
        }
    }

    private int getArg0(int defaultValue) {
        return getArg(0, defaultValue, true);
    }

    private int getArg1(int defaultValue) {
        return getArg(1, defaultValue, true);
    }

    private int getArg(int index, int defaultValue, boolean treatZeroAsDefault) {
        int result = mArgs[index];
        if (result < 0 || (result == 0 && treatZeroAsDefault)) {
            result = defaultValue;
        }
        return result;
    }

    private void collectOSCArgs(int b) {
        if (mOSCOrDeviceControlArgs.length() < MAX_OSC_STRING_LENGTH) {
            mOSCOrDeviceControlArgs.appendCodePoint(b);
            continueSequence(mEscapeState);
        } else {
            unknownSequence(b);
        }
    }

    private void unimplementedSequence(int b) {
        logError("Unimplemented sequence char '" + (char) b + "' (U+" + String.format("%04x", b) + ")");
        finishSequence();
    }

    private void unknownSequence(int b) {
        logError("Unknown sequence char '" + (char) b + "' (numeric value=" + b + ")");
        finishSequence();
    }

    private void unknownParameter(int parameter) {
        logError("Unknown parameter: " + parameter);
        finishSequence();
    }

    private void logError(String errorType) {
        if (LOG_ESCAPE_SEQUENCES) {
            StringBuilder buf = new StringBuilder();
            buf.append(errorType);
            buf.append(", escapeState=");
            buf.append(mEscapeState);
            boolean firstArg = true;
            if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
            for (int i = 0; i <= mArgIndex; i++) {
                int value = mArgs[i];
                if (value >= 0) {
                    if (firstArg) {
                        firstArg = false;
                        buf.append(", args={");
                    } else {
                        buf.append(',');
                    }
                    buf.append(value);
                }
            }
            if (!firstArg) buf.append('}');
            finishSequenceAndLogError(buf.toString());
        }
    }

    private void finishSequenceAndLogError(String error) {
        if (LOG_ESCAPE_SEQUENCES) Logger.logWarn(mClient, LOG_TAG, error);
        finishSequence();
    }

    private void finishSequence() {
        mEscapeState = ESC_NONE;
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param codePoint The code point of the character to display
     */
    private void emitCodePoint(int codePoint) {
        mLastEmittedCodePoint = codePoint;
        if (mUseLineDrawingUsesG0 ? mUseLineDrawingG0 : mUseLineDrawingG1) {

            switch (codePoint) {
                case '_':
                    codePoint = ' ';
                    break;
                case '`':
                    codePoint = '◆';
                    break;
                case '0':
                    codePoint = '█';
                    break;
                case 'a':
                    codePoint = '▒';
                    break;
                case 'b':
                    codePoint = '␉';
                    break;
                case 'c':
                    codePoint = '␌';
                    break;
                case 'd':
                    codePoint = '\r';
                    break;
                case 'e':
                    codePoint = '␊';
                    break;
                case 'f':
                    codePoint = '°';
                    break;
                case 'g':
                    codePoint = '±';
                    break;
                case 'h':
                    codePoint = '\n';
                    break;
                case 'i':
                    codePoint = '␋';
                    break;
                case 'j':
                    codePoint = '┘';
                    break;
                case 'k':
                    codePoint = '┐';
                    break;
                case 'l':
                    codePoint = '┌';
                    break;
                case 'm':
                    codePoint = '└';
                    break;
                case 'n':
                    codePoint = '┼';
                    break;
                case 'o':
                    codePoint = '⎺';
                    break;
                case 'p':
                    codePoint = '⎻';
                    break;
                case 'q':
                    codePoint = '─';
                    break;
                case 'r':
                    codePoint = '⎼';
                    break;
                case 's':
                    codePoint = '⎽';
                    break;
                case 't':
                    codePoint = '├';
                    break;
                case 'u':
                    codePoint = '┤';
                    break;
                case 'v':
                    codePoint = '┴';
                    break;
                case 'w':
                    codePoint = '┬';
                    break;
                case 'x':
                    codePoint = '│';
                    break;
                case 'y':
                    codePoint = '≤';
                    break;
                case 'z':
                    codePoint = '≥';
                    break;
                case '{':
                    codePoint = 'π';
                    break;
                case '|':
                    codePoint = '≠';
                    break;
                case '}':
                    codePoint = '£';
                    break;
                case '~':
                    codePoint = '·';
                    break;
            }
        }

        final boolean autoWrap = isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP);
        final int displayWidth = WcWidth.width(codePoint);
        final boolean cursorInLastColumn = mCursorCol == mRightMargin - 1;

        if (autoWrap) {
            if (cursorInLastColumn && ((mAboutToAutoWrap && displayWidth == 1) || displayWidth == 2)) {
                mScreen.setLineWrap(mCursorRow);
                mCursorCol = mLeftMargin;
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++;
                } else {
                    scrollDownOneLine();
                }
            }
        } else if (cursorInLastColumn && displayWidth == 2) {


            return;
        }

        if (mInsertMode && displayWidth > 0) {

            int destCol = mCursorCol + displayWidth;
            if (destCol < mRightMargin)
                mScreen.blockCopy(mCursorCol, mCursorRow, mRightMargin - destCol, 1, destCol, mCursorRow);
        }

        int offsetDueToCombiningChar = ((displayWidth <= 0 && mCursorCol > 0 && !mAboutToAutoWrap) ? 1 : 0);
        int column = mCursorCol - offsetDueToCombiningChar;





        if (column < 0) column = 0;
        mScreen.setChar(column, mCursorRow, codePoint, getStyle());

        if (autoWrap && displayWidth > 0)
            mAboutToAutoWrap = (mCursorCol == mRightMargin - displayWidth);

        mCursorCol = Math.min(mCursorCol + displayWidth, mRightMargin - 1);
    }

    private void setCursorRow(int row) {
        mCursorRow = row;
        mAboutToAutoWrap = false;
    }

    private void setCursorCol(int col) {
        mCursorCol = col;
        mAboutToAutoWrap = false;
    }

    /**
     * Set the cursor mode, but limit it to margins if {@link #DECSET_BIT_ORIGIN_MODE} is enabled.
     */
    private void setCursorColRespectingOriginMode(int col) {
        setCursorPosition(col, mCursorRow);
    }

    /**
     * TODO: Better name, distinguished from {@link #setCursorPosition(int, int)} by not regarding origin mode.
     */
    private void setCursorRowCol(int row, int col) {
        mCursorRow = Math.max(0, Math.min(row, mRows - 1));
        mCursorCol = Math.max(0, Math.min(col, mColumns - 1));
        mAboutToAutoWrap = false;
    }

    public int getScrollCounter() {
        return mScrollCounter;
    }

    public void clearScrollCounter() {
        mScrollCounter = 0;
    }

    public boolean isAutoScrollDisabled() {
        return mAutoScrollDisabled;
    }

    public void toggleAutoScrollDisabled() {
        mAutoScrollDisabled = !mAutoScrollDisabled;
    }


    /**
     * Reset terminal state so user can interact with it regardless of present state.
     */
    public void reset() {
        setCursorStyle();
        mArgIndex = 0;
        mContinueSequence = false;
        mEscapeState = ESC_NONE;
        mInsertMode = false;
        mTopMargin = mLeftMargin = 0;
        mBottomMargin = mRows;
        mRightMargin = mColumns;
        mAboutToAutoWrap = false;
        mForeColor = mSavedStateMain.mSavedForeColor = mSavedStateAlt.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
        mBackColor = mSavedStateMain.mSavedBackColor = mSavedStateAlt.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
        setDefaultTabStops();

        mUseLineDrawingG0 = mUseLineDrawingG1 = false;
        mUseLineDrawingUsesG0 = true;

        mSavedStateMain.mSavedCursorRow = mSavedStateMain.mSavedCursorCol = mSavedStateMain.mSavedEffect = mSavedStateMain.mSavedDecFlags = 0;
        mSavedStateAlt.mSavedCursorRow = mSavedStateAlt.mSavedCursorCol = mSavedStateAlt.mSavedEffect = mSavedStateAlt.mSavedDecFlags = 0;
        mCurrentDecSetFlags = 0;

        setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true);
        setDecsetinternalBit(DECSET_BIT_CURSOR_ENABLED, true);
        mSavedDecSetFlags = mSavedStateMain.mSavedDecFlags = mSavedStateAlt.mSavedDecFlags = mCurrentDecSetFlags;


        mUtf8Index = mUtf8ToFollow = 0;

        mColors.reset();
        mSession.onColorsChanged();
    }

    public String getSelectedText(int x1, int y1, int x2, int y2) {
        return mScreen.getSelectedText(x1, y1, x2, y2);
    }

    /**
     * Get the terminal session's title (null if not set).
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Change the terminal session's title.
     */
    private void setTitle(String newTitle) {
        String oldTitle = mTitle;
        mTitle = newTitle;
        if (!Objects.equals(oldTitle, newTitle)) {
            mSession.titleChanged(oldTitle, newTitle);
        }
    }

    /**
     * If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".
     */
    public void paste(String text) {

        text = text.replaceAll("(\u001B|[\u0080-\u009F])", "");

        text = text.replaceAll("\r?\n", "\r");


        boolean bracketed = isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE);
        if (bracketed) mSession.write("\033[200~");
        mSession.write(text);
        if (bracketed) mSession.write("\033[201~");
    }

    /**
     * http://www.vt100.net/docs/vt510-rm/DECSC
     */
    static final class SavedScreenState {
        /**
         * Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences.
         */
        int mSavedCursorRow, mSavedCursorCol;
        int mSavedEffect, mSavedForeColor, mSavedBackColor;
        int mSavedDecFlags;
        boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;
    }

    @Override
    public String toString() {
        return "TerminalEmulator[size=" + mScreen.mColumns + "x" + mScreen.mScreenRows + ", margins={" + mTopMargin + "," + mRightMargin + "," + mBottomMargin
                + "," + mLeftMargin + "}]";
    }

}
