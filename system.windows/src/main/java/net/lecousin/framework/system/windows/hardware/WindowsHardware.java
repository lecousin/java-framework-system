package net.lecousin.framework.system.windows.hardware;

import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.system.windows.hardware.drive.WindowsDrives;

public class WindowsHardware implements Hardware {

	private WindowsDrives drives = new WindowsDrives();
	
	@Override
	public Drives getDrives() {
		return drives;
	}
	
}
