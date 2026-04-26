package com.mohamedrejeb.stylus

/**
 * A single pen / stylus / touch event.
 *
 * Coordinates and tilt/rotation are kept as [Double] to preserve the precision
 * native APIs report (Cocoa NSEvent, X11 XInput2, Win32 RTS, DOM PointerEvent).
 *
 * @property type Which kind of pen action (hover / move / press / release).
 * @property tool Hardware tool currently in use (pen, eraser, mouse, touch, none).
 * @property button Pen button that triggered the event, or [PenButton.None].
 * @property x X coordinate in the host's local pixel space.
 * @property y Y coordinate in the host's local pixel space.
 * @property pressure Normalized pressure in `[0, 1]`, or `0` if unsupported.
 * @property tangentPressure Normalized tangential pressure in `[0, 1]`. Most
 *   devices report `0`.
 * @property tiltX Tilt around the X axis (radians on iOS, degrees elsewhere — see platform impl).
 * @property tiltY Tilt around the Y axis (same units as [tiltX]).
 * @property rotation Barrel rotation in radians, or `0` if unsupported.
 * @property timestamp Event time in milliseconds since the Unix epoch.
 */
data class PenEvent(
    val type: PenEventType,
    val tool: PenTool,
    val button: PenButton,
    val x: Double,
    val y: Double,
    val pressure: Double,
    val tangentPressure: Double = 0.0,
    val tiltX: Double = 0.0,
    val tiltY: Double = 0.0,
    val rotation: Double = 0.0,
    val timestamp: Long = currentTimeMillis(),
) {
    /** Return a copy with translated `x` / `y`. Other axes are preserved. */
    fun translate(x: Double, y: Double): PenEvent = copy(x = x, y = y)

    /**
     * Constructor used by the native JNI bridge — keep stable.
     *
     * Axis layout (same order on the C++ side, indexed by `stylus::Axis`):
     * `[X, Y, PRESSURE, TANGENT_PRESSURE, TILT_X, TILT_Y, ROTATION]`.
     */
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(type: PenEventType, tool: PenTool, button: PenButton, axes: DoubleArray) : this(
        type = type,
        tool = tool,
        button = button,
        x = axes.getOrElse(0) { 0.0 },
        y = axes.getOrElse(1) { 0.0 },
        pressure = axes.getOrElse(2) { 0.0 },
        tangentPressure = axes.getOrElse(3) { 0.0 },
        tiltX = axes.getOrElse(4) { 0.0 },
        tiltY = axes.getOrElse(5) { 0.0 },
        rotation = axes.getOrElse(6) { 0.0 },
    )
}

internal expect fun currentTimeMillis(): Long
