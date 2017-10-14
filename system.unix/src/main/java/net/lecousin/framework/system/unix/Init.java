package net.lecousin.framework.system.unix;

import com.sun.jna.Native;
import com.sun.jna.Platform;

import net.lecousin.framework.plugins.CustomExtensionPoint;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.unix.hardware.DrivesUnixUdev;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.linux.Udev;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration;
import net.lecousin.framework.system.unix.jna.mac.RunLoopThread;

public class Init implements CustomExtensionPoint {

	public Init() {
		if (Platform.isMac()) {
			try {
				RunLoopThread.init();
				JnaInstances.diskArbitration = Native.loadLibrary("DiskArbitration", DiskArbitration.class);
				// TODO
			} catch (Throwable t) {
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
