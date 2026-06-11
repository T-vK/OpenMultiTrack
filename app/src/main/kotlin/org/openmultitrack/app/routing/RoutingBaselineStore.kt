package org.openmultitrack.app.routing

import android.content.Context

class RoutingBaselineStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(pending: PendingRoutingRestore) {
        prefs.edit().putString(KEY_PENDING, pending.toJson().toString()).commit()
    }

    fun load(): PendingRoutingRestore? =
        prefs.getString(KEY_PENDING, null)?.let(PendingRoutingRestore::fromJson)

    fun clear() {
        prefs.edit().remove(KEY_PENDING).commit()
    }

    companion object {
        private const val PREFS = "omt_routing_baseline"
        private const val KEY_PENDING = "pending_restore"
    }
}
