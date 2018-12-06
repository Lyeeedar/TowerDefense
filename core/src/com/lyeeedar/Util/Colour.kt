package com.lyeeedar.Util

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.NumberUtils
import com.badlogic.gdx.utils.Pool

/**
 * Created by Philip on 30-Mar-16.
 */

class Colour()
{
	var fixed: Boolean = false

	var r: Float = 0f
		set(value)
		{
			if (fixed) throw Exception("Tried to modify fixed colour!")
			field = value
		}

	var g: Float = 0f
		set(value)
		{
			if (fixed) throw Exception("Tried to modify fixed colour!")
			field = value
		}

	var b: Float = 0f
		set(value)
		{
			if (fixed) throw Exception("Tried to modify fixed colour!")
			field = value
		}

	var a: Float = 0f
		set(value)
		{
			if (fixed) throw Exception("Tried to modify fixed colour!")
			field = value
		}

	constructor(col: Float, fixed: Boolean = false) : this()
	{
		set(col)
		this.fixed = fixed
	}

	constructor(col: Color, fixed: Boolean = false) : this()
	{
		set(col.r, col.g, col.b, col.a)
		this.fixed = fixed
	}

	constructor(col: Colour, fixed: Boolean = false) : this()
	{
		set(col.r, col.g, col.b, col.a)
		this.fixed = fixed
	}

	constructor(r: Float, g:Float, b:Float, a:Float, fixed: Boolean = false) : this()
	{
		set(r, g, b, a)
		this.fixed = fixed
	}

	constructor(r: Int, g:Int, b:Int, a:Int, fixed: Boolean = false) : this()
	{
		set(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f)
		this.fixed = fixed
	}

	fun copy(): Colour = Colour(this)

	fun r(r: Float): Colour
	{
		this.r = r
		return this
	}

	fun g(g: Float): Colour
	{
		this.g = g
		return this
	}

	fun b(b: Float): Colour
	{
		this.b = b
		return this
	}

	fun a(a: Float): Colour
	{
		this.a = a
		return this
	}

	fun set(other: Colour): Colour
	{
		r = other.r
		g = other.g
		b = other.b
		a = other.a

		return this
	}

	fun set(col: Float): Colour
	{
		r = col
		g = col
		b = col
		a = col

		return this
	}

	fun set(col: Color, packed: Float? = null): Colour
	{
		r = col.r
		g = col.g
		b = col.b
		a = col.a

		if (packed != null)
		{
			cachedR = r
			cachedG = g
			cachedB = b
			cachedA = a
			cachedFB = packed
		}

		return this
	}

	fun set(r: Float, g:Float, b:Float, a:Float): Colour
	{
		this.r = r
		this.g = g
		this.b = b
		this.a = a

		return this
	}

	fun clamp()
	{
		r = r.clamp(0f, 1f)
		g = g.clamp(0f, 1f)
		b = b.clamp(0f, 1f)
		a = a.clamp(0f, 1f)
	}

	fun mul(other: Colour) : Colour
	{
		timesAssign(other)
		return this
	}

	fun mul(r: Float, g: Float, b: Float, a: Float): Colour
	{
		this.r *= r
		this.g *= g
		this.b *= b
		this.a *= a
		return this
	}

	operator fun times(other: Colour): Colour
	{
		val col = Colour(this)
		col.r *= other.r
		col.g *= other.g
		col.b *= other.b
		col.a *= other.a

		return col
	}

	operator fun times(other: Float): Colour
	{
		val col = Colour(this)
		col.r *= other
		col.g *= other
		col.b *= other
		col.a *= other

		return col
	}


	operator fun timesAssign(other: Colour)
	{
		r *= other.r
		g *= other.g
		b *= other.b
		a *= other.a
	}

	operator fun timesAssign(other: Color)
	{
		r *= other.r
		g *= other.g
		b *= other.b
		a *= other.a
	}

	operator fun timesAssign(alpha: Float)
	{
		r *= alpha
		g *= alpha
		b *= alpha
		a *= alpha
	}

	operator fun plusAssign(other: Colour)
	{
		r += other.r
		g += other.g
		b += other.b
		a += other.a
	}

	operator fun divAssign(value: Float)
	{
		r /= value
		g /= value
		b /= value
		a /= value
	}

	fun lerp(target: Colour, t: Float) : Colour
	{
		this.r += t * (target.r - this.r)
		this.g += t * (target.g - this.g)
		this.b += t * (target.b - this.b)
		this.a += t * (target.a - this.a)

		return this
	}

	fun lerpHSV(target: Colour, t: Float): Colour
	{
		val start = HSLColour(this)
		val end = HSLColour(target)

		val lerped = start.lerp(end, t)

		return lerped.toRGB()
	}

	fun toFloatBits() : Float
	{
		if (cachedR == r && cachedG == g && cachedB == b && cachedA == a) return cachedFB
		else
		{
			val intBits = (255 * a).toInt() shl 24 or ((255 * b).toInt() shl 16) or ((255 * g).toInt() shl 8) or (255 * r).toInt()
			cachedFB = NumberUtils.intToFloatColor(intBits)

			cachedR = r
			cachedB = b
			cachedG = g
			cachedA = a

			return cachedFB
		}
	}
	var cachedR: Float = -1f
	var cachedG: Float = -1f
	var cachedB: Float = -1f
	var cachedA: Float = -1f
	var cachedFB: Float = -1f

	fun color() : Color
	{
		return Color(r, g, b, a)
	}

	override fun equals(other: Any?): Boolean
	{
		if (other !is Colour) return false

		if (other.r != r) return false
		if (other.g != g) return false
		if (other.b != b) return false
		if (other.a != a) return false

		return true
	}

	fun freeTS()
	{
		synchronized(pool)
		{
			pool.free(this)
		}
	}

	companion object
	{
		val BLACK = Colour(Color.BLACK, true)
		val WHITE = Colour(Color.WHITE, true)
		val LIGHT_GRAY = Colour(Color.LIGHT_GRAY, true)
		val DARK_GRAY = Colour(Color.DARK_GRAY, true)
		val GOLD = Colour(Color.GOLD, true)
		val BLUE = Colour(Color.BLUE, true)
		val GREEN = Colour(Color.GREEN, true)
		val RED = Colour(Color.RED, true)
		val PINK = Colour(Color.PINK, true)
		val ORANGE = Colour(Color.ORANGE, true)
		val YELLOW = Colour(Color.YELLOW, true)

		fun random(s: Float = 0.9f, l: Float = 0.7f): Colour
		{
			val hsl = HSLColour(Random.random(), s, l, 1.0f)
			return hsl.toRGB()
		}

		val pool: Pool<Colour> = object : Pool<Colour>() {
			override fun newObject(): Colour
			{
				return Colour()
			}
		}

		fun obtainTS(): Colour
		{
			synchronized(pool)
			{
				return pool.obtain()
			}
		}
	}
}