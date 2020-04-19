package net.lecousin.framework.system.unix.hardware;

import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.system.unix.hardware.drive.LinuxDrives;
import net.lecousin.framework.system.unix.jna.JnaInstances;

public class LinuxHardware implements Hardware {
	
	private Drives drives;
	
	public LinuxHardware() {
		if (JnaInstances.udev != null) {
			drives = new LinuxDrives();
		}
	}
	
	@Override
	public Drives getDrives() {
		return drives;
	}

}
