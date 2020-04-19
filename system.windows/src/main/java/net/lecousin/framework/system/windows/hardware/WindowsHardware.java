package net.lecousin.framework.system.windows.hardware;

import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.system.windows.hardware.drive.DrivesWin;

public class WindowsHardware implements Hardware {

	private DrivesWin drives = new DrivesWin();
	
	@Override
	public Drives getDrives() {
		return drives;
	}
	
}
