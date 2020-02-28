package net.lecousin.framework.system.hardware;

import java.io.File;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

/**
 * Physical drive.
 */
public interface PhysicalDrive extends Drive {

	/** Identifier of this drive on the system. */
	String getOSId();
	
	/** Manufacturer. */
	String getManufacturer();

	/** Model. */
	String getModel();
	
	/** Version. */
	String getVersion();
	
	/** Serial number. */
	String getSerialNumber();
	
	/** Return true if this is a removable drive such as DVD. */
	boolean isRemovable();
	
	/** Return the total capacity of this drive. */
	BigInteger getSize();
	
	/** Return the type of interface. */
	InterfaceType getInterface();
	
	/** Return the type of drive. */
	Type getType();
	
	/** List of partitions on this drive. */
	List<DiskPartition> getPartitions();
	
	@Override
	default List<File> getMountPoints() {
		LinkedList<File> list = new LinkedList<>();
		for (DiskPartition p : getPartitions())
			if (p.mountPoint != null)
				list.add(p.mountPoint);
		return list;
	}
	
	@Override
	default boolean supportConcurrentAccess() {
		switch (getType()) {
		case FLASH:
		case SDD:
			return true;
		default:
			return false;
		}
	}

	
	/** Types of interface. */
	enum InterfaceType {
		Unknown,
		SCSI, ATAPI, ATA, IEEE1394,
		SSA, Fibre, USB, RAID, iSCSI, SAS, SATA,
		SD, MMC, Virtual, FileBackedVirtual
	}
	
	/** Types of drive. */
	enum Type {
		UNKNOWN, HARDDISK, CDROM, FLOPPY, FLASH, SDD
	}
	
}
