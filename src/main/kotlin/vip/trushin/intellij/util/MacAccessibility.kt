package vip.trushin.intellij.util

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Layer 3: macOS Accessibility fallback (R10, P6).
 * Used for native system dialogs that Swing automation cannot reach.
 */
object MacAccessibility {

    private val logger = LoggerFactory.getLogger(MacAccessibility::class.java)

    /**
     * Execute an AppleScript command and return the output.
     */
    fun runAppleScript(script: String): String {
        val process = ProcessBuilder("osascript", "-e", script)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.warn("AppleScript exited with code {}: {}", exitCode, output)
        }
        return output
    }

    /**
     * Bring IntelliJ IDEA to the foreground.
     */
    fun activateIntelliJ() {
        runAppleScript("""
            tell application "IntelliJ IDEA" to activate
        """.trimIndent())
    }

    /**
     * Get the title of the frontmost IntelliJ window.
     */
    fun getFrontWindowTitle(): String {
        return runAppleScript("""
            tell application "System Events"
                tell process "idea"
                    get name of front window
                end tell
            end tell
        """.trimIndent())
    }

    /**
     * Click a button in a native macOS dialog by name.
     */
    fun clickNativeButton(buttonName: String) {
        runAppleScript("""
            tell application "System Events"
                tell process "idea"
                    click button "$buttonName" of front window
                end tell
            end tell
        """.trimIndent())
    }

    /**
     * Handle a native file chooser dialog (open/save).
     * These are invisible to Swing automation (R10.6).
     */
    fun handleNativeFileChooser(filePath: String) {
        runAppleScript("""
            tell application "System Events"
                tell process "idea"
                    tell sheet 1 of front window
                        set value of text field 1 to "$filePath"
                        click button "Open"
                    end tell
                end tell
            end tell
        """.trimIndent())
    }

    /**
     * Take a screenshot using macOS screencapture.
     */
    fun takeScreenshot(outputPath: String): Boolean {
        return try {
            val process = ProcessBuilder("screencapture", "-x", outputPath)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            logger.error("screencapture failed: {}", e.message)
            false
        }
    }

    /**
     * Check if accessibility permissions are granted (P6).
     */
    fun checkAccessibilityPermission(): Boolean {
        val result = runAppleScript("""
            tell application "System Events"
                try
                    get name of first process
                    return "granted"
                on error
                    return "denied"
                end try
            end tell
        """.trimIndent())
        return result.contains("granted") || !result.contains("denied")
    }

    /**
     * Dump the accessibility tree for the IntelliJ process.
     * Useful for discovering native UI elements.
     */
    fun dumpAccessibilityTree(outputFile: File) {
        val script = """
            tell application "System Events"
                tell process "idea"
                    set windowInfo to ""
                    repeat with w in windows
                        set windowInfo to windowInfo & "Window: " & name of w & return
                        try
                            repeat with uiElem in UI elements of w
                                set windowInfo to windowInfo & "  " & class of uiElem & ": " & description of uiElem & return
                            end repeat
                        end try
                    end repeat
                    return windowInfo
                end tell
            end tell
        """.trimIndent()
        val output = runAppleScript(script)
        outputFile.writeText(output)
        logger.info("Accessibility tree dumped to: {}", outputFile.absolutePath)
    }
}
