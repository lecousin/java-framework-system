package net.lecousin.framework.system.mac;

import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.mac.hardware.MacHardware;
import net.lecousin.framework.system.mac.software.MacSoftware;
import net.lecousin.framework.system.software.Software;

public class MacSystem extends LCSystem {

	private MacHardware hardware;
	private MacSoftware software;
	
	MacSystem() {
		hardware = new MacHardware();
		software = new MacSoftware();
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
