pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OpenMultiTrack"

include(":app")
include(":domain")
include(":usb-audio")
include(":audio-engine")
include(":mixer-behringer")
include(":session-io")
