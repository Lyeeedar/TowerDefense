package com.lyeeedar.Game.Level

import com.badlogic.gdx.utils.Array
import com.lyeeedar.Renderables.Renderable
import com.lyeeedar.Renderables.Sprite.SpriteWrapper
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour
import com.lyeeedar.Util.Point

class Tile(x: Int, y: Int) : Point(x, y)
{
	val entities = Array<Entity>()

	var fillingEntity: Entity? = null
		set(value)
		{
			field = value

			tileDirty = true
		}

	var previewTower: TowerDefinition? = null

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