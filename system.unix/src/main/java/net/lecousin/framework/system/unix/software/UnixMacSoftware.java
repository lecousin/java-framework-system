package net.lecousin.framework.system.unix.software;

import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.software.process.Processes;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.software.process.ProcessesMac;
import net.lecousin.framework.system.unix.software.process.ProcessesUnix;

public class UnixMacSoftware implements Software {

	private Processes processes;
	
	public UnixMacSoftware() {
		if (JnaInstances.systemB != null)
			processes = new ProcessesMac();
		else
			processes = new ProcessesUnix();
	}
	
	@Override
	public Processes getProcesses() {
		return processes;
	}
	
}
