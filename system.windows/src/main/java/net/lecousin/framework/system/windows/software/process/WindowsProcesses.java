package net.lecousin.framework.system.windows.software.process;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinBase.FILETIME;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.threads.ApplicationThread;
import net.lecousin.framework.event.SimpleEvent;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.system.software.process.Processes;
import net.lecousin.framework.system.software.process.SeparateProcess;
import net.lecousin.framework.system.windows.jna.Kernel32;
import net.lecousin.framework.system.windows.jna.Psapi;

/**
 * Implementation of Processes for Windows.
 *
 */
public class WindowsProcesses extends Processes {

	@Override
	public int getCurrentProcessId() {
		return Kernel32.INSTANCE.GetCurrentProcessId();
	}
	
	@Override
	public List<Integer> listProcessesIds() {
		byte[] buf = new byte[4096 * 4];
		IntByReference size = new IntByReference();
		if (!Psapi.INSTANCE.EnumProcesses(buf, buf.length, size)) return null;
		int nb = size.getValue() / 4;
		List<Integer> list = new ArrayList<>(nb);
		for (int i = 0; i < nb; ++i)
			list.add(Integer.valueOf(DataUtil.Read32.LE.read(buf, i * 4)));
		return list;
	}
	
	@Override
	public long getProcessCPUTimeNano(int id) {
		HANDLE h = com.sun.jna.platform.win32.Kernel32.INSTANCE.OpenProcess(0x0400, false, id);
		if (h == null) return -1;
		FILETIME creation = new FILETIME();
		FILETIME exit = new FILETIME();
		FILETIME kernel = new FILETIME();
		FILETIME user = new FILETIME();
		boolean res = Kernel32.INSTANCE.GetProcessTimes(h, creation, exit, kernel, user);
		com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(h);
		if (!res) return -1;
		// times are in 100-nanoseconds units, so we multiply it by 100 to get an approximation of nanoseconds
		return (kernel.toDWordLong().longValue() + user.toDWordLong().longValue()) * 100;
	}
	
	@Override
	public void killProcess(int id) throws Exception {
		HANDLE h = com.sun.jna.platform.win32.Kernel32.INSTANCE.OpenProcess(0x0401, false, id);
		if (h == null) throw new Exception("Unable to get process handle");
		boolean ok = com.sun.jna.platform.win32.Kernel32.INSTANCE.TerminateProcess(h, 1);
		if (!ok) throw new Exception("TerminateProcess failed");
	}
	
	private static class SeparateProcessWin implements SeparateProcess, Closeable {
		private SeparateProcessWin(HANDLE handle, String command) {
			this.handle = handle;
			app = LCCore.getApplication();
			thread = app.createThread(new WaitFor());
			thread.setName("Wait for process to terminate: " + command);
			thread.start();
			app.toClose(1, this);
		}
		
		private Application app;
		private HANDLE handle;
		private Thread thread;
		private Integer exitCode = null;
		private SimpleEvent terminated = new SimpleEvent();
		
		private class WaitFor implements ApplicationThread {
			@Override
			public Application getApplication() {
				return app;
			}
			
			@Override
			public void run() {
				try {
					Kernel32.INSTANCE.WaitForSingleObject(handle, WinBase.INFINITE);
					synchronized (terminated) {
						IntByReference code = new IntByReference();
						if (Kernel32.INSTANCE.GetExitCodeProcess(handle, code))
							exitCode = Integer.valueOf(code.getValue());
						else
							exitCode = Integer.valueOf(9999);
					}
					terminated.fire();
				} finally {
					Kernel32.INSTANCE.CloseHandle(handle);
					LCCore.getApplication().closed(SeparateProcessWin.this);
				}
			}
			
			@Override
			public void debugStatus(StringBuilder s) {
				s.append(" - ").append(thread.getName()).append('\n');
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
			Kernel32.INSTANCE.TerminateProcess(handle, 0);
			Kernel32.INSTANCE.CloseHandle(handle);
			LCCore.getApplication().closed(SeparateProcessWin.this);
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
		boolean debug = LCCore.getApplication().isDebugMode();
		String cmd = "cmd";
		StringBuilder params = new StringBuilder();
		params.append("/S /C \"");
		for (int i = 0; i < command.length; ++i) {
			if (i > 0) params.append(' ');
			params.append('"');
			params.append(command[i].replace("\\", "\\\\").replace("\"", "\\\""));
			params.append('"');
		}
		params.append('"');
		if (debug)
			LCCore.getApplication().getDefaultLogger().info("Execute command: " + cmd + " " + params.toString());

		Shell32.SHELLEXECUTEINFO execInfo = new Shell32.SHELLEXECUTEINFO();
        execInfo.lpFile = cmd;
        execInfo.lpParameters = params.toString();
        execInfo.nShow = debug ? 5 : 0;
        execInfo.fMask = Shell32.SEE_MASK_NOCLOSEPROCESS;
        if (elevated)
        	execInfo.lpVerb = "runas";
        boolean result = Shell32.INSTANCE.ShellExecuteEx(execInfo);

		if (!result) {
			int lastError = Kernel32.INSTANCE.GetLastError();
			// TODO if elevated is true, and error is 1223 = cancelled by user, apperror=5 (access denied)
			throw new Exception("Error executing command: " + lastError + " (apperror=" + execInfo.hInstApp + ")");
		}

		if (debug)
			LCCore.getApplication().getDefaultLogger().info("Command executed: " + cmd + " " + params.toString());
		
		return new SeparateProcessWin(execInfo.hProcess, Arrays.toString(command));
	}
}
