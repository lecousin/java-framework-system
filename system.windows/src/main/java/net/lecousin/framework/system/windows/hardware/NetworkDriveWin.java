package net.lecousin.framework.system.windows.hardware;

import java.io.File;
import java.util.Collections;
import java.util.List;

import net.lecousin.framework.system.hardware.NetworkDrive;

public class NetworkDriveWin implements NetworkDrive {

	public NetworkDriveWin(File mountPoint) {
		this.mountPoint = mountPoint;
	}
	
	private File mountPoint;
	
	public File getDriveLetter() { return mountPoint; }
	
	@Override
	public List<File> getMountPoints() {
		return Collections.singletonList(mountPoint);
	}
	
	@Override
	public String toString() {
		return "Network drive " + mountPoint.getPath();
	}
	
}
