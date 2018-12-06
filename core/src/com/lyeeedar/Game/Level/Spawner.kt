package com.lyeeedar.Game.Level

import com.lyeeedar.Util.Colour

class Spawner : Entity()
{
	var sourceIndex = 0

	lateinit var linkedDestination: Sinker

	init
	{
		sprite.colour = Colour.GREEN
	}

	var accumulator = 1f
	override fun update(delta: Float, map: Map)
	{
		accumulator += delta * 2f

		while (accumulator > 1f)
		{
			accumulator -= 1f

			val enemy = Enemy(this)
			enemy.tile = tile
			tile.entities.add(enemy)

			enemy.update(delta, map)
		}
	}
}

class Sinker : Entity()
{
	init
	{
		sprite.colour = Colour.BLUE
	}

	override fun update(delta: Float, map: Map)
	{

	}

	fun sink(enemy: Enemy)
	{

	}
}