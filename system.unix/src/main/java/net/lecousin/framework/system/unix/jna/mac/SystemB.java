package net.lecousin.framework.system.unix.jna.mac;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.Structure;

public interface SystemB extends com.sun.jna.platform.mac.SystemB {
	
	public static SystemB INSTANCE = Native.loadLibrary("System", SystemB.class);

    // params.h
    public static int MAXPATHLEN = 1024;
	
    // fsstat paths
    public static int MNT_WAIT = 0x0001;
    public static int MNT_NOWAIT = 0x0010;
    public static int MNT_DWAIT = 0x0100;
	
    // length of fs type name including null
    public static int MFSTYPENAMELEN = 16;
    
    public static class Statfs extends Structure {
        public int f_bsize; /* fundamental file system block size */
        public int f_iosize; /* optimal transfer block size */
        public long f_blocks; /* total data blocks in file system */
        public long f_bfree; /* free blocks in fs */
        public long f_bavail; /* free blocks avail to non-superuser */
        public long f_files; /* total file nodes in file system */
        public long f_ffree; /* free file nodes in fs */
        public int[] f_fsid = new int[2]; /* file system id */
        public int f_owner; /* user that mounted the filesystem */
        public int f_type; /* type of filesystem */
        public int f_flags; /* copy of mount exported flags */
        public int f_fssubtype; /* fs sub-type (flavor) */
        /* fs type name */
        public byte[] f_fstypename = new byte[MFSTYPENAMELEN];
        /* directory on which mounted */
        public byte[] f_mntonname = new byte[MAXPATHLEN];
        /* mounted filesystem */
        public byte[] f_mntfromname = new byte[MAXPATHLEN];
        public int[] f_reserved = new int[8]; /* For future use */

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(new String[] { "f_bsize", "f_iosize", "f_blocks", "f_bfree", "f_bavail", "f_files",
                    "f_ffree", "f_fsid", "f_owner", "f_type", "f_flags", "f_fssubtype", "f_fstypename", "f_mntonname",
                    "f_mntfromname", "f_reserved" });
        }
    }
	
    public int getfsstat64(Statfs[] buf, int bufsize, int flags);
}
