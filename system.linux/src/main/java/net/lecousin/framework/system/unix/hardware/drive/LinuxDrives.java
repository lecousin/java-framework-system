package net.lecousin.framework.system.unix.hardware.drive;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.FileIO;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.drive.DiskPartition;
import net.lecousin.framework.system.hardware.drive.DiskPartitionTable;
import net.lecousin.framework.system.hardware.drive.Drive;
import net.lecousin.framework.system.hardware.drive.DriveListener;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.system.hardware.drive.PhysicalDrive;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.LibC;
import net.lecousin.framework.system.unix.jna.Udev;
import net.lecousin.framework.system.unix.jna.LibC.FDSet;
import net.lecousin.framework.system.unix.jna.LibC.TimeVal;
import net.lecousin.framework.util.AsyncCloseable;

/** Drives implementation for Linux with udev. */
public class LinuxDrives extends Drives {
	
	public LinuxDrives() {
		init();
	}

	@Override
	protected void initializeDrives(WorkProgress progress) {
		initDrives(progress);
		progress.done();
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
	
	@Override
	public Collection<DriveListener> getDriveListeners() {
		synchronized (listeners) {
			return new ArrayList<>(listeners);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IO.Readable.Seekable & IO.KnownSize> T openReadOnly(PhysicalDrive drive, Priority priority) throws AccessDeniedException {
		File f = new File(drive.getOSId());
		if (!f.canRead())
			throw new AccessDeniedException(drive.getOSId());
		return (T)new FileIO.ReadOnly(f, priority);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends IO.Writable.Seekable & IO.KnownSize> T openWriteOnly(PhysicalDrive drive, Priority priority) throws AccessDeniedException {
		File f = new File(drive.getOSId());
		if (!f.canWrite())
			throw new AccessDeniedException(drive.getOSId());
		return (T)new FileIO.WriteOnly(f, priority);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends  IO.Readable.Seekable & IO.KnownSize & IO.Writable.Seekable> T openReadWrite(PhysicalDrive drive, Priority priority)
	throws AccessDeniedException {
		File f = new File(drive.getOSId());
		if (!f.canRead() || !f.canWrite())
			throw new AccessDeniedException(drive.getOSId());
		return (T)new FileIO.ReadWrite(f, priority);
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
		private Async<Exception> closing = null;
		
		@Override
		public void run() {
			Udev udev = JnaInstances.udev;
			Udev.UdevMonitor monitor = udev.udev_monitor_new_from_netlink(handle, "udev");
			udev.udev_monitor_filter_add_match_subsystem_devtype(monitor, "block", null);
			udev.udev_monitor_enable_receiving(monitor);
			int fd = udev.udev_monitor_get_fd(monitor);
			TimeVal tv;
			FDSet fds;
			synchronized (LinuxDrives.class) {
				fds = new FDSet();
				fds.FD_ZERO();
				fds.FD_SET(fd);
				tv = new TimeVal(2, 0);
			}
			while (closing == null) {
				synchronized (LinuxDrives.class) {
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
				} catch (Exception t) {
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
		public IAsync<Exception> closeAsync() {
			if (closing != null) return closing;
			closing = new Async<>();
			return closing;
		}
	}

	private class MonitorMounts extends Thread implements AsyncCloseable<Exception> {
		public MonitorMounts() {
			super("Mount points Monitor");
		}
		
		private Async<Exception> closing = null;
		
		@Override
		public void run() {
			int fd;
			LibC.PollFD[] pfd;
			synchronized (LinuxDrives.class) {
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
				} catch (Exception t) {
					LCSystem.log.error("Error analyzing mount points", t);
				}
			}

			LibC.INSTANCE.close(fd);
			closing.unblock();
		}
		
		@Override
		public IAsync<Exception> closeAsync() {
			if (closing != null) return closing;
			closing = new Async<>();
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
		String vendor = udev.udev_device_get_property_value(device, "ID_VENDOR");
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
				
		LinuxPhysicalDrive drive = new LinuxPhysicalDrive();
		drive.devpath = devpath;
		drive.osId = devnode;
		drive.manufacturer = vendor;
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
		else if ("scsi".equals(bus))
			drive.itype = PhysicalDrive.InterfaceType.SCSI;
		else {
			LCSystem.log.warn("Unknown bus type '" + bus + "' for " + devnode);
			drive.itype = PhysicalDrive.InterfaceType.Unknown;
		}
		drive.removable = "1".equals(removable);
		drive.size = size;
		
		readPartitions(drive);
		signalNewDrive(drive);
	}

	// TODO synchronized
	private void newPartition(Udev udev, Udev.UdevDevice device, List<String[]> mountsLines) {
		String devpath = udev.udev_device_get_property_value(device, "DEVPATH");

		LinuxPhysicalDrive drive = null;
		for (Drive d : drives) {
			if (!(d instanceof LinuxPhysicalDrive)) continue;
			LinuxPhysicalDrive pd = (LinuxPhysicalDrive)d;
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
			p.filesystem = udev.udev_device_get_property_value(device, "ID_FS_TYPE");
			
			String s = udev.udev_device_get_property_value(device, "ID_PART_ENTRY_OFFSET");
			if (s != null) {
				try {
					p.start = Long.parseLong(s);
				} catch (Exception e) {
					// ignore
				}
			} else {
				s = udev.udev_device_get_sysattr_value(device, "start");
				if (s != null) {
					try {
						p.start = Long.parseLong(s);
					} catch (Exception e) {
						// ignore
					}
				}
			}
			
			s = udev.udev_device_get_property_value(device, "ID_PART_ENTRY_SIZE");
			if (s != null) {
				try {
					p.size = Long.parseLong(s);
				} catch (Exception e) {
					// ignore
				}
			} else {
				s = udev.udev_device_get_sysattr_value(device, "size");
				if (s != null) {
					try {
						p.size = Long.parseLong(s);
					} catch (Exception e) {
						// ignore
					}
				}
			}
			
			s = udev.udev_device_get_property_value(device, "PARTN");
			if (s != null)
				try { p.index = Integer.parseInt(s); }
				catch (NumberFormatException e) { /* ignore */ }
			// TODO continue
			addMountPoint(p, mountsLines);
			
			drive.partitions.add(p);
			signalNewPartition(p);
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
				if ((d instanceof LinuxPhysicalDrive) && ((LinuxPhysicalDrive)d).devpath.equals(devpath)) {
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
				if (!(d instanceof LinuxPhysicalDrive)) continue;
				for (Iterator<DiskPartition> it = ((LinuxPhysicalDrive)d).partitions.iterator(); it.hasNext(); ) {
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
	
	private void readPartitions(LinuxPhysicalDrive drive) {
    	try (IO stream = openReadOnly(drive, Task.Priority.IMPORTANT)) {
    		List<DiskPartition> partitions = new ArrayList<>();
    		DiskPartitionTable.readPartitionTable((IO.Readable.Seekable)stream, partitions);
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
    				signalNewPartition(p);
    			}
    		}
    	} catch (AccessDeniedException e) {
    		LCSystem.log.info("Not enough privileges to access to drive " + drive);
    	} catch (Exception e) {
    		LCSystem.log.error("Unable to read partitions for drive " + drive, e);
    	}
	}
	
	private void signalNewDrive(Drive drive) {
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
	
	private void signalNewPartition(DiskPartition p) {
		List<DriveListener> listenersToCall;
		synchronized (this.listeners) {
			listenersToCall = new ArrayList<>(this.listeners);
		}
		logPartition(p);
		for (DriveListener listener : listenersToCall)
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
		} catch (Exception t) {
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
			LinuxPhysicalDrive dr = null;
			DiskPartition newMountPoint = null;
			synchronized (drives) {
				for (Drive d : drives) {
					if (!(d instanceof LinuxPhysicalDrive)) continue;
					LinuxPhysicalDrive drive = (LinuxPhysicalDrive)d;
					if (!tokens[0].startsWith(drive.osId)) continue;
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
				signalNewPartition(dp);
			} else if (newMountPoint != null) {
				signalNewPartition(newMountPoint);
			}
		}
	}
	
	private static void logDrive(Drive drive) {
		if (drive instanceof LinuxPhysicalDrive)
			logPhysicalDrive((LinuxPhysicalDrive)drive);
	}
	
	private static void logPhysicalDrive(LinuxPhysicalDrive drive) {
		StringBuilder s = new StringBuilder(128);
		s.append("Drive detected: ");
		s.append(drive.osId);
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
