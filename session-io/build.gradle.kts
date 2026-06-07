plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain")) // AudioConstants
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
