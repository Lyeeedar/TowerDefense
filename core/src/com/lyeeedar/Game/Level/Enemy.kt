package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Path
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Renderables.Renderable
import com.lyeeedar.Util.AssetManager
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

	var hp = 20
	val maxHP = 20

	val chosenOffset: Vector2 = Vector2(Random.random() * 0.8f - 0.4f, Random.random() * 0.8f - 0.4f)

	var currentAnim: MoveAnimation? = null
	var currentPath: Path<Vector2>? = null
	var pathDist = 0f
	var pathDuration = 0f

	var pos: Vector2 = Vector2()

	var moveSpeed = 0.3f

	var effects = Array<Renderable>()

	init
	{
		sprite = AssetManager.loadSprite("Oryx/uf_split/uf_heroes/goblin")
		sprite.drawActualSize = true
		//sprite.baseScale[0] = 0.8f
		//sprite.baseScale[1] = 0.8f
	}

	override fun update(delta: Float, map: Map)
	{
		if (hp <= 0)
		{
			val effect = AssetManager.loadParticleEffect("Death")
			this.tile.effects.add(effect)

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
			currentAnim = MoveAnimation.obtain().set(pathDuration, UnsmoothedPath(animPath))

			sprite.animation = null
			sprite.animation = currentAnim
			pathDist = 0f
		}

		pathDist += delta

		val a = MathUtils.clamp(currentAnim!!.time() / currentAnim!!.duration(), 0f, 1f)
		val alpha = currentAnim!!.eqn!!.apply(a)
		pos = currentPath!!.valueAt(alpha)
		val tile = map.grid[pos.x.toInt(), pos.y.toInt()]

		if (tile.fillingEntity == source.linkedDestination)
		{
			source.linkedDestination.sink(this)
		}
		else
		{
			this.tile = tile
			tile.entities.add(this)
		}
	}
}