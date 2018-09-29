package net.lecousin.framework.system.unix.software;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.lecousin.framework.system.software.Processes;
import net.lecousin.framework.system.unix.jna.LibC;

/**
 * Implementation of Processes for Unix systems.
 *
 */
public class ProcessesUnix extends Processes {

	@Override
	public int getCurrentProcessId() {
		return LibC.INSTANCE.getpid();
	}

	private static final Pattern DIGITS = Pattern.compile("\\d+");
			
	@Override
	public List<Integer> listProcessesIds() {
		File dir = new File("/proc");
		File[] files = null;
		if (dir.exists() && dir.isDirectory()) files = dir.listFiles();
		if (files != null) {
			ArrayList<Integer> list = new ArrayList<>(files.length);
			for (File f : files) {
				String name = f.getName();
				if (!DIGITS.matcher(name).matches()) continue;
				list.add(Integer.valueOf(Integer.parseInt(name)));
			}
			return list;
		}
		return new ArrayList<>(0);
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
		// TODO Auto-generated method stub
		return null;
	}

}
