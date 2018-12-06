package com.lyeeedar.Game.Level

import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectSet
import com.lyeeedar.Direction
import com.lyeeedar.Renderables.Renderable
import com.lyeeedar.Renderables.Sprite.SpriteWrapper
import com.lyeeedar.Util.*

class Map
{
	val grid: Array2D<Tile>

	init
	{
		grid = Array2D(20, 20) { x, y -> Tile(x, y) }

		val sinker = Sinker()
		val spawner = Spawner()
		spawner.linkedDestination = sinker

		spawner.tile = grid.random()!!
		spawner.tile.fillingEntity = spawner

		sinker.tile = grid.random()!!
		sinker.tile.fillingEntity = sinker

		updatePath()
	}

	val width: Int
		get() = grid.width

	val height: Int
		get() = grid.height

	val entityList = ObjectSet<Entity>()
	fun update(delta: Float)
	{
		var pathsDirty = false
		for (tile in grid)
		{
			entityList.addAll(tile.entities)

			if (tile.fillingEntity != null)
			{
				entityList.add(tile.fillingEntity!!)
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

		for (entity in entityList)
		{
			entity.update(delta, this)
		}
	}

	private fun updatePath()
	{
		val sources = Array<Spawner>()

		for (tile in grid)
		{
			tile.nextTile.clear()

			if (tile.fillingEntity is Spawner)
			{
				sources.add(tile.fillingEntity as Spawner)
			}

			for (entity in tile.entities)
			{
				if (entity is Enemy)
				{
					entity.currentPath = null
				}
				else
				{
					throw Exception("Unhandled entity type! " + entity.javaClass.name)
				}
			}
		}

		var index = 0
		for (source in sources)
		{
			source.sourceIndex = index++
			val dest = source.linkedDestination.tile!!

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
						if (!nextTile.isSolid && nextTile.fillingEntity == null)
						{
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

			// Process grid to find path
			for (tile in grid)
			{
				val next = Direction.CardinalValues.filter { tempCostGrid.inBounds(tile + it) }.mapNotNull { tempCostGrid[tile + it] }.minBy { it.cost } ?: tile
				tile.nextTile.add(grid[next])
			}
		}
	}
}

class PathfindNode(var cost: Int, x: Int, y: Int) : Point(x, y)
{
	var inQueue = true
}

class Tile(x: Int, y: Int) : Point(x, y)
{
	val entities = Array<Entity>()

	val nextTile = Array<Tile>()

	var fillingEntity: Entity? = null
		set(value)
		{
			field = value

			tileDirty = true
		}

	var isSolid = false
		set(value)
		{
			field = value

			if (field)
			{
				groundSprite!!.sprite!!.colour = Colour.DARK_GRAY
			}
			else
			{
				groundSprite!!.sprite!!.colour = Colour.LIGHT_GRAY
			}

			tileDirty = true
		}

	val effects = Array<Renderable>()

	var groundSprite: SpriteWrapper? = null
	var wallSprite: SpriteWrapper? = null

	var tileDirty = false

	init
	{
		groundSprite = SpriteWrapper()
		groundSprite!!.sprite = AssetManager.loadSprite("white", colour = Colour.LIGHT_GRAY)
	}
}