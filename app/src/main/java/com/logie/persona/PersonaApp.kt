package com.logie.persona

import android.app.Application
import com.logie.persona.reports.BugReports

/**
 * The app's process: its reports start with it ([BugReports.start], the Blizzard launcher's): the run's log, crashes caught and sent,
 * how the last run ended, the phone's memory and processes. The account's name is kept out of every report.
 */
class PersonaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BugReports.accountName = runCatching { com.logie.persona.install.InstallCenter.accountName(this) }.getOrNull()
        BugReports.start(this)
    }
}
