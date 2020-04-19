package net.lecousin.framework.system.windows.jna;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;

// skip checkstyle: TypeName
// skip checkstyle: MethodName
// skip checkstyle: ParameterName
// skip checkstyle: AbbreviationAsWordInName
// skip checkstyle: JavadocType
// skip checkstyle: JavadocMethod
public interface Ntdll extends com.sun.jna.platform.win32.NtDll {

    Ntdll INSTANCE = Native.load("Ntdll", Ntdll.class);
    // Optional: wraps every call to the native library in a
    // synchronized block, limiting native calls to one at a time
    Ntdll SYNC_INSTANCE = (Ntdll) Native.synchronizedLibrary(INSTANCE);
	
    public static final int CLASS_SYSTEM_HANDLE_INFORMATION = 16;
    
    int NtQuerySystemInformation(int info_class, byte[] buffer, int size, IntByReference buffer_size);
}
