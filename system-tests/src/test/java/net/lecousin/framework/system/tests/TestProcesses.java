package net.lecousin.framework.system.tests;

import net.lecousin.framework.core.test.LCCoreAbstractTest;
import net.lecousin.framework.system.LCSystem;
import net.lecousin.framework.system.software.process.Processes;

import org.junit.Assert;
import org.junit.Test;

public class TestProcesses extends LCCoreAbstractTest {

	@Test
	public void testProcesses() {
		Processes processes = LCSystem.get().getSoftware().getProcesses();
		Assert.assertNotEquals(0, processes.listProcessesIds().size());
	}
	
}
