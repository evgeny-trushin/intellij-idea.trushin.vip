package vip.trushin.intellij.capture

import com.intellij.remoterobot.RemoteRobot
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/**
 * Handles screenshot capture and UI tree dumps (R6.1, R6.3).
 */
class ScreenCapture(
    private val capturesDir: Path,
    private val robot: RemoteRobot,
    private val robotPort: Int = 8082
) {
    private val logger = LoggerFactory.getLogger(ScreenCapture::class.java)
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

    init {
        capturesDir.toFile().mkdirs()
    }

    /**
     * Take a screenshot via Remote Robot and save to captures directory.
     * Returns the path to the saved screenshot file.
     */
    fun takeScreenshot(label: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormat)
        val filename = "${timestamp}_${sanitize(label)}.png"
        val file = capturesDir.resolve(filename).toFile()

        try {
            val image: BufferedImage = robot.getScreenshot()
            ImageIO.write(image, "png", file)
            logger.info("Screenshot saved: {}", file.absolutePath)
        } catch (e: Exception) {
            logger.warn("Remote Robot screenshot failed, falling back to screencapture: {}", e.message)
            fallbackScreenshot(file)
        }
        return file.absolutePath
    }

    /**
     * Dump the UI component tree from the inspection server (R7.3).
     * Returns the path to the saved hierarchy XML file.
     */
    fun dumpUiTree(label: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormat)
        val filename = "${timestamp}_${sanitize(label)}_hierarchy.xml"
        val file = capturesDir.resolve(filename).toFile()

        try {
            dumpViaInspection(file)
        } catch (e: Exception) {
            logger.warn("UI tree dump failed: {}", e.message)
            file.writeText("<error>${e.message}</error>")
        }
        return file.absolutePath
    }

    /**
     * macOS screencapture fallback (Layer 3 - R10).
     */
    private fun fallbackScreenshot(file: File) {
        try {
            val process = ProcessBuilder("screencapture", "-x", file.absolutePath)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                logger.info("Fallback screenshot saved: {}", file.absolutePath)
            } else {
                logger.error("screencapture failed with exit code: {}", exitCode)
            }
        } catch (e: Exception) {
            logger.error("Fallback screencapture failed: {}", e.message)
        }
    }

    /**
     * Dump UI hierarchy via HTTP inspection server endpoint.
     */
    private fun dumpViaInspection(file: File) {
        val url = "http://127.0.0.1:$robotPort/hierarchy"
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        val response = connection.inputStream.bufferedReader().readText()
        file.writeText(response)
        logger.info("UI tree via inspection endpoint saved: {}", file.absolutePath)
    }

    /**
     * Start screen recording via macOS screencapture (R6.5).
     * Returns the Process which can be stopped later.
     */
    fun startScreenRecording(label: String): Pair<Process, String> {
        val timestamp = LocalDateTime.now().format(timestampFormat)
        val filename = "${timestamp}_${sanitize(label)}_recording.mov"
        val file = capturesDir.resolve(filename).toFile()

        val process = ProcessBuilder("screencapture", "-v", file.absolutePath)
            .redirectErrorStream(true)
            .start()
        logger.info("Screen recording started: {}", file.absolutePath)
        return Pair(process, file.absolutePath)
    }

    /**
     * Stop a running screen recording process.
     */
    fun stopScreenRecording(process: Process) {
        process.destroy()
        logger.info("Screen recording stopped")
    }

    private fun sanitize(label: String): String =
        label.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(60)
}
