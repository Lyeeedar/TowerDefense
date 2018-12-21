package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Direction
import com.lyeeedar.Renderables.Animation.ExpandAnimation
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Renderables.Renderable
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Util.*
import ktx.math.minus
import ktx.math.plus

class Enemy(val source: Spawner, val def: EnemyDef) : Entity()
{
	var queuedDam = 0

	val actualhp: Int
		get() = hp - queuedDam

	var hp = def.health

	val chosenOffset: Vector2 = Vector2(Random.randomWeighted() * Random.sign() * 0.4f, Random.randomWeighted() * Random.sign() * 0.4f)

	var pos: Vector2 = Vector2()
	var currentDest: Tile? = null

	var effects = Array<Renderable>()

	init
	{
		sprite = def.sprite.copy()
		sprite.baseScale[0] = 0.5f
		sprite.baseScale[1] = 0.5f
	}

	override fun update(delta: Float, map: Map)
	{
		if (hp <= 0)
		{
			val effect = def.death.copy()
			this.tile.effects.add(effect)

			return
		}

		val futurepos = getFuturePos(delta, map, this)
		pos = futurepos.pos
		currentDest = futurepos.destTile

		val tile = map.grid.getClamped(pos.x.toInt(), pos.y.toInt())

		if (tile.fillingEntity == source.linkedDestination)
		{
			source.linkedDestination.sink(this)

			val sprite = sprite.copy()
			sprite.animation = null
			sprite.animation = ExpandAnimation.obtain().set(0.5f, 1f, 0f)
			sprite.animation = MoveAnimation.obtain().set(0.5f, source.linkedDestination.tile.getPosDiff(pos.x, pos.y))

			source.linkedDestination.tile.effects.add(sprite)
		}
		else
		{
			this.tile = tile
			tile.entities.add(this)
		}
	}

	companion object
	{
		class FuturePos(val pos: Vector2, val destTile: Tile?)
		fun getFuturePos(delta: Float, map: Map, enemy: Enemy): FuturePos
		{
			var pos = enemy.pos.cpy()
			var currentDest = enemy.currentDest
			var tile = enemy.tile

			var moveDist = enemy.def.tilesASecond * delta
			val pathgrid = map.paths[enemy.source]

			while (moveDist > 0f)
			{
				if (currentDest == null)
				{
					val surroundingTiles = Direction.CardinalValuesAndCenter.mapNotNull { map.grid.tryGet(tile.x, tile.y, it, null) }.filter { pathgrid[it] != null }
					var minValue = Int.MAX_VALUE
					for (tile in surroundingTiles)
					{
						if (pathgrid[tile]!!.cost < minValue)
						{
							minValue = pathgrid[tile]!!.cost
						}
					}

					val possibleTiles = Array<Tile>()
					for (tile in surroundingTiles)
					{
						if (pathgrid[tile]!!.cost == minValue)
						{
							possibleTiles.add(tile)
						}
					}

					currentDest = possibleTiles.random()
				}

				if (currentDest == null)
				{
					break
				}

				val targetPos = currentDest.toVec() + enemy.chosenOffset
				val diff = targetPos.cpy() - pos
				val len = diff.len()

				val move = min(moveDist, len)
				moveDist -= move

				val alpha = move / len
				pos.lerp(targetPos, alpha)

				if (moveDist > 0f)
				{
					tile = currentDest
					currentDest = null
				}
			}

			return FuturePos(pos, currentDest)
		}
	}
}

class EnemyDef
{
	lateinit var name: String
	lateinit var description: String
	lateinit var sprite: Sprite
	lateinit var death: ParticleEffect
	var tilesASecond: Float = 0f
	var health: Int = 0

	companion object
	{
		fun load(path: String): EnemyDef
		{
			val xml = getXml("Enemies/$path")
			return load(xml)
		}

		fun load(xmlData: XmlData): EnemyDef
		{
			val def = EnemyDef()

			def.name = xmlData.get("Name")
			def.description = xmlData.get("Description")
			def.sprite = AssetManager.loadSprite(xmlData.getChildByName("Sprite")!!)
			def.death = AssetManager.loadParticleEffect(xmlData.getChildByName("Death")!!)
			def.tilesASecond = xmlData.getFloat("TilesASecond")
			def.health = xmlData.getInt("Health")

			return def
		}
	}
}