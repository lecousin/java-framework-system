package net.lecousin.framework.system.unix.jna.linux;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;

public interface Udev extends Library {

	final class UdevHandle extends PointerType {

		public UdevHandle(Pointer address) {
			super(address);
		}

		public UdevHandle() {
			super();
		}
	}

	final class UdevDevice extends PointerType {

		public UdevDevice(Pointer address) {
			super(address);
		}

		public UdevDevice() {
			super();
		}
	}

	final class UdevEnumerate extends PointerType {

		public UdevEnumerate(Pointer address) {
			super(address);
		}

		public UdevEnumerate() {
			super();
		}
	}

	final class UdevListEntry extends PointerType {

		public UdevListEntry(Pointer address) {
			super(address);
		}

		public UdevListEntry() {
			super();
		}
	}

	final class UdevMonitor extends PointerType {

		public UdevMonitor(Pointer address) {
			super(address);
		}

		public UdevMonitor() {
			super();
		}
	}

	UdevHandle udev_new();

	void udev_unref(UdevHandle handle);

	UdevEnumerate udev_enumerate_new(UdevHandle handle);
	

	int udev_enumerate_add_match_subsystem(UdevEnumerate enumerate, String subsystem);

	int udev_enumerate_scan_devices(UdevEnumerate enumerate);

	UdevListEntry udev_enumerate_get_list_entry(UdevEnumerate enumerate);

	void udev_enumerate_unref(UdevEnumerate enumerate);
	
	
	UdevDevice udev_device_new_from_syspath(UdevHandle handle, String syspath);

	UdevDevice udev_device_get_parent_with_subsystem_devtype(Udev.UdevDevice device, String subsystem, String devtype);

	String udev_device_get_devtype(UdevDevice device);

	String udev_device_get_devnode(UdevDevice device);

	String udev_device_get_syspath(UdevDevice device);

	UdevListEntry udev_device_get_properties_list_entry(UdevDevice device);
	
	String udev_device_get_property_value(UdevDevice device, String key);

	String udev_device_get_sysname(UdevDevice device);

	UdevListEntry udev_device_get_sysattr_list_entry(UdevDevice device);
	
	String udev_device_get_sysattr_value(final UdevDevice device, final String sysattr);
	
	void udev_device_unref(UdevDevice device);

	
	UdevListEntry udev_list_entry_get_next(UdevListEntry listEntry);

	String udev_list_entry_get_name(UdevListEntry listEntry);
	
	
	UdevMonitor udev_monitor_new_from_netlink(UdevHandle handle, String name); // name should be "udev" 
	
	int udev_monitor_filter_add_match_subsystem_devtype(UdevMonitor monitor, String subsystem, String devtype);
	
	int udev_monitor_enable_receiving(UdevMonitor monitor); // returns 0 on success
	
	int udev_monitor_get_fd(UdevMonitor monitor);
	
	UdevDevice udev_monitor_receive_device(UdevMonitor monitor);
	
	void udev_monitor_unref(UdevMonitor monitor);
	
}
