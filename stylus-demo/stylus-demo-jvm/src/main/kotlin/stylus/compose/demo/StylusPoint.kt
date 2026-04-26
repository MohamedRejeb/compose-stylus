package stylus.compose.demo

import java.io.Serializable
import java.util.*
import kotlin.math.atan2
import kotlin.math.sqrt

class StylusPoint
/**
 * Creates a new instance of `Point2D` with origin coordinates.
 */ @JvmOverloads constructor(
    /** The x coordinate.  */
    var x: Float = 0f,
    /** The y coordinate.  */
    var y: Float = 0f,
    /** The pressure at current coordinate.  */
    var pressure: Float = 0f
) :
    Cloneable, Serializable {
    /**
     * The x coordinate of the point.
     *
     * @return The x coordinate.
     */

    /**
     * The y coordinate of the point.
     *
     * @return The y coordinate.
     */


    /**
     * Creates a new instance of `Point2D` with coordinates from
     * provided point.
     */
    constructor(point: StylusPoint) : this(point.x, point.y, point.pressure)

    /**
     * Creates a new instance of `Point2D` with specified coordinates.
     *
     * @param x The x coordinate of the point.
     * @param y The y coordinate of the point.
     */

    /**
     * Sets new coordinates of this point.
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    fun set(x: Float, y: Float): StylusPoint {
        this.x = x
        this.y = y

        return this
    }

    fun set(p: StylusPoint): StylusPoint {
        set(p.x, p.y)

        return this
    }

    /**
     * Returns the distance from this `Point2D` to a specified
     * `Point2D`.
     *
     * @param p The point to which the distance should be measured.
     *
     * @return The distance to the given point.
     */
    fun distance(p: StylusPoint): Float {
        val dx = p.x - x
        val dy = p.y - y

        return sqrt(dx * dx + dy * dy)
    }

    fun add(p: StylusPoint): StylusPoint {
        x += p.x
        y += p.y

        return this
    }

    fun subtract(p: StylusPoint): StylusPoint {
        x -= p.x
        y -= p.y

        return this
    }

    fun interpolate(v: StylusPoint, f: Float): StylusPoint {
        return StylusPoint(
            this.x + (v.x - this.x) * f, this.y + (v.y - this.y) * f,
            pressure
        )
    }

    fun multiply(scalar: Float): StylusPoint {
        x *= scalar
        y *= scalar

        return this
    }

    fun magnitude(): Float {
        return sqrt(x * x + y * y)
    }

    fun magnitudeSqr(): Float {
        return x * x + y * y
    }

    fun angleBetween(p: StylusPoint): Float {
        return atan2(x - p.x, y - p.y)
    }

    fun normalize(): StylusPoint {
        val length = sqrt(x * x + y * y)

        x /= length
        y /= length

        return this
    }

    fun normalize(length: Float): StylusPoint {
        var mag = sqrt(x * x + y * y)

        if (mag > 0) {
            mag = length / mag
            x *= mag
            y *= mag
        }

        return this
    }

    fun perpendicular(): StylusPoint {
        val t = x
        this.x = -y
        this.y = t

        return this
    }

    fun getNormal(p: StylusPoint): StylusPoint {
        val dx = p.x - x
        val dy = p.y - y

        return StylusPoint(-dy, dx, pressure)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as StylusPoint

        val xc = that.x.compareTo(x) == 0
        val yc = that.y.compareTo(y) == 0
        val pc = that.pressure.compareTo(pressure) == 0

        return xc && yc && pc
    }

    override fun hashCode(): Int {
        return Objects.hash(x, y, pressure)
    }

    public override fun clone(): StylusPoint {
        return StylusPoint(x, y, pressure)
    }

    override fun toString(): String {
        return javaClass.name + " (" + x + ", " + y + ", " + pressure + ")"
    }

    companion object {
        private const val serialVersionUID = 3737399850497337585L
    }
}