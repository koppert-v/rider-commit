plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.koppert"
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localPlatformPath = providers.gradleProperty("localPlatformPath").orNull
        if (localPlatformPath != null) {
            local(localPlatformPath)
        } else {
            rider(providers.gradleProperty("platformVersion"))
        }

        bundledModule("intellij.platform.vcs.impl")
        bundledPlugin("Git4Idea")
    }

    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        name = "AI Commit for Rider"
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "242"
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
