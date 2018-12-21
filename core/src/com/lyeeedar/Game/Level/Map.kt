package com.lyeeedar.Game.Level

import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.IntMap
import com.badlogic.gdx.utils.ObjectMap
import com.badlogic.gdx.utils.ObjectSet
import com.lyeeedar.Direction
import com.lyeeedar.Renderables.Sprite.SpriteWrapper
import com.lyeeedar.Util.*
import ktx.collections.set

class Map(val grid: Array2D<Tile>)
{
	val width: Int
		get() = grid.width

	val height: Int
		get() = grid.height

	val waves = Array<Wave>()
	var currentWave = -1

	val ambient = Colour()

	val paths = ObjectMap<Spawner, Array2D<PathfindNode?>>()

	init
	{
		updatePath()
	}

	val enemyList = ObjectSet<Enemy>()
	val towerList = Array<Tower>()
	val otherList = Array<Entity>()
	fun update(delta: Float)
	{
		var advanceWave = false
		if (currentWave == -1)
		{
			advanceWave = true
		}
		else
		{
			if (currentWave < waves.size)
			{
				waves[currentWave].remainingDuration -= delta
				if (waves[currentWave].remainingDuration <= -4)
				{
					advanceWave = true
				}
			}
		}

		if (advanceWave)
		{
			currentWave++
			if (currentWave < waves.size)
			{
				val wave = waves[currentWave]
				for (tile in grid)
				{
					val spawner = tile.fillingEntity as? Spawner ?: continue

					val spawnerWave = wave.spawners[spawner.character]
					spawner.currentWave = spawnerWave.copy()
				}
			}
		}

		var pathsDirty = false
		for (tile in grid)
		{
			for (entity in tile.entities)
			{
				if (entity is Enemy)
				{
					enemyList.add(entity)
				}
				else if (entity is Tower)
				{
					towerList.add(entity)
				}
				else
				{
					otherList.add(entity)
				}
			}

			tile.entities.clear()

			if (tile.fillingEntity != null)
			{
				val entity = tile.fillingEntity!!
				if (entity is Tower)
				{
					towerList.add(entity)
				}
				else
				{
					otherList.add(entity)
				}
			}

			if (tile.tileDirty)
			{
				pathsDirty = true
				tile.tileDirty = false
			}
		}

		if (pathsDirty)
		{
			updatePath()
		}

		for (entity in enemyList)
		{
			entity.update(delta, this)
		}
		for (entity in otherList)
		{
			entity.update(delta, this)
		}
		for (entity in towerList)
		{
			entity.update(delta, this)
		}

		enemyList.clear()
		otherList.clear()
		towerList.clear()
	}

	private fun updatePath()
	{
		val sources = Array<Spawner>()

		for (tile in grid)
		{
			if (tile.fillingEntity is Spawner)
			{
				sources.add(tile.fillingEntity as Spawner)
			}

			for (entity in tile.entities)
			{
				if (entity is Enemy)
				{
					entity.currentDest = null
				}
				else
				{
					throw Exception("Unhandled entity type! " + entity.javaClass.name)
				}
			}
		}

		paths.clear()
		for (source in sources)
		{
			val dest = source.linkedDestination.tile

			val tempCostGrid = Array2D<PathfindNode?>(grid.width, grid.height)
			val processQueue = Array<PathfindNode>(false, 32)

			tempCostGrid[dest] = PathfindNode(0, dest.x, dest.y)
			processQueue.add(tempCostGrid[dest])

			// compute all tiles
			while (processQueue.size > 0)
			{
				val current = processQueue.removeIndex(0)
				val nextCost = current.cost + 1
				current.inQueue = false

				for (offset in Direction.CardinalValues)
				{
					val next = current + offset

					if (grid.inBounds(next))
					{
						val nextTile = grid[next]
						var nextCost = nextCost
						if (!nextTile.isSolid && nextTile.fillingEntity == null)
						{
							if (grid.get(nextTile, 1).any { it.isSolid || nextTile.fillingEntity != null })
							{
								nextCost++
							}

							if (tempCostGrid[next] == null)
							{
								tempCostGrid[next] = PathfindNode(nextCost, next.x, next.y)
								processQueue.add(tempCostGrid[next])
							}
							else
							{
								val existing = tempCostGrid[next]!!
								if (nextCost < existing.cost)
								{
									existing.cost = nextCost

									if (!existing.inQueue)
									{
										processQueue.add(existing)
									}
								}
							}
						}
					}
				}
			}

			paths[source] = tempCostGrid
		}
	}

	companion object
	{
		fun load(path: String): Map
		{
			val xml = getXml(path)

			val charGrid: Array2D<Char>
			val gridEl = xml.getChildByName("Grid")!!
			val width = gridEl.getChild(0).text.length
			val height = gridEl.childCount
			charGrid = Array2D<Char>(width, height) { x, y -> gridEl.getChild(y).text[x] }

			val symbolsMap = IntMap<Symbol>()
			val symbolsEl = xml.getChildByName("Symbols")
			if (symbolsEl != null)
			{
				for (symbolEl in symbolsEl.children)
				{
					val character = symbolEl.get("Character")[0]
					val extends = symbolEl.get("Extends", " ")!!.firstOrNull() ?: ' '

					val usageCondition = symbolEl.get("UsageCondition", "1")!!.toLowerCase()
					val fallbackChar = symbolEl.get("FallbackCharacter", ".")!!.firstOrNull() ?: '.'

					val nameKey = symbolEl.get("NameKey", null)

					var sprite: SpriteWrapper? = null
					val symbolSpriteEl = symbolEl.getChildByName("Sprite")
					if (symbolSpriteEl != null)
					{
						sprite = SpriteWrapper.load(symbolSpriteEl)
					}

					symbolsMap[character.toInt()] = Symbol(
						character, extends,
						usageCondition, fallbackChar,
						nameKey,
						sprite)
				}
			}

			val spawnerDefs = IntMap<SpawnerDef>()
			val spawnersEl = xml.getChildByName("Spawners")
			if (spawnersEl != null)
			{
				for (spawnerEl in spawnersEl.children)
				{
					val character = spawnerEl.get("Character")[0]
					val destination = spawnerEl.get("Destination")[0]

					spawnerDefs[character.toInt()] = SpawnerDef(character, destination)
				}
			}

			val spawners = ObjectMap<Char, Array<Spawner>>()
			val sinkers = ObjectMap<Char, Sinker>()

			val theme = Theme.load(xml.get("Theme"))

			fun loadTile(tile: Tile, char: Char)
			{
				if (spawnerDefs.containsKey(char.toInt()))
				{
					val def = spawnerDefs[char.toInt()]

					val spawner = Spawner(char)
					tile.fillingEntity = spawner
					spawner.tile = tile

					if (!spawners.containsKey(def.destination))
					{
						spawners[def.destination] = Array()
					}

					spawners[def.destination].add(spawner)

					tile.isSolid = false
					tile.sprite = theme.path.copy()
				}
				else if (symbolsMap.containsKey(char.toInt()))
				{
					val symbol = symbolsMap[char.toInt()]
					if (symbol.extends != ' ')
					{
						loadTile(tile, symbol.extends)
					}
					else if (char != '.')
					{
						loadTile(tile, '.')
					}

					tile.isSolid = true

					if (symbol.sprite != null)
					{
						tile.sprite = symbol.sprite.copy()
					}
				}
				else if (char == '#')
				{
					tile.isSolid = true
					tile.sprite = theme.ground.copy()
				}
				else if (char == '.')
				{
					tile.isSolid = false
					tile.sprite = theme.path.copy()
				}
				else if (char == ',')
				{
					tile.isSolid = true
					tile.sprite = theme.pathborder.copy()
				}
				else if (char == '!')
				{
					tile.isSolid = true
					tile.sprite = theme.wall.copy()
				}
				else if (char == '@')
				{
					tile.isSolid = true
					tile.sprite = theme.pathborder.copy()

					val buildSite = BuildSite()
					buildSite.tile = tile
					tile.fillingEntity = buildSite
				}
				else if (char.isDigit())
				{
					tile.isSolid = false
					tile.sprite = theme.pathborder.copy()

					val sinker = Sinker()
					tile.fillingEntity = sinker
					sinker.tile = tile

					sinkers[char] = sinker
				}
				else
				{
					tile.sprite = theme.ground.copy()
				}
			}

			val grid = Array2D(charGrid.xSize, charGrid.ySize) { x, y -> Tile(x, y) }

			for (x in 0 until charGrid.xSize)
			{
				for (y in 0 until charGrid.ySize)
				{
					val tile = grid[x, y]
					val char = charGrid[x, y]

					loadTile(tile, char)
				}
			}

			for (spawnerPair in spawners)
			{
				val sinker = sinkers[spawnerPair.key]

				for (spawner in spawnerPair.value)
				{
					spawner.linkedDestination = sinker
				}
			}

			val map = Map(grid)

			val wavesEl = xml.getChildByName("Waves")!!
			for (waveEl in wavesEl.children)
			{
				val wave = Wave.load(waveEl)
				map.waves.add(wave)
			}

			map.ambient.set(AssetManager.loadColour(xml.getChildByName("Ambient")!!))

			return map
		}
	}
}

class SpawnerDef(val char: Char, val destination: Char)

data class Symbol(
	val char: Char, val extends: Char,
	val usageCondition: String, val fallbackChar: Char,
	val nameKey: String?,
	val sprite: SpriteWrapper?)

class PathfindNode(var cost: Int, x: Int, y: Int) : Point(x, y)
{
	var inQueue = true
}