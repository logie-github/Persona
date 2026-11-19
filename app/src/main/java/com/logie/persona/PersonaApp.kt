package com.logie.persona

import android.app.Application
import com.logie.persona.reports.Reports

/**
 * The app's process: a crash anywhere in it (the launcher, the Windows game's screen) is written to files/crash.log for SHARE LOG and
 * reported as an issue, as are the other ways a run can fail ([Reports]).
 */
class PersonaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reports.start(this)
    }
}
