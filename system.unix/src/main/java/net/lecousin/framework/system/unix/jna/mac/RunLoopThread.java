package net.lecousin.framework.system.unix.jna.mac;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.util.AsyncCloseable;

public class RunLoopThread extends Thread implements AsyncCloseable<Exception> {

	public static void init() {
		if (instance != null) return;
		instance = new RunLoopThread();
		instance.start();
		LCCore.Environment env = LCCore.get();
		if (env != null) env.toClose(instance);
		instance.start.block(0);
	}
	
	private static RunLoopThread instance = null;
	
	private RunLoopThread() {
		super("Mac OS - RunLoop");
	}
	
	private SynchronizationPoint<Exception> start = new SynchronizationPoint<>();
	private SynchronizationPoint<Exception> close = null;
	
	@Override
	public ISynchronizationPoint<Exception> closeAsync() {
		if (close != null) return close;
		close = new SynchronizationPoint<>();
		CoreFoundation.INSTANCE.CFRunLoopStop(CoreFoundation.INSTANCE.CFRunLoopGetMain());
		return close;
	}
	
	@Override
	public void run() {
		start.unblock();
		CoreFoundation.INSTANCE.CFRunLoopRun();
		if (close == null) close = new SynchronizationPoint<>(true);
		else close.unblock();
	}
}
