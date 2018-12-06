package com.lyeeedar.desktop

import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Util.XmlData

class XmlLoadTester
{
	companion object
	{
		fun test()
		{
			for (path in XmlData.getExistingPaths().toList())
			{
				try
				{
					val xml = XmlData.getXml(path)
					when (xml.name.toUpperCase())
					{
						"EFFECT" -> ParticleEffect.load(path.split("Particles/")[1])
						//else -> throw RuntimeException("Unhandled path type '${xml.name}'!")
					}

					System.out.println("Test loaded '$path'")
				}
				catch (ex: Exception)
				{
					System.err.println("Failed to load '$path'")
					throw ex
				}
			}
		}
	}
}