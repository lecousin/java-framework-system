package net.lecousin.framework.system.windows;

import java.io.File;

import com.jacob.com.LibraryLoader;
import com.sun.jna.platform.win32.Shell32;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.plugins.CustomExtensionPoint;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.Drives;
import net.lecousin.framework.system.software.Processes;
import net.lecousin.framework.system.windows.hardware.DrivesWin;
import net.lecousin.framework.system.windows.jna.Advapi32;
import net.lecousin.framework.system.windows.jna.Iphlpapi;
import net.lecousin.framework.system.windows.jna.Kernel32;
import net.lecousin.framework.system.windows.jna.Ntdll;
import net.lecousin.framework.system.windows.jna.Psapi;
import net.lecousin.framework.system.windows.jna.User32;
import net.lecousin.framework.system.windows.software.ProcessesWin;

/**
 * Initialization.
 */
public class Init implements CustomExtensionPoint {

	/** Constructor called by the extension point system. */
	public Init() {
		// init libraries
		if (Advapi32.INSTANCE == null) { /* nothing */ }
		if (Iphlpapi.INSTANCE == null) { /* nothing */ }
		if (Kernel32.INSTANCE == null) { /* nothing */ }
		if (Ntdll.INSTANCE == null) { /* nothing */ }
		if (Psapi.INSTANCE == null) { /* nothing */ }
		if (User32.INSTANCE == null) { /* nothing */ }
		if (Shell32.INSTANCE == null) { /* nothing */ }
		
		for (File f : LCCore.get().getSystemLibraries().getLibrariesLocations()) {
			if (f.getName().startsWith("jacob-")) {
				System.out.println("jacob library found:" + f.getAbsolutePath());
				File lib = new File(f.getParentFile(), LibraryLoader.getPreferredDLLName() + ".dll");
				System.setProperty(LibraryLoader.JACOB_DLL_PATH, lib.getAbsolutePath());
				break;
			}
		}
		Drives.setInstance(new DrivesWin());
		Processes.setInstance(new ProcessesWin());
		LCSystem.log.info("System initialized with Windows implementation");
	}
	
	@Override
	public boolean keepAfterInit() {
		return false;
	}

}
