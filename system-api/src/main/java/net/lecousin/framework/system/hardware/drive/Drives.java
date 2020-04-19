package net.lecousin.framework.system.hardware.drive;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.concurrent.threads.Threading;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.Triple;

/**
 * Get information about drives.
 */
public abstract class Drives {
	
	private WorkProgress init;

	protected void init() {
		init = new WorkProgressImpl(10000, "Analyzing drives information");
		// launch init
		Task.unmanaged("Analyzing drives information", Priority.RATHER_IMPORTANT, t -> {
			initializeDrives(init);
			return null;
		}).start();
		// on success, register us as a DrivesProvider to the threading system
		init.getSynch().onSuccess(() -> {
			LCSystem.log.info("Drives information initialized");
			registerToThreadingSystem();
		});
	}
	
	protected abstract void initializeDrives(WorkProgress progress);
	
	/** Initialize detection of drives on the system if not yet done, this method should be called first
	 * and the returned WorkProgress should be done before using any other method.
	 */
	public final WorkProgress initialize() {
		return init;
	}
	
	/** Return the current list of drives. */
	public abstract List<Drive> getDrives();
	
	/** Add a listener. */
	public abstract void addDriveListener(DriveListener listener);
	
	/** Remove a listener. */
	public abstract void removeDriveListener(DriveListener listener);
	
	/** Add a listener and call this listener with the current drives before to return. */
	public abstract void getDrivesAndListen(DriveListener listener);
	
	/** Return the currently registered listeners. */
	public abstract Collection<DriveListener> getDriveListeners();
	
	/** Open a drive in read-only mode, in most of the system this requires administrator privileges. */
	public abstract <T extends IO.Readable.Seekable & IO.KnownSize>
	T openReadOnly(PhysicalDrive drive, Priority priority) throws IOException;

	/** Open a drive in write-only mode, in most of the system this requires administrator privileges. */
	public abstract <T extends IO.Writable.Seekable & IO.KnownSize>
	T openWriteOnly(PhysicalDrive drive, Priority priority) throws IOException;
	
	/** Open a drive in read and write mode, in most of the system this requires administrator privileges. */
	public abstract <T extends IO.Readable.Seekable & IO.KnownSize & IO.Writable.Seekable>
	T openReadWrite(PhysicalDrive drive, Priority priority) throws IOException;
	
	/** Return the dive containing the given file, or null. */
	public Drive getDriveFor(File file) throws IOException {
		String path = file.getCanonicalPath();
		for (Drive drive : getDrives()) {
			for (File mount : drive.getMountPoints()) {
				if (path.startsWith(mount.getCanonicalPath()))
					return drive;
			}
		}
		return null;
	}
	
	private void registerToThreadingSystem() {
		try {
			Threading.getDrivesManager().setDrivesProvider(
				(onNewDrive, onDriveRemoved, onNewPartition, onPartitionRemoved) -> getDrivesAndListen(
				new DriveListener() {
					@Override
					public void newDrive(Drive drive) {
						onNewDrive.accept(new Triple<>(
							drive, drive.getMountPoints(), Boolean.valueOf(drive.supportConcurrentAccess()))
						);
					}
					
					@Override
					public void driveRemoved(Drive drive) {
						onDriveRemoved.accept(drive);
					}
					
					@Override
					public void newPartition(DiskPartition partition) {
						if (partition.mountPoint != null) 
							onNewPartition.accept(new Pair<>(partition.drive, partition.mountPoint));
					}
					
					@Override
					public void partitionRemoved(DiskPartition partition) {
						if (partition.mountPoint != null)
							onPartitionRemoved.accept(new Pair<>(partition.drive, partition.mountPoint));
					}
				})
			);
		} catch (IllegalStateException e) {
			/* ignore */
		}
		
	}
	
}
