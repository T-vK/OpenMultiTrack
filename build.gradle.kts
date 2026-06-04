plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("checkLicenses") {
    group = "verification"
    description = "Reminder to keep dependencies FOSS-only (no Play Services)."
    doLast {
        logger.lifecycle("License check: verify :app:dependencies manually for Play Services artifacts.")
    }
}
