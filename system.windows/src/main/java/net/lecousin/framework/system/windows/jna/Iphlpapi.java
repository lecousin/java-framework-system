package net.lecousin.framework.system.windows.jna;

import java.net.InetAddress;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

// skip checkstyle: TypeName
// skip checkstyle: MethodName
// skip checkstyle: ParameterName
// skip checkstyle: AbbreviationAsWordInName
// skip checkstyle: JavadocType
// skip checkstyle: JavadocMethod
public interface Iphlpapi extends StdCallLibrary {

	@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_INTERFACE")
    Iphlpapi INSTANCE = Native.loadLibrary("iphlpapi", Iphlpapi.class);
    // Optional: wraps every call to the native library in a
    // synchronized block, limiting native calls to one at a time
    Iphlpapi SYNC_INSTANCE = (Iphlpapi) Native.synchronizedLibrary(INSTANCE);

    int GetAdaptersInfo(byte[] out, IntByReference out_size);
    
    int GetAdaptersAddresses(int family, int flags, byte[] reserved, byte[] out, IntByReference out_size);
    
    int GetIfTable(byte[] out, IntByReference out_size, boolean sort);
    
    int GetBestInterfaceEx(byte[] sockaddr, IntByReference interf_index);
    
    public static class Util {
    	public static byte[] build_sockaddr(InetAddress addr) {
    		byte[] ip = addr.getAddress();
    		byte[] sockaddr = new byte[16];
    		if (ip.length == 4) {
    			// IPv4
    			sockaddr[0] = 2; // AF_INET
    		} else {
    			// IPv6
    			sockaddr[0] = 23; // AF_INET6
    		}
    		System.arraycopy(ip, 0, sockaddr, 4, ip.length);
    		return sockaddr;
    	}
    }
}
