package com.lyeeedar.Screens

import com.lyeeedar.Game.Level.Map
import com.lyeeedar.Game.Level.TileType
import com.lyeeedar.UI.MapWidget
import com.lyeeedar.Util.Array2D

class MapScreen : AbstractScreen()
{
	lateinit var map: Map
	override fun create()
	{
		map = Map.load("Levels/Test")

		val collisionGrid = Array2D<Boolean>(map.width, map.height) { x,y -> map.grid[x,y].type == TileType.WALL }
		//Global.collisionGrid = collisionGrid

		val mapWidget = MapWidget(map)

		mainTable.add(mapWidget).grow()
	}

	override fun doRender(delta: Float)
	{
		map.update(delta)
	}
}