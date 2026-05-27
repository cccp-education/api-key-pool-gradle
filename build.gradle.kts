plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

group = "education.cccp"
version = libs.versions.api.key.pool.get()
kotlin.jvmToolchain(JavaVersion.VERSION_24.ordinal)

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("education.cccp:api-key-pool:0.0.1")
    implementation(kotlin("stdlib-jdk8"))
}

// Ce build racine consomme la librairie api-key-pool publiée en mavenLocal.
// Il sert de dogfood : il exerce la lib dans des conditions réelles.
// Utilisation : cd api-key-pool-plugin && ./gradlew publishToMavenLocal && cd .. && ./gradlew build
