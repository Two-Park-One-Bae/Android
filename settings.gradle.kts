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
        // 카카오 SDK 는 Maven Central 에 없고 카카오가 직접 운영하는 저장소에만 있다.
        // includeGroup 으로 범위를 좁혀 다른 의존성이 이 서버를 뒤지지 않게 한다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public") {
            content { includeGroup("com.kakao.sdk") }
        }
    }
}

rootProject.name = "nursemate-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":wear")
include(":core:model")
include(":core:data")
include(":core:timer")
include(":core:network")
include(":core:vision")
include(":core:datalayer")
include(":core:designsystem")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    "JDK 17 이상이 필요합니다. 현재: ${JavaVersion.current()} (java.home=${System.getProperty("java.home")})"
}
