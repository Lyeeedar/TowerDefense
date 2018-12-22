package com.lyeeedar.Game.Level

import com.lyeeedar.Renderables.Light
import com.lyeeedar.Renderables.PulseLightAnimation
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour
import com.lyeeedar.Util.Range

class BuildSite : Entity()
{
	init
	{
		sprite = AssetManager.loadSprite("Oryx/Custom/terrain/crystal1")
		sprite.drawActualSize = true
		sprite.baseScale[0] = 0.8f
		sprite.baseScale[1] = 0.8f

		val lightCol = Colour(107, 18, 136, 255)
		sprite.light = Light(lightCol, 3f, 2f)
		sprite.light!!.baseColour.set(lightCol)

		val anim = PulseLightAnimation()
		anim.periodRange = Range(1f, 2f)
		anim.minBrightnessRange = Range(1.5f, 2f)
		anim.maxBrightnessRange = Range(2.5f, 3f)
		anim.minRangeRange = Range(2f, 2f)
		anim.maxRangeRange = Range(2f, 2f)

		sprite.light!!.anim = anim
	}

	override fun update(delta: Float, map: Map)
	{

	}

}