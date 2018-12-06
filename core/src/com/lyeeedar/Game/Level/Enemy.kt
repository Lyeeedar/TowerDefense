package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Path
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Util.Colour
import com.lyeeedar.Util.Random
import com.lyeeedar.Util.UnsmoothedPath
import com.lyeeedar.Util.valueAt
import ktx.collections.toGdxArray
import ktx.math.plus

class Enemy(val source: Spawner) : Entity()
{
	var queuedDam = 0

	val actualhp: Int
		get() = hp - queuedDam

	var hp = 10
	val maxHP = 10

	val chosenOffset: Vector2 = Vector2(Random.random() * 0.8f - 0.4f, Random.random() * 0.8f - 0.4f)

	var currentPath: Path<Vector2>? = null
	var pathDist = 0f
	var pathDuration = 0f

	var moveSpeed = 0.2f

	init
	{
		sprite.colour = Colour.RED
		sprite.baseScale[0] = 0.4f
		sprite.baseScale[1] = 0.4f
	}

	override fun update(delta: Float, map: Map)
	{
		if (hp <= 0)
		{
			this.tile.entities.removeValue(this, true)
			return
		}

		if (currentPath == null)
		{
			// update with a new path
			val source = source
			val sourceIndex = source.sourceIndex
			val dest = source.linkedDestination.tile

			val path = Array<Vector2>()
			path.add(tile.toVec())

			var current = tile
			while (current != dest)
			{
				path.add(current.toVec())

				current = current.nextTile[sourceIndex]
			}
			path.add(current.toVec())

			currentPath = UnsmoothedPath(path.toArray(Vector2::class.java))
			val animPath: kotlin.Array<Vector2> = path.map { Vector2(it) + chosenOffset }.toGdxArray().toArray(Vector2::class.java)
			for (point in animPath)
			{
				point.y = (map.grid.height - point.y - 1)
			}

			pathDuration = currentPath!!.approxLength(50) * moveSpeed

			sprite.animation = null
			sprite.animation = MoveAnimation.obtain().set(pathDuration, UnsmoothedPath(animPath))
			pathDist = 0f
		}

		pathDist += delta

		val a = MathUtils.clamp(pathDist / pathDuration, 0f, 1f)
		val pos = currentPath!!.valueAt(a)
		val tile = map.grid[pos.x.toInt(), pos.y.toInt()]

		if (tile.fillingEntity == source.linkedDestination)
		{
			this.tile.entities.removeValue(this, true)
			source.linkedDestination.sink(this)
		}
		else
		{
			if (tile != this.tile)
			{
				this.tile.entities.removeValue(this, true)
				this.tile = tile
				tile.entities.add(this)
			}
		}
	}
}