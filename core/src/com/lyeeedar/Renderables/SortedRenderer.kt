package com.lyeeedar.Renderables

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BigMesh
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.IntMap
import com.badlogic.gdx.utils.ObjectSet
import com.badlogic.gdx.utils.Pool
import com.lyeeedar.BlendMode
import com.lyeeedar.Direction
import com.lyeeedar.Global
import com.lyeeedar.Renderables.Particle.Emitter
import com.lyeeedar.Renderables.Particle.Particle
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Renderables.RadixSort.Companion.MOST_SIGNIFICANT_BYTE_INDEX
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Renderables.Sprite.TilingSprite
import com.lyeeedar.Util.*
import ktx.collections.set
import squidpony.squidmath.LightRNG


/**
 * Created by Philip on 04-Jul-16.
 */

// ----------------------------------------------------------------------
class SortedRenderer(var tileSize: Float, val width: Float, val height: Float, val layers: Int, val alwaysOnscreen: Boolean)
{
	private var batchID: Int = random.nextInt()

	private val tempVec = Vector2()
	private val tempVec3 = Vector3()
	private val tempCol = Colour()
	private val bitflag = EnumBitflag<Direction>()

	private val startingArraySize = 128
	private var spriteArray = Array<RenderSprite?>(startingArraySize) { null }
	private var sortedArray = Array<RenderSprite?>(startingArraySize) { null }
	private var queuedSprites = 0

	private val tilingMap: IntMap<ObjectSet<Long>> = IntMap()

	private val setPool: Pool<ObjectSet<Long>> = object : Pool<ObjectSet<Long>>() {
		override fun newObject(): ObjectSet<Long>
		{
			return ObjectSet()
		}
	}

	private val lights = com.badlogic.gdx.utils.Array<Light>()

	private var screenShakeRadius: Float = 0f
	private var screenShakeAccumulator: Float = 0f
	private var screenShakeSpeed: Float = 0f
	private var screenShakeAngle: Float = 0f
	private var screenShakeLocked: Boolean = false

	private val BLENDMODES = BlendMode.values().size
	private val MAX_INDEX = 6 * BLENDMODES
	private val X_BLOCK_SIZE = layers * MAX_INDEX
	private val Y_BLOCK_SIZE = X_BLOCK_SIZE * width.toInt()
	private val MAX_Y_BLOCK_SIZE = Y_BLOCK_SIZE * height.toInt()
	private val MAX_X_BLOCK_SIZE = X_BLOCK_SIZE * width.toInt()

	private var delta: Float = 0f

	private var inBegin = false
	private var inStaticBegin = false
	private var offsetx: Float = 0f
	private var offsety: Float = 0f

	private val ambientLight = Colour()

	// ----------------------------------------------------------------------
	private class VertexBuffer
	{
		var offset = -1
		var count = -1
		lateinit var texture: Texture
		var blendSrc: Int = -1
		var blendDst: Int = -1

		init
		{

		}

		fun reset(blendSrc: Int, blendDst: Int, texture: Texture): VertexBuffer
		{
			this.blendSrc = blendSrc
			this.blendDst = blendDst
			this.texture = texture
			count = 0
			offset = 0

			return this
		}
	}

	// ----------------------------------------------------------------------
	private val mesh: BigMesh
	private var currentBuffer: VertexBuffer? = null
	private val vertices: FloatArray
	private var currentVertexCount = 0
	private var currentStaticVertexCount = 0
	private val staticBuffers = com.badlogic.gdx.utils.Array<VertexBuffer>()
	private val queuedBuffers = com.badlogic.gdx.utils.Array<VertexBuffer>()
	private lateinit var shader: ShaderProgram
	private var shaderLightNum: Int = -1
	private lateinit var lightPosRange: FloatArray
	private lateinit var lightColourBrightness: FloatArray
	private lateinit var lightShadowData: FloatArray
	private lateinit var lightShadowPoints: FloatArray
	private val combinedMatrix: Matrix4 = Matrix4()

	private val executor = LightweightThreadpool(3)

	// ----------------------------------------------------------------------
	private val bufferPool: Pool<VertexBuffer> = object : Pool<VertexBuffer>() {
		override fun newObject(): VertexBuffer
		{
			return VertexBuffer()
		}
	}

	init
	{
		mesh = BigMesh(maxSprites * 4, maxSprites * 6,
					   VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
					   VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
					   VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
					   VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "1"),
					   VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_spritePos"),
					   VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_blendAlpha")
					  )

		val len = maxSprites * 6
		val indices = IntArray(len)
		var j = 0
		var i = 0
		while (i < len)
		{
			indices[i] = j
			indices[i + 1] = j + 1
			indices[i + 2] = j + 2
			indices[i + 3] = j + 2
			indices[i + 4] = j + 3
			indices[i + 5] = j
			i += 6
			j += 4
		}
		mesh.setIndices(indices)

		vertices = FloatArray(maxVertices)

		shaderLightNum = 10
		lightPosRange = FloatArray(shaderLightNum * 3)
		lightColourBrightness = FloatArray(shaderLightNum * 4)
		lightShadowData = FloatArray(shaderLightNum * 3)
		lightShadowPoints = FloatArray(maxShadowPoints * 2)
		shader = createShader(shaderLightNum)
	}

	// ----------------------------------------------------------------------
	fun begin(deltaTime: Float, offsetx: Float, offsety: Float, ambientLight: Colour)
	{
		if (inBegin) throw Exception("Begin called again before flush!")

		this.ambientLight.set(ambientLight)
		delta = deltaTime
		this.offsetx = offsetx
		this.offsety = offsety
		inBegin = true
	}

	// ----------------------------------------------------------------------
	fun beginStatic()
	{
		if (inBegin) throw Exception("BeginStatic called within begin!")
		if (inStaticBegin) throw Exception("BeginStatic called BeginStatic!")

		for (buffer in staticBuffers)
		{
			bufferPool.free(buffer)
		}
		staticBuffers.clear()

		delta = 0f
		inStaticBegin = true
		currentStaticVertexCount = 0
	}

	// ----------------------------------------------------------------------
	fun end(batch: Batch)
	{
		if (!inBegin) throw Exception("End called before begin!")

		flush(batch)

		inBegin = false
	}

	// ----------------------------------------------------------------------
	fun endStatic(batch: Batch)
	{
		if (!inStaticBegin) throw Exception("EndStatic called before beginstatic!")

		flush(batch)

		inStaticBegin = false
	}

	// ----------------------------------------------------------------------
	fun setScreenShake(amount: Float, speed: Float)
	{
		screenShakeRadius = amount
		screenShakeSpeed = speed
	}

	// ----------------------------------------------------------------------
	fun lockScreenShake()
	{
		screenShakeLocked = true
	}

	// ----------------------------------------------------------------------
	fun unlockScreenShake()
	{
		screenShakeLocked = false
	}

	// ----------------------------------------------------------------------
	private fun requestRender(blendSrc: Int, blendDst: Int, texture: Texture, drawFun: (vertices: FloatArray, offset: Int) -> Unit)
	{
		if (currentBuffer == null)
		{
			currentBuffer = bufferPool.obtain()
			currentBuffer!!.reset(blendSrc, blendDst, texture)
			currentBuffer!!.offset = currentStaticVertexCount + currentVertexCount
		}

		var buffer = currentBuffer!!
		if (buffer.blendSrc != blendSrc || buffer.blendDst != blendDst || buffer.texture != texture)
		{
			queuedBuffers.add(currentBuffer)
			buffer = bufferPool.obtain()
			buffer.reset(blendSrc, blendDst, texture)
			buffer.offset = currentStaticVertexCount + currentVertexCount

			currentBuffer = buffer
		}

		val offset = currentStaticVertexCount + currentVertexCount
		buffer.count += verticesASprite
		currentVertexCount += verticesASprite

		if (currentStaticVertexCount + currentVertexCount == maxVertices) throw Exception("Too many vertices queued!")

		executor.addJob {
			drawFun.invoke(vertices, offset)
		}
	}

	// ----------------------------------------------------------------------
	private fun storeStatic()
	{
		executor.awaitAllJobs()

		queuedBuffers.add(currentBuffer!!)
		currentBuffer = null

		staticBuffers.addAll(queuedBuffers)
		queuedBuffers.clear()
		currentStaticVertexCount = currentVertexCount
		currentVertexCount = 0
	}

	// ----------------------------------------------------------------------
	private fun waitOnRender()
	{
		if (currentBuffer == null) return

		if (lights.size > shaderLightNum)
		{
			shaderLightNum = lights.size + 5
			shader.dispose()
			shader = createShader(shaderLightNum)
			lightPosRange = FloatArray(shaderLightNum * 3)
			lightColourBrightness = FloatArray(shaderLightNum * 4)
			lightShadowData = FloatArray(shaderLightNum * 3)
		}

		Gdx.gl.glEnable(GL20.GL_BLEND)
		Gdx.gl.glDepthMask(false)
		shader.begin()

		shader.setUniformMatrix("u_projTrans", combinedMatrix)
		shader.setUniformf("u_offset", offsetx, offsety)
		shader.setUniformi("u_texture", 0)
		shader.setUniformf("u_ambient", ambientLight.vec3())
		shader.setUniformf("u_tileSize", tileSize)

		var shadowCacheOffset = 0
		var i = 0
		for (light in lights)
		{
			lightPosRange[(i*3)+0] = light.pos.x * tileSize + offsetx
			lightPosRange[(i*3)+1] = light.pos.y * tileSize + offsety
			lightPosRange[(i*3)+2] = (light.range * tileSize * 0.9f) * (light.range * tileSize * 0.9f)

			lightColourBrightness[(i*4)+0] = light.colour.r
			lightColourBrightness[(i*4)+1] = light.colour.g
			lightColourBrightness[(i*4)+2] = light.colour.b
			lightColourBrightness[(i*4)+3] = light.brightness

			if (Global.collisionGrid != null)
			{
				val mode = if (light.cache.currentShadowCast.size <= light.cache.invCurrentShadowCast.size) -10 else 10
				val cast = if (mode < 0) light.cache.currentShadowCast else light.cache.invCurrentShadowCast

				val numCount = when
				{
					!light.hasShadows -> 0f
					!light.cache.anyOpaque() -> 0f
					!light.cache.anyClear() -> -1f
					else -> cast.size.toFloat()
				}

				lightShadowData[(i*3)+0] = numCount
				lightShadowData[(i*3)+1] = (shadowCacheOffset / 2).toFloat()
				lightShadowData[(i*3)+2] = mode.toFloat()

				for (point in cast)
				{
					val dx = point.x - light.pos.x
					val dy = point.y - light.pos.y

					lightShadowPoints[shadowCacheOffset++] = dx
					lightShadowPoints[shadowCacheOffset++] = dy
				}
			}

			i++
		}

		shader.setUniform3fv("u_lightPosRange", lightPosRange, 0, lights.size * 3)
		shader.setUniform4fv("u_lightColourBrightness", lightColourBrightness, 0, lights.size * 4)

		if (Global.collisionGrid != null)
		{
			shader.setUniform3fv("u_lightShadowData", lightShadowData, 0, lights.size * 3)
			shader.setUniform2fv("u_lightShadowPoints", lightShadowPoints, 0, shadowCacheOffset)
		}

		shader.setUniformi("u_numLights", lights.size)

		executor.awaitAllJobs()

		queuedBuffers.add(currentBuffer!!)
		currentBuffer = null

		mesh.setVertices(vertices, 0, currentStaticVertexCount + currentVertexCount)
		mesh.bind(shader)

		var lastBlendSrc = -1
		var lastBlendDst = -1
		var lastTexture: Texture? = null
		var currentOffset = 0

		fun drawBuffer(buffer: VertexBuffer)
		{
			if (buffer.texture != lastTexture)
			{
				buffer.texture.bind()
				lastTexture = buffer.texture
			}

			if (buffer.blendSrc != lastBlendSrc || buffer.blendDst != lastBlendDst)
			{
				Gdx.gl.glBlendFunc(buffer.blendSrc, buffer.blendDst)

				lastBlendSrc = buffer.blendSrc
				lastBlendDst = buffer.blendDst
			}

			val spritesInBuffer = buffer.count / (4 * 11)
			val drawCount = spritesInBuffer * 6
			mesh.render(shader, GL20.GL_TRIANGLES, currentOffset, drawCount)
			currentOffset += drawCount
		}

		for (buffer in staticBuffers)
		{
			drawBuffer(buffer)
		}

		for (buffer in queuedBuffers)
		{
			drawBuffer(buffer)
			bufferPool.free(buffer)
		}
		queuedBuffers.clear()

		mesh.unbind(shader)

		Gdx.gl.glDepthMask(true)
		Gdx.gl.glDisable(GL20.GL_BLEND)
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
		shader.end()

		currentVertexCount = 0
	}

	// ----------------------------------------------------------------------
	private fun queueRenderJobs()
	{
		for (i in 0 until queuedSprites)
		{
			val rs = spriteArray[i]!!

			var sprite = rs.sprite
			if (rs.tilingSprite != null)
			{
				bitflag.clear()
				for (dir in Direction.Values)
				{
					val hash = Point.getHashcode(rs.px, rs.py, dir)
					val keys = tilingMap[hash]

					if (keys?.contains(rs.tilingSprite!!.checkID) != true)
					{
						bitflag.setBit(dir)
					}
				}

				sprite = rs.tilingSprite!!.getSprite(bitflag)
			}

			var texture = rs.texture?.texture

			if (sprite != null)
			{
				texture = sprite.currentTexture.texture
			}

			requestRender(rs.blend.src, rs.blend.dst, texture!!, { vertices: FloatArray, offset: Int ->
				val localx = rs.x
				val localy = rs.y
				val localw = rs.width * tileSize
				val localh = rs.height * tileSize

				val colour = rs.colour

				if (sprite != null)
				{
					colour.mul(sprite.getRenderColour())
					sprite.render(vertices, offset, colour, localx, localy, localw, localh, rs.scaleX, rs.scaleY, rs.rotation)
				}

				if (rs.texture != null)
				{
					doDraw(vertices, offset,
						   rs.texture!!, rs.nextTexture ?: rs.texture!!, colour,
						   localx, localy, 0.5f, 0.5f, 1f, 1f, localw * rs.scaleX, localh * rs.scaleY, rs.rotation, rs.flipX, rs.flipY,
						   0f, rs.blendAlpha)
				}
			} )
		}
	}

	// ----------------------------------------------------------------------
	private fun cleanup()
	{
		// clean up
		for (i in 0 until queuedSprites)
		{
			val rs = spriteArray[i]!!
			rs.free()
		}

		batchID = random.nextInt()
		Particle.generateBrownianVectors()

		for (entry in tilingMap)
		{
			setPool.free(entry.value)
		}
		tilingMap.clear()

		lights.clear()

		if (queuedSprites < spriteArray.size / 4)
		{
			spriteArray = spriteArray.copyOf(spriteArray.size / 4)
			sortedArray = sortedArray.copyOf(sortedArray.size / 4)
		}

		queuedSprites = 0
	}

	// ----------------------------------------------------------------------
	private fun flush(batch: Batch)
	{
		// Begin prerender work
		executor.addJob {
			// sort
			RadixSort.sort(spriteArray, sortedArray, 0, 0, queuedSprites, MOST_SIGNIFICANT_BYTE_INDEX)

			// do screen shake
			if ( screenShakeRadius > 2 )
			{
				screenShakeAccumulator += delta

				while ( screenShakeAccumulator >= screenShakeSpeed )
				{
					screenShakeAccumulator -= screenShakeSpeed
					screenShakeAngle += (150 + Random.random() * 60)

					if (!screenShakeLocked)
					{
						screenShakeRadius *= 0.9f
					}
				}

				offsetx += Math.sin( screenShakeAngle.toDouble() ).toFloat() * screenShakeRadius
				offsety += Math.cos( screenShakeAngle.toDouble() ).toFloat() * screenShakeRadius
			}
		}

		for (light in lights)
		{
			executor.addJob {
				light.update(delta)
			}
		}

		executor.awaitAllJobs()

		// begin rendering
		queueRenderJobs()

		if (inStaticBegin)
		{
			storeStatic()
		}
		else
		{
			combinedMatrix.set(batch.projectionMatrix).mul(batch.transformMatrix)
			waitOnRender()
		}

		cleanup()
	}

	// ----------------------------------------------------------------------
	private fun getComparisonVal(x: Int, y: Int, layer: Int, index: Int, blend: BlendMode) : Int
	{
		if (index > MAX_INDEX-1) throw RuntimeException("Index too high! $index >= $MAX_INDEX!")
		if (layer > layers-1) throw RuntimeException("Layer too high! $index >= $layers!")

		val yBlock = MAX_Y_BLOCK_SIZE - y * Y_BLOCK_SIZE
		val xBlock = (MAX_X_BLOCK_SIZE - x * X_BLOCK_SIZE)
		val lBlock = layer * MAX_INDEX
		val iBlock = index * BLENDMODES

		return yBlock + xBlock + lBlock + iBlock + blend.ordinal
	}

	// ----------------------------------------------------------------------
	fun update(renderable: Renderable, deltaTime: Float? = null)
	{
		if (renderable.batchID != batchID) renderable.update(deltaTime ?: delta)
		renderable.batchID = batchID
	}

	// ----------------------------------------------------------------------
	fun queue(renderable: Renderable, ix: Float, iy: Float, layer: Int, index: Int, colour: Colour = Colour.WHITE, width: Float = 1f, height: Float = 1f)
	{
		if (renderable is Sprite) queueSprite(renderable, ix, iy, layer, index, colour, width, height)
		else if (renderable is TilingSprite) queueSprite(renderable, ix, iy, layer, index, colour, width, height)
		else if (renderable is ParticleEffect) queueParticle(renderable, ix, iy, layer, index, colour, width, height)
		else throw Exception("Unknown renderable type! " + renderable.javaClass)
	}

	// ----------------------------------------------------------------------
	private fun storeRenderSprite(renderSprite: RenderSprite)
	{
		if (queuedSprites == spriteArray.size-1)
		{
			spriteArray = spriteArray.copyOf(spriteArray.size * 2)
			sortedArray = sortedArray.copyOf(sortedArray.size * 2)
		}

		spriteArray[queuedSprites] = renderSprite
		sortedArray[queuedSprites] = renderSprite

		queuedSprites++
	}

	// ----------------------------------------------------------------------
	fun queueParticle(effect: ParticleEffect, ix: Float, iy: Float, layer: Int, index: Int, colour: Colour = Colour.WHITE, width: Float = 1f, height: Float = 1f, lit: Boolean = true)
	{
		if (!inBegin && !inStaticBegin) throw Exception("Queue called before begin!")

		var lx = ix
		var ly = iy

		if (effect.lockPosition)
		{

		}
		else
		{
			if (effect.facing.x != 0)
			{
				lx = ix + effect.size[1].toFloat() * 0.5f
				ly = iy + effect.size[0].toFloat() * 0.5f
			}
			else
			{
				if (effect.isCentered)
				{
					lx = ix + 0.5f
					ly = iy + 0.5f
				}
				else
				{
					lx = ix + effect.size[0].toFloat() * 0.5f
					ly = iy + effect.size[1].toFloat() * 0.5f
				}
			}

			effect.setPosition(lx, ly)
		}

		update(effect)

		if (!effect.visible) return
		if (effect.renderDelay > 0 && !effect.showBeforeRender)
		{
			return
		}

		val posOffset = effect.animation?.renderOffset(false)
		lx += (posOffset?.get(0) ?: 0f)
		ly += (posOffset?.get(1) ?: 0f)

		if (effect.faceInMoveDirection)
		{
			val angle = getRotation(effect.lastPos, tempVec.set(lx, ly))
			effect.rotation = angle
			effect.lastPos.set(lx, ly)
		}

		if (effect.light != null)
		{
			addLight(effect.light!!, lx, ly)
		}

		//val scale = effect.animation?.renderScale()?.get(0) ?: 1f
		val animCol = effect.animation?.renderColour() ?: Colour.WHITE

		for (emitter in effect.emitters)
		{
			val emitterOffset = emitter.keyframe1.offset.lerp(emitter.keyframe2.offset, emitter.keyframeAlpha)

			for (particle in emitter.particles)
			{
				var px = 0f
				var py = 0f

				if (emitter.simulationSpace == Emitter.SimulationSpace.LOCAL)
				{
					tempVec.set(emitterOffset)
					tempVec.scl(emitter.size)
					tempVec.rotate(emitter.rotation)

					px += (emitter.position.x + tempVec.x)
					py += (emitter.position.y + tempVec.y)
				}

				for (pdata in particle.particles)
				{
					val keyframe1 = pdata.keyframe1
					val keyframe2 = pdata.keyframe2
					val alpha = pdata.keyframeAlpha

					val tex1 = keyframe1.texture[pdata.texStream]
					val tex2 = keyframe2.texture[pdata.texStream]

					val col = tempCol.set(keyframe1.colour[pdata.colStream]).lerp(keyframe2.colour[pdata.colStream], alpha)
					col.a = keyframe1.alpha[pdata.alphaStream].lerp(keyframe2.alpha[pdata.alphaStream], alpha)

					val size = keyframe1.size[pdata.sizeStream].lerp(keyframe2.size[pdata.sizeStream], alpha, pdata.ranVal)
					var sizex = size * width
					var sizey = size * height

					if (particle.allowResize)
					{
						sizex *= emitter.size.x
						sizey *= emitter.size.y
					}

					val rotation = if (emitter.simulationSpace == Emitter.SimulationSpace.LOCAL) pdata.rotation + emitter.rotation + emitter.emitterRotation else pdata.rotation

					col.mul(colour).mul(animCol).mul(effect.colour)

					tempVec.set(pdata.position)

					if (emitter.simulationSpace == Emitter.SimulationSpace.LOCAL) tempVec.scl(emitter.size).rotate(emitter.rotation + emitter.emitterRotation)

					val drawx = tempVec.x + px
					val drawy = tempVec.y + py

					val localx = drawx * tileSize + offsetx
					val localy = drawy * tileSize + offsety
					val localw = sizex * tileSize
					val localh = sizey * tileSize

					if (localx + localw < 0 || localx > Global.stage.width || localy + localh < 0 || localy > Global.stage.height) continue

					val comparisonVal = getComparisonVal((drawx-sizex*0.5f).toInt(), (drawy-sizey*0.5f).toInt(), layer, index, particle.blend)

					val rs = RenderSprite.obtain().set( null, null, tex1.second, drawx * tileSize, drawy * tileSize, tempVec.x, tempVec.y, col, sizex, sizey, rotation, 1f, 1f, effect.flipX, effect.flipY, particle.blend, lit, comparisonVal )

					if (particle.blendKeyframes)
					{
						rs.nextTexture = tex2.second
						rs.blendAlpha = alpha
					}

					storeRenderSprite(rs)
				}
			}
		}
	}

	// ----------------------------------------------------------------------
	private fun addToMap(tilingSprite: TilingSprite, ix: Float, iy: Float)
	{
		// Add to map
		val hash = Point.getHashcode(ix.toInt(), iy.toInt())
		var keys = tilingMap[hash]
		if (keys == null)
		{
			keys = setPool.obtain()
			keys.clear()

			tilingMap[hash] = keys
		}
		keys.add(tilingSprite.checkID)
	}

	// ----------------------------------------------------------------------
	fun addLight(light: Light, ix: Float, iy: Float)
	{
		light.pos.set(ix, iy)
		lights.add(light)
	}

	// ----------------------------------------------------------------------
	fun queueSprite(tilingSprite: TilingSprite, ix: Float, iy: Float, layer: Int, index: Int, colour: Colour = Colour.WHITE, width: Float = 1f, height: Float = 1f, lit: Boolean = true)
	{
		if (!inBegin && !inStaticBegin) throw Exception("Queue called before begin!")

		update(tilingSprite)

		if (!tilingSprite.visible) return
		if (tilingSprite.renderDelay > 0 && !tilingSprite.showBeforeRender)
		{
			return
		}

		var lx = ix
		var ly = iy

		var x = ix * tileSize
		var y = iy * tileSize

		if ( tilingSprite.animation != null )
		{
			val offset = tilingSprite.animation?.renderOffset(false)

			if (offset != null)
			{
				x += offset[0] * tileSize
				y += offset[1] * tileSize

				lx += offset[0]
				ly += offset[1]
			}
		}

		addToMap(tilingSprite, ix, iy)

		if (tilingSprite.light != null)
		{
			tilingSprite.light!!.pos.set(lx, ly)
			lights.add(tilingSprite.light!!)
		}

		// check if onscreen
		if (!alwaysOnscreen && !isSpriteOnscreen(tilingSprite, x, y, width, height)) return

		val comparisonVal = getComparisonVal(lx.toInt(), ly.toInt(), layer, index, BlendMode.MULTIPLICATIVE)

		val rs = RenderSprite.obtain().set(null, tilingSprite, null, x, y, ix, iy, colour, width, height, 0f, 1f, 1f, false, false, BlendMode.MULTIPLICATIVE, lit, comparisonVal)

		storeRenderSprite(rs)
	}

	// ----------------------------------------------------------------------
	fun queueSprite(sprite: Sprite, ix: Float, iy: Float, layer: Int, index: Int, colour: Colour = Colour.WHITE, width: Float = 1f, height: Float = 1f, scaleX: Float = 1f, scaleY: Float = 1f, lit: Boolean = true)
	{
		if (!inBegin && !inStaticBegin) throw Exception("Queue called before begin!")

		update(sprite)

		if (!sprite.visible) return
		if (sprite.renderDelay > 0 && !sprite.showBeforeRender)
		{
			return
		}

		var lx = ix
		var ly = iy

		var x = ix * tileSize
		var y = iy * tileSize

		var rotation = 0f

		var lScaleX = sprite.baseScale[0] * scaleX
		var lScaleY = sprite.baseScale[1] * scaleY

		if ( sprite.animation != null )
		{
			val offset = sprite.animation?.renderOffset(false)

			if (offset != null)
			{
				x += offset[0] * tileSize
				y += offset[1] * tileSize

				lx += offset[0]
				ly += offset[1]
			}

			rotation = sprite.animation?.renderRotation() ?: 0f

			val scale = sprite.animation!!.renderScale()
			if (scale != null)
			{
				lScaleX *= scale[0]
				lScaleY *= scale[1]
			}
		}

		if (sprite.drawActualSize)
		{
			val widthRatio = width / 32f
			val regionWidth = sprite.currentTexture.regionWidth.toFloat()
			val trueWidth = regionWidth * widthRatio
			val widthOffset = (trueWidth - width) / 2

			lx -= widthOffset
		}

		lx = lx + 0.5f - (0.5f * lScaleX)
		ly = ly + 0.5f - (0.5f * lScaleY)

		if (sprite.faceInMoveDirection)
		{
			val angle = getRotation(sprite.lastPos, tempVec.set(x, y))
			sprite.rotation = angle
			sprite.lastPos.set(x, y)
		}

		if (sprite.light != null)
		{
			addLight(sprite.light!!, ix, iy)
		}

		// check if onscreen
		if (!alwaysOnscreen && !isSpriteOnscreen(sprite, x, y, width, height, scaleX, scaleY)) return

		val comparisonVal = getComparisonVal(lx.toInt(), ly.toInt(), layer, index, BlendMode.MULTIPLICATIVE)

		val rs = RenderSprite.obtain().set(sprite, null, null, x, y, ix, iy, colour, width, height, rotation, scaleX, scaleY, false, false, BlendMode.MULTIPLICATIVE, lit, comparisonVal)

		storeRenderSprite(rs)
	}

	// ----------------------------------------------------------------------
	fun queueTexture(texture: TextureRegion, ix: Float, iy: Float, layer: Int, index: Int, colour: Colour = Colour.WHITE, width: Float = 1f, height: Float = 1f, scaleX: Float = 1f, scaleY: Float = 1f, sortX: Float? = null, sortY: Float? = null, lit: Boolean = true)
	{
		if (!inBegin && !inStaticBegin) throw Exception("Queue called before begin!")

		val lx = ix - width
		val ly = iy - height

		val x = ix * tileSize
		val y = iy * tileSize

		// check if onscreen

		val localx = x + offsetx
		val localy = y + offsety
		val localw = width * tileSize
		val localh = height * tileSize

		if (localx + localw < 0 || localx > Global.stage.width || localy + localh < 0 || localy > Global.stage.height) return

		val comparisonVal = getComparisonVal((sortX ?: lx).toInt(), (sortY ?: ly).toInt(), layer, index, BlendMode.MULTIPLICATIVE)

		val rs = RenderSprite.obtain().set(null, null, texture, x, y, ix, iy, colour, width, height, 0f, scaleX, scaleY, false, false, BlendMode.MULTIPLICATIVE, lit, comparisonVal)

		storeRenderSprite(rs)
	}

	// ----------------------------------------------------------------------
	private fun isSpriteOnscreen(sprite: Sprite, x: Float, y: Float, width: Float, height: Float, scaleX: Float = 1f, scaleY: Float = 1f): Boolean
	{
		var localx = x + offsetx
		var localy = y + offsety
		var localw = width * tileSize * sprite.size[0]
		var localh = height * tileSize * sprite.size[1]

		var scaleX = sprite.baseScale[0] * scaleX
		var scaleY = sprite.baseScale[1] * scaleY

		if (sprite.animation != null)
		{
			val scale = sprite.animation!!.renderScale()
			if (scale != null)
			{
				scaleX *= scale[0]
				scaleY *= scale[1]
			}
		}

		if (sprite.drawActualSize)
		{
			val texture = sprite.textures.items[sprite.texIndex]

			val widthRatio = localw / 32f
			val heightRatio = localh / 32f

			val regionWidth = sprite.referenceSize ?: texture.regionWidth.toFloat()
			val regionHeight = sprite.referenceSize ?: texture.regionHeight.toFloat()

			val trueWidth = regionWidth * widthRatio
			val trueHeight = regionHeight * heightRatio

			val widthOffset = (trueWidth - localw) / 2f

			localx -= widthOffset
			localw = trueWidth
			localh = trueHeight
		}

		if (sprite.rotation != 0f && sprite.fixPosition)
		{
			val offset = Sprite.getPositionCorrectionOffsets(x, y, localw / 2.0f, localh / 2.0f, localw, localh, scaleX, scaleY, sprite.rotation, tempVec3)
			localx -= offset.x
			localy -= offset.y
		}

		if (scaleX != 1f)
		{
			val newW = localw * scaleX
			val diff = newW - localw

			localx -= diff * 0.5f
			localw = newW
		}
		if (scaleY != 1f)
		{
			val newH = localh * scaleY
			val diff = newH - localh

			localy -= diff * 0.5f
			localh = newH
		}

		if (localx + localw < 0 || localx > Global.stage.width || localy + localh < 0 || localy > Global.stage.height) return false

		return true
	}

	// ----------------------------------------------------------------------
	private fun isSpriteOnscreen(sprite: TilingSprite, x: Float, y: Float, width: Float, height: Float): Boolean
	{
		val localx = x + offsetx
		val localy = y + offsety
		val localw = width * tileSize
		val localh = height * tileSize

		if (localx + localw < 0 || localx > Global.stage.width || localy + localh < 0 || localy > Global.stage.height) return false

		return true
	}

	// ----------------------------------------------------------------------
	companion object
	{
		private val smoothLighting = true
		private val random = LightRNG()

		private const val vertexSize = 4 + 2 + 2 + 2 + 2 + 1
		private const val maxSprites = 10000
		private const val verticesASprite = vertexSize * 4
		private const val maxVertices = maxSprites * vertexSize

		private const val maxShadowPoints = 100

		fun createShader(numLights: Int): ShaderProgram
		{
			val numShadowCastPoints = maxShadowPoints

			val vertexShader = """
attribute vec4 ${ShaderProgram.POSITION_ATTRIBUTE};
attribute vec4 ${ShaderProgram.COLOR_ATTRIBUTE};
attribute vec2 ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
attribute vec2 ${ShaderProgram.TEXCOORD_ATTRIBUTE}1;
attribute vec2 a_spritePos;
attribute float a_blendAlpha;

uniform mat4 u_projTrans;
uniform vec2 u_offset;

varying vec4 v_color;
varying vec2 v_spritePos;
varying vec2 v_pixelPos;
varying vec2 v_texCoords1;
varying vec2 v_texCoords2;
varying float v_blendAlpha;

void main()
{
	v_color = ${ShaderProgram.COLOR_ATTRIBUTE};
	v_color.a = min(v_color.a, 1.0);

	vec4 rawPos = ${ShaderProgram.POSITION_ATTRIBUTE};
	vec2 worldPos = ${ShaderProgram.POSITION_ATTRIBUTE}.xy + u_offset;
	vec4 truePos = vec4(worldPos.x, worldPos.y, rawPos.z, rawPos.w);

	v_pixelPos = worldPos;
	v_spritePos = a_spritePos;
	v_texCoords1 = ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
	v_texCoords2 = ${ShaderProgram.TEXCOORD_ATTRIBUTE}1;
	v_blendAlpha = a_blendAlpha;
	gl_Position = u_projTrans * truePos;
}
"""
			var fragmentShader = """
#ifdef GL_ES
#define LOWP lowp
precision mediump float;
#else
#define LOWP
#endif

varying vec4 v_color;
varying vec2 v_spritePos;
varying vec2 v_pixelPos;
varying vec2 v_texCoords1;
varying vec2 v_texCoords2;
varying float v_blendAlpha;

uniform float u_tileSize;

uniform vec3 u_ambient;
uniform int u_numLights;
uniform vec3 u_lightPosRange[$numLights];
uniform vec4 u_lightColourBrightness[$numLights];

#ifdef SHADOWS
uniform vec3 u_lightShadowData[$numLights];
uniform vec2 u_lightShadowPoints[$numShadowCastPoints];
#endif

uniform sampler2D u_texture;

vec3 calculateLight(int index)
{
	vec3 posRange = u_lightPosRange[index];
	vec4 colourBrightness = u_lightColourBrightness[index];

	vec2 pos = posRange.xy;
	float rangeSq = posRange.z;

	vec2 pixelPos = v_pixelPos;

#ifdef TILELIGHTING
	pixelPos = (floor(v_spritePos / u_tileSize)) * u_tileSize;
#else
	pos += 0.5 * u_tileSize;
#endif

	vec2 diff = pos - pixelPos;
	float distSq = (diff.x * diff.x) + (diff.y * diff.y);
	if (distSq > rangeSq)
	{
		return vec3(0.0, 0.0, 0.0);
	}
	else
	{
#ifdef SHADOWS

		vec3 shadowData = u_lightShadowData[index];
		int numShadowPoints = int(shadowData.x);
		int shadowPointOffset = int(shadowData.y);
		int mode = int(shadowData.z); // -10 = shadow points are the visible ones. 10 = shadow points are the invisible ones

		// A value of 0 means all tiles are visible
		if (numShadowPoints > 0)
		{
			pixelPos = (floor(v_spritePos / u_tileSize)) * u_tileSize;

			bool found = false;
			for (int i = 0; i < numShadowPoints; i++)
			{
				vec2 offset = u_lightShadowPoints[shadowPointOffset+i];
				vec2 visiblePos = posRange.xy + offset * u_tileSize;
				diff = visiblePos - pixelPos;
				float len = (diff.x * diff.x) + (diff.y * diff.y);

				if (len < u_tileSize)
				{
					found = true;
					break;
				}
			}

			if (mode < 0 && !found)
			{
				return vec3(0.0, 0.0, 0.0);
			}
			else if (mode > 0 && found)
			{
				return vec3(0.0, 0.0, 0.0);
			}

		}
		// A value of -1 means no tiles are visible
		else if (numShadowPoints == -1)
		{
			return vec3(0.0, 0.0, 0.0);
		}

#endif

		float alpha = 1.0 - (distSq / rangeSq);

		vec3 lightCol = colourBrightness.rgb;
		float brightness = colourBrightness.a;

		return lightCol * brightness * alpha;
	}
}

void main()
{
	vec4 col1 = texture2D(u_texture, v_texCoords1);
	vec4 col2 = texture2D(u_texture, v_texCoords2);

	vec4 outCol = mix(col1, col2, v_blendAlpha);

	vec3 lightCol = u_ambient;
	for (int i = 0; i < u_numLights; i++)
	{
		lightCol += calculateLight(i);
	}

	vec4 finalCol = clamp(v_color * outCol * vec4(lightCol, 1.0), 0.0, 1.0);
	gl_FragColor = finalCol;
}
"""
			if (!smoothLighting)
			{
				fragmentShader = "#define TILELIGHTING 1\n$fragmentShader"
			}

			if (Global.collisionGrid != null)
			{
				fragmentShader = "#define SHADOWS 1\n$fragmentShader"
			}

			val shader = ShaderProgram(vertexShader, fragmentShader)
			if (!shader.isCompiled) throw IllegalArgumentException("Error compiling shader: " + shader.log)
			return shader
		}
	}
}

// ----------------------------------------------------------------------
class RenderSprite(val parentBlock: RenderSpriteBlock, val parentBlockIndex: Int) : Comparable<RenderSprite>
{
	internal var px: Int = 0
	internal var py: Int = 0
	internal val colour: Colour = Colour(1f, 1f, 1f, 1f)
	internal var sprite: Sprite? = null
	internal var tilingSprite: TilingSprite? = null
	internal var texture: TextureRegion? = null
	internal var nextTexture: TextureRegion? = null
	internal var blendAlpha = 0f
	internal var x: Float = 0f
	internal var y: Float = 0f
	internal var width: Float = 1f
	internal var height: Float = 1f
	internal var rotation: Float = 0f
	internal var scaleX: Float = 1f
	internal var scaleY: Float = 1f
	internal var flipX: Boolean = false
	internal var flipY: Boolean = false
	internal var blend: BlendMode = BlendMode.MULTIPLICATIVE
	internal var isLit: Boolean = true

	val tempColour = Colour()
	val tlCol = Colour()
	val trCol = Colour()
	val blCol = Colour()
	val brCol = Colour()

	internal var comparisonVal: Int = 0

	// ----------------------------------------------------------------------
	operator fun set(sprite: Sprite?, tilingSprite: TilingSprite?, texture: TextureRegion?,
					 x: Float, y: Float,
					 ix: Float, iy: Float,
					 colour: Colour,
					 width: Float, height: Float,
					 rotation: Float,
					 scaleX: Float, scaleY: Float,
					 flipX: Boolean, flipY: Boolean,
					 blend: BlendMode, lit: Boolean,
					 comparisonVal: Int): RenderSprite
	{
		this.px = ix.toInt()
		this.py = iy.toInt()
		this.colour.set(colour)
		this.sprite = sprite
		this.tilingSprite = tilingSprite
		this.texture = texture
		this.x = x
		this.y = y
		this.width = width
		this.height = height
		this.comparisonVal = comparisonVal
		this.blend = blend
		this.rotation = rotation
		this.scaleX = scaleX
		this.scaleY = scaleY
		this.flipX = flipX
		this.flipY = flipY
		this.isLit = lit

		nextTexture = null

		return this
	}

	// ----------------------------------------------------------------------
	override fun compareTo(other: RenderSprite): Int
	{
		return comparisonVal.compareTo(other.comparisonVal)
	}

	// ----------------------------------------------------------------------
	internal fun free() = parentBlock.free(this)

	// ----------------------------------------------------------------------
	companion object
	{
		private var currentBlock: RenderSpriteBlock = RenderSpriteBlock.obtain()

		internal fun obtain(): RenderSprite
		{
			val rs = currentBlock.obtain()

			if (currentBlock.full())
			{
				currentBlock = RenderSpriteBlock.obtain()
			}

			return rs
		}
	}
}

// ----------------------------------------------------------------------
class RenderSpriteBlock
{
	private var count = 0
	private var index: Int = 0
	private val sprites = Array(blockSize) { RenderSprite(this, it) }

	internal inline fun full() = index == blockSize

	internal fun obtain(): RenderSprite
	{
		val sprite = sprites[index]
		index++
		count++

		return sprite
	}

	internal fun free(data: RenderSprite)
	{
		count--

		if (count == 0 && index == blockSize)
		{
			pool.free(this)
			index = 0
		}
	}

	companion object
	{
		public const val blockSize: Int = 128

		fun obtain(): RenderSpriteBlock
		{
			val block = pool.obtain()
			return block
		}

		private val pool: Pool<RenderSpriteBlock> = object : Pool<RenderSpriteBlock>() {
			override fun newObject(): RenderSpriteBlock
			{
				return RenderSpriteBlock()
			}
		}
	}
}

// ----------------------------------------------------------------------
class RadixSort
{
	companion object
	{
		/**
		 * The byte index of the most significant byte in each 32-bit integer.
		 */
		public const val MOST_SIGNIFICANT_BYTE_INDEX = 3

		/**
		 * The mask for manipulating the sign bit.
		 */
		private const val SIGN_BIT_MASK = -0x80000000

		/**
		 * The amount of bits per byte.
		 */
		private const val BITS_PER_BYTE = 8

		/**
		 * The mask for extracting the bucket index.
		 */
		private const val EXTRACT_BYTE_MASK = 0xff

		/**
		 * The amount of buckets considered for sorting.
		 */
		private const val BUCKET_AMOUNT = 256

		private const val QUICKSORT_THRESHOLD = 128

		private val bucketPool: Pool<IntArray> = object : Pool<IntArray>() {
			override fun newObject(): IntArray
			{
				return IntArray(BUCKET_AMOUNT)
			}
		}

		public fun sort(
				source: Array<RenderSprite?>, target: Array<RenderSprite?>,
				sourceOffset: Int, targetOffset: Int, rangeLength: Int,
				byteIndex: Int)
		{
			if (rangeLength < QUICKSORT_THRESHOLD)
			{
				source.sort(sourceOffset, sourceOffset + rangeLength)

				if (byteIndex and 1 == 0)
				{
					System.arraycopy(source, sourceOffset, target, targetOffset, rangeLength)
				}

				return
			}

			val bucketSizeMap = bucketPool.obtain()
			for (i in 0 until BUCKET_AMOUNT)
			{
				bucketSizeMap[i] = 0
			}

			// Count the size of each bucket.
			for (i in sourceOffset until sourceOffset + rangeLength)
			{
				bucketSizeMap[getBucketIndex(source[i]!!.comparisonVal, byteIndex)]++
			}

			// Compute the map mapping each bucket to its beginning index.
			val startIndexMap = bucketPool.obtain()
			startIndexMap[0] = 0

			for (i in 1 until BUCKET_AMOUNT)
			{
				startIndexMap[i] = startIndexMap[i - 1] + bucketSizeMap[i - 1]
			}

			// The map mapping each bucket index to amount of elements already put
			// in the bucket.
			val processedMap = bucketPool.obtain()
			for (i in 0 until BUCKET_AMOUNT)
			{
				processedMap[i] = 0
			}

			for (i in sourceOffset until sourceOffset + rangeLength)
			{
				val element = source[i]
				val bucket = getBucketIndex(element!!.comparisonVal, byteIndex)
				target[targetOffset + startIndexMap[bucket] +
					   processedMap[bucket]++] = element
			}

			if (byteIndex > 0)
			{
				// Recursively sort the buckets.
				for (i in 0 until BUCKET_AMOUNT)
				{
					if (bucketSizeMap[i] != 0)
					{
						sort(target,
							 source,
							 targetOffset + startIndexMap[i],
							 sourceOffset + startIndexMap[i],
							 bucketSizeMap[i],
							 byteIndex - 1)
					}
				}
			}

			bucketPool.free(bucketSizeMap)
			bucketPool.free(startIndexMap)
			bucketPool.free(processedMap)
		}

		/**
		 * Returns the bucket index for `element` when considering
		 * `byteIndex`th byte within the element. The indexing starts from
		 * the least significant bytes.
		 *
		 * @param element   the element for which to compute the bucket index.
		 * @param byteIndex the index of the byte to be considered.
		 * @return the bucket index.
		 */
		private fun getBucketIndex(element: Int, byteIndex: Int): Int
		{
			var result = element
			if (byteIndex == MOST_SIGNIFICANT_BYTE_INDEX)
			{
				result = result xor SIGN_BIT_MASK
			}
			return result.ushr(byteIndex * BITS_PER_BYTE) and EXTRACT_BYTE_MASK
		}
	}
}