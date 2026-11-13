package com.termux.terminal;

public class ApcTest extends TerminalTestCase {

    public void testApcConsumed() {





        withTerminalSized(2, 2)
            .enterString("\033_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA\033\\")
            .assertLinesAre("  ", "  ");


        withTerminalSized(12, 2)
            .enterString("hello \033_some\023\033_\\apc#end\033\\ world")
            .assertLinesAre("hello  world", "            ");
    }

}
