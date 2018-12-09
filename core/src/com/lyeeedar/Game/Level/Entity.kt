package com.lyeeedar.Game.Level

import com.lyeeedar.Util.AssetManager

abstract class Entity
{
	lateinit var tile: Tile

	var sprite = AssetManager.loadSprite("white")

	abstract fun update(delta: Float, map: Map)
}