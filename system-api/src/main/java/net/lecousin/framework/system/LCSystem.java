package net.lecousin.framework.system;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.log.Logger;

/**
 * Utilities for system classes.
 */
public class LCSystem {

	public static Logger log;
	
	static {
		log = LCCore.get().getSystemLogger("system");
	}
	
}
