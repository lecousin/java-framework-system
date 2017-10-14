package net.lecousin.framework.system.unix.jna;

import net.lecousin.framework.system.unix.jna.linux.Udev;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration;

public final class JnaInstances {

	private JnaInstances() { /* no instance */ }
	
	/** Linux udev */
	public static Udev udev = null;
	
	/** Mac DiskArbitration */
	public static DiskArbitration diskArbitration = null;
	
}
