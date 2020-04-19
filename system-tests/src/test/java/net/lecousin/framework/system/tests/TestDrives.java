package net.lecousin.framework.system.tests;

import java.io.File;

import net.lecousin.framework.core.test.LCCoreAbstractTest;
import net.lecousin.framework.log.Logger;
import net.lecousin.framework.log.LoggerFactory;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.hardware.drive.Drive;
import net.lecousin.framework.system.hardware.drive.Drives;

import org.junit.Assert;
import org.junit.Test;

public class TestDrives extends LCCoreAbstractTest {

	@Test
	public void testDrives() throws Exception {
		Drives drives = LCSystem.get().getHardware().getDrives();
		drives.initialize().getSynch().blockThrow(0);
		Assert.assertNotEquals(0, drives.getDrives().size());
		Logger logger = LoggerFactory.get(TestDrives.class);
		for (Drive drive : drives.getDrives()) {
			logger.info("Drive detected: " + drive);
			for (File f : drive.getMountPoints()) {
				logger.info(" - Mount point detected: " + f.getAbsolutePath());
			}
		}
	}
	
}
