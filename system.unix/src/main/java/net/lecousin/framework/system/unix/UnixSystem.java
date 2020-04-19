package net.lecousin.framework.system.unix;

import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.unix.hardware.UnixHardware;
import net.lecousin.framework.system.unix.software.UnixSoftware;

public class UnixSystem extends LCSystem {

	private UnixHardware hardware;
	private UnixSoftware software;
	
	UnixSystem() {
		hardware = new UnixHardware();
		software = new UnixSoftware();
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
