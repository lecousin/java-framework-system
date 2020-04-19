package net.lecousin.framework.system.windows.software;

import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.software.process.Processes;
import net.lecousin.framework.system.windows.software.process.ProcessesWin;

public class WindowsSoftware implements Software {

	private ProcessesWin processes = new ProcessesWin();
	
	@Override
	public Processes getProcesses() {
		return processes;
	}
	
}
