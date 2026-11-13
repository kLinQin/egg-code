package com.termux.terminal;

/**
 * ${CSI}${top};${bottom}r" - set Scrolling Region [top;bottom] (default = full size of window) (DECSTBM).
 * <p/>
 * "DECSTBM moves the cursor to column 1, line 1 of the page" (http://www.vt100.net/docs/vt510-rm/DECSTBM).
 */
public class ScrollRegionTest extends TerminalTestCase {

	public void testScrollRegionTop() {
		withTerminalSized(3, 4).enterString("111222333444").assertLinesAre("111", "222", "333", "444");
		enterString("\033[2r").assertCursorAt(0, 0);
		enterString("\r\n\r\n\r\n\r\nCDEFGH").assertLinesAre("111", "444", "CDE", "FGH").assertHistoryStartsWith("333");
		enterString("IJK").assertLinesAre("111", "CDE", "FGH", "IJK").assertHistoryStartsWith("444");

		enterString("\033[r").enterString("\r\n\r\n\r\n").enterString("LMNOPQ").assertLinesAre("CDE", "FGH", "LMN", "OPQ");
	}

	public void testScrollRegionBottom() {
		withTerminalSized(3, 4).enterString("111222333444");
		assertLinesAre("111", "222", "333", "444");
		enterString("\033[1;3r").assertCursorAt(0, 0);
		enterString("\r\n\r\nCDEFGH").assertLinesAre("222", "CDE", "FGH", "444").assertHistoryStartsWith("111");

		enterString("\033[r").enterString("\r\n\r\n\r\n").enterString("IJKLMN").assertLinesAre("CDE", "FGH", "IJK", "LMN");
	}

	public void testScrollRegionResetWithOriginMode() {
		withTerminalSized(3, 4).enterString("111222333444");
		assertLinesAre("111", "222", "333", "444");

		enterString("\033[?6h\033[2r").assertCursorAt(1, 0);
	}

	public void testScrollRegionLeft() {

		withTerminalSized(3, 3).enterString("\033[?69h\033[2sABCDEFG").assertLinesAre("ABC", " DE", " FG");
		enterString("HI").assertLinesAre("ADE", " FG", " HI").enterString("JK").assertLinesAre("AFG", " HI", " JK");
		enterString("\n").assertLinesAre("AHI", " JK", "   ");
	}

	public void testScrollRegionRight() {

		withTerminalSized(3, 3).enterString("YYY\033[?69h\033[1;2sABCDEF").assertLinesAre("ABY", "CD ", "EF ");
		enterString("GH").assertLinesAre("CDY", "EF ", "GH ").enterString("IJ").assertLinesAre("EFY", "GH ", "IJ ");
		enterString("\n").assertLinesAre("GHY", "IJ ", "   ");
	}

	public void testScrollRegionOnAllSides() {

		withTerminalSized(4, 4).enterString("ABCDEFGHIJKLMNOP").assertLinesAre("ABCD", "EFGH", "IJKL", "MNOP");

		enterString("\033[?6h\033[2;3r").assertCursorAt(1, 0);
		enterString("\033[?69h\033[2;3s").assertCursorAt(1, 1);
		enterString("QRST").assertLinesAre("ABCD", "EQRH", "ISTL", "MNOP");
		enterString("UV").assertLinesAre("ABCD", "ESTH", "IUVL", "MNOP");
	}

	public void testDECCOLMResetsScrollMargin() {


		withTerminalSized(3, 4).enterString("111222333444").assertLinesAre("111", "222", "333", "444");
		enterString("\033[2r\033[?3h\r\nABCDEFGHIJKL").assertLinesAre("ABC", "DEF", "GHI", "JKL");
	}

	public void testScrollOutsideVerticalRegion() {
		withTerminalSized(3, 4).enterString("\033[0;2rhi\033[4;0Hyou").assertLinesAre("hi ", "   ", "   ", "you");

	}

	public void testNELRespectsLeftMargin() {


		withTerminalSized(3, 3).enterString("\033[?69h\033[2sABC\033ED").assertLinesAre("ABC", "D  ", "   ");
		withTerminalSized(3, 3).enterString("\033[?69h\033[2sABC\033[?6h\033ED").assertLinesAre("ABC", " D ", "   ");
	}

	public void testRiRespectsLeftMargin() {

		withTerminalSized(4, 3).enterString("ABCD\033[?69h\033[2;3s\033[?6h\033M").assertLinesAre("A  D", " BC ", "    ");
	}

	public void testSdRespectsLeftMargin() {

		withTerminalSized(4, 3).enterString("ABCD\033[?69h\033[2;3s\033[?6h\033[2T").assertLinesAre("A  D", "    ", " BC ");
	}

	public void testBackwardIndex() {


		withTerminalSized(3, 3).enterString("ABCDEF\0336H").assertLinesAre("ABC", "DHF", "   ");
		enterString("\0336\0336I").assertLinesAre("ABC", "IHF", "   ");
		enterString("\0336\0336").assertLinesAre(" AB", " IH", "   ");

		withTerminalSized(3, 3).enterString("\033[?69h\033[2sABCDEF\0336\0336").assertLinesAre("A B", "  D", "  F");
	}

	public void testForwardIndex() {


		withTerminalSized(3, 3).enterString("ABCD\0339E").assertLinesAre("ABC", "D E", "   ");
		enterString("\0339").assertLinesAre("BC ", " E ", "   ");

		withTerminalSized(3, 3).enterString("\033[?69h\033[0;2sABCD\0339").assertLinesAre("B  ", "D  ", "   ");
	}

	public void testScrollDownWithScrollRegion() {
		withTerminalSized(2, 5).enterString("1\r\n2\r\n3\r\n4\r\n5").assertLinesAre("1 ", "2 ", "3 ", "4 ", "5 ");
		enterString("\033[3r").enterString("\033[2T").assertLinesAre("1 ", "2 ", "  ", "  ", "3 ");
	}

	public void testScrollDownBelowScrollRegion() {
		withTerminalSized(2, 5).enterString("1\r\n2\r\n3\r\n4\r\n5").assertLinesAre("1 ", "2 ", "3 ", "4 ", "5 ");
		enterString("\033[1;3r");
		enterString("\033[4;1H");
		enterString("QQ\r\nRR\r\n\r\n\r\nYY");
		assertLinesAre("1 ", "2 ", "3 ", "QQ", "YY");
	}

	/** See https://github.com/termux/termux-app/issues/1340 */
	public void testScrollRegionDoesNotLimitCursorMovement() {
		withTerminalSized(6, 4)
			.enterString("\033[4;7r\033[3;1Haaa\033[Axxx")
			.assertLinesAre(
				"      ",
				"   xxx",
				"aaa   ",
				"      "
			);

		withTerminalSized(6, 4)
			.enterString("\033[1;3r\033[3;1Haaa\033[Bxxx")
			.assertLinesAre(
				"      ",
				"      ",
				"aaa   ",
				"   xxx"
			);
	}

	/**
	 * See <a href="https://github.com/termux/termux-packages/issues/12556">reported issue</a>.
	 */
	public void testClearingWhenScrollingWithMargins() {
		int newForeground = 2;
		int newBackground = 3;
		int size = 3;
		TerminalTestCase terminal = withTerminalSized(size, size)

			.enterString("\033[?69h\033[2s")

			.enterString("\033[" + (30 + newForeground) + ";" + (40 + newBackground) + "m")

			.enterString("\r\n\r\n\r\n\r\n\r\n");
		for (int row = 0; row < size; row++) {
			for (int col = 0; col < size; col++) {


				int expectedForeground = col == 0 ? TextStyle.COLOR_INDEX_FOREGROUND : newForeground;
				int expectedBackground = col == 0 ? TextStyle.COLOR_INDEX_BACKGROUND : newBackground;
				terminal.assertForegroundColorAt(row, col, expectedForeground);
				terminal.assertBackgroundColorAt(row, col, expectedBackground);
			}
		}
	}

}
