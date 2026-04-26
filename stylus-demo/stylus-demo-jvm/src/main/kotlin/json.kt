import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import stylus.compose.demo.StylusPoint

fun pointsToJson(
    points: List<StylusPoint>,
    clipboardManager: ClipboardManager,
) {

    // do it from scratch

    val json = StringBuilder()

    json.append("{")
    json.append("\"points\": [")
    points.forEachIndexed { index, point ->
        json.append("{")
        json.append("\"x\": ${point.x},")
        json.append("\"y\": ${point.y},")
        json.append("\"pressure\": ${point.pressure}")
        json.append("}")
        if (index < points.size - 1) {
            json.append(",")
        }
    }
    json.append("]")
    json.append("}")

    val jsonString = json.toString()

    clipboardManager.setText(AnnotatedString(jsonString))
    println(jsonString)
}