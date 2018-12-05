package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.Path
import com.badlogic.gdx.math.Vector2
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Random

class Enemy(val source: Tile) : Entity()
{
	val chosenOffset: Vector2

	val sprite = AssetManager.loadSprite("white")

	var currentPath: Path<Vector2>? = null
	var pathDist = 0f

	var moveSpeed = 5f

	init
	{
		chosenOffset = Vector2(Random.random(), Random.random())
	}
}