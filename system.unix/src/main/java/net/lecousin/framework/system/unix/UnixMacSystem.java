package net.lecousin.framework.system.unix;

import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.software.Software;
import net.lecousin.framework.system.unix.hardware.UnixMacHardware;
import net.lecousin.framework.system.unix.software.UnixMacSoftware;

public class UnixMacSystem extends LCSystem {

	private UnixMacHardware hardware;
	private UnixMacSoftware software;
	
	UnixMacSystem() {
		hardware = new UnixMacHardware();
		software = new UnixMacSoftware();
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
