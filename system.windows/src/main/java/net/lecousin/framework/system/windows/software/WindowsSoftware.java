package net.lecousin.framework.system.windows.software;

import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.software.process.Processes;
import net.lecousin.framework.system.windows.software.process.WindowsProcesses;

public class WindowsSoftware implements Software {

	private WindowsProcesses processes = new WindowsProcesses();
	
	@Override
	public Processes getProcesses() {
		return processes;
	}
	
}
