package com.termux.terminal;

import java.io.UnsupportedEncodingException;

public class UnicodeInputTest extends TerminalTestCase {

    public void testIllFormedUtf8SuccessorByteNotConsumed() throws Exception {




        withTerminalSized(5, 5);
        mTerminal.append(new byte[]{(byte) 0b11101111, (byte) 'a'}, 2);
        assertLineIs(0, ((char) TerminalEmulator.UNICODE_REPLACEMENT_CHAR) + "a   ");


        byte[] input = new byte[]{
                (byte) 0x61, (byte) 0xF1,
                (byte) 0x80, (byte) 0x80,
                (byte) 0xe1, (byte) 0x80,
                (byte) 0xc2, (byte) 0x62,
                (byte) 0x80, (byte) 0x63,
                (byte) 0x80, (byte) 0xbf,
                (byte) 0x64
        };
        withTerminalSized(10, 2);
        mTerminal.append(input, input.length);
        assertLinesAre("a\uFFFD\uFFFD\uFFFDb\uFFFDc\uFFFD\uFFFDd", "          ");


        withTerminalSized(5, 2);
        input = new byte[]{
                (byte) 0xed, (byte) 0xa0,
                (byte) 0x80, (byte) 0xed,
                (byte) 0xad, (byte) 0xbf,
                (byte) 0xed, (byte) 0xae,
                (byte) 0x80, (byte) 0xed,
                (byte) 0xbf, (byte) 0xbf
        };
        mTerminal.append(input, input.length);
        assertLinesAre("\uFFFD\uFFFD\uFFFD\uFFFD ", "     ");





        withTerminalSized(5, 2);
        input = new byte[]{(byte) 0xe0, (byte) 0xa0, ' '};
        mTerminal.append(input, input.length);
        assertLinesAre("\uFFFD    ", "     ");





    }

    public void testUnassignedCodePoint() throws UnsupportedEncodingException {
        withTerminalSized(3, 3);

        byte[] b = new byte[]{(byte) 0xf3, (byte) 0x82, (byte) 0x95, (byte) 0x81};
        mTerminal.append(b, b.length);
        enterString("Y");
        assertEquals(1, Character.charCount(TerminalEmulator.UNICODE_REPLACEMENT_CHAR));
        assertLineStartsWith(0, TerminalEmulator.UNICODE_REPLACEMENT_CHAR, 'Y', ' ');
    }

    public void testStuff() {
        withTerminalSized(80, 24);
        byte[] b = new byte[]{(byte) 0xf3, (byte) 0x82, (byte) 0x95, (byte) 0x81, (byte) 0x61, (byte) 0x38, (byte) 0xe7, (byte) 0x8f,
                (byte) 0xae, (byte) 0xc2, (byte) 0x9f, (byte) 0xe8, (byte) 0xa0, (byte) 0x9f, (byte) 0xe8, (byte) 0x8c, (byte) 0xa4,
                (byte) 0xed, (byte) 0x93, (byte) 0x89, (byte) 0xef, (byte) 0xbf, (byte) 0xbd, (byte) 0x42, (byte) 0xc2, (byte) 0x9b,
                (byte) 0xe6, (byte) 0x87, (byte) 0x89, (byte) 0x5a};
        mTerminal.append(b, b.length);
    }

    public void testSimpleCombining() throws Exception {
        withTerminalSized(3, 2).enterString(" a\u0302 ").assertLinesAre(" a\u0302 ", "   ");
    }

    public void testCombiningCharacterInFirstColumn() throws Exception {
        withTerminalSized(5, 3).enterString("test\r\nhi\r\n").assertLinesAre("test ", "hi   ", "     ");


        withTerminalSized(5, 5).enterString("test\r\nabc\r\n\u0302\r\ndef\r\n");
        assertLinesAre("test ", "abc  ", " \u0302    ", "def  ", "     ");
    }

    public void testCombiningCharacterInLastColumn() throws Exception {
        withTerminalSized(3, 2).enterString("  a\u0302").assertLinesAre("  a\u0302", "   ");
        withTerminalSized(3, 2).enterString("  à̲").assertLinesAre("  à̲", "   ");
        withTerminalSized(3, 2).enterString("Aà̲F").assertLinesAre("Aà̲F", "   ");
    }

    public void testWideCharacterInLastColumn() throws Exception {
        withTerminalSized(3, 2).enterString("  枝\u0302").assertLinesAre("   ", "枝\u0302 ");

        withTerminalSized(3, 2).enterString(" 枝").assertLinesAre(" 枝", "   ").assertCursorAt(0, 2);
        enterString("a").assertLinesAre(" 枝", "a  ");
    }

    public void testWideCharacterDeletion() throws Exception {

        withTerminalSized(3, 2).enterString("枝\033[Da").assertLinesAre(" a ", "   ");
        withTerminalSized(3, 2).enterString("枝\033[2Da").assertLinesAre("a  ", "   ");
        withTerminalSized(3, 2).enterString("枝\033[2D枝").assertLinesAre("枝 ", "   ");
        withTerminalSized(3, 2).enterString("枝\033[1D枝").assertLinesAre(" 枝", "   ");
        withTerminalSized(5, 2).enterString(" 枝 \033[Da").assertLinesAre(" 枝a ", "     ");
        withTerminalSized(5, 2).enterString("a \033[D\u0302").assertLinesAre("a\u0302    ", "     ");
        withTerminalSized(5, 2).enterString("枝 \033[D\u0302").assertLinesAre("枝\u0302   ", "     ");
        enterString("Z").assertLinesAre("枝\u0302Z  ", "     ");
        enterString("\033[D ").assertLinesAre("枝\u0302   ", "     ");

        enterString("\033[2DU").assertLinesAre(" U   ", "     ");
    }

    public void testWideCharOverwriting() {
        withTerminalSized(3, 2).enterString("abc\033[3D枝").assertLinesAre("枝c", "   ");
    }

    public void testOverlongUtf8Encoding() throws Exception {


        withTerminalSized(5, 5).mTerminal.append(new byte[]{(byte) 0xc0, (byte) 0xa0, 'Y'}, 3);
        assertLineIs(0, "\uFFFDY   ");
    }

    public void testWideCharacterWithoutWrapping() throws Exception {



        withTerminalSized(3, 3).enterString("\033[?7l").enterString("枝枝枝").assertLinesAre("枝 ", "   ", "   ");
        enterString("a枝").assertLinesAre("枝a", "   ", "   ");
    }

}
