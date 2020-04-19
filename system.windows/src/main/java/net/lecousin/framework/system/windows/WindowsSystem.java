package net.lecousin.framework.system.windows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.jna.platform.win32.DBT;
import com.sun.jna.platform.win32.DBT.DEV_BROADCAST_HDR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HDEVNOTIFY;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.Wtsapi32;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.threads.SystemThread;
import net.lecousin.framework.concurrent.threads.Threading;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.windows.hardware.WindowsHardware;
import net.lecousin.framework.system.windows.jna.User32;
import net.lecousin.framework.system.windows.software.WindowsSoftware;

// skip checkstyle: ParameterName
// skip checkstyle: LocalVariableName
/**
 * Utilities for Windows system.
 */
public final class WindowsSystem extends LCSystem {
	
	WindowsSystem() {
		startEventHandler();
		hardware = new WindowsHardware();
		software = new WindowsSoftware();
	}
	
	private WindowsHardware hardware;
	private WindowsSoftware software;

	private HWND hWnd;
	private Map<Integer,List<WindowsEventListener>> listeners = new HashMap<>();
	
	@Override
	public WindowsHardware getHardware() {
		// TODO Auto-generated method stub
		return hardware;
	}
	
	@Override
	public WindowsSoftware getSoftware() {
		return software;
	}
	
	/** Add a listener for a specific event. */
	public void addSystemEventListener(int eventId, WindowsEventListener listener) {
		Integer i = Integer.valueOf(eventId);
		synchronized (listeners) {
			List<WindowsEventListener> list = listeners.get(i);
			if (list == null) {
				list = new ArrayList<>();
				listeners.put(i, list);
			}
			list.add(listener);
		}
	}
	
	public HWND getHiddenWindow() { return hWnd; }

	private static final int WM_NCCREATE = 0x0081;
	private WinUser.WindowProc wndProc = new WinUser.WindowProc() {
		@Override
		public LRESULT callback(HWND hWnd, int uMsg, WPARAM uParam, LPARAM lParam) {
			switch (uMsg) {
	        case WM_NCCREATE:
	        case WinUser.WM_QUIT:
	            return new LRESULT(1);
	        default:
			}
			synchronized (listeners) {
				List<WindowsEventListener> list = listeners.get(Integer.valueOf(uMsg));
				if (list != null) {
					for (WindowsEventListener listener : list)
						listener.fire(uMsg, uParam, lParam);
				}
			}
			return new LRESULT(0);
		}
	};
	
	private void startEventHandler() {
		Thread t = Threading.createSystemThread(new SystemThread() {
			@Override
			public void run() {
				// define new window class
				String windowClass = "MyWindowClass";
				HMODULE hInst = Kernel32.INSTANCE.GetModuleHandle("");

				WNDCLASSEX wClass = new WNDCLASSEX();
				wClass.hInstance = hInst;
				wClass.lpfnWndProc = wndProc;
				wClass.lpszClassName = windowClass;

				// register window class
				User32.INSTANCE.RegisterClassEx(wClass);

				// create new window
				hWnd = User32.INSTANCE
						.CreateWindowEx(
								com.sun.jna.platform.win32.User32.WS_EX_TOPMOST,
								windowClass,
								"My hidden helper window, used only to catch the windows events",
								0, 0, 0, 0, 0,
								null,
								null, hInst, null);

				Wtsapi32.INSTANCE.WTSRegisterSessionNotification(hWnd, Wtsapi32.NOTIFY_FOR_THIS_SESSION);

				/* this filters for all device classes */
				DEV_BROADCAST_HDR notificationFilter = new DEV_BROADCAST_HDR();
				notificationFilter.dbch_devicetype = DBT.DBT_DEVTYP_DEVICEINTERFACE;

				/*
				 * use User32.DEVICE_NOTIFY_ALL_INTERFACE_CLASSES instead of
				 * DEVICE_NOTIFY_WINDOW_HANDLE to ignore the dbcc_classguid value
				 */
				HDEVNOTIFY hDevNotify = User32.INSTANCE.RegisterDeviceNotification(
						hWnd, notificationFilter, com.sun.jna.platform.win32.User32.DEVICE_NOTIFY_WINDOW_HANDLE);

				MSG msg = new MSG();
				while (User32.INSTANCE.GetMessage(msg, hWnd, 0, 0xFFFF) != 0) {
					User32.INSTANCE.TranslateMessage(msg);
					User32.INSTANCE.DispatchMessage(msg);
				}

				User32.INSTANCE.UnregisterDeviceNotification(hDevNotify);
				Wtsapi32.INSTANCE.WTSUnRegisterSessionNotification(hWnd);
				User32.INSTANCE.UnregisterClass(windowClass, hInst);
				User32.INSTANCE.DestroyWindow(hWnd);
			}
			
			@Override
			public void debugStatus(StringBuilder s) {
				// nothing to say
			}
		});
		t.setName("Windows Event Handler");
		t.start();
		LCCore.get().toClose(() -> User32.INSTANCE.PostMessage(hWnd, WinUser.WM_QUIT, new WPARAM(0), new LPARAM(0)));
	}

}
