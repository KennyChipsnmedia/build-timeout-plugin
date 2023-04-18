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
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
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

        startAt = DateTime.now();

        return Math.min(absTimeout, noactTimeout);
    }

    @Override
    public void onWrite(AbstractBuild<?,?> build, byte b[], int length) {
        BuildTimeoutWrapper.EnvironmentImpl env = build.getEnvironments().get(BuildTimeoutWrapper.EnvironmentImpl.class);
        if (env != null) {
            DateTime deadline = startAt.plusMinutes(Integer.parseInt(timeoutMinutes));
            DateTime delay = DateTime.now().plusSeconds(Integer.parseInt(timeoutSecondsString));

            if(!delay.isAfter(deadline)) {
                build.getDisplayName();
                // TODO logging debug
//                LOGGER.log(Level.INFO, build.getFullDisplayName() + " delay reschedule:" + dateTimeFormat.print(delay) + " deadline:" + dateTimeFormat.print(deadline) );
                env.rescheduleIfScheduled();
            }
            else {
                // TODO logging debug
//                LOGGER.log(Level.INFO, build.getFullDisplayName() + " no more delay:" + dateTimeFormat.print(delay) + " is after deadline:" + dateTimeFormat.print(deadline) );
            }
        }
    }

    @Override
    public Descriptor<BuildTimeOutStrategy> getDescriptor() {
        return DESCRIPTOR;
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
