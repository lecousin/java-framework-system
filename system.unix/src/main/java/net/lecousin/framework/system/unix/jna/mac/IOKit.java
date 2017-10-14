package net.lecousin.framework.system.unix.jna.mac;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFAllocatorRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFMutableDictionaryRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFStringRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFTypeRef;

public interface IOKit extends Library {

	public static IOKit INSTANCE = Native.loadLibrary("IOKit", IOKit.class);
	
	public static class MachPort extends IntByReference {
    }
	
	public void IOObjectRelease(int object);
	
	public int IOMasterPort(int unused, IntByReference masterPort);
	
	public int IOIteratorNext(int iterator);
	
	public CFMutableDictionaryRef IOServiceMatching(String name);
	
	public int IOServiceGetMatchingService(int port, CFMutableDictionaryRef matchingDictionary);
	
	public int IOServiceGetMatchingServices(int port, CFMutableDictionaryRef matchingDictionary, IntByReference iterator);
	
	public CFTypeRef IORegistryEntryCreateCFProperty(int entry, CFStringRef key, CFAllocatorRef allocator, int options);
	
	public static class Util {
		
		private static MachPort masterPort = new MachPort();
		
		public static int getMatchingService(String serviceName) {
	        if (setMasterPort() == 0) {
	            int service = INSTANCE.IOServiceGetMatchingService(masterPort.getValue(), INSTANCE.IOServiceMatching(serviceName));
	            return service;
	        }
	        return 0;
	    }
		
	    public static int getMatchingServices(String serviceName, IntByReference serviceIterator) {
	        int setMasterPort = setMasterPort();
	        if (setMasterPort == 0) {
	            return INSTANCE.IOServiceGetMatchingServices(masterPort.getValue(), INSTANCE.IOServiceMatching(serviceName), serviceIterator);
	        }
	        return setMasterPort;
	    }
		
	    public static int getMatchingServices(CFMutableDictionaryRef matchingDictionary, IntByReference serviceIterator) {
	        int setMasterPort = setMasterPort();
	        if (setMasterPort == 0) {
	            return INSTANCE.IOServiceGetMatchingServices(masterPort.getValue(), matchingDictionary, serviceIterator);
	        }
	        return setMasterPort;
	    }
	    
	    public static String getIORegistryStringProperty(int entry, String key) {
	        String value = null;
	        CFStringRef keyAsCFString = CFStringRef.toCFString(key);
	        CFTypeRef valueAsCFString = INSTANCE.IORegistryEntryCreateCFProperty(entry, keyAsCFString, CoreFoundation.INSTANCE.CFAllocatorGetDefault(), 0);
	        if (valueAsCFString != null && valueAsCFString.getPointer() != null)
	            value = CoreFoundation.Util.cfPointerToString(valueAsCFString.getPointer());
	        CoreFoundation.INSTANCE.CFRelease(valueAsCFString);
	        return value;
	    }

	    public static long getIORegistryLongProperty(int entry, String key) {
	        long value = 0L;
	        CFStringRef keyAsCFString = CFStringRef.toCFString(key);
	        CFTypeRef valueAsCFNumber = INSTANCE.IORegistryEntryCreateCFProperty(entry, keyAsCFString, CoreFoundation.INSTANCE.CFAllocatorGetDefault(), 0);
	        if (valueAsCFNumber != null && valueAsCFNumber.getPointer() != null)
	            value = CoreFoundation.Util.cfPointerToLong(valueAsCFNumber.getPointer());
	        CoreFoundation.INSTANCE.CFRelease(valueAsCFNumber);
	        return value;
	    }

	    public static int getIORegistryIntProperty(int entry, String key) {
	        int value = 0;
	        CFStringRef keyAsCFString = CFStringRef.toCFString(key);
	        CFTypeRef valueAsCFNumber = INSTANCE.IORegistryEntryCreateCFProperty(entry, keyAsCFString, CoreFoundation.INSTANCE.CFAllocatorGetDefault(), 0);
	        if (valueAsCFNumber != null && valueAsCFNumber.getPointer() != null)
	            value = CoreFoundation.Util.cfPointerToInt(valueAsCFNumber.getPointer());
	        CoreFoundation.INSTANCE.CFRelease(valueAsCFNumber);
	        return value;
	    }

	    public static boolean getIORegistryBooleanProperty(int entry, String key) {
	        boolean value = false;
	        CFStringRef keyAsCFString = CFStringRef.toCFString(key);
	        CFTypeRef valueAsCFBoolean = INSTANCE.IORegistryEntryCreateCFProperty(entry, keyAsCFString, CoreFoundation.INSTANCE.CFAllocatorGetDefault(), 0);
	        if (valueAsCFBoolean != null && valueAsCFBoolean.getPointer() != null)
	            value = CoreFoundation.Util.cfPointerToBoolean(valueAsCFBoolean.getPointer());
	        CoreFoundation.INSTANCE.CFRelease(valueAsCFBoolean);
	        return value;
	    }

	    public static byte[] getIORegistryByteArrayProperty(int entry, String key) {
	        byte[] value = null;
	        CFStringRef keyAsCFString = CFStringRef.toCFString(key);
	        CFTypeRef valueAsCFData = INSTANCE.IORegistryEntryCreateCFProperty(entry, keyAsCFString, CoreFoundation.INSTANCE.CFAllocatorGetDefault(), 0);
	        if (valueAsCFData != null && valueAsCFData.getPointer() != null) {
	            int length = CoreFoundation.INSTANCE.CFDataGetLength(valueAsCFData);
	            PointerByReference p = CoreFoundation.INSTANCE.CFDataGetBytePtr(valueAsCFData);
	            value = p.getPointer().getByteArray(0, length);
	        }
	        CoreFoundation.INSTANCE.CFRelease(valueAsCFData);
	        return value;
	    }
	    
		private static int setMasterPort() {
	        if (masterPort.getValue() == 0)
	            return INSTANCE.IOMasterPort(0, masterPort);
	        return 0;
	    }
	}
	
}
