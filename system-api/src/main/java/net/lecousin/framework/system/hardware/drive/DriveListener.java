package net.lecousin.framework.system.hardware.drive;

/** Listen to the system to know when a new drive is connected or when a drive is disconnected. */
public interface DriveListener {

	/** New drive. */
	void newDrive(Drive drive);
	
	/** Drive removed. */
	void driveRemoved(Drive drive);
	
	/** New partition. */
	void newPartition(DiskPartition partition);
	
	/** Partition removed. */
	void partitionRemoved(DiskPartition partition);

}
