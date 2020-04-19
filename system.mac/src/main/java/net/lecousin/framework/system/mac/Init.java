package net.lecousin.framework.system.mac;

import com.sun.jna.Native;
import com.sun.jna.platform.mac.SystemB;

import net.lecousin.framework.plugins.CustomExtensionPoint;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.mac.jna.CoreFoundation;
import net.lecousin.framework.system.mac.jna.DiskArbitration;
import net.lecousin.framework.system.mac.jna.IOKit;
import net.lecousin.framework.system.mac.jna.JnaInstances;
import net.lecousin.framework.system.mac.jna.RunLoopThread;

/**
 * Initialization.
 */
public class Init implements CustomExtensionPoint {

	/** Constructor called by the extension point mechanism. */
	public Init() {
		try {
			JnaInstances.diskArbitration = Native.load("DiskArbitration", DiskArbitration.class);
			JnaInstances.coreFoundation = Native.load("CoreFoundation", CoreFoundation.class);
			JnaInstances.ALLOCATOR = JnaInstances.coreFoundation.CFAllocatorGetDefault();
			JnaInstances.ioKit = Native.load("IOKit", IOKit.class);
			JnaInstances.systemB = Native.load("System", SystemB.class);
			RunLoopThread.init();
		} catch (Throwable t) {
			LCSystem.log.error("Error loading native libraries for Mac", t);
		}
		new MacSystem();
	}
	
	@Override
	public boolean keepAfterInit() {
		return false;
	}
	
}
