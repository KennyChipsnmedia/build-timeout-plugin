package hudson.plugins.build_timeout.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.plugins.build_timeout.BuildTimeOutStrategy;
import hudson.plugins.build_timeout.BuildTimeOutStrategyDescriptor;
import hudson.plugins.build_timeout.BuildTimeoutWrapper;
import hudson.tasks.BuildWrapper;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jfree.data.time.Minute;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FusionTimeoutStrategy extends BuildTimeOutStrategy {
    private static final Logger LOGGER = Logger.getLogger(BuildWrapper.class.getName());
    private static final DateTimeFormatter dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
    private final String timeoutMinutes;
    private final String timeoutSecondsString;
    private DateTime startAt = DateTime.now();
    private String reason = null;


    @Deprecated
    public FusionTimeoutStrategy(int timeoutMinutes, String timeoutSecondsString) {
        this.timeoutMinutes = Integer.toString(Math.max((int) (BuildTimeoutWrapper.MINIMUM_TIMEOUT_MILLISECONDS / MINUTES), timeoutMinutes));
        this.timeoutSecondsString = timeoutSecondsString;
    }

    @DataBoundConstructor
    public FusionTimeoutStrategy(String timeoutMinutes, String timeoutSecondsString) {
        this.timeoutMinutes = timeoutMinutes;
        this.timeoutSecondsString = timeoutSecondsString;

    }

    /**
     * @return minutes to timeout.
     */
    public String getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public String getTimeoutSecondsString()
    {
        return timeoutSecondsString;
    }

    @Override
    public long getTimeOut(@NonNull AbstractBuild<?,?> build, @NonNull BuildListener listener)
        throws InterruptedException, MacroEvaluationException, IOException {
        long absTimeout =  MINUTES * Math.max((int) (BuildTimeoutWrapper.MINIMUM_TIMEOUT_MILLISECONDS / MINUTES), Integer.parseInt(
            expandAll(build, listener, getTimeoutMinutes())));
        long noactTimeout = Long.parseLong(expandAll(build, listener, getTimeoutSecondsString())) * 1000L;

        DateTime now = DateTime.now();

        startAt = new DateTime(now);

        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        DateTime absDeadLine = now.plusMinutes(Integer.parseInt(timeoutMinutes));
        DateTime noactDeadline = now.plusSeconds(Integer.parseInt(timeoutSecondsString));
        String whichTimeout = noactTimeout < absTimeout ? "NO_ACTIVITY_TIMEOUT" : "ABS_TIMEOUT";
        DateTime deadline = noactTimeout < absTimeout ? noactDeadline : absDeadLine;

        int duration = Math.abs(Minutes.minutesBetween(startAt, deadline).getMinutes());

        reason = "Reason: " + whichTimeout + " from: " + dtf.print(now) + " to: " + dtf.print(deadline) + ", duration:" + duration + " min";
        return Math.min(absTimeout, noactTimeout);

    }

    @Override
    public void onWrite(AbstractBuild<?,?> build, byte b[], int length) {

        BuildTimeoutWrapper.EnvironmentImpl env = build.getEnvironments().get(BuildTimeoutWrapper.EnvironmentImpl.class);
        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");


        if (env != null) {
            DateTime now = DateTime.now();
            DateTime absDeadLine = startAt.plusMinutes(Integer.parseInt(timeoutMinutes));
            DateTime noactDeadline = now.plusSeconds(Integer.parseInt(timeoutSecondsString));

            if(!noactDeadline.isAfter(absDeadLine)) {
                int duration = Math.abs(Minutes.minutesBetween(startAt, noactDeadline).getMinutes());
                reason = "Reason: NO_ACTIVITY_TIMEOUT from: " + dtf.print(now) + " to: " + dtf.print(noactDeadline) + ", duration:" + duration + " min";
                env.rescheduleIfScheduled();
            }
            else {
                int duration = Math.abs(Minutes.minutesBetween(startAt, absDeadLine).getMinutes());
                reason = "Reason: ABS_TIMEOUT from: " + dtf.print(startAt) + " to: " + dtf.print(absDeadLine) + ", duration:" + duration + " min";
            }
        }
    }

    @Override
    public Descriptor<BuildTimeOutStrategy> getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", FusionTimeoutStrategy.class.getSimpleName() + "[", "]")
            .add("timeoutMinutes='" + timeoutMinutes + "'")
            .toString();
    }

    @Extension
    public static final FusionTimeoutStrategy.DescriptorImpl DESCRIPTOR = new FusionTimeoutStrategy.DescriptorImpl();

    public static class DescriptorImpl extends BuildTimeOutStrategyDescriptor {

        @Override
//        public String getDisplayName() {
//            return Messages.AbsoluteTimeOutStrategy_DisplayName();
//        }
        public String getDisplayName() {
            return "Fusion(Absolute Or NoActivity)";
        }

        @Override
        public boolean isApplicableAsBuildStep() {
            return true;
        }
    }
}
