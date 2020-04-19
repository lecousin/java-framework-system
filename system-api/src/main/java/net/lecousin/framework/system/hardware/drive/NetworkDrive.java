package net.lecousin.framework.system.hardware.drive;

/**
 * Network drive.
 */
public interface NetworkDrive extends Drive {
	
	@Override
	default boolean supportConcurrentAccess() {
		return true;
	}

}
