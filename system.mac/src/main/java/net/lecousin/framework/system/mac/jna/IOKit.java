package net.lecousin.framework.system.mac.jna;

import com.sun.jna.platform.mac.CoreFoundation.CFBooleanRef;
import com.sun.jna.platform.mac.CoreFoundation.CFDataRef;
import com.sun.jna.platform.mac.CoreFoundation.CFNumberRef;
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef;
import com.sun.jna.platform.mac.CoreFoundation.CFTypeRef;

public interface IOKit extends com.sun.jna.platform.mac.IOKit {

	public static class Util {
		
	    public static String getIORegistryStringProperty(IORegistryEntry entry, String key) {
	        String value = null;
	        CFStringRef keyAsCFString = CFStringRef.createCFString(key);
	        CFTypeRef valueAsCFString = JnaInstances.ioKit.IORegistryEntryCreateCFProperty(
	        	entry, keyAsCFString, JnaInstances.coreFoundation.CFAllocatorGetDefault(), 0);
	        if (valueAsCFString != null && valueAsCFString.getPointer() != null)
	            value = new CFStringRef(valueAsCFString.getPointer()).stringValue();
	        JnaInstances.coreFoundation.CFRelease(valueAsCFString);
	        return value;
	    }

	    public static long getIORegistryLongProperty(IORegistryEntry entry, String key) {
	        long value = 0L;
	        CFStringRef keyAsCFString = CFStringRef.createCFString(key);
	        CFTypeRef valueAsCFNumber = JnaInstances.ioKit.IORegistryEntryCreateCFProperty(
	        	entry, keyAsCFString, JnaInstances.coreFoundation.CFAllocatorGetDefault(), 0);
	        if (valueAsCFNumber != null && valueAsCFNumber.getPointer() != null)
	            value = new CFNumberRef(valueAsCFNumber.getPointer()).longValue();
	        JnaInstances.coreFoundation.CFRelease(valueAsCFNumber);
	        return value;
	    }

	    public static int getIORegistryIntProperty(IORegistryEntry entry, String key) {
	        int value = 0;
	        CFStringRef keyAsCFString = CFStringRef.createCFString(key);
	        CFTypeRef valueAsCFNumber = JnaInstances.ioKit.IORegistryEntryCreateCFProperty(
	        	entry, keyAsCFString, JnaInstances.coreFoundation.CFAllocatorGetDefault(), 0);
	        if (valueAsCFNumber != null && valueAsCFNumber.getPointer() != null)
	            value = new CFNumberRef(valueAsCFNumber.getPointer()).intValue();
	        JnaInstances.coreFoundation.CFRelease(valueAsCFNumber);
	        return value;
	    }

	    public static boolean getIORegistryBooleanProperty(IORegistryEntry entry, String key) {
	        boolean value = false;
	        CFStringRef keyAsCFString = CFStringRef.createCFString(key);
	        CFTypeRef valueAsCFBoolean = JnaInstances.ioKit.IORegistryEntryCreateCFProperty(
	        	entry, keyAsCFString, JnaInstances.coreFoundation.CFAllocatorGetDefault(), 0);
	        if (valueAsCFBoolean != null && valueAsCFBoolean.getPointer() != null)
	            value = new CFBooleanRef(valueAsCFBoolean.getPointer()).booleanValue();
	        JnaInstances.coreFoundation.CFRelease(valueAsCFBoolean);
	        return value;
	    }

	    public static byte[] getIORegistryByteArrayProperty(IORegistryEntry entry, String key) {
	        byte[] value = null;
	        CFStringRef keyAsCFString = CFStringRef.createCFString(key);
	        CFTypeRef valueAsCFData = JnaInstances.ioKit.IORegistryEntryCreateCFProperty(
	        	entry, keyAsCFString, JnaInstances.coreFoundation.CFAllocatorGetDefault(), 0);
	        if (valueAsCFData != null && valueAsCFData.getPointer() != null) {
	        	CFDataRef data = new CFDataRef(valueAsCFData.getPointer());
	        	value = data.getBytePtr().getByteArray(0, data.getLength());
	        }
	        JnaInstances.coreFoundation.CFRelease(valueAsCFData);
	        return value;
	    }
	    
	}
	
}
