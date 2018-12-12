package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ObjectMap
import com.lyeeedar.Renderables.Animation.AbstractAnimationDefinition
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Util.*
import ktx.collections.set
import ktx.collections.toGdxArray
import ktx.math.minus

class Tower(val def: TowerDefinition) : Entity()
{
	var shotAccumulator = 0f

	var selected = false

	init
	{
		for (effect in def.effects)
		{
			if (effect is ShotEffectType)
			{
				effect.targetCache = TargetCache(effect.count)
			}
		}

		sprite = def.sprite.copy()
	}

	override fun update(delta: Float, map: Map)
	{
		shotAccumulator += delta * def.attacksPerSecond

		if (shotAccumulator > 1f)
		{
			while (shotAccumulator > 1f)
			{
				shotAccumulator -= 1f

				for (effect in def.effects)
				{
					effect.apply(this, map)
				}
			}
		}
	}
}

class TowerUpgrade(val cost: Int, val guid: String)
{
	lateinit var towerDef: TowerDefinition
}

class TowerUpgradeTree
{
	lateinit var root: String
	val towerDefMap = ObjectMap<String, TowerDefinition>()

	companion object
	{
		fun load(path: String): TowerUpgradeTree
		{
			val xml = getXml("Towers/$path")
			return load(xml)
		}

		fun load(xmlData: XmlData): TowerUpgradeTree
		{
			val tree = TowerUpgradeTree()

			val defsEl = xmlData.getChildByName("TowerDefs")!!
			for (el in defsEl.children)
			{
				val guid = el.getAttribute("GUID")
				val def = TowerDefinition.load(el, tree)

				tree.towerDefMap[guid] = def
			}

			for (def in tree.towerDefMap.values())
			{
				def.resolve()
			}

			tree.root = xmlData.get("Root")

			return tree
		}
	}
}

class TowerDefinition(val xmlData: XmlData, val upgradeTree: TowerUpgradeTree)
{
	lateinit var name: String
	lateinit var icon: Sprite
	lateinit var sprite: Sprite

	var attacksPerSecond: Float = 0f
	val effects = com.badlogic.gdx.utils.Array<AbstractEffectType>()

	val upgrades = com.badlogic.gdx.utils.Array<TowerUpgrade>()

	fun copy(): TowerDefinition
	{
		val def = load(xmlData, upgradeTree)
		def.resolve()
		return def
	}

	fun resolve()
	{
		for (upgrade in upgrades)
		{
			upgrade.towerDef = upgradeTree.towerDefMap[upgrade.guid]
		}
	}

	companion object
	{
		fun load(xmlData: XmlData, upgradeTree: TowerUpgradeTree): TowerDefinition
		{
			val def = TowerDefinition(xmlData, upgradeTree)

			def.name = xmlData.get("Name")
			def.icon = AssetManager.loadSprite(xmlData.getChildByName("Icon")!!)
			def.sprite = AssetManager.loadSprite(xmlData.getChildByName("Sprite")!!)
			def.attacksPerSecond = xmlData.getFloat("AttacksPerSecond")

			val effectsEl = xmlData.getChildByName("Effects")!!
			for (el in effectsEl.children)
			{
				def.effects.add(AbstractEffectType.load(el))
			}

			val upgradesEl = xmlData.getChildByName("Upgrades")
			if (upgradesEl != null)
			{
				for (el in upgradesEl.children)
				{
					val cost = el.getInt("Cost")
					val guid = el.get("Upgrade")

					def.upgrades.add(TowerUpgrade(cost, guid))
				}
			}

			return def
		}
	}
}

abstract class AbstractEffectType
{
	abstract fun apply(entity: Entity, map: Map)
	abstract fun parse(xmlData: XmlData)

	companion object
	{
		fun load(xmlData: XmlData): AbstractEffectType
		{
			val effect = when(xmlData.getAttribute("meta:RefKey").toUpperCase())
			{
				"DAMAGEEFFECT" -> DamageEffectType()
				"SHOTEFFECT" -> ShotEffectType();
				else -> throw Exception("Unknown effect type '" + xmlData.getAttribute("meta:RefKey") + "'!")
			}

			effect.parse(xmlData)

			return effect
		}
	}
}

class DamageEffectType : AbstractEffectType()
{
	var damage: Int = 0
	lateinit var effect: ParticleEffect

	override fun apply(entity: Entity, map: Map)
	{
		if (entity is Enemy)
		{
			entity.hp -= damage
			entity.effects.add(effect.copy())
		}
	}

	override fun parse(xmlData: XmlData)
	{
		damage = xmlData.getInt("Damage")
		effect = AssetManager.loadParticleEffect(xmlData.getChildByName("Effect")!!)
	}
}

class TargetCache(targetCount: Int)
{
	val targets: Array<Enemy?> = arrayOfNulls<Enemy?>(targetCount)
}

class ShotEffectType : AbstractEffectType()
{
	var range: Float = 0f
	var count: Int = 0
	lateinit var flightEffect: ParticleEffect
	lateinit var animDef: AbstractAnimationDefinition

	var effects = com.badlogic.gdx.utils.Array<AbstractEffectType>()

	var targetCache: TargetCache? = null

	override fun apply(entity: Entity, map: Map)
	{
		val allenemies = map.grid.flatMap { it.entities.asSequence() }.mapNotNull { it as? Enemy }.asGdxArray()
		val enemiesInRange = allenemies.filter { it != entity && it.actualhp > 0 && it.pos.dst2(entity.tile.toVec()) <= range*range }.asGdxArray()

		for (i in 0 until count)
		{
			if (enemiesInRange.size == 0)
			{
				if (targetCache != null)
				{
					targetCache!!.targets[i] = null
				}

				continue
			}

			val enemy: Enemy
			if (targetCache != null)
			{
				if (targetCache!!.targets[i] != null && enemiesInRange.contains(targetCache!!.targets[i]))
				{
					enemy = targetCache!!.targets[i]!!
					enemiesInRange.removeValue(enemy, true)
				}
				else
				{
					enemy = enemiesInRange.filter { !targetCache!!.targets.contains(it) }.toGdxArray().random()
					targetCache!!.targets[i] = enemy
					enemiesInRange.removeValue(enemy, true)
				}
			}
			else
			{
				enemy = enemiesInRange.random()
				enemiesInRange.removeValue(enemy, true)
			}

			val flightTime = 0.2f + enemy.tile.euclideanDist(entity.tile) * 0.025f

			val a = MathUtils.clamp((enemy.pathDist + flightTime) / enemy.pathDuration, 0f, 1f)
			val targetPos = enemy.currentPath!!.valueAt(a)

			val path = arrayOf(Vector2(), targetPos - entity.tile.toVec())
			path[1].y *= -1

			val effect = flightEffect.copy()
			effect.faceInMoveDirection = true
			effect.animation = animDef.getAnimation(flightTime, path)

			entity.tile.effects.add(effect)

			Future.call({
							for (effect in effects)
							{
								effect.apply(enemy, map)
							}
						}, flightTime * animDef.speedMultiplier)
		}
	}

	override fun parse(xmlData: XmlData)
	{
		range = xmlData.getFloat("Range")
		count = xmlData.getInt("Count")
		flightEffect = AssetManager.loadParticleEffect(xmlData.getChildByName("FlightEffect")!!)
		animDef = AbstractAnimationDefinition.load(xmlData.getChildByName("Animation")!!)

		val effectsEl = xmlData.getChildByName("Effects")!!
		for (el in effectsEl.children)
		{
			effects.add(load(el))
		}
	}

}