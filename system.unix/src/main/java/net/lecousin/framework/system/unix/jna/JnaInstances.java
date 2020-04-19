package net.lecousin.framework.system.unix.jna;

import net.lecousin.framework.system.unix.jna.linux.Udev;

public final class JnaInstances {

	private JnaInstances() {
		/* no instance */
	}
	
	/** Linux udev. */
	public static Udev udev = null;
	
}
