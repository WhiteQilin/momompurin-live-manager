package ph.edu.umak.momompurin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compose rejects a negative `Dp` in `Modifier.padding` at runtime with
 * `IllegalArgumentException: Padding must be non-negative`.
 *
 * That is not a warning: it throws while the composition is measuring, so the
 * screen crashes the moment it is opened. The Dashboard did exactly that - the
 * offline notice cancelled the LazyColumn's gutter with
 * `.padding(horizontal = -MomoSpace.Lg)`, so turning Offline mode ON and tapping
 * Home killed the app (`DashboardScreen.kt:109`).
 *
 * A runtime rule that can be checked statically belongs in the build. This test
 * walks every Kotlin source file under `ui/` and fails on a negated value inside a
 * `padding(...)` call - the shape the crash took - plus any bare negative `dp`
 * literal anywhere in the package, which is the same mistake written differently.
 */
class NoNegativePaddingTest {

    /** The source roots a Gradle test can see, module-relative. */
    private val roots = listOf(
        File("src/main/java/ph/edu/umak/momompurin"),
        File("app/src/main/java/ph/edu/umak/momompurin"),
    )

    private fun kotlinFiles(): List<File> {
        val root = roots.firstOrNull { it.isDirectory }
        assertTrue(
            "source root not found from ${File(".").absolutePath}; " +
                "tried ${roots.joinToString()}",
            root != null,
        )
        return root!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    /**
     * `padding(` ... `-` with no other operator between the open paren and the
     * minus. Catches `padding(horizontal = -MomoSpace.Lg)`, `padding(-8.dp)`,
     * `padding(start = -x)` and the `padding( all = -1.dp )` spacing variants.
     */
    private val negativePadding = Regex(
        """padding\s*\([^)]*?=\s*-\s*[A-Za-z0-9_(]""",
    )

    /** A negated Dp constant: `-MomoSpace.Lg`, `-16.dp`, `-MomoDimens.X`. */
    private val negatedDp = Regex(
        """-\s*(MomoSpace|MomoDimens|MomoRadius)\.[A-Za-z]|-\s*\d+(\.\d+)?\.dp""",
    )

    private fun codeLines(file: File): List<Pair<Int, String>> =
        file.readLines().mapIndexedNotNull { index, raw ->
            val line = raw.trim()
            // Comments cannot crash anything, and the fix notes quote the old code.
            when {
                line.startsWith("//") -> null
                line.startsWith("*") -> null
                line.startsWith("/*") -> null
                line.contains("Padding must be non-negative") -> null
                else -> (index + 1) to raw
            }
        }

    @Test
    fun no_padding_call_receives_a_negative_value() {
        val offenders = mutableListOf<String>()
        kotlinFiles().forEach { file ->
            codeLines(file).forEach { (lineNo, line) ->
                if (negativePadding.containsMatchIn(line)) {
                    offenders += "${file.name}:$lineNo  ${line.trim()}"
                }
            }
        }
        assertTrue(
            "Modifier.padding received a negative Dp (crashes with " +
                "\"Padding must be non-negative\"):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun no_negated_dp_constant_appears_anywhere_in_the_ui_package() {
        val offenders = mutableListOf<String>()
        kotlinFiles().forEach { file ->
            codeLines(file).forEach { (lineNo, line) ->
                if (negatedDp.containsMatchIn(line)) {
                    offenders += "${file.name}:$lineNo  ${line.trim()}"
                }
            }
        }
        assertTrue(
            "A negated Dp constant is present. Compose has no negative spacing " +
                "concept - restructure the layout instead:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * The Dashboard case specifically: the notice must be OUTSIDE the padded list,
     * so this asserts the shape of the fix rather than only the absence of the bug.
     */
    @Test
    fun the_dashboard_renders_the_header_notice_outside_the_padded_list() {
        val root = roots.firstOrNull { it.isDirectory } ?: error("no source root")
        val dashboard = File(root, "ui/screens/DashboardScreen.kt")
        assertTrue("DashboardScreen.kt not found", dashboard.isFile)
        val text = dashboard.readText()

        assertTrue(
            "the Dashboard must read the header notice",
            text.contains("LocalHeaderNotice.current"),
        )
        // The notice is drawn in an outer Column, before the LazyColumn, and the
        // list's own contentPadding carries no negative value.
        val noticeIndex = text.indexOf("headerNotice()")
        val listIndex = text.indexOf("LazyColumn(")
        assertTrue("expected both the notice and the list", noticeIndex > 0 && listIndex > 0)
        assertTrue(
            "the notice must be composed BEFORE the LazyColumn (outer Column), " +
                "not as a list item with a negative gutter",
            noticeIndex < listIndex,
        )
    }
}
