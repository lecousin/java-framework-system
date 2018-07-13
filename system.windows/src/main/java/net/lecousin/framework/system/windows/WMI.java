package net.lecousin.framework.system.windows;

import java.io.Closeable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import com.jacob.com.EnumVariant;
import com.jacob.com.Variant;

import net.lecousin.framework.application.LCCore;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.exception.NoException;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.util.Triple;

/**
 * Access to Windows Management Instrumentation.
 */
public class WMI extends Thread implements Closeable {

	private WMI() {
		super("LCSystem - Win32 - WMI ActiveX Component");
	}
	
	private static WMI instance;
	
	/** Return the instance or create it. */
	public static synchronized WMI instance() {
		if (instance == null) {
			instance = new WMI();
			instance.start();
			LCCore.get().toClose(instance);
		}
		return instance;
	}

	private ActiveXComponent wmi;
	private ActiveXComponent wmiconnect;
	private boolean quit = false;
	private LinkedList<Triple<Triple<String,String,String[]>,List<Map<String,String>>,SynchronizationPoint<NoException>>>
		queries = new LinkedList<Triple<Triple<String,String,String[]>,List<Map<String,String>>,SynchronizationPoint<NoException>>>();
	
	@Override
	public void close() {
		quit = true;
		synchronized (queries) {
			queries.notifyAll();
		}
	}
	
	// skip checkstyle: LocalVariableName
	@Override
	public void run() {
		wmi = new ActiveXComponent("WbemScripting.SWbemLocator");
		// no connection parameters means to connect to the local machine
		Variant conRet = wmi.invoke("ConnectServer");
		// the author liked the ActiveXComponent api style over the Dispatch
		// style
		Dispatch conRetDispatch = conRet.toDispatch();
		wmiconnect = new ActiveXComponent(conRetDispatch);
		while (!quit) {
			Triple<Triple<String,String,String[]>,List<Map<String,String>>,SynchronizationPoint<NoException>> p;
			synchronized (queries) {
				if (queries.isEmpty())
					try { queries.wait(); }
					catch (InterruptedException e) { break; }
				if (quit) break;
				p = queries.removeFirst();
			}
			Triple<String,String,String[]> t = p.getValue1();
			String[] fields = t.getValue3();
			StringBuilder q = new StringBuilder();
			q.append("select ");
			for (int i = 0; i < fields.length; ++i) {
				if (i > 0) q.append(',');
				q.append(fields[i]);
			}
			String className = t.getValue1();
			String where = t.getValue2();
			q.append(" from ").append(className);
			if (where != null) q.append(" where ").append(where);
			List<Map<String,String>> list = null;
			Variant vCollection = null;
			Variant vQ = null;
			EnumVariant enumVariant = null;
			Dispatch vColD = null;
			try {
				vQ = new Variant(q.toString());
				vCollection = wmiconnect.invoke("ExecQuery", vQ);
				vColD = vCollection.toDispatch();
				enumVariant = new EnumVariant(vColD);
				Dispatch item = null;
				list = new LinkedList<Map<String,String>>();
				while (enumVariant.hasMoreElements()) {
					Variant itemV = enumVariant.nextElement();
					item = itemV.toDispatch();
					Map<String,String> map = new HashMap<String,String>();
					list.add(map);
					for (int i = 0; i < fields.length; ++i) {
						Variant value = Dispatch.call(item, fields[i]);
						map.put(fields[i], value.toString());
						value.safeRelease();
					}
					item.safeRelease();
					itemV.safeRelease();
				}
			} catch (Throwable th) {
				if (LCSystem.log.error())
					LCSystem.log.error("WMI call failed for query: " + q.toString(), th);
			} finally {
				if (enumVariant != null)
					enumVariant.safeRelease();
				if (vColD != null)
					vColD.safeRelease();
				if (vCollection != null)
					vCollection.safeRelease();
				if (vQ != null)
					vQ.safeRelease();
			}
			if (list == null) list = new LinkedList<Map<String,String>>();
			p.setValue2(list);
			p.getValue3().unblock();
		}
		conRetDispatch.safeRelease();
		conRet.safeRelease();
		wmiconnect.safeRelease();
		wmiconnect = null;
		wmi.safeRelease();
		wmi = null;
		instance = null;
	}
	
	/** Query. */
	public List<Map<String,String>> query(String className, String where, String... fields) {
		Triple<String,String,String[]> t = new Triple<String,String,String[]>(className, where, fields);
		SynchronizationPoint<NoException> sp = new SynchronizationPoint<NoException>();
		Triple<Triple<String,String,String[]>,List<Map<String,String>>,SynchronizationPoint<NoException>> 
			p = new Triple<Triple<String,String,String[]>,List<Map<String,String>>,SynchronizationPoint<NoException>>(t, null, sp);
		List<Map<String,String>> list;
		synchronized (queries) {
			queries.add(p);
			queries.notify();
		}
		sp.block(0);
		list = p.getValue2();
		return list;
	}
	
}
