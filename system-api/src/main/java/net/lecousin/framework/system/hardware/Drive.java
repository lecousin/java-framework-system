package net.lecousin.framework.system.hardware;

import java.io.File;
import java.util.List;

/**
 * Base interface for a drive.
 */
public interface Drive {

	/** Return a list of mount points if any. */
	public List<File> getMountPoints();
	
}
