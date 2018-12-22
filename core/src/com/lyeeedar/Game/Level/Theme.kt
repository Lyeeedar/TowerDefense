package com.lyeeedar.Game.Level

import com.badlogic.gdx.utils.Array
import com.lyeeedar.Util.getXml

class Theme(val filepath: String)
{
	val symbols = Array<Symbol>()

	lateinit var backgroundTile: String

	companion object
	{
		fun load(path: String): Theme
		{
			val xml = getXml("Themes/$path")
			val theme = Theme(path)

			theme.backgroundTile = xml.get("BackgroundTile")

			val symbolsEl = xml.getChildByName("Symbols")!!
			for (symbolEl in symbolsEl.children)
			{
				val symbol = Symbol.parse(symbolEl)
				theme.symbols.add(symbol)
			}

			return theme
		}
	}
}