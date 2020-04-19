package net.lecousin.framework.system;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.system.hardware.Hardware;
import net.lecousin.framework.system.software.Software;

/**
 * Base class to access functionalities from the operating system.
 */
public abstract class LCSystem {

	public static final Logger log;
	
	static {
		log = LCCore.get().getSystemLogger("system");
	}

	private static LCSystem instance;
	
	public static LCSystem get() {
		return instance;
	}
	
	protected LCSystem() {
		if (instance != null) throw new IllegalStateException();
		instance = this;
	}
	
	public abstract Hardware getHardware();
	
	public abstract Software getSoftware();
	
}
