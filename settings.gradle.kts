pluginManagement {
    includeBuild("build-logic")
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

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "nursemate-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":wear")
include(":core:model")
include(":core:data")
include(":core:network")
include(":core:datalayer")
include(":core:designsystem")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    "JDK 17 이상이 필요합니다. 현재: ${JavaVersion.current()} (java.home=${System.getProperty("java.home")})"
}
