package net.lecousin.framework.system.hardware.drive;

import java.io.File;

import net.lecousin.framework.adapter.Adapter;

/** Convert a partition into a File, by using its mount point. */
public class DiskPartitionToFile implements Adapter<DiskPartition, File> {
	
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
