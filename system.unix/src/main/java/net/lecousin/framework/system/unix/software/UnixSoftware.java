package net.lecousin.framework.system.unix.software;

import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.unix.software.process.ProcessesUnix;

public class UnixSoftware implements Software {

	private ProcessesUnix processes;
	
	public UnixSoftware() {
		processes = new ProcessesUnix();
	}
	
	@Override
	public ProcessesUnix getProcesses() {
		return processes;
	}
	
}
