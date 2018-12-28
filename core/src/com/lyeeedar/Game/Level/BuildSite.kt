package com.lyeeedar.Game.Level

import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour

class BuildSite : Entity()
{
	init
	{
		sprite = AssetManager.loadSprite("Oryx/Custom/terrain/crystalcluster")
		sprite.drawActualSize = true
		sprite.baseScale[0] = 0.7f
		sprite.baseScale[1] = 0.7f

		val lightCol = Colour(107, 18, 136, 255)
		//sprite.light = Light(lightCol, 3f, 2f)

		//sprite.light!!.anim = PulseLightAnimation.create(2f, 2f, 2f, 20f, 10f)
	}

	override fun update(delta: Float, map: Map)
	{

	}

}