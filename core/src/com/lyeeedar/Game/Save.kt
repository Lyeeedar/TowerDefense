package com.lyeeedar.Game

import com.badlogic.gdx.Gdx
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.lyeeedar.Global
import com.lyeeedar.MainGame
import com.lyeeedar.Settings
import com.lyeeedar.Util.registerGdxSerialisers
import com.lyeeedar.Util.registerLyeeedarSerialisers
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class Save
{
	companion object
	{
		val kryo: Kryo by lazy { initKryo() }
		fun initKryo(): Kryo
		{
			val kryo = Kryo()
			kryo.isRegistrationRequired = false

			kryo.registerGdxSerialisers()
			kryo.registerLyeeedarSerialisers()

			return kryo
		}

		fun save()
		{
			if (doingLoad) return

			val outputFile = Gdx.files.local("save.dat")

			val output: Output
			try
			{
				output = Output(GZIPOutputStream(outputFile.write(false)))
			}
			catch (e: Exception)
			{
				e.printStackTrace()
				return
			}

			// Obtain all data

			// Save all data
			Global.settings.save(kryo, output)

			val currentScreen = Global.game.currentScreenEnum
			output.writeInt(currentScreen.ordinal)

			output.close()
		}

		var doingLoad = false
		fun load(): Boolean
		{
			doingLoad = true
			var input: Input? = null

			try
			{
				val saveFileHandle = Gdx.files.local("save.dat")
				if (!saveFileHandle.exists())
				{
					doingLoad = false
					return false
				}

				input = Input(GZIPInputStream(saveFileHandle.read()))

				// Load all data
				val settings = Settings.load(kryo, input)

				val currentScreen = MainGame.ScreenEnum.values()[input.readInt()]

			}
			catch (ex: Exception)
			{
				if (!Global.release)
				{
					throw ex
				}

				doingLoad = false
				return false
			}
			finally
			{
				input?.close()
			}

			doingLoad = false
			return true
		}
	}
}