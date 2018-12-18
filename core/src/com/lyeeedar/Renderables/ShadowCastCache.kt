package com.lyeeedar.Renderables

import com.badlogic.gdx.utils.ObjectSet
import com.lyeeedar.Global.Companion.collisionGrid
import com.lyeeedar.Util.Point
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
	private val opaqueTiles = com.badlogic.gdx.utils.Array<Point>()
	private val clearTiles = com.badlogic.gdx.utils.Array<Point>()
	val currentShadowCast = com.badlogic.gdx.utils.Array<Point>()
	val currentShadowCastSet = ObjectSet<Point>()

	@JvmOverloads fun getShadowCast(x: Int, y: Int, range: Int): com.badlogic.gdx.utils.Array<Point>
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
				currentShadowCastSet.clear()

				for (ix in 0 until range * 2)
				{
					for (iy in 0 until range * 2)
					{
						val gx = ix + x - range
						val gy = iy + y - range

						val point = Point.obtainTS().set(gx, gy)
						currentShadowCast.add(point)
						currentShadowCastSet.add(point)
					}
				}
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
			currentShadowCastSet.clear()

			// build grid
			var anySolid = false
			val resistanceGrid = Array(range * 2) { DoubleArray(range * 2) }
			for (ix in 0 until range * 2)
			{
				for (iy in 0 until range * 2)
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
				rawOutput = fov.calculateFOV(resistanceGrid, range, range, range.toDouble(), Radius.SQUARE)
			}

			for (ix in 0 until range * 2)
			{
				for (iy in 0 until range * 2)
				{
					val gx = ix + x - range
					val gy = iy + y - range

					if ((!anySolid || rawOutput!![ix][iy] > 0) && collisionGrid.inBounds(gx, gy))
					{
						val point = Point.obtainTS().set(gx, gy)
						currentShadowCast.add(point)
						currentShadowCastSet.add(point)
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
		}

		return currentShadowCast
	}
}
