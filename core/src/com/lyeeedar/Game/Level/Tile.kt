package com.lyeeedar.Game.Level

import com.badlogic.gdx.utils.Array
import com.lyeeedar.Renderables.Renderable
import com.lyeeedar.Renderables.Sprite.SpriteWrapper
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour
import com.lyeeedar.Util.Point

enum class TileType
{
	PATH,
	GROUND,
	WALL
}

class Tile(x: Int, y: Int) : Point(x, y)
{
	val enemies = Array<Enemy>()

	var fillingEntity: Entity? = null
		set(value)
		{
			field = value

			tileDirty = true
		}

	var previewTower: TowerDefinition? = null

	var type: TileType = TileType.GROUND
		set(value)
		{
			field = value
			tileDirty = true
		}

	val effects = Array<Renderable>()

	var sprite: SpriteWrapper? = null

	var tileDirty = false

	init
	{
		sprite = SpriteWrapper()
		sprite!!.sprite = AssetManager.loadSprite("white", colour = Colour.LIGHT_GRAY)
	}
}