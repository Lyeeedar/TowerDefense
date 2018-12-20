package com.lyeeedar.Game.Level

import com.lyeeedar.Renderables.Light
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour

class BuildSite : Entity()
{
	init
	{
		sprite = AssetManager.loadSprite("Oryx/Custom/terrain/crystal1")
		sprite.drawActualSize = true

		sprite.light = Light(Colour(107, 18, 136, 255), 1f, 4f)
	}

	override fun update(delta: Float, map: Map)
	{

	}

}