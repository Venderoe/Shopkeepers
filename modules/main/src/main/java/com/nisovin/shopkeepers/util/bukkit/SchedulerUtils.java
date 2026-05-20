package com.nisovin.shopkeepers.util.bukkit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.nisovin.shopkeepers.util.java.Validate;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Scheduler related utilities.
 */
public final class SchedulerUtils {

	private static final long MILLIS_PER_TICK = 50L;
	private static final AtomicInteger activeAsyncTasks = new AtomicInteger();

	/**
	 * Creates an {@link Executor} that executes tasks on the server's main thread using
	 * {@link #runOnMainThreadOrOmit(Plugin, Runnable)}.
	 * <p>
	 * If the thread registering the task is already the server's main thread, the task is run
	 * immediately. Otherwise, it is scheduled using the global region scheduler. If the plugin is
	 * not enabled at the time of task registration, the task is omitted.
	 * 
	 * @param plugin
	 *            the plugin
	 * @return the executor
	 */
	public static Executor createSyncExecutor(Plugin plugin) {
		return (runnable) -> runOnMainThreadOrOmit(plugin, runnable);
	}

	/**
	 * Creates an {@link Executor} that executes tasks using
	 * {@link #runAsyncTaskOrOmit(Plugin, Runnable)}.
	 * 
	 * @param plugin
	 *            the plugin
	 * @return the executor
	 */
	public static Executor createAsyncExecutor(Plugin plugin) {
		return (runnable) -> runAsyncTaskOrOmit(plugin, runnable);
	}

	public static int getActiveAsyncTasks(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		return activeAsyncTasks.get();
	}

	private static void validatePluginTask(Plugin plugin, Runnable task) {
		Validate.notNull(plugin, "plugin is null");
		Validate.notNull(task, "task is null");
	}

	/**
	 * Checks if the current thread is the server's main thread.
	 * 
	 * @return <code>true</code> if currently running on the main thread
	 */
	public static boolean isMainThread() {
		return Bukkit.isPrimaryThread() || Bukkit.isGlobalTickThread();
	}

	/**
	 * Schedules the given task to be run on the primary thread if required.
	 * <p>
	 * If the current thread is already the primary thread, the task will be run immediately.
	 * Otherwise, it attempts to schedule the task to run on the server's primary thread. However,
	 * if the plugin is disabled, the task won't be scheduled.
	 * 
	 * @param plugin
	 *            the plugin to use for scheduling, not <code>null</code>
	 * @param task
	 *            the task, not <code>null</code>
	 * @return <code>true</code> if the task was run or successfully scheduled to be run,
	 *         <code>false</code> otherwise
	 */
	public static boolean runOnMainThreadOrOmit(Plugin plugin, Runnable task) {
		validatePluginTask(plugin, task);
		if (isMainThread()) {
			task.run();
			return true;
		} else {
			return (runTaskOrOmit(plugin, task) != null);
		}
	}

	public static @Nullable TaskHandle runTaskOrOmit(Plugin plugin, Runnable task) {
		return runTaskLaterOrOmit(plugin, task, 0L);
	}

	public static @Nullable TaskHandle runTaskLaterOrOmit(
			Plugin plugin,
			Runnable task,
			long delay
	) {
		return runGlobalTaskLaterOrOmit(plugin, task, delay);
	}

	public static @Nullable TaskHandle runGlobalTaskOrOmit(Plugin plugin, Runnable task) {
		return runGlobalTaskLaterOrOmit(plugin, task, 0L);
	}

	public static @Nullable TaskHandle runGlobalTaskLaterOrOmit(
			Plugin plugin,
			Runnable task,
			long delay
	) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runDelayed(
						plugin,
						ignored -> task.run(),
						Math.max(1L, delay)
				);
				return new TaskHandle(scheduledTask);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable TaskHandle runGlobalTaskTimerOrOmit(
			Plugin plugin,
			Runnable task,
			long initialDelay,
			long period
	) {
		validatePluginTask(plugin, task);
		Validate.isTrue(period > 0L, "period has to be positive");
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
						plugin,
						ignored -> task.run(),
						Math.max(1L, initialDelay),
						period
				);
				return new TaskHandle(scheduledTask);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable TaskHandle runRegionTaskOrOmit(
			Plugin plugin,
			Location location,
			Runnable task
	) {
		return runRegionTaskLaterOrOmit(plugin, location, task, 0L);
	}

	public static @Nullable TaskHandle runRegionTaskLaterOrOmit(
			Plugin plugin,
			Location location,
			Runnable task,
			long delay
	) {
		validatePluginTask(plugin, task);
		Validate.notNull(location, "location is null");
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runDelayed(
						plugin,
						location,
						ignored -> task.run(),
						Math.max(1L, delay)
				);
				return new TaskHandle(scheduledTask);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable TaskHandle runRegionTaskTimerOrOmit(
			Plugin plugin,
			Location location,
			Runnable task,
			long initialDelay,
			long period
	) {
		validatePluginTask(plugin, task);
		Validate.notNull(location, "location is null");
		Validate.isTrue(period > 0L, "period has to be positive");
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(
						plugin,
						location,
						ignored -> task.run(),
						Math.max(1L, initialDelay),
						period
				);
				return new TaskHandle(scheduledTask);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable TaskHandle runEntityTaskOrOmit(
			Plugin plugin,
			Entity entity,
			Runnable task
	) {
		return runEntityTaskLaterOrOmit(plugin, entity, task, 0L);
	}

	public static @Nullable TaskHandle runEntityTaskLaterOrOmit(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long delay
	) {
		validatePluginTask(plugin, task);
		Validate.notNull(entity, "entity is null");
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = entity.getScheduler().runDelayed(
						plugin,
						ignored -> task.run(),
						null,
						Math.max(1L, delay)
				);
				return (scheduledTask != null) ? new TaskHandle(scheduledTask) : null;
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable TaskHandle runEntityTaskTimerOrOmit(
			Plugin plugin,
			Entity entity,
			Runnable task,
			long initialDelay,
			long period
	) {
		validatePluginTask(plugin, task);
		Validate.notNull(entity, "entity is null");
		Validate.isTrue(period > 0L, "period has to be positive");
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = entity.getScheduler().runAtFixedRate(
						plugin,
						ignored -> task.run(),
						null,
						Math.max(1L, initialDelay),
						period
				);
				return (scheduledTask != null) ? new TaskHandle(scheduledTask) : null;
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable TaskHandle runAsyncTaskOrOmit(Plugin plugin, Runnable task) {
		return runAsyncTaskLaterOrOmit(plugin, task, 0L);
	}

	public static @Nullable TaskHandle runAsyncTaskLaterOrOmit(
			Plugin plugin,
			Runnable task,
			long delay
	) {
		validatePluginTask(plugin, task);
		// Tasks can only be registered while enabled:
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runDelayed(
						plugin,
						ignored -> {
							activeAsyncTasks.incrementAndGet();
							try {
								task.run();
							} finally {
								activeAsyncTasks.decrementAndGet();
							}
						},
						ticksToMillis(delay),
						TimeUnit.MILLISECONDS
				);
				return new TaskHandle(scheduledTask);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static @Nullable TaskHandle runAsyncTaskTimerOrOmit(
			Plugin plugin,
			Runnable task,
			long initialDelay,
			long period
	) {
		validatePluginTask(plugin, task);
		Validate.isTrue(period > 0L, "period has to be positive");
		if (plugin.isEnabled()) {
			try {
				ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(
						plugin,
						ignored -> {
							activeAsyncTasks.incrementAndGet();
							try {
								task.run();
							} finally {
								activeAsyncTasks.decrementAndGet();
							}
						},
						ticksToMillis(initialDelay),
						ticksToMillis(period),
						TimeUnit.MILLISECONDS
				);
				return new TaskHandle(scheduledTask);
			} catch (IllegalPluginAccessException e) {
				// Couldn't register task: The plugin got disabled just now.
			}
		}
		return null;
	}

	public static void cancelGlobalAndAsyncTasks(Plugin plugin) {
		Validate.notNull(plugin, "plugin is null");
		Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
		Bukkit.getAsyncScheduler().cancelTasks(plugin);
	}

	private static long ticksToMillis(long ticks) {
		return Math.max(0L, ticks) * MILLIS_PER_TICK;
	}

	/**
	 * Awaits the completion of async tasks of the specified plugin.
	 * <p>
	 * If a logger is specified, it will be used to print informational messages suited to the
	 * context of this method being called during disabling of the plugin.
	 * 
	 * @param plugin
	 *            the plugin
	 * @param asyncTasksTimeoutSeconds
	 *            the duration to wait for async tasks to finish in seconds (can be <code>0</code>)
	 * @param logger
	 *            the logger used for printing informational messages, can be <code>null</code>
	 * @return the number of remaining async tasks that are still running after waiting for the
	 *         specified duration
	 */
	public static int awaitAsyncTasksCompletion(
			Plugin plugin,
			int asyncTasksTimeoutSeconds,
			@Nullable Logger logger
	) {
		Validate.notNull(plugin, "plugin is null");
		Validate.isTrue(asyncTasksTimeoutSeconds >= 0, "asyncTasksTimeoutSeconds cannot be negative");

		int activeAsyncTasks = getActiveAsyncTasks(plugin);
		if (activeAsyncTasks > 0 && asyncTasksTimeoutSeconds > 0) {
			if (logger != null) {
				logger.info("Waiting up to " + asyncTasksTimeoutSeconds + " seconds for "
						+ activeAsyncTasks + " remaining async tasks to finish ...");
			}

			final long asyncTasksTimeoutMillis = TimeUnit.SECONDS.toMillis(asyncTasksTimeoutSeconds);
			final long waitStartNanos = System.nanoTime();
			long waitDurationMillis = 0L;
			do {
				// Periodically check again:
				try {
					Thread.sleep(25L);
				} catch (InterruptedException e) {
					// Ignore, but reset interrupt flag:
					Thread.currentThread().interrupt();
				}
				// Update the number of active async task before breaking from loop:
				activeAsyncTasks = getActiveAsyncTasks(plugin);

				// Update waiting duration and compare to timeout:
				waitDurationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStartNanos);
				if (waitDurationMillis > asyncTasksTimeoutMillis) {
					// Timeout reached, abort waiting..
					break;
				}
			} while (activeAsyncTasks > 0);

			if (waitDurationMillis > 1 && logger != null) {
				logger.info("Waited " + waitDurationMillis + " ms for async tasks to finish.");
			}
		}

		if (activeAsyncTasks > 0 && logger != null) {
			// Severe, since this can potentially result in data loss, depending on what the tasks
			// are doing:
			logger.severe("There are still " + activeAsyncTasks
					+ " remaining async tasks active! Disabling anyway now.");
		}
		return activeAsyncTasks;
	}

	private SchedulerUtils() {
	}
}
