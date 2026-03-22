package vip.trushin.intellij.util

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Launches IntelliJ IDEA with the Remote Robot server plugin enabled (R4.2).
 * Configures launch flags to disable native macOS dialogs (R1.5).
 */
object IdeLauncher {

    private val logger = LoggerFactory.getLogger(IdeLauncher::class.java)

    /**
     * Standard macOS application paths where IntelliJ IDEA may be installed.
     */
    private val IDEA_PATHS = listOf(
        "/Applications/IntelliJ IDEA.app",
        "/Applications/IntelliJ IDEA CE.app",
        "/Applications/IntelliJ IDEA Ultimate.app",
        "${System.getProperty("user.home")}/Applications/IntelliJ IDEA.app",
        "${System.getProperty("user.home")}/Applications/IntelliJ IDEA CE.app"
    )

    /**
     * Find the IntelliJ IDEA installation path.
     */
    fun findIdeaPath(): Path? {
        return IDEA_PATHS
            .map { Paths.get(it) }
            .firstOrNull { it.toFile().exists() }
    }

    /**
     * Build the VM options for launching with Robot Server plugin.
     *
     * @param robotPort Port for the inspection/robot server (default: 8082)
     * @param robotPluginPath Path to the robot-server-plugin JAR (downloaded by Gradle)
     */
    fun buildVmOptions(robotPort: Int = 8082, robotPluginPath: String? = null): List<String> {
        val options = mutableListOf(
            // R1.5: Disable native macOS dialogs
            "-Dide.mac.file.chooser.native=false",
            "-DjbScreenMenuBar.enabled=false",
            "-Dapple.laf.useScreenMenuBar=false",
            // Robot server configuration
            "-Drobot-server.port=$robotPort",
            "-Didea.trust.all.projects=true",
            // Performance
            "-Xmx4g",
            "-XX:ReservedCodeCacheSize=512m"
        )

        if (robotPluginPath != null) {
            options.add("-Dplugin.path=$robotPluginPath")
        }

        return options
    }

    /**
     * Launch IntelliJ IDEA with robot server enabled.
     *
     * @param projectPath Path to the project to open
     * @param robotPort Robot server port
     * @param waitForStartup Whether to wait for the IDE to be ready
     * @return The launched process
     */
    fun launch(
        projectPath: Path? = null,
        robotPort: Int = 8082,
        waitForStartup: Boolean = true
    ): Process {
        val ideaPath = findIdeaPath()
            ?: throw IllegalStateException(
                "IntelliJ IDEA not found. Searched: ${IDEA_PATHS.joinToString(", ")}"
            )

        val ideaBinary = ideaPath.resolve("Contents/MacOS/idea")
        if (!ideaBinary.toFile().exists()) {
            throw IllegalStateException("IDE binary not found at: $ideaBinary")
        }

        // Install robot-server-plugin into IntelliJ's plugins directory
        val pluginDir = System.getProperty("robot.plugin.dir")
        installRobotPlugin(pluginDir)

        val vmOptions = buildVmOptions(robotPort)
        val command = mutableListOf(ideaBinary.toString())

        logger.info("Launching IntelliJ IDEA from: {}", ideaPath)
        logger.info("VM options: {}", vmOptions.joinToString(" "))

        if (projectPath != null) {
            command.add(projectPath.toString())
            logger.info("Opening project: {}", projectPath)
        }

        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)

        // Set IDEA_PROPERTIES and IDEA_VM_OPTIONS environment variables
        val env = processBuilder.environment()
        env["IDEA_PROPERTIES"] = createPropertiesFile(vmOptions).absolutePath
        env["IDEA_VM_OPTIONS"] = createVmOptionsFile(vmOptions).absolutePath

        val process = processBuilder.start()
        logger.info("IntelliJ IDEA launched (PID: {})", process.pid())

        if (waitForStartup) {
            waitForRobotServer(robotPort)
        }

        return process
    }

    /**
     * Create a temporary vmoptions file for IntelliJ launch.
     */
    private fun createVmOptionsFile(vmOptions: List<String>): File {
        val vmFile = File.createTempFile("idea_gitflow_", ".vmoptions")
        vmFile.deleteOnExit()
        vmFile.writeText(vmOptions.joinToString("\n"))
        logger.info("VM options file created: {}", vmFile.absolutePath)
        return vmFile
    }

    /**
     * Create a temporary properties file with the required VM options.
     */
    private fun createPropertiesFile(vmOptions: List<String>): File {
        val propsFile = File.createTempFile("idea_gitflow_", ".properties")
        propsFile.deleteOnExit()

        val properties = vmOptions
            .filter { it.startsWith("-D") }
            .map {
                val kv = it.removePrefix("-D")
                val eq = kv.indexOf('=')
                if (eq > 0) "${kv.substring(0, eq)}=${kv.substring(eq + 1)}" else kv
            }

        propsFile.writeText(properties.joinToString("\n"))
        logger.info("Properties file created: {}", propsFile.absolutePath)
        return propsFile
    }

    /**
     * Wait for the Robot Server to become available (P7: resilience over speed).
     */
    fun waitForRobotServer(port: Int = 8082, timeoutSeconds: Int = 120) {
        logger.info("Waiting for Robot Server on port {}...", port)
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        var lastError: String? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val url = java.net.URL("http://127.0.0.1:$port")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code in 200..299) {
                    logger.info("Robot Server ready on port {}", port)
                    return
                }
                lastError = "HTTP $code"
            } catch (e: Exception) {
                lastError = e.message
            }
            Thread.sleep(2000)
        }

        throw IllegalStateException(
            "Robot Server not ready after ${timeoutSeconds}s on port $port. Last error: $lastError"
        )
    }

    /**
     * Install robot-server-plugin into IntelliJ's user plugins directory.
     * The plugin ZIP is extracted to ~/Library/Application Support/JetBrains/<version>/plugins/
     */
    fun installRobotPlugin(downloadDir: String?) {
        if (downloadDir == null) {
            logger.info("No robot plugin download dir specified, skipping install")
            return
        }

        val pluginZip = File(downloadDir).listFiles()
            ?.firstOrNull { it.name.contains("robot-server-plugin") && it.extension == "zip" }

        if (pluginZip == null) {
            logger.warn("robot-server-plugin ZIP not found in {}", downloadDir)
            return
        }

        // Find IntelliJ plugins directory
        val jetbrainsDir = File(System.getProperty("user.home"),
            "Library/Application Support/JetBrains")
        val ideaConfig = jetbrainsDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("IntelliJIdea") }
            ?.maxByOrNull { it.name }

        if (ideaConfig == null) {
            logger.warn("IntelliJ config directory not found under {}", jetbrainsDir)
            return
        }

        val pluginsTarget = File(ideaConfig, "plugins")
        val robotPluginDir = File(pluginsTarget, "robot-server-plugin")

        if (robotPluginDir.exists()) {
            logger.info("robot-server-plugin already installed at {}", robotPluginDir)
            return
        }

        // Extract ZIP to plugins directory
        logger.info("Installing robot-server-plugin from {} to {}", pluginZip, pluginsTarget)
        val process = ProcessBuilder("unzip", "-o", "-q", pluginZip.absolutePath, "-d", pluginsTarget.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) {
            logger.info("robot-server-plugin installed successfully")
        } else {
            logger.error("Failed to extract plugin (exit {}): {}", exitCode, output)
        }
    }

    /**
     * Check if IntelliJ IDEA is already running.
     */
    fun isIdeaRunning(): Boolean {
        return try {
            val process = ProcessBuilder("pgrep", "-f", "idea")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.trim().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}

fun main() {
    val port = System.getProperty("robot.port", "8082").toInt()
    val pluginDir = System.getProperty("robot.plugin.dir")

    // Always try to install the plugin first
    if (pluginDir != null) {
        println("Installing robot-server-plugin...")
        IdeLauncher.installRobotPlugin(pluginDir)
    }

    if (IdeLauncher.isIdeaRunning()) {
        println("IntelliJ IDEA appears to be running. Checking robot server...")
        try {
            IdeLauncher.waitForRobotServer(port, timeoutSeconds = 10)
            println("Robot server is already available on port $port")
        } catch (e: Exception) {
            println("Robot server not available.")
            println("Restart IntelliJ IDEA to load the robot-server-plugin, then try again.")
            println("Or add -Drobot-server.port=$port to Help > Edit Custom VM Options")
        }
    } else {
        println("Launching IntelliJ IDEA with Robot Server on port $port...")
        IdeLauncher.launch(robotPort = port)
        println("IntelliJ IDEA launched and Robot Server ready on port $port")
    }
}
