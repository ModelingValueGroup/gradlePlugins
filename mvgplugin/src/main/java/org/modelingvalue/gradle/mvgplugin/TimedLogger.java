//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2026 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.mvgplugin;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

@SuppressWarnings({"RedundantCast", "unused"})
public class TimedLogger implements Logger {
    private final Logger delegate;
    private final long   t0 = System.currentTimeMillis();

    public TimedLogger(Logger delegate) {
        this.delegate = delegate;
    }

    static LogLevel toLogLevel(Marker marker) {
        if (marker == null) {
            return LogLevel.INFO;
        } else if (marker == Logging.LIFECYCLE) {
            return LogLevel.LIFECYCLE;
        } else {
            return marker == Logging.QUIET ? LogLevel.QUIET : LogLevel.INFO;
        }
    }

    protected void log(LogLevel logLevel, Throwable throwable, String format, Object... args) {
        FormattingTuple tuple           = MessageFormatter.arrayFormat(format, args);
        Throwable       loggedThrowable = throwable == null ? tuple.getThrowable() : throwable;
        this.log(logLevel, loggedThrowable, tuple.getMessage());
    }

    protected void log(LogLevel logLevel, Throwable throwable, String message) {
        this.delegate.log(logLevel, String.format("%,12d ", System.currentTimeMillis() - t0) + message, throwable);
    }

    ////////////////////////////////////////////////////////
    public boolean isLifecycleEnabled() {
        return this.delegate.isLifecycleEnabled();
    }

    public boolean isQuietEnabled() {
        return this.delegate.isQuietEnabled();
    }

    public boolean isEnabled(LogLevel level) {
        return this.delegate.isEnabled(level);
    }

    public String getName() {
        return this.delegate.getName();
    }

    public boolean isTraceEnabled() {
        return this.delegate.isTraceEnabled();
    }

    public boolean isTraceEnabled(Marker marker) {
        return this.delegate.isTraceEnabled(marker);
    }

    public boolean isDebugEnabled() {
        return this.delegate.isDebugEnabled();
    }

    public boolean isDebugEnabled(Marker marker) {
        return this.delegate.isDebugEnabled(marker);
    }

    public boolean isInfoEnabled() {
        return this.delegate.isInfoEnabled();
    }

    public boolean isInfoEnabled(Marker marker) {
        return this.delegate.isInfoEnabled(marker);
    }

    public boolean isWarnEnabled() {
        return this.delegate.isWarnEnabled();
    }

    public boolean isWarnEnabled(Marker marker) {
        return this.delegate.isWarnEnabled(marker);
    }

    public boolean isErrorEnabled() {
        return this.delegate.isErrorEnabled();
    }

    public boolean isErrorEnabled(Marker marker) {
        return this.delegate.isErrorEnabled(marker);
    }

    public void trace(String msg) {
    }

    public void trace(String format, Object arg) {
    }

    public void trace(String format, Object arg1, Object arg2) {
    }

    public void trace(String format, Object... arguments) {
    }

    public void trace(String msg, Throwable t) {
    }

    public void trace(Marker marker, String msg) {
    }

    public void trace(Marker marker, String format, Object arg) {
    }

    public void trace(Marker marker, String format, Object arg1, Object arg2) {
    }

    public void trace(Marker marker, String format, Object... argArray) {
    }

    public void trace(Marker marker, String msg, Throwable t) {
    }

    public void debug(String message) {
        if (this.isDebugEnabled()) {
            this.log(LogLevel.DEBUG, (Throwable) null, (String) message);
        }
    }

    public void debug(String format, Object arg) {
        if (this.isDebugEnabled()) {
            this.log(LogLevel.DEBUG, (Throwable) null, format, (Object) arg);
        }
    }

    public void debug(String format, Object arg1, Object arg2) {
        if (this.isDebugEnabled()) {
            this.log(LogLevel.DEBUG, (Throwable) null, format, arg1, arg2);
        }
    }

    public void debug(String format, Object... arguments) {
        if (this.isDebugEnabled()) {
            this.log(LogLevel.DEBUG, (Throwable) null, format, (Object[]) arguments);
        }
    }

    public void debug(String msg, Throwable t) {
        if (this.isDebugEnabled()) {
            this.log(LogLevel.DEBUG, t, msg);
        }
    }

    public void debug(Marker marker, String msg) {
        if (this.isDebugEnabled(marker)) {
            this.log(LogLevel.DEBUG, (Throwable) null, (String) msg);
        }
    }

    public void debug(Marker marker, String format, Object arg) {
        if (this.isDebugEnabled(marker)) {
            this.log(LogLevel.DEBUG, (Throwable) null, format, (Object) arg);
        }
    }

    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (this.isDebugEnabled(marker)) {
            this.log(LogLevel.DEBUG, (Throwable) null, format, arg1, arg2);
        }
    }

    public void debug(Marker marker, String format, Object... argArray) {
        if (this.isDebugEnabled(marker)) {
            this.log(LogLevel.DEBUG, (Throwable) null, format, (Object[]) argArray);
        }
    }

    public void debug(Marker marker, String msg, Throwable t) {
        if (this.isDebugEnabled(marker)) {
            this.log(LogLevel.DEBUG, t, msg);
        }
    }

    public void info(String message) {
        if (this.isInfoEnabled()) {
            this.log(LogLevel.INFO, (Throwable) null, (String) message);
        }
    }

    public void info(String format, Object arg) {
        if (this.isInfoEnabled()) {
            this.log(LogLevel.INFO, (Throwable) null, format, (Object) arg);
        }
    }

    public void info(String format, Object arg1, Object arg2) {
        if (this.isInfoEnabled()) {
            this.log(LogLevel.INFO, (Throwable) null, format, arg1, arg2);
        }
    }

    public void info(String format, Object... arguments) {
        if (this.isInfoEnabled()) {
            this.log(LogLevel.INFO, (Throwable) null, format, (Object[]) arguments);
        }
    }

    public void lifecycle(String message) {
        if (this.isLifecycleEnabled()) {
            this.log(LogLevel.LIFECYCLE, (Throwable) null, (String) message);
        }
    }

    public void lifecycle(String message, Object... objects) {
        if (this.isLifecycleEnabled()) {
            this.log(LogLevel.LIFECYCLE, (Throwable) null, message, (Object[]) objects);
        }
    }

    public void lifecycle(String message, Throwable throwable) {
        if (this.isLifecycleEnabled()) {
            this.log(LogLevel.LIFECYCLE, throwable, message);
        }
    }

    public void quiet(String message) {
        if (this.isQuietEnabled()) {
            this.log(LogLevel.QUIET, (Throwable) null, (String) message);
        }
    }

    public void quiet(String message, Object... objects) {
        if (this.isQuietEnabled()) {
            this.log(LogLevel.QUIET, (Throwable) null, message, (Object[]) objects);
        }
    }

    public void quiet(String message, Throwable throwable) {
        if (this.isQuietEnabled()) {
            this.log(LogLevel.QUIET, throwable, message);
        }
    }

    public void log(LogLevel level, String message) {
        if (this.isEnabled(level)) {
            this.log(level, (Throwable) null, (String) message);
        }
    }

    public void log(LogLevel level, String message, Object... objects) {
        if (this.isEnabled(level)) {
            this.log(level, (Throwable) null, message, (Object[]) objects);
        }
    }

    public void log(LogLevel level, String message, Throwable throwable) {
        if (this.isEnabled(level)) {
            this.log(level, throwable, message);
        }
    }

    public void info(String msg, Throwable t) {
        if (this.isInfoEnabled()) {
            this.log(LogLevel.INFO, t, msg);
        }
    }

    public void info(Marker marker, String msg) {
        if (this.isInfoEnabled(marker)) {
            this.log(toLogLevel(marker), (Throwable) null, (String) msg);
        }
    }

    public void info(Marker marker, String format, Object arg) {
        if (this.isInfoEnabled(marker)) {
            this.log(toLogLevel(marker), (Throwable) null, format, (Object) arg);
        }
    }

    public void info(Marker marker, String format, Object arg1, Object arg2) {
        if (this.isInfoEnabled(marker)) {
            this.log(toLogLevel(marker), (Throwable) null, format, arg1, arg2);
        }
    }

    public void info(Marker marker, String format, Object... argArray) {
        if (this.isInfoEnabled(marker)) {
            this.log(toLogLevel(marker), (Throwable) null, format, (Object[]) argArray);
        }
    }

    public void info(Marker marker, String msg, Throwable t) {
        if (this.isInfoEnabled(marker)) {
            this.log(toLogLevel(marker), t, msg);
        }
    }

    public void warn(String message) {
        if (this.isWarnEnabled()) {
            this.log(LogLevel.WARN, (Throwable) null, (String) message);
        }
    }

    public void warn(String format, Object arg) {
        if (this.isWarnEnabled()) {
            this.log(LogLevel.WARN, (Throwable) null, format, (Object) arg);
        }
    }

    public void warn(String format, Object arg1, Object arg2) {
        if (this.isWarnEnabled()) {
            this.log(LogLevel.WARN, (Throwable) null, format, arg1, arg2);
        }
    }

    public void warn(String format, Object... arguments) {
        if (this.isWarnEnabled()) {
            this.log(LogLevel.WARN, (Throwable) null, format, (Object[]) arguments);
        }
    }

    public void warn(String msg, Throwable t) {
        if (this.isWarnEnabled()) {
            this.log(LogLevel.WARN, t, msg);
        }
    }

    public void warn(Marker marker, String msg) {
        if (this.isWarnEnabled(marker)) {
            this.log(LogLevel.WARN, (Throwable) null, (String) msg);
        }
    }

    public void warn(Marker marker, String format, Object arg) {
        if (this.isWarnEnabled(marker)) {
            this.log(LogLevel.WARN, (Throwable) null, format, (Object) arg);
        }
    }

    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if (this.isWarnEnabled(marker)) {
            this.log(LogLevel.WARN, (Throwable) null, format, arg1, arg2);
        }
    }

    public void warn(Marker marker, String format, Object... argArray) {
        if (this.isWarnEnabled(marker)) {
            this.log(LogLevel.WARN, (Throwable) null, format, (Object[]) argArray);
        }
    }

    public void warn(Marker marker, String msg, Throwable t) {
        if (this.isWarnEnabled(marker)) {
            this.log(LogLevel.WARN, t, msg);
        }
    }

    public void error(String message) {
        if (this.isErrorEnabled()) {
            this.log(LogLevel.ERROR, (Throwable) null, (String) message);
        }
    }

    public void error(String format, Object arg) {
        if (this.isErrorEnabled()) {
            this.log(LogLevel.ERROR, (Throwable) null, format, (Object) arg);
        }
    }

    public void error(String format, Object arg1, Object arg2) {
        if (this.isErrorEnabled()) {
            this.log(LogLevel.ERROR, (Throwable) null, format, arg1, arg2);
        }
    }

    public void error(String format, Object... arguments) {
        if (this.isErrorEnabled()) {
            this.log(LogLevel.ERROR, (Throwable) null, format, (Object[]) arguments);
        }
    }

    public void error(String msg, Throwable t) {
        if (this.isErrorEnabled()) {
            this.log(LogLevel.ERROR, t, msg);
        }
    }

    public void error(Marker marker, String msg) {
        if (this.isErrorEnabled(marker)) {
            this.log(LogLevel.ERROR, (Throwable) null, (String) msg);
        }
    }

    public void error(Marker marker, String format, Object arg) {
        if (this.isErrorEnabled(marker)) {
            this.log(LogLevel.ERROR, (Throwable) null, format, (Object) arg);
        }
    }

    public void error(Marker marker, String format, Object arg1, Object arg2) {
        if (this.isErrorEnabled(marker)) {
            this.log(LogLevel.ERROR, (Throwable) null, format, arg1, arg2);
        }
    }

    public void error(Marker marker, String format, Object... argArray) {
        if (this.isErrorEnabled(marker)) {
            this.log(LogLevel.ERROR, (Throwable) null, format, (Object[]) argArray);
        }
    }

    public void error(Marker marker, String msg, Throwable t) {
        if (this.isErrorEnabled(marker)) {
            this.log(LogLevel.ERROR, t, msg);
        }
    }
}
