rootProject.name = "Grunteon"

// Launch bootstrap
include(":grunt-bootstrap")

// Components
include(":genesis")
include(":grunt-main")
include(":grunt-back")
include(":grunt-testcase")
include(":grunt-yapyap")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
