package com.lyeeedar.Renderables

import com.badlogic.gdx.math.Vector2
import com.lyeeedar.Util.Colour
import com.lyeeedar.Util.ciel
import squidpony.squidgrid.FOV

class Light(colour: Colour? = null, brightness: Float = 1f, range: Float = 3f)
{
	var pos = Vector2()

	val colour = Colour.WHITE.copy()
	var range = 0f

	init
	{
		if (colour != null)
		{
			this.colour.set(colour).mul(brightness, brightness, brightness, 1f)
		}

		this.range = range
	}

	val cache: ShadowCastCache = ShadowCastCache(fovType = FOV.RIPPLE)

	var batchID: Int = 0

	fun update(delta: Float)
	{
		cache.getShadowCast(pos.x.toInt(), pos.y.toInt(), range.ciel())
	}

	fun copy(): Light
	{
		val light = Light(colour, range)
		return light
	}

	override fun hashCode(): Int
	{
		val posHash = ((pos.x * 1000 + pos.y) * 1000).toInt()
		val colHash = colour.hashCode()
		val rangeHash = (range * 1000).toInt()

		return posHash + colHash + rangeHash
	}
}