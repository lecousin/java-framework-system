package net.lecousin.framework.system.unix.hardware;

import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.system.unix.hardware.drive.DrivesUnixUdev;
import net.lecousin.framework.system.unix.jna.JnaInstances;

public class UnixHardware implements Hardware {
	
	private Drives drives;
	
	public UnixHardware() {
		if (JnaInstances.udev != null) {
			drives = new DrivesUnixUdev();
		}
	}
	
	@Override
	public Drives getDrives() {
		return drives;
	}

}
