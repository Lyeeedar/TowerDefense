package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Util.*
import ktx.collections.toGdxArray
import ktx.math.minus

class Tower : Entity()
{
	var shotDam = 1
	var multishot = 1
	var shotrate = 3f

	var range = 4

	var shotAccumulator = 0f

	val targets: Array<Enemy?> = arrayOfNulls<Enemy?>(multishot)

	init
	{
		sprite.colour = Colour.PINK
	}

	override fun update(delta: Float, map: Map)
	{
		shotAccumulator += delta * shotrate

		if (shotAccumulator > 1f)
		{
			// get all enemies in range
			val tilesInRange = map.grid.filter { it.dist(tile) <= range }
			val enemiesInRange = tilesInRange.flatMap { it.entities.asSequence() }.mapNotNull { it as? Enemy }.filter { it.actualhp > 0 && it.currentPath != null }.asGdxArray()

			while (shotAccumulator > 1f)
			{
				shotAccumulator -= 1f

				for (i in 0 until multishot)
				{
					if (enemiesInRange.size == 0)
					{
						continue
					}

					val enemy: Enemy
					if (targets[i] != null && enemiesInRange.contains(targets[i]))
					{
						enemy = targets[i]!!
						enemiesInRange.removeValue(enemy, true)
					}
					else
					{
						enemy = enemiesInRange.filter { !targets.contains(it) }.toGdxArray().random()
						targets[i] = enemy
						enemiesInRange.removeValue(enemy, true)
					}

					val flightTime = 0.2f + enemy.tile.euclideanDist(tile) * 0.025f

					val a = MathUtils.clamp((enemy.pathDist + flightTime) / enemy.pathDuration, 0f, 1f)
					val targetPos = enemy.currentPath!!.valueAt(a)

					val path = arrayOf(Vector2(), targetPos - tile.toVec())
					path[1].y *= -1

					val sprite = AssetManager.loadSprite("white")
					sprite.baseScale[0] = 0.2f
					sprite.baseScale[1] = 0.2f
					sprite.colour = Colour.BLACK

					sprite.animation = MoveAnimation.obtain().set(flightTime, UnsmoothedPath(path))

					tile.effects.add(sprite)

					enemy.queuedDam += shotDam

					Future.call({
						enemy.queuedDam -= shotDam
						enemy.hp -= shotDam
								}, flightTime)
				}
			}
		}
	}
}