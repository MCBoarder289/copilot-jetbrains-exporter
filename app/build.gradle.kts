plugins {
    application
    id("com.gradleup.shadow") version "9.0.0"
    id("org.graalvm.buildtools.native") version "0.10.3"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.nitrite)
    implementation(libs.nitrite.mvstore)
    implementation(libs.picocli)
    implementation(libs.jackson.databind)

    annotationProcessor(libs.picocli.codegen)

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

// Tell picocli annotation processor which project these classes belong to so it
// generates reflect-config.json into META-INF/native-image/picocli-generated/
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Aproject=io.github.copilotjetbrains/app"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val generateVersionProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    outputs.dir(outputDir)
    doLast {
        val f = outputDir.get().asFile.resolve("version.properties")
        f.parentFile.mkdirs()
        f.writeText("version=${project.version}\n")
    }
}

tasks.named("processResources") {
    dependsOn(generateVersionProperties)
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/resources"))

tasks.named<org.gradle.jvm.tasks.Jar>("shadowJar") {
    archiveBaseName.set("copilot-jetbrains-exporter")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    manifest {
        attributes["Main-Class"] = "io.github.copilotjetbrains.Main"
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("copilot-jetbrains-exporter")
            mainClass.set("io.github.copilotjetbrains.Main")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            // SLF4J has no providers at runtime (NOP) — safe to init at build time
            buildArgs.add("--initialize-at-build-time=org.slf4j")
        }
    }
    // Use the GraalVM toolchain configured via JAVA_HOME or sdkman rather than
    // trying to auto-detect an installed GraalVM distribution
    toolchainDetection.set(false)
}
