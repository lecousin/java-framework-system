package net.lecousin.framework.system.windows;

import java.io.IOException;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT.HANDLE;

public class Win32IOException extends IOException {

	private static final long serialVersionUID = -2678633604379315330L;

	public static void throw_last_error(HANDLE... h) throws Win32IOException {
		int err = Native.getLastError();
		if (h != null) {
			for (int i = 0; i < h.length; ++i)
				com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(h[i]);
		}
		throw new Win32IOException(err, "Error #"+err+": "+getErrorMessage(err));
	}
	
	public Win32IOException(int errorNum, String message) {
		super(message);
		this.errorNum = errorNum;
	}
	private int errorNum;
	public int getErrorNumber() { return errorNum; }
	
	public static String getLastError() {
		int err = Native.getLastError();
		return "Error #"+err+": "+getErrorMessage(err);
	}

	public static String getErrorMessage(int err) {
		switch (err) {
		case 1: return "Invalid function";
		case 2: return "File not found";
		case 3: return "Path not found";
		case 4: return "Too many open files";
		case 5: return "Access denied";
		case 6: return "Invalid handle";
		case 7: return "The storage control blocks were destroyed";
		case 8: return "Not enough memory";
		case 9: return "Invalid block";
		case 10: return "The environment is incorrect";
		case 11: return "Invalid format";
		case 12: return "Invalid access";
		case 13: return "Invalid data";
		case 14: return "Out of memory";
		case 15: return "Invalid drive";
		case 16: return "The directory cannot be removed: this is the current directory";
		case 17: return "Cannot move to a different device";
		case 18: return "No more files";
		case 19: return "The media is write protected";
		case 20: return "Cannot find the specified device";
		case 21: return "The device is not ready";
		case 22: return "The device does not reconize the command";
		case 23: return "Data error (CRC)";
		case 50: return "Not supported";
		case 87: return "Invalid parameter";
		case 998: return "Invalid access to memory location";
		case 1400: return "Invalid window handle";
		default: return "";
		}
	}
	
}
