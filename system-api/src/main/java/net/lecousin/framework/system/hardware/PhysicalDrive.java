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
	public Object getOSId();
	
	/** Manufacturer. */
	public String getManufacturer();

	/** Model. */
	public String getModel();
	
	/** Version. */
	public String getVersion();
	
	/** Serial number. */
	public String getSerialNumber();
	
	/** Return true if this is a removable drive such as DVD. */
	public boolean isRemovable();
	
	/** Return the total capacity of this drive. */
	public BigInteger getSize();
	
	/** Return the type of interface. */
	public InterfaceType getInterface();
	
	/** Return the type of drive. */
	public Type getType();
	
	/** List of partitions on this drive. */
	public List<DiskPartition> getPartitions();
	
	@Override
	public default List<File> getMountPoints() {
		LinkedList<File> list = new LinkedList<>();
		for (DiskPartition p : getPartitions())
			if (p.mountPoint != null)
				list.add(p.mountPoint);
		return list;
	}
	
	/** Types of interface. */
	public static enum InterfaceType {
		Unknown,
		SCSI, ATAPI, ATA, IEEE1394,
		SSA, Fibre, USB, RAID, iSCSI, SAS, SATA,
		SD, MMC, Virtual, FileBackedVirtual
	}
	
	/** Types of drive. */
	public static enum Type {
		UNKNOWN, HARDDISK, CDROM, FLOPPY, FLASH, SDD
	}
	
}
