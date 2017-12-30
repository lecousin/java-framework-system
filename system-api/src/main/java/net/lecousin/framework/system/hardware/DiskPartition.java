package net.lecousin.framework.system.hardware;

import java.io.File;

import net.lecousin.framework.adapter.Adapter;
import net.lecousin.framework.io.serialization.annotations.Transient;
import net.lecousin.framework.io.serialization.annotations.TypeSerializationMethod;

/** Partition on a drive. */
public class DiskPartition {

	// skip checkstyle: AbbreviationAsWordInName
	// skip checkstyle: MemberName
	public String OSID;
	@Transient
	public Drive drive;
	public int index;
	@TypeSerializationMethod("getAbsolutePath")
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
	
	/** Convert a partition into a File, by using its mount point. */
	public static class ToFile implements Adapter<DiskPartition, File> {
		@Override
		public Class<DiskPartition> getInputType() { return DiskPartition.class; }
		
		@Override
		public Class<File> getOutputType() { return File.class; }
		
		@Override
		public boolean canAdapt(DiskPartition input) {
			return true;
		}
		
		@Override
		public File adapt(DiskPartition input) {
			return input.mountPoint;
		}
	}
	
}
