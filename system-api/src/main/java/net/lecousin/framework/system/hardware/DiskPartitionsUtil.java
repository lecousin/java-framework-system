package net.lecousin.framework.system.hardware;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import net.lecousin.framework.io.IO;
import net.lecousin.framework.io.util.DataUtil;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.util.StringUtil;

/**
 * Utility methods for a disk partition.
 */
public class DiskPartitionsUtil {

	/** Read the partition table from a disk content. */
	public static boolean readPartitionTable(IO.Readable.Seekable content, List<DiskPartition> partitions) {
		return readPartitionTable(content, 0, -1, partitions);
	}
	
	/** Read the partition table from a disk content. */
	public static boolean readPartitionTable(IO.Readable.Seekable content, long pos, long logicStart, List<DiskPartition> partitions) {
		byte[] b = new byte[4 * 16];
		ByteBuffer buffer = ByteBuffer.wrap(b);
		try {
			if (content.readFullySync(pos + 0x1BE, buffer) != 4 * 16) return false;
			int nbExt = 0;
			for (int i = 0; i < 4; ++i) {
				DiskPartition p = new DiskPartition();
				p.partitionSlotIndex = i + 1;
				p.bootable = false;
				if (b[i * 16] == (byte)0x80) p.bootable = true; 
				else if (b[i * 16] != 0) {
					if (LCSystem.log.warn())
						LCSystem.log.warn("Invalid partition table: bootable flag = 0x"
							+ StringUtil.encodeHexa(b[i * 16]) + " for partition index " + (i + 1));
					return false;
				}
				p.startHead = (short)(b[i * 16 + 1] & 0xFF);
				p.startSector = (short)(b[i * 16 + 2] & 0x3F);
				p.startCylinder = (b[i * 16 + 3] & 0xFF) | ((b[i * 16 + 2] & 0xC0) << 2);
				p.type = (short)(b[i * 16 + 4] & 0xFF);
				if (p.type == 0) continue;
				//if (p.type == 0xEE) ;// TODO return readGUIDTable();
				p.endHead = (short)(b[i * 16 + 5] & 0xFF);
				p.endSector = (short)(b[i * 16 + 6] & 0x3F);
				p.endCylinder = (b[i * 16 + 7] & 0xFF) | ((b[i * 16 + 6] & 0xC0) << 2);
				p.lba = DataUtil.readIntegerLittleEndian(b, i * 16 + 8) & 0xFFFFFFFFL;
				p.nbSectors = DataUtil.readIntegerLittleEndian(b, i * 16 + 12) & 0xFFFFFFFFL;
				if (p.lba == 0) {
					if (LCSystem.log.error()) LCSystem.log.error("Cannot determine partition position for index " + (i + 1));
					continue;
					// lba = (start_cylinder * disk_nb_heads + start_head)  * disk_nb_sectors + start_sector - 1;
					// nb_sectors = (end_cylinder * disk_nb_heads + end_head)  * disk_nb_sectors + end_sector;
					// nb_sectors -= lba;
				}
				p.start = p.lba * 512;
				p.size = p.nbSectors * 512;
				if (p.type == 5 || p.type == 15) {
					if (logicStart > 0)
						p.start += logicStart;
					ArrayList<DiskPartition> list = new ArrayList<DiskPartition>();
					if (!readPartitionTable(content, p.start, logicStart > 0 ? logicStart : p.start, list)) return false;
					for (DiskPartition part : list) {
						partitions.add(part);
						part.partitionSlotIndex += 4 + nbExt * 4;
					}
					nbExt++;
					continue;
				}
				if (logicStart > 0)
					p.start += pos;
				partitions.add(p);
			}
			return true;
		} catch (IOException e) {
			if (LCSystem.log.error()) LCSystem.log.error("Error while reading partition table", e);
			return false;
		}
	}
	
}
