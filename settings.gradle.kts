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

// NM-396 스파이크 — RF-DETR 온디바이스 런타임 검증용 임시 모듈.
// 스파이크 종료 후 결론에 따라 제거하거나 정식 모듈로 승격한다.
include(":spike-rfdetr-harness")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    "JDK 17 이상이 필요합니다. 현재: ${JavaVersion.current()} (java.home=${System.getProperty("java.home")})"
}
