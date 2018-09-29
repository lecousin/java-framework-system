package net.lecousin.framework.system.unix.software;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.event.SimpleEvent;
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
	
	private static class SeparateProcess implements Processes.SeparateProcess, Closeable {
		private SeparateProcess(Process process, String command) {
			this.p = process;
			Application app = LCCore.getApplication();
			thread = app.getThreadFactory().newThread(new WaitFor());
			thread.setName("Wait for process to terminate: " + command);
			thread.start();
			app.toClose(this);
		}
		
		private Process p;
		private Thread thread;
		private Integer exitCode = null;
		private SimpleEvent terminated = new SimpleEvent();
		
		private class WaitFor implements Runnable {
			@Override
			public void run() {
				try {
					SeparateProcess.this.exitCode = p.waitFor();
					terminated.fire();
				} catch (InterruptedException e) {
				} finally {
					if (p.isAlive()) p.destroyForcibly();
					LCCore.getApplication().closed(SeparateProcess.this);
				}
			}
		}
		
		@Override
		public int getExitCode() throws IllegalThreadStateException {
			if (exitCode == null)
				throw new IllegalThreadStateException();
			return exitCode.intValue();
		}
		
		@Override
		public void kill() {
			p.destroyForcibly();
			LCCore.getApplication().closed(SeparateProcess.this);
		}
		
		@Override
		public void addListener(Runnable listener) {
			synchronized (terminated) {
				if (exitCode == null) {
					terminated.addListener(listener);
					return;
				}
			}
			listener.run();
		}
		
		@Override
		public void close() {
			if (exitCode != null) return;
			kill();
		}
	}
	
	@Override
	public SeparateProcess executeCommand(String[] command, boolean elevated) throws Exception {
		//if (elevated)
		//	return executeElevated(command);
		/*
		String[] args = new String[command.length + 3];
		System.arraycopy(command, 0, args, 3, command.length);
		args[0] = "/usr/bin/x-terminal-emulator";
		args[1] = "--wait";
		args[2] = "-e";
		command = args;*/
		ProcessBuilder builder = new ProcessBuilder(command);
		Process p = builder.start();
		return new SeparateProcess(p, Arrays.toString(command));
	}

}
