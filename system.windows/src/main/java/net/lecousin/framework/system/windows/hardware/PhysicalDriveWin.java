package net.lecousin.framework.system.windows.hardware;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.system.hardware.DiskPartition;
import net.lecousin.framework.system.hardware.PhysicalDrive;

public class PhysicalDriveWin implements PhysicalDrive {

	@Override
	public String toString() {
		return ""+manufacturer+" - "+model+" - "+serial;
	}
	
	@Override
	public String getOSId() { return OSID; }
	
	@Override
	public String getManufacturer() { return manufacturer; }
	@Override
	public String getModel() { return model; }
	@Override
	public String getVersion() { return version; }
	@Override
	public String getSerialNumber() { return serial; }
	
	@Override
	public boolean isRemovable() { return removable; }
	
	public int getBytesPerSector() { return bytes_per_sector; }
	public int getSectorsPerTrack() { return sectors_per_track; }
	public int getTracksPerCylinder() { return tracks_per_cylinder; }
	public int getNbCylinders() { return cylinders; }
	
	@Override
	public BigInteger getSize() { return size; }
	
	@Override
	public InterfaceType getInterface() { return bus; }
	@Override
	public Type getType() { return type; }
	
	@Override
	public List<DiskPartition> getPartitions() { return partitions; }
	
	String OSID;
	String manufacturer;
	String model;
	String version;
	String serial;
	boolean removable;
	int bytes_per_sector;
	int sectors_per_track;
	int tracks_per_cylinder;
	int cylinders;
	BigInteger size;
	InterfaceType bus;
	Type type;
	Map<String,Object> infos;
	
	List<DiskPartition> partitions = new ArrayList<DiskPartition>();
	
	@Override
	public boolean equals(Object obj) { return obj instanceof PhysicalDriveWin ? OSID.equals(((PhysicalDriveWin)obj).OSID) : false; }
	@Override
	public int hashCode() { return OSID.hashCode(); }
		
}
