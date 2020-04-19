package net.lecousin.framework.system.unix.hardware.drive;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.framework.system.hardware.drive.DiskPartition;
import net.lecousin.framework.system.hardware.drive.PhysicalDrive;

/** Implementation of PhysicalDrive for Unix systems. */
public class PhysicalDriveUnix implements PhysicalDrive {

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder(128);
		s.append(manufacturer);
		s.append(" - ");
		s.append(model);
		s.append(" - ");
		s.append(version);
		s.append(" - ");
		s.append(serial);
		return s.toString();
	}
	
	String devpath;
	String osId;
	BigInteger size;
	String model;
	String manufacturer;
	String version;
	String serial;
	Type type;
	InterfaceType itype;
	boolean removable;
	
	List<DiskPartition> partitions = new ArrayList<>();
	
	@Override
	public String getOSId() {
		return osId;
	}
	
	@Override
	public BigInteger getSize() {
		return size;
	}
	
	@Override
	public String getModel() {
		return model;
	}
	
	@Override
	public String getManufacturer() {
		return manufacturer;
	}
	
	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public String getSerialNumber() {
		return serial;
	}

	@Override
	public boolean isRemovable() {
		return removable;
	}

	@Override
	public InterfaceType getInterface() {
		return itype;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public List<DiskPartition> getPartitions() {
		return partitions;
	}
	
}
