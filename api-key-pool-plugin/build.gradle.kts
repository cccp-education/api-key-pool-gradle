plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    id("education.cccp.build.publishing") version "0.0.14"
    // LINT-ADOPT-13 (S-336) — static analysis gate (ktlint in `check`).
    id("education.cccp.build.lint") version "0.0.14"
}

group = "education.cccp"
version = "0.0.2"
kotlin.jvmToolchain(JavaVersion.VERSION_25.ordinal)

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)
    testImplementation(libs.assertj)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

publishingConventions {
    publicationType = "LIBRARY"
    // Sonatype 130791 — the POM url/scm must point to the exact public
    // repository, never to the organization root (classified "unreachable_repo").
    website = "https://github.com/cccp-education/api-key-pool-gradle"
    vcsUrl = "https://github.com/cccp-education/api-key-pool-gradle.git"
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("ApiKeyPool")
                description.set("N0 shared library — LLM API key pool with rotation, quota tracking, and audit logging.")
            }
        }
    }
    repositories {
        mavenCentral()
    }
}
