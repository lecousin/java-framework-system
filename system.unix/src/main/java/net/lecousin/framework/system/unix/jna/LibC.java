package net.lecousin.framework.system.unix.jna;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;

public interface LibC extends com.sun.jna.platform.unix.LibC {

	public static LibC INSTANCE = Native.loadLibrary(NAME, LibC.class);

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
	
	public int getpid();
	
	public int select(int n, FDSet read, FDSet write, FDSet error, TimeVal timeout);
	
}
