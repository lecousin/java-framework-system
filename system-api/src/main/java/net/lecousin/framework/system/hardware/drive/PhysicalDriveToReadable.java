package net.lecousin.framework.system.hardware.drive;

import java.io.IOException;

import net.lecousin.framework.adapter.Adapter;
import net.lecousin.framework.adapter.AdapterException;
import net.lecousin.framework.concurrent.threads.Task;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.system.LCSystem;

/** Convert a PhysicalDrive into a Readable, using the openReadOnly method. */
public class PhysicalDriveToReadable implements Adapter<PhysicalDrive,IO.Readable.Seekable> {
	
	@Override
	public Class<PhysicalDrive> getInputType() { return PhysicalDrive.class; }
	
	@Override
	public Class<IO.Readable.Seekable> getOutputType() { return IO.Readable.Seekable.class; }
	
	@Override
	public boolean canAdapt(PhysicalDrive input) {
		return true;
	}
	
	@Override
	public IO.Readable.Seekable adapt(PhysicalDrive input) throws AdapterException {
		try {
			return LCSystem.get().getHardware().getDrives().openReadOnly(input, Task.Priority.NORMAL);
		} catch (IOException e) {
			throw new AdapterException("Unable to convert PhysicalDrive into Seekable", e);
		}
	}
}
