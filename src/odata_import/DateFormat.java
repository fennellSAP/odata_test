package odata_import;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * An easier-to-use version of the original {@link SimpleDateFormat} that avoids tons of boilerplate code with a few
 * simple changes to the function signatures.
 * <p>
 * Keep in mind that time zones are VERY important to the runtime behaviour of this class. If you don't set a time zone
 * explicitly with the {@link #setTimeZone(String)} method, then Java will assume the local timezone for the server.
 * Confusingly, on our test servers, NetWeaver is set to UTC time. However, the Java runtimes on the <i>same
 * machines</i> are in the {@code Europe/Berlin} time zone, which is different from {@code UTC} by 1 to 2 hours! If
 * tests are being developed in the {@code EST} time zone then it adds to the confusion. Make sure to <i>carefully</i>
 * keep track of time zones to avoid messy test issues where tests pass locally but fail when run on Jenkins.
 *
 * @author Jonathan Benn
 */
public class DateFormat {

    /**
     * The internal formatter that does all the work
     */
    private SimpleDateFormat formatter;

    /**
     * @param pattern
     *            the pattern describing the date and time format
     * @see SimpleDateFormat#SimpleDateFormat(String)
     */
    public DateFormat(String pattern) {
        formatter = new SimpleDateFormat(pattern);
    }

    /**
     * @param date
     *            the time value to be formatted into a time string
     * @return the formatted time string
     * @see SimpleDateFormat#format(Date)
     */
    public String format(Date date) {
        return formatter.format(date);
    }

    /**
     * @param date
     *            the time value to be formatted into a time string
     * @param timeZone
     *            the {@link TimeZone} to use this one time when formatting the date. Will not affect subsequent runs of
     *            {@link #format(Date)}
     * @return the formatted time string
     * @see SimpleDateFormat#format(Date)
     */
    public String format(Date date, String timeZone) {
        return format(date, TimeZone.getTimeZone(timeZone));
    }

    /**
     * @param date
     *            the time value to be formatted into a time string
     * @param timeZone
     *            the {@link TimeZone} to use this one time when formatting the date. Will not affect subsequent runs of
     *            {@link #format(Date)}
     * @return the formatted time string
     * @see SimpleDateFormat#format(Date)
     */
    public String format(Date date, TimeZone timeZone) {
        TimeZone oldTimeZone = formatter.getTimeZone();
        formatter.setTimeZone(timeZone);
        String formattedDate = formatter.format(date);
        formatter.setTimeZone(oldTimeZone);
        return formattedDate;
    }

    /**
     * @return the pattern string describing this date format
     */
    public String getPattern() {
        return formatter.toPattern();
    }

    /**
     * @return the time zone associated with this formatter
     * @see SimpleDateFormat#getTimeZone()
     */
    public TimeZone getTimeZone() {
        return formatter.getTimeZone();
    }

    /**
     * @param source
     *            a {@link String} to parse
     * @return a {@link Date} parsed from the inputed {@code source} string
     * @see SimpleDateFormat#parse(String)
     */
    public Date parse(String source) {
        try {
            return formatter.parse(source);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param timeZone
     *            the given new time zone
     * @return this object for function call chaining
     * @see SimpleDateFormat#setTimeZone(TimeZone)
     */
    public DateFormat setTimeZone(String timeZone) {
        return setTimeZone(TimeZone.getTimeZone(timeZone));
    }

    /**
     * @param timeZone
     *            the given new time zone
     * @return this object for function call chaining
     * @see SimpleDateFormat#setTimeZone(TimeZone)
     */
    public DateFormat setTimeZone(TimeZone timeZone) {
        formatter.setTimeZone(timeZone);
        return this;
    }

    @Override
    public String toString() {
        return "DateFormat(pattern=" + getPattern() + ", timeZone=" + getTimeZone().getID() + ")";
    }
}
