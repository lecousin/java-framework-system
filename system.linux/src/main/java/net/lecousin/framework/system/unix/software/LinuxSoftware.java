package net.lecousin.framework.system.unix.software;

import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.unix.software.process.LinuxProcesses;

public class LinuxSoftware implements Software {

	private LinuxProcesses processes;
	
	public LinuxSoftware() {
		processes = new LinuxProcesses();
	}
	
	@Override
	public LinuxProcesses getProcesses() {
		return processes;
	}
	
}
