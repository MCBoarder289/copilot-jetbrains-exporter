plugins {
    application
    id("com.gradleup.shadow") version "9.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.nitrite)
    implementation(libs.nitrite.mvstore)
    implementation(libs.picocli)
    implementation(libs.jackson.databind)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "io.github.copilotjetbrains.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<org.gradle.jvm.tasks.Jar>("shadowJar") {
    archiveBaseName.set("copilot-jetbrains-exporter")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    manifest {
        attributes["Main-Class"] = "io.github.copilotjetbrains.Main"
    }
}
