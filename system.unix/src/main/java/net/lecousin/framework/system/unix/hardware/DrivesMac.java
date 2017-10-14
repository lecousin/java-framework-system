package net.lecousin.framework.system.unix.hardware;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.io.IO.KnownSize;
import net.lecousin.framework.io.IO.Readable.Seekable;
import net.lecousin.framework.mutable.Mutable;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.DiskPartition;
import net.lecousin.framework.system.hardware.Drive;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.hardware.PhysicalDrive;
import net.lecousin.framework.system.hardware.PhysicalDrive.InterfaceType;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFArrayRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFDictionaryRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFMutableDictionaryRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFStringRef;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration.DADiskAppearedCallback;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration.DADiskDescriptionChangedCallback;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration.DADiskDisappearedCallback;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration.DADiskRef;
import net.lecousin.framework.system.unix.jna.mac.DiskArbitration.DASessionRef;
import net.lecousin.framework.system.unix.jna.mac.IOKit;
import net.lecousin.framework.system.unix.jna.mac.SystemB;
import net.lecousin.framework.system.unix.jna.mac.SystemB.Statfs;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.ProcessUtil;

/**
 * Drives implementation for Mac OS.
 */
public class DrivesMac extends Drives {

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

	@SuppressWarnings("resource")
	private void initDrives(WorkProgress progress) {
		DiskArbitration da = JnaInstances.diskArbitration;
		DASessionRef session = da.DASessionCreate(CoreFoundation.ALLOCATOR);
		
        List<String> bsdNames = new ArrayList<>();
        IntByReference iter = new IntByReference();
        IOKit.Util.getMatchingServices("IOMedia", iter);
        int media = IOKit.INSTANCE.IOIteratorNext(iter.getValue());
        while (media != 0) {
        	//if (IOKit.Util.getIORegistryBooleanProperty(media, "Whole")) {
        	DADiskRef disk = da.DADiskCreateFromIOMedia(CoreFoundation.ALLOCATOR, session, media);
        	String name = da.DADiskGetBSDName(disk);
        	System.out.println("BSD Name: " + name);
        	bsdNames.add(name);
        	//}
        	IOKit.INSTANCE.IOObjectRelease(media);
        	media = IOKit.INSTANCE.IOIteratorNext(iter.getValue());
        }
		
        Mutable<List<Pair<String, String>>> bsdNamesMountPoints = new Mutable<>(null);
        Mutable<List<DiskImageInfo>> diskImages = new Mutable<>(null);
		for (String name : bsdNames) {
			DADiskRef disk = da.DADiskCreateFromBSDName(CoreFoundation.ALLOCATOR, session, "/dev/" + name);
			if (disk != null) {
				newDisk(disk, bsdNamesMountPoints, diskImages);
				CoreFoundation.INSTANCE.CFRelease(disk);
			}
		}
		bsdNamesMountPoints = null;
		diskImages = null;

		da.DARegisterDiskAppearedCallback(session, null, new DADiskAppearedCallback() {
			@Override
			public void callback(DADiskRef disk, Pointer context) {
				newDisk(disk, new Mutable<>(null), new Mutable<>(null));
			}
		}, null);
		da.DARegisterDiskDescriptionChangedCallback(session, null, null, new DADiskDescriptionChangedCallback() {
			@Override
			public void callback(DADiskRef disk, CFArrayRef keys, Pointer context) {
				// TODO diskChanged(disk);
			}
		}, null);
		da.DARegisterDiskDisappearedCallback(session, null, new DADiskDisappearedCallback() {
			@Override
			public void callback(DADiskRef disk, Pointer context) {
				// TODO diskRemoved(disk);
			}
		}, null);
		
		da.DASessionScheduleWithRunLoop(session, CoreFoundation.INSTANCE.CFRunLoopGetMain(), CFStringRef.toCFString("kCFRunLoopDefaultMode"));
		
		LCCore.get().toClose(new Closeable() {
			@Override
			public void close() {
				CoreFoundation.INSTANCE.CFRelease(session);
			}
		});
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
			ProcessUtil.consumeProcessConsole(process, (line) -> { lines.add(line); }, (line) -> { });
			int exitCode = process.waitFor();
			if (exitCode != 0) return infos;
		} catch (Throwable t) {
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
			if (line.length() > 16 && line.charAt(16) == ':') {
				String key = line.substring(0, 15).trim();
				String value = line.length() > 18 ? line.substring(18).trim() : "";
				if ("image-path".equals(key))
					di.path = value;
			} else if (line.startsWith("/dev/")) {
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
        int numfs = SystemB.INSTANCE.getfsstat64(null, 0, 0);
        Statfs[] fs = new Statfs[numfs];
        SystemB.INSTANCE.getfsstat64(fs, numfs * new Statfs().size(), SystemB.MNT_NOWAIT);
        List<Pair<String, String>> list = new ArrayList<>(numfs);
        for (Statfs f : fs) {
            String mntFrom = new String(f.f_mntfromname).trim();
            String mntPath = new String(f.f_mntonname).trim();
            list.add(new Pair<>(mntFrom, mntPath));
            LCSystem.log.info("Mounted filesystem: " + mntFrom + " => " + mntPath);
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
	
	private static final CFStringRef strDADeviceModel = CFStringRef.toCFString("DADeviceModel");
    private static final CFStringRef strDAMediaSize = CFStringRef.toCFString("DAMediaSize");
    private static final CFStringRef strDAMediaBSDName = CFStringRef.toCFString("DAMediaBSDName");
	private static final CFStringRef strDAMediaWhole = CFStringRef.toCFString("DAMediaWhole");
	private static final CFStringRef strDABusPath = CFStringRef.toCFString("DABusPath");
	private static final CFStringRef strDADeviceVendor = CFStringRef.toCFString("DADeviceVendor");
	private static final CFStringRef strDAMediaRemovable = CFStringRef.toCFString("DAMediaRemovable");
	private static final CFStringRef strDADeviceRevision = CFStringRef.toCFString("DADeviceRevision");
    private static final CFStringRef strDAVolumeKind = CFStringRef.toCFString("DAVolumeKind");
    private static final CFStringRef strDAVolumeName = CFStringRef.toCFString("DAVolumeName");
    private static final CFStringRef strModel = CFStringRef.toCFString("Model");
    private static final CFStringRef strIOPropertyMatch = CFStringRef.toCFString("IOPropertyMatch");
	
	private void newDisk(DADiskRef disk, Mutable<List<Pair<String, String>>> bsdNamesMountPoints, Mutable<List<DiskImageInfo>> diskImages) {
		DiskArbitration da = JnaInstances.diskArbitration;
		CFDictionaryRef diskInfo = da.DADiskCopyDescription(disk);
		if (diskInfo == null) return;
		
		Pointer ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDADeviceModel);
		String model = CoreFoundation.Util.cfPointerToString(ptr);
		ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDAMediaSize);
		long size = ptr == null ? -1 : CoreFoundation.Util.cfPointerToLong(ptr);

		ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDAMediaBSDName);
		String bsdName = ptr == null ? null : CoreFoundation.Util.cfPointerToString(ptr);
		
		ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDAMediaWhole);
		if (ptr != null && CoreFoundation.Util.cfPointerToBoolean(ptr)) {
			if ("Disk Image".equals(model)) {
				newDiskImage(bsdName, bsdNamesMountPoints, diskImages);
				return;
			}
			PhysicalDriveUnix drive = new PhysicalDriveUnix();
			drive.devpath = bsdName;
			drive.model = model;
			drive.size = BigInteger.valueOf(size);
			ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDABusPath);
			drive.OSID = ptr == null ? null : CoreFoundation.Util.cfPointerToString(ptr);
			ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDADeviceVendor);
			drive.manufacturer = ptr == null ? null : CoreFoundation.Util.cfPointerToString(ptr);
			ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDAMediaRemovable);
			drive.removable = ptr == null ? false : CoreFoundation.Util.cfPointerToBoolean(ptr);
			ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDADeviceRevision);
			drive.version = ptr == null ? null : CoreFoundation.Util.cfPointerToString(ptr);
			drive.itype = InterfaceType.Unknown; // TODO
			
			CFStringRef modelNameRef = CFStringRef.toCFString(model);
			CFMutableDictionaryRef propertyDict = CoreFoundation.INSTANCE.CFDictionaryCreateMutable(
					CoreFoundation.ALLOCATOR, 0, null, null);
			CoreFoundation.INSTANCE.CFDictionarySetValue(propertyDict, strModel, modelNameRef);
			CFMutableDictionaryRef matchingDict = CoreFoundation.INSTANCE.CFDictionaryCreateMutable(
					CoreFoundation.ALLOCATOR, 0, null, null);
			CoreFoundation.INSTANCE.CFDictionarySetValue(matchingDict, strIOPropertyMatch, propertyDict);

			IntByReference serviceIterator = new IntByReference();
			// getMatchingServices releases matchingDict
			IOKit.Util.getMatchingServices(matchingDict, serviceIterator);
			int sdService = IOKit.INSTANCE.IOIteratorNext(serviceIterator.getValue());
			while (sdService != 0) {
				// look up the serial number
				drive.serial = IOKit.Util.getIORegistryStringProperty(sdService, "Serial Number");
				IOKit.INSTANCE.IOObjectRelease(sdService);
				if (drive.serial != null)
					break;
				// iterate
				sdService = IOKit.INSTANCE.IOIteratorNext(serviceIterator.getValue());
			}
			IOKit.INSTANCE.IOObjectRelease(serviceIterator.getValue());
			CoreFoundation.INSTANCE.CFRelease(modelNameRef);
			CoreFoundation.INSTANCE.CFRelease(propertyDict);
			newDrive(drive);
			return;
		}
		// volume
		
		DiskPartition part = new DiskPartition();
		part.size = size;
		ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDABusPath);
		String OSID = ptr == null ? null : CoreFoundation.Util.cfPointerToString(ptr);
		synchronized (drives) {
			for (Drive d : drives)
				if (((PhysicalDriveUnix)d).OSID != null && ((PhysicalDriveUnix)d).OSID.equals(OSID)) {
					part.drive = d;
					break;
				}
		}
		if (part.drive == null) return;
		ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDAVolumeKind);
		part.filesystem = ptr == null ? null : CoreFoundation.Util.cfPointerToString(ptr);
		ptr = CoreFoundation.INSTANCE.CFDictionaryGetValue(diskInfo, strDAVolumeName);
		part.name = ptr == null ? null : CoreFoundation.Util.cfPointerToString(ptr);
		part.mountPoint = getMountPointFromDeviceName("/dev/" + bsdName, bsdNamesMountPoints);
		((PhysicalDriveUnix)part.drive).partitions.add(part);
		newPartition(part);
	}
	
	private void newDrive(PhysicalDriveUnix drive) {
		LCSystem.log.info("New drive: " + drive);
		List<DriveListener> listeners;
		synchronized (this.listeners) {
			listeners = new ArrayList<>(this.listeners);
		}
		synchronized (drives) {
			drives.add(drive);
		}
		for (DriveListener listener : listeners)
			listener.newDrive(drive);
	}
	
	private void newPartition(DiskPartition part) {
		// TODO log
		List<DriveListener> listeners;
		synchronized (this.listeners) {
			listeners = new ArrayList<>(this.listeners);
		}
		for (DriveListener listener : listeners)
			listener.newPartition(part);
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
						newPartition(p);
					}
					return;
				}
		}
	}

	@Override
	public <T extends Seekable & KnownSize> T openReadOnly(PhysicalDrive drive, byte priority) throws IOException {
		// TODO
		throw new IOException("Open drive not supported on MAC by library net.lecousin.system.unix");
	}

	@Override
	public <T extends net.lecousin.framework.io.IO.Writable.Seekable & KnownSize> T
	openWriteOnly(PhysicalDrive drive, byte priority) throws IOException {
		// TODO
		throw new IOException("Open drive not supported on MAC by library net.lecousin.system.unix");
	}

	@Override
	public <T extends Seekable & KnownSize & net.lecousin.framework.io.IO.Writable.Seekable> T
	openReadWrite(PhysicalDrive drive, byte priority) throws IOException {
		// TODO
		throw new IOException("Open drive not supported on MAC by library net.lecousin.system.unix");
	}
	
}
