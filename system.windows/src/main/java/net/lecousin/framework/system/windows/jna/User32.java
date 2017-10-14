package net.lecousin.framework.system.windows.jna;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

// skip checkstyle: TypeName
// skip checkstyle: MethodName
// skip checkstyle: ParameterName
// skip checkstyle: AbbreviationAsWordInName
// skip checkstyle: JavadocType
// skip checkstyle: JavadocMethod
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_INTERFACE")
public interface User32 extends com.sun.jna.platform.win32.User32 {

	public static final User32 INSTANCE = Native.loadLibrary("user32", User32.class, W32APIOptions.UNICODE_OPTIONS);

	/**
	 * Changes an attribute of the specified window
	 * @param   hWnd        A handle to the window
	 * @param   nIndex      The zero-based offset to the value to be set.
	 * @param   callback    The callback function for the value to be set.
	 */
	public int SetWindowLong(HWND hWnd, int nIndex, Callback callback);
    
	public LRESULT SendMessage(HWND hWnd, int uMsg, WPARAM wParam, LPARAM lParam);
}
