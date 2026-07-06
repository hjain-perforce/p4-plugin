package org.jenkinsci.plugins.p4.console;

import com.perforce.p4java.server.callback.ILogCallback;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes P4Java's internal trace/log output into a {@link java.util.logging.Logger}
 * named for the plugin package so advanced P4 trace diagnostics (e.g. '-vssl',
 * '-vrpc', '-vnet', '-vtime') surface under Manage Jenkins &gt; System Log rather
 * than the build console (P4JENKINS-175).
 *
 * <p>Registered globally via {@link com.perforce.p4java.Log#setLogCallback} only
 * when trace flags are configured; when no flags are set no callback is installed
 * and connection behaviour is unchanged.</p>
 */
public class P4TraceLogging implements ILogCallback {

	/**
	 * Plugin package logger; controllable from Manage Jenkins &gt; System Log.
	 */
	private static final Logger logger = Logger.getLogger("org.jenkinsci.plugins.p4");

	private final LogTraceLevel traceLevel;

	public P4TraceLogging(LogTraceLevel traceLevel) {
		this.traceLevel = (traceLevel == null) ? LogTraceLevel.NONE : traceLevel;
	}

	/**
	 * Map a numeric P4 trace level (e.g. the '3' from '-vrpc=3') to a P4Java
	 * {@link LogTraceLevel}. Levels at or above the most verbose enum value are
	 * clamped to {@link LogTraceLevel#ALL}; negative values map to
	 * {@link LogTraceLevel#NONE}.
	 *
	 * @param level numeric trace level
	 * @return corresponding P4Java trace level
	 */
	public static LogTraceLevel toTraceLevel(int level) {
		switch (level) {
			case 0:
				return LogTraceLevel.NONE;
			case 1:
				return LogTraceLevel.COARSE;
			case 2:
				return LogTraceLevel.FINE;
			case 3:
				return LogTraceLevel.SUPERFINE;
			default:
				return (level < 0) ? LogTraceLevel.NONE : LogTraceLevel.ALL;
		}
	}

	@Override
	public LogTraceLevel getTraceLevel() {
		return traceLevel;
	}

	@Override
	public void internalError(String errorString) {
		logger.severe(errorString);
	}

	@Override
	public void internalException(Throwable thr) {
		logger.log(Level.SEVERE, (thr == null) ? null : thr.getLocalizedMessage(), thr);
	}

	@Override
	public void internalWarn(String warnString) {
		logger.warning(warnString);
	}

	@Override
	public void internalInfo(String infoString) {
		logger.info(infoString);
	}

	@Override
	public void internalStats(String statsString) {
		logger.fine(statsString);
	}

	@Override
	public void internalTrace(LogTraceLevel traceLevel, String traceMessage) {
		logger.fine(traceMessage);
	}
}
