plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.toivape"

version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion.convention(JavaLanguageVersion.of(21)) }
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.github.wimdeblauwe:htmx-spring-boot:4.0.1")
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.wimdeblauwe:htmx-spring-boot-thymeleaf:4.0.1")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("com.microsoft.playwright:playwright:1.50.0")

    testImplementation("io.kotest:kotest-assertions-core-jvm:5.8.1")
    testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

tasks.withType<Test> { useJUnitPlatform() }

// Define the test task with unit test configuration
tasks.named<Test>("test") {
    filter {
        includeTestsMatching("*Test")
        excludeTestsMatching("*IntegrationTest")
    }
}

// Create a separate task for integration tests
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    useJUnitPlatform()

    filter { includeTestsMatching("*IT") }
    shouldRunAfter(tasks.named("test"))
}
