package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Path
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Renderables.Renderable
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Util.*
import ktx.collections.toGdxArray
import ktx.math.plus

class Enemy(val source: Spawner, val def: EnemyDef) : Entity()
{
	var queuedDam = 0

	val actualhp: Int
		get() = hp - queuedDam

	var hp = def.health

	val chosenOffset: Vector2 = Vector2(Random.random() * 0.8f - 0.4f, Random.random() * 0.8f - 0.4f)

	var currentAnim: MoveAnimation? = null
	var currentPath: Path<Vector2>? = null
	var pathDist = 0f
	var pathDuration = 0f

	var pos: Vector2 = Vector2()

	var effects = Array<Renderable>()

	init
	{
		sprite = def.sprite.copy()
	}

	override fun update(delta: Float, map: Map)
	{
		if (hp <= 0)
		{
			val effect = def.death.copy()
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

			pathDuration = currentPath!!.approxLength(50) * def.tilesASecond
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