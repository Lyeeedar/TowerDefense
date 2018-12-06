package com.lyeeedar

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.lyeeedar.Screens.AbstractScreen
import com.lyeeedar.Screens.MapScreen
import com.lyeeedar.Screens.ParticleEditorScreen
import com.lyeeedar.Util.Future
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import javax.swing.JOptionPane

class MainGame : Game()
{
	enum class ScreenEnum
	{
		MAP,

		PARTICLEEDITOR,
		INVALID
	}

	private val screens = HashMap<ScreenEnum, AbstractScreen>()
	var currentScreen: AbstractScreen? = null
	val currentScreenEnum: ScreenEnum
		get()
		{
			for (se in ScreenEnum.values())
			{
				if (screens[se] == currentScreen)
				{
					return se
				}
			}
			return ScreenEnum.INVALID
		}

	override fun create()
	{
		Global.applicationChanger.processResources()
		Global.setup()

		if (Global.android)
		{

		}
		else if (Global.release)
		{
			val sw = StringWriter()
			val pw = PrintWriter(sw)

			val handler = Thread.UncaughtExceptionHandler { myThread, e ->
				e.printStackTrace(pw)
				val exceptionAsString = sw.toString()

				val file = Gdx.files.local("error.log")
				file.writeString(exceptionAsString, false)

				JOptionPane.showMessageDialog(null, "A fatal error occurred. Please send the error.log to me so that I can fix it.", "An error occurred", JOptionPane.ERROR_MESSAGE)

				e.printStackTrace()
			}

			Thread.currentThread().uncaughtExceptionHandler = handler
		}

		if (Global.PARTICLE_EDITOR)
		{
			screens.put(ScreenEnum.PARTICLEEDITOR, ParticleEditorScreen())
			switchScreen(ScreenEnum.PARTICLEEDITOR)
		}
		else
		{
			screens.put(ScreenEnum.MAP, MapScreen())

			switchScreen(ScreenEnum.MAP)
		}

	}

	fun switchScreen(screen: AbstractScreen)
	{
		this.setScreen(screen)
	}

	fun switchScreen(screen: ScreenEnum)
	{
		this.setScreen(screens[screen])
	}

	inline fun <reified T : AbstractScreen> getTypedScreen(): T?
	{
		for (screen in getAllScreens())
		{
			if (screen is T)
			{
				return screen
			}
		}

		return null
	}

	fun getAllScreens() = screens.values

	override fun setScreen(screen: Screen?)
	{
		if (currentScreen != null)
		{
			currentScreen!!.fadeOutTransition(0.2f)

			Future.call(
					{
						val ascreen = screen as AbstractScreen
						currentScreen = ascreen
						super.setScreen(screen)
						ascreen.fadeInTransition(0.2f)
					}, 0.2f)
		}
		else
		{
			currentScreen = screen as? AbstractScreen
			super.setScreen(screen)
		}
	}

	fun getScreen(screen: ScreenEnum): AbstractScreen = screens[screen]!!
}
