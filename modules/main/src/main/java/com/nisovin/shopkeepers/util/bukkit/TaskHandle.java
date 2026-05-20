package com.nisovin.shopkeepers.util.bukkit;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Handle for a task scheduled through Paper's Folia-aware schedulers.
 */
public final class TaskHandle {

	private final ScheduledTask scheduledTask;

	TaskHandle(ScheduledTask scheduledTask) {
		assert scheduledTask != null;
		this.scheduledTask = scheduledTask;
	}

	public void cancel() {
		scheduledTask.cancel();
	}

	public boolean isCancelled() {
		return scheduledTask.isCancelled();
	}
}
