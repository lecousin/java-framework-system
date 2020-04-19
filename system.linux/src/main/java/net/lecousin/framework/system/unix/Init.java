package net.lecousin.framework.system.unix;

import com.sun.jna.Native;

import net.lecousin.framework.plugins.CustomExtensionPoint;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.Udev;

/**
 * Initialization.
 */
public class Init implements CustomExtensionPoint {

	/** Constructor called by the extension point mechanism. */
	public Init() {
		try {
			JnaInstances.udev = Native.load("udev", Udev.class);
		} catch (Throwable t) {
			LCSystem.log.error("Error loading udev library for Linux", t);
		}
		new LinuxSystem();
	}
	
	@Override
	public boolean keepAfterInit() {
		return false;
	}
	
}
