package net.lecousin.framework.system.windows;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import net.lecousin.framework.concurrent.Task;
import net.lecousin.framework.concurrent.TaskManager;
import net.lecousin.framework.concurrent.Threading;
import net.lecousin.framework.concurrent.synch.AsyncWork;
import net.lecousin.framework.concurrent.synch.ISynchronizationPoint;
import net.lecousin.framework.concurrent.synch.SynchronizationPoint;
import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.IOUtil;
import net.lecousin.framework.system.windows.jna.Kernel32;
import net.lecousin.framework.util.Pair;
import net.lecousin.framework.util.RunnableWithParameter;

/**
 * A Win32 stream to IO.Readable.Seekable.
 */
public class Win32HandleStream extends IO.AbstractIO implements IO.Readable.Seekable, IO.KnownSize {
	
	private HANDLE h;
	private long sector = 0;
	private short sectorPos = 0;
	private boolean sectorReady = false;
	private byte[] buffer = new byte[512];
	private BigInteger deviceSize;
	private String name;
	private Object taskManagerResource;
	private byte priority;
	
	/** Constructor. */
	public Win32HandleStream(HANDLE h, BigInteger size, String name, Object taskManagerResource, byte priority) { 
		this.h = h; 
		this.deviceSize = size;
		this.name = name;
		this.taskManagerResource = taskManagerResource;
		this.priority = priority;
	}
	
	@Override
	public String getSourceDescription() {
		return name;
	}
	
	public Object getTaskManagerResource() { return taskManagerResource; }
	
	@Override
	public ISynchronizationPoint<IOException> canStartReading() {
		return new SynchronizationPoint<>(true);
	}
	
	private void read_sector() throws IOException {
		if (deviceSize == null) throw new EOFException();
		if (BigInteger.valueOf(sector).multiply(BigInteger.valueOf(512)).compareTo(deviceSize) >= 0) throw new EOFException();
		long pos = sector * 512;
		int low = (int)(pos & 0xFFFFFFFF);
		IntByReference high = new IntByReference((int)(pos >> 32));
		pos = Kernel32.INSTANCE.SetFilePointer(h, low, high, 0);
		if (pos == -1)
			Win32IOException.throwLastError();
		IntByReference read = new IntByReference(0);
		if (!Kernel32.INSTANCE.ReadFile(h, buffer, 512, read, null))
			Win32IOException.throwLastError();
		sectorReady = true;
	}
	/*
	private void flush_sector() throws IOException {
		long pos = sector*512;
		int low = (int)(pos & 0xFFFFFFFF);
		IntByReference high = new IntByReference((int)(pos >> 32));
		if (Kernel32.INSTANCE.SetFilePointer(h, low, high, 0) == -1)
			Win32IOException.throw_last_error();
		IntByReference w = new IntByReference();
		if (!Kernel32.INSTANCE.WriteFile(h, buffer, 512, w, null))
			Win32IOException.throw_last_error();
		sector_modified = false;
	}
	*/
	
	@Override
	public byte getPriority() { return priority; }
	
	@Override
	public void setPriority(byte priority) { this.priority = priority; }
	
	@Override
	public TaskManager getTaskManager() {
		return Threading.get(taskManagerResource);
	}
	
	@Override
	public IO getWrappedIO() { return null; }
	
	@Override
	protected ISynchronizationPoint<IOException> closeIO() {
		//if (sector_modified) flush_sector();
		com.sun.jna.platform.win32.Kernel32.INSTANCE.CloseHandle(h);
		return new SynchronizationPoint<>(true);
	}
	
	@Override
	public long getSizeSync() {
		return deviceSize != null ? deviceSize.longValue() : 0;
	}
	
	@Override
	public AsyncWork<Long, IOException> getSizeAsync() {
		AsyncWork<Long, IOException> sp = new AsyncWork<>();
		sp.unblockSuccess(deviceSize != null ? Long.valueOf(deviceSize.longValue()) : Long.valueOf(0));
		return sp;
	}
	
	@Override
	public long getPosition() { return sector * 512 + sectorPos; }
	
	/** Seek. */
	public void setPosition(long pos) {
		long n = pos / 512;
		if (n != sector) {
			//if (sector_modified) flush_sector();
			sector = n; 
			sectorReady = false; 
		}
		sectorPos = (short)(pos % 512);
	}
	
	@Override
	public AsyncWork<Integer,IOException> readAsync(ByteBuffer buffer, RunnableWithParameter<Pair<Integer,IOException>> ondone) {
		Task<Integer,IOException> t = new Task<Integer,IOException>(this.taskManagerResource, "Read", priority, ondone) {
			@Override
			public Integer run() throws IOException {
				if (buffer.remaining() == 0) return Integer.valueOf(0);
				if (sectorPos == 512) {
					//if (sector_modified) flush_sector();
					sector++;
					sectorPos = 0;
					sectorReady = false;
				}
				if (!sectorReady)
					try { read_sector(); }
					catch (EOFException e) { return Integer.valueOf(0); }
					catch (IOException e) { throw e; }
				int l = 512 - sectorPos;
				if (l > buffer.remaining()) l = buffer.remaining();
				buffer.put(Win32HandleStream.this.buffer, sectorPos, l);
				sectorPos += l;
				return Integer.valueOf(l);
			}
		};
		t.start();
		return t.getSynch();
	}
	
	@Override
	public AsyncWork<Integer,IOException> readAsync(long pos, ByteBuffer buffer, RunnableWithParameter<Pair<Integer,IOException>> ondone) {
		setPosition(pos);
		return readAsync(buffer, ondone);
	}
	
	@Override
	public AsyncWork<Integer,IOException> readFullyAsync(ByteBuffer buffer, RunnableWithParameter<Pair<Integer,IOException>> ondone) {
		Task<Integer,IOException> t = new Task<Integer,IOException>(this.taskManagerResource, "Read", priority, ondone) {
			@Override
			public Integer run() throws IOException {
				if (buffer.remaining() == 0) return Integer.valueOf(0);
				if (sectorPos == 512) {
					//if (sector_modified) flush_sector();
					sector++;
					sectorPos = 0;
					sectorReady = false;
				}
				if (!sectorReady)
					try { read_sector(); }
					catch (EOFException e) { return Integer.valueOf(0); }
					catch (IOException e) { throw e; }
				int total = 0;
				do {
					int l = 512 - sectorPos;
					if (l > buffer.remaining()) l = buffer.remaining();
					buffer.put(Win32HandleStream.this.buffer, sectorPos, l);
					total += l;
					sectorPos += l;
					if (buffer.remaining() == 0) break;
					//if (sector_modified) flush_sector();
					sectorPos = 0;
					sector++;
					try { read_sector(); }
					catch (EOFException e) { break; }
					catch (IOException e) { throw e; }
				} while (true);
				return Integer.valueOf(total);
			}
		};
		t.start();
		return t.getSynch();
	}
	
	@Override
	public AsyncWork<Integer,IOException> readFullyAsync(long pos, ByteBuffer buffer, RunnableWithParameter<Pair<Integer,IOException>> ondone) {
		setPosition(pos);
		return readFullyAsync(buffer, ondone);
	}
	
	@Override
	public int readSync(ByteBuffer buffer) throws IOException {
		return IOUtil.readSyncUsingAsync(this, buffer);
	}
	
	@Override
	public int readSync(long pos, ByteBuffer buffer) throws IOException {
		return IOUtil.readSyncUsingAsync(this, pos, buffer);
	}
	
	@Override
	public int readFullySync(ByteBuffer buffer) throws IOException {
		return IOUtil.readFullySyncUsingAsync(this, buffer);
	}
	
	@Override
	public int readFullySync(long pos, ByteBuffer buffer) throws IOException {
		return IOUtil.readFullySyncUsingAsync(this, pos, buffer);
	}
	
	@Override
	public AsyncWork<Long,IOException> skipAsync(long n, RunnableWithParameter<Pair<Long,IOException>> ondone) {
		setPosition(getPosition() + n);
		Long r = Long.valueOf(n);
		if (ondone != null) ondone.run(new Pair<>(r, null));
		return new AsyncWork<Long,IOException>(r, null);
	}
	
	@Override
	public long skipSync(long n) {
		setPosition(getPosition() + n);
		return n;
	}
	
	@Override
	public long seekSync(SeekType type, long move) {
		switch (type) {
		case FROM_BEGINNING:
			setPosition(move);
			break;
		case FROM_CURRENT:
			setPosition(getPosition() + move);
			break;
		case FROM_END:
			setPosition(deviceSize.longValue() - move);
			break;
		default: break;
		}
		return getPosition();
	}
	
	@Override
	public AsyncWork<Long,IOException> seekAsync(SeekType type, long move, RunnableWithParameter<Pair<Long,IOException>> ondone) {
		Long p = Long.valueOf(seekSync(type, move));
		if (ondone != null) ondone.run(new Pair<>(p, null));
		return new AsyncWork<Long,IOException>(p, null);
	}

}
