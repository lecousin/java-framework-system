package net.lecousin.framework.system.hardware;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.lecousin.framework.adapter.Adapter;
import net.lecousin.framework.concurrent.DrivesTaskManager;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.Threading;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.event.Listener;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.util.Pair;

/**
 * Get information about drives.
 */
public abstract class Drives {

	private static Drives instance = null;
	
	public static Drives getInstance() {
		return instance;
	}
	
	/** Set the implementation. */
	public static void setInstance(Drives drives) throws IllegalStateException {
		synchronized (Drives.class) {
			if (instance != null) throw new IllegalStateException();
			instance = drives;
		}
		ISynchronizationPoint<Exception> init = drives.initialize().getSynch();
		init.listenInline(() -> {
			if (init.isSuccessful())
				try {
					Threading.getDrivesTaskManager().setDrivesProvider(new DrivesTaskManager.DrivesProvider() {
						@Override
						public void provide(
							Listener<Pair<Object, List<File>>> onNewDrive,
							Listener<Pair<Object, List<File>>> onDriveRemoved,
							Listener<Pair<Object, File>> onNewPartition,
							Listener<Pair<Object, File>> onPartitionRemoved
						) {
							drives.getDrivesAndListen(new DriveListenerImpl(
								(drive) -> { onNewDrive.fire(new Pair<>(drive, drive.getMountPoints())); },
								(drive) -> { onDriveRemoved.fire(new Pair<>(drive, drive.getMountPoints())); },
								(part) -> { if (part.mountPoint != null) onNewPartition.fire(new Pair<>(part.drive, part.mountPoint)); },
								(part) -> { if (part.mountPoint != null) onPartitionRemoved.fire(new Pair<>(part.drive, part.mountPoint)); }
							));
						}
					});
				} catch (IllegalStateException e) {
					/* ignore */
				}
		});
	}
	
	/** Listen to the system to know when a new drive is connected or when a drive is disconnected. */
	public static interface DriveListener {
		/** New drive. */
		void newDrive(Drive drive);
		
		/** Drive removed. */
		void driveRemoved(Drive drive);
		
		/** New partition. */
		void newPartition(DiskPartition partition);
		
		/** Partition removed. */
		void partitionRemoved(DiskPartition partition);
	}
	
	/** DriveListener with individual listeners for each event. */
	public static class DriveListenerImpl implements DriveListener {
		/** Constructor. */
		public DriveListenerImpl(
			Listener<Drive> newDrive, Listener<Drive> driveRemoved,
			Listener<DiskPartition> newPartition, Listener<DiskPartition> partitionRemoved
		) {
			this.newDrive = newDrive;
			this.driveRemoved = driveRemoved;
			this.newPartition = newPartition;
			this.partitionRemoved = partitionRemoved;
		}
		
		private Listener<Drive> newDrive;
		private Listener<Drive> driveRemoved;
		private Listener<DiskPartition> newPartition;
		private Listener<DiskPartition> partitionRemoved;
		
		@Override
		public void newDrive(Drive drive) {
			newDrive.fire(drive);
		}
		
		@Override
		public void driveRemoved(Drive drive) {
			driveRemoved.fire(drive);
		}
		
		@Override
		public void newPartition(DiskPartition partition) {
			newPartition.fire(partition);
		}
		
		@Override
		public void partitionRemoved(DiskPartition partition) {
			partitionRemoved.fire(partition);
		}
	}
	
	/** Initialize detection of drives on the system if not yet done, this method should be called first
	 * and the returned WorkProgress should be done before using any other method.
	 */
	public abstract WorkProgress initialize();
	
	/** Return the current list of drives. */
	public abstract List<Drive> getDrives();
	
	/** Add a listener. */
	public abstract void addDriveListener(DriveListener listener);
	
	/** Remove a listener. */
	public abstract void removeDriveListener(DriveListener listener);
	
	/** Add a listener and call this listener with the current drives. */
	public abstract void getDrivesAndListen(DriveListener listener);
	
	/** Open a drive in read-only mode, in most of the system this requires administrator privileges. */
	public abstract <T extends IO.Readable.Seekable & IO.KnownSize>
	T openReadOnly(PhysicalDrive drive, byte priority) throws IOException;

	/** Open a drive in write-only mode, in most of the system this requires administrator privileges. */
	public abstract <T extends IO.Writable.Seekable & IO.KnownSize>
	T openWriteOnly(PhysicalDrive drive, byte priority) throws IOException;
	
	/** Open a drive in read and write mode, in most of the system this requires administrator privileges. */
	public abstract <T extends IO.Readable.Seekable & IO.KnownSize & IO.Writable.Seekable>
	T openReadWrite(PhysicalDrive drive, byte priority) throws IOException;
	
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
	
	/** Convert a PhysicalDrive into a Readable, using the openReadOnly method. */
	public static class PhysicalDriveToReadable implements Adapter<PhysicalDrive,IO.Readable.Seekable> {
		@Override
		public Class<PhysicalDrive> getInputType() { return PhysicalDrive.class; }
		
		@Override
		public Class<Seekable> getOutputType() { return IO.Readable.Seekable.class; }
		
		@Override
		public boolean canAdapt(PhysicalDrive input) {
			return true;
		}
		
		@Override
		public Seekable adapt(PhysicalDrive input) throws IOException {
			return instance.openReadOnly(input, Task.PRIORITY_NORMAL);
		}
	}
	
}
