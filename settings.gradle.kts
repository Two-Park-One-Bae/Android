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
        // Airbridge SDK 도 Maven Central 에 없다 (NM-543). 카카오와 같은 이유로 범위를 좁힌다.
        maven("https://sdk-download.airbridge.io/maven") {
            content { includeGroup("io.airbridge") }
        }
    }
}

rootProject.name = "nursemate-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":wear")
// 조건부 전달 — Hexagon 세대마다 하나씩. 기기는 자기 세대 모듈 하나만 받는다.
// 세대를 더할 때는 모듈 디렉터리 · app 의 dynamicFeatures · 기기그룹 셋을 같이 손본다.
// 세대와 무관한 토대. 세대 모듈들이 이걸 문다.
include(":qnn_core")
include(":qnn_v69")
include(":qnn_v73")
include(":qnn_v75")
include(":qnn_v79")
include(":qnn_v81")
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
