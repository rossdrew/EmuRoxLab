import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

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

// Scope pitest to a subset of classes for fast local verification, e.g.
// ./gradlew pitest -PpitestScope=com.rox.apu.*
// Defaults to the full project, which is the real CI/build gate.
val pitestScope = (project.findProperty("pitestScope") as String?)?.split(",") ?: listOf("com.rox.*")

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(pitestScope)
    targetTests.set(pitestScope)
    //AudioSmokeDemo/RomAudioSmokeDemo/PpuDebugViewerDemo are manual "run it and look/listen" entry
    //points, not automatically testable - same for the Swing window/panels the debug viewer paints
    //with (TileDecoder is deliberately not excluded here - it's the one piece of that package that's
    //a pure, fully-testable function)
    //trailing "*" on PpuDebugViewerDemo also catches its anonymous WindowAdapter ($1)
    excludedClasses.set(listOf(
            "com.rox.AudioSmokeDemo", "com.rox.RomAudioSmokeDemo", "com.rox.PpuDebugViewerDemo*",
            "com.rox.ppu.debug.PpuDebugFrame", "com.rox.ppu.debug.ChrViewerPanel",
            "com.rox.ppu.debug.NametableViewerPanel", "com.rox.ppu.debug.OamViewerPanel",
            "com.rox.ppu.debug.RegisterHudPanel"
    ))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(90)
    coverageThreshold.set(90)
    failWhenNoMutations.set(true)
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

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                //see the matching pitest excludedClasses comment above for why these are excluded
                exclude(
                        "com/rox/AudioSmokeDemo.class", "com/rox/RomAudioSmokeDemo.class",
                        "com/rox/PpuDebugViewerDemo.class", "com/rox/PpuDebugViewerDemo\$1.class",
                        "com/rox/ppu/debug/PpuDebugFrame.class",
                        "com/rox/ppu/debug/ChrViewerPanel.class", "com/rox/ppu/debug/NametableViewerPanel.class",
                        "com/rox/ppu/debug/OamViewerPanel.class", "com/rox/ppu/debug/RegisterHudPanel.class"
                )
            }
        })
    )

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

tasks.register("pitestBadge") {
    description = "Substitute for the lack of a Pitest report service"
    dependsOn("pitest")

    doLast {
        val mutations = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file("build/reports/pitest/mutations.xml"))
            .getElementsByTagName("mutation")

        val statuses = (0 until mutations.length)
            .map { mutations.item(it) }
            .map {
                it.attributes
                    .getNamedItem("status")
                    .nodeValue
            }

        val total = statuses.size
        val covered = statuses.count { it != "NO_COVERAGE" }

        val score = if (total == 0) 0 else
            ((covered.toDouble() / total) * 100).roundToInt()

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