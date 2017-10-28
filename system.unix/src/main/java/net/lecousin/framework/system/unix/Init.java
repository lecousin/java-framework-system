package net.lecousin.framework.system.unix;

import com.sun.jna.Native;
import com.sun.jna.Platform;

import net.lecousin.framework.plugins.CustomExtensionPoint;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.unix.hardware.DrivesMac;
import net.lecousin.framework.system.unix.hardware.DrivesUnixUdev;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.linux.Udev;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration;
import net.lecousin.framework.system.unix.jna.mac.IOKit;
import net.lecousin.framework.system.unix.jna.mac.RunLoopThread;
import net.lecousin.framework.system.unix.jna.mac.SystemB;

public class Init implements CustomExtensionPoint {

	public Init() {
		if (Platform.isMac()) {
			try {
				JnaInstances.diskArbitration = Native.loadLibrary("DiskArbitration", DiskArbitration.class);
				JnaInstances.coreFoundation = Native.loadLibrary("CoreFoundation", CoreFoundation.class);
				JnaInstances.ALLOCATOR = JnaInstances.coreFoundation.CFAllocatorGetDefault();
				JnaInstances.ioKit = Native.loadLibrary("IOKit", IOKit.class);
				JnaInstances.systemB = Native.loadLibrary("System", SystemB.class);
				RunLoopThread.init();
				Drives.setInstance(new DrivesMac());
			} catch (Throwable t) {
				LCSystem.log.error("Error loading native libraries for Mac", t);
			}
		} else {
			try {
				JnaInstances.udev = Native.loadLibrary("udev", Udev.class);
				Drives.setInstance(new DrivesUnixUdev());
			} catch (Throwable t) {
			}
		}
	}
	
}
