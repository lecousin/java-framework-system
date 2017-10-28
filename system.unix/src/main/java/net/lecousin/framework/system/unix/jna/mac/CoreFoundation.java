package net.lecousin.framework.system.unix.jna.mac;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.ptr.ByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import net.lecousin.framework.system.unix.jna.JnaInstances;

public interface CoreFoundation extends Library {

	public static final int UTF_8 = 0x08000100;
	
    public static class CFAllocatorRef extends PointerType {
    }
	
    public static class CFDictionaryRef extends PointerType {
    }
    
    public static class CFMutableDictionaryRef extends CFDictionaryRef {
    }

    public static class CFTypeRef extends PointerType {
    }

    public static class CFNumberRef extends PointerType {
    }

    public static class CFBooleanRef extends PointerType {
    }

    public static class CFArrayRef extends PointerType {
    }

    public static class CFURLRef extends PointerType {
    }
    
    public static class CFStringRef extends PointerType {
        public static CFStringRef toCFString(String s) {
            final char[] chars = s.toCharArray();
            int length = chars.length;
            return JnaInstances.coreFoundation.CFStringCreateWithCharacters(null, chars, new NativeLong(length));
        }
    }

    public static class CFRunLoopRef extends PointerType {
    }
    
    public CFAllocatorRef CFAllocatorGetDefault();
    
    public void CFRelease(PointerType pointer);
    
    public CFStringRef CFStringCreateWithCharacters(Object object, char[] chars, NativeLong length);
    
    public Pointer CFDictionaryGetValue(CFDictionaryRef dictionary, CFStringRef key);

    public boolean CFDictionaryGetValueIfPresent(CFDictionaryRef dictionary, CFStringRef key, PointerType value);

    public void CFDictionarySetValue(CFMutableDictionaryRef dict, PointerType key, PointerType value);

    public CFMutableDictionaryRef CFDictionaryCreateMutable(CFAllocatorRef allocator, int capacity, Pointer keyCallBacks, Pointer valueCallBacks);
    
    public boolean CFStringGetCString(Pointer cfString, Pointer bufferToFill, long maxSize, int encoding);

    public boolean CFBooleanGetValue(Pointer booleanRef);

    public CFTypeRef CFArrayGetValueAtIndex(CFArrayRef array, int index);

    public void CFNumberGetValue(Pointer cfNumber, int intSize, ByReference value);

    public long CFStringGetLength(Pointer str);

    public long CFStringGetMaximumSizeForEncoding(long length, int encoding);
    
    public int CFDataGetLength(CFTypeRef theData);

    public PointerByReference CFDataGetBytePtr(CFTypeRef theData);
    
    public CFRunLoopRef CFRunLoopGetCurrent();

	public CFRunLoopRef CFRunLoopGetMain();
	
	public void CFRunLoopRun();
	
	public void CFRunLoopStop(CFRunLoopRef runLoop);
    
    public static class Util {

        public enum CFNumberType {
            unusedZero, kCFNumberSInt8Type, kCFNumberSInt16Type, kCFNumberSInt32Type, kCFNumberSInt64Type, kCFNumberFloat32Type, kCFNumberFloat64Type, kCFNumberCharType, kCFNumberShortType, kCFNumberIntType, kCFNumberLongType, kCFNumberLongLongType, kCFNumberFloatType, kCFNumberDoubleType, kCFNumberCFIndexType, kCFNumberNSIntegerType, kCFNumberCGFloatType, kCFNumberMaxType
        }
    	
        public static long cfPointerToLong(Pointer p) {
            LongByReference lbr = new LongByReference();
            JnaInstances.coreFoundation.CFNumberGetValue(p, CFNumberType.kCFNumberLongLongType.ordinal(), lbr);
            return lbr.getValue();
        }
    	
        public static int cfPointerToInt(Pointer p) {
            IntByReference ibr = new IntByReference();
            JnaInstances.coreFoundation.CFNumberGetValue(p, CFNumberType.kCFNumberIntType.ordinal(), ibr);
            return ibr.getValue();
        }

        public static boolean cfPointerToBoolean(Pointer p) {
            return JnaInstances.coreFoundation.CFBooleanGetValue(p);
        }
        
        public static String cfPointerToString(Pointer p) {
            if (p == null) {
                return "null";
            }
            long length = JnaInstances.coreFoundation.CFStringGetLength(p);
            long maxSize = JnaInstances.coreFoundation.CFStringGetMaximumSizeForEncoding(length, UTF_8);
            if (maxSize == 0) {
                maxSize = 1;
            }
            Pointer buf = new Memory(maxSize);
            JnaInstances.coreFoundation.CFStringGetCString(p, buf, maxSize, UTF_8);
            return buf.getString(0);
        }
        
    }
}
