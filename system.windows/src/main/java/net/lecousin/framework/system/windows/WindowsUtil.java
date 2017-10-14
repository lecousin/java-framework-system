package net.lecousin.framework.system.windows;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.platform.win32.WinReg.HKEYByReference;
import com.sun.jna.ptr.IntByReference;

public class WindowsUtil {

	public static List<String> toStrings(byte[] buffer) {
        int i = 0;
    	boolean lastzero = true;
    	String s = "";
    	List<String> list = new LinkedList<String>();
    	do {
    		char c = (char)((buffer[i] & 0xFF) | ((buffer[i+1] & 0xFF) << 8));
    		i += 2;
    		if (c == 0) {
    			if (lastzero) break;
    			lastzero = true;
   				list.add(s);
    			s = "";
    			continue;
    		}
    		lastzero = false;
    		s += c;
    	} while (i < buffer.length);
		if (!s.isEmpty())
			list.add(s);
    	return list;		
		
	}
	
	public static String toStringUNICODE(byte[] buf, int off) {
		return toStringUNICODE(buf, off, buf.length);
	}

	public static String toStringUNICODE(byte[] buf, int off, int max) {
		StringBuilder s = new StringBuilder();
		while (off < max-1) {
			char c = (char)((buf[off] & 0xFF) | ((buf[off+1] & 0xFF) << 8));
			off += 2;
			if (c == 0) break;
			s.append(c);
		}
		return s.toString();
	}
	public static String toStringASCII(byte[] buf, int off) {
		StringBuilder s = new StringBuilder();
		while (off < buf.length) {
			char c = (char)(buf[off] & 0xFF);
			off ++;
			if (c == 0) break;
			s.append(c);
		}
		return s.toString();
	}
	
	public static byte[] toUNICODE(String s) {
		byte[] b = new byte[s.length()*2+2];
		for (int i = 0; i < s.length(); ++i) {
			char c = s.charAt(i);
			b[i*2] = (byte)(c & 0xFF);
			b[i*2+1] = (byte)((c >> 8) & 0xFF);
		}
		return b;
	}
	
	public static HKEY openKey(HKEY base, String path) {
		String[] names = path.split("\\\\");
		HKEY key = base;
		for (int i = 0; i < names.length; ++i) {
			if (names[i].length() == 0) continue;
			HKEYByReference key_ref = new HKEYByReference();
			int res = Advapi32.INSTANCE.RegOpenKeyEx(key, names[i], 0, 0x20019, key_ref);
			if (res != 0) return null;
			if (key != base)
				Kernel32.INSTANCE.CloseHandle(key);
			key = key_ref.getValue();
		}
		return key;
	}
	public static final int REG_SZ = 1;
	public static final int REG_EXPAND_SZ = 2;
	public static String getValue_REG_SZ(HKEY key, String value_name, byte[] buf) {
		IntByReference type = new IntByReference();
		IntByReference buf_size = new IntByReference(buf.length);
		int res = Advapi32.INSTANCE.RegQueryValueEx(key, value_name, 0, type, buf, buf_size);
		if (res != 0) return null;
		if (type.getValue() == REG_EXPAND_SZ) {
			if (buf_size.getValue() == 0) return "";
			String s = toStringUNICODE(buf, 0);
			int i = 0, j;
			while (i < s.length() && (j = s.indexOf('%', i)) >= 0) {
				int k = s.indexOf('%', j+1);
				if (k < 0) break;
				String to_expand = s.substring(j+1, k).toLowerCase();
				String value = null;
				for (Map.Entry<String, String> e : System.getenv().entrySet()) {
					if (e.getKey().toLowerCase().equals(to_expand)) {
						value = e.getValue();
						break;
					}
				}
				if (value == null) {
					i = j+1;
					continue;
				}
				s = s.substring(0, j) + value + s.substring(k+1);
				i = j + value.length();
			}
			return s;
		} else if (type.getValue() == REG_SZ) {
			if (buf_size.getValue() == 0) return "";
			return toStringUNICODE(buf, 0);
		}
		return null;
	}
	public static List<String> getValuesNames(HKEY key) {
		List<String> names = new LinkedList<String>();
		char[] name = new char[256];
		byte[] buf = new byte[256];
		int index = 0;
		do {
			IntByReference name_len = new IntByReference(256);
			IntByReference type = new IntByReference();
			IntByReference buf_size = new IntByReference(buf.length);
			int res = com.sun.jna.platform.win32.Advapi32.INSTANCE.RegEnumValue(key, index, name, name_len, null, type, buf, buf_size);
			if (res != 0) break;
			if (name_len.getValue() > 0) names.add(new String(name, 0, name_len.getValue()));
			index++;
		} while (true);
		return names;
	}

	public static int FILE_ANY_ACCESS = 0x0000;
	public static int FILE_READ_ACCESS = 0x0002;
	
	public static int METHOD_BUFFERED = 0;
	
	public static int CTL_CODE(int DeviceType, int Function, int Method, int Access) {
		return ((DeviceType) << 16) | ((Access) << 14) | ((Function) << 2) | (Method);
	}
	
	public static int FILE_DEVICE_MASS_STORAGE = 0x0000002d;
	public static int FILE_DEVICE_CHANGER = 0x00000030;
	public static int FILE_DEVICE_DISK = 0x00000007;
	
	public static int IOCTL_CHANGER_BASE = FILE_DEVICE_CHANGER;
	public static int IOCTL_STORAGE_BASE = FILE_DEVICE_MASS_STORAGE;
	public static int IOCTL_VOLUME_BASE = 'V';
	public static int  IOCTL_DISK_BASE = FILE_DEVICE_DISK;
	
	public static int IOCTL_CHANGER_GET_PRODUCT_DATA = CTL_CODE(IOCTL_CHANGER_BASE, 0x0002, METHOD_BUFFERED, FILE_READ_ACCESS);
	
	public static int IOCTL_STORAGE_QUERY_PROPERTY = CTL_CODE(IOCTL_STORAGE_BASE, 0x0500, METHOD_BUFFERED, FILE_ANY_ACCESS);

	public static int IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS = CTL_CODE(IOCTL_VOLUME_BASE, 0, METHOD_BUFFERED, FILE_ANY_ACCESS);
	
	public static int IOCTL_DISK_GET_PARTITION_INFO_EX = CTL_CODE(IOCTL_DISK_BASE,0x12,METHOD_BUFFERED,FILE_ANY_ACCESS);
	
}
