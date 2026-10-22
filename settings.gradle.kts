pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "orbita"

include(":sim", ":ai", ":game", ":desktop", ":android", ":tools:arena")
