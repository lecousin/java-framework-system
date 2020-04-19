package net.lecousin.framework.system.unix;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.platform.mac.SystemB;

import net.lecousin.framework.plugins.CustomExtensionPoint;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.linux.Udev;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration;
import net.lecousin.framework.system.unix.jna.mac.IOKit;
import net.lecousin.framework.system.unix.jna.mac.RunLoopThread;

/**
 * Initialization.
 */
public class Init implements CustomExtensionPoint {

	/** Constructor called by the extension point mechanism. */
	public Init() {
		if (Platform.isMac()) {
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
		} else {
			try {
				JnaInstances.udev = Native.loadLibrary("udev", Udev.class);
			} catch (Throwable t) {
				LCSystem.log.error("Error loading udev library for Linux", t);
			}
		}
		new UnixMacSystem();
	}
	
	@Override
	public boolean keepAfterInit() {
		return false;
	}
	
}
