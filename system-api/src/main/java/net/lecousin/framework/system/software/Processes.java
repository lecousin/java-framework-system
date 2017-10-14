package net.lecousin.framework.system.software;

import java.util.List;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.system.LCSystem;

/**
 * Information from operating system about running processes.
 */
public abstract class Processes {

	public static Processes instance = null;

	/** Return the id of the current process. */
	public abstract int getCurrentProcessId();
	
	/** Return a list of process ids. */
	public abstract List<Integer> listProcessesIds();
	
	/** Terminate the given process. */
	public abstract void killProcess(int id) throws Exception;
	
	/** Return the CPU time consumed by the given process. */
	public abstract long getProccessCPUTimeNano(int id);
	
	static {
		Task<Void,NoException> task = new Task.Cpu<Void,NoException>("Checking idle time of the application", Task.PRIORITY_BACKGROUND) {
			private long lastCheckTime = -1;
			private long lastCPUTime;
			private int processId;
			private double[] usageLast10Minutes = new double[10];
			private int minute = 0;
			@Override
			public Void run() {
				if (lastCheckTime < 0) {
					// first time we execute
					if (LCSystem.log.debug())
						LCSystem.log.debug("Start monitoring CPU usage");
					lastCheckTime = System.nanoTime();
					processId = instance.getCurrentProcessId();
					lastCPUTime = instance.getProccessCPUTimeNano(processId);
					return null;
				}
				long now = System.nanoTime();
				long cpu = instance.getProccessCPUTimeNano(processId);
				long used = cpu - lastCPUTime;
				used /= Runtime.getRuntime().availableProcessors();
				long time = now - lastCheckTime;
				double usage = used * 100.d / time;
				lastCheckTime = now;
				lastCPUTime = cpu;
				
				if (LCSystem.log.debug())
					LCSystem.log.debug(String.format("%.2f",
						new Double(usage)) + "% of CPU used by this application over the last minute");
				if (minute < 10)
					usageLast10Minutes[minute++] = usage;
				else {
					System.arraycopy(usageLast10Minutes, 1, usageLast10Minutes, 0, 9);
					usageLast10Minutes[9] = usage;
				}
				// TODO
				return null;
			}
		};
		task.executeEvery(60000, 60000);
		task.start();
	}
	
}
