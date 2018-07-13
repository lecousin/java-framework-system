package net.lecousin.framework.system.windows.jna;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

// skip checkstyle: TypeName
// skip checkstyle: MethodName
// skip checkstyle: ParameterName
// skip checkstyle: AbbreviationAsWordInName
// skip checkstyle: JavadocType
// skip checkstyle: JavadocMethod
public interface Psapi extends StdCallLibrary {

    Psapi INSTANCE = Native.loadLibrary("psapi", Psapi.class);
    // Optional: wraps every call to the native library in a
    // synchronized block, limiting native calls to one at a time
    Psapi SYNC_INSTANCE = (Psapi) Native.synchronizedLibrary(INSTANCE);

    boolean EnumProcesses(byte[] buf, int buf_size, IntByReference size_filled);
    
    int GetProcessImageFileName(HANDLE h, byte[] buf, int buf_size);
}
