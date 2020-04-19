package net.lecousin.framework.system.mac.jna;

import com.sun.jna.platform.mac.CoreFoundation.CFAllocatorRef;
import com.sun.jna.platform.mac.SystemB;

public final class JnaInstances {

	private JnaInstances() {
		/* no instance */
	}
	
	/** Mac DiskArbitration. */
	public static DiskArbitration diskArbitration = null;

	/** Mac CoreFoundation. */
	public static CoreFoundation coreFoundation = null;
	public static CFAllocatorRef ALLOCATOR = null;

	/** Mac IOKit. */
	public static IOKit ioKit = null;
	
	/** Mac SystemB. */
	public static SystemB systemB = null;
	
}
