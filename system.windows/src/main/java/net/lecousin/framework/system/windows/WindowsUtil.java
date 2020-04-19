package net.lecousin.framework.system.windows;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;

/**
 * Utilities for Windows types.
 */
public final class WindowsUtil {
	
	private WindowsUtil() {
		// no instance
	}

	/** Return a list of String from a buffer containing strings separated by 0. */
	public static List<String> toStrings(byte[] buffer) {
        int i = 0;
    	boolean lastzero = true;
    	StringBuilder s = new StringBuilder();
    	List<String> list = new LinkedList<>();
    	do {
    		char c = (char)((buffer[i] & 0xFF) | ((buffer[i + 1] & 0xFF) << 8));
    		i += 2;
    		if (c == 0) {
    			if (lastzero) break;
    			lastzero = true;
    			list.add(s.toString());
    			s = new StringBuilder();
    			continue;
    		}
    		lastzero = false;
    		s.append(c);
    	} while (i < buffer.length);
    	if (s.length() != 0)
			list.add(s.toString());
    	return list;		
		
	}
	
	/** Return a list of String from a buffer containing strings separated by 0. */
	public static List<String> toStrings(char[] buffer) {
        int i = 0;
    	boolean lastzero = true;
    	StringBuilder s = new StringBuilder();
    	List<String> list = new LinkedList<>();
    	do {
    		char c = buffer[i];
    		i++;
    		if (c == 0) {
    			if (lastzero) break;
    			lastzero = true;
   				list.add(s.toString());
    			s = new StringBuilder();
    			continue;
    		}
    		lastzero = false;
    		s.append(c);
    	} while (i < buffer.length);
		if (s.length() != 0)
			list.add(s.toString());
    	return list;		
		
	}
	
	/** Convert a Windows Unicode buffer to a Java string. */
	public static String toStringUnicode(byte[] buf, int off) {
		return toStringUnicode(buf, off, buf.length);
	}

	/** Convert a Windows Unicode buffer to a Java string. */
	public static String toStringUnicode(byte[] buf, int off, int max) {
		StringBuilder s = new StringBuilder();
		while (off < max - 1) {
			char c = (char)((buf[off] & 0xFF) | ((buf[off + 1] & 0xFF) << 8));
			off += 2;
			if (c == 0) break;
			s.append(c);
		}
		return s.toString();
	}
	
	/** Convert a Windows Ascii buffer to a Java string. */
	public static String toStringAscii(byte[] buf, int off) {
		StringBuilder s = new StringBuilder();
		while (off < buf.length) {
			char c = (char)(buf[off] & 0xFF);
			off ++;
			if (c == 0) break;
			s.append(c);
		}
		return s.toString();
	}
	
	/** Convert a Java string into a Windows Unicode buffer. */
	public static byte[] toUnicode(String s) {
		byte[] b = new byte[s.length() * 2 + 2];
		for (int i = 0; i < s.length(); ++i) {
			char c = s.charAt(i);
			b[i * 2] = (byte)(c & 0xFF);
			b[i * 2 + 1] = (byte)((c >> 8) & 0xFF);
		}
		return b;
	}
	
	/** Open a Registry key. */
	public static HKEY openKey(HKEY base, String path) {
		String[] names = path.split("\\\\");
		HKEY key = base;
		for (int i = 0; i < names.length; ++i) {
			if (names[i].length() == 0) continue;
			HKEYByReference keyRef = new HKEYByReference();
			int res = Advapi32.INSTANCE.RegOpenKeyEx(key, names[i], 0, 0x20019, keyRef);
			if (res != 0) return null;
			if (key != base)
				Kernel32.INSTANCE.CloseHandle(key);
			key = keyRef.getValue();
		}
		return key;
	}
	
	public static final int REG_SZ = 1;
	public static final int REG_EXPAND_SZ = 2;
	
	/** Return the String value from Registry. */
	public static String getValue_REG_SZ(HKEY key, String valueName, byte[] buf) {
		IntByReference type = new IntByReference();
		IntByReference bufSize = new IntByReference(buf.length);
		int res = Advapi32.INSTANCE.RegQueryValueEx(key, valueName, 0, type, buf, bufSize);
		if (res != 0) return null;
		if (type.getValue() == REG_EXPAND_SZ) {
			if (bufSize.getValue() == 0) return "";
			String s = toStringUnicode(buf, 0);
			int i = 0;
			int j;
			while (i < s.length() && (j = s.indexOf('%', i)) >= 0) {
				int k = s.indexOf('%', j + 1);
				if (k < 0) break;
				String toExpand = s.substring(j + 1, k).toLowerCase();
				String value = null;
				for (Map.Entry<String, String> e : System.getenv().entrySet()) {
					if (e.getKey().toLowerCase().equals(toExpand)) {
						value = e.getValue();
						break;
					}
				}
				if (value == null) {
					i = j + 1;
					continue;
				}
				s = s.substring(0, j) + value + s.substring(k + 1);
				i = j + value.length();
			}
			return s;
		} else if (type.getValue() == REG_SZ) {
			if (bufSize.getValue() == 0) return "";
			return toStringUnicode(buf, 0);
		}
		return null;
	}
	
	/** Return the values from the given Registry key. */
	public static List<String> getValuesNames(HKEY key) {
		List<String> names = new LinkedList<>();
		char[] name = new char[256];
		byte[] buf = new byte[256];
		int index = 0;
		do {
			IntByReference nameLen = new IntByReference(256);
			IntByReference type = new IntByReference();
			IntByReference bufSize = new IntByReference(buf.length);
			int res = com.sun.jna.platform.win32.Advapi32.INSTANCE.RegEnumValue(key, index, name, nameLen, null, type, buf, bufSize);
			if (res != 0) break;
			if (nameLen.getValue() > 0) names.add(new String(name, 0, nameLen.getValue()));
			index++;
		} while (true);
		return names;
	}

	public static final int FILE_ANY_ACCESS = 0x0000;
	public static final int FILE_READ_ACCESS = 0x0002;
	
	public static final int METHOD_BUFFERED = 0;
	
	// skip checkstyle: MethodName
	// skip checkstyle: AbbreviationAsWordInName
	/** Create a CTL Code. */
	public static int CTL_CODE(int deviceType, int function, int method, int access) {
		return ((deviceType) << 16) | ((access) << 14) | ((function) << 2) | (method);
	}
	
	public static final int FILE_DEVICE_MASS_STORAGE = 0x0000002d;
	public static final int FILE_DEVICE_CHANGER = 0x00000030;
	public static final int FILE_DEVICE_DISK = 0x00000007;
	
	public static final int IOCTL_CHANGER_BASE = FILE_DEVICE_CHANGER;
	public static final int IOCTL_STORAGE_BASE = FILE_DEVICE_MASS_STORAGE;
	public static final int IOCTL_VOLUME_BASE = 'V';
	public static final int  IOCTL_DISK_BASE = FILE_DEVICE_DISK;
	
	public static final int IOCTL_CHANGER_GET_PRODUCT_DATA = CTL_CODE(IOCTL_CHANGER_BASE, 0x0002, METHOD_BUFFERED, FILE_READ_ACCESS);
	
	public static final int IOCTL_STORAGE_QUERY_PROPERTY = CTL_CODE(IOCTL_STORAGE_BASE, 0x0500, METHOD_BUFFERED, FILE_ANY_ACCESS);

	public static final int IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS = CTL_CODE(IOCTL_VOLUME_BASE, 0, METHOD_BUFFERED, FILE_ANY_ACCESS);
	
	public static final int IOCTL_DISK_GET_PARTITION_INFO_EX = CTL_CODE(IOCTL_DISK_BASE,0x12,METHOD_BUFFERED,FILE_ANY_ACCESS);
	
}
