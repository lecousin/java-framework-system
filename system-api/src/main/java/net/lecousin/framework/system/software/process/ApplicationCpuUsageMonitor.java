package net.lecousin.framework.system.software.process;

import net.lecousin.framework.concurrent.CancelException;
import net.lecousin.framework.concurrent.Executable;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.system.LCSystem;

public class ApplicationCpuUsageMonitor {

	ApplicationCpuUsageMonitor() {
		Task<Void,NoException> task = Task.cpu("Checking idle time of the application", Task.Priority.BACKGROUND, new Check());
		task.executeEvery(60000, 60000);
		task.start();
	}
	
	private class Check implements Executable<Void, NoException> {
		
		private long lastCheckTime = -1;
		private long lastCPUTime;
		private int processId;
		private double[] usageLast10Minutes = new double[10];
		private int minute = 0;
		
		@Override
		public Void execute(Task<Void, NoException> taskContext) throws NoException, CancelException {
			Processes processes = LCSystem.get().getSoftware().getProcesses();
			if (lastCheckTime < 0) {
				// first time we execute
				if (LCSystem.log.debug())
					LCSystem.log.debug("Start monitoring CPU usage");
				lastCheckTime = System.nanoTime();
				processId = processes.getCurrentProcessId();
				lastCPUTime = processes.getProcessCPUTimeNano(processId);
				return null;
			}
			long now = System.nanoTime();
			long cpu = processes.getProcessCPUTimeNano(processId);
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
		
	}
	
}
