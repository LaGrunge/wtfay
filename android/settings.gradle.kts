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

rootProject.name = "TyGde"
include(":app")

// Алгоритм адресов живёт в ../core со своими тестами; подключаем как составную сборку.
includeBuild("../core") {
    dependencySubstitution {
        substitute(module("wtfay:core")).using(project(":"))
    }
}
