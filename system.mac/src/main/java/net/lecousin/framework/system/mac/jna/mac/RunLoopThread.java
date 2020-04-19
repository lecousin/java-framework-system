package net.lecousin.framework.system.mac.jna.mac;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.async.IAsync;
import net.lecousin.framework.system.mac.jna.JnaInstances;
import net.lecousin.framework.concurrent.async.Async;
import net.lecousin.framework.util.AsyncCloseable;

public class RunLoopThread extends Thread implements AsyncCloseable<Exception> {

	public static void init() throws Exception {
		if (instance != null) return;
		instance = new RunLoopThread();
		instance.start();
		LCCore.Environment env = LCCore.get();
		if (env != null) env.toClose(instance);
		instance.start.blockThrow(0);
	}
	
	private static RunLoopThread instance = null;
	
	private RunLoopThread() {
		super("Mac OS - RunLoop");
	}
	
	private Async<Exception> start = new Async<>();
	private Async<Exception> close = null;
	
	@Override
	public IAsync<Exception> closeAsync() {
		if (close != null) return close;
		close = new Async<>();
		JnaInstances.coreFoundation.CFRunLoopStop(JnaInstances.coreFoundation.CFRunLoopGetMain());
		return close;
	}
	
	@Override
	public void run() {
		start.unblock();
		JnaInstances.coreFoundation.CFRunLoopRun();
		if (close == null) close = new Async<>(true);
		else close.unblock();
	}
}
