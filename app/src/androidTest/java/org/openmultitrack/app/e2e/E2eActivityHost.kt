package org.openmultitrack.app.e2e

import android.content.Context
import org.openmultitrack.app.MainActivity

/** Activity access for e2e harnesses (standalone rule or Compose test rule). */
interface E2eActivityHost {
    val appContext: Context
    fun <T> runOnActivity(block: (MainActivity) -> T): T
}
