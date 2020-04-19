package net.lecousin.framework.system.unix.jna.mac;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation.CFAllocatorRef;
import com.sun.jna.platform.mac.CoreFoundation.CFArrayRef;
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef;

import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFRunLoopRef;
import net.lecousin.framework.system.unix.jna.mac.CoreFoundation.CFURLRef;

// skip checkstyle: MethodName
public interface DiskArbitration extends com.sun.jna.platform.mac.DiskArbitration {

	public static interface DADiskAppearedCallback extends Callback {
		public void callback(DADiskRef disk, Pointer context);
	}

	public static interface DADiskDescriptionChangedCallback extends Callback {
		public void callback(DADiskRef disk, CFArrayRef keys, Pointer context);
	}
	
	public static interface DADiskDisappearedCallback extends Callback {
		public void callback(DADiskRef disk, Pointer context);
	}
	
	public DADiskRef DADiskCreateFromVolumePath(CFAllocatorRef allocator, DASessionRef session, CFURLRef path);
	
	// called when a disk appears or a partition appears
	public void DARegisterDiskAppearedCallback(DASessionRef session, CFDictionaryRef match, DADiskAppearedCallback callback, Pointer context);
	
	// called when a disk’s description has changed (and, in OS X v10.7 and later, when a volume is first mounted)
	public void DARegisterDiskDescriptionChangedCallback(
		DASessionRef session, CFDictionaryRef match, CFArrayRef watch, DADiskDescriptionChangedCallback callback, Pointer context);
	
	// called when a removable disk is ejected
	public void DARegisterDiskDisappearedCallback(
		DASessionRef session, CFDictionaryRef match, DADiskDisappearedCallback callback, Pointer context);
	
	public void DASessionScheduleWithRunLoop(DASessionRef session, CFRunLoopRef runLoop, CFStringRef runLoopMode);
}
