package net.lecousin.framework.system.unix;

import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.unix.hardware.LinuxHardware;
import net.lecousin.framework.system.unix.software.LinuxSoftware;

public class LinuxSystem extends LCSystem {

	private LinuxHardware hardware;
	private LinuxSoftware software;
	
	LinuxSystem() {
		hardware = new LinuxHardware();
		software = new LinuxSoftware();
	}
	
	@Override
	public Hardware getHardware() {
		return hardware;
	}
	
	@Override
	public Software getSoftware() {
		return software;
	}
	
}
