plugins {
    id("com.gradle.develocity") version "3.17.6"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.0.2"
}

val isCI = providers.environmentVariable("CI").isPresent

develocity {
    server = "https://ge.gradle.org"
    buildScan {
        uploadInBackground = !isCI
        publishing.onlyIf { it.isAuthenticated }
        obfuscation {
            ipAddresses { addresses -> addresses.map { "0.0.0.0" } }
        }
    }
}

buildCache {
    local {
        isEnabled = true
    }

    remote(develocity.buildCache) {
        server = "https://eu-build-cache.gradle.org"
        isEnabled = true
        val accessKey = providers.environmentVariable("DEVELOCITY_ACCESS_KEY").orNull
        isPush = isCI && !accessKey.isNullOrEmpty()
    }
}

rootProject.name = "develocity-agent-adapters"

include("develocity-gradle-plugin-adapters")
include("develocity-maven-extension-adapters")
