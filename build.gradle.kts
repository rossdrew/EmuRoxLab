plugins {
    id("java")
    id("jacoco") //test coverage
}

group = "com.rox"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

jacoco { //Test coverage
    toolVersion = "0.8.14"
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.mockito:mockito-core:5.18.0") //Mocks
    testImplementation("net.jqwik:jqwik:1.9.3") //Properties

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}