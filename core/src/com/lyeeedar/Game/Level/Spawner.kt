package com.lyeeedar.Game.Level

import com.lyeeedar.Util.Colour

class Spawner(val character: Char) : Entity()
{
	var sourceIndex = 0

	var currentWave: SpawnerWave? = null

	lateinit var linkedDestination: Sinker

	init
	{
		sprite.colour = Colour.GREEN
	}

	override fun update(delta: Float, map: Map)
	{
		if (currentWave != null)
		{
			currentWave!!.remainingDuration -= delta
			if (currentWave!!.remainingDuration < 0f)
			{
				currentWave = null
				return
			}

			for (waveEnemy in currentWave!!.enemies)
			{
				waveEnemy.enemyAccumulator += delta * waveEnemy.enemiesASecond

				while (waveEnemy.enemyAccumulator >= 1)
				{
					waveEnemy.enemyAccumulator -= 1

					val enemy = Enemy(this, waveEnemy.enemyDef)
					enemy.tile = tile
					tile.entities.add(enemy)

					enemy.update(delta, map)
				}
			}
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