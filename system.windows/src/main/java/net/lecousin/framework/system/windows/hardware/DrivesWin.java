package net.lecousin.framework.system.windows.hardware;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.Threading;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IO.KnownSize;
import net.lecousin.framework.io.IO.Readable;
import net.lecousin.framework.io.IO.Writable;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.progress.WorkProgress;
import net.lecousin.framework.progress.WorkProgressImpl;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.DiskPartition;
import net.lecousin.framework.system.hardware.DiskPartitionsUtil;
import net.lecousin.framework.system.hardware.Drive;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.hardware.PhysicalDrive;
import net.lecousin.framework.system.hardware.PhysicalDrive.InterfaceType;
import net.lecousin.framework.system.windows.WMI;
import net.lecousin.framework.system.windows.Win32HandleStream;
import net.lecousin.framework.system.windows.Win32IOException;
import net.lecousin.framework.system.windows.WindowsSystem;
import net.lecousin.framework.system.windows.WindowsUtil;
import net.lecousin.framework.system.windows.jna.Kernel32;

/** Drives implementation for Windows. */
public class DrivesWin extends Drives {

	private WorkProgress init = null;
	
	@Override
	public synchronized WorkProgress initialize() {
		if (init != null) return init;
		init = new WorkProgressImpl(100000, "Loading drives information");
		new Thread("Initializing Drives Information") {
			@Override
			public void run() {
				WindowsSystem.addSystemEventListener(WM_DEVICECHANGE, deviceChangeListener);
				loadDrives(init);
				LCSystem.log.info("Drives information initialized");
				init.done();
			}
		}.start();
		return init;
	}
	
	
	private static final int WM_DEVICECHANGE = 0x219;
	private static final int DBT_DEVICE_ARRIVAL = 0x8000;
	private static final int DBT_DEVICE_REMOVECOMPLETE = 0x8004;
	private static final int DBT_DEVNODES_CHANGED = 0x0007; // A device has been added to or removed from the system.
	private WindowsSystem.WindowsListener deviceChangeListener = (eventId, uParam, lParam) -> {
		switch (uParam.intValue()) {
		case DBT_DEVICE_ARRIVAL:
		case DBT_DEVICE_REMOVECOMPLETE:
		case DBT_DEVNODES_CHANGED: // without this one, sometimes USB are not detected
			loadDrives(null);
			break;
		default: break;
		}
	};

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
	public <T extends Readable.Seekable & KnownSize> T openReadOnly(PhysicalDrive drive, byte priority) throws IOException {
		if (!(drive instanceof PhysicalDriveWin)) throw new IOException("Invalid drive");
		PhysicalDriveWin d = (PhysicalDriveWin)drive;
		HANDLE h = openDevice(d.osId, true, false);
		Object taskManagerResource = Threading.CPU;
		for (Object o : Threading.getDrivesTaskManager().getResources())
			if (o == drive) {
				taskManagerResource = o;
				break;
			}
		return (T)new Win32HandleStream(h, d.size, d.osId, taskManagerResource, priority);
	}
	
	@Override
	public <T extends Readable.Seekable & KnownSize & Writable.Seekable> T openReadWrite(PhysicalDrive drive, byte priority) throws IOException {
		throw new IOException("Write not supported");
	}
	
	@Override
	public <T extends Writable.Seekable & KnownSize> T openWriteOnly(PhysicalDrive drive, byte priority) throws IOException {
		throw new IOException("Write not supported");
	}

	private static HANDLE openDevice(String deviceId, boolean readable, boolean writable) throws IOException {
		HANDLE h = com.sun.jna.platform.win32.Kernel32.INSTANCE.CreateFile(
				"\\\\.\\" + deviceId, 
				(writable ? WinNT.GENERIC_WRITE : 0) | (readable ? 0x80000000 : 0), 
				3, // read and write allowed 
				null, 
				3, // open existing
				0, 
				null);
		if (((int)Pointer.nativeValue(h.getPointer())) != -1)
			return h;
		if (Native.getLastError() == 5)
			throw new AccessDeniedException(deviceId);
		/*
		if (LCSystemWin32.win_maj_ver >= LCSystemWin32.WIN_VISTA) {
			// we may need to elevate privileges of current process
			// TODO
		}*/
		Win32IOException.throwLastError(h);
		return null; // never reached, only for compilation purpose
	}
	
	private void loadDrives(WorkProgress progress) {
		Kernel32 lib = Kernel32.INSTANCE;
		byte[] buffer = new byte[65536];
		int result;
        
		// TODO check really the speed of each step to better distribute
		long stepRoots;
		long stepDevices;
		long stepWMI;
		if (progress != null) {
			stepRoots = progress.getRemainingWork() / 5;
			stepDevices = progress.getRemainingWork() / 5;
			stepWMI = progress.getRemainingWork() - stepRoots - stepDevices;
		} else {
			stepRoots = stepDevices = stepWMI = 0;
		}

		// list mounted partitions
		Map<String,File> rootsNames = new HashMap<>();
		File[] roots = File.listRoots();
		int nbSteps = roots.length;
		if (progress != null) {
			long step = stepRoots / (nbSteps + 1);
			stepRoots -= step;
			progress.progress(step);
		}

		for (File root : roots) {
			long step = stepRoots / nbSteps--;
			stepRoots -= step;
        	String device = root.getAbsolutePath();
        	int i = device.indexOf(':');
        	device = device.substring(0, i + 1);
			result = lib.QueryDosDeviceW(WindowsUtil.toUnicode(device), buffer, 65536);
            if (result != 0) {
            	List<String> ss = WindowsUtil.toStrings(buffer);
            	if (ss.size() == 1) {
            		rootsNames.put(ss.get(0), root);
            		if (LCSystem.log.debug()) LCSystem.log.debug("Mount point " + root.getAbsolutePath() + " is on device " + ss.get(0));
            	} else {
            		if (LCSystem.log.error())
            			LCSystem.log.error("Unexpected response for mount point " + root.getAbsolutePath() + ": "
            				+ ss.size() + " strings, 1 expected");
            	}
            } else {
            	if (LCSystem.log.error())
            		LCSystem.log.error("Unable to get information about mount point " + root.getAbsolutePath() + ": "
            			+ Win32IOException.getLastError());
            }
            if (progress != null) progress.progress(step);
		}

        result = lib.QueryDosDeviceW(null, buffer, 65536);
        if (result == 0) {
        	if (progress != null) progress.done();
        	if (LCSystem.log.error())
        		LCSystem.log.error("Unable to retrieve the list of devices: " + Win32IOException.getLastError());
        	return;
        }
        List<String> devices = WindowsUtil.toStrings(buffer);
        nbSteps = devices.size();
        if (progress != null) {
        	long step = stepDevices / (nbSteps + 1);
        	stepDevices -= step;
        	progress.progress(step);
        }

        List<Drive> drives = new LinkedList<>();
        for (String deviceId : devices) {
        	long step = stepDevices / nbSteps--;
        	stepDevices -= step;
        	PhysicalDriveWin drive = null;
        	if (deviceId.startsWith("PhysicalDrive")) {
        		// new hard disk
        		drive = new PhysicalDriveWin();
        		drive.osId = deviceId;
        		drive.type = PhysicalDrive.Type.HARDDISK;
        		if (LCSystem.log.debug()) LCSystem.log.debug("Disk detected: " + deviceId);
        		// look for volumes
        		int hdIndex = Integer.parseInt(deviceId.substring(13));
        		String volumeStart = "Harddisk" + hdIndex + "Partition";
        		for (String volumeId : devices) {
        			if (volumeId.startsWith(volumeStart)) {
        				if (LCSystem.log.debug()) LCSystem.log.debug("Partition detected for " + deviceId + ": " + volumeId);
        				int partitionIndex = Integer.parseInt(volumeId.substring(volumeStart.length()));
        				DiskPartition partition = new DiskPartition();
        				drive.partitions.add(partition);
        				partition.drive = drive;
        				partition.index = partitionIndex;
        				partition.OSID = volumeId;
        				buffer = new byte[2048];
                        result = lib.QueryDosDeviceW(WindowsUtil.toUnicode(volumeId), buffer, 2048);
                        if (result != 0) {
                        	List<String> ss = WindowsUtil.toStrings(buffer);
                        	if (ss.size() == 1) {
                        		partition.mountPoint = rootsNames.get(ss.get(0));
                        		if (LCSystem.log.debug())
                        			LCSystem.log.debug(partition.mountPoint != null
                        				? "Mount point found: " + partition.mountPoint.getAbsolutePath()
                        				: "No mount point for " + ss.get(0));
                        	} else {
                        		if (LCSystem.log.error())
                        			LCSystem.log.error("Unexpected response for " + volumeId + ": "
                        				+ ss.size() + " strings, 1 expected");
                        	}
                        } else {
                        	if (LCSystem.log.error())
                        		LCSystem.log.error("Error retrieving device name for " + volumeId + ": "
                        			+ Win32IOException.getLastError());
                        }
                        try {
                        	// TODO in parallel?
	                        HANDLE h = openDevice(volumeId, false, false);
	                		buffer = new byte[4096];
	                		IntByReference nb = new IntByReference(0);
	                        if (lib.DeviceIoControl(h, WindowsUtil.IOCTL_DISK_GET_PARTITION_INFO_EX, null, 0, buffer, buffer.length, nb, null)) {
	                        	partition.start = DataUtil.Read64.LE.read(buffer, 5);
	                        	partition.size = DataUtil.Read64.LE.read(buffer, 13);
	                        	partition.partitionSlotIndex = DataUtil.Read32.LE.read(buffer, 21);
	                        	int style = DataUtil.Read32.LE.read(buffer, 1);
	                        	switch (style) {
	                        	case 0: // MBR
	                        		partition.type = (short)(buffer[0x20] & 0xFF);
	                        		partition.bootable = buffer[0x21] != 0;
	                        		// recognized = buffer[0x22] != 0;
	                        		// hidden sectors = IOUtil.readIntegerIntel(buffer, 0x23);
	                        		break;
	                        	case 1: // GPT
	                        		break;
	                        	default:
	                        	}
	                        }
	                        //Log.debug(this, "PARTITION "+volume_id+":\r\n"+DebugUtil.dump_hexa(buffer, 0, nb.getValue()));
	                        com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(h);
                        } catch (IOException e) {
                        	if (LCSystem.log.error())
                        		LCSystem.log.error("Unable to open partition " + volumeId + " to get information", e);
                        }
        			}
        		}
        	} else if (deviceId.startsWith("CdRom")) {
        		drive = new PhysicalDriveWin();
        		drive.osId = deviceId;
        		drive.type = PhysicalDrive.Type.CDROM;
        		DiskPartition partition = new DiskPartition();
        		partition.drive = drive;
        		partition.OSID = deviceId;
        		drive.partitions.add(partition);
        		if (LCSystem.log.debug()) LCSystem.log.debug("CD Drive detected: " + deviceId);
				buffer = new byte[2048];
                result = lib.QueryDosDeviceW(WindowsUtil.toUnicode(deviceId), buffer, 2048);
                if (result != 0) {
                	List<String> ss = WindowsUtil.toStrings(buffer);
                	if (ss.size() == 1) {
                		partition.mountPoint = rootsNames.get(ss.get(0));
                		if (LCSystem.log.debug())
                			LCSystem.log.debug(partition.mountPoint != null
                				? "Mount point found: " + partition.mountPoint.getAbsolutePath()
                				: "No mount point for " + ss.get(0));
                	} else {
                		if (LCSystem.log.error())
                			LCSystem.log.error("Unexpected response for " + deviceId + ": "
                				+ ss.size() + " strings, 1 expected");
                	}
                } else {
                	if (LCSystem.log.error())
                		LCSystem.log.error("Error retrieving device name for " + deviceId + ": "
                			+ Win32IOException.getLastError());
                }
        	} else {
        		//System.out.println("Device ignored: " + device_id);
        	}
        	
        	if (drive == null) {
        		if (progress != null) progress.progress(step);
        		continue;
        	}
        	
        	// fill information about drive
        	// TODO make it in parallel
        	try {
	    		HANDLE h = openDevice(deviceId, false, false);
	    		buffer = new byte[4096];
	    		byte[] input = new byte[12];
	    		IntByReference nb = new IntByReference(0);
	    		boolean res = Kernel32.INSTANCE.DeviceIoControl(
	    			h, WindowsUtil.IOCTL_STORAGE_QUERY_PROPERTY, input, 12, buffer, 4096, nb, null
	    		);
	    		if (res) {
	        		drive.removable = buffer[10] != 0;
	        		int off;
	        		off = DataUtil.Read32.LE.read(buffer, 12);
	        		if (off > 0) drive.manufacturer = WindowsUtil.toStringAscii(buffer, off);
	        		off = DataUtil.Read32.LE.read(buffer, 16);
	        		if (off > 0) drive.model = WindowsUtil.toStringAscii(buffer, off);
	        		off = DataUtil.Read32.LE.read(buffer, 20);
	        		if (off > 0) drive.version = WindowsUtil.toStringAscii(buffer, off);
	        		off = DataUtil.Read32.LE.read(buffer, 24);
	        		if (off > 0) drive.serial = WindowsUtil.toStringAscii(buffer, off);
	        		// skip checkstyle: OneStatementPerLine
	        		switch (buffer[28]) {
	        		case 0x01: drive.bus = InterfaceType.SCSI; break;
	        		case 0x02: drive.bus = InterfaceType.ATAPI; break;
	        		case 0x03: drive.bus = InterfaceType.ATA; break;
	        		case 0x04: drive.bus = InterfaceType.IEEE1394; break;
	        		case 0x05: drive.bus = InterfaceType.SSA; break;
	        		case 0x06: drive.bus = InterfaceType.Fibre; break;
	        		case 0x07: drive.bus = InterfaceType.USB; break;
	        		case 0x08: drive.bus = InterfaceType.RAID; break;
	        		case 0x09: drive.bus = InterfaceType.iSCSI; break;
	        		case 0x0A: drive.bus = InterfaceType.SAS; break;
	        		case 0x0B: drive.bus = InterfaceType.SATA; break;
	        		case 0x0C: drive.bus = InterfaceType.SD; break;
	        		case 0x0D: drive.bus = InterfaceType.MMC; break;
	        		case 0x0E: drive.bus = InterfaceType.Virtual; break;
	        		case 0x0F: drive.bus = InterfaceType.FileBackedVirtual; break;
	        		default: drive.bus = InterfaceType.Unknown; break;
	        		}
	        		if (LCSystem.log.debug())
	        			LCSystem.log.debug("Disk " + drive.osId + " is: " + drive.manufacturer + " - "
	        				+ drive.model + " - " + drive.serial + (drive.removable ? " (removable)" : " (fixed)")
	        				+ " on " + drive.bus.toString());
	    		}
	    		com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(h);
        	} catch (IOException e) {
        		if (LCSystem.log.error())
        			LCSystem.log.error("Unable to open drive " + deviceId + " to get information");
        	}

        	// fill partition information
        	try (IO stream = openReadOnly(drive, Task.PRIORITY_IMPORTANT)) {
	    		List<DiskPartition> partitions = new ArrayList<>();
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
	    					dp.startSector = p.startSector;
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
	    			}
	    		}
        	} catch (Win32IOException e) {
        		switch (e.getErrorNumber()) {
        		case WinError.ERROR_ACCESS_DENIED: // ignore the access denied, as this is not really an error, only lack of privileges
        			break;
        		default:
            		if (LCSystem.log.error())
            			LCSystem.log.error("Error reading partition table of drive " + drive.osId, e);
        		}
        	} catch (Exception e) {
        		if (LCSystem.log.error())
        			LCSystem.log.error("Error reading partition table of drive " + drive.osId, e);
        	}
    		
    		drives.add(drive);
    		if (progress != null) progress.progress(step);
        }
        
        // network drives
        for (Map.Entry<String,File> e : rootsNames.entrySet()) {
        	String device = e.getKey();
        	if (device.startsWith("\\Device\\LanmanRedirector")) {
        		drives.add(new NetworkDriveWin(e.getValue()));
    			if (LCSystem.log.debug())
    				LCSystem.log.debug("Network drive found from device name: " + e.getValue().getAbsolutePath());
        	}
        }

        fillDrivesFromWmic(drives);
		checkSerial(drives);
		if (progress != null)
			progress.progress(stepWMI);
		
        synchronized (this.drives) {
        	// check removed
        	for (Iterator<Drive> it = this.drives.iterator(); it.hasNext(); ) {
        		Drive d = it.next();
        		boolean found = false;
        		if (d instanceof PhysicalDriveWin) {
	        		for (Drive drive : drives) {
	        			if (!(drive instanceof PhysicalDriveWin)) continue;
	        			if (((PhysicalDriveWin)drive).osId.equals(((PhysicalDriveWin)d).osId)) {
	        				found = true;
	        				break;
	        			}
	        		}
        		} else if (d instanceof NetworkDriveWin) {
	        		for (Drive drive : drives) {
	        			if (!(drive instanceof NetworkDriveWin)) continue;
	        			if (((NetworkDriveWin)drive).getDriveLetter().equals(((NetworkDriveWin)d).getDriveLetter())) {
	        				found = true;
	        				break;
	        			}
	        		}
        		}
        		if (!found) {
        			it.remove();
        			synchronized (listeners) {
        				for (DriveListener listener : listeners)
        					listener.driveRemoved(d);
        			}
        		}
        	}
        	// check added and modified
	        for (Drive drive : drives) {
	        	if (drive instanceof PhysicalDriveWin) {
	        		PhysicalDriveWin existing = null;
		        	for (Drive d : this.drives) {
		        		if (!(d instanceof PhysicalDriveWin)) continue;
		        		if (((PhysicalDriveWin)d).osId.equals(((PhysicalDriveWin)drive).osId)) {
		        			existing = (PhysicalDriveWin)d;
		        			break;
		        		}
		        	}
		        	if (existing == null) {
			        	this.drives.add(drive);
	        			synchronized (listeners) {
	        				for (DriveListener listener : listeners)
	        					listener.newDrive(drive);
	        			}
		        	} else {
		        		// check partitions removed, or new mount point
		        		for (Iterator<DiskPartition> it = existing.partitions.iterator(); it.hasNext(); ) {
		        			DiskPartition p = it.next();
		        			boolean found = false;
		        			for (DiskPartition p2 : ((PhysicalDriveWin)drive).partitions)
		        				if (p2.OSID.equals(p.OSID)) {
		        					found = true;
		        					if (p.mountPoint == null && p2.mountPoint != null) {
		        						p.mountPoint = p2.mountPoint;
		        						LCSystem.log.info(
		        							"Previously detected partition without mount point is now mounted: "
		        							+ p2);
		        	        			synchronized (listeners) {
		        	        				for (DriveListener listener : listeners)
		        	        					listener.newPartition(p);
		        	        			}
		        					}
		        					break;
		        				}
		        			if (!found) {
		        				it.remove();
        						LCSystem.log.info("Parition removed: " + p);
        	        			synchronized (listeners) {
        	        				for (DriveListener listener : listeners)
        	        					listener.partitionRemoved(p);
        	        			}
		        			}
		        		}
		        		// check partitions added
		        		for (DiskPartition p : ((PhysicalDriveWin) drive).partitions) {
		        			boolean found = false;
		        			for (DiskPartition p2 : existing.partitions)
		        				if (p2.OSID.equals(p.OSID)) {
		        					found = true;
		        					break;
		        				}
		        			if (!found) {
		        				LCSystem.log.info("New partition added to the drive: " + p);
		        				existing.partitions.add(p);
        	        			synchronized (listeners) {
        	        				for (DriveListener listener : listeners)
        	        					listener.newPartition(p);
        	        			}
		        			}
		        		}
		        	}
	        	} else if (drive instanceof NetworkDriveWin) {
		        	boolean found = false;
	        		for (Drive d : this.drives) {
		        		if (!(d instanceof NetworkDriveWin)) continue;
		        		if (((NetworkDriveWin)d).getDriveLetter().equals(((NetworkDriveWin)drive).getDriveLetter())) {
		        			found = true;
		        			break;
		        		}
		        	}
	        		if (!found) {
			        	this.drives.add(drive);
	        			synchronized (listeners) {
	        				for (DriveListener listener : listeners)
	        					listener.newDrive(drive);
	        			}
		        	}
	        	}
	        }
        }
	}
	
	private static void fillDrivesFromWmic(List<Drive> drives) {
		List<Map<String,String>> list = WMI.instance().query(
			"Win32_DiskDrive", null, "BytesPerSector", "DeviceID", "SectorsPerTrack", "Size", "TotalCylinders",
			"TracksPerCylinder","PNPDeviceID", "Manufacturer", "SerialNumber", "Model", "InterfaceType"
		);
		for (Map<String,String> map : list) {
			String id = map.get("DeviceID");
			if (id == null) continue;
			id = id.toLowerCase();
			PhysicalDriveWin drive = null;
			for (Drive d : drives) {
				if (!(d instanceof PhysicalDriveWin)) continue;
				if (id.equals("\\\\.\\" + ((PhysicalDriveWin)d).osId.toLowerCase())) {
					drive = (PhysicalDriveWin)d;
					break;
				}
			}
			if (drive == null) continue;
			try { drive.bytesPerSector = Integer.parseInt(map.get("BytesPerSector")); }
			catch (Throwable t) { /* ignore */ }
			try { drive.sectorsPerTrack = Integer.parseInt(map.get("SectorsPerTrack")); }
			catch (Throwable t) { /* ignore */ }
			try { drive.tracksPerCylinder = Integer.parseInt(map.get("TrackPerCylinder")); }
			catch (Throwable t) { /* ignore */ }
			try { drive.cylinders = Integer.parseInt(map.get("TotalCylinders")); }
			catch (Throwable t) { /* ignore */ }
			try { drive.size = new BigInteger(map.get("Size")); }
			catch (Throwable t) { /* ignore */ }
			drive.infos = new HashMap<String,Object>(1);
			drive.infos.put("pnp", map.get("PNPDeviceID"));
			if (drive.manufacturer == null)
				try { drive.manufacturer = map.get("Manufacturer"); }
				catch (Throwable t) { /* ignore */ }
			if (drive.model == null)
				try { drive.model = map.get("Model"); }
				catch (Throwable t) { /* ignore */ }
			if (drive.serial == null)
				try { drive.serial = map.get("SerialNumber"); }
				catch (Throwable t) { /* ignore */ }
			if (drive.bus == null) {
				try {
					String bus = map.get("InterfaceType");
					switch (bus.toLowerCase()) {
					case "usb": drive.bus = PhysicalDrive.InterfaceType.USB; break;
					case "ide": break; // ??? sata, atapi ???
					case "1394": drive.bus = PhysicalDrive.InterfaceType.IEEE1394; break;
					case "scsi": drive.bus = PhysicalDrive.InterfaceType.SCSI; break;
					case "hdc": break; // ???
					default: // ???
					}
				} catch (Throwable t) { /* ignore */ }
			}
		}
		list = WMI.instance().query("Win32_CDROMDrive", null, "Size", "DeviceID", "PNPDeviceID", "Manufacturer", "SerialNumber");
		int index = 0;
		for (Map<String,String> map : list) {
			PhysicalDriveWin drive = null;
			for (Drive d : drives) {
				if (!(d instanceof PhysicalDriveWin)) continue;
				if (((PhysicalDriveWin)d).osId.equals("CdRom" + index)) {
					drive = (PhysicalDriveWin)d;
					break;
				}
			}
			index++;
			if (drive == null) continue;
			try { drive.size = new BigInteger(map.get("Size")); }
			catch (Throwable t) { /* ignore */ }
			drive.infos = new HashMap<String,Object>(1);
			drive.infos.put("pnp", map.get("PNPDeviceID"));
			if (drive.manufacturer == null)
				try { drive.manufacturer = map.get("Manufacturer"); }
				catch (Throwable t) { /* ignore */ }
			if (drive.serial == null)
				try { drive.serial = map.get("SerialNumber"); }
				catch (Throwable t) { /* ignore */ }
		}

		list = WMI.instance().query("Win32_LogicalDisk", null, "DeviceID", "FileSystem", "VolumeName", "VolumeSerialNumber", "DriveType");
		for (Drive drive : drives) {
			if (drive instanceof PhysicalDriveWin) {
				PhysicalDriveWin pdrive = (PhysicalDriveWin)drive;
				for (DiskPartition p : pdrive.getPartitions()) {
					File root = p.mountPoint;
					if (root == null) continue;
					for (Map<String,String> logical : list) {
						if (root.getAbsolutePath().startsWith(logical.get("DeviceID"))) {
							if (!logical.get("FileSystem").equals("null"))
								p.filesystem = logical.get("FileSystem");
							if (!logical.get("VolumeName").equals("null"))
								p.name = logical.get("VolumeName");
							if (!logical.get("VolumeSerialNumber").equals("null"))
								p.serial = logical.get("VolumeSerialNumber");
							break;
						}
					}
				}
			} else if (drive instanceof NetworkDriveWin) {
				// nothing
			}
		}
		// add network drives if not yet detected
		for (Map<String,String> logical : list) {
			if ("4".equals(logical.get("DriveType"))) {
				String letter = logical.get("DeviceID");
				boolean found = false;
				for (Drive d : drives) {
					if (!(d instanceof NetworkDriveWin)) continue;
					if (((NetworkDriveWin)d).getDriveLetter().getAbsolutePath().startsWith(letter)) {
						found = true;
						break;
					}
				}
				if (!found) {
					for (File f : File.listRoots()) {
						if (f.getAbsolutePath().startsWith(letter)) {
							drives.add(new NetworkDriveWin(f));
		        			if (LCSystem.log.debug())
		        				LCSystem.log.debug("Network drive found from WMI: " + f.getAbsolutePath());
							break;
						}
					}
				}
			}
		}
	}

	private static void checkSerial(List<Drive> drives) {
		for (Drive d : drives) {
			if (!(d instanceof PhysicalDriveWin)) continue;
			PhysicalDriveWin drive = (PhysicalDriveWin)d;
			if (drive.serial != null) continue;
			if (drive.infos == null) continue;
			String pnp = (String)drive.infos.get("pnp");
			if (pnp != null) {
				if (pnp.startsWith("USBSTOR")) {
					int i = pnp.lastIndexOf('\\');
					if (i > 0) {
						String[] parts = pnp.substring(i + 1).split("&");
						if (parts.length > 1) {
							drive.serial = parts[parts.length - 2];
							i = drive.serial.indexOf('_');
							if (i >= 0) {
								int j = i + 1;
								while (j < drive.serial.length() && drive.serial.charAt(j) == '_') j++;
								if (j == drive.serial.length())
									drive.serial = drive.serial.substring(0,i);
								else
									drive.serial = drive.serial.substring(j);
							}
						}
					}
				}
			}
		}
	}

}
