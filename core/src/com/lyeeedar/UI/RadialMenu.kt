package com.lyeeedar.UI

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils.lerp
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Global
import com.lyeeedar.Util.AssetManager

class RadialMenu(val closeAction: () -> Unit) : Widget()
{
	val circle = AssetManager.loadTextureRegion("Sprites/bigcircle")!!
	val white = AssetManager.loadTextureRegion("Sprites/white")!!

	enum class Position
	{
		Bottom,
		Top
	}

	class MenuItem(val icon: TextureRegion, val tooltip: String, val clickAction: () -> Unit, val dockPosition: Position)
	{
		var assignedAngle: Float = 0f
		val glyphCache = GlyphLayout()
	}

	val menuSize = 128f
	val itemSize = 32f

	var clickPos: Vector2 = Vector2()
	val items = Array<MenuItem>()

	fun addItem(icon: TextureRegion, tooltip: String, clickAction: () -> Unit, dockPosition: Position)
	{
		val item = MenuItem(icon, tooltip, clickAction, dockPosition)
		items.add(item)

		val font = Global.skin.getFont("default")
		item.glyphCache.setText(font, item.tooltip, Color.WHITE, itemSize * 2f, Align.center, true)

		assignAngles()
	}

	var backgroundTable = Table()
	fun show()
	{
		backgroundTable.background = TextureRegionDrawable(AssetManager.loadTextureRegion("white")).tint(Color(0f, 0f, 0f, 0.0f))
		backgroundTable.touchable = Touchable.enabled
		backgroundTable.setFillParent(true)

		backgroundTable.addClickListenerFull {
				inputEvent, x, y ->

			val menux = clickPos.x - menuSize / 2f
			val menuy = clickPos.y - menuSize / 2f

			val itemx = clickPos.x - itemSize / 2f
			val itemy = clickPos.y - itemSize / 2f

			var clicked = false
			for (item in items)
			{
				val vec = Vector2(0f, menuSize/2f)
				vec.rotate(item.assignedAngle)

				val minx = itemx + vec.x
				val maxx = minx + itemSize
				val miny = itemy + vec.y
				val maxy = miny + itemSize

				if (x in minx..maxx && y in miny..maxy)
				{
					item.clickAction.invoke()
					close()

					clicked = true
					inputEvent?.handle()
					break
				}
			}

			if (!clicked)
			{
				close()
			}
		}

		Global.stage.addActor(backgroundTable)
		Global.stage.addActor(this)
	}

	fun close()
	{
		backgroundTable.remove()
		remove()

		closeAction.invoke()
	}

	fun assignAngles()
	{
		val angleStep = 360f / items.size

		val topItems = items.filter { it.dockPosition == Position.Top }
		val bottomItems = items.filter { it.dockPosition == Position.Bottom }

		var anglecurrent = -angleStep * (topItems.size.toFloat() / 2f)
		for (item in topItems)
		{
			item.assignedAngle = anglecurrent + angleStep / 2f
			anglecurrent += angleStep
		}

		anglecurrent = 180f + angleStep * (bottomItems.size.toFloat() / 2f)
		for (item in bottomItems)
		{
			item.assignedAngle = anglecurrent - angleStep / 2f
			anglecurrent -= angleStep
		}
	}

	override fun draw(batch: Batch, parentAlpha: Float)
	{
		val menux = clickPos.x - menuSize / 2f
		val menuy = clickPos.y - menuSize / 2f

		batch.color = Color.DARK_GRAY
		batch.draw(circle, menux, menuy, menuSize, menuSize)

		val itemx = clickPos.x - itemSize / 2f
		val itemy = clickPos.y - itemSize / 2f

		val font = Global.skin.getFont("default")

		for (item in items)
		{
			val vec = Vector2(0f, menuSize/2f)
			vec.rotate(item.assignedAngle)

			val alphax = (vec.x + menuSize/2f) / menuSize
			val alphay = (vec.y + menuSize/2f) / menuSize

			batch.color = Color.YELLOW
			batch.draw(circle, itemx + vec.x, itemy + vec.y, itemSize, itemSize)
			batch.draw(item.icon, itemx + vec.x, itemy + vec.y, itemSize, itemSize)

			vec.set(0f, menuSize/2f + itemSize)
			vec.rotate(item.assignedAngle)

			vec.x += lerp( -item.glyphCache.width, 0f, alphax)
			vec.y += lerp(0f, item.glyphCache.height, alphay)

			font.draw(batch, item.glyphCache, clickPos.x + vec.x, clickPos.y + vec.y)
		}
	}
}