package net.lecousin.framework.system.unix.hardware;

import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.system.unix.hardware.drive.DrivesMac;
import net.lecousin.framework.system.unix.hardware.drive.DrivesUnixUdev;
import net.lecousin.framework.system.unix.jna.JnaInstances;

public class UnixMacHardware implements Hardware {
	
	private Drives drives;
	
	public UnixMacHardware() {
		if (JnaInstances.diskArbitration != null) {
			drives = new DrivesMac();
		} else if (JnaInstances.udev != null) {
			drives = new DrivesUnixUdev();
		}
	}
	
	@Override
	public Drives getDrives() {
		return drives;
	}

}
