package net.lecousin.framework.system.unix.hardware.drive;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation.CFBooleanRef;
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFIndex;
import com.sun.jna.platform.mac.CoreFoundation.CFMutableDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFNumberRef;
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef;
import com.sun.jna.platform.mac.DiskArbitration.DADiskRef;
import com.sun.jna.platform.mac.DiskArbitration.DASessionRef;
import com.sun.jna.platform.mac.IOKit.IOIterator;
import com.sun.jna.platform.mac.IOKit.IORegistryEntry;
import com.sun.jna.platform.mac.IOKitUtil;
import com.sun.jna.platform.mac.SystemB;
import com.sun.jna.platform.mac.SystemB.Statfs;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.threads.Task.Priority;
import net.lecousin.framework.io.IO.KnownSize;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.mutable.Mutable;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.drive.DiskPartition;
import net.lecousin.framework.system.hardware.drive.Drive;
import net.lecousin.framework.system.hardware.drive.DriveListener;
import net.lecousin.framework.system.hardware.drive.Drives;
import net.lecousin.framework.system.hardware.drive.PhysicalDrive;
import net.lecousin.framework.system.hardware.drive.PhysicalDrive.InterfaceType;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration;
import net.lecousin.framework.system.unix.jna.mac.IOKit;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.ProcessUtil;

/**
 * Drives implementation for Mac OS.
 */
public class DrivesMac extends Drives {
	
	private static final String DEV_ROOT = "/dev/";

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

	private void initDrives(WorkProgress progress) {
		DiskArbitration da = JnaInstances.diskArbitration;
		DASessionRef session = da.DASessionCreate(JnaInstances.ALLOCATOR);
		
        List<String> bsdNames = new ArrayList<>();
        IOIterator iter = IOKitUtil.getMatchingServices("IOMedia");
        IORegistryEntry media = iter.next();
        while (media != null) {
        	//if (IOKit.Util.getIORegistryBooleanProperty(media, "Whole")) {
        	DADiskRef disk = da.DADiskCreateFromIOMedia(JnaInstances.ALLOCATOR, session, media);
        	String name = da.DADiskGetBSDName(disk);
        	bsdNames.add(name);
        	//}
        	JnaInstances.ioKit.IOObjectRelease(media);
        	media = iter.next();
        }
		
        Mutable<List<Pair<String, String>>> bsdNamesMountPoints = new Mutable<>(null);
        Mutable<List<DiskImageInfo>> diskImages = new Mutable<>(null);
		for (String name : bsdNames) {
			DADiskRef disk = da.DADiskCreateFromBSDName(JnaInstances.ALLOCATOR, session, DEV_ROOT + name);
			if (disk != null) {
				newDisk(disk, bsdNamesMountPoints, diskImages);
				JnaInstances.coreFoundation.CFRelease(disk);
			}
		}
		bsdNamesMountPoints = null;
		diskImages = null;

		da.DARegisterDiskAppearedCallback(session, null,
			(disk, context) -> newDisk(disk, new Mutable<>(null), new Mutable<>(null)), null);
		da.DARegisterDiskDescriptionChangedCallback(session, null, null, 
			(disk, keys, context) -> { /* TODO diskChanged(disk);*/ }, null);
		da.DARegisterDiskDisappearedCallback(session, null,
			(disk, context) -> diskRemoved(disk), null);
		
		da.DASessionScheduleWithRunLoop(
			session, JnaInstances.coreFoundation.CFRunLoopGetMain(), CFStringRef.createCFString("kCFRunLoopDefaultMode")
		);
		
		LCCore.get().toClose(() -> JnaInstances.coreFoundation.CFRelease(session));
	}
	
	private static class DiskImageInfo {
		private String path;
		private List<String> devices = new LinkedList<>();
	}
	
	private static List<DiskImageInfo> hdiutilInfo() {
		List<DiskImageInfo> infos = new LinkedList<>();
		List<String> lines = new LinkedList<>();
		try {
			Process process = Runtime.getRuntime().exec("hdiutil info");
			ProcessUtil.consumeProcessConsole(process, lines::add, line -> { });
			int exitCode = process.waitFor();
			if (exitCode != 0) return infos;
		} catch (Exception t) {
			LCSystem.log.error("Error running command hdiutil info", t);
			return infos;
		}
		DiskImageInfo di = null;
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty()) continue;
			if (line.equals("================================================")) {
				if (di != null)
					infos.add(di);
				di = new DiskImageInfo();
				continue;
			}
			if (di == null) continue;
			if (line.length() > 16 && line.charAt(16) == ':') {
				String key = line.substring(0, 15).trim();
				String value = line.length() > 18 ? line.substring(18).trim() : "";
				if ("image-path".equals(key))
					di.path = value;
			} else if (line.startsWith(DEV_ROOT)) {
				int i = line.indexOf(' ');
				String dev = i < 0 ? line : line.substring(0, i);
				di.devices.add(dev);
			}
		}
		if (di != null)
			infos.add(di);
		return infos;
	}
	
	private static List<Pair<String, String>> getMountPoints() {
        int numfs = JnaInstances.systemB.getfsstat64(null, 0, 0);
        Statfs[] fs = new Statfs[numfs];
        JnaInstances.systemB.getfsstat64(fs, numfs * new Statfs().size(), SystemB.MNT_NOWAIT);
        List<Pair<String, String>> list = new ArrayList<>(numfs);
        for (Statfs f : fs) {
            String mntFrom = new String(f.f_mntfromname).trim();
            String mntPath = new String(f.f_mntonname).trim();
            if (mntFrom.isEmpty() || mntPath.isEmpty())
            	continue;
            list.add(new Pair<>(mntFrom, mntPath));
        }
        return list;
	}
	
	private static File getMountPointFromDeviceName(String deviceName, Mutable<List<Pair<String, String>>> bsdNamesMountPoints) {
		if (bsdNamesMountPoints.get() == null)
			bsdNamesMountPoints.set(getMountPoints());
		for (Pair<String, String> p : bsdNamesMountPoints.get())
			if (deviceName.equals(p.getValue1()))
				return new File(p.getValue2());
		return null;
	}
	
	private static final CFStringRef strDADeviceModel = CFStringRef.createCFString("DADeviceModel");
    private static final CFStringRef strDAMediaSize = CFStringRef.createCFString("DAMediaSize");
    private static final CFStringRef strDAMediaBSDName = CFStringRef.createCFString("DAMediaBSDName");
	private static final CFStringRef strDAMediaWhole = CFStringRef.createCFString("DAMediaWhole");
	private static final CFStringRef strDABusPath = CFStringRef.createCFString("DABusPath");
	private static final CFStringRef strDABusName = CFStringRef.createCFString("DABusName");
	private static final CFStringRef strDADeviceVendor = CFStringRef.createCFString("DADeviceVendor");
	private static final CFStringRef strDAMediaRemovable = CFStringRef.createCFString("DAMediaRemovable");
	private static final CFStringRef strDADeviceRevision = CFStringRef.createCFString("DADeviceRevision");
    private static final CFStringRef strDAVolumeKind = CFStringRef.createCFString("DAVolumeKind");
    private static final CFStringRef strDAVolumeName = CFStringRef.createCFString("DAVolumeName");
    private static final CFStringRef strModel = CFStringRef.createCFString("Model");
    private static final CFStringRef strIOPropertyMatch = CFStringRef.createCFString("IOPropertyMatch");
	
	private void newDisk(DADiskRef disk, Mutable<List<Pair<String, String>>> bsdNamesMountPoints, Mutable<List<DiskImageInfo>> diskImages) {
		DiskArbitration da = JnaInstances.diskArbitration;
		CFDictionaryRef diskInfo = da.DADiskCopyDescription(disk);
		if (diskInfo == null) return;
		
		Pointer ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDADeviceModel);
		String model = ptr != null ? new CFStringRef(ptr).stringValue() : "null";
		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAMediaSize);
		long size = ptr == null ? -1 : new CFNumberRef(ptr).longValue();

		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAMediaBSDName);
		String bsdName = ptr == null ? null : new CFStringRef(ptr).stringValue();
		
		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAMediaWhole);
		if (ptr != null && new CFBooleanRef(ptr).booleanValue()) {
			if ("Disk Image".equals(model)) {
				newDiskImage(bsdName, bsdNamesMountPoints, diskImages);
				return;
			}
			PhysicalDriveUnix drive = new PhysicalDriveUnix();
			drive.devpath = bsdName;
			drive.model = model;
			drive.size = BigInteger.valueOf(size);
			ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDABusPath);
			drive.osId = ptr == null ? null : new CFStringRef(ptr).stringValue();
			ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDADeviceVendor);
			drive.manufacturer = ptr == null ? null : new CFStringRef(ptr).stringValue();
			ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAMediaRemovable);
			drive.removable = ptr == null ? false : new CFBooleanRef(ptr).booleanValue();
			ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDADeviceRevision);
			drive.version = ptr == null ? null : new CFStringRef(ptr).stringValue();
			ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDABusName);
			String s = ptr == null ? null : new CFStringRef(ptr).stringValue();
			if ("scsi".equals(s))
				drive.itype = InterfaceType.SCSI;
			else
				drive.itype = InterfaceType.Unknown; // TODO
			drive.type = PhysicalDrive.Type.UNKNOWN; // TODO
			
			CFStringRef modelNameRef = CFStringRef.createCFString(model);
			CFMutableDictionaryRef propertyDict = JnaInstances.coreFoundation.CFDictionaryCreateMutable(
					JnaInstances.ALLOCATOR, new CFIndex(0), null, null);
			JnaInstances.coreFoundation.CFDictionarySetValue(propertyDict, strModel, modelNameRef);
			CFMutableDictionaryRef matchingDict = JnaInstances.coreFoundation.CFDictionaryCreateMutable(
					JnaInstances.ALLOCATOR, new CFIndex(0), null, null);
			JnaInstances.coreFoundation.CFDictionarySetValue(matchingDict, strIOPropertyMatch, propertyDict);

			// getMatchingServices releases matchingDict
			IOIterator serviceIterator = IOKitUtil.getMatchingServices(matchingDict);
			if (serviceIterator != null) {
				IORegistryEntry sdService = serviceIterator.next();
				while (sdService != null) {
					// look up the serial number
					drive.serial = IOKit.Util.getIORegistryStringProperty(sdService, "Serial Number");
					JnaInstances.ioKit.IOObjectRelease(sdService);
					if (drive.serial != null)
						break;
					// iterate
					sdService = serviceIterator.next();
				}
				serviceIterator.release();
			}
			JnaInstances.coreFoundation.CFRelease(modelNameRef);
			JnaInstances.coreFoundation.CFRelease(propertyDict);
			newDrive(drive);
			return;
		}
		// volume
		
		DiskPartition part = new DiskPartition();
		part.size = size;
		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDABusPath);
		String osId = ptr == null ? null : new CFStringRef(ptr).stringValue();
		synchronized (drives) {
			for (Drive d : drives)
				if (((PhysicalDriveUnix)d).osId != null && ((PhysicalDriveUnix)d).osId.equals(osId)) {
					part.drive = d;
					break;
				}
		}
		if (part.drive == null) return;
		part.OSID = bsdName;
		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAVolumeKind);
		part.filesystem = ptr == null ? null : new CFStringRef(ptr).stringValue();
		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAVolumeName);
		part.name = ptr == null ? null : new CFStringRef(ptr).stringValue();
		part.mountPoint = getMountPointFromDeviceName(DEV_ROOT + bsdName, bsdNamesMountPoints);
		for (DiskPartition p : ((PhysicalDriveUnix)part.drive).partitions) {
			if (p.OSID.equals(bsdName))
				return;
		}
		((PhysicalDriveUnix)part.drive).partitions.add(part);
		newPartition(part);
	}

	private void diskRemoved(DADiskRef disk) {
		DiskArbitration da = JnaInstances.diskArbitration;
		CFDictionaryRef diskInfo = da.DADiskCopyDescription(disk);
		if (diskInfo == null) return;
		
		Pointer ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDADeviceModel);
		String model = ptr != null ? new CFStringRef(ptr).stringValue() : "null";

		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAMediaBSDName);
		String bsdName = ptr == null ? null : new CFStringRef(ptr).stringValue();
		
		ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, strDAMediaWhole);
		if (ptr != null && new CFBooleanRef(ptr).booleanValue()) {
			if ("Disk Image".equals(model)) {
				diskImageRemoved(bsdName);
				return;
			}
			PhysicalDriveUnix drive = null;
			synchronized (drives) {
				for (Iterator<Drive> it = drives.iterator(); it.hasNext(); ) {
					PhysicalDriveUnix d = (PhysicalDriveUnix)it.next();
					if (d.devpath.equals(bsdName)) {
						drive = d;
						it.remove();
						break;
					}
				}
			}
			if (drive != null)
				driveRemoved(drive);
			return;
		}
		// volume

		DiskPartition part = null;
		synchronized (drives) {
			for (Drive d : drives)
				for (Iterator<DiskPartition> it = ((PhysicalDriveUnix)d).partitions.iterator(); it.hasNext(); ) {
					DiskPartition p = it.next();
					if (p.OSID.equals(bsdName)) {
						part = p;
						it.remove();
						break;
					}
				}
		}
		if (part == null) return;
		partitionRemoved(part);
	}
	
	private void newDrive(PhysicalDriveUnix drive) {
		LCSystem.log.info("New drive on " + drive.devpath + " (" + drive.osId + "): " + drive);
		List<DriveListener> listenersToCall;
		synchronized (this.listeners) {
			listenersToCall = new ArrayList<>(this.listeners);
		}
		synchronized (drives) {
			for (Drive d : drives)
				if ((d instanceof PhysicalDriveUnix) && ((PhysicalDriveUnix)d).devpath.equals(drive.devpath)) {
					LCSystem.log.info("Drive already known: " + drive);
					return;
				}
			drives.add(drive);
		}
		for (DriveListener listener : listenersToCall)
			listener.newDrive(drive);
	}
	
	private void driveRemoved(PhysicalDriveUnix drive) {
		LCSystem.log.info("Drive removed on " + drive.devpath + " (" + drive.osId + "): " + drive);
		List<DriveListener> listenersToCall;
		synchronized (this.listeners) {
			listenersToCall = new ArrayList<>(this.listeners);
		}
		for (DriveListener listener : listenersToCall)
			listener.driveRemoved(drive);
	}
	
	private void newPartition(DiskPartition part) {
		LCSystem.log.info("New partition: " + part);
		List<DriveListener> listenersToCall;
		synchronized (this.listeners) {
			listenersToCall = new ArrayList<>(this.listeners);
		}
		for (DriveListener listener : listenersToCall)
			listener.newPartition(part);
	}
	
	private void partitionRemoved(DiskPartition part) {
		LCSystem.log.info("Partition removed: " + part);
		List<DriveListener> listenersToCall;
		synchronized (this.listeners) {
			listenersToCall = new ArrayList<>(this.listeners);
		}
		for (DriveListener listener : listenersToCall)
			listener.partitionRemoved(part);
	}
	
	private void newDiskImage(String bsdName, Mutable<List<Pair<String, String>>> bsdNamesMountPoints, Mutable<List<DiskImageInfo>> diskImages) {
		if (diskImages.get() == null)
			diskImages.set(hdiutilInfo());
		if (diskImages.get() == null)
			return;
		for (DiskImageInfo di : diskImages.get()) {
			File mountPoint = null;
			for (String dev : di.devices) {
				mountPoint = getMountPointFromDeviceName(dev, bsdNamesMountPoints);
				if (mountPoint != null) break;
			}
			for (String dev : di.devices)
				if (dev.equals(bsdName)) {
					DiskPartition part = null;
					synchronized (drives) {
						for (Drive d : drives) {
							for (DiskPartition p : ((PhysicalDriveUnix)d).partitions)
								if (p.mountPoint != null && di.path.startsWith(p.mountPoint.getAbsolutePath())) {
									part = p;
									break;
								}
						}
					}
					if (part != null) {
						DiskPartition p = new DiskPartition();
						p.drive = part.drive;
						p.mountPoint = mountPoint;
						p.OSID = bsdName;
						newPartition(p);
					}
					return;
				}
		}
	}
	
	private void diskImageRemoved(String bsdName) {
		DiskPartition part = null;
		synchronized (drives) {
			for (Drive d : drives) {
				for (Iterator<DiskPartition> it = ((PhysicalDriveUnix)d).partitions.iterator(); it.hasNext(); ) {
					DiskPartition p = it.next();
					if (p.OSID.equals(bsdName)) {
						it.remove();
						part = p;
						break;
					}
				}
				if (part != null)
					break;
			}
		}
		if (part != null)
			partitionRemoved(part);
	}

	@Override
	public <T extends Seekable & KnownSize> T openReadOnly(PhysicalDrive drive, Priority priority) throws IOException {
		// TODO
		throw new IOException("Open drive not supported on MAC by library net.lecousin.system.unix");
	}

	@Override
	public <T extends net.lecousin.framework.io.IO.Writable.Seekable & KnownSize> T
	openWriteOnly(PhysicalDrive drive, Priority priority) throws IOException {
		// TODO
		throw new IOException("Open drive not supported on MAC by library net.lecousin.system.unix");
	}

	@Override
	public <T extends Seekable & KnownSize & net.lecousin.framework.io.IO.Writable.Seekable> T
	openReadWrite(PhysicalDrive drive, Priority priority) throws IOException {
		// TODO
		throw new IOException("Open drive not supported on MAC by library net.lecousin.system.unix");
	}
	
}
