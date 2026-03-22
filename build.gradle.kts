plugins {
    kotlin("jvm") version "1.9.22"
}

group = "vip.trushin.intellij"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

val remoteRobotVersion = "0.11.23"

// Configuration to download the robot-server-plugin JAR
val robotServerPlugin by configurations.creating

dependencies {
    // JetBrains Remote Robot
    implementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    implementation("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")

    // Kotlin standard library
    implementation(kotlin("stdlib"))

    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // OkHttp for inspection server access
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Robot Server Plugin — injected into IntelliJ at launch time
    robotServerPlugin("com.intellij.remoterobot:robot-server-plugin:$remoteRobotVersion")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    testImplementation("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")
    testImplementation(kotlin("test"))
}

// Copy robot-server-plugin JAR to a known location for IDE injection
tasks.register<Copy>("downloadRobotPlugin") {
    group = "ide"
    description = "Download robot-server-plugin JAR for IntelliJ injection"
    from(robotServerPlugin)
    into(layout.buildDirectory.dir("robot-plugin"))
}

// JVM args needed for Gson reflection on Java 17+
val jvmOpenArgs = listOf(
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens", "java.base/java.io=ALL-UNNAMED",
    "--add-opens", "java.base/java.util=ALL-UNNAMED"
)

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
    jvmArgs(jvmOpenArgs)
    systemProperty("robot.host", System.getProperty("robot.host", "127.0.0.1"))
    systemProperty("robot.port", System.getProperty("robot.port", "8082"))
    systemProperty("captures.dir", System.getProperty("captures.dir",
        "${project.rootDir}/captures"))
    systemProperty("event.log", System.getProperty("event.log",
        "${System.getProperty("user.home")}/git-flow-log.jsonl"))
}

// Integration tests require a running IntelliJ IDEA with Remote Robot server
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Run integration tests against a running IntelliJ IDEA instance"
    useJUnitPlatform {
        includeTags("integration")
    }
    jvmArgs(jvmOpenArgs)
    systemProperty("robot.host", System.getProperty("robot.host", "127.0.0.1"))
    systemProperty("robot.port", System.getProperty("robot.port", "8082"))
    systemProperty("captures.dir", System.getProperty("captures.dir",
        "${project.rootDir}/captures"))
    systemProperty("event.log", System.getProperty("event.log",
        "${System.getProperty("user.home")}/git-flow-log.jsonl"))
}

kotlin {
    jvmToolchain(17)
}

// Task to launch IntelliJ with robot-server plugin
tasks.register<JavaExec>("launchIde") {
    group = "ide"
    description = "Launch IntelliJ IDEA with Remote Robot server plugin"
    dependsOn("downloadRobotPlugin")
    mainClass.set("vip.trushin.intellij.util.IdeLauncherKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("robot.port", System.getProperty("robot.port", "8082"))
    systemProperty("robot.plugin.dir", layout.buildDirectory.dir("robot-plugin").get().asFile.absolutePath)
}

// Task to generate use-case reports from captured data
tasks.register<JavaExec>("generateReport") {
    group = "reporting"
    description = "Generate Markdown use-case report from captured events"
    mainClass.set("vip.trushin.intellij.capture.ReportGeneratorKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(
        System.getProperty("event.log", "${System.getProperty("user.home")}/git-flow-log.jsonl"),
        System.getProperty("captures.dir", "${project.rootDir}/captures"),
        System.getProperty("report.output", "${project.rootDir}/use-case-report.md")
    )
}
