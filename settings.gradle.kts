pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Drafty"

// App module
include(":app")

// Core modules
include(":core:domain")
include(":core:data")
include(":core:ink-engine")
include(":core:pdf-engine")
include(":core:ui")

// Feature modules
include(":feature:canvas")
include(":feature:notebooks")
include(":feature:pdf-viewer")
