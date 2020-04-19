package net.lecousin.framework.system.hardware.drive;

import java.io.File;
import java.util.List;

/**
 * Base interface for a drive.
 */
public interface Drive {

	/** Return a list of mount points if any. */
	public List<File> getMountPoints();
	
	/** Return true if the drive can be concurrently accessed with good performance. */
	public boolean supportConcurrentAccess();
	
}
