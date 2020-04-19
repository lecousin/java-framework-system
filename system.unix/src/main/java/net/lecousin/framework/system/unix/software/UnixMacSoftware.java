package net.lecousin.framework.system.unix.software;

import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.software.process.Processes;
import net.lecousin.framework.system.unix.software.process.ProcessesUnix;

public class UnixMacSoftware implements Software {

	private ProcessesUnix processes = new ProcessesUnix();
	
	@Override
	public Processes getProcesses() {
		return processes;
	}
	
}
