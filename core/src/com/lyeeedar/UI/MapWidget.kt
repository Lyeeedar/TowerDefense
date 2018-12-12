package com.lyeeedar.UI

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.lyeeedar.Game.Level.*
import com.lyeeedar.Game.Level.Map
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Renderables.SortedRenderer
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Util.AssetManager
import com.lyeeedar.Util.Colour
import com.lyeeedar.Util.Point
import com.lyeeedar.Util.max

class MapWidget(val map: Map) : Widget()
{
	var tileSize = 32f
		set(value)
		{
			field = value
			ground.tileSize = value
			floating.tileSize = value
		}

	val glow: Sprite = AssetManager.loadSprite("glow")
	val frame: Sprite = AssetManager.loadSprite("GUI/frame", colour = Colour(Color(0.6f, 0.7f, 0.9f, 0.6f)))
	val border: Sprite = AssetManager.loadSprite("GUI/border", colour = Colour(Color(0.6f, 0.9f, 0.6f, 0.6f)))
	val hp_full: Sprite = AssetManager.loadSprite("GUI/health_full")
	val hp_dr: Sprite = AssetManager.loadSprite("GUI/health_DR")
	val hp_damaged: Sprite = AssetManager.loadSprite("GUI/health_damaged")
	val hp_neutral: Sprite = AssetManager.loadSprite("GUI/health_neutral")
	val hp_full_friendly: Sprite = AssetManager.loadSprite("GUI/health_full_green")
	val hp_full_summon: Sprite = AssetManager.loadSprite("GUI/health_full_blue")
	val hp_empty: Sprite = AssetManager.loadSprite("GUI/health_empty")
	val atk_full: Sprite = AssetManager.loadSprite("GUI/attack_full")
	val atk_empty: Sprite = AssetManager.loadSprite("GUI/attack_empty")
	val stage_full: Sprite = AssetManager.loadSprite("GUI/attack_full")
	val stage_empty: Sprite = AssetManager.loadSprite("GUI/attack_empty")
	val changer: Sprite = AssetManager.loadSprite("Oryx/Custom/items/changer", drawActualSize = true)
	val white: Sprite = AssetManager.loadSprite("white")
	val circle: Sprite = AssetManager.loadSprite("bigcircle")

	val TILE = 0
	val ENTITY = TILE+1
	val EFFECT = ENTITY+1

	val ground = SortedRenderer(tileSize, map.width.toFloat(), map.height.toFloat(), EFFECT+1, true)
	val floating = SortedRenderer(tileSize, map.width.toFloat(), map.height.toFloat(), EFFECT+1, true)

	val shapeRenderer = ShapeRenderer()

	val tempCol = Colour()

	var hpLossTimer = 0f

	init
	{
		touchable = Touchable.enabled

		addListener(object : InputListener()
					{
						override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean
						{
							val xp = x + ((map.width * tileSize) / 2f) - (width / 2f)

							val sx = (xp / tileSize).toInt()
							val sy = (map.height-1) - (y / tileSize).toInt()

							if (button == Input.Buttons.LEFT)
							{
								//map.grid[sx, sy].isSolid = !map.grid[sx, sy].isSolid

								val screenPos = pointToScreenspace(Vector2(sx.toFloat(), sy.toFloat()))
								val tile = map.grid[sx, sy]

								if (tile.isSolid)
								{
									if (tile.fillingEntity != null)
									{
										val tower = tile.fillingEntity as? Tower
										if (tower != null)
										{
											val menu = RadialMenu({
												tower.selected = false
																  })

											for (upgrade in tower.def.upgrades)
											{
												menu.addItem(upgrade.towerDef.icon.textures[0], "Upgrade to " + upgrade.towerDef.name + " (" + upgrade.cost + " gold)", {
													val tower = Tower(upgrade.towerDef)
													tower.tile = tile
													tile.fillingEntity = tower
												}, RadialMenu.Position.Top)
											}

											menu.addItem(AssetManager.loadTextureRegion("Sprites/white")!!, "Sell tower for 32 gold.", {
												tile.fillingEntity = null
											}, RadialMenu.Position.Bottom)

											tower.selected = true

											menu.clickPos = screenPos
											menu.show()
										}

									}
									else
									{
										val arrowTower = TowerUpgradeTree.load("Arrow")
										val root = arrowTower.towerDefMap[arrowTower.root]

										val menu = RadialMenu({})

										menu.addItem(root.icon.textures[0], "Build " + root.name, {
											val tower = Tower(root)
											tower.tile = tile
											tile.fillingEntity = tower
										}, RadialMenu.Position.Top)

										menu.clickPos = screenPos
										menu.show()
									}
								}


							}

							//map.select(Point(sx, sy))

							return true
						}

						override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int)
						{
							//map.clearDrag()

							super.touchUp(event, x, y, pointer, button)
						}

						override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int)
						{
							val xp = x + ((map.width * tileSize) / 2f) - (width / 2f)

							val sx = (xp / tileSize).toInt()
							val sy = (map.height - 1) - (y / tileSize).toInt()

							val point = Point(sx, sy)

							//if (point != map.dragStart)
							//{
							//	map.dragEnd(point)
							//}
						}

						override fun keyTyped(event: InputEvent?, character: Char): Boolean
						{
							if (character == 'd')
							{
								ground.debugDraw = !ground.debugDraw
							}

							return false
						}
					})

		atk_empty.baseScale = floatArrayOf(0.14f, 0.14f)
		atk_full.baseScale = floatArrayOf(0.14f, 0.14f)
	}

	fun getRect(point: Point, actualSize: Boolean = false): Rectangle
	{
		val array = com.badlogic.gdx.utils.Array<Point>()
		array.add(point)
		val rect = getRect(array)

		if (actualSize)
		{
			rect.height *= 1.5f
		}

		return rect
	}

	fun getRect(points: com.badlogic.gdx.utils.Array<Point>): Rectangle
	{
		var minx = Float.MAX_VALUE
		var miny = Float.MAX_VALUE
		var maxx = -Float.MAX_VALUE
		var maxy = -Float.MAX_VALUE

		for (point in points)
		{
			val screenSpace = pointToScreenspace(point)
			if (screenSpace.x < minx)
			{
				minx = screenSpace.x
			}
			if (screenSpace.y < miny)
			{
				miny = screenSpace.y
			}
			if (screenSpace.x + tileSize > maxx)
			{
				maxx = screenSpace.x + tileSize
			}
			if (screenSpace.y + tileSize > maxy)
			{
				maxy = screenSpace.y + tileSize
			}
		}

		return Rectangle(minx, miny, maxx - minx, maxy - miny)
	}

	fun pointToScreenspace(point: Point): Vector2
	{
		val xp = x + (width / 2f) - ((map.width * tileSize) / 2f)

		return Vector2(xp + point.x * tileSize, renderY + ((map.height - 1) - point.y) * tileSize)
	}

	fun pointToScreenspace(point: Vector2): Vector2
	{
		val xp = x + (width / 2f) - ((map.width * tileSize) / 2f)

		return Vector2(xp + point.x * tileSize, renderY + ((map.height - 1) - point.y) * tileSize)
	}

	override fun invalidate()
	{
		super.invalidate()

		val w = width / map.width.toFloat()
		val h = (height - 16f) / map.height.toFloat()

		tileSize = Math.min(w, h)
	}

	class TargetLine(val v1: Vector2, val v2: Vector2, val color: Color)

	var renderY = 0f
	override fun draw(batch: Batch?, parentAlpha: Float)
	{
		val xp = this.x + (this.width / 2f) - ((map.width * tileSize) / 2f)
		val yp = this.y
		renderY = yp

		ground.begin(Gdx.app.graphics.deltaTime, xp, yp)
		floating.begin(Gdx.app.graphics.deltaTime, xp, yp)

		batch!!.color = Color.WHITE

		val lines = com.badlogic.gdx.utils.Array<TargetLine>()

		for (x in 0 until map.width)
		{
			for (y in 0 until map.height)
			{
				val tile = map.grid[x, y]

				var tileColour = Colour.WHITE

				if (tile.entities.size > 0)
				{
					tileColour = Colour.GOLD
				}

				val xi = x.toFloat()
				val yi = (map.height-1) - y.toFloat()

				var tileHeight = 0

				val groundSprite = tile.groundSprite
				if (groundSprite != null)
				{
					if (!groundSprite.hasChosenSprites)
					{
						groundSprite.chooseSprites()
					}

					val sprite = groundSprite.chosenSprite
					if (sprite != null)
					{
						ground.queueSprite(sprite, xi, yi, TILE, tileHeight, tileColour)
					}

					val tilingSprite = groundSprite.chosenTilingSprite
					if (tilingSprite != null)
					{
						ground.queueSprite(tilingSprite, xi, yi, TILE, tileHeight, tileColour)
					}

					tileHeight++
				}

				val wallSprite = tile.wallSprite
				if (wallSprite != null)
				{
					if (!wallSprite.hasChosenSprites)
					{
						wallSprite.chooseSprites()
					}

					val sprite = wallSprite.chosenSprite
					if (sprite != null)
					{
						ground.queueSprite(sprite, xi, yi, TILE, tileHeight, tileColour)
					}

					val tilingSprite = wallSprite.chosenTilingSprite
					if (tilingSprite != null)
					{
						ground.queueSprite(tilingSprite, xi, yi, TILE, tileHeight, tileColour)
					}

					tileHeight++
				}

				if (tile.fillingEntity != null)
				{
					ground.queueSprite(tile.fillingEntity!!.sprite, xi, yi, ENTITY, 0, tileColour)

					val tower = tile.fillingEntity as? Tower
					if (tower != null)
					{
						if (tower.selected)
						{
							var range = 0f
							for (effect in tower.def.effects)
							{
								if (effect is ShotEffectType)
								{
									range = max(range, effect.range)
								}
							}

							if (range > 0f)
							{
								floating.queueSprite(circle, xi, yi, ENTITY, 0, Colour(1f, 1f, 1f, 0.1f), scaleX = range * 2f, scaleY = range * 2f)
							}
						}
					}
				}

				for (entity in tile.entities)
				{
					if (entity is Enemy)
					{
						ground.update(entity.sprite, Gdx.app.graphics.deltaTime)

						if (entity.sprite.animation == null)
						{
							ground.queueSprite(entity.sprite, xi, yi, ENTITY, 0, tileColour)
						}
						else
						{
							ground.queueSprite(entity.sprite, 0f, 0f, ENTITY, 0, tileColour)
						}

						if (entity.hp != entity.def.health && entity.sprite.animation != null)
						{
							val pos = entity.sprite.animation?.renderOffset(false)!!
							pos[0] += (1f - entity.sprite.baseScale[0]) / 2f - 0.1f
							pos[1] += 1f - ((1f - entity.sprite.baseScale[1]) / 2f)
							pos[1] += 0.2f

							val a = entity.hp.toFloat() / entity.def.health.toFloat()
							val col = Colour.RED.copy().lerpHSV(Colour.GREEN.copy(), a)

							ground.queueSprite(white, pos[0], pos[1], ENTITY, 0, col, width = a * entity.sprite.baseScale[0] + 0.2f, height = 0.1f)
						}

						for (effect in entity.effects)
						{
							if (effect is Sprite)
							{
								if (effect.completed)
								{
									entity.effects.removeValue(effect, true)
								}
								else
								{
									floating.queueSprite(effect, xi, yi, EFFECT, 0)
								}
							}
							else if (effect is ParticleEffect)
							{
								if (effect.completed)
								{
									entity.effects.removeValue(effect, true)
								}
								else
								{
									floating.queueParticle(effect, xi, yi, EFFECT, 0)
								}
							}
						}
					}
					else
					{
						ground.queueSprite(entity.sprite, 0f, 0f, ENTITY, 0, tileColour)
					}
				}

				for (effect in tile.effects)
				{
					if (effect is Sprite)
					{
						if (effect.completed)
						{
							tile.effects.removeValue(effect, true)
						}
						else
						{
							floating.queueSprite(effect, xi, yi, EFFECT, 0)
						}
					}
					else if (effect is ParticleEffect)
					{
						if (effect.completed)
						{
							tile.effects.removeValue(effect, true)
						}
						else
						{
							floating.queueParticle(effect, xi, yi, EFFECT, 0)
						}
					}
				}
			}
		}

		ground.flush(batch)
		floating.flush(batch)
	}
}