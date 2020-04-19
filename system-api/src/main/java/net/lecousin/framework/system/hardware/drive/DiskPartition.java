package net.lecousin.framework.system.hardware.drive;

import java.io.File;

/** Partition on a drive. */
public class DiskPartition {

	// skip checkstyle: MemberName
	public String OSID;
	public Drive drive;
	public int index;
	public File mountPoint;
	public String filesystem;
	public String name;
	public String serial;

	public int partitionSlotIndex;
	
	public long start;
	public long size;
	public short type;

	public boolean bootable;
	
	public short startHead;
	public short startSector;
	public int startCylinder;
	public short endHead;
	public short endSector;
	public int endCylinder;
	
	public long lba;
	public long nbSectors;
	
	@Override
	public String toString() {
		return "Partition " + OSID + " (" + name + ")";
	}
	
}
