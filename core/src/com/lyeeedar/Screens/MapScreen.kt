package com.lyeeedar.Screens

import com.lyeeedar.Game.Level.Map
import com.lyeeedar.UI.MapWidget

class MapScreen : AbstractScreen()
{
	lateinit var map: Map
	override fun create()
	{
		map = Map.load("Levels/Test")

		val mapWidget = MapWidget(map)

		mainTable.add(mapWidget).grow()
	}

	override fun doRender(delta: Float)
	{
		map.update(delta)
	}
}