package com.lyeeedar.Game.Level

import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectMap
import com.lyeeedar.Util.XmlData
import ktx.collections.set

class Wave
{
	var duration: Float = 0f
	var remainingDuration: Float = 0f

	val spawners = ObjectMap<Char, SpawnerWave>()

	companion object
	{
		fun load(xmlData: XmlData): Wave
		{
			val wave = Wave()
			wave.duration = xmlData.getFloat("Duration")
			wave.remainingDuration = wave.duration

			val spawnersEl = xmlData.getChildByName("Spawners")!!
			for (spawnerEl in spawnersEl.children)
			{
				val spawner = SpawnerWave.load(spawnerEl, wave.duration)
				wave.spawners[spawner.character] = spawner
			}

			return wave
		}
	}
}

class SpawnerWave
{
	var character: Char = ' '
	var duration: Float = 0f

	var remainingDuration: Float = 0f

	val enemies = Array<WaveEnemy>()

	fun copy(): SpawnerWave
	{
		val new = SpawnerWave()
		new.duration = duration
		new.remainingDuration = duration

		for (enemy in enemies)
		{
			new.enemies.add(enemy.copy())
		}

		return new
	}

	companion object
	{
		fun load(xmlData: XmlData, duration: Float): SpawnerWave
		{
			val spawnerWave = SpawnerWave()

			spawnerWave.character = xmlData.get("Character")[0]
			spawnerWave.duration = duration
			spawnerWave.remainingDuration = duration

			val enemiesEl = xmlData.getChildByName("Enemies")!!
			for (enemyEl in enemiesEl.children)
			{
				spawnerWave.enemies.add(WaveEnemy.load(enemyEl, duration))
			}

			return spawnerWave
		}
	}
}

class WaveEnemy
{
	lateinit var enemyDef: EnemyDef
	var count: Int = 0
	var enemiesASecond: Float = 0f

	var enemyAccumulator: Float = 0f

	fun copy(): WaveEnemy
	{
		val new = WaveEnemy()
		new.enemyDef = enemyDef
		new.count = count
		new.enemiesASecond = enemiesASecond

		return new
	}

	companion object
	{
		fun load(xmlData: XmlData, duration: Float): WaveEnemy
		{
			val enemy = WaveEnemy()

			enemy.count = xmlData.getInt("Count")
			enemy.enemyDef = EnemyDef.Companion.load(xmlData.get("Enemy"))
			enemy.enemiesASecond = enemy.count / duration

			return enemy
		}
	}
}