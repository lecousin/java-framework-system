package net.lecousin.framework.system.unix.jna;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;

public interface LibC extends com.sun.jna.platform.unix.LibC {

	public static LibC INSTANCE = Native.load(NAME, LibC.class);

	public static class FDSet extends Structure {

        private final static int NFBBITS = NativeLong.SIZE * 8;
        private final static int fd_count = 1024;
        
        public NativeLong[] fd_array = new NativeLong[(fd_count + NFBBITS - 1) / NFBBITS];

        public FDSet() {
            for (int i = 0; i < fd_array.length; ++i) {
                fd_array[i] = new NativeLong();
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
        protected List getFieldOrder() {
            return Arrays.asList(
            	"fd_array"
            );
        }

        public void FD_SET(int fd) {
            fd_array[fd / NFBBITS].setValue(fd_array[fd / NFBBITS].longValue() | (1L << (fd % NFBBITS)));
        }

        public boolean FD_ISSET(int fd) {
            return (fd_array[fd / NFBBITS].longValue() & (1L << (fd % NFBBITS))) != 0;
        }

        public void FD_ZERO() {
            for (NativeLong fd : fd_array) {
                fd.setValue(0L);
            }
        }

        public void FD_CLR(int fd) {
            fd_array[fd / NFBBITS].setValue(fd_array[fd / NFBBITS].longValue() & ~(1L << (fd % NFBBITS)));
        }

    }
	
	public static class TimeVal extends Structure {

        public NativeLong tv_sec;
        public NativeLong tv_usec;

        public TimeVal(long sec, long usec) {
            tv_sec = new NativeLong(sec);
            tv_usec = new NativeLong(usec);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
        protected List getFieldOrder() {
            return Arrays.asList(
            	"tv_sec", "tv_usec"
            );
        }
    }	

	public static class PollFD extends Structure {

		/** File descriptor to poll. */
	    public int fd;
	    /** Types of events poller cares about. */
	    public short events;
	    /** Types of events that actually occurred. */
	    public short revents;

	    public PollFD(int fd, short events, short revents) {
	        super();
	        this.fd = fd;
	        this.events = events;
	        this.revents = revents;
	    }

	    /**
	     * Specifies fields order.
	     * 
	     * @see com.sun.jna.Structure#getFieldOrder()
	     */
	    @SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
	    protected List getFieldOrder() {
	        return Arrays.asList("fd", "events", "revents");
	    }
	    
    }
	
	/** Open for reading only. */
    public static final int O_RDONLY = 00;

    /** Open in nonblocking mode. */
    public static final int O_NONBLOCK = 04000;

    /** There is urgent data to read. */
    public static final short POLLPRI = 0x002;

    /** Error condition. */
    public static final short POLLERR = 0x008;
    
	
	public int getpid();
	
	public int open(String path, int oflag);
	
	public int close(int fd);
	
	public int select(int n, FDSet read, FDSet write, FDSet error, TimeVal timeout);
	
	public int poll(PollFD[] fds, int nfds, int timeout);
}
