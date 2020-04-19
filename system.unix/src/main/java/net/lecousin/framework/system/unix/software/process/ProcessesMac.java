package net.lecousin.framework.system.unix.software.process;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.platform.mac.SystemB;

import net.lecousin.framework.system.software.process.Processes;
import net.lecousin.framework.system.software.process.SeparateProcess;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.LibC;

/**
 * Implementation of Processes for Unix systems.
 *
 */
public class ProcessesMac extends Processes {

	@Override
	public int getCurrentProcessId() {
		return LibC.INSTANCE.getpid();
	}

	@Override
	public List<Integer> listProcessesIds() {
		int[] pids = new int[1024];
		int nb = JnaInstances.systemB.proc_listpids(SystemB.PROC_ALL_PIDS, 0, pids, pids.length * SystemB.INT_SIZE) / SystemB.INT_SIZE;
		ArrayList<Integer> list = new ArrayList<>(nb);
		for (int i = 0; i < nb; ++i)
			list.add(Integer.valueOf(pids[i]));
		return list;
	}

	@Override
	public void killProcess(int id) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long getProcessCPUTimeNano(int id) {
		// TODO
		return 0;
	}
	
	@Override
	public SeparateProcess executeCommand(String[] command, boolean elevated) throws Exception {
		throw new Exception("Not yet implemented");
	}

}
