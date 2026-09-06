package opensagetv.vibe.miniclient.android.util;

import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.logging.ILogger;

/**
 * Android logger implementation backed by SLF4J only.
 *
 * <p>All logging remains local through the existing SLF4J backend. The
 * metadata methods remain as no-ops to preserve the shared {@link ILogger}
 * interface after cloud crash-reporting removal.</p>
 */
public class Logger implements ILogger
{
    private org.slf4j.Logger log;

    public static Logger getLogger(Class cls)
    {
        Logger logger = new Logger();
        logger.log = LoggerFactory.getLogger(cls);
        return logger;
    }

    public static Logger getLogger(String name)
    {
        Logger logger = new Logger();
        logger.log = LoggerFactory.getLogger(name);
        return logger;
    }

    @Override
    public ILogger getLoggerInstance(String name)
    {
        return Logger.getLogger(name);
    }

    @Override
    public ILogger getLoggerInstance(Class cls)
    {
        return Logger.getLogger(cls);
    }

    @Override
    public void recordException(Throwable t)
    {
        log.error("Unhandled exception", t);
    }

    @Override
    public void logError(String message)
    {
        log.error(message);
    }

    @Override
    public void logError(String message, Throwable t)
    {
        log.error(message, t);
    }

    @Override
    public void logWarning(String message)
    {
        log.warn(message);
    }

    @Override
    public void logWarning(String message, Throwable t)
    {
        log.warn(message, t);
    }

    @Override
    public void logDebug(String message)
    {
        log.debug(message);
    }

    @Override
    public void logDebug(String message, Throwable t)
    {
        log.debug(message, t);
    }

    @Override
    public void logInfo(String message)
    {
        log.info(message);
    }

    @Override
    public void logInfo(String message, Throwable t)
    {
        log.info(message, t);
    }

    @Override
    public void logTrace(String message)
    {
        log.trace(message);
    }

    @Override
    public void logTrace(String message, Throwable t)
    {
        log.trace(message, t);
    }

    @Override
    public void setCustomKey(String key, String value)
    {
        // Cloud crash-report metadata is no longer used.
    }

    @Override
    public void setUserID(String userID)
    {
        // Cloud crash-report user metadata is no longer used.
    }
}
