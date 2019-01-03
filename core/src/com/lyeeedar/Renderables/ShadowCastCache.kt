package com.lyeeedar.Renderables

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ObjectSet
import com.lyeeedar.Direction
import com.lyeeedar.Global.Companion.collisionGrid
import com.lyeeedar.Util.Point
import com.lyeeedar.Util.max
import com.lyeeedar.Util.min
import ktx.collections.isNotEmpty
import squidpony.squidgrid.FOV
import squidpony.squidgrid.Radius

class ShadowCastCache @JvmOverloads constructor(val fovType: Int = FOV.SHADOW)
{
	fun copy(): ShadowCastCache
	{
		val cache = ShadowCastCache(fovType)
		cache.lastrange = lastrange
		cache.lastx = lastx
		cache.lasty = lasty

		for (p in opaqueTiles)
		{
			cache.opaqueTiles.add(p.copy())
		}

		for (p in currentShadowCast)
		{
			cache.currentShadowCast.add(p.copy())
		}

		return cache
	}

	private val fov: FOV = FOV(fovType)

	var lastrange: Int = -Int.MAX_VALUE
		private set
	var lastx: Int = -Int.MAX_VALUE
		private set
	var lasty: Int = -Int.MAX_VALUE
		private set
	val opaqueTiles = com.badlogic.gdx.utils.Array<Point>()
	val clearTiles = com.badlogic.gdx.utils.Array<Point>()
	val currentShadowCast = com.badlogic.gdx.utils.Array<Point>()
	val invCurrentShadowCast = com.badlogic.gdx.utils.Array<Point>()
	val opaqueRegions = com.badlogic.gdx.utils.Array<Rectangle>()

	fun anyOpaque() = opaqueTiles.size > 0
	fun anyClear() = clearTiles.size > 0

	fun updateOpaqueRegions()
	{
		opaqueRegions.clear()

		val tileSet = ObjectSet<Point>()
		for (tile in opaqueTiles)
		{
			tileSet.add(tile)
		}

		while (tileSet.isNotEmpty())
		{
			val sourceTile = tileSet.asSequence().first()
			tileSet.remove(sourceTile)

			var chosenDir: Direction = Direction.CENTER
			for (dir in Direction.CardinalValues)
			{
				val newPoint = sourceTile + dir
				if (tileSet.contains(newPoint))
				{
					chosenDir = dir
					break
				}
			}

			if (chosenDir != Direction.CENTER)
			{
				var end1 = sourceTile
				while (true)
				{
					val newPoint = end1 + chosenDir
					if (!tileSet.contains(newPoint))
					{
						break
					}

					tileSet.remove(newPoint)
					end1 = newPoint
				}

				var end2 = sourceTile
				while (true)
				{
					val newPoint = end2 - chosenDir
					if (!tileSet.contains(newPoint))
					{
						break
					}

					tileSet.remove(newPoint)
					end2 = newPoint
				}

				val minx = min(end1.x, end2.x)
				val miny = min(end1.y, end2.y)
				val maxx = max(end1.x, end2.x)
				val maxy = max(end1.y, end2.y)

				opaqueRegions.add(Rectangle(minx.toFloat(), miny.toFloat(), (maxx - minx).toFloat() + 1f, (maxy - miny).toFloat() + 1f))
			}
			else
			{
				opaqueRegions.add(Rectangle(sourceTile.x.toFloat(), sourceTile.y.toFloat(), 1f, 1f))
			}
		}
	}

	fun getShadowCast(x: Int, y: Int, range: Int): com.badlogic.gdx.utils.Array<Point>
	{
		val collisionGrid = collisionGrid
		if (collisionGrid == null)
		{
			var recalculate = false

			if (x != lastx || y != lasty)
			{
				recalculate = true
			}
			else if (range != lastrange)
			{
				recalculate = true
			}

			if (recalculate)
			{
				Point.freeAllTS(currentShadowCast)
				currentShadowCast.clear()

				Point.freeAllTS(invCurrentShadowCast)
				invCurrentShadowCast.clear()

				for (ix in 0 until range * 2 + 1)
				{
					for (iy in 0 until range * 2 + 1)
					{
						val gx = ix + x - range
						val gy = iy + y - range

						val point = Point.obtainTS().set(gx, gy)
						currentShadowCast.add(point)
					}
				}

				lastx = x
				lasty = y
				lastrange = range

				updateOpaqueRegions()
			}

			return currentShadowCast
		}


		var recalculate = false

		if (x != lastx || y != lasty)
		{
			recalculate = true
		}
		else if (range != lastrange)
		{
			recalculate = true
		}
		else
		{
			for (pos in opaqueTiles)
			{
				if (!collisionGrid.tryGet(pos.x, pos.y, true)!!)
				{
					recalculate = true // something has moved
					break
				}
			}

			if (!recalculate)
			{
				for (pos in clearTiles)
				{
					if (collisionGrid.tryGet(pos.x, pos.y, true)!!)
					{
						recalculate = true // something has moved
						break
					}
				}
			}
		}

		if (recalculate)
		{
			Point.freeAllTS(currentShadowCast)
			currentShadowCast.clear()

			Point.freeAllTS(invCurrentShadowCast)
			invCurrentShadowCast.clear()

			// build grid
			var anySolid = false
			val resistanceGrid = Array(range * 2 + 1) { DoubleArray(range * 2 + 1) }
			for (ix in 0 until range * 2 + 1)
			{
				for (iy in 0 until range * 2 + 1)
				{
					val gx = ix + x - range
					val gy = iy + y - range

					if (collisionGrid.inBounds(gx, gy))
					{
						resistanceGrid[ix][iy] = (if (collisionGrid[gx, gy]) 1 else 0).toDouble()
					}
					else
					{
						resistanceGrid[ix][iy] = 1.0
					}

					anySolid = anySolid || resistanceGrid[ix][iy] == 1.0
				}
			}

			var rawOutput: Array<DoubleArray>? = null
			if (anySolid)
			{
				rawOutput = fov.calculateFOV(resistanceGrid, range, range, range.toDouble() + 1, Radius.SQUARE)
			}

			for (ix in 0 until range * 2 + 1)
			{
				for (iy in 0 until range * 2 + 1)
				{
					val gx = ix + x - range
					val gy = iy + y - range

					if (collisionGrid.inBounds(gx, gy) && Vector2.dst2(gx.toFloat(), gy.toFloat(), x.toFloat(), y.toFloat()) <= (range * range).toFloat())
					{
						if ((!anySolid || rawOutput!![ix][iy] > 0))
						{
							val point = Point.obtainTS().set(gx, gy)
							currentShadowCast.add(point)
						}
						else
						{
							val point = Point.obtainTS().set(gx, gy)
							invCurrentShadowCast.add(point)
						}
					}
				}
			}

			// build list of clear/opaque
			opaqueTiles.clear()
			clearTiles.clear()

			for (pos in currentShadowCast)
			{
				if (pos.x < 0 || pos.y < 0 || pos.x >= collisionGrid.xSize || pos.y >= collisionGrid.ySize)
				{
					continue
				}

				if (collisionGrid[pos.x, pos.y])
				{
					opaqueTiles.add(pos)
				}
				else
				{
					clearTiles.add(pos)
				}
			}
			lastx = x
			lasty = y
			lastrange = range

			updateOpaqueRegions()
		}

		return currentShadowCast
	}
}
