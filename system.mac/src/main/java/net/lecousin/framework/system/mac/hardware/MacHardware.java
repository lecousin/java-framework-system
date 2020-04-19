package net.lecousin.framework.system.mac.hardware;

import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.mac.hardware.drive.DrivesMac;

public class MacHardware implements Hardware {
	
	private DrivesMac drives;
	
	public MacHardware() {
		drives = new DrivesMac();
	}
	
	@Override
	public DrivesMac getDrives() {
		return drives;
	}

}
