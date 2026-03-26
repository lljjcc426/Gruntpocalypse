rootProject.name = "Grunteon"

// Launch bootstrap
include(":grunt-bootstrap")

// Components
include(":grunt-main")
include(":grunt-testcase")
include(":grunt-yapyap")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}