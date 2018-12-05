package com.lyeeedar.Game.Level

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Direction
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Util.Array2D
import com.lyeeedar.Util.Point
import com.lyeeedar.Util.UnsmoothedPath
import com.lyeeedar.Util.valueAt
import ktx.math.plus

class Map
{
	val grid: Array2D<Tile>

	init
	{
		grid = Array2D(20, 20) { x, y -> Tile(x, y) }
		updatePath()
	}

	val entityList = Array<Entity>()
	fun update(delta: Float)
	{
		for (tile in grid)
		{
			entityList.addAll(tile.entities)
			tile.entities.clear()
		}

		for (entity in entityList)
		{
			if (entity is Enemy)
			{
				if (entity.currentPath == null)
				{
					// update with a new path
					val source = entity.source
					val sourceIndex = source.sourceIndex
					val dest = source.linkedDestination

					val path = Array<Vector2>()
					path.add(source.toVec() + entity.chosenOffset)

					var current = entity.tile!!
					while (current != dest)
					{
						path.add(current.toVec() + entity.chosenOffset)

						current = current.nextTile[sourceIndex]
					}

					entity.currentPath = UnsmoothedPath(path.toArray())

					entity.sprite.animation = null
					entity.sprite.animation = MoveAnimation.obtain().set(entity.moveSpeed * entity.currentPath!!.approxLength(50), entity.currentPath!!)
					entity.pathDist = 0f
				}

				entity.pathDist += delta

				val pos = entity.currentPath!!.valueAt(entity.pathDist)
				val tile = grid[pos.x.toInt(), pos.y.toInt()]

				
				tile.entities.add(entity)
			}
			else
			{
				throw Exception("Unhandled entity type! " + entity.javaClass.name)
			}
		}
	}

	fun updatePath()
	{
		val sources = Array<Tile>()

		for (tile in grid)
		{
			tile.nextTile.clear()

			if (tile.isSource)
			{
				sources.add(tile)
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
			val dest = source.linkedDestination!!

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
					val nextTile = grid[next]
					if (!nextTile.isSolid)
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

			// Process grid to find path
			for (tile in grid)
			{
				val node = tempCostGrid[tile]

				if (node != null)
				{
					val next = Direction.CardinalValues.mapNotNull { tempCostGrid[node + it] }.minBy { it.cost }!!
					tile.nextTile.add(grid[next])
				}
				else
				{
					tile.nextTile.add(tile)
				}
			}
		}
	}
}

class PathfindNode(var cost: Int, x: Int, y: Int) : Point(x, y)
{
	var processed = false
	var inQueue = true
}

class Tile(x: Int, y: Int) : Point(x, y)
{
	val entities = Array<Entity>()

	val nextTile = Array<Tile>()

	var isSource = false
	var isSolid = false

	var sourceIndex = 0

	var linkedDestination: Tile? = null
}