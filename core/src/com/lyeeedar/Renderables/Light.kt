package com.lyeeedar.Renderables

import com.badlogic.gdx.math.Vector2
import com.lyeeedar.Util.*
import squidpony.squidgrid.FOV

class Light(colour: Colour? = null, brightness: Float = 1f, range: Float = 3f)
{
	var pos = Vector2()

	val baseColour = Colour.WHITE.copy()
	var baseBrightness = 0f
	var baseRange = 0f

	val colour = Colour.WHITE.copy()
	var range = 0f

	var anim: LightAnimation? = null

	init
	{
		if (colour != null)
		{
			baseColour.set(colour)

			this.colour.set(colour).mul(brightness, brightness, brightness, 1f)
		}

		baseBrightness = brightness
		baseRange = range

		this.range = range
	}

	val cache: ShadowCastCache = ShadowCastCache(fovType = FOV.RIPPLE)

	var batchID: Int = 0

	fun update(delta: Float)
	{
		cache.getShadowCast(pos.x.toInt(), pos.y.toInt(), range.ciel())
		anim?.update(delta, this)
	}

	fun copy(): Light
	{
		val light = Light(colour, range)
		light.baseColour.set(baseColour)
		light.baseBrightness = baseBrightness
		light.baseRange = baseRange
		light.anim = anim?.copy()
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

abstract class LightAnimation
{
	abstract fun update(delta: Float, light: Light)
	abstract fun parse(xmlData: XmlData)

	abstract fun copy(): LightAnimation

	companion object
	{
		fun load(xmlData: XmlData): LightAnimation
		{
			val anim = when(xmlData.getAttribute("meta:RefKey").toUpperCase())
			{
				"PULSELIGHTANIMATION" -> PulseLightAnimation()
				else -> throw Exception("Unknown light animation type '" + xmlData.getAttribute("meta:RefKey") + "'!")
			}

			anim.parse(xmlData)

			return anim
		}
	}
}

class PulseLightAnimation : LightAnimation()
{
	lateinit var periodRange: Range
	lateinit var minBrightnessRange: Range
	lateinit var maxBrightnessRange: Range
	lateinit var minRangeRange: Range
	lateinit var maxRangeRange: Range

	var toMax = true
	var currentPeriod = -1f
	var startBrightness = 0f
	var targetBrightness = 0f
	var startRange = 0f
	var targetRange = 0f
	var time = 0f

	override fun update(delta: Float, light: Light)
	{
		if (currentPeriod < 0f)
		{
			currentPeriod = periodRange.getValue(Random.random)
			startBrightness = minBrightnessRange.getValue(Random.random)
			targetBrightness = maxBrightnessRange.getValue(Random.random)
			startRange = minRangeRange.getValue(Random.random)
			targetRange = maxRangeRange.getValue(Random.random)
			time = 0f
		}

		time += delta

		if (time > currentPeriod)
		{
			time -= currentPeriod

			toMax = !toMax

			currentPeriod = periodRange.getValue(Random.random)
			startBrightness = targetBrightness
			startRange = targetRange

			targetBrightness = if (toMax) maxBrightnessRange.getValue(Random.random) else minBrightnessRange.getValue(Random.random)
			targetRange = if (toMax) maxRangeRange.getValue(Random.random) else minRangeRange.getValue(Random.random)
		}

		val alpha = time / currentPeriod
		val brightness = startBrightness.lerp(targetBrightness, alpha)
		val range = startRange.lerp(targetRange, alpha)

		light.colour.set(light.baseColour).mul(brightness, brightness, brightness, 1.0f)
		light.range = range
	}

	override fun parse(xmlData: XmlData)
	{
		periodRange = Range.parse(xmlData.get("Period"))
		minBrightnessRange = Range.parse(xmlData.get("MinBrightness"))
		maxBrightnessRange = Range.parse(xmlData.get("MaxBrightness"))
		minRangeRange = Range.parse(xmlData.get("MinRange"))
		maxRangeRange = Range.parse(xmlData.get("MaxRange"))
	}

	override fun copy(): LightAnimation
	{
		val anim = PulseLightAnimation()
		anim.periodRange = periodRange
		anim.minBrightnessRange = minBrightnessRange
		anim.maxBrightnessRange = maxBrightnessRange
		anim.minRangeRange = minRangeRange
		anim.maxRangeRange = maxRangeRange

		return anim
	}

	companion object
	{
		fun create(period: Float, brightness: Float, range: Float, changePercent: Float, randomPercent: Float): PulseLightAnimation
		{
			val change = changePercent / 100f
			val random = randomPercent / 100f

			val anim = PulseLightAnimation()
			anim.periodRange = Range(period * (1f - random), period * (1f + random))

			val minBrightness = brightness * (1f - change)
			val maxBrightness = brightness * (1f + change)
			anim.minBrightnessRange = Range(minBrightness * (1f - random), minBrightness * (1f + random))
			anim.maxBrightnessRange = Range(maxBrightness * (1f - random), maxBrightness * (1f + random))

			anim.minRangeRange = Range(range, range)
			anim.maxRangeRange = Range(range, range)

			return anim
		}
	}
}