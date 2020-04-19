package net.lecousin.framework.system.mac;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation.CFArrayRef;
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
import com.sun.jna.ptr.IntByReference;

import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.Artifact;
import net.lecousin.framework.application.Version;
import net.lecousin.framework.core.test.LCCoreAbstractTest;
import net.lecousin.framework.system.mac.Init;
import net.lecousin.framework.system.mac.jna.DiskArbitration;
import net.lecousin.framework.system.mac.jna.IOKit;
import net.lecousin.framework.system.mac.jna.JnaInstances;
import net.lecousin.framework.system.mac.jna.DiskArbitration.DADiskAppearedCallback;
import net.lecousin.framework.system.mac.jna.DiskArbitration.DADiskDescriptionChangedCallback;
import net.lecousin.framework.system.mac.jna.DiskArbitration.DADiskDisappearedCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestDiskArbitration {

	public static void main(String[] args) {
		Application.start(new Artifact("net.lecousin.framework.system", "test-mac", new Version("0")), true);
		new Init();
		
		TestDiskArbitration instance = new TestDiskArbitration();
		instance.init();
		
		System.out.println(" === Start monitoring ===");
		
		DiskArbitration da = JnaInstances.diskArbitration;
		da.DARegisterDiskAppearedCallback(instance.session, null, new DADiskAppearedCallback() {
			@Override
			public void callback(DADiskRef disk, Pointer context) {
				System.out.println("Disk appeared");
				showDiskInfo(disk);
				//JnaInstances.coreFoundation.CFRelease(disk);
			}
		}, null);
		da.DARegisterDiskDescriptionChangedCallback(instance.session, null, null, new DADiskDescriptionChangedCallback() {
			@Override
			public void callback(DADiskRef disk, CFArrayRef keys, Pointer context) {
				System.out.println("Disk changed");
				showDiskInfo(disk);
				//JnaInstances.coreFoundation.CFRelease(disk);
			}
		}, null);
		da.DARegisterDiskDisappearedCallback(instance.session, null, new DADiskDisappearedCallback() {
			@Override
			public void callback(DADiskRef disk, Pointer context) {
				System.out.println("Disk disappeared");
				showDiskInfo(disk);
				//JnaInstances.coreFoundation.CFRelease(disk);
			}
		}, null);
		
		da.DASessionScheduleWithRunLoop(instance.session, JnaInstances.coreFoundation.CFRunLoopGetMain(), CFStringRef.createCFString("kCFRunLoopDefaultMode"));

		try { Thread.sleep(10 * 60 * 1000); }
		catch (InterruptedException e) {}
		
		System.out.println("Close session");
		
		instance.close();
	}
	
	@BeforeClass
	public static void initLC() throws Exception {
		LCCoreAbstractTest.init();
	}
	
	private DASessionRef session;
	
	@Before
	public void init() {
		if (JnaInstances.systemB == null)
			return;
        int numfs = JnaInstances.systemB.getfsstat64(null, 0, 0);
        Statfs[] fs = new Statfs[numfs];
        JnaInstances.systemB.getfsstat64(fs, numfs * new Statfs().size(), SystemB.MNT_NOWAIT);
        for (Statfs f : fs) {
            String mntFrom = new String(f.f_mntfromname).trim();
            String mntPath = new String(f.f_mntonname).trim();
            System.out.println("Mounted filesystem: " + mntFrom + " => " + mntPath);
        }

		
		DiskArbitration da = JnaInstances.diskArbitration;
		
		DASessionRef session = da.DASessionCreate(JnaInstances.ALLOCATOR);
		
		System.out.println("-- Iteration over services IOMedia ---");
        List<String> bsdNames = new ArrayList<>();
        IOIterator iter = IOKitUtil.getMatchingServices("IOMedia");
        IORegistryEntry media = iter.next();
        while (media != null) {
            //if (IOKit.Util.getIORegistryBooleanProperty(media, "Whole")) {
                DADiskRef disk = da.DADiskCreateFromIOMedia(JnaInstances.ALLOCATOR, session, media);
                String name = da.DADiskGetBSDName(disk);
                System.out.println("BSD Name: " + name);
                bsdNames.add(name);
            //}
            JnaInstances.ioKit.IOObjectRelease(media);
            media = iter.next();
        }

		System.out.println("-- For each BSD name ---");
        
		for (String name : bsdNames) {
			System.out.println("Create disk for " + name);
			DADiskRef disk = da.DADiskCreateFromBSDName(JnaInstances.ALLOCATOR, session, "/dev/" + name);
			if (disk != null) {
				showDiskInfo(disk);
				JnaInstances.coreFoundation.CFRelease(disk);
			}
		}
	}
	
	@After
	public void close() {
		if (session != null)
			JnaInstances.coreFoundation.CFRelease(session);
	}
	
	private static void showDiskInfo(DADiskRef disk) {
		System.out.println(" ++ DISK INFO ++");
		DiskArbitration da = JnaInstances.diskArbitration;
		CFDictionaryRef diskInfo = da.DADiskCopyDescription(disk);
		if (diskInfo != null) {
			Pointer modelPtr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, CFStringRef.createCFString("DADeviceModel"));
			String model = modelPtr != null ? new CFStringRef(modelPtr).stringValue() : "null";
			Pointer sizePtr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, CFStringRef.createCFString("DAMediaSize"));
			long size = sizePtr == null ? -1 : new CFNumberRef(sizePtr).longValue();

			// Use the model as a key to get serial from IOKit
			String serial = null;
			if (!"Disk Image".equals(model)) {
				CFStringRef modelNameRef = CFStringRef.createCFString(model);
				CFMutableDictionaryRef propertyDict = JnaInstances.coreFoundation.CFDictionaryCreateMutable(JnaInstances.ALLOCATOR, new CFIndex(0), null, null);
				JnaInstances.coreFoundation.CFDictionarySetValue(propertyDict, CFStringRef.createCFString("Model"), modelNameRef);
				CFMutableDictionaryRef matchingDict = JnaInstances.coreFoundation.CFDictionaryCreateMutable(JnaInstances.ALLOCATOR, new CFIndex(0), null, null);
				JnaInstances.coreFoundation.CFDictionarySetValue(matchingDict, CFStringRef.createCFString("IOPropertyMatch"), propertyDict);

				// search for all IOservices that match the model
				// getMatchingServices releases matchingDict
				System.out.println(" > Iteration over services of disk <");
				IOIterator serviceIterator = IOKitUtil.getMatchingServices(matchingDict);
				if (serviceIterator != null) {
					IORegistryEntry sdService = serviceIterator.next();
					while (sdService != null) {
						// look up the serial number
						serial = IOKit.Util.getIORegistryStringProperty(sdService, "Serial Number");
						JnaInstances.ioKit.IOObjectRelease(sdService);
						if (serial != null)
							break;
						// iterate
						sdService = serviceIterator.next();
					}
					serviceIterator.release();
				}
				System.out.println(" > End of iteration over services of disk <");
				JnaInstances.coreFoundation.CFRelease(modelNameRef);
				JnaInstances.coreFoundation.CFRelease(propertyDict);
			}

			System.out.println("Disk: " + model + " - " + serial + " - " + size);
			
			printProperty(diskInfo, "DABusName", PropType.STRING);
			printProperty(diskInfo, "DABusPath", PropType.STRING);
			
			printProperty(diskInfo, "DADeviceVendor", PropType.STRING);
			printProperty(diskInfo, "DADeviceModel", PropType.STRING);
			printProperty(diskInfo, "DADeviceRevision", PropType.STRING);
			printProperty(diskInfo, "DADevicePath", PropType.STRING);
			printProperty(diskInfo, "DADeviceInternal", PropType.BOOLEAN);
			
			printProperty(diskInfo, "DAMediaKind", PropType.STRING);
			printProperty(diskInfo, "DAMediaType", PropType.STRING);
			printProperty(diskInfo, "DAMediaName", PropType.STRING);
			printProperty(diskInfo, "DAMediaPath", PropType.STRING);
			printProperty(diskInfo, "DAMediaContent", PropType.STRING);
			printProperty(diskInfo, "DAMediaBSDName", PropType.STRING);
			printProperty(diskInfo, "DAMediaBSDUnit", PropType.LONG);
			printProperty(diskInfo, "DAMediaEjectable", PropType.BOOLEAN);
			printProperty(diskInfo, "DAMediaRemovable", PropType.BOOLEAN);
			printProperty(diskInfo, "DAMediaWhole", PropType.BOOLEAN);
			printProperty(diskInfo, "DAMediaLeaf", PropType.BOOLEAN);
			printProperty(diskInfo, "DAMediaSize", PropType.LONG);
			printProperty(diskInfo, "DAMediaBlockSize", PropType.LONG);
			
			printProperty(diskInfo, "DAVolumeKind", PropType.STRING);
			printProperty(diskInfo, "DAVolumeName", PropType.STRING);
			printProperty(diskInfo, "DAVolumeMountable", PropType.BOOLEAN);
			printProperty(diskInfo, "DAVolumeNetwork", PropType.BOOLEAN);

			JnaInstances.coreFoundation.CFRelease(diskInfo);
		}

	}
	
	private static enum PropType {
		STRING,
		BOOLEAN,
		LONG
	}
	
	private static void printProperty(CFDictionaryRef diskInfo, String propertyName, PropType type) {
		Pointer ptr = JnaInstances.coreFoundation.CFDictionaryGetValue(diskInfo, CFStringRef.createCFString(propertyName));
		System.out.print("** " + propertyName);
		if (ptr == null) {
			System.out.println("    > NULL");
			return;
		}
		switch (type) {
		case STRING: System.out.println("    > " + new CFStringRef(ptr).stringValue()); break;
		case BOOLEAN: System.out.println("    > " + new CFBooleanRef(ptr).booleanValue()); break;
		case LONG: System.out.println("    > " + new CFNumberRef(ptr).longValue()); break;
		}
	}
	
	@Test
	public void printInfo() {
		// nothing
	}
	
	/* Result on VM
Mounted filesystem: /dev/disk0s2 => /
Mounted filesystem: devfs => /dev
Mounted filesystem: map -hosts => /net
Mounted filesystem: map auto_home => /home
Mounted filesystem: /dev/disk1s1 => /Volumes/Eclipse
BSD Name: disk0
BSD Name: disk0s1
BSD Name: disk0s2
BSD Name: disk0s3
BSD Name: disk1
BSD Name: disk1s1
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 84825604096
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > VBOX HARDDISK Media
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:0
** DAMediaContent    > GUID_partition_scheme
** DAMediaBSDName    > disk0
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > true
** DAMediaLeaf    > false
** DAMediaSize    > 84825604096
** DAMediaBlockSize    > 512
** DAVolumeKind    > NULL
** DAVolumeName    > NULL
** DAVolumeMountable    > false
** DAVolumeNetwork    > false
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 209715200
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > EFI System Partition
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:1
** DAMediaContent    > C12A7328-F81F-11D2-BA4B-00A0C93EC93B
** DAMediaBSDName    > disk0s1
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 209715200
** DAMediaBlockSize    > 512
** DAVolumeKind    > msdos
** DAVolumeName    > EFI
** DAVolumeMountable    > true
** DAVolumeNetwork    > false
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 83965845504
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > macOS Sierra Final by TechReviews
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:2
** DAMediaContent    > 48465300-0000-11AA-AA11-00306543ECAC
** DAMediaBSDName    > disk0s2
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 83965845504
** DAMediaBlockSize    > 512
** DAVolumeKind    > hfs
** DAVolumeName    > macOS Sierra Final by TechReviews
** DAVolumeMountable    > true
** DAVolumeNetwork    > false
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 650002432
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > Recovery HD
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:3
** DAMediaContent    > 426F6F74-0000-11AA-AA11-00306543ECAC
** DAMediaBSDName    > disk0s3
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 650002432
** DAMediaBlockSize    > 512
** DAVolumeKind    > hfs
** DAVolumeName    > Recovery HD
** DAVolumeMountable    > true
** DAVolumeNetwork    > false
Disk: Disk Image - null - 292552704
** DABusName    > /
** DABusPath    > IODeviceTree:/
** DADeviceVendor    > Apple
** DADeviceModel    > Disk Image
** DADeviceRevision    > 10.12v444
** DADevicePath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel
** DADeviceInternal    > NULL
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > Apple UDIF read-only compressed (zlib) Media
** DAMediaPath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel/IOBlockStorageDriver/Apple UDIF read-only compressed (zlib) Media
** DAMediaContent    > GUID_partition_scheme
** DAMediaBSDName    > disk1
** DAMediaBSDUnit    > 1
** DAMediaEjectable    > true
** DAMediaRemovable    > true
** DAMediaWhole    > true
** DAMediaLeaf    > false
** DAMediaSize    > 292552704
** DAMediaBlockSize    > 512
** DAVolumeKind    > NULL
** DAVolumeName    > NULL
** DAVolumeMountable    > false
** DAVolumeNetwork    > false
Disk: Disk Image - null - 292511744
** DABusName    > /
** DABusPath    > IODeviceTree:/
** DADeviceVendor    > Apple
** DADeviceModel    > Disk Image
** DADeviceRevision    > 10.12v444
** DADevicePath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel
** DADeviceInternal    > NULL
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > disk image
** DAMediaPath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel/IOBlockStorageDriver/Apple UDIF read-only compressed (zlib) Media/IOGUIDPartitionScheme/disk image@1
** DAMediaContent    > 48465300-0000-11AA-AA11-00306543ECAC
** DAMediaBSDName    > disk1s1
** DAMediaBSDUnit    > 1
** DAMediaEjectable    > true
** DAMediaRemovable    > true
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 292511744
** DAMediaBlockSize    > 512
** DAVolumeKind    > hfs
** DAVolumeName    > Eclipse
** DAVolumeMountable    > true
** DAVolumeNetwork    > false
 === Start monitoring ===
Disk appeared
Disk: Disk Image - null - 292511744
** DABusName    > /
** DABusPath    > IODeviceTree:/
** DADeviceVendor    > Apple
** DADeviceModel    > Disk Image
** DADeviceRevision    > 10.12v444
** DADevicePath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel
** DADeviceInternal    > NULL
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > disk image
** DAMediaPath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel/IOBlockStorageDriver/Apple UDIF read-only compressed (zlib) Media/IOGUIDPartitionScheme/disk image@1
** DAMediaContent    > 48465300-0000-11AA-AA11-00306543ECAC
** DAMediaBSDName    > disk1s1
** DAMediaBSDUnit    > 1
** DAMediaEjectable    > true
** DAMediaRemovable    > true
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 292511744
** DAMediaBlockSize    > 512
** DAVolumeKind    > hfs
** DAVolumeName    > Eclipse
** DAVolumeMountable    > true
** DAVolumeNetwork    > false
Disk appeared
Disk: Disk Image - null - 292552704
** DABusName    > /
** DABusPath    > IODeviceTree:/
** DADeviceVendor    > Apple
** DADeviceModel    > Disk Image
** DADeviceRevision    > 10.12v444
** DADevicePath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel
** DADeviceInternal    > NULL
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > Apple UDIF read-only compressed (zlib) Media
** DAMediaPath    > IOService:/IOResources/IOHDIXController/IOHDIXHDDriveOutKernel@0/IODiskImageBlockStorageDeviceOutKernel/IOBlockStorageDriver/Apple UDIF read-only compressed (zlib) Media
** DAMediaContent    > GUID_partition_scheme
** DAMediaBSDName    > disk1
** DAMediaBSDUnit    > 1
** DAMediaEjectable    > true
** DAMediaRemovable    > true
** DAMediaWhole    > true
** DAMediaLeaf    > false
** DAMediaSize    > 292552704
** DAMediaBlockSize    > 512
** DAVolumeKind    > NULL
** DAVolumeName    > NULL
** DAVolumeMountable    > false
** DAVolumeNetwork    > false
Disk appeared
Disk: null - null - -1
** DABusName    > NULL
** DABusPath    > NULL
** DADeviceVendor    > NULL
** DADeviceModel    > NULL
** DADeviceRevision    > NULL
** DADevicePath    > NULL
** DADeviceInternal    > NULL
** DAMediaKind    > NULL
** DAMediaType    > NULL
** DAMediaName    > NULL
** DAMediaPath    > NULL
** DAMediaContent    > NULL
** DAMediaBSDName    > NULL
** DAMediaBSDUnit    > NULL
** DAMediaEjectable    > NULL
** DAMediaRemovable    > NULL
** DAMediaWhole    > NULL
** DAMediaLeaf    > NULL
** DAMediaSize    > NULL
** DAMediaBlockSize    > NULL
** DAVolumeKind    > autofs
** DAVolumeName    > NULL
** DAVolumeMountable    > true
** DAVolumeNetwork    > true
Disk appeared
Disk: null - null - -1
** DABusName    > NULL
** DABusPath    > NULL
** DADeviceVendor    > NULL
** DADeviceModel    > NULL
** DADeviceRevision    > NULL
** DADevicePath    > NULL
** DADeviceInternal    > NULL
** DAMediaKind    > NULL
** DAMediaType    > NULL
** DAMediaName    > NULL
** DAMediaPath    > NULL
** DAMediaContent    > NULL
** DAMediaBSDName    > NULL
** DAMediaBSDUnit    > NULL
** DAMediaEjectable    > NULL
** DAMediaRemovable    > NULL
** DAMediaWhole    > NULL
** DAMediaLeaf    > NULL
** DAMediaSize    > NULL
** DAMediaBlockSize    > NULL
** DAVolumeKind    > autofs
** DAVolumeName    > NULL
** DAVolumeMountable    > true
** DAVolumeNetwork    > true
Disk appeared
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 84825604096
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > VBOX HARDDISK Media
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:0
** DAMediaContent    > GUID_partition_scheme
** DAMediaBSDName    > disk0
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > true
** DAMediaLeaf    > false
** DAMediaSize    > 84825604096
** DAMediaBlockSize    > 512
** DAVolumeKind    > NULL
** DAVolumeName    > NULL
** DAVolumeMountable    > false
** DAVolumeNetwork    > false
Disk appeared
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 209715200
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > EFI System Partition
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:1
** DAMediaContent    > C12A7328-F81F-11D2-BA4B-00A0C93EC93B
** DAMediaBSDName    > disk0s1
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 209715200
** DAMediaBlockSize    > 512
** DAVolumeKind    > msdos
** DAVolumeName    > EFI
** DAVolumeMountable    > true
** DAVolumeNetwork    > false
Disk appeared
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 83965845504
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > macOS Sierra Final by TechReviews
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:2
** DAMediaContent    > 48465300-0000-11AA-AA11-00306543ECAC
** DAMediaBSDName    > disk0s2
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 83965845504
** DAMediaBlockSize    > 512
** DAVolumeKind    > hfs
** DAVolumeName    > macOS Sierra Final by TechReviews
** DAVolumeMountable    > true
** DAVolumeNetwork    > false
Disk appeared
Disk: VBOX HARDDISK                            - VB424b1ef6-47e513c3  - 650002432
** DABusName    > PMP
** DABusPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0
** DADeviceVendor    > NULL
** DADeviceModel    > VBOX HARDDISK                           
** DADeviceRevision    > 1.0     
** DADevicePath    > IOService:/AppleACPIPlatformExpert/PCI0@1e0000/AppleACPIPCI/pci8086,2829@1F,2/AppleICH8AHCI/PRT0@0/IOAHCIDevice@0/AppleAHCIDiskDriver/IOAHCIBlockStorageDevice
** DADeviceInternal    > true
** DAMediaKind    > IOMedia
** DAMediaType    > NULL
** DAMediaName    > Recovery HD
** DAMediaPath    > IODeviceTree:/PCI0@1e0000/pci8086,2829@1F,2/PRT0@0/PMP@0/@0:3
** DAMediaContent    > 426F6F74-0000-11AA-AA11-00306543ECAC
** DAMediaBSDName    > disk0s3
** DAMediaBSDUnit    > 0
** DAMediaEjectable    > false
** DAMediaRemovable    > false
** DAMediaWhole    > false
** DAMediaLeaf    > true
** DAMediaSize    > 650002432
** DAMediaBlockSize    > 512
** DAVolumeKind    > hfs
** DAVolumeName    > Recovery HD
** DAVolumeMountable    > true
** DAVolumeNetwork    > false

	 * 
	 */
}
