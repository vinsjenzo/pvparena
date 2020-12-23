package net.slipcor.pvparena.config;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFileFormatter extends Formatter {
    private static final String LOG_TEMPLATE = "%1$tF %1$tT [%2$s] %3$s %4$s%n";

    public LogFileFormatter() {
        super();
    }

    @Override
    public String format(final LogRecord record) {
        final Throwable exception = record.getThrown();
        final String logLevel = record.getLevel().getLocalizedName().toUpperCase();

        String throwable = "";
        if (exception != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            exception.printStackTrace(pw);
            pw.close();
            throwable = sw.toString();
        }

        return String.format(LOG_TEMPLATE, record.getMillis(), logLevel, record.getMessage(), throwable);
    }
}
