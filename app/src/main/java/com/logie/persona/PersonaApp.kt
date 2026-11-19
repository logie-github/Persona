package com.logie.persona

import android.app.Application
import java.io.File

/** The app's process: a crash anywhere in it (the launcher, the Windows game's screen) is written to files/crash.log before Android ends it, for SHARE LOG. */
class PersonaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val f = File(filesDir, "crash.log")
                if (f.length() > 512 * 1024) f.delete()
                f.appendText("${java.util.Date()} ${android.os.Process.myPid()} ${thread.name}: ${e.stackTraceToString()}\n")
            }
            previous?.uncaughtException(thread, e)
        }
    }
}
