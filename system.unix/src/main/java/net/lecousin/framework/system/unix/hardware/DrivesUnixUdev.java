package net.lecousin.framework.system.unix.hardware;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.DiskPartition;
import net.lecousin.framework.system.hardware.DiskPartitionsUtil;
import net.lecousin.framework.system.hardware.Drive;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.hardware.PhysicalDrive;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.LibC;
import net.lecousin.framework.system.unix.jna.LibC.FDSet;
import net.lecousin.framework.system.unix.jna.LibC.TimeVal;
import net.lecousin.framework.system.unix.jna.linux.Udev;
import net.lecousin.framework.util.AsyncCloseable;

/** Drives implementation for Linux with udev. */
public class DrivesUnixUdev extends Drives {

	private WorkProgress init = null;

	@Override
	public WorkProgress initialize() {
		if (init != null) return init;
		init = new WorkProgressImpl(100000, "Loading drives information");
		new Thread("Initializing Drives Information") {
			@Override
			public void run() {
				initDrives(init);
				LCSystem.log.info("Drives information initialized");
				init.done();
			}
		}.start();
		return init;
	}

	private List<Drive> drives = new ArrayList<>();
	private List<DriveListener> listeners = new ArrayList<>();
	
	@Override
	public List<Drive> getDrives() {
		return new ArrayList<>(drives);
	}
	
	@Override
	public void getDrivesAndListen(DriveListener listener) {
		synchronized (drives) {
			for (Drive d : drives) listener.newDrive(d);
			synchronized (listeners) {
				listeners.add(listener);
			}
		}
	}

	@Override
	public void addDriveListener(DriveListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}
	
	@Override
	public void removeDriveListener(DriveListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IO.Readable.Seekable & IO.KnownSize> T openReadOnly(PhysicalDrive drive, byte priority) {
		return (T)new FileIO.ReadOnly(new File((String)drive.getOSId()), priority);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IO.Writable.Seekable & IO.KnownSize> T openWriteOnly(PhysicalDrive drive, byte priority) {
		return (T)new FileIO.WriteOnly(new File((String)drive.getOSId()), priority);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends  IO.Readable.Seekable & IO.KnownSize & IO.Writable.Seekable> T openReadWrite(PhysicalDrive drive, byte priority) {
		return (T)new FileIO.ReadWrite(new File((String)drive.getOSId()), priority);
	}

	private void initDrives(WorkProgress progress) {
		List<String[]> mountsLines = readMounts();
		
		Udev udev = JnaInstances.udev;
		Udev.UdevHandle handle = udev.udev_new();
		Udev.UdevEnumerate enumerate = udev.udev_enumerate_new(handle);
		udev.udev_enumerate_add_match_subsystem(enumerate, "block");
		udev.udev_enumerate_scan_devices(enumerate);
		Udev.UdevListEntry entry = udev.udev_enumerate_get_list_entry(enumerate);
		
		while (entry != null) {
			String name = udev.udev_list_entry_get_name(entry);
			
			Udev.UdevDevice device = null;
			try {
				device = udev.udev_device_new_from_syspath(handle, name);
				newDevice(udev, device, mountsLines);
			} catch (Throwable t) {
				LCSystem.log.error("Error reading device", t);
			} finally {
				if (device != null)
					udev.udev_device_unref(device);
			}
			
			entry = udev.udev_list_entry_get_next(entry);
		}
		
		udev.udev_enumerate_unref(enumerate);
		
		addAdditionalPartitions(mountsLines);

		MonitorUdev monitor = new MonitorUdev(handle);
		monitor.start();
		LCCore.get().toClose(monitor);
		File f = new File("/proc/self/mounts");
		if (f.exists()) {
			MonitorMounts m = new MonitorMounts();
			m.start();
			LCCore.get().toClose(m);
		}
	}
	
	private class MonitorUdev extends Thread implements AsyncCloseable<Exception> {
		public MonitorUdev(Udev.UdevHandle handle) {
			super("Udev Monitor");
			this.handle = handle;
		}
		
		private Udev.UdevHandle handle;
		private SynchronizationPoint<Exception> closing = null;
		
		@Override
		public void run() {
			Udev udev = JnaInstances.udev;
			Udev.UdevMonitor monitor = udev.udev_monitor_new_from_netlink(handle, "udev");
			udev.udev_monitor_filter_add_match_subsystem_devtype(monitor, "block", null);
			udev.udev_monitor_enable_receiving(monitor);
			int fd = udev.udev_monitor_get_fd(monitor);
			TimeVal tv;
			FDSet fds;
			synchronized (DrivesUnixUdev.class) {
				fds = new FDSet();
				fds.FD_ZERO();
				fds.FD_SET(fd);
				tv = new TimeVal(2, 0);
			}
			while (closing == null) {
				synchronized (DrivesUnixUdev.class) {
					fds = new FDSet();
					fds.FD_ZERO();
					fds.FD_SET(fd);
					tv = new TimeVal(2, 0);
				}
				int ret = LibC.INSTANCE.select(fd + 1, fds, null, null, tv);
				if (ret <= 0) continue;

				Udev.UdevDevice device = udev.udev_monitor_receive_device(monitor);
				try {
					String action = udev.udev_device_get_property_value(device, "ACTION");
					if ("add".equals(action)) {
						List<String[]> mounts = readMounts();
						newDevice(udev, device, mounts);
						addAdditionalPartitions(mounts);
					} else if ("remove".equals(action)) {
						deviceRemoved(udev, device);
					}
				} catch (Throwable t) {
					LCSystem.log.error("Error analyzing device", t);
				} finally {
					udev.udev_device_unref(device);
				}
			}
			
			udev.udev_monitor_unref(monitor);
			udev.udev_unref(handle);
			closing.unblock();
		}
		
		@Override
		public ISynchronizationPoint<Exception> closeAsync() {
			if (closing != null) return closing;
			closing = new SynchronizationPoint<>();
			return closing;
		}
	}

	private class MonitorMounts extends Thread implements AsyncCloseable<Exception> {
		public MonitorMounts() {
			super("Mount points Monitor");
		}
		
		private SynchronizationPoint<Exception> closing = null;
		
		@Override
		public void run() {
			int fd;
			LibC.PollFD[] pfd;
			synchronized (DrivesUnixUdev.class) {
				fd = LibC.INSTANCE.open("/proc/self/mounts", LibC.O_RDONLY);
				LibC.PollFD pollfd = new LibC.PollFD(fd, (short)(LibC.POLLERR | LibC.POLLPRI), (short)0);
				pfd = new LibC.PollFD[] { pollfd };
			}
			while (closing == null) {
				int ret = LibC.INSTANCE.poll(pfd, 1, 2000);
				if (ret <= 0) continue;
				try {
					LCSystem.log.info("Mount points file changed.");
					List<String[]> mounts = readMounts();
					addAdditionalPartitions(mounts);
				} catch (Throwable t) {
					LCSystem.log.error("Error analyzing mount points", t);
				}
			}

			LibC.INSTANCE.close(fd);
			closing.unblock();
		}
		
		@Override
		public ISynchronizationPoint<Exception> closeAsync() {
			if (closing != null) return closing;
			closing = new SynchronizationPoint<>();
			return closing;
		}
	}
	
	private void newDevice(Udev udev, Udev.UdevDevice device, List<String[]> mountsLines) {
		String devnode = udev.udev_device_get_devnode(device);
		String devtype = udev.udev_device_get_devtype(device);
		if ("disk".equals(devtype)) {
			if (!devnode.startsWith("/dev/loop") && !devnode.startsWith("/dev/ram")) {
				newDisk(udev, device);
			}
		} else if ("partition".equals(devtype)) {
			newPartition(udev, device, mountsLines);
		}
	}
	
	private void newDisk(Udev udev, Udev.UdevDevice device) {
		String devnode = udev.udev_device_get_devnode(device);
		String devpath = udev.udev_device_get_property_value(device, "DEVPATH");
		String model = udev.udev_device_get_property_value(device, "ID_MODEL");
		String serial = udev.udev_device_get_property_value(device, "ID_SERIAL_SHORT");
		String revision = udev.udev_device_get_property_value(device, "ID_REVISION");
		String type = udev.udev_device_get_property_value(device, "ID_TYPE");
		String bus = udev.udev_device_get_property_value(device, "ID_BUS");
		String removable = udev.udev_device_get_sysattr_value(device, "removable");
		String sizeStr = udev.udev_device_get_sysattr_value(device, "size");
		BigInteger size = null;
		if (sizeStr != null) {
			try {
				size = new BigInteger(sizeStr);
				size = size.multiply(BigInteger.valueOf(512));
			} catch (NumberFormatException e) {
				/* ignore */
			}
		}
				
		PhysicalDriveUnix drive = new PhysicalDriveUnix();
		drive.devpath = devpath;
		drive.OSID = devnode;
		drive.model = model;
		drive.version = revision;
		drive.serial = serial;
		if ("disk".equals(type))
			drive.type = PhysicalDrive.Type.HARDDISK;
		else if ("cd".equals(type))
			drive.type = PhysicalDrive.Type.CDROM;
		else {
			LCSystem.log.warn("Unknown device type '" + type + "' for " + devnode);
			drive.type = PhysicalDrive.Type.UNKNOWN;
		}
		if ("ata".equals(bus))
			drive.itype = PhysicalDrive.InterfaceType.ATA;
		else if ("usb".equals(bus))
			drive.itype = PhysicalDrive.InterfaceType.USB;
		else {
			LCSystem.log.warn("Unknown bus type '" + bus + "' for " + devnode);
			drive.itype = PhysicalDrive.InterfaceType.Unknown;
		}
		drive.removable = "1".equals(removable);
		drive.size = size;
		
		readPartitions(drive);
		newDrive(drive);
	}

	// TODO synchronized
	private void newPartition(Udev udev, Udev.UdevDevice device, List<String[]> mountsLines) {
		String devpath = udev.udev_device_get_property_value(device, "DEVPATH");

		PhysicalDriveUnix drive = null;
		for (Drive d : drives) {
			if (!(d instanceof PhysicalDriveUnix)) continue;
			PhysicalDriveUnix pd = (PhysicalDriveUnix)d;
			if (devpath.startsWith(pd.devpath + '/')) {
				drive = pd;
				break;
			}
		}

		if (drive == null)
			LCSystem.log.warn("Partition on unknown drive: " + devpath);
		else {
			String osId = udev.udev_device_get_devnode(device);
			boolean found = false;
			for (DiskPartition p : drive.partitions)
				if (p.OSID != null && p.OSID.equals(osId)) {
					found = true;
					break;
				}
			if (found)
				return;
			DiskPartition p = new DiskPartition();
			p.drive = drive;
			p.OSID = osId;
			String s = udev.udev_device_get_property_value(device, "PARTN");
			if (s != null)
				try { p.index = Integer.parseInt(s); }
				catch (NumberFormatException e) { /* ignore */ }
			// TODO continue
			addMountPoint(p, mountsLines);
			
			drive.partitions.add(p);
			newPartition(p);
		}
	}
	
	private void deviceRemoved(Udev udev, Udev.UdevDevice device) {
		String devnode = udev.udev_device_get_devnode(device);
		String devtype = udev.udev_device_get_devtype(device);
		if ("disk".equals(devtype)) {
			if (!devnode.startsWith("/dev/loop") && !devnode.startsWith("/dev/ram")) {
				diskRemoved(udev, device);
			}
		} else if ("partition".equals(devtype)) {
			partitionRemoved(udev, device);
		}
	}
	
	private void diskRemoved(Udev udev, Udev.UdevDevice device) {
		String devpath = udev.udev_device_get_property_value(device, "DEVPATH");
		Drive drive = null;
		synchronized (drives) {
			for (Iterator<Drive> it = drives.iterator(); it.hasNext(); ) {
				Drive d = it.next();
				if ((d instanceof PhysicalDriveUnix) && ((PhysicalDriveUnix)d).devpath.equals(devpath)) {
					it.remove();
					drive = d;
					break;
				}
			}
		}
		if (drive == null) return;
		LCSystem.log.info("Drive removed: " + drive);
		List<DriveListener> listeners;
		synchronized (this.listeners) {
			listeners = new ArrayList<>(this.listeners);
		}
		for (DriveListener listener : listeners)
			listener.driveRemoved(drive);
	}

	private void partitionRemoved(Udev udev, Udev.UdevDevice device) {
		String devnode = udev.udev_device_get_devnode(device);

		DiskPartition part = null;
		synchronized (drives) {
			for (Drive d : drives) {
				if (!(d instanceof PhysicalDriveUnix)) continue;
				for (Iterator<DiskPartition> it = ((PhysicalDriveUnix)d).partitions.iterator(); it.hasNext(); ) {
					DiskPartition p = it.next();
					if (p.OSID != null && p.OSID.equals(devnode)) {
						part = p;
						it.remove();
						break;
					}
				}
				if (part != null) break;
			}
		}
		
		if (part == null) return;
		LCSystem.log.info("Partition removed: " + part);
		List<DriveListener> listeners;
		synchronized (this.listeners) {
			listeners = new ArrayList<>(this.listeners);
		}
		for (DriveListener listener : listeners)
			listener.partitionRemoved(part);
	}
	
	private void readPartitions(PhysicalDriveUnix drive) {
    	try (IO stream = openReadOnly(drive, Task.PRIORITY_IMPORTANT)) {
    		List<DiskPartition> partitions = new ArrayList<DiskPartition>();
    		DiskPartitionsUtil.readPartitionTable((IO.Readable.Seekable)stream, partitions);
    		for (DiskPartition p : partitions) {
    			boolean found = false;
    			for (DiskPartition dp : drive.partitions) {
    				if (dp.start == p.start) {
    					found = true;
    					dp.partitionSlotIndex = p.partitionSlotIndex;
    					dp.nbSectors = p.nbSectors;
    					dp.startCylinder = p.startCylinder;
    					dp.startHead = p.startHead;
    					dp.startSector = dp.startSector;
    					dp.endCylinder = p.endCylinder;
    					dp.endHead = p.endHead;
    					dp.endSector = p.endSector;
    					dp.lba = p.lba;
    					break;
    				}
    			}
    			if (!found) {
    				p.drive = drive;
    				drive.partitions.add(p);
    				newPartition(p);
    			}
    		}
    	} catch (Throwable t) {
    		// TODO
    	}
	}
	
	private void newDrive(Drive drive) {
		List<DriveListener> listeners;
		synchronized (this.listeners) {
			listeners = new ArrayList<>(this.listeners);
		}
		synchronized (drives) {
			drives.add(drive);
		}
		logDrive(drive);
		for (DriveListener listener : listeners)
			listener.newDrive(drive);
	}
	
	private void newPartition(DiskPartition p) {
		List<DriveListener> listeners;
		synchronized (this.listeners) {
			listeners = new ArrayList<>(this.listeners);
		}
		logPartition(p);
		for (DriveListener listener : listeners)
			listener.newPartition(p);
	}
	
	private static List<String[]> readMounts() {
		File f = new File("/proc/self/mounts");
		if (!f.exists()) return null;
		Pattern whitespaces = Pattern.compile("\\s+");
		try {
			// it is a special file, and file system returns a 0 length file, so we cannot read it in the usual way
			List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
			LinkedList<String[]> result = new LinkedList<>();
			for (String line : lines) {
				line = line.trim();
				String[] tokens = whitespaces.split(line);
				if (tokens.length < 3) continue;
				if (!tokens[0].startsWith("/dev/")) continue;
				result.add(tokens);
			}
			return result;
		} catch (Throwable t) {
			LCSystem.log.error("Unable to parse /proc/self/mounts", t);
			return null;
		}
	}
	
	private static void addMountPoint(DiskPartition dp, List<String[]> mountsLines) {
		if (mountsLines == null) return;
		for (String[] tokens : mountsLines) {
			if (dp.OSID.equals(tokens[0])) {
				dp.mountPoint = new File(tokens[1]);
				return;
			}
		}
	}

	private void addAdditionalPartitions(List<String[]> mountsLines) {
		if (mountsLines == null) return;
		for (String[] tokens : mountsLines) {
			PhysicalDriveUnix dr = null;
			DiskPartition newMountPoint = null;
			synchronized (drives) {
				for (Drive d : drives) {
					if (!(d instanceof PhysicalDriveUnix)) continue;
					PhysicalDriveUnix drive = (PhysicalDriveUnix)d;
					if (!tokens[0].startsWith(drive.OSID)) continue;
					boolean found = false;
					for (DiskPartition p : drive.partitions) {
						if (p.OSID.equals(tokens[0])) {
							found = true;
							if (p.mountPoint == null) {
								p.mountPoint = new File(tokens[1]);
								newMountPoint = p;
							}
							break;
						}
					}
					if (!found)
						dr = drive;
					break;
				}
			}
			if (dr != null) {
				DiskPartition dp = new DiskPartition();
				dp.mountPoint = new File(tokens[1]);
				dp.drive = dr;
				dp.OSID = tokens[0];
				dr.partitions.add(dp);
				newPartition(dp);
			} else if (newMountPoint != null)
				newPartition(newMountPoint);
		}
	}
	
	private static void logDrive(Drive drive) {
		if (drive instanceof PhysicalDriveUnix)
			logPhysicalDrive((PhysicalDriveUnix)drive);
	}
	
	private static void logPhysicalDrive(PhysicalDriveUnix drive) {
		StringBuilder s = new StringBuilder(128);
		s.append("Drive detected: ");
		s.append(drive.OSID);
		s.append(" (").append(drive.toString()).append(")");
		s.append(" type ").append(drive.type).append(" bus ").append(drive.itype);
		LCSystem.log.info(s.toString());
	}
	
	private static void logPartition(DiskPartition p) {
		StringBuilder s = new StringBuilder(128);
		s.append("Partition detected: ");
		s.append(p.OSID);
		File m = p.mountPoint;
		if (m == null)
			s.append(" not mounted");
		else
			s.append(" mounted on ").append(m.getAbsolutePath());
		s.append(" on drive ");
		s.append(p.drive.toString());
		LCSystem.log.info(s.toString());
	}
	
}
