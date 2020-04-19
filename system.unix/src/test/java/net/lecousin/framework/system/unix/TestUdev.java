package net.lecousin.framework.system.unix;

import net.lecousin.framework.application.Application;
import net.lecousin.framework.application.Artifact;
import net.lecousin.framework.application.Version;
import net.lecousin.framework.core.test.LCCoreAbstractTest;
import net.lecousin.framework.system.unix.jna.JnaInstances;
import net.lecousin.framework.system.unix.jna.LibC;
import net.lecousin.framework.system.unix.jna.LibC.FDSet;
import net.lecousin.framework.system.unix.jna.LibC.TimeVal;
import net.lecousin.framework.system.unix.jna.linux.Udev;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestUdev {

	public static void main(String[] args) {
		Application.start(new Artifact("net.lecousin.framework.system", "test-linux", new Version("0")), true);
		new Init();
		TestUdev instance = new TestUdev();
		instance.init();
		instance.monitor();
	}
	
	@BeforeClass
	public static void initLC() throws Exception {
		LCCoreAbstractTest.init();
	}
	
	private Udev udev;
	private Udev.UdevHandle handle;
	
	@Before
	public void init() {
		udev = JnaInstances.udev;
		if (udev == null)
			return;
		handle = udev.udev_new();
		Udev.UdevEnumerate enumerate = udev.udev_enumerate_new(handle);
		udev.udev_enumerate_add_match_subsystem(enumerate, "block");
		udev.udev_enumerate_scan_devices(enumerate);
		Udev.UdevListEntry entry = udev.udev_enumerate_get_list_entry(enumerate);
		for (; entry != null; entry = udev.udev_list_entry_get_next(entry)) {
			System.out.println(" --- Entry ---");
			String name = udev.udev_list_entry_get_name(entry);
			System.out.println("Entry name: " + name);
			
			Udev.UdevDevice device = null;
			try {
				device = udev.udev_device_new_from_syspath(handle, name);
				String devnode = udev.udev_device_get_devnode(device);
				System.out.println("Device node: " + devnode);
				if (devnode.startsWith("/dev/loop")) continue;
				String type = udev.udev_device_get_devtype(device);
				System.out.println("Device type: " + type);
				
				String model = udev.udev_device_get_property_value(device, "ID_MODEL");
				System.out.println("Model: " + model);
				String serial = udev.udev_device_get_property_value(device, "ID_SERIAL_SHORT");
				System.out.println("Serial: " + serial);
				String revision = udev.udev_device_get_property_value(device, "ID_REVISION");
				System.out.println("Revision: " + revision);
				String type2 = udev.udev_device_get_property_value(device, "ID_TYPE");
				System.out.println("Type: " + type2);
				String bus = udev.udev_device_get_property_value(device, "ID_BUS");
				System.out.println("Bus: " + bus);
				
				Udev.UdevListEntry list = udev.udev_device_get_properties_list_entry(device);
				while (list != null) {
					String pname = udev.udev_list_entry_get_name(list);
					String pvalue = udev.udev_device_get_property_value(device, pname);
					System.out.println("Property " + pname + " = " + pvalue);
					list = udev.udev_list_entry_get_next(list);
				}
				
				list = udev.udev_device_get_sysattr_list_entry(device);
				while (list != null) {
					String pname = udev.udev_list_entry_get_name(list);
					String pvalue = udev.udev_device_get_sysattr_value(device, pname);
					System.out.println("Attribute " + pname + " = " + pvalue);
					list = udev.udev_list_entry_get_next(list);
				}
				
			} finally {
				if (device != null)
					udev.udev_device_unref(device);
			}
		}
	}
	
	private void monitor() {
		System.out.println("====================== Start monitor =====================");
		Udev.UdevMonitor monitor = udev.udev_monitor_new_from_netlink(handle, "udev");
		int res = udev.udev_monitor_enable_receiving(monitor);
		System.out.println("result = " + res);
		int fd = udev.udev_monitor_get_fd(monitor);
		
		while (true) {
			FDSet fds = new FDSet();
			fds.FD_ZERO();
			fds.FD_SET(fd);
			TimeVal tv = new TimeVal(10, 0);
			
			int ret = LibC.INSTANCE.select(fd + 1, fds, null, null, tv);
			System.out.println(ret);
			if (ret <= 0) continue;

			Udev.UdevDevice device = udev.udev_monitor_receive_device(monitor);
			
			if (device == null) return;

			System.out.println(" --- NEW DEVICE ---");
			try {
				String devnode = udev.udev_device_get_devnode(device);
				System.out.println("Device node: " + devnode);
				String type = udev.udev_device_get_devtype(device);
				System.out.println("Device type: " + type);
				
				String model = udev.udev_device_get_property_value(device, "ID_MODEL");
				System.out.println("Model: " + model);
				String serial = udev.udev_device_get_property_value(device, "ID_SERIAL_SHORT");
				System.out.println("Serial: " + serial);
				String revision = udev.udev_device_get_property_value(device, "ID_REVISION");
				System.out.println("Revision: " + revision);
				String type2 = udev.udev_device_get_property_value(device, "ID_TYPE");
				System.out.println("Type: " + type2);
				String bus = udev.udev_device_get_property_value(device, "ID_BUS");
				System.out.println("Bus: " + bus);
				
				Udev.UdevListEntry list = udev.udev_device_get_properties_list_entry(device);
				while (list != null) {
					String pname = udev.udev_list_entry_get_name(list);
					String pvalue = udev.udev_device_get_property_value(device, pname);
					System.out.println("Property " + pname + " = " + pvalue);
					list = udev.udev_list_entry_get_next(list);
				}
				
				list = udev.udev_device_get_sysattr_list_entry(device);
				while (list != null) {
					String pname = udev.udev_list_entry_get_name(list);
					String pvalue = udev.udev_device_get_sysattr_value(device, pname);
					System.out.println("Attribute " + pname + " = " + pvalue);
					list = udev.udev_list_entry_get_next(list);
				}
				
			} finally {
				udev.udev_device_unref(device);
			}
		}
	}
	
	@Test
	public void printUDevInfos() {
		Assume.assumeNotNull(udev);
		// nothing
	}

}
