package org.openmultitrack.app.routing

import android.content.Context

interface RoutingPendingStore {
    fun save(pending: PendingRoutingRestore)
    fun load(): PendingRoutingRestore?
    fun clear()
}

class RoutingBaselineStore(context: Context) : RoutingPendingStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun save(pending: PendingRoutingRestore) {
        prefs.edit().putString(KEY_PENDING, pending.toJson().toString()).commit()
    }

    override fun load(): PendingRoutingRestore? =
        prefs.getString(KEY_PENDING, null)?.let(PendingRoutingRestore::fromJson)

    override fun clear() {
        prefs.edit().remove(KEY_PENDING).commit()
    }

    companion object {
        private const val PREFS = "omt_routing_baseline"
        private const val KEY_PENDING = "pending_restore"
    }
}
