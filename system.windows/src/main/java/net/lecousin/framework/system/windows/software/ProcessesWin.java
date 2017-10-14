package net.lecousin.framework.system.windows.software;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.system.software.Processes;
import net.lecousin.framework.system.windows.jna.Kernel32;
import net.lecousin.framework.system.windows.jna.Psapi;

/**
 * Implementation of Processes for Windows.
 *
 */
public class ProcessesWin extends Processes {

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
		List<Integer> list = new ArrayList<Integer>(nb);
		for (int i = 0; i < nb; ++i)
			list.add(Integer.valueOf(DataUtil.readIntegerLittleEndian(buf, i * 4)));
		return list;
	}
	
	@Override
	public long getProccessCPUTimeNano(int id) {
		HANDLE h = com.sun.jna.platform.win32.Kernel32.INSTANCE.OpenProcess(0x0400, false, id);
		if (h == null) return -1;
		byte[] creation = new byte[8];
		byte[] exit = new byte[8];
		byte[] kernel = new byte[8];
		byte[] user = new byte[8];
		boolean res = Kernel32.INSTANCE.GetProcessTimes(h, creation, exit, kernel, user);
		com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(h);
		if (!res) return -1;
		// times are in 100-nanoseconds units, so we multiply it by 100 to get an approximation of nanoseconds
		return (DataUtil.readLongLittleEndian(kernel, 0) + DataUtil.readLongLittleEndian(user, 0)) * 100;
	}
	
	@Override
	public void killProcess(int id) throws Exception {
		HANDLE h = com.sun.jna.platform.win32.Kernel32.INSTANCE.OpenProcess(0x0401, false, id);
		if (h == null) throw new Exception("Unable to get process handle");
		boolean ok = com.sun.jna.platform.win32.Kernel32.INSTANCE.TerminateProcess(h, 1);
		if (!ok) throw new Exception("TerminateProcess failed");
	}
}
