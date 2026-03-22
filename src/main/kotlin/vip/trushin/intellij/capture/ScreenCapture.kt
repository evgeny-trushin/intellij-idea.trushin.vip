package vip.trushin.intellij.capture

import com.intellij.remoterobot.RemoteRobot
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Handles screenshot capture and UI tree dumps (R6.1, R6.3).
 *
 * Screenshot strategy (in priority order):
 * 1. IDE-internal paint: Renders the IDE frame from inside the JVM via
 *    SwingUtilities.invokeAndWait + frame.paint(). This captures ONLY the
 *    IntelliJ window content — no other apps, no desktop, no terminal.
 * 2. Remote Robot API: robot.getScreenshot() — captures the full display.
 * 3. macOS screencapture: screencapture -x — full display fallback.
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
     * Take a screenshot of the IntelliJ IDE window only.
     * Uses IDE-internal rendering to capture exactly the IDE content,
     * excluding other applications and the desktop.
     *
     * Returns the absolute path to the saved PNG file.
     */
    fun takeScreenshot(label: String): String {
        val timestamp = LocalDateTime.now().format(timestampFormat)
        val filename = "${timestamp}_${sanitize(label)}.png"
        val file = capturesDir.resolve(filename).toFile()

        // Strategy 1: IDE-internal paint (captures only IntelliJ window)
        if (captureViaIdePaint(file)) {
            return file.absolutePath
        }

        // Strategy 2: Remote Robot API (captures full display)
        if (captureViaRobotApi(file)) {
            return file.absolutePath
        }

        // Strategy 3: macOS screencapture (captures full display, last resort)
        captureViaMacScreencapture(file)
        return file.absolutePath
    }

    /**
     * Capture the IDE frame + all visible popups/dialogs by compositing them
     * onto a single BufferedImage on the EDT.
     *
     * This renders ONLY IntelliJ IDEA content — no other apps visible.
     *
     * How it works:
     * 1. Gets the project's JFrame from WindowManager
     * 2. Paints the main frame to a BufferedImage
     * 3. Finds all visible child windows (popups, dialogs, tooltips)
     * 4. Paints each overlay at its correct position relative to the frame
     * 5. Result: a composite image showing the frame + all active dialogs
     */
    private fun captureViaIdePaint(file: File): Boolean {
        return try {
            val filePath = file.absolutePath.replace("\\", "/").replace("'", "\\'")
            robot.callJs<Boolean>("""
                importClass(javax.swing.SwingUtilities);
                importClass(java.awt.image.BufferedImage);
                importClass(javax.imageio.ImageIO);
                importClass(java.io.File);
                importClass(java.util.concurrent.atomic.AtomicBoolean);

                var success = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(new java.lang.Runnable({
                    run: function() {
                        try {
                            var projects = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects();
                            if (projects.length == 0) return;
                            var frame = com.intellij.openapi.wm.WindowManager.getInstance().getFrame(projects[0]);
                            if (frame == null) return;
                            var fb = frame.getBounds();
                            if (fb.width <= 0 || fb.height <= 0) return;

                            // Paint the main IDE frame
                            var img = new BufferedImage(fb.width, fb.height, BufferedImage.TYPE_INT_RGB);
                            var g = img.createGraphics();
                            frame.paint(g);
                            g.dispose();

                            // Composite all visible overlay windows (popups, dialogs)
                            // on top of the frame at their correct positions
                            var allWindows = java.awt.Window.getWindows();
                            for (var i = 0; i < allWindows.length; i++) {
                                var w = allWindows[i];
                                if (w.isVisible() && w !== frame && w.getWidth() > 10 && w.getHeight() > 10) {
                                    var wb = w.getBounds();
                                    var rx = wb.x - fb.x;
                                    var ry = wb.y - fb.y;
                                    // Only paint windows that overlap the frame area
                                    if (rx >= -wb.width && ry >= -wb.height && rx < fb.width && ry < fb.height) {
                                        var g2 = img.createGraphics();
                                        g2.translate(rx, ry);
                                        w.paint(g2);
                                        g2.dispose();
                                    }
                                }
                            }

                            ImageIO.write(img, "png", new File("$filePath"));
                            success.set(true);
                        } catch(e) {
                            // Paint failed — fall through to other strategies
                        }
                    }
                }));
                success.get();
            """.trimIndent())
            if (file.exists() && file.length() > 0) {
                logger.info("IDE-internal screenshot saved: {}", file.absolutePath)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.debug("IDE-internal screenshot failed: {}", e.message)
            false
        }
    }

    /**
     * Capture via Remote Robot's getScreenshot() API.
     * This captures the full display, not just the IDE window.
     */
    private fun captureViaRobotApi(file: File): Boolean {
        return try {
            val image = robot.getScreenshot()
            javax.imageio.ImageIO.write(image, "png", file)
            logger.info("Robot API screenshot saved: {}", file.absolutePath)
            true
        } catch (e: Exception) {
            logger.debug("Robot API screenshot failed: {}", e.message)
            false
        }
    }

    /**
     * macOS screencapture fallback (Layer 3 - R10).
     * Activates IntelliJ first to ensure it's the frontmost window,
     * then captures the screen.
     */
    private fun captureViaMacScreencapture(file: File) {
        try {
            // Activate IntelliJ IDEA to bring it to front before capture
            val activate = ProcessBuilder("osascript", "-e",
                "tell application \"IntelliJ IDEA\" to activate")
                .redirectErrorStream(true)
                .start()
            activate.waitFor()
            Thread.sleep(300)

            val process = ProcessBuilder("screencapture", "-x", file.absolutePath)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                logger.info("macOS screencapture saved: {}", file.absolutePath)
            } else {
                logger.error("screencapture failed with exit code: {}", exitCode)
            }
        } catch (e: Exception) {
            logger.error("macOS screencapture failed: {}", e.message)
        }
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
