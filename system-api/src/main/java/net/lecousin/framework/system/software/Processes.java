package net.lecousin.framework.system.software;

import java.util.List;

import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.system.LCSystem;

/**
 * Information from operating system about running processes.
 */
public abstract class Processes {

	private static Processes instance = null;
	
	public static Processes getInstance() { return instance; }
	
	/** Set the implementation. */
	public static void setInstance(Processes processes) throws IllegalStateException {
		synchronized (Processes.class) {
			if (instance != null) throw new IllegalStateException();
			instance = processes;

			Task<Void,NoException> task = Task.cpu("Checking idle time of the application", Task.Priority.BACKGROUND,
			new Executable<Void, NoException>() {
				private long lastCheckTime = -1;
				private long lastCPUTime;
				private int processId;
				private double[] usageLast10Minutes = new double[10];
				private int minute = 0;
				@Override
				public Void execute(Task<Void, NoException> taskContext) {
					if (lastCheckTime < 0) {
						// first time we execute
						if (LCSystem.log.debug())
							LCSystem.log.debug("Start monitoring CPU usage");
						lastCheckTime = System.nanoTime();
						processId = instance.getCurrentProcessId();
						lastCPUTime = instance.getProcessCPUTimeNano(processId);
						return null;
					}
					long now = System.nanoTime();
					long cpu = instance.getProcessCPUTimeNano(processId);
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
					// TODO make the information available, not just logging
					return null;
				}
			});
			task.executeEvery(60000, 60000);
			task.start();
		}
	}

	/** Return the id of the current process. */
	public abstract int getCurrentProcessId();
	
	/** Return a list of process ids. */
	public abstract List<Integer> listProcessesIds();
	
	/** Terminate the given process. */
	public abstract void killProcess(int id) throws Exception;
	
	/** Return the CPU time consumed by the given process. */
	public abstract long getProcessCPUTimeNano(int id);
	
	/** Launch the given command, optionally with elevated privileges. */
	public abstract SeparateProcess executeCommand(String[] command, boolean elevatedPrivileges) throws Exception;
	
	/** Interface to manage separated process. */
	public interface SeparateProcess {
		/** Return the exit code, or throws IllegalThreadStateException if the process is not yet terminated. */
		int getExitCode() throws IllegalThreadStateException;
		
		/** Kill the process. */
		void kill();
		
		/** Call the listener when the process terminates. */
		void addListener(Runnable listener);
	}
	
}
