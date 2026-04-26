import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    application {
        val state = rememberWindowState()

        Window(
            onCloseRequest = ::exitApplication,
            state = state
        ) {
            PathDem()
        }
    }
}

@Composable
fun PathDem() {
    val path = remember { Path() }

    var update by remember {
        mutableIntStateOf(0)
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (path.isEmpty)
                        path.moveTo(offset.x, offset.y).also {
                            println("MoveTo: ${offset.x}, ${offset.y}")
                        }
                    else
                        path.lineTo(offset.x, offset.y).also {
                            println("LineTo: ${offset.x}, ${offset.y}")
                        }

                    update++
                }
            }
    ) {
        update

        drawPath(
            path = path,
            color = Color.Black
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        IconButton(
            onClick = {
                path.reset()
                update++
            },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.Clear, contentDescription = "Clear")
        }
    }

    Icon(SvgviewerOutput, null, tint = Color.Black)
}

/**
 * MoveTo: 654.0, 236.0
 * LineTo: 430.0, 374.0
 * LineTo: 344.0, 560.0
 * LineTo: 654.0, 492.0
 * LineTo: 656.0, 238.0
 * LineTo: 530.0, 468.0
 * LineTo: 518.0, 242.0
 *
 * convert this to svg
 *
 <svg width="1000" height="1000" xmlns="http://www.w3.org/2000/svg">
     <path d="M 654.0 236.0 L 430.0 374.0 L 344.0 560.0 L 654.0 492.0 L 656.0 238.0 L 530.0 468.0 L 518.0 242.0" fill="none" stroke="black" stroke-width="1"/>
   </svg>
 */
