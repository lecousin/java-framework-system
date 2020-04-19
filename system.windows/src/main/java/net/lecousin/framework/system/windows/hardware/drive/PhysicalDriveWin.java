package net.lecousin.framework.system.windows.hardware.drive;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.lecousin.framework.system.hardware.drive.DiskPartition;
import net.lecousin.framework.system.hardware.drive.PhysicalDrive;

/**
 * Physical drive.
 */
public class PhysicalDriveWin implements PhysicalDrive {

	@Override
	public String toString() {
		return "" + manufacturer + " - " + model + " - " + serial;
	}
	
	@Override
	public String getOSId() { return osId; }
	
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
	
	public int getBytesPerSector() { return bytesPerSector; }
	
	public int getSectorsPerTrack() { return sectorsPerTrack; }
	
	public int getTracksPerCylinder() { return tracksPerCylinder; }
	
	public int getNbCylinders() { return cylinders; }
	
	@Override
	public BigInteger getSize() { return size; }
	
	@Override
	public InterfaceType getInterface() { return bus; }
	
	@Override
	public Type getType() { return type; }
	
	@Override
	public List<DiskPartition> getPartitions() { return partitions; }
	
	String osId;
	String manufacturer;
	String model;
	String version;
	String serial;
	boolean removable;
	int bytesPerSector;
	int sectorsPerTrack;
	int tracksPerCylinder;
	int cylinders;
	BigInteger size;
	InterfaceType bus;
	Type type;
	Map<String,Object> infos;
	
	List<DiskPartition> partitions = new ArrayList<>();
	
	@Override
	public boolean equals(Object obj) { return (obj instanceof PhysicalDriveWin) && osId.equals(((PhysicalDriveWin)obj).osId); }
	
	@Override
	public int hashCode() { return osId.hashCode(); }
		
}
