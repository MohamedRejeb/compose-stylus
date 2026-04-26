package stylus.compose.demo

import androidx.compose.ui.graphics.Color

class StylusBrush(
    var color: Color = Color.Black,
    var width: Float = 12f
) {
    constructor(brush: StylusBrush) : this(brush.color, brush.width)
}
