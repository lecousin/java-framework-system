package net.lecousin.framework.system.unix.jna;

import net.lecousin.framework.system.unix.jna.linux.Udev;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFAllocatorRef;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration;
import net.lecousin.framework.system.unix.jna.mac.IOKit;
import net.lecousin.framework.system.unix.jna.mac.SystemB;

public final class JnaInstances {

	private JnaInstances() { /* no instance */ }
	
	/** Linux udev */
	public static Udev udev = null;
	
	/** Mac DiskArbitration */
	public static DiskArbitration diskArbitration = null;

	/** Mac CoreFoundation */
	public static CoreFoundation coreFoundation = null;
	public static CFAllocatorRef ALLOCATOR = null;

	/** Mac IOKit */
	public static IOKit ioKit = null;
	
	/** Mac SystemB */
	public static SystemB systemB = null;
	
}
