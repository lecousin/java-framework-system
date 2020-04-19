package net.lecousin.framework.system.windows;

import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;

// skip checkstyle: ParameterName
/** Listener of Windows events. */
public interface WindowsEventListener {

	/** Called when Windows raises an event. */
	public void fire(int eventId, WPARAM uParam, LPARAM lParam);
	
}
