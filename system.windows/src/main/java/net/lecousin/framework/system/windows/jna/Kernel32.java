package net.lecousin.framework.system.windows.jna;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

// skip checkstyle: TypeName
// skip checkstyle: MethodName
// skip checkstyle: ParameterName
// skip checkstyle: AbbreviationAsWordInName
// skip checkstyle: JavadocType
// skip checkstyle: JavadocMethod
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_INTERFACE")
public interface Kernel32 extends com.sun.jna.platform.win32.Kernel32 {

	Kernel32 INSTANCE = Native.loadLibrary("kernel32", Kernel32.class);
    // Optional: wraps every call to the native library in a
    // synchronized block, limiting native calls to one at a time
    Kernel32 SYNC_INSTANCE = (Kernel32) Native.synchronizedLibrary(INSTANCE);
	
    int QueryDosDeviceW(byte[] lpDeviceName, byte[] lpTargetPath, int ucchMax);
    
    int SetFilePointer(HANDLE file, int move, IntByReference moveHigh, int moveMethod);
    
    boolean DeviceIoControl(HANDLE h, int controlCode, byte[] buffer, int len_buffer,
    	byte[] out, int out_len, IntByReference out_returned, OVERLAPPED o);
    
    boolean GetProcessTimes(HANDLE h, byte[] creation, byte[] exit, byte[] kernel, byte[] user);
    
    boolean OpenProcessToken(HANDLE hProcess, int token_access, HANDLEByReference hToken);
}
