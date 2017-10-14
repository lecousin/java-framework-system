package net.lecousin.framework.system.windows.jna;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.PSID;
import com.sun.jna.platform.win32.WinNT.PSIDByReference;
import com.sun.jna.ptr.IntByReference;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

// skip checkstyle: TypeName
// skip checkstyle: MethodName
// skip checkstyle: AbbreviationAsWordInName
// skip checkstyle: JavadocType
// skip checkstyle: JavadocMethod
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_INTERFACE")
public interface Advapi32 extends com.sun.jna.platform.win32.Advapi32 {

	Advapi32 INSTANCE = Native.loadLibrary("advapi32", Advapi32.class);
    // Optional: wraps every call to the native library in a
    // synchronized block, limiting native calls to one at a time
    Advapi32 SYNC_INSTANCE = (Advapi32) Native.synchronizedLibrary(INSTANCE);
    
    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
	public static class SID_IDENTIFIER_AUTHORITY extends Structure {
		public byte[] value = new byte[6];
		
		@Override
		protected List<String> getFieldOrder() { return Arrays.asList("value"); }
	}
    
    boolean CheckTokenMembership(HANDLE token, PSID sidToCheck, IntByReference isMember);
    
    boolean AllocateAndInitializeSid(
    	SID_IDENTIFIER_AUTHORITY authority, byte subAuthorityCount, int subAuthority0, int subAuthority1, int subAuthority2,
    	int subAuthority3, int subAuthority4, int subAuthority5, int subAuthority6, int subAuthority7, PSIDByReference psid
    );
    
    void FreeSid(PSID sid);
    
}
