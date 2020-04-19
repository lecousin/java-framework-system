package net.lecousin.framework.system.unix.jna.mac;

import com.sun.jna.PointerType;

public interface CoreFoundation extends com.sun.jna.platform.mac.CoreFoundation {

	public static final int UTF_8 = 0x08000100;
	
    public static class CFURLRef extends PointerType {
    }
    
    public static class CFRunLoopRef extends PointerType {
    }
    
    public CFRunLoopRef CFRunLoopGetCurrent();

	public CFRunLoopRef CFRunLoopGetMain();
	
	public void CFRunLoopRun();
	
	public void CFRunLoopStop(CFRunLoopRef runLoop);
    
}
