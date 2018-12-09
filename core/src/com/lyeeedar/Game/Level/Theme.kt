package com.lyeeedar.Game.Level

import com.lyeeedar.Renderables.Sprite.SpriteWrapper
import com.lyeeedar.Util.getXml

class Theme(val filepath: String)
{
	lateinit var path: SpriteWrapper
	lateinit var wall: SpriteWrapper
	lateinit var block: SpriteWrapper

	lateinit var backgroundTile: String

	companion object
	{
		fun load(path: String): Theme
		{
			val xml = getXml("Themes/$path")
			val theme = Theme(path)

			theme.path = SpriteWrapper.load(xml.getChildByName("Path")!!)
			theme.wall = SpriteWrapper.load(xml.getChildByName("Wall")!!)
			theme.block = SpriteWrapper.load(xml.getChildByName("Block")!!)

			theme.backgroundTile = xml.get("BackgroundTile")

			return theme
		}
	}
}