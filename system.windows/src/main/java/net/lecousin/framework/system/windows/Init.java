package net.lecousin.framework.system.windows;

import java.io.File;

import com.jacob.com.LibraryLoader;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.plugins.CustomExtensionPoint;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.software.Processes;
import net.lecousin.framework.system.windows.hardware.DrivesWin;
import net.lecousin.framework.system.windows.software.ProcessesWin;

public class Init implements CustomExtensionPoint {

	public Init() {
		for (File f : LCCore.get().getSystemLibraries().getLibrariesLocations()) {
			if (f.getName().startsWith("jacob-")) {
				System.out.println("jacob library found:" + f.getAbsolutePath());
				File lib = new File(f.getParentFile(), LibraryLoader.getPreferredDLLName() + ".dll");
				System.setProperty(LibraryLoader.JACOB_DLL_PATH, lib.getAbsolutePath());
				break;
			}
		}
		Drives.setInstance(new DrivesWin());
		Processes.instance = new ProcessesWin();
		LCSystem.log.info("System initialized with Windows implementation");
	}

}
