package com.lyeeedar.Game.Level

import com.lyeeedar.Renderables.Animation.ExpandAnimation
import com.lyeeedar.Renderables.Light
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour
import ktx.math.plus

class Spawner(val character: Char) : Entity()
{
	var currentWave: SpawnerWave? = null

	lateinit var linkedDestination: Sinker

	init
	{
		sprite = AssetManager.loadSprite("Oryx/Custom/terrain/portal_blue")
		sprite.baseScale[0] = 2f
		sprite.size[1] = 2

		sprite.light = Light(Colour(0.5f, 0.7f, 1.0f, 1.0f), 1f, 3f)
		//sprite.light!!.anim = PulseLightAnimation.create(3f, 1f, 3f, 20f, 10f)
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
					enemy.pos = tile.toVec() + enemy.chosenOffset

					val spawnEffect = AssetManager.loadParticleEffect("Heal")
					spawnEffect.colour = Colour(0.2f, 0.5f, 1f, 1f)
					enemy.effects.add(spawnEffect)

					enemy.update(delta, map)

					enemy.sprite.animation = ExpandAnimation.obtain().set(0.1f, 0f, 1f)
				}
			}
		}
	}
}

class Sinker : Entity()
{
	init
	{
		sprite = AssetManager.loadSprite("Oryx/Custom/terrain/portal_red")
		sprite.baseScale[0] = 2f
		sprite.size[1] = 2

		//sprite.light = Light(Colour(1.0f, 0.7f, 0.3f, 1.0f), 1f, 3f)
	//	sprite.light!!.anim = PulseLightAnimation.create(3f, 1f, 3f, 20f, 10f)
	}

	override fun update(delta: Float, map: Map)
	{

	}

	fun sink(enemy: Enemy)
	{

	}
}