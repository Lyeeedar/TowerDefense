package com.lyeeedar.Util

import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.async.ThreadUtils.yield

// ----------------------------------------------------------------------
class ThreadpoolJob(val parentBlock: ThreadpoolJobBlock)
{
	private lateinit var task: () -> Unit

	private var completed = false

	fun setTask(task: () -> Unit)
	{
		this.task = task
		this.completed = false
	}

	fun await()
	{
		try
		{
			while (!completed)
			{
				yield()
			}
		}
		catch (ex: Exception) {}
	}

	fun execute()
	{
		task.invoke()

		completed = true
	}

	fun free()
	{
		parentBlock.free(this)
	}

	companion object
	{
		private var currentBlock: ThreadpoolJobBlock = ThreadpoolJobBlock.obtain()

		internal fun obtain(): ThreadpoolJob
		{
			val item = currentBlock.obtain()

			if (currentBlock.full())
			{
				currentBlock = ThreadpoolJobBlock.obtain()
			}

			return item
		}
	}
}

// ----------------------------------------------------------------------
class ThreadpoolJobBlock
{
	private var count = 0
	private var index: Int = 0
	private val items = Array(blockSize) { ThreadpoolJob(this) }

	internal inline fun full() = index == blockSize

	internal fun obtain(): ThreadpoolJob
	{
		val item = items[index]
		index++
		count++

		return item
	}

	internal fun free(data: ThreadpoolJob)
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
		public const val blockSize: Int = 64

		fun obtain(): ThreadpoolJobBlock
		{
			val block = pool.obtain()
			return block
		}

		private val pool: Pool<ThreadpoolJobBlock> = object : Pool<ThreadpoolJobBlock>() {
			override fun newObject(): ThreadpoolJobBlock
			{
				return ThreadpoolJobBlock()
			}
		}
	}
}

// ----------------------------------------------------------------------
class ThreadPoolThread(val index: Int)
{
	private val taskQueue = com.badlogic.gdx.utils.Array<ThreadpoolJob>(false, 100)
	private val thread: Thread

	init
	{
		thread = object : Thread() {
			override fun run() {
				while (true)
				{
					if (taskQueue.size == 0) yield()

					var job: ThreadpoolJob? = null
					synchronized(taskQueue)
					{
						if (taskQueue.size > 0)
						{
							job = taskQueue.removeIndex(taskQueue.size - 1)
						}
					}

					job?.execute()
				}
			}
		}
		thread.name = "LightweightThreadPool $index"
		thread.start()
	}

	fun addTask(job: ThreadpoolJob)
	{
		synchronized(taskQueue)
		{
			taskQueue.add(job)
		}
	}
}

// ----------------------------------------------------------------------
/**
 * Assumes all tasks are roughly equal cost to compute
 * Assumes all tasks will be enqueued from a single thread
 */
// ----------------------------------------------------------------------
class LightweightThreadpool(val numThreads: Int)
{
	private var queueIndex = 0
	private val threads: kotlin.Array<ThreadPoolThread> = kotlin.Array<ThreadPoolThread>(numThreads) { i -> ThreadPoolThread(i) }

	fun addTask(task: ()->Unit): ThreadpoolJob
	{
		val job = ThreadpoolJob.obtain()
		job.setTask(task)

		threads[queueIndex].addTask(job)

		queueIndex = (queueIndex + 1).rem(numThreads)

		return job
	}
}