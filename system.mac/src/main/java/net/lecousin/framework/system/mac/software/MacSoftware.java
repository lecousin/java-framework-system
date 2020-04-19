package net.lecousin.framework.system.mac.software;

import net.lecousin.framework.system.mac.software.process.ProcessesMac;
import net.lecousin.framework.system.software.Software;

public class MacSoftware implements Software {

	private ProcessesMac processes;
	
	public MacSoftware() {
		processes = new ProcessesMac();
	}
	
	@Override
	public ProcessesMac getProcesses() {
		return processes;
	}
	
}
