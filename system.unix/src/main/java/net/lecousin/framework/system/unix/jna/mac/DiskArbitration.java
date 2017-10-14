package net.lecousin.framework.system.unix.jna.mac;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;

import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFAllocatorRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFArrayRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFDictionaryRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFRunLoopRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFStringRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFURLRef;

public interface DiskArbitration extends Library {

	public static class DASessionRef extends PointerType {
    }

	public static class DADiskRef extends PointerType {
    }
	
	public static interface DADiskAppearedCallback extends Callback {
		public void callback(DADiskRef disk, Pointer context);
	}
	public static interface DADiskDescriptionChangedCallback extends Callback {
		public void callback(DADiskRef disk, CFArrayRef keys, Pointer context);
	}
	public static interface DADiskDisappearedCallback extends Callback {
		public void callback(DADiskRef disk, Pointer context);
	}
	
	public DASessionRef DASessionCreate(CFAllocatorRef allocator);
	
	public DADiskRef DADiskCreateFromBSDName(CFAllocatorRef allocator, DASessionRef session, String diskName);
	public DADiskRef DADiskCreateFromIOMedia(CFAllocatorRef allocator, DASessionRef session, int media);
	public DADiskRef DADiskCreateFromVolumePath(CFAllocatorRef allocator, DASessionRef session, CFURLRef path);
	
	public String DADiskGetBSDName(DADiskRef disk);
	
	public CFDictionaryRef DADiskCopyDescription(DADiskRef disk);

	// called when a disk appears or a partition appears
	public void DARegisterDiskAppearedCallback(DASessionRef session, CFDictionaryRef match, DADiskAppearedCallback callback, Pointer context);
	
	// called when a disk’s description has changed (and, in OS X v10.7 and later, when a volume is first mounted)
	public void DARegisterDiskDescriptionChangedCallback(DASessionRef session, CFDictionaryRef match, CFArrayRef watch, DADiskDescriptionChangedCallback callback, Pointer context);
	
	// called when a removable disk is ejected
	public void DARegisterDiskDisappearedCallback(DASessionRef session, CFDictionaryRef match, DADiskDisappearedCallback callback, Pointer context);
	
	public void DASessionScheduleWithRunLoop(DASessionRef session, CFRunLoopRef runLoop, CFStringRef runLoopMode);
}
