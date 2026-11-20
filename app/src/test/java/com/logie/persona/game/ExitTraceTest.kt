package com.logie.persona.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The exit trace's lines, as the desktop rig printed them (Winlator's Wine 10.10, a stand-in program calling ExitProcess(3)). */
class ExitTraceTest {
    @Test fun aTracedCallIsKeptAndNamedTheRestDropped() {
        ExitTrace.reset()
        ExitTrace.loaded("""0140:trace:loaddll:build_module Loaded L"D:\\testgame\\exitprobe.exe" at 0000000140000000: native""")
        ExitTrace.loaded("""0140:trace:loaddll:build_module Loaded L"C:\\windows\\system32\\kernelbase.dll" at 00006FFFFFC10000: builtin""")
        ExitTrace.loaded("""00ec:trace:loaddll:build_module Loaded L"D:\\persona4_golden\\steam_api64.dll" at 00000002B5A00000: native""")
        val exit = "0140:Call KERNEL32.ExitProcess(00000003) ret=140001050"
        assertTrue(ExitTrace.isRelay(exit)); assertTrue(ExitTrace.wanted(exit))
        assertEquals("$exit (in exitprobe.exe)", ExitTrace.named(exit))
        assertEquals("00ec:Call KERNEL32.ExitProcess(00000000) ret=2b5a01234 (in steam_api64.dll)", ExitTrace.named("00ec:Call KERNEL32.ExitProcess(00000000) ret=2b5a01234"))
        for (noise in listOf(
            "0048:Call window proc 00006FFFFFF57A80 (hwnd=0000000000010020,msg=WM_NCCREATE,wp=00000000,lp=7ffffe2fed98)",
            "0140:Call PE DLL (proc=00006FFFFEC96B10,module=00006FFFFEC80000 L\"ole32.dll\",reason=PROCESS_DETACH,res=0000000000000001)",
            "0140:Call TLS callback (proc=00000001400014B0,module=0000000140000000,reason=PROCESS_ATTACH,reserved=0)",
            "002c:Ret  kernelbase.CreateProcessW() retval=00000001 ret=140003858",
            "0140:Starting thread proc 0000000140001060 (arg=000000007FFD0000)",
            "0140:trace:relay:load_list L\"RelayInclude\" = L\"kernel32.ExitProcess\"",
        )) { assertTrue(noise, ExitTrace.isRelay(noise)); assertFalse(noise, ExitTrace.wanted(noise)) }
        assertFalse(ExitTrace.isRelay("008c:trace:seh:call_seh_handlers calling TEB handler 000000027F7A58F0"))
        assertFalse(ExitTrace.isRelay("0074:err:nsi:poll_events bind failed, errno 13."))
    }
}
