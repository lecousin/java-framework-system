package net.lecousin.framework.system.software.process;

import java.util.List;

/**
 * Information from operating system about running processes.
 */
public abstract class Processes {
	
	protected ApplicationCpuUsageMonitor appCpuMonitor = new ApplicationCpuUsageMonitor();

	/** Return the id of the current process. */
	public abstract int getCurrentProcessId();
	
	/** Return a list of process ids. */
	public abstract List<Integer> listProcessesIds();
	
	/** Terminate the given process. */
	public abstract void killProcess(int id) throws Exception;
	
	/** Return the CPU time consumed by the given process. */
	public abstract long getProcessCPUTimeNano(int id);
	
	public final ApplicationCpuUsageMonitor getApplicationCpuUsageMonitor() {
		return appCpuMonitor;
	}
	
	/** Launch the given command, optionally with elevated privileges. */
	public abstract SeparateProcess executeCommand(String[] command, boolean elevatedPrivileges) throws Exception;
	
}
