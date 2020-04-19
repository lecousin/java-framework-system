package net.lecousin.framework.system.software.process;

public interface SeparateProcess {
	
	/** Return the exit code, or throws IllegalThreadStateException if the process is not yet terminated. */
	int getExitCode() throws IllegalThreadStateException;
	
	/** Kill the process. */
	void kill();
	
	/** Call the listener when the process terminates. */
	void addListener(Runnable listener);

}
