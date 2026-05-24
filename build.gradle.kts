plugins {
    id("java")
    id("jacoco") //test coverage
    id("info.solidsoft.pitest") version "1.19.0" //mutation testing
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

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(listOf("com.rox.*"))
    targetTests.set(listOf("com.rox.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
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

/**
 * Substitute for the lack of a Pitest report service
 */
tasks.register("pitestBadge") {
    dependsOn("pitest")

    doLast {
        val xmlFile = file("build/reports/pitest/mutations.xml")
        val mutations = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xmlFile)
            .getElementsByTagName("mutation")

        var total = 0
        var killed = 0

        for (i in 0 until mutations.length) {
            val mutation = mutations.item(i)
            val status = mutation.attributes
                .getNamedItem("status")
                .nodeValue

            total++

            if (status == "KILLED") {
                killed++
            }
        }

        val score = if (total == 0) 0 else Math.round((killed.toDouble() / total) * 100).toInt()

        val color = when {
            score >= 80 -> "brightgreen"
            score >= 60 -> "yellow"
            else -> "red"
        }

        val badgeJson = """
        {
          "schemaVersion": 1,
          "label": "mutation coverage",
          "message": "$score%",
          "color": "$color"
        }
        """.trimIndent()

        val badgesDir = file("badges")
        badgesDir.mkdirs()

        file("badges/pitest.json").writeText(badgeJson)

        println("Mutation coverage: $score%")
    }
}